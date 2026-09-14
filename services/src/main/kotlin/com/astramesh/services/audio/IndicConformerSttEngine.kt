package com.astramesh.services.audio

import android.content.Context
import com.astramesh.common.AstraLog
import com.astramesh.core.Language
import com.astramesh.core.SttConfig
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * On-Device IndicConformer INT8 Speech-to-Text Engine.
 * Executes offline neural CTC decoding across 10 project languages using ONNX Runtime Mobile.
 * Extracts 80-bin Log-Mel Spectrogram features [1, 80, time] matching NeMo acoustic preprocessor contract.
 * Features language/script token range filtering during CTC decoding to ensure correct script output.
 */
class IndicConformerSttEngine(
    private val context: Context,
    private val modelAssetPath: String = "models/stt/model.int8.onnx",
    private val tokensAssetPath: String = "models/stt/tokens.txt"
) {
    private val tag = "ASTRA_VOICE"
    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    // Vocab map: Token ID -> Token String
    private val idToToken = mutableMapOf<Int, String>()
    private var blankId = 5632 // Default for <blk> in tokens.txt

    private val accumulatedPcm = mutableListOf<Float>()
    private var currentPartialText = ""
    private var chunkCount = 0

    val isInitialized: Boolean
        get() = ortSession != null && idToToken.isNotEmpty()

    init {
        loadVocabulary()
        initializeSession()
    }

    private fun loadVocabulary() {
        try {
            context.assets.open(tokensAssetPath).use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).useLines { lines ->
                    for (line in lines) {
                        val trimmed = line.trim()
                        if (trimmed.isEmpty()) continue
                        val lastSpace = trimmed.lastIndexOf(" ")
                        if (lastSpace > 0) {
                            val token = trimmed.substring(0, lastSpace)
                            val id = trimmed.substring(lastSpace + 1).toIntOrNull()
                            if (id != null) {
                                idToToken[id] = token
                                if (token == "<blk>" || token == "<blank>" || token == "<pad>") {
                                    blankId = id
                                }
                            }
                        }
                    }
                }
            }
            AstraLog.i(tag, "STT: Loaded ${idToToken.size} tokens from '$tokensAssetPath' (blankId=$blankId)")
        } catch (e: Exception) {
            AstraLog.w(tag, "STT: Failed to load tokens vocabulary: ${e.message}")
        }
    }

    private fun initializeSession() {
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            val modelFile = ModelAssetLoader.getOrExtractAssetFile(context, modelAssetPath)
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
            }
            ortSession = ortEnv?.createSession(modelFile.absolutePath, options)
            AstraLog.i(tag, "STT initialized: IndicConformer INT8 ONNX session loaded from '${modelFile.absolutePath}'")
        } catch (e: Exception) {
            AstraLog.w(tag, "STT initialization failed: ${e.message}")
        }
    }

    fun reset() {
        accumulatedPcm.clear()
        currentPartialText = ""
        chunkCount = 0
    }

    /**
     * Feed audio samples incrementally (float array in [-1.0, 1.0]).
     * Decodes every few chunks for low latency partial feedback.
     */
    fun processChunk(samples: FloatArray, language: Language = Language.HINDI): String {
        for (s in samples) {
            accumulatedPcm.add(s)
        }
        chunkCount++

        // Periodically decode every ~4 chunks (~128ms)
        if (chunkCount % 4 == 0 || chunkCount == 1) {
            currentPartialText = decodeAudio(accumulatedPcm.toFloatArray(), language)
            if (currentPartialText.isNotBlank()) {
                AstraLog.d(tag, "STT partial = $currentPartialText")
            }
        }
        return currentPartialText
    }

    fun finalize(language: Language = Language.HINDI): String {
        val finalTranscript = decodeAudio(accumulatedPcm.toFloatArray(), language)
        reset()
        val result = finalTranscript.ifBlank { currentPartialText }.trim()
        AstraLog.d(tag, "STT final = $result")
        return result
    }

    private fun decodeAudio(samples: FloatArray, language: Language): String {
        if (samples.size < SttConfig.FRAME_SIZE_SAMPLES) return ""

        val session = ortSession
        val env = ortEnv
        if (session == null || env == null) {
            AstraLog.w(tag, "STT: Cannot decode, session/env null")
            return currentPartialText
        }

        try {
            AstraLog.d(tag, "STT inference started: processing ${samples.size} PCM samples for lang=${language.name}")
            val featureOutput = AudioFeatureExtractor.extractFeatures(samples)
            val timeSteps = featureOutput.timeSteps

            val signalTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(featureOutput.features),
                longArrayOf(1, 80, timeSteps.toLong())
            )
            val lengthTensor = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(longArrayOf(timeSteps.toLong())),
                longArrayOf(1)
            )

            val inputs = mapOf(
                "processed_signal" to signalTensor,
                "processed_signal_length" to lengthTensor
            )

            val results = session.run(inputs)
            val logitsTensor = results.get(0) as? OnnxTensor

            val decodedText = if (logitsTensor != null) {
                extractCtcTranscript(logitsTensor, language)
            } else {
                ""
            }

            signalTensor.close()
            lengthTensor.close()
            results.close()

            return decodedText
        } catch (e: Exception) {
            AstraLog.w(tag, "STT decode pass error: ${e.message}")
            return currentPartialText
        }
    }

    /**
     * Checks if a token ID belongs to the script corresponding to the target language.
     */
    private fun isTokenInLanguageScript(tokenId: Int, language: Language): Boolean {
        if (tokenId == blankId || tokenId == 0) return true
        return when (language) {
            Language.BENGALI -> tokenId in 1..511
            Language.GUJARATI -> tokenId in 1281..1534
            Language.KANNADA -> tokenId in 1793..2047
            Language.MALAYALAM -> tokenId in 2561..2815
            Language.ODIA -> tokenId in 3585..3837
            Language.TAMIL -> tokenId in 4865..5114
            Language.TELUGU -> tokenId in 5121..5374
            Language.HINDI, Language.MARATHI, Language.ENGLISH -> (tokenId in 513..1279) || (tokenId in 4609..4863)
        }
    }

    private fun extractCtcTranscript(logitsTensor: OnnxTensor, language: Language): String {
        val info = logitsTensor.info
        val shape = info.shape // e.g. [1, T, 5633]
        val buffer = logitsTensor.floatBuffer

        if (shape.size < 3) return ""
        val timeSteps: Int
        val vocabSize: Int
        val isTimeFirst = shape[1] > shape[2] || shape[2] == idToToken.size.toLong()

        if (isTimeFirst) {
            timeSteps = shape[1].toInt()
            vocabSize = shape[2].toInt()
        } else {
            timeSteps = shape[2].toInt()
            vocabSize = shape[1].toInt()
        }

        val tokens = mutableListOf<Int>()
        var prevToken = -1

        for (t in 0 until timeSteps) {
            var maxVal = Float.NEGATIVE_INFINITY
            var maxIdx = blankId

            for (v in 0 until vocabSize) {
                if (!isTokenInLanguageScript(v, language)) continue

                val flatIdx = if (isTimeFirst) t * vocabSize + v else v * timeSteps + t
                if (flatIdx < buffer.capacity()) {
                    val score = buffer.get(flatIdx)
                    if (score > maxVal) {
                        maxVal = score
                        maxIdx = v
                    }
                }
            }

            // CTC collapse rule: Ignore blank token & consecutive repeated tokens
            if (maxIdx != blankId && maxIdx != prevToken) {
                tokens.add(maxIdx)
            }
            prevToken = maxIdx
        }

        // Assemble text from tokens
        val sb = StringBuilder()
        for (tokenId in tokens) {
            val tokenStr = idToToken[tokenId] ?: ""
            if (tokenStr.startsWith("▁") || tokenStr.startsWith(" ")) {
                if (sb.isNotEmpty()) sb.append(" ")
                sb.append(tokenStr.removePrefix("▁").removePrefix(" "))
            } else {
                sb.append(tokenStr)
            }
        }

        return sb.toString().trim()
    }

    fun release() {
        try {
            ortSession?.close()
            ortEnv?.close()
        } catch (_: Exception) {}
        ortSession = null
        ortEnv = null
    }
}
