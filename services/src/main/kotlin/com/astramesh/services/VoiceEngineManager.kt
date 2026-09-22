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
import com.astramesh.core.OfflineTranslationEngine
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Enterprise Offline Voice & Audio Engine Manager for iTantra / AstraMesh.
 * Fully on-device Edge-ML Pipeline:
 * 1. Silero VAD v5 ONNX Voice Activity Detection (Sub-millisecond, zero GC churn)
 * 2. IndicConformer INT8 ONNX Bounded Streaming Speech-to-Text (On-Demand Lazy Scoped Lifecycle)
 * 3. Rule-based Sub-millisecond Multilingual Emergency Classifier
 * 4. Sub-millisecond Zero-RAM Offline Multilingual Translation Engine
 * 5. MMS-TTS (VITS) Single-Model-Resident On-Device Neural Synthesis Engine
 * 6. Pre-recorded WAV SOS Alert Audio Dispatch
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

        /**
         * Idle grace period before unloading the heavy STT ONNX acoustic model session from memory.
         * Balances rapid consecutive recording flows without thrashing disk I/O while keeping background RAM minimal.
         */
        const val STT_IDLE_TIMEOUT_MS = 45_000L

        /**
         * Idle grace period before unloading the active MMS-TTS neural voice model session.
         */
        const val TTS_IDLE_TIMEOUT_MS = 60_000L

        /**
         * Idle grace period before unloading the NLLB-200 translation models.
         */
        const val NLLB_IDLE_TIMEOUT_MS = 60_000L
    }

    private val vadEngine = SileroVadEngine(context)

    // STT Engine is lazily instantiated and held only during active recording/comm sessions
    @Volatile
    private var sttEngine: IndicConformerSttEngine? = null
    private val sttLock = Any()
    private var sttIdleJob: Job? = null

    // TTS Engine enforces single-model-resident policy in RAM
    private val ttsEngine = MmsVitsTtsEngine(context)
    private var ttsIdleJob: Job? = null

    @Volatile
    private var nllbEngine: NllbTranslationEngine? = null
    private val nllbLock = Any()
    private var nllbIdleJob: Job? = null

    private fun getOrInitNllbEngine(): NllbTranslationEngine {
        return nllbEngine ?: synchronized(nllbLock) {
            nllbIdleJob?.cancel()
            nllbIdleJob = null
            nllbEngine ?: NllbTranslationEngine(context).also { 
                nllbEngine = it
                AstraLog.i(TAG, "ASTRA_VOICE: NLLB Translation Engine initialized on-demand")
            }
        }
    }

    private fun getOrInitSttEngine(): IndicConformerSttEngine {
        synchronized(sttLock) {
            sttIdleJob?.cancel()
            sttIdleJob = null
            return sttEngine ?: IndicConformerSttEngine(context).also {
                sttEngine = it
                AstraLog.i(TAG, "ASTRA_VOICE: IndicConformer STT Engine initialized on-demand for active speech session")
            }
        }
    }

    private fun scheduleSttIdleUnload() {
        synchronized(sttLock) {
            sttIdleJob?.cancel()
            sttIdleJob = scope.launch {
                delay(STT_IDLE_TIMEOUT_MS)
                unloadSttEngine()
            }
        }
    }

    /**
     * Explicitly unloads and releases STT engine resources to reclaim memory.
     */
    fun unloadSttEngine() {
        synchronized(sttLock) {
            sttIdleJob?.cancel()
            sttIdleJob = null
            sttEngine?.let {
                it.release()
                sttEngine = null
                AstraLog.i(TAG, "ASTRA_VOICE: STT engine idle timeout expired ($STT_IDLE_TIMEOUT_MS ms) — IndicConformer INT8 ONNX session released from RAM")
            }
        }
    }

    private fun scheduleNllbIdleUnload() {
        synchronized(nllbLock) {
            nllbIdleJob?.cancel()
            nllbIdleJob = scope.launch {
                delay(NLLB_IDLE_TIMEOUT_MS)
                unloadNllbEngine()
            }
        }
    }

    /**
     * Explicitly unloads and releases NLLB engine resources to reclaim memory.
     */
    fun unloadNllbEngine() {
        synchronized(nllbLock) {
            nllbIdleJob?.cancel()
            nllbIdleJob = null
            nllbEngine?.let {
                it.release()
                nllbEngine = null
                AstraLog.i(TAG, "ASTRA_VOICE: NLLB engine idle timeout expired ($NLLB_IDLE_TIMEOUT_MS ms) — INT8 ONNX session released from RAM")
            }
        }
    }

    private fun scheduleTtsIdleUnload() {
        ttsIdleJob?.cancel()
        ttsIdleJob = scope.launch {
            delay(TTS_IDLE_TIMEOUT_MS)
            ttsEngine.unloadModel()
            AstraLog.i(TAG, "ASTRA_VOICE: TTS engine idle timeout expired ($TTS_IDLE_TIMEOUT_MS ms) — MMS-TTS ONNX session released from RAM")
        }
    }

    /**
     * Preloads the default TTS model into memory.
     */
    fun preloadTts(language: Language = preferredLanguage) {
        ttsIdleJob?.cancel()
        ttsEngine.preload(language)
    }

    override fun preload(language: Language) {
        preloadTts(language)
    }

    override fun setPreferredSpeechLanguage(language: Language) {
        preferredLanguage = language
    }

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
            AstraLog.i(TAG, "ASTRA_VOICE: STT config: name=IndicConformer INT8 (On-Demand Lazy), exists=$sttExists, size=$sttSize bytes")
            AstraLog.i(TAG, "ASTRA_VOICE: STT TOKENS: exists=$tokensExists, size=$tokensSize bytes")
            AstraLog.i(TAG, "ASTRA_VOICE: MT ENGINE: name=Offline Multilingual Rule Engine (Zero-RAM), languages=10 Indic")
            AstraLog.i(TAG, "ASTRA_VOICE: TTS MODEL: name=MMS-TTS (VITS) Single-Model-Resident, languages=10 Indic")
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

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                AstraLog.e(TAG, "Failed to initialize AudioRecord")
                isRecording.set(false)
                return
            }

            audioRecord?.startRecording()
            AstraLog.d(TAG, "ASTRA_VOICE: AudioRecord started (8kHz mono)")

            recordingJob = scope.launch {
                val pcmBuffer = ByteArray(BYTES_PER_FRAME)
                var silentFrames = 0

                while (isActive && isRecording.get()) {
                    val read = audioRecord?.read(pcmBuffer, 0, BYTES_PER_FRAME) ?: -1
                    if (read == BYTES_PER_FRAME) {
                        val rms = calculateRms(pcmBuffer)
                        if (rms < SILENCE_THRESHOLD_RMS) {
                            silentFrames++
                            if (silentFrames > 5) continue
                        } else {
                            silentFrames = 0
                        }

                        val adpcmChunk = AudioCodec.encodeAdpcm(pcmBuffer)
                        onChunk(adpcmChunk)
                    }
                }
            }
        } catch (e: Exception) {
            AstraLog.e(TAG, "ERROR starting AudioRecord", e)
            isRecording.set(false)
            cleanupAudioRecord()
        }
    }

    fun stopRecording() {
        isRecording.set(false)
        recordingJob?.cancel()
        recordingJob = null
        cleanupAudioRecord()
    }

    private fun cleanupAudioRecord() {
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            AstraLog.w(TAG, "Warning stopping AudioRecord: ${e.message}")
        }
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            AstraLog.w(TAG, "Warning releasing AudioRecord: ${e.message}")
        }
        audioRecord = null
    }

    // ==========================================
    // AUDIO MESH PLAYBACK (8kHz ADPCM for BLE)
    // ==========================================

    fun playAudioChunk(adpcmData: ByteArray) {
        if (playbackQueue.size >= MAX_PLAYBACK_QUEUE_SIZE) {
            playbackQueue.poll()
        }
        val pcmData = AudioCodec.decodeAdpcm(adpcmData)
        playbackQueue.offer(pcmData)

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
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL)
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
                        kotlinx.coroutines.delay(20)
                        if (playbackQueue.isEmpty()) {
                            kotlinx.coroutines.delay(150)
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

    /**
     * Starts offline streaming Speech-to-Text with Silero VAD segmentation.
     * Initializes the IndicConformer STT engine on demand if not already resident.
     *
     * @param language Target language for STT decoding
     * @param onRmsChanged Real-time mic volume level (for UI visualizers)
     * @param onPartialResult Live partial transcript updates during speech (strictly for UI captioning)
     * @param onFinalResult Completed sentence transcript fired immediately on VAD SPEECH_END (for mesh transmission)
     * @param onResult Backward-compatible generic result callback
     */
    // NOTE: There is a normal, expected ~250-300ms gap between a [UI_EVENT] MIC_PRESS log and
    // the corresponding [MODEL_LIFECYCLE] LOAD_START for STT, because Silero VAD buffers ~256ms
    // of audio (8 chunks of 40ms PCM frames) before the first partial decode triggers ONNX
    // session creation. This is correct behavior, not a bug — do not "fix" this gap.
    @SuppressLint("MissingPermission")
    fun startStt(
        language: Language = Language.HINDI,
        onRmsChanged: ((Int) -> Unit)? = null,
        onPartialResult: ((String) -> Unit)? = null,
        onFinalResult: ((String) -> Unit)? = null,
        onResult: ((String) -> Unit)? = null
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

        val engine = getOrInitSttEngine()
        engine.reset()

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
                AstraLog.e(TAG, "ASTRA_VOICE: AudioRecord init failed for STT")
                isSttActive.set(false)
                deferred.complete("")
                scheduleSttIdleUnload()
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

                        val vadEvent = vadEngine.processStreamFrame(
                            floatBuffer,
                            threshold = SttConfig.VAD_THRESHOLD,
                            minSpeechMs = SttConfig.VAD_MIN_SPEECH_MS,
                            minSilenceMs = SttConfig.VAD_MIN_SILENCE_MS,
                            speechPadMs = SttConfig.VAD_SPEECH_PAD_MS
                        )

                        if (vadEvent.isSpeech) {
                            val activeStt = sttEngine ?: getOrInitSttEngine()
                            val partial = activeStt.processChunk(floatBuffer, language)
                            if (partial.isNotBlank() && partial != currentTranscript) {
                                currentTranscript = partial
                                onPartialResult?.invoke(partial)
                                onResult?.invoke(partial)
                                AstraLog.d(TAG, "ASTRA_VOICE: STT partial = $partial")
                            }
                        }

                        // Event-driven immediate sentence completion on VAD SPEECH_END
                        if (vadEvent.eventType == VADEventType.SPEECH_END) {
                            val activeStt = sttEngine ?: getOrInitSttEngine()
                            val finalResult = activeStt.finalize(language)
                            if (finalResult.isNotBlank()) {
                                currentTranscript = finalResult
                                AstraLog.d(TAG, "[STT] language=${language.name} text=\"$finalResult\"")
                                onFinalResult?.invoke(finalResult)
                                onResult?.invoke(finalResult)
                                AstraLog.d(TAG, "ASTRA_VOICE: STT final = $finalResult (VAD SPEECH_END)")
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
            scheduleSttIdleUnload()
        }
    }

    suspend fun stopSttAndAwaitResult(timeoutMs: Long = 1200L): String {
        isSttActive.set(false)
        cleanupSttRecord()

        val activeStt = sttEngine
        val finalResult = activeStt?.finalize(preferredLanguage) ?: ""
        if (finalResult.isNotBlank()) {
            currentTranscript = finalResult
            AstraLog.d(TAG, "ASTRA_VOICE: STT final = $finalResult")
        } else {
            AstraLog.w(TAG, "ASTRA_VOICE: STT completed with empty transcript")
        }
        recognitionDeferred?.complete(currentTranscript)

        sttRecordJob?.cancel()
        sttRecordJob = null

        scheduleSttIdleUnload()
        return currentTranscript.trim()
    }

    fun stopStt() {
        isSttActive.set(false)
        cleanupSttRecord()
        sttRecordJob?.cancel()
        sttRecordJob = null
        recognitionDeferred?.complete(currentTranscript)
        scheduleSttIdleUnload()
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

    override fun translateText(text: String, sourceLang: Language, targetLang: Language): String {
        if (!com.astramesh.core.TranslationSettings.isTranslationEnabled.value) {
            AstraLog.d(TAG, "ASTRA_VOICE: Translation globally disabled, returning original text")
            return text
        }
        if (sourceLang == targetLang) {
            AstraLog.d(TAG, "ASTRA_VOICE: [TRANSLATION path=\"same_lang\"] ${sourceLang.name} -> ${targetLang.name}")
            return text
        }
        val dictResult = OfflineTranslationEngine.translate(text, sourceLang, targetLang)
        if (dictResult != text) {
            AstraLog.d(TAG, "ASTRA_VOICE: [TRANSLATION path=\"dict\"] ${sourceLang.name} -> ${targetLang.name}: '$text' -> '$dictResult'")
            return dictResult
        }
        // Lazy neural fallback via NLLB-200 INT8 ONNX engine
        val nllb = getOrInitNllbEngine()
        val nllbResult = nllb.translate(text, sourceLang, targetLang)
        val path = if (nllbResult != text && nllbResult.isNotBlank()) "nllb" else "none"
        AstraLog.d(TAG, "ASTRA_VOICE: [TRANSLATION path=\"$path\"] ${sourceLang.name} -> ${targetLang.name}: '$text' -> '$nllbResult'")
        scheduleNllbIdleUnload()
        return nllbResult
    }

    /**
     * Plays pre-recorded emergency beacon audio directly from asset package for the given language.
     * Bypasses TTS synthesis completely for canned SOS beacon alerts.
     */
    override fun playEmergencyBeacon(language: Language) {
        triggerEmergencyVibration()
        playEmergencyAlertSiren()

        val assetPath = "audio/emergency/${language.code}.wav"
        // Emergency beacon audio files are pending from teammate — expected at app/src/main/assets/audio/emergency/<lang>.wav,
        // one per supported language code (bn, en, gu, hi, kn, ml, mr, or, ta, te).
        // Do not fall back to TTS synthesis here per product decision — if the file is missing, this will log a warning
        // and safely no-op without crashing or synthesizing TTS.
        try {
            val afd = context.assets.openFd(assetPath)
            val mediaPlayer = android.media.MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL)
                        .build()
                )
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                setOnCompletionListener { mp ->
                    try { mp.release() } catch (_: Exception) {}
                }
                setOnErrorListener { mp, what, extra ->
                    AstraLog.w(TAG, "Emergency beacon playback error: what=$what extra=$extra")
                    try { mp.release() } catch (_: Exception) {}
                    true
                }
                prepare()
                start()
            }
            AstraLog.i(TAG, "Playing pre-recorded emergency beacon audio for ${language.code} from $assetPath")
        } catch (e: Exception) {
            AstraLog.w(
                TAG,
                "Emergency beacon audio file '$assetPath' could not be loaded: ${e.message}. " +
                "Pending from teammate at app/src/main/assets/audio/emergency/${language.code}.wav. " +
                "Safely no-oping without TTS fallback per product architecture decision."
            )
        }
    }

    /**
     * Synthesizes and plays text via MMS-TTS in lazy single-model resident mode.
     */
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

        AstraLog.d(TAG, "[TTS_INPUT] language=${language.name} text=\"$cleanText\"")
        AstraLog.d(TAG, "[TTS_LANGUAGE] language=${language.name} code=${language.code}")
        AstraLog.d(TAG, "ASTRA_VOICE: TTS model loaded = MMS-TTS (${language.name})")
        AstraLog.d(TAG, "ASTRA_VOICE: TTS inference started for '$cleanText' (isEmergency=$isEmergency)")

        if (isEmergency) {
            triggerEmergencyVibration()
            playEmergencyAlertSiren()
        }

        ttsIdleJob?.cancel()
        scope.launch {
            ttsEngine.synthesizeAndPlay(cleanText, language, isEmergency) {
                AstraLog.d(TAG, "ASTRA_VOICE: AudioTrack playback completed (TTS completed)")
                scheduleTtsIdleUnload()
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
            val toneGenerator = android.media.ToneGenerator(AudioManager.STREAM_MUSIC, 100)
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
        sttIdleJob?.cancel()
        ttsIdleJob?.cancel()
        ttsEngine.stopPlayback()
        vadEngine.release()
        sttEngine?.release()
        sttEngine = null
        ttsEngine.release()
        nllbEngine?.release()
        nllbEngine = null
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
