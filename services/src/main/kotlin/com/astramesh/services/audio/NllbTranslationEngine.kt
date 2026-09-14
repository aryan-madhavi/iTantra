package com.astramesh.services.audio

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.astramesh.common.AstraLog
import com.astramesh.core.Language
import com.astramesh.core.OfflineTranslationEngine
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * On-Device NLLB-200 INT8 Neural Machine Translation Engine.
 * Supports direct translation across all 10 project languages using ONNX Runtime Mobile.
 * Includes deterministic fallback to OfflineTranslationEngine phrasebook and same-language fast-path.
 */
class NllbTranslationEngine(
    private val context: Context,
    private val encoderAssetPath: String = "models/mt/nllb200-int8-onnx/encoder_model_int8.onnx",
    private val decoderAssetPath: String = "models/mt/nllb200-int8-onnx/decoder_model_int8.onnx",
    private val vocabAssetPath: String = "models/mt/nllb200-int8-onnx/shared_vocabulary.txt"
) {
    private val tag = "NllbTranslationEngine"
    private var ortEnv: OrtEnvironment? = null
    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null

    // NLLB Token mapping: Token ID <-> Token string
    private val idToToken = mutableMapOf<Long, String>()
    private val tokenToId = mutableMapOf<String, Long>()

    // NLLB Language Code mapping
    private val languageTokenIds = mapOf(
        Language.HINDI to 256047L,
        Language.BENGALI to 256015L,
        Language.TELUGU to 256119L,
        Language.MARATHI to 256077L,
        Language.TAMIL to 256118L,
        Language.GUJARATI to 256043L,
        Language.KANNADA to 256057L,
        Language.MALAYALAM to 256073L,
        Language.ODIA to 256089L,
        Language.ENGLISH to 256027L
    )

    private val eosTokenId = 2L
    private val padTokenId = 1L
    private val maxNewTokens = 64

    val isInitialized: Boolean
        get() = encoderSession != null && decoderSession != null && idToToken.isNotEmpty()

    init {
        loadVocabulary()
        initializeSessions()
    }

    private fun loadVocabulary() {
        try {
            context.assets.open(vocabAssetPath).use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).useLines { lines ->
                    var idx = 0L
                    for (line in lines) {
                        val token = line.trimEnd('\r', '\n')
                        idToToken[idx] = token
                        tokenToId[token] = idx
                        idx++
                    }
                }
            }
            AstraLog.i(tag, "Loaded ${idToToken.size} vocabulary tokens for NLLB-200")
        } catch (e: Exception) {
            AstraLog.w(tag, "Failed to load NLLB vocabulary from $vocabAssetPath: ${e.message}")
        }
    }

    private fun initializeSessions() {
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            val env = ortEnv ?: return

            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
            }

            if (ModelAssetLoader.assetExists(context, encoderAssetPath)) {
                val encFile = ModelAssetLoader.getOrExtractAssetFile(context, encoderAssetPath)
                encoderSession = env.createSession(encFile.absolutePath, options)
                AstraLog.i(tag, "NLLB-200 INT8 Encoder Session loaded from '${encFile.absolutePath}' (${encFile.length()} bytes)")
            }

            if (ModelAssetLoader.assetExists(context, decoderAssetPath)) {
                val decFile = ModelAssetLoader.getOrExtractAssetFile(context, decoderAssetPath)
                decoderSession = env.createSession(decFile.absolutePath, options)
                AstraLog.i(tag, "NLLB-200 INT8 Decoder Session loaded from '${decFile.absolutePath}' (${decFile.length()} bytes)")
            }
        } catch (e: Exception) {
            AstraLog.w(tag, "NLLB-200 ONNX initialization notice: ${e.message}")
        }
    }

    fun getLanguageTokenId(language: Language): Long {
        return languageTokenIds[language] ?: 256047L
    }

    /**
     * Translates input text between any pair of the 10 canonical languages.
     * Enforces strict 0.00ms same-language fast path.
     */
    fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""

        // Same-language optimization: Zero latency, exact transcript preserved (0.00ms bypass)
        if (sourceLang == targetLang) {
            return trimmed
        }

        // 1. Deterministic phrasebook lookup
        val phrasebookResult = OfflineTranslationEngine.translate(trimmed, sourceLang, targetLang)
        if (phrasebookResult != trimmed) {
            return phrasebookResult
        }

        // 2. Neural NLLB-200 Encoder-Decoder Translation
        val enc = encoderSession
        val dec = decoderSession
        val env = ortEnv

        if (enc != null && dec != null && env != null && idToToken.isNotEmpty()) {
            try {
                val neuralResult = generateNeuralTranslation(trimmed, sourceLang, targetLang, enc, dec, env)
                if (neuralResult.isNotBlank()) {
                    return neuralResult
                }
            } catch (e: Exception) {
                AstraLog.w(tag, "Neural generation error: ${e.message}")
            }
        }

        return phrasebookResult
    }

    private fun generateNeuralTranslation(
        text: String,
        sourceLang: Language,
        targetLang: Language,
        encSession: OrtSession,
        decSession: OrtSession,
        env: OrtEnvironment
    ): String {
        val srcTokenId = getLanguageTokenId(sourceLang)
        val tgtTokenId = getLanguageTokenId(targetLang)

        // Tokenize source sequence: [src_lang_id, tokens..., eos_token_id]
        val tokenIds = mutableListOf<Long>()
        tokenIds.add(srcTokenId)

        val words = text.split(Regex("\\s+"))
        for (word in words) {
            val spaceWord = " $word"
            val directId = tokenToId[spaceWord] ?: tokenToId[word]
            if (directId != null) {
                tokenIds.add(directId)
            } else {
                var first = true
                for (char in word) {
                    val piece = if (first) " $char" else char.toString()
                    val pieceId = tokenToId[piece] ?: tokenToId[char.toString()] ?: 3L
                    tokenIds.add(pieceId)
                    first = false
                }
            }
        }
        tokenIds.add(eosTokenId)

        val seqLen = tokenIds.size.toLong()
        val inputIdsTensor = OnnxTensor.createTensor(
            env,
            LongBuffer.wrap(tokenIds.toLongArray()),
            longArrayOf(1, seqLen)
        )
        val attentionMaskTensor = OnnxTensor.createTensor(
            env,
            LongBuffer.wrap(LongArray(tokenIds.size) { 1L }),
            longArrayOf(1, seqLen)
        )

        // Run Encoder: -> last_hidden_state [1, seqLen, 1024]
        val encInputs = mapOf(
            "input_ids" to inputIdsTensor,
            "attention_mask" to attentionMaskTensor
        )
        val encResults = encSession.run(encInputs)
        val lastHiddenTensor = encResults.get(0) as OnnxTensor
        val hiddenInfo = lastHiddenTensor.info
        val hiddenShape = hiddenInfo.shape // [1, seqLen, 1024]
        val hiddenBuf = lastHiddenTensor.floatBuffer
        val hiddenArray = FloatArray(hiddenBuf.remaining())
        hiddenBuf.get(hiddenArray)

        inputIdsTensor.close()
        encResults.close()

        // Autoregressive Decoder generation: Starts with [eos_token_id, target_lang_id]
        val generatedIds = mutableListOf<Long>(eosTokenId, tgtTokenId)

        for (step in 1..maxNewTokens) {
            val decSeqLen = generatedIds.size.toLong()
            val decInputIdsTensor = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(generatedIds.toLongArray()),
                longArrayOf(1, decSeqLen)
            )
            val encHiddenTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(hiddenArray),
                hiddenShape
            )
            val encMaskTensor = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(LongArray(tokenIds.size) { 1L }),
                longArrayOf(1, seqLen)
            )

            val decInputs = mapOf(
                "encoder_attention_mask" to encMaskTensor,
                "input_ids" to decInputIdsTensor,
                "encoder_hidden_states" to encHiddenTensor
            )

            val decResults = decSession.run(decInputs)
            val logitsTensor = decResults.get(0) as OnnxTensor
            val logitsShape = logitsTensor.info.shape // [1, decSeqLen, 256206]
            val vocabSize = logitsShape[2].toInt()
            val logitsBuf = logitsTensor.floatBuffer

            // Last step position logits
            val lastPosOffset = (decSeqLen - 1).toInt() * vocabSize
            var maxLogit = Float.NEGATIVE_INFINITY
            var bestId = eosTokenId

            for (v in 0 until vocabSize) {
                val logit = logitsBuf.get(lastPosOffset + v)
                if (logit > maxLogit) {
                    maxLogit = logit
                    bestId = v.toLong()
                }
            }

            decInputIdsTensor.close()
            encHiddenTensor.close()
            encMaskTensor.close()
            decResults.close()

            if (bestId == eosTokenId) {
                break
            }
            generatedIds.add(bestId)
        }

        attentionMaskTensor.close()

        // Decode generated IDs back to text (skipping initial [EOS, target_lang_id])
        val resultSb = StringBuilder()
        for (i in 2 until generatedIds.size) {
            val id = generatedIds[i]
            val piece = idToToken[id] ?: ""
            if (piece.startsWith(" ")) {
                if (resultSb.isNotEmpty()) resultSb.append(" ")
                resultSb.append(piece.removePrefix(" "))
            } else {
                resultSb.append(piece)
            }
        }

        return resultSb.toString().trim()
    }

    fun release() {
        try {
            encoderSession?.close()
            decoderSession?.close()
            ortEnv?.close()
        } catch (_: Exception) {}
        encoderSession = null
        decoderSession = null
        ortEnv = null
    }
}
