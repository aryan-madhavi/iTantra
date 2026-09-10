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
) {
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

    private var speechRecognizer: android.speech.SpeechRecognizer? = null
    private var textToSpeech: android.speech.tts.TextToSpeech? = null
    private var isTtsReady = false

    /**
     * Start continuous Speech-to-Text (STT).
     */
    fun startStt(onResult: (transcript: String) -> Unit) {
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(context)) {
            AstraLog.w("VoiceEngineManager", "STT not available on this device")
            return
        }

        AstraLog.d("VoiceEngineManager", "STT_START speech recognition started")
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    AstraLog.e("VoiceEngineManager", "ERROR STT error code: $error")
                }
                override fun onResults(results: android.os.Bundle?) {
                    val matches = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    AstraLog.d("VoiceEngineManager", "STT_RESULT result='$text'")
                    if (text.isNotBlank()) {
                        onResult(text)
                    }
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    val matches = partialResults?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        AstraLog.d("VoiceEngineManager", "STT_RESULT partial='$text'")
                    }
                }
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
            startListening(intent)
        }
    }

    fun stopStt() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    /**
     * Synthesize and speak text via Text-to-Speech (TTS).
     */
    fun speakText(text: String, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) return
        AstraLog.d("VoiceEngineManager", "TTS_START speaking text='$text'")

        if (textToSpeech == null) {
            textToSpeech = android.speech.tts.TextToSpeech(context) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    isTtsReady = true
                    textToSpeech?.language = java.util.Locale.getDefault()
                    performSpeak(text, onDone)
                } else {
                    AstraLog.e("VoiceEngineManager", "ERROR TTS failed to initialize status=$status")
                }
            }
        } else if (isTtsReady) {
            performSpeak(text, onDone)
        }
    }

    private fun performSpeak(text: String, onDone: (() -> Unit)?) {
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
        textToSpeech?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, utteranceId)
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
