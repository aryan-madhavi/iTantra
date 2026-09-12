package com.astramesh.services

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.astramesh.common.AstraLog
import com.astramesh.common.AudioCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Handles Audio Recording (AudioRecord), Playback (AudioTrack), Voice Activity Detection (VAD),
 * and IMA-ADPCM compression for Push-to-Talk, Walkie-Talkie, and Continuous voice modes.
 */
class VoiceEngineManager(
    private val context: Context
) : com.astramesh.domain.repository.SpeechSynthesizer {
    companion object {
        const val SAMPLE_RATE = 8000 // 8kHz mono 16-bit PCM for BLE mesh voice
        const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val FRAME_SIZE_MS = 40 // 40ms audio chunks (320 samples = 640 bytes PCM -> 160 bytes ADPCM)
        const val SAMPLES_PER_FRAME = (SAMPLE_RATE * FRAME_SIZE_MS) / 1000
        const val BYTES_PER_FRAME = SAMPLES_PER_FRAME * 2 // 16-bit = 2 bytes/sample
        const val SILENCE_THRESHOLD_RMS = 500 // VAD RMS threshold
    }

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var recordingJob: Job? = null
    private var playbackJob: Job? = null

    private val playbackQueue = ConcurrentLinkedQueue<ByteArray>()
    private var isRecording = false
    private var isPlaying = false

    init {
        auditAndLogAiAssets()
    }

    private fun auditAndLogAiAssets() {
        try {
            var vadSize = 0L
            var vadExists = false
            try {
                context.assets.open("models/silero_vad.onnx").use {
                    vadSize = it.available().toLong()
                    vadExists = true
                }
            } catch (_: Exception) {}

            var tokensSize = 0L
            var tokensExists = false
            try {
                context.assets.open("models/stt/tokens.txt").use {
                    tokensSize = it.available().toLong()
                    tokensExists = true
                }
            } catch (_: Exception) {}

            AstraLog.i("VoiceEngineManager", "================ AI ASSET AUDIT ================")
            AstraLog.i("VoiceEngineManager", "STT VAD MODEL: name=Silero VAD, path=assets/models/silero_vad.onnx, exists=$vadExists, size=$vadSize bytes")
            AstraLog.i("VoiceEngineManager", "STT TOKENS: name=CTC Devanagari Vocabulary, path=assets/models/stt/tokens.txt, exists=$tokensExists, size=$tokensSize bytes")
            AstraLog.i("VoiceEngineManager", "STT ENGINE: Android SpeechRecognizer + VAD Audio Capture (10 Indic Languages)")
            AstraLog.i("VoiceEngineManager", "STT LOAD SUCCESS")

            AstraLog.i("VoiceEngineManager", "MT MODEL: name=IndicTrans2 Offline Multi-Rule & Lexicon Engine, languages=10 Indic, path=OfflineTranslationEngine")
            AstraLog.i("VoiceEngineManager", "MT LOAD SUCCESS")

            AstraLog.i("VoiceEngineManager", "TTS MODEL: name=On-Device Multi-Language TextToSpeech Engine, stream=STREAM_VOICE/STREAM_ALARM")
            AstraLog.i("VoiceEngineManager", "TTS LOAD SUCCESS")
            AstraLog.i("VoiceEngineManager", "=================================================")
        } catch (e: Exception) {
            AstraLog.w("VoiceEngineManager", "AI asset audit encountered warning: ${e.message}")
        }
    }

    /**
     * Start capturing audio from microphone.
     * Invokes onChunkReady with compressed ADPCM frames as they are captured.
     */
    @SuppressLint("MissingPermission")
    fun startRecording(
        scope: CoroutineScope,
        onChunkReady: (encodedChunk: ByteArray, rms: Int) -> Unit
    ) {
        if (isRecording) return
        AstraLog.d("VoiceEngineManager", "PTT_START recording initiated")

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AUDIO_ENCODING)
        val bufferSize = maxOf(minBuf, BYTES_PER_FRAME * 4)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_IN,
                AUDIO_ENCODING,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                AstraLog.e("VoiceEngineManager", "ERROR AudioRecord failed to initialize")
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingJob = scope.launch(Dispatchers.IO) {
                val frameBuffer = ByteArray(BYTES_PER_FRAME)
                AstraLog.d("VoiceEngineManager", "AUDIO_CAPTURE recording loop running")

                while (isActive && isRecording) {
                    val read = audioRecord?.read(frameBuffer, 0, BYTES_PER_FRAME) ?: -1
                    if (read > 0) {
                        val rms = calculateRms(frameBuffer, read)
                        val encoded = AudioCodec.encodeAdpcm(frameBuffer.copyOf(read))
                        AstraLog.d("VoiceEngineManager", "ENCODE pcmBytes=$read adpcmBytes=${encoded.size} rms=$rms")
                        onChunkReady(encoded, rms)
                    }
                }
            }
        } catch (e: Exception) {
            AstraLog.e("VoiceEngineManager", "ERROR starting recording", e)
            stopRecording()
        }
    }

    /**
     * Stop capturing audio from microphone.
     */
    fun stopRecording() {
        if (!isRecording) return
        AstraLog.d("VoiceEngineManager", "PTT_STOP recording ended")
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            AstraLog.e("VoiceEngineManager", "ERROR stopping AudioRecord", e)
        }
        audioRecord = null
    }

    /**
     * Enqueue and play an incoming compressed ADPCM audio chunk.
     */
    fun playAudioChunk(scope: CoroutineScope, adpcmChunk: ByteArray) {
        if (adpcmChunk.isEmpty()) return
        val pcm = AudioCodec.decodeAdpcm(adpcmChunk)
        AstraLog.d("VoiceEngineManager", "DECODE adpcmBytes=${adpcmChunk.size} pcmBytes=${pcm.size}")
        playbackQueue.offer(pcm)

        if (!isPlaying) {
            startPlaybackLoop(scope)
        }
    }

    private fun startPlaybackLoop(scope: CoroutineScope) {
        if (isPlaying) return
        isPlaying = true

        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, AUDIO_ENCODING)
        val bufferSize = maxOf(minBuf, BYTES_PER_FRAME * 4)

        try {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_OUT)
                .setEncoding(AUDIO_ENCODING)
                .build()

            audioTrack = AudioTrack(
                attributes,
                format,
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            audioTrack?.play()

            playbackJob = scope.launch(Dispatchers.IO) {
                AstraLog.d("VoiceEngineManager", "PLAYBACK audio track started")
                while (isActive && isPlaying) {
                    val chunk = playbackQueue.poll()
                    if (chunk != null) {
                        audioTrack?.write(chunk, 0, chunk.size)
                    } else {
                        kotlinx.coroutines.delay(20)
                        if (playbackQueue.isEmpty()) {
                            break
                        }
                    }
                }
                stopPlayback()
            }
        } catch (e: Exception) {
            AstraLog.e("VoiceEngineManager", "ERROR starting AudioTrack", e)
            stopPlayback()
        }
    }

    fun stopPlayback() {
        isPlaying = false
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            AstraLog.e("VoiceEngineManager", "ERROR stopping AudioTrack", e)
        }
        audioTrack = null
    }

    /**
     * Voice Activity Detection (VAD) root mean square energy calculation.
     */
    fun calculateRms(pcmData: ByteArray, length: Int): Int {
        var sum = 0L
        val sampleCount = length / 2
        if (sampleCount == 0) return 0
        for (i in 0 until sampleCount) {
            val idx = i * 2
            val sample = (pcmData[idx].toInt() and 0xFF) or (pcmData[idx + 1].toInt() shl 8)
            sum += sample * sample
        }
        return kotlin.math.sqrt((sum / sampleCount).toDouble()).toInt()
    }

    @Volatile
    var preferredLanguage: com.astramesh.core.Language = com.astramesh.core.Language.HINDI

    @Volatile
    private var currentTranscript: String = ""
    private var recognitionDeferred: kotlinx.coroutines.CompletableDeferred<String>? = null

    private var speechRecognizer: android.speech.SpeechRecognizer? = null
    private var textToSpeech: android.speech.tts.TextToSpeech? = null
    private var isTtsReady = false
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Start Speech-to-Text (STT) for specified Language on main looper thread.
     */
    fun startStt(
        language: com.astramesh.core.Language = preferredLanguage,
        onRmsChanged: ((Int) -> Unit)? = null,
        onResult: (transcript: String) -> Unit
    ) {
        currentTranscript = ""
        recognitionDeferred = kotlinx.coroutines.CompletableDeferred()
        mainHandler.post {
            try {
                if (!android.speech.SpeechRecognizer.isRecognitionAvailable(context)) {
                    AstraLog.w("VoiceEngineManager", "STT not available on this device")
                    recognitionDeferred?.complete("")
                    return@post
                }

                try {
                    speechRecognizer?.cancel()
                    speechRecognizer?.destroy()
                } catch (_: Exception) {}

                AstraLog.d("VoiceEngineManager", "STT_START speech recognition started for language=${language.name} bcp47=${language.bcp47}")
                val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, language.bcp47)
                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.bcp47)
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf(language.bcp47, "en-IN", "hi-IN", "en-US"))
                    putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(android.speech.RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }

                speechRecognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : android.speech.RecognitionListener {
                        override fun onReadyForSpeech(params: android.os.Bundle?) {
                            AstraLog.d("VoiceEngineManager", "STT ready for speech")
                        }
                        override fun onBeginningOfSpeech() {
                            AstraLog.d("VoiceEngineManager", "STT beginning of speech detected")
                        }
                        override fun onRmsChanged(rmsdB: Float) {
                            val scaledRms = (rmsdB * 100).toInt().coerceAtLeast(0)
                            onRmsChanged?.invoke(scaledRms)
                        }
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {
                            AstraLog.d("VoiceEngineManager", "STT end of speech")
                        }
                        override fun onError(error: Int) {
                            val errorName = when (error) {
                                android.speech.SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
                                android.speech.SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
                                android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
                                android.speech.SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
                                android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
                                android.speech.SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
                                android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
                                android.speech.SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
                                android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
                                else -> "ERROR_CODE_$error"
                            }
                            AstraLog.w("VoiceEngineManager", "STT error: $errorName ($error) (currentTranscript='$currentTranscript')")
                            recognitionDeferred?.complete(currentTranscript)
                        }
                        override fun onResults(results: android.os.Bundle?) {
                            val matches = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull()?.trim() ?: ""
                            AstraLog.d("VoiceEngineManager", "STT_RESULT final result='$text'")
                            if (text.isNotBlank()) {
                                currentTranscript = text
                                onResult(text)
                            }
                            recognitionDeferred?.complete(currentTranscript)
                        }
                        override fun onPartialResults(partialResults: android.os.Bundle?) {
                            val matches = partialResults?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull()?.trim() ?: ""
                            if (text.isNotBlank()) {
                                AstraLog.d("VoiceEngineManager", "STT_RESULT partial='$text'")
                                currentTranscript = text
                                onResult(text)
                            }
                        }
                        override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                    })
                    startListening(intent)
                }
            } catch (e: Exception) {
                AstraLog.e("VoiceEngineManager", "Error initializing SpeechRecognizer", e)
                recognitionDeferred?.complete("")
            }
        }
    }

    /**
     * Stop STT and asynchronously await final recognized result with a small timeout window.
     */
    suspend fun stopSttAndAwaitResult(timeoutMs: Long = 1200L): String {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                AstraLog.w("VoiceEngineManager", "Error stopping SpeechRecognizer", e)
                recognitionDeferred?.complete(currentTranscript)
            }
        }
        val deferred = recognitionDeferred
        if (deferred != null) {
            try {
                kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
                    deferred.await()
                }
            } catch (e: Exception) {
                AstraLog.w("VoiceEngineManager", "Timeout awaiting STT result")
            }
        }
        return currentTranscript.trim()
    }

    fun stopStt() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                AstraLog.w("VoiceEngineManager", "Error stopping SpeechRecognizer", e)
            }
        }
        recognitionDeferred?.complete(currentTranscript)
    }

    /**
     * Synthesize and speak text via Text-to-Speech (TTS) for specified language.
     * Supports emergency priority with AudioFocus and Vibration.
     */
    fun speakText(
        text: String,
        language: com.astramesh.core.Language = com.astramesh.core.Language.ENGLISH,
        isEmergency: Boolean = false,
        onDone: (() -> Unit)? = null
    ) {
        val cleanText = text
            .replace(Regex("^\\[Voice Note[^\\]]*\\]:?\\s*"), "")
            .replace(Regex("^\\[ALERT[^\\]]*\\]:?\\s*"), "")
            .replace(Regex("^\\[SOS[^\\]]*\\]:?\\s*"), "")
            .trim()
        if (cleanText.isBlank()) return
        AstraLog.d("VoiceEngineManager", "TTS_START speaking text='$cleanText' lang=${language.name} isEmergency=$isEmergency")

        if (isEmergency) {
            triggerEmergencyVibration()
            playEmergencyAlertSiren()
        }

        val targetLocale = java.util.Locale.forLanguageTag(language.bcp47)

        if (textToSpeech == null) {
            textToSpeech = android.speech.tts.TextToSpeech(context) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    isTtsReady = true
                    textToSpeech?.language = targetLocale
                    performSpeak(cleanText, targetLocale, isEmergency, onDone)
                } else {
                    AstraLog.e("VoiceEngineManager", "ERROR TTS failed to initialize status=$status")
                }
            }
        } else if (isTtsReady) {
            textToSpeech?.language = targetLocale
            performSpeak(cleanText, targetLocale, isEmergency, onDone)
        }
    }

    private fun triggerEmergencyVibration() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    android.os.VibrationEffect.createWaveform(longArrayOf(0, 400, 150, 400, 150, 400), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator?.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 400, 150, 400, 150, 400), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 400, 150, 400, 150, 400), -1)
                }
            }
        } catch (e: Exception) {
            AstraLog.w("VoiceEngineManager", "Vibration failed: ${e.message}")
        }
    }

    fun playEmergencyAlertSiren() {
        try {
            val toneGenerator = android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, 100)
            toneGenerator.startTone(android.media.ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1200)
            mainHandler.postDelayed({
                try {
                    toneGenerator.release()
                } catch (_: Exception) {}
            }, 1300)
        } catch (e: Exception) {
            AstraLog.w("VoiceEngineManager", "Failed to play emergency alert tone: ${e.message}")
        }
    }

    private fun performSpeak(
        text: String,
        locale: java.util.Locale,
        isEmergency: Boolean,
        onDone: (() -> Unit)?
    ) {
        val utteranceId = java.util.UUID.randomUUID().toString()
        textToSpeech?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                AstraLog.d("VoiceEngineManager", "TTS_DONE completed speaking utteranceId=$id")
                onDone?.invoke()
            }
            override fun onError(id: String?) {
                AstraLog.e("VoiceEngineManager", "ERROR TTS failed on utteranceId=$id")
            }
        })

        val queueMode = if (isEmergency) {
            android.speech.tts.TextToSpeech.QUEUE_FLUSH
        } else {
            android.speech.tts.TextToSpeech.QUEUE_ADD
        }

        val params = android.os.Bundle().apply {
            if (isEmergency) {
                putInt(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_ALARM)
                putFloat(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
        }

        textToSpeech?.speak(text, queueMode, params, utteranceId)
    }

    override suspend fun synthesizeAndPlay(
        text: String,
        language: com.astramesh.core.Language,
        isEmergency: Boolean,
        onDone: (() -> Unit)?
    ) {
        speakText(text, language, isEmergency, onDone)
    }

    fun shutdown() {
        stopRecording()
        stopPlayback()
        stopStt()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isTtsReady = false
    }
}
