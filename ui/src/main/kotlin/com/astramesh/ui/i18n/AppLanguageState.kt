package com.astramesh.ui.i18n

import android.content.Context
import com.astramesh.core.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shared state for application UI language only. */
class AppLanguageState(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _language = MutableStateFlow(
        resolvePersistedCode(preferences.getString(LANGUAGE_CODE_KEY, null))
    )

    val language: StateFlow<Language> = _language.asStateFlow()

    fun setLanguage(language: Language) {
        preferences.edit().putString(LANGUAGE_CODE_KEY, language.code).commit()
        _language.value = language
    }

    companion object {
        const val PREFERENCES_NAME = "app_language_preferences"
        const val LANGUAGE_CODE_KEY = "language_code"

        val supportedLanguages: List<Language> = listOf(
            Language.HINDI,
            Language.GUJARATI,
            Language.MARATHI,
            Language.KANNADA,
            Language.MALAYALAM,
            Language.TAMIL,
            Language.TELUGU,
            Language.ODIA,
            Language.BENGALI,
            Language.ENGLISH
        )

        fun resolvePersistedCode(code: String?): Language {
            if (code.isNullOrBlank()) return Language.HINDI
            val normalizedCode = code.trim()
            val isSupportedCode = supportedLanguages.any { it.code.equals(normalizedCode, ignoreCase = true) }
            return if (isSupportedCode) Language.fromCode(normalizedCode) else Language.HINDI
        }
    }
}
