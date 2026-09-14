package com.astramesh.services

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.astramesh.common.AstraLog
import com.astramesh.common.AudioCodec
import com.astramesh.core.EmergencyClassifier
import com.astramesh.core.Language
import com.astramesh.core.SttConfig
import com.astramesh.services.audio.IndicConformerSttEngine
import com.astramesh.services.audio.MmsVitsTtsEngine
import com.astramesh.services.audio.NllbTranslationEngine
import com.astramesh.services.audio.SileroVadEngine
import com.astramesh.services.audio.VADEventType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Enterprise Offline Voice & Audio Engine Manager for iTantra.
 * Fully on-device Edge-ML Pipeline:
 * 1. Silero VAD v5 ONNX Voice Activity Detection
 * 2. IndicConformer INT8 ONNX Streaming Speech-to-Text
 * 3. Rule-based Sub-millisecond Multilingual Emergency Classifier
 * 4. NLLB-200 INT8 Offline Neural Translation Engine
 * 5. MMS-TTS (VITS) On-Device Synthesis Engine with LRU Model Caching
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
        const val SILENCE_THRESHOLD_RMS = 500
        private const val MAX_PLAYBACK_QUEUE_SIZE = 100
        private const val TAG = "ASTRA_VOICE"
    }

    private val vadEngine = SileroVadEngine(context)
    private val sttEngine = IndicConformerSttEngine(context)
    private val translationEngine = NllbTranslationEngine(context)
    private val ttsEngine = MmsVitsTtsEngine(context)

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var recordingJob: Job? = null
    private var playbackJob: Job? = null
    private var sttRecordJob: Job? = null
    private var sttAudioRecord: AudioRecord? = null

    private val playbackQueue = ConcurrentLinkedQueue<ByteArray>()
    private val isRecording = AtomicBoolean(false)
    private val isPlaying = AtomicBoolean(false)
    private val isSttActive = AtomicBoolean(false)

    @Volatile
    var preferredLanguage: Language = Language.HINDI

    @Volatile
    private var currentTranscript: String = ""
    private var recognitionDeferred: CompletableDeferred<String>? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO)

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

            var sttSize = 0L
            var sttExists = false
            try {
                context.assets.open("models/stt/model.int8.onnx").use {
                    sttSize = it.available().toLong()
                    sttExists = true
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

            AstraLog.i(TAG, "ASTRA_VOICE: ================ AI ASSET AUDIT ================")
            AstraLog.i(TAG, "ASTRA_VOICE: VAD initialized: name=Silero VAD v5, exists=$vadExists, size=$vadSize bytes")
            AstraLog.i(TAG, "ASTRA_VOICE: STT initialized: name=IndicConformer INT8, exists=$sttExists, size=$sttSize bytes")
            AstraLog.i(TAG, "ASTRA_VOICE: STT TOKENS: exists=$tokensExists, size=$tokensSize bytes")
            AstraLog.i(TAG, "ASTRA_VOICE: MT MODEL: name=NLLB-200 Offline Translation Engine, languages=10 Indic")
            AstraLog.i(TAG, "ASTRA_VOICE: TTS MODEL: name=MMS-TTS (VITS) Multi-Language Engine, cacheSize=3")
            AstraLog.i(TAG, "ASTRA_VOICE: =================================================")
        } catch (e: Exception) {
            AstraLog.w(TAG, "ASTRA_VOICE: Asset audit warning: ${e.message}")
        }
    }

    // ==========================================
    // AUDIO MESH RECORDING (8kHz ADPCM for BLE)
    // ==========================================

    @SuppressLint("MissingPermission")
    fun startRecording(onChunk: (ByteArray) -> Unit) {
        if (isRecording.getAndSet(true)) return

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_IN,
            AUDIO_ENCODING
        ).coerceAtLeast(BYTES_PER_FRAME * 2)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_IN,
                AUDIO_ENCODING,
                bufferSize
            )

            audioRecord?.startRecording()
            AstraLog.d(TAG, "ASTRA_VOICE: microphone started (mesh recording)")

            recordingJob = scope.launch {
                val pcmFrame = ByteArray(BYTES_PER_FRAME)
                while (isActive && isRecording.get()) {
                    val read = audioRecord?.read(pcmFrame, 0, BYTES_PER_FRAME) ?: -1
                    if (read == BYTES_PER_FRAME) {
                        val rms = calculateRms(pcmFrame)
                        if (rms >= SILENCE_THRESHOLD_RMS) {
                            val adpcmChunk = AudioCodec.encodeAdpcm(pcmFrame)
                            onChunk(adpcmChunk)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AstraLog.e(TAG, "ERROR starting microphone recording", e)
            isRecording.set(false)
        }
    }

    fun stopRecording() {
        isRecording.set(false)
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            AstraLog.w(TAG, "Warning releasing AudioRecord: ${e.message}")
        }
        audioRecord = null
    }

    // ==========================================
    // AUDIO MESH PLAYBACK (8kHz ADPCM from BLE)
    // ==========================================

    fun playAudioChunk(adpcmChunk: ByteArray) {
        if (adpcmChunk.isEmpty()) return

        val pcmData = AudioCodec.decodeAdpcm(adpcmChunk)
        if (playbackQueue.size < MAX_PLAYBACK_QUEUE_SIZE) {
            playbackQueue.offer(pcmData)
        }

        if (!isPlaying.getAndSet(true)) {
            startAudioTrackPlayback()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAudioTrackPlayback() {
        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_OUT,
            AUDIO_ENCODING
        ).coerceAtLeast(BYTES_PER_FRAME * 4)

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setLegacyStreamType(AudioManager.STREAM_VOICE_CALL)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AUDIO_ENCODING)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_OUT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            AstraLog.d(TAG, "ASTRA_VOICE: AudioTrack playback started")

            playbackJob = scope.launch {
                while (isActive && isPlaying.get()) {
                    val chunk = playbackQueue.poll()
                    if (chunk != null) {
                        audioTrack?.write(chunk, 0, chunk.size)
                    } else {
                        kotlinx.coroutines.delay(10)
                        if (playbackQueue.isEmpty()) {
                            isPlaying.set(false)
                            break
                        }
                    }
                }
                cleanupAudioTrack()
            }
        } catch (e: Exception) {
            AstraLog.e(TAG, "ERROR starting AudioTrack", e)
            isPlaying.set(false)
            cleanupAudioTrack()
        }
    }

    fun stopPlayback() {
        isPlaying.set(false)
        playbackJob?.cancel()
        playbackJob = null
        playbackQueue.clear()
        cleanupAudioTrack()
    }

    private fun cleanupAudioTrack() {
        try {
            audioTrack?.stop()
        } catch (e: Exception) {
            AstraLog.w(TAG, "Warning stopping AudioTrack: ${e.message}")
        }
        try {
            audioTrack?.release()
        } catch (e: Exception) {
            AstraLog.w(TAG, "Warning releasing AudioTrack: ${e.message}")
        }
        audioTrack = null
    }

    // ==========================================
    // OFFLINE STT & VAD (SILERO + INDIC-CONFORMER)
    // ==========================================

    @SuppressLint("MissingPermission")
    fun startStt(
        language: Language = Language.HINDI,
        onRmsChanged: ((Int) -> Unit)? = null,
        onResult: (String) -> Unit
    ) {
        if (isSttActive.getAndSet(true)) {
            AstraLog.w(TAG, "ASTRA_VOICE: startStt called but STT is already active")
            return
        }

        currentTranscript = ""
        val deferred = CompletableDeferred<String>()
        recognitionDeferred = deferred
        preferredLanguage = language
        vadEngine.resetState()
        sttEngine.reset()

        AstraLog.d(TAG, "ASTRA_VOICE: microphone started (STT capture lang=${language.name})")

        val bufferSize = AudioRecord.getMinBufferSize(
            SttConfig.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(SttConfig.FRAME_SIZE_SAMPLES * 2 * 4)

        try {
            sttAudioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SttConfig.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (sttAudioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                AstraLog.e(TAG, "ASTRA_VOICE: ERROR STT AudioRecord init failed")
                isSttActive.set(false)
                deferred.complete("")
                return
            }

            sttAudioRecord?.startRecording()
            sttRecordJob = scope.launch {
                val shortBuffer = ShortArray(SttConfig.FRAME_SIZE_SAMPLES)
                val floatBuffer = FloatArray(SttConfig.FRAME_SIZE_SAMPLES)

                while (isActive && isSttActive.get()) {
                    val read = sttAudioRecord?.read(shortBuffer, 0, SttConfig.FRAME_SIZE_SAMPLES) ?: -1
                    if (read == SttConfig.FRAME_SIZE_SAMPLES) {
                        var sum = 0.0
                        for (i in 0 until read) {
                            val sample = shortBuffer[i]
                            sum += sample * sample
                            floatBuffer[i] = sample / 32768.0f
                        }
                        val rms = sqrt(sum / read).toInt()
                        onRmsChanged?.invoke(rms)

                        AstraLog.d(TAG, "ASTRA_VOICE: VAD inference started")
                        val vadEvent = vadEngine.processStreamFrame(
                            floatBuffer,
                            threshold = SttConfig.VAD_THRESHOLD,
                            minSpeechMs = SttConfig.VAD_MIN_SPEECH_MS,
                            minSilenceMs = SttConfig.VAD_MIN_SILENCE_MS,
                            speechPadMs = SttConfig.VAD_SPEECH_PAD_MS
                        )

                        if (vadEvent.isSpeech) {
                            val partial = sttEngine.processChunk(floatBuffer, language)
                            if (partial.isNotBlank() && partial != currentTranscript) {
                                currentTranscript = partial
                                onResult(partial)
                                AstraLog.d(TAG, "ASTRA_VOICE: STT partial = $partial")
                            }
                        }

                        if (vadEvent.eventType == VADEventType.SPEECH_END) {
                            val finalResult = sttEngine.finalize(language)
                            if (finalResult.isNotBlank()) {
                                currentTranscript = finalResult
                                onResult(finalResult)
                                AstraLog.d(TAG, "ASTRA_VOICE: STT final = $finalResult")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AstraLog.e(TAG, "ASTRA_VOICE: Error initializing offline STT: ${e.message}", e)
            isSttActive.set(false)
            deferred.complete("")
            cleanupSttRecord()
        }
    }

    suspend fun stopSttAndAwaitResult(timeoutMs: Long = 1200L): String {
        isSttActive.set(false)
        cleanupSttRecord()

        val finalResult = sttEngine.finalize(preferredLanguage)
        if (finalResult.isNotBlank()) {
            currentTranscript = finalResult
            AstraLog.d(TAG, "ASTRA_VOICE: STT final = $finalResult")
        } else {
            AstraLog.w(TAG, "ASTRA_VOICE: STT completed with empty transcript")
        }
        recognitionDeferred?.complete(currentTranscript)

        sttRecordJob?.cancel()
        sttRecordJob = null

        return currentTranscript.trim()
    }

    fun stopStt() {
        isSttActive.set(false)
        cleanupSttRecord()
        sttRecordJob?.cancel()
        sttRecordJob = null
        recognitionDeferred?.complete(currentTranscript)
    }

    private fun cleanupSttRecord() {
        try {
            sttAudioRecord?.stop()
        } catch (_: Exception) {}
        try {
            sttAudioRecord?.release()
        } catch (_: Exception) {}
        sttAudioRecord = null
    }

    // ==========================================
    // OFFLINE TRANSLATION & TTS PLAYBACK
    // ==========================================

    fun translateText(text: String, sourceLang: Language, targetLang: Language): String {
        AstraLog.d(TAG, "ASTRA_VOICE: language = ${sourceLang.name} -> ${targetLang.name}")
        AstraLog.d(TAG, "ASTRA_VOICE: translation started")
        val result = translationEngine.translate(text, sourceLang, targetLang)
        AstraLog.d(TAG, "ASTRA_VOICE: translation result = $result")
        return result
    }

    fun speakText(
        text: String,
        language: Language = Language.ENGLISH,
        isEmergency: Boolean = false,
        onDone: (() -> Unit)? = null
    ) {
        val cleanText = text
            .replace(Regex("^\\[Voice Note[^\\]]*\\]:?\\s*"), "")
            .replace(Regex("^\\[ALERT[^\\]]*\\]:?\\s*"), "")
            .replace(Regex("^\\[SOS[^\\]]*\\]:?\\s*"), "")
            .trim()
        if (cleanText.isBlank()) {
            onDone?.invoke()
            return
        }

        AstraLog.d(TAG, "ASTRA_VOICE: TTS model loaded = MMS-TTS (${language.name})")
        AstraLog.d(TAG, "ASTRA_VOICE: TTS inference started for '$cleanText'")

        if (isEmergency) {
            triggerEmergencyVibration()
            playEmergencyAlertSiren()
        }

        scope.launch {
            ttsEngine.synthesizeAndPlay(cleanText, language, isEmergency) {
                AstraLog.d(TAG, "ASTRA_VOICE: AudioTrack playback started (TTS completed)")
                onDone?.invoke()
            }
        }
    }

    fun triggerEmergencyVibration() {
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
            AstraLog.w(TAG, "Vibration failed: ${e.message}")
        }
    }

    fun playEmergencyAlertSiren() {
        try {
            val toneGenerator = android.media.ToneGenerator(AudioManager.STREAM_ALARM, 100)
            toneGenerator.startTone(android.media.ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1200)
            mainHandler.postDelayed({
                try {
                    toneGenerator.release()
                } catch (_: Exception) {}
            }, 1300)
        } catch (e: Exception) {
            AstraLog.w(TAG, "Failed to play emergency alert tone: ${e.message}")
        }
    }

    override suspend fun synthesizeAndPlay(
        text: String,
        language: Language,
        isEmergency: Boolean,
        onDone: (() -> Unit)?
    ) {
        speakText(text, language, isEmergency, onDone)
    }

    fun shutdown() {
        stopRecording()
        stopPlayback()
        stopStt()
        ttsEngine.stopPlayback()
        vadEngine.release()
        sttEngine.release()
        translationEngine.release()
        ttsEngine.release()
    }

    private fun calculateRms(pcmData: ByteArray): Int {
        var sum = 0.0
        val numSamples = pcmData.size / 2
        for (i in 0 until numSamples) {
            val sample = (pcmData[i * 2].toInt() and 0xFF) or (pcmData[i * 2 + 1].toInt() shl 8)
            val shortSample = sample.toShort()
            sum += shortSample * shortSample
        }
        return sqrt(sum / numSamples).toInt()
    }
}
