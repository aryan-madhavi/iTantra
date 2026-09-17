package com.astramesh.services.audio

import com.astramesh.core.Language
import com.google.common.truth.Truth.assertThat
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class NllbTokenizerTest {

    companion object {
        private lateinit var tokenizer: NllbTokenizer

        @BeforeClass
        @JvmStatic
        fun setUp() {
            // Locate tokenizer.json by traversing upward from current working directory
            var currentDir: File? = File(System.getProperty("user.dir") ?: ".")
            var modelFile: File? = null
            while (currentDir != null) {
                val candidate = File(currentDir, "models_archive/mt/nllb200-int8-onnx/tokenizer.json")
                if (candidate.exists()) {
                    modelFile = candidate
                    break
                }
                currentDir = currentDir.parentFile
            }
            if (modelFile == null || !modelFile.exists()) {
                val fallback = File("/home/aryan/ASTRA/bitchat/models_archive/mt/nllb200-int8-onnx/tokenizer.json")
                if (fallback.exists()) modelFile = fallback
            }

            println("Found tokenizer.json at: ${modelFile?.absolutePath}")
            assertThat(modelFile).isNotNull()
            assertThat(modelFile!!.exists()).isTrue()
            tokenizer = NllbTokenizer.fromFile(modelFile)
        }
    }

    @Test
    fun testVocabularyAndMergesLoaded() {
        assertThat(tokenizer.tokenToId.size).isAtLeast(256000)
        assertThat(tokenizer.bpeRanks.size).isAtLeast(500000)
        assertThat(tokenizer.unkTokenId).isEqualTo(3L)
        assertThat(tokenizer.eosTokenId).isEqualTo(2L)
        assertThat(tokenizer.padTokenId).isEqualTo(1L)
        assertThat(tokenizer.bosTokenId).isEqualTo(0L)
    }

    @Test
    fun testLanguageTokenIds() {
        // Verified against tokenizer.json & lang_codes.json
        assertThat(tokenizer.getLanguageTokenId(Language.HINDI)).isEqualTo(256068L)
        assertThat(tokenizer.getLanguageTokenId(Language.GUJARATI)).isEqualTo(256064L)
        assertThat(tokenizer.getLanguageTokenId(Language.MARATHI)).isEqualTo(256116L)
        assertThat(tokenizer.getLanguageTokenId(Language.KANNADA)).isEqualTo(256083L)
        assertThat(tokenizer.getLanguageTokenId(Language.MALAYALAM)).isEqualTo(256115L)
        assertThat(tokenizer.getLanguageTokenId(Language.TAMIL)).isEqualTo(256170L)
        assertThat(tokenizer.getLanguageTokenId(Language.TELUGU)).isEqualTo(256172L)
        assertThat(tokenizer.getLanguageTokenId(Language.ODIA)).isEqualTo(256136L)
        assertThat(tokenizer.getLanguageTokenId(Language.BENGALI)).isEqualTo(256026L)
        assertThat(tokenizer.getLanguageTokenId(Language.ENGLISH)).isEqualTo(256047L)
    }

    @Test
    fun testEnglishTokenization() {
        val text = "Hello world"
        val tokens = tokenizer.tokenize(text)
        val ids = tokenizer.encode(text)

        println("English tokens: $tokens, ids: $ids")
        assertThat(tokens).containsExactly("▁Hello", "▁world").inOrder()
        assertThat(ids).containsExactly(94124L, 15697L).inOrder()

        val decoded = tokenizer.decode(ids)
        println("English decoded: $decoded")
        assertThat(decoded).isEqualTo("Hello world")
    }

    @Test
    fun testHindiTokenization() {
        val text = "नमस्ते दुनिया"
        val tokens = tokenizer.tokenize(text)
        val ids = tokenizer.encode(text)

        println("Hindi tokens: $tokens, ids: $ids")
        assertThat(tokens).containsExactly("▁नम", "स्ते", "▁दुनिया").inOrder()
        assertThat(ids).containsExactly(138487L, 32014L, 20482L).inOrder()

        val decoded = tokenizer.decode(ids)
        println("Hindi decoded: $decoded")
        assertThat(decoded).isEqualTo("नमस्ते दुनिया")
    }

    @Test
    fun testMarathiTokenization() {
        val text = "कसा आहेस?"
        val tokens = tokenizer.tokenize(text)
        val ids = tokenizer.encode(text)

        println("Marathi tokens: $tokens, ids: $ids")
        assertThat(tokens).containsExactly("▁कसा", "▁आहेस", "?").inOrder()
        assertThat(ids).containsExactly(107199L, 136716L, 248130L).inOrder()

        val decoded = tokenizer.decode(ids)
        println("Marathi decoded: $decoded")
        assertThat(decoded).isEqualTo("कसा आहेस?")
    }

    @Test
    fun testTamilTokenization() {
        val text = "வணக்கம் உலகம்"
        val tokens = tokenizer.tokenize(text)
        val ids = tokenizer.encode(text)

        println("Tamil tokens: $tokens, ids: $ids")
        assertThat(tokens).containsExactly("▁வண", "க்கம்", "▁உலகம்").inOrder()
        assertThat(ids).containsExactly(115457L, 50693L, 214716L).inOrder()

        val decoded = tokenizer.decode(ids)
        println("Tamil decoded: $decoded")
        assertThat(decoded).isEqualTo("வணக்கம் உலகம்")
    }

    @Test
    fun testNormalizationAndSpacing() {
        val raw = "  Hello    world  "
        val tokens = tokenizer.tokenize(raw)
        // Trailing whitespace in Metaspace creates a trailing ▁ token
        assertThat(tokens).containsExactly("▁Hello", "▁world", "▁").inOrder()

        val trimmedTokens = tokenizer.tokenize(raw.trim())
        assertThat(trimmedTokens).containsExactly("▁Hello", "▁world").inOrder()
    }
}
