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
import kotlin.math.max
import kotlin.math.min

/**
 * On-Device MMS-TTS (VITS) Neural Text-to-Speech Engine using ONNX Runtime Mobile.
 * Synthesizes 16kHz mono PCM16 audio in pure offline mode across 10 Indic languages.
 * Implements bounded LRU memory caching (max 3 languages active simultaneously).
 */
class MmsVitsTtsEngine(
    private val context: Context,
    private val maxCacheSize: Int = SttConfig.TTS_MAX_CACHE_SIZE
) {
    private val tag = "MmsVitsTtsEngine"
    private var ortEnv: OrtEnvironment? = null

    // LRU Cache for active language sessions with automatic session closure
    private val sessionCache = object : LinkedHashMap<Language, OrtSession>(maxCacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Language, OrtSession>?): Boolean {
            if (size > maxCacheSize && eldest != null) {
                try {
                    eldest.value.close()
                    AstraLog.d(tag, "Evicted and closed MMS-TTS session for ${eldest.key.name}")
                } catch (_: Exception) {}
                return true
            }
            return false
        }
    }

    // Vocabulary mappings: Language -> (Character/Token -> Token ID)
    private val vocabCache = mutableMapOf<Language, Map<String, Int>>()

    private var currentAudioTrack: AudioTrack? = null
    private val audioTrackLock = Any()

    init {
        try {
            ortEnv = OrtEnvironment.getEnvironment()
        } catch (e: Exception) {
            AstraLog.w(tag, "OrtEnvironment initialization failure: ${e.message}")
        }
    }

    private fun getOrLoadVocab(language: Language): Map<String, Int> {
        vocabCache[language]?.let { return it }

        val vocabPath = "models/tts/${language.code}/vocab.json"
        val map = mutableMapOf<String, Int>()
        try {
            val jsonString = context.assets.open(vocabPath).use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
            }
            val jsonObject = JSONObject(jsonString)
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val id = jsonObject.getInt(key)
                map[key] = id
            }
            vocabCache[language] = map
            AstraLog.d(tag, "Loaded ${map.size} vocab tokens for MMS-TTS ${language.name}")
        } catch (e: Exception) {
            AstraLog.w(tag, "Failed to load vocab from $vocabPath: ${e.message}")
        }
        return map
    }

    private fun getOrCreateSession(language: Language): OrtSession? {
        synchronized(sessionCache) {
            sessionCache[language]?.let { return it }

            val env = ortEnv ?: return null
            val modelPath = "models/tts/${language.code}/model.onnx"
            try {
                if (ModelAssetLoader.assetExists(context, modelPath)) {
                    val modelFile = ModelAssetLoader.getOrExtractAssetFile(context, modelPath)
                    val options = OrtSession.SessionOptions().apply {
                        setIntraOpNumThreads(2)
                    }
                    val session = env.createSession(modelFile.absolutePath, options)
                    sessionCache[language] = session
                    AstraLog.i(tag, "Loaded MMS-TTS ONNX session for ${language.name} from '${modelFile.absolutePath}' (${modelFile.length()} bytes)")
                    return session
                }
            } catch (e: Exception) {
                AstraLog.w(tag, "MMS-TTS model load failure for ${language.code}: ${e.message}")
            }
            return null
        }
    }

    /**
     * Synthesizes and plays out text incrementally across clause boundaries.
     */
    suspend fun synthesizeAndPlay(
        text: String,
        language: Language = Language.HINDI,
        isEmergency: Boolean = false,
        onDone: (() -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val normalized = TTSNormalizer.normalizeForTTS(text, language)
        if (normalized.isBlank()) {
            onDone?.invoke()
            return@withContext
        }

        val chunks = LinguisticChunker.chunkText(normalized, language)
        AstraLog.d(tag, "Synthesizing text in ${chunks.size} linguistic chunks for lang=${language.name}")

        initAudioTrack(isEmergency)

        try {
            for (chunk in chunks) {
                val pcmAudio = synthesizeChunkToPcm(chunk, language)
                if (pcmAudio.isNotEmpty()) {
                    playPcmChunk(pcmAudio)
                }
            }
        } catch (e: Exception) {
            AstraLog.e(tag, "Error during TTS playback: ${e.message}", e)
        } finally {
            stopPlayback()
            onDone?.invoke()
        }
    }

    /**
     * Synthesize a text chunk to 16kHz PCM audio bytes.
     */
    fun synthesizeChunkToPcm(chunkText: String, language: Language): ByteArray {
        val session = getOrCreateSession(language)
        val env = ortEnv
        val vocab = getOrLoadVocab(language)

        if (session != null && env != null && vocab.isNotEmpty()) {
            try {
                // MMS-TTS tokenization: characters mapped with interleaved 0 (<pad>)
                val processedText = if (language == Language.ENGLISH) chunkText.lowercase() else chunkText
                val tokens = mutableListOf<Long>()
                tokens.add(0L)
                for (char in processedText) {
                    val charStr = char.toString()
                    val tokenId = vocab[charStr]
                    if (tokenId != null) {
                        tokens.add(tokenId.toLong())
                        tokens.add(0L)
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

    private fun initAudioTrack(isEmergency: Boolean) {
        synchronized(audioTrackLock) {
            stopPlayback()

            val sampleRate = SttConfig.SAMPLE_RATE
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ) * 2

            val usage = if (isEmergency) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_VOICE_COMMUNICATION
            val contentType = if (isEmergency) AudioAttributes.CONTENT_TYPE_SONIFICATION else AudioAttributes.CONTENT_TYPE_SPEECH
            val streamType = if (isEmergency) AudioManager.STREAM_ALARM else AudioManager.STREAM_VOICE_CALL

            currentAudioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(contentType)
                        .setLegacyStreamType(streamType)
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

            currentAudioTrack?.play()
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

    fun stopPlayback() {
        synchronized(audioTrackLock) {
            try {
                currentAudioTrack?.stop()
                currentAudioTrack?.release()
            } catch (_: Exception) {}
            currentAudioTrack = null
        }
    }

    fun release() {
        stopPlayback()
        synchronized(sessionCache) {
            for (session in sessionCache.values) {
                try { session.close() } catch (_: Exception) {}
            }
            sessionCache.clear()
        }
        try { ortEnv?.close() } catch (_: Exception) {}
        ortEnv = null
    }
}
