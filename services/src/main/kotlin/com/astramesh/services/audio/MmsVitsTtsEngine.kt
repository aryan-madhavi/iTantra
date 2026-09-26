package com.astramesh.services.audio

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.astramesh.common.AstraLog
import com.astramesh.core.Language
import com.astramesh.core.LinguisticChunker
import com.astramesh.core.SttConfig
import com.astramesh.core.TTSNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.LongBuffer
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Resolved language tokenizer parameters extracted from tokenizer_config.json / special_tokens_map.json
 * and mapped against that language's vocab.json.
 */
data class TtsLanguageTokenizer(
    val language: Language,
    val charToId: Map<String, Int>,
    val padToken: String,
    val padTokenId: Long,
    val unkToken: String,
    val unkTokenId: Long
)

/**
 * On-Device MMS-TTS (VITS) Neural Text-to-Speech Engine using ONNX Runtime Mobile.
 * Synthesizes 16kHz mono PCM16 audio in pure offline mode across 10 Indic languages.
 *
 * MEMORY FOOTPRINT & LIFECYCLE MANAGEMENT:
 * Enforces strict single-model resident policy in RAM (one active OrtSession at a time).
 * When the language is switched, the previously loaded ONNX session is explicitly closed,
 * nulled, and released before loading the new language's model.
 * All model swaps and inference executions are synchronized on `sessionLock` to ensure
 * in-flight inference requests complete safely before any session disposal.
 */
