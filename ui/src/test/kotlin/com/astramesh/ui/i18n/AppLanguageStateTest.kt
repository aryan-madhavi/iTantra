package com.astramesh.ui.i18n

import android.content.Context
import android.content.SharedPreferences
import com.astramesh.core.Language
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class AppLanguageStateTest {
    private val context = mockk<Context>(relaxed = true)
    private val preferences = mockk<SharedPreferences>(relaxed = true)
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)

    private fun state(savedCode: String?): AppLanguageState {
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns preferences
        every { preferences.getString(any(), any()) } returns savedCode
        every { preferences.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.commit() } returns true
        return AppLanguageState(context)
    }

    @Test
    fun `missing persisted code defaults to Hindi`() {
        assertThat(state(null).language.value).isEqualTo(Language.HINDI)
    }

    @Test
    fun `persisted code restores canonical language and setter persists code`() {
        val appLanguageState = state("te")

        assertThat(appLanguageState.language.value).isEqualTo(Language.TELUGU)
        appLanguageState.setLanguage(Language.ENGLISH)

        verify { editor.putString("language_code", "en") }
        verify { editor.commit() }
        assertThat(appLanguageState.language.value).isEqualTo(Language.ENGLISH)
    }

    @Test
    fun `invalid persisted code falls back to Hindi`() {
        assertThat(state("pa").language.value).isEqualTo(Language.HINDI)
        assertThat(state("invalid").language.value).isEqualTo(Language.HINDI)
    }

    @Test
    fun `all canonical ui languages are exactly ten and round-trip through code`() {
        assertThat(Language.entries).hasSize(10)
        assertThat(Language.entries.map { it.code }).containsExactly("hi", "gu", "mr", "kn", "ml", "ta", "te", "or", "bn", "en").inOrder()
        Language.entries.forEach { language ->
            val reloaded = Language.fromCode(language.code)
            assertThat(reloaded).isEqualTo(language)
        }
    }

    @Test
    fun `punjabi is not part of the supported ui picker`() {
        assertThat(Language.entries.map { it.code }).doesNotContain("pa")
        assertThat(AppStrings.forLanguage("pa").languageCode).isEqualTo("en")
    }
}