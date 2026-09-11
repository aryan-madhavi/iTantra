package com.astramesh.ui.i18n

import com.astramesh.core.Language
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppStringsTest {
    @Test
    fun `all canonical language codes resolve to localized strings`() {
        Language.entries.forEach { language ->
            val strings = AppStrings.forLanguage(language.code)
            assertThat(strings.languageCode).isEqualTo(language.code)
            assertThat(strings.appName).isEqualTo("ITANTRA")
        }
    }

    @Test
    fun `unsupported Punjabi code falls back without becoming a supported language`() {
        val strings = AppStrings.forLanguage("pa")

        assertThat(strings.languageCode).isEqualTo("en")
        assertThat(Language.entries.map { it.code }).doesNotContain("pa")
    }

    @Test
    fun `existing translated values remain available`() {
        assertThat(AppStrings.forLanguage("hi").selectPrimaryLanguage).isEqualTo("प्राथमिक भाषा चुनें")
        assertThat(AppStrings.forLanguage("mr").selectPrimaryLanguage).isEqualTo("प्राथमिक भाषा निवडा")
        assertThat(AppStrings.forLanguage("gu").selectPrimaryLanguage).isEqualTo("પ્રાથમિક ભાષા પસંદ કરો")
    }

    @Test
    fun `all canonical languages provide non-empty added UI labels`() {
        Language.entries.forEach { language ->
            val strings = AppStrings.forLanguage(language.code)
            assertThat(strings.back).isNotEmpty()
            assertThat(strings.contacts).isNotEmpty()
            assertThat(strings.meshBroadcast).isNotEmpty()
            assertThat(strings.distressDescription).isNotEmpty()
            assertThat(strings.holdToTalk).isNotEmpty()
            assertThat(strings.cacheDescription).isNotEmpty()
        }
    }
}