class MmsVitsTtsEngine(
    private val context: Context
) {
    private val tag = "MmsVitsTtsEngine"
    private var ortEnv: OrtEnvironment? = null

    // Single active ONNX Runtime session and its associated language
    private var activeSession: OrtSession? = null
    private var activeLanguage: Language? = null
    private val sessionLock = Any()

    private val tokenizerCache = mutableMapOf<Language, TtsLanguageTokenizer>()
    private var currentAudioTrack: AudioTrack? = null
    private val audioTrackLock = Any()

    @Volatile
    private var isEmergencyPlaybackActive = false

    init {
        try {
            ortEnv = OrtEnvironment.getEnvironment()
        } catch (e: Exception) {
            AstraLog.w(tag, "MMS-TTS ORT Environment creation failed: ${e.message}")
        }
    }

    /**
     * Resolves and caches the tokenizer metadata for the given language.
     * Extracts "pad_token" and "unk_token" strings from special_tokens_map.json / tokenizer_config.json
     * and looks up their exact numeric IDs in vocab.json.
     */
    fun getOrLoadTokenizer(language: Language): TtsLanguageTokenizer {
        synchronized(tokenizerCache) {
            tokenizerCache[language]?.let { return it }

            val vocabMap = mutableMapOf<String, Int>()
            val vocabPath = "models/tts/${language.code}/vocab.json"
            var padTokenStr = ""
            var unkTokenStr = "<unk>"

            try {
                if (ModelAssetLoader.assetExists(context, vocabPath)) {
                    val jsonStr = context.assets.open(vocabPath).use { stream ->
                        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
                    }
                    val json = JSONObject(jsonStr)
                    for (key in json.keys()) {
                        vocabMap[key] = json.getInt(key)
                    }
                }

                val specialMapPath = "models/tts/${language.code}/special_tokens_map.json"
                val tokConfigPath = "models/tts/${language.code}/tokenizer_config.json"

                if (ModelAssetLoader.assetExists(context, specialMapPath)) {
                    val specStr = context.assets.open(specialMapPath).use { stream ->
                        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
                    }
                    val specJson = JSONObject(specStr)
                    padTokenStr = specJson.optString("pad_token", padTokenStr)
                    unkTokenStr = specJson.optString("unk_token", unkTokenStr)
                }

                if (padTokenStr.isEmpty() && ModelAssetLoader.assetExists(context, tokConfigPath)) {
                    val cfgStr = context.assets.open(tokConfigPath).use { stream ->
                        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
                    }
                    val cfgJson = JSONObject(cfgStr)
                    padTokenStr = cfgJson.optString("pad_token", "")
                    unkTokenStr = cfgJson.optString("unk_token", unkTokenStr)
                }
            } catch (e: Exception) {
                AstraLog.w(tag, "MMS-TTS tokenizer loading failed for ${language.code}: ${e.message}")
            }

            // Resolve pad_token ID from vocabulary (or fallback to 0)
            val padId = (vocabMap[padTokenStr] ?: 0).toLong()
            // Resolve unk_token ID (if missing in vocab, fallback to padTokenId)
            val unkId = (vocabMap[unkTokenStr] ?: padId.toInt()).toLong()

            val tokenizer = TtsLanguageTokenizer(
                language = language,
                charToId = vocabMap,
                padToken = padTokenStr,
                padTokenId = padId,
                unkToken = unkTokenStr,
                unkTokenId = unkId
            )
            tokenizerCache[language] = tokenizer

            AstraLog.i(
                tag,
                "TTS tokenizer resolved [lang=${language.code}]: pad='$padTokenStr'(id=$padId), unk='$unkTokenStr'(id=$unkId), vocabSize=${vocabMap.size}"
            )

            return tokenizer
        }
    }

    /**
     * Gets or creates the ONNX session for the given language.
     * Enforces single-model-resident policy: any existing session for another language
     * is explicitly closed and nulled before creating the new session.
     */
    fun getOrCreateSession(language: Language): OrtSession? {
        synchronized(sessionLock) {
            if (activeLanguage == language && activeSession != null) {
                return activeSession
            }

            // Explicitly close previously loaded ONNX session to prevent RAM ballooning
            activeSession?.let { oldSession ->
                try {
                    oldSession.close()
                    AstraLog.i(tag, "[MODEL_LIFECYCLE] DISPOSE engine=TTS reason=language_switch")
                    AstraLog.i(tag, "MMS-TTS: Explicitly closed and released ONNX session for ${activeLanguage?.name} to enforce single-model RAM footprint")
                } catch (e: Exception) {
                    AstraLog.w(tag, "MMS-TTS: Error closing previous session: ${e.message}")
                }
            }
            activeSession = null
            activeLanguage = null

            val env = ortEnv ?: run {
                try {
                    OrtEnvironment.getEnvironment().also { ortEnv = it }
                } catch (e: Exception) {
                    AstraLog.w(tag, "MMS-TTS ORT Environment creation failed: ${e.message}")
                    null
                }
            } ?: return null

            val modelPath = "models/tts/${language.code}/model.onnx"
            try {
                if (ModelAssetLoader.assetExists(context, modelPath)) {
                    val modelFile = ModelAssetLoader.getOrExtractAssetFile(context, modelPath)
                    val options = OrtSession.SessionOptions().apply {
                        setIntraOpNumThreads(2)
                    }
                    AstraLog.i(tag, "[MODEL_LIFECYCLE] LOAD_START engine=TTS")
                    val session = env.createSession(modelFile.absolutePath, options)
                    activeSession = session
                    activeLanguage = language
                    AstraLog.i(tag, "[MODEL_LIFECYCLE] LOAD_COMPLETE engine=TTS")
                    AstraLog.i(tag, "Loaded MMS-TTS ONNX session for ${language.name} from '${modelFile.absolutePath}' (${modelFile.length()} bytes). Single model resident in RAM.")
                    return session
                } else {
                    AstraLog.w(tag, "MMS-TTS model asset not found: $modelPath")
                }
            } catch (e: Exception) {
                AstraLog.w(tag, "MMS-TTS model load failure for ${language.code}: ${e.message}")
            }
            return null
        }
    }

    /**
     * Preloads the specified language model into memory, releasing any previously loaded model.
     */
    fun preload(language: Language): Boolean {
        return getOrCreateSession(language) != null
    }

    /**
     * Unloads and closes the active model session without closing the ORT environment.
     * Useful for reclaiming memory when TTS is idle.
     */
    fun unloadModel() {
        synchronized(sessionLock) {
            activeSession?.let { session ->
                try {
                    session.close()
                    AstraLog.i(tag, "[MODEL_LIFECYCLE] DISPOSE engine=TTS reason=idle_timeout")
                    AstraLog.i(tag, "MMS-TTS: Unloaded and released active model for ${activeLanguage?.name}")
                } catch (e: Exception) {
                    AstraLog.w(tag, "MMS-TTS: Error unloading model: ${e.message}")
                }
            }
            activeSession = null
            activeLanguage = null
        }
    }

    fun getCurrentLoadedLanguage(): Language? = synchronized(sessionLock) { activeLanguage }

    fun isModelLoaded(language: Language): Boolean = synchronized(sessionLock) {
        activeLanguage == language && activeSession != null
    }

    /**
     * Synthesizes and plays out text incrementally across clause boundaries.
     * Guaranteed non-interruptible for emergency alerts.
     */
    suspend fun synthesizeAndPlay(
        text: String,
        language: Language,
        isEmergency: Boolean = false,
        onDone: (() -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        if (text.isBlank()) {
            onDone?.invoke()
            return@withContext
        }

        // Priority check: Drop normal voice playback if an emergency alert is actively playing
        if (!isEmergency && isEmergencyPlaybackActive) {
            AstraLog.w(tag, "TTS: Dropping normal priority voice message because active emergency alert is playing")
            onDone?.invoke()
            return@withContext
        }

        val normalized = TTSNormalizer.normalizeForTTS(text, language)
        val chunks = LinguisticChunker.chunkText(normalized, language)
        AstraLog.d(tag, "Synthesizing text in ${chunks.size} linguistic chunks for lang=${language.name} (isEmergency=$isEmergency)")

        val trackReady = initAudioTrack(isEmergency)
        if (!trackReady) {
            AstraLog.w(tag, "AudioTrack init skipped due to active emergency priority lock")
            onDone?.invoke()
            return@withContext
        }

        try {
            var totalBytesWritten = 0
            for (chunk in chunks) {
                if (!isEmergency && isEmergencyPlaybackActive) {
                    AstraLog.w(tag, "TTS playback aborted mid-speech by higher-priority emergency message")
                    break
                }
                val pcmAudio = synthesizeChunkToPcm(chunk, language)
                if (pcmAudio.isNotEmpty()) {
                    playPcmChunk(pcmAudio)
                    totalBytesWritten += pcmAudio.size
                }
            }
            if (totalBytesWritten > 0) {
                drainAudioTrack(totalBytesWritten / 2, SttConfig.SAMPLE_RATE)
            }
        } catch (e: Exception) {
            AstraLog.e(tag, "Error during TTS playback: ${e.message}", e)
        } finally {
            stopPlayback()
            if (isEmergency) {
                isEmergencyPlaybackActive = false
            }
            onDone?.invoke()
        }
    }

    private fun drainAudioTrack(totalFramesWritten: Int, sampleRate: Int) {
        val track = currentAudioTrack ?: return
        try {
            val maxWaitMs = ((totalFramesWritten * 1000L) / sampleRate) + 300L
            val startTime = System.currentTimeMillis()
            while (track.playState == AudioTrack.PLAYSTATE_PLAYING &&
                track.playbackHeadPosition < totalFramesWritten &&
                (System.currentTimeMillis() - startTime) < maxWaitMs
            ) {
                Thread.sleep(25)
            }
        } catch (_: Exception) {}
    }

    /**
     * Synthesize a text chunk to 16kHz PCM audio bytes.
     * Guarded by `sessionLock` to ensure model swaps do not close active sessions mid-inference.
     */
    fun synthesizeChunkToPcm(chunkText: String, language: Language): ByteArray {
        synchronized(sessionLock) {
            val session = getOrCreateSession(language)
            val env = ortEnv
            val tokenizer = getOrLoadTokenizer(language)
            val vocab = tokenizer.charToId
            val padId = tokenizer.padTokenId
            val unkId = tokenizer.unkTokenId

            if (session != null && env != null && vocab.isNotEmpty()) {
                try {
                    val startNs = System.nanoTime()

                    // MMS-TTS VITS tokenization: characters mapped with dynamic interleaved padTokenId
                    val processedText = if (language == Language.ENGLISH) chunkText.lowercase() else chunkText
                    val tokens = mutableListOf<Long>()
                    tokens.add(padId)
                    for (i in 0 until processedText.length) {
                        val char = processedText[i]
                        val charStr = char.toString()
                        val tokenId = vocab[charStr]
                        if (tokenId != null) {
                            tokens.add(tokenId.toLong())
                            tokens.add(padId)
                        } else {
                            // Map unrecognized characters to resolved unkTokenId to preserve timing/length
                            tokens.add(unkId)
                            tokens.add(padId)
                        }
                    }

                    if (tokens.size <= 1) return ByteArray(0)

                    val tokenArray = tokens.toLongArray()
                    val seqLen = tokenArray.size.toLong()

                    val inputIdsTensor = OnnxTensor.createTensor(
                        env,
                        LongBuffer.wrap(tokenArray),
                        longArrayOf(1, seqLen)
                    )

                    val maskArray = LongArray(tokenArray.size) { 1L }
                    val attentionMaskTensor = OnnxTensor.createTensor(
                        env,
                        LongBuffer.wrap(maskArray),
                        longArrayOf(1, seqLen)
                    )

                    val inputs = mapOf(
                        "input_ids" to inputIdsTensor,
                        "attention_mask" to attentionMaskTensor
                    )

                    val results = session.run(inputs)
                    val outputTensor = results.get(0) as? OnnxTensor
                    val floatBuffer = outputTensor?.floatBuffer

                    val pcmBytes = if (floatBuffer != null) {
                        val count = floatBuffer.remaining()
                        val floatSamples = FloatArray(count)
                        floatBuffer.get(floatSamples)
                        floatToPcm16(floatSamples)
                    } else {
                        ByteArray(0)
                    }

                    val endNs = System.nanoTime()
                    val durationMs = (endNs - startNs) / 1_000_000.0
                    val audioSamples = pcmBytes.size / 2
                    val audioDurationMs = (audioSamples * 1000.0) / SttConfig.SAMPLE_RATE
                    val rtf = if (audioDurationMs > 0) durationMs / audioDurationMs else 0.0

                    AstraLog.d(
                        tag,
                        "TTS chunk synthesis [lang=${language.code}]: duration=${String.format(Locale.US, "%.1f", durationMs)}ms (audio=${String.format(Locale.US, "%.1f", audioDurationMs)}ms, RTF=${String.format(Locale.US, "%.2f", rtf)})"
                    )

                    inputIdsTensor.close()
                    attentionMaskTensor.close()
                    results.close()

                    return pcmBytes
                } catch (e: Exception) {
                    AstraLog.w(tag, "MMS-TTS inference error for '${language.code}': ${e.message}")
                }
            }

            return ByteArray(0)
        }
    }

    private fun floatToPcm16(floatSamples: FloatArray): ByteArray {
        val pcm = ByteArray(floatSamples.size * 2)
        var idx = 0
        for (sample in floatSamples) {
            val clamped = max(-1.0f, min(1.0f, sample))
            val shortVal = (clamped * 32767.0f).toInt().toShort()
            pcm[idx++] = (shortVal.toInt() and 0xFF).toByte()
            pcm[idx++] = ((shortVal.toInt() shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    private fun initAudioTrack(isEmergency: Boolean): Boolean {
        synchronized(audioTrackLock) {
            if (isEmergencyPlaybackActive && !isEmergency) {
                return false
            }

            stopPlaybackInternal()

            if (isEmergency) {
                isEmergencyPlaybackActive = true
                try {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    val maxMusicVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 100
                    audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusicVol, 0)
                } catch (e: Exception) {
                    AstraLog.w(tag, "Could not set stream music max volume: ${e.message}")
                }
            }

            val sampleRate = SttConfig.SAMPLE_RATE
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ) * 2

            val usage = AudioAttributes.USAGE_MEDIA
            val contentType = if (isEmergency) AudioAttributes.CONTENT_TYPE_SONIFICATION else AudioAttributes.CONTENT_TYPE_SPEECH

            currentAudioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(contentType)
                        .setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            currentAudioTrack?.setVolume(1.0f)
            currentAudioTrack?.play()
            return true
        }
    }

    private fun playPcmChunk(pcmData: ByteArray) {
        synchronized(audioTrackLock) {
            val track = currentAudioTrack ?: return
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                var written = 0
                while (written < pcmData.size) {
                    val res = track.write(pcmData, written, pcmData.size - written)
                    if (res <= 0) break
                    written += res
                }
            }
        }
    }

    private fun stopPlaybackInternal() {
        try {
            currentAudioTrack?.stop()
            currentAudioTrack?.release()
        } catch (_: Exception) {}
        currentAudioTrack = null
    }

    fun stopPlayback() {
        synchronized(audioTrackLock) {
            stopPlaybackInternal()
        }
    }

    fun release() {
        stopPlayback()
        unloadModel()
        try { ortEnv?.close() } catch (_: Exception) {}
        ortEnv = null
        synchronized(tokenizerCache) {
            tokenizerCache.clear()
        }
    }
}
