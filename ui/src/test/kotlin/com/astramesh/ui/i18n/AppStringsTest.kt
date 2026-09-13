package com.astramesh.ui.i18n

import android.content.Context
import android.content.SharedPreferences
import com.astramesh.core.Language
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class AppStringsTest {
    @Test
    fun `all supported languages resolve localized strings`() {
        AppLanguageState.supportedLanguages.forEach { language ->
            val strings = AppStrings.forLanguage(language.code)
            assertThat(strings.languageCode).isEqualTo(language.code)
            assertThat(strings.allUiStrings).isNotEmpty()
            strings.allUiStrings.forEach { value ->
                assertThat(value).isNotEmpty()
            }
        }
    }

    @Test
    fun `supported languages do not silently reuse English UI copy`() {
        val english = AppStrings.forLanguage("en")
        AppLanguageState.supportedLanguages.filter { it.code != "en" }.forEach { language ->
            val strings = AppStrings.forLanguage(language.code)
            assertThat(strings.languageCode).isEqualTo(language.code)
            assertThat(strings.appInterfaceLanguage).isNotEqualTo(english.appInterfaceLanguage)
            assertThat(strings.holdToBroadcast).isNotEqualTo(english.holdToBroadcast)
            assertThat(strings.settingsTitle).isNotEqualTo(english.settingsTitle)
            assertThat(strings.pushToTalk).isNotEqualTo(english.pushToTalk)
        }
    }

    @Test
    fun `unsupported language does not enter UI language map`() {
        assertThat(AppStrings.forLanguage("pa")).isEqualTo(AppStrings.English)
        assertThat(AppLanguageState.supportedLanguages).hasSize(10)
        assertThat(AppLanguageState.supportedLanguages.map { it.code })
            .containsExactly("hi", "gu", "mr", "kn", "ml", "ta", "te", "or", "bn", "en")
            .inOrder()
        assertThat(AppLanguageState.supportedLanguages.map { it.code }).doesNotContain("pa")
    }

    @Test
    fun `missing and invalid persisted codes default to Hindi`() {
        assertThat(AppLanguageState.resolvePersistedCode(null)).isEqualTo(Language.HINDI)
        assertThat(AppLanguageState.resolvePersistedCode(" ")).isEqualTo(Language.HINDI)
        assertThat(AppLanguageState.resolvePersistedCode("pa")).isEqualTo(Language.HINDI)
        assertThat(AppLanguageState.resolvePersistedCode("not-a-language")).isEqualTo(Language.HINDI)
    }

    @Test
    fun `supported persisted codes resolve through Language fromCode`() {
        AppLanguageState.supportedLanguages.forEach { language ->
            assertThat(AppLanguageState.resolvePersistedCode(language.code)).isEqualTo(language)
        }
    }

    @Test
    fun `selecting a UI language updates the shared state`() {
        val context = mockk<Context>()
        val preferences = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns preferences
        every { preferences.getString(any(), any()) } returns null
        every { preferences.edit() } returns editor

        val state = AppLanguageState(context)
        state.setLanguage(Language.GUJARATI)

        assertThat(state.language.value).isEqualTo(Language.GUJARATI)
        io.mockk.verify { editor.putString("language_code", "gu") }
    }

    @Test
    fun `newly added ui string properties resolve for all 10 supported languages`() {
        AppLanguageState.supportedLanguages.forEach { language ->
            val strings = AppStrings.forLanguage(language.code)
            assertThat(strings.typeMessage).isNotEmpty()
            assertThat(strings.connectAction).isNotEmpty()
            assertThat(strings.pairingQrCode).isNotEmpty()
            assertThat(strings.exportIdentity).isNotEmpty()
            assertThat(strings.identityExported).isNotEmpty()
            assertThat(strings.importIdentity).isNotEmpty()
            assertThat(strings.importIdentityRequested).isNotEmpty()
            assertThat(strings.resetIdentity).isNotEmpty()
            assertThat(strings.identityResetToast).isNotEmpty()
            assertThat(strings.emergencyDataWipe).isNotEmpty()
            assertThat(strings.emergencyWipeToast).isNotEmpty()
            assertThat(strings.noContactsFound).isNotEmpty()
            assertThat(strings.newChat).isNotEmpty()
            assertThat(strings.noChatsFound).isNotEmpty()
        }
    }
}

