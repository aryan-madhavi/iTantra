package com.astramesh.core

import com.astramesh.common.AstraLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global session-level setting holder for offline translation enable/disable state.
 * Allows users to toggle off translation globally to eliminate NLLB inference latency.
 */
object TranslationSettings {
    private val _isTranslationEnabled = MutableStateFlow(true)
    val isTranslationEnabled: StateFlow<Boolean> = _isTranslationEnabled.asStateFlow()

    fun setTranslationEnabled(enabled: Boolean) {
        _isTranslationEnabled.value = enabled
        AstraLog.d("TranslationSettings", "[UI_EVENT] TRANSLATION_TOGGLE enabled=$enabled")
    }

    fun toggleTranslation(): Boolean {
        val next = !_isTranslationEnabled.value
        _isTranslationEnabled.value = next
        AstraLog.d("TranslationSettings", "[UI_EVENT] TRANSLATION_TOGGLE enabled=$next")
        return next
    }
}
