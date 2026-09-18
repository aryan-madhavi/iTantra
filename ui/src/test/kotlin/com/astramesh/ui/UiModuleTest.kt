package com.astramesh.ui

import com.astramesh.core.Language
import com.astramesh.core.TranslationSettings
import com.astramesh.ui.components.parseMessageContent
import com.astramesh.ui.theme.AstraBackground
import com.astramesh.ui.theme.AstraCyan
import com.astramesh.ui.theme.AstraEmerald
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UiModuleTest {

    @Test
    fun `theme colors are properly configured`() {
        assertThat(AstraBackground.value).isNotEqualTo(0UL)
        assertThat(AstraCyan.value).isNotEqualTo(0UL)
        assertThat(AstraEmerald.value).isNotEqualTo(0UL)
    }

    @Test
    fun `parseMessageContent parses translated message header accurately`() {
        val raw = "[Voice Note English -> Hindi]: Help is on the way"
        val parsed = parseMessageContent(raw)
        assertThat(parsed.cleanText).isEqualTo("Help is on the way")
        assertThat(parsed.translationBadge).isEqualTo("EN → HI")
        assertThat(parsed.isUntranslated).isFalse()
    }

    @Test
    fun `parseMessageContent parses explicit untranslated tag`() {
        val raw = "[Untranslated HI]: मुझे सहायता चाहिए"
        val parsed = parseMessageContent(raw)
        assertThat(parsed.cleanText).isEqualTo("मुझे सहायता चाहिए")
        assertThat(parsed.translationBadge).isEqualTo("HI")
        assertThat(parsed.isUntranslated).isTrue()
    }

    @Test
    fun `parseMessageContent detects untranslated language when translation is disabled`() {
        TranslationSettings.setTranslationEnabled(false)
        try {
            val raw = "मुझे सहायता चाहिए हम फँसे हुए हैं"
            val parsed = parseMessageContent(raw, localLanguage = Language.ENGLISH)
            assertThat(parsed.cleanText).isEqualTo("मुझे सहायता चाहिए हम फँसे हुए हैं")
            assertThat(parsed.translationBadge).isEqualTo("HI")
            assertThat(parsed.isUntranslated).isTrue()
        } finally {
            TranslationSettings.setTranslationEnabled(true)
        }
    }

    @Test
    fun `TranslationSettings toggle and state works as expected`() {
        TranslationSettings.setTranslationEnabled(true)
        assertThat(TranslationSettings.isTranslationEnabled.value).isTrue()
        TranslationSettings.toggleTranslation()
        assertThat(TranslationSettings.isTranslationEnabled.value).isFalse()
        TranslationSettings.toggleTranslation()
        assertThat(TranslationSettings.isTranslationEnabled.value).isTrue()
    }
}

