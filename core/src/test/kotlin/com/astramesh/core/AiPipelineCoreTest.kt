package com.astramesh.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AiPipelineCoreTest {

    @Test
    fun `SttConfig constants match specified values verbatim`() {
        assertThat(SttConfig.WAKE_WORD_SENSITIVITY_THRESHOLD).isEqualTo(0.1f)
        assertThat(SttConfig.COMMAND_CAPTURE_TIMEOUT_MS).isEqualTo(5000L)
        assertThat(SttConfig.VAD_THRESHOLD).isEqualTo(0.3f)
        assertThat(SttConfig.VAD_MIN_SILENCE_MS).isEqualTo(200L)
        assertThat(SttConfig.VAD_MIN_SPEECH_MS).isEqualTo(250L)
        assertThat(SttConfig.VAD_SPEECH_PAD_MS).isEqualTo(50L)
        assertThat(SttConfig.SAMPLE_RATE).isEqualTo(16000)
        assertThat(SttConfig.FRAME_SIZE_SAMPLES).isEqualTo(512)
        assertThat(SttConfig.CONTEXT_SIZE_SAMPLES).isEqualTo(64)
        assertThat(SttConfig.TOTAL_INPUT_SAMPLES).isEqualTo(576)
        assertThat(SttConfig.TTS_MAX_CACHE_SIZE).isEqualTo(3)
    }

    @Test
    fun `EmergencyClassifier detects emergency phrases across languages`() {
        val hindiAlert = EmergencyClassifier.classifyDetailed("मदद चाहिए यहां आग लगी है", "hi")
        assertThat(hindiAlert.isEmergency).isTrue()
        assertThat(hindiAlert.priority).isEqualTo("P0")
        assertThat(hindiAlert.matchedEmergency).contains("मदद")

        val englishAlert = EmergencyClassifier.classifyDetailed("Trapped on 3rd floor send help", "en")
        assertThat(englishAlert.isEmergency).isTrue()
        assertThat(englishAlert.priority).isEqualTo("P0")
        assertThat(englishAlert.matchedEmergency).contains("trapped")

        val normalMsg = EmergencyClassifier.classifyDetailed("हम सुरक्षित पहुंच गए हैं", "hi")
        assertThat(normalMsg.isEmergency).isFalse()
        assertThat(normalMsg.priority).isEqualTo("P2")
    }

    @Test
    fun `OfflineLanguageDetector detects distinct scripts and disambiguates Devanagari`() {
        // Bengali
        val bnResult = OfflineLanguageDetector.detect("সবকিছু ঠিক আছে")
        assertThat(bnResult.language).isEqualTo(Language.BENGALI)

        // Gujarati
        val guResult = OfflineLanguageDetector.detect("બધું બરાબર છે")
        assertThat(guResult.language).isEqualTo(Language.GUJARATI)

        // Tamil
        val taResult = OfflineLanguageDetector.detect("உடனடி உதவி தேவை")
        assertThat(taResult.language).isEqualTo(Language.TAMIL)

        // Telugu
        val teResult = OfflineLanguageDetector.detect("అత్యవసర సహాయం కావాలి")
        assertThat(teResult.language).isEqualTo(Language.TELUGU)

        // Kannada
        val knResult = OfflineLanguageDetector.detect("ತುರ್ತು ನೆರವಿನ ಅಗತ್ಯವಿದೆ")
        assertThat(knResult.language).isEqualTo(Language.KANNADA)

        // Malayalam
        val mlResult = OfflineLanguageDetector.detect("അടിയന്തര സഹಾಯം ആവശ്യമാണ്")
        assertThat(mlResult.language).isEqualTo(Language.MALAYALAM)

        // Odia
        val orResult = OfflineLanguageDetector.detect("ଜରୁରୀକାଳୀନ ସହାୟତା ଆବଶ୍ୟକ")
        assertThat(orResult.language).isEqualTo(Language.ODIA)

        // English
        val enResult = OfflineLanguageDetector.detect("Emergency Alert required")
        assertThat(enResult.language).isEqualTo(Language.ENGLISH)

        // Marathi Devanagari Disambiguation
        val mrResult = OfflineLanguageDetector.detect("मला येथे मदत पाहिजे आम्ही अडकलो आहोत")
        assertThat(mrResult.language).isEqualTo(Language.MARATHI)

        // Hindi Devanagari Disambiguation
        val hiResult = OfflineLanguageDetector.detect("मुझे यहाँ सहायता चाहिए हम फँसे हुए हैं")
        assertThat(hiResult.language).isEqualTo(Language.HINDI)
    }

    @Test
    fun `OfflineTranslationEngine enforces same-language 0ms bypass`() {
        val originalText = "नमस्कार, तुम्ही कसे आहात?"
        val sameLangResult = OfflineTranslationEngine.translate(originalText, Language.MARATHI, Language.MARATHI)
        assertThat(sameLangResult).isEqualTo(originalText)

        val hiText = "नमस्ते, आप कैसे हैं?"
        val sameLangHi = OfflineTranslationEngine.translate(hiText, Language.HINDI, Language.HINDI)
        assertThat(sameLangHi).isEqualTo(hiText)

        val enText = "Emergency rescue needed immediately"
        val sameLangEn = OfflineTranslationEngine.translate(enText, Language.ENGLISH, Language.ENGLISH)
        assertThat(sameLangEn).isEqualTo(enText)
    }

    @Test
    fun `TTSNormalizer expands numerals and cleans punctuation`() {
        val normalizedHi = TTSNormalizer.normalizeForTTS("कमरा नंबर १२ में ४ लोग हैं!", Language.HINDI)
        assertThat(normalizedHi).contains("बारह")
        assertThat(normalizedHi).contains("चार")
        assertThat(normalizedHi).endsWith("!")

        val normalizedEn = TTSNormalizer.normalizeForTTS("Room 42 has 3 people.", Language.ENGLISH)
        assertThat(normalizedEn).contains("forty two")
        assertThat(normalizedEn).contains("three")
    }

    @Test
    fun `LinguisticChunker splits long text along clause boundaries`() {
        val longText = "आपातकालीन चेतावनी! हमें तुरंत सहायता चाहिए। हम तीसरी मंजिल पर फंसे हुए हैं और आग फैल रही है।"
        val chunks = LinguisticChunker.chunkText(longText, Language.HINDI, minWords = 2, maxWords = 8)
        assertThat(chunks).isNotEmpty()
        for (chunk in chunks) {
            val count = chunk.split(Regex("\\s+")).size
            assertThat(count).isAtMost(8)
        }
    }
}
