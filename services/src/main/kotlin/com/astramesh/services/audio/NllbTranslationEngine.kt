package com.astramesh.services.audio

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.astramesh.common.AstraLog
import com.astramesh.core.Language
import com.astramesh.core.OfflineTranslationEngine
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * On-Device NLLB-200 INT8 Neural Machine Translation Engine.
 * Supports direct translation across all 10 project languages using ONNX Runtime Mobile.
 * Driven by HuggingFace BPE fast-tokenizer  for accurate multilingual subword tokenization.
 * Includes deterministic fallback to OfflineTranslationEngine phrasebook and same-language fast-path.
 */
class NllbTranslationEngine(
    private val context: Context,
    private val encoderAssetPath: String = "models/mt/nllb200-int8-onnx/encoder_model_int8.onnx",
    private val decoderAssetPath: String = "models/mt/nllb200-int8-onnx/decoder_model_int8.onnx",
    private val tokenizerAssetPath: String = "models/mt/nllb200-int8-onnx/tokenizer.json"
) {
    private val tag = "NllbTranslationEngine"
    private var ortEnv: OrtEnvironment? = null
    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null

    // Pure Kotlin NLLB-200 BPE Tokenizer
    var tokenizer: NllbTokenizer? = null
        private set

    // Authoritative NLLB Language Code mapping verified against tokenizer.json and lang_codes.json
    private val languageTokenIds = mapOf(
        Language.HINDI to 256068L,     // hin_Deva
        Language.GUJARATI to 256064L,  // guj_Gujr
        Language.MARATHI to 256116L,   // mar_Deva
        Language.KANNADA to 256083L,   // kan_Knda
        Language.MALAYALAM to 256115L, // mal_Mlym
        Language.TAMIL to 256170L,     // tam_Taml
        Language.TELUGU to 256172L,    // tel_Telu
        Language.ODIA to 256136L,      // ory_Orya
        Language.BENGALI to 256026L,   // ben_Beng
        Language.ENGLISH to 256047L    // eng_Latn
    )

    private val eosTokenId = 2L
    private val padTokenId = 1L
    private val maxNewTokens = 64

    val isInitialized: Boolean
        get() = encoderSession != null && decoderSession != null && tokenizer != null

    init {
        loadTokenizer()
        initializeSessions()
    }

    private fun loadTokenizer() {
        try {
            if (ModelAssetLoader.assetExists(context, tokenizerAssetPath)) {
                context.assets.open(tokenizerAssetPath).use { stream ->
                    tokenizer = NllbTokenizer.fromInputStream(stream)
                }
                AstraLog.i(tag, "Loaded NLLB-200 BPE tokenizer from asset $tokenizerAssetPath")
            } else {
                val file = File(tokenizerAssetPath)
                if (file.exists()) {
                    tokenizer = NllbTokenizer.fromFile(file)
                    AstraLog.i(tag, "Loaded NLLB-200 BPE tokenizer from file ${file.absolutePath}")
                }
            }
        } catch (e: Exception) {
            AstraLog.w(tag, "Failed to load NLLB tokenizer from $tokenizerAssetPath: ${e.message}")
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
        return tokenizer?.getLanguageTokenId(language) ?: languageTokenIds[language] ?: 256047L
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
        val tok = tokenizer

        if (enc != null && dec != null && env != null && tok != null) {
            try {
                val neuralResult = generateNeuralTranslation(trimmed, sourceLang, targetLang, enc, dec, env, tok)
                if (neuralResult.isNotBlank()) {
                    return neuralResult
                }
            } catch (e: Exception) {
                AstraLog.w(tag, "Neural generation error: ${e.message}")
            }
        }

        return phrasebookResult
    }

    /**
     * Core neural translation generation method using real BPE encoding and symmetrical decoding.
     */
    fun generateNeuralTranslation(
        text: String,
        sourceLang: Language,
        targetLang: Language,
        encSession: OrtSession,
        decSession: OrtSession,
        env: OrtEnvironment,
        tok: NllbTokenizer
    ): String {
        val srcTokenId = getLanguageTokenId(sourceLang)
        val tgtTokenId = getLanguageTokenId(targetLang)

        // Tokenize source sequence using real BPE: [src_lang_id, bpe_tokens..., eos_token_id]
        val textTokenIds = tok.encode(text)
        val tokenIds = ArrayList<Long>(textTokenIds.size + 2)
        tokenIds.add(srcTokenId)
        tokenIds.addAll(textTokenIds)
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
        if (generatedIds.size <= 2) return ""
        val generatedTokens = generatedIds.subList(2, generatedIds.size)
        val decodedText = tok.decode(generatedTokens)

        return decodedText
    }

    fun release() {
        try {
            encoderSession?.close()
            decoderSession?.close()
        } catch (_: Exception) {}
        encoderSession = null
        decoderSession = null
        tokenizer = null
    }
}
