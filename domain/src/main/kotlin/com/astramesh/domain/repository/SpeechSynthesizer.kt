package com.astramesh.domain.repository

import com.astramesh.core.Language

interface SpeechSynthesizer {
    suspend fun synthesizeAndPlay(
        text: String,
        language: Language,
        isEmergency: Boolean = false,
        onDone: (() -> Unit)? = null
    )

    fun clearQueues() {}
}

