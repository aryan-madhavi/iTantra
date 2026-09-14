package com.astramesh.core

/**
 * Tunable parameters and constants for the offline VAD and STT streaming pipelines.
 * Standardized across all iTantra modules (SIH Problem Statement #26173).
 */
object SttConfig {
    const val WAKE_WORD_SENSITIVITY_THRESHOLD = 0.1f
    const val COMMAND_CAPTURE_TIMEOUT_MS = 5000L
    const val VAD_THRESHOLD = 0.3f
    const val VAD_MIN_SILENCE_MS = 200L
    const val VAD_MIN_SPEECH_MS = 250L
    const val VAD_SPEECH_PAD_MS = 50L

    const val SAMPLE_RATE = 16000
    const val FRAME_SIZE_SAMPLES = 512       // 32 ms at 16kHz
    const val CONTEXT_SIZE_SAMPLES = 64      // 4 ms rolling context
    const val TOTAL_INPUT_SAMPLES = 576      // Silero VAD v5 requirement
    const val TTS_MAX_CACHE_SIZE = 3
}
