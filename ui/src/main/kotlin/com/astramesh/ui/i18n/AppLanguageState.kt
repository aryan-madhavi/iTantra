package com.astramesh.ui.i18n

import android.content.Context
import android.util.Log
import com.astramesh.core.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shared application-level state for the Compose UI language. */
class AppLanguageState(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    private val _language = MutableStateFlow(loadLanguage())
    val language: StateFlow<Language> = _language.asStateFlow()

    init {
        Log.d("APP_LANGUAGE_DEBUG", "LANGUAGE LOADED=${_language.value.code}")
    }

    fun setLanguage(language: Language) {
        Log.d(
            "APP_LANGUAGE_DEBUG",
            "SET_LANGUAGE before=${_language.value.code}, requested=${language.code}"
        )
        if (_language.value != language) {
            preferences.edit().putString(LANGUAGE_CODE_KEY, language.code).apply()
            _language.value = language
        }
        Log.d("APP_LANGUAGE_DEBUG", "SET_LANGUAGE after=${_language.value.code}")
    }

    private fun loadLanguage(): Language {
        val savedCode = preferences.getString(LANGUAGE_CODE_KEY, null)
        return if (savedCode.isNullOrBlank()) {
            Language.HINDI
        } else {
            val language = Language.fromCode(savedCode)
            if (Language.entries.any { it.code.equals(savedCode, ignoreCase = true) }) language else Language.HINDI
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "astra_ui_preferences"
        private const val LANGUAGE_CODE_KEY = "language_code"
    }
}
