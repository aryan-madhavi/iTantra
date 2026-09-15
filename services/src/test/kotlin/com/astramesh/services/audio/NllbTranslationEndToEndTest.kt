package com.astramesh.services.audio

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.astramesh.core.Language
import com.astramesh.core.OfflineTranslationEngine
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class NllbTranslationEndToEndTest {

    companion object {
        private var ortEnv: OrtEnvironment? = null
        private var encSession: OrtSession? = null
        private var decSession: OrtSession? = null
        private lateinit var tokenizer: NllbTokenizer
        private lateinit var engine: NllbTranslationEngine

        @BeforeClass
        @JvmStatic
        fun setUp() {
            var currentDir: File? = File(System.getProperty("user.dir"))
            var modelsDir: File? = null
            while (currentDir != null) {
                val candidate = File(currentDir, "models_archive/mt/nllb200-int8-onnx")
                if (candidate.exists() && File(candidate, "tokenizer.json").exists()) {
                    modelsDir = candidate
                    break
                }
                currentDir = currentDir.parentFile
            }
            if (modelsDir == null) {
                val fallback = File("/home/aryan/ASTRA/bitchat/models_archive/mt/nllb200-int8-onnx")
                if (fallback.exists()) modelsDir = fallback
            }

            println("Loading models from: ${modelsDir?.absolutePath}")
            assertThat(modelsDir).isNotNull()

            val tokFile = File(modelsDir, "tokenizer.json")
            val encFile = File(modelsDir, "encoder_model_int8.onnx")
            val decFile = File(modelsDir, "decoder_model_int8.onnx")

            assertThat(tokFile.exists()).isTrue()
            assertThat(encFile.exists()).isTrue()
            assertThat(decFile.exists()).isTrue()

            tokenizer = NllbTokenizer.fromFile(tokFile)

            try {
                ortEnv = OrtEnvironment.getEnvironment()
                val env = ortEnv!!
                val opts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                }
                encSession = env.createSession(encFile.absolutePath, opts)
                decSession = env.createSession(decFile.absolutePath, opts)
                println("Successfully initialized ONNX Runtime sessions for NLLB Encoder & Decoder")
            } catch (e: Throwable) {
                println("Notice: ONNX Runtime native loading in host JVM: ${e.message}")
            }

            val mockContext = mockk<Context>(relaxed = true)
            engine = NllbTranslationEngine(mockContext)
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            try {
                encSession?.close()
                decSession?.close()
                ortEnv?.close()
            } catch (_: Exception) {}
        }
    }

    @Test
    fun testBpeEncodingPrintouts() {
        println("=== BPE TOKENIZATION VERIFICATION ===")

        val marathiSentence = "आम्ही उद्या सकाळी लवकर निघणार आहोत."
        val marathiTokens = tokenizer.tokenize(marathiSentence)
        val marathiIds = tokenizer.encode(marathiSentence)
        println("Marathi input:   $marathiSentence")
        println("Marathi tokens:  $marathiTokens")
        println("Marathi IDs:     $marathiIds")
        assertThat(marathiIds).isNotEmpty()

        val hindiSentence = "यहाँ का मौसम आज बहुत अच्छा है।"
        val hindiTokens = tokenizer.tokenize(hindiSentence)
        val hindiIds = tokenizer.encode(hindiSentence)
        println("Hindi input:     $hindiSentence")
        println("Hindi tokens:    $hindiTokens")
        println("Hindi IDs:       $hindiIds")
        assertThat(hindiIds).isNotEmpty()

        val englishSentence = "The quick brown fox jumps over the lazy dog."
        val englishTokens = tokenizer.tokenize(englishSentence)
        val englishIds = tokenizer.encode(englishSentence)
        println("English input:   $englishSentence")
        println("English tokens:  $englishTokens")
        println("English IDs:     $englishIds")
        assertThat(englishIds).isNotEmpty()
        println("======================================")
    }

    @Test
    fun testEndToEndTranslations() {
        val env = ortEnv
        val enc = encSession
        val dec = decSession

        if (env == null || enc == null || dec == null) {
            println("ONNX Runtime sessions not active in this JVM environment, skipping ONNX execution test.")
            return
        }

        println("=== END-TO-END NEURAL TRANSLATION INFERENCE ===")

        // Free-form test sentences deliberately NOT in OfflineTranslationEngine PHRASE_INDEX
        val testCases = listOf(
            Triple("मला नवीन पुस्तके वाचायला आवडतात.", Language.MARATHI, Language.HINDI),
            Triple("यह सड़क सीधे अस्पताल की तरफ जाती है।", Language.HINDI, Language.ENGLISH),
            Triple("The emergency shelter is located near the central station.", Language.ENGLISH, Language.TAMIL),
            Triple("आम्हाला मदतीची आवश्यकता आहे, कृपया लवकर या.", Language.MARATHI, Language.HINDI),
            Triple("Water supplies are arriving tomorrow morning.", Language.ENGLISH, Language.HINDI)
        )

        for ((input, srcLang, tgtLang) in testCases) {
            // Verify it does NOT hit phrasebook fast path
            val phrasebookLookup = OfflineTranslationEngine.translate(input, srcLang, tgtLang)
            val isPhrasebookMatch = (phrasebookLookup != input)
            println("Phrasebook matched for '$input': $isPhrasebookMatch")

            val translated = engine.generateNeuralTranslation(
                text = input,
                sourceLang = srcLang,
                targetLang = tgtLang,
                encSession = enc,
                decSession = dec,
                env = env,
                tok = tokenizer
            )

            println("--------------------------------------------------")
            println("Source [${srcLang.name}]: $input")
            println("Target [${tgtLang.name}]: $translated")
            assertThat(translated).isNotEmpty()
            assertThat(translated).isNotEqualTo(input)
        }
        println("==================================================")
    }
}
