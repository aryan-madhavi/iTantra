package com.astramesh.domain.repository

import com.astramesh.core.Language

interface SpeechSynthesizer {
    fun translateText(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): String = text

    suspend fun synthesizeAndPlay(
        text: String,
        language: Language,
        isEmergency: Boolean = false,
        onDone: (() -> Unit)? = null
    )

    fun playEmergencyBeacon(language: Language) {}

    fun preload(language: Language) {}
    fun setPreferredSpeechLanguage(language: Language) {}
}
