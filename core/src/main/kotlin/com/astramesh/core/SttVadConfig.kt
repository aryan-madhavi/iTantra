package com.astramesh.core

/**
 * Runtime configuration parameters for Silero VAD, Speech-to-Text, and Keyword Spotting.
 * Mapped directly from UI/Settings sliders and switches:
 * - Wake-Word sensitivity threshold (0.01 to 0.50)
 * - Command capture timeout (1000 to 10000 ms)
 * - VAD threshold (0.10 to 0.90)
 * - VAD minimum silence (100 to 500 ms)
 * - VAD minimum speech (100 to 500 ms)
 * - VAD speech padding (0 to 100 ms)
 */
data class SttVadConfig(
    val enableKeywordDetection: Boolean = false,
    val wakeWordSensitivity: Float = 0.10f,
    val commandCaptureTimeoutMs: Long = 5000L,
    val vadThreshold: Float = 0.30f,
    val vadMinSilenceMs: Long = 200L,
    val vadMinSpeechMs: Long = 250L,
    val vadSpeechPadMs: Long = 50L
) {
    /**
     * Converts normalized 0.1..0.9 vadThreshold to RMS audio energy threshold.
     * Lower threshold = more sensitive (detects soft speech/whispers).
     * Higher threshold = less sensitive (requires louder speech in noisy environments).
     */
    val effectiveRmsThreshold: Int
        get() = (vadThreshold * 2000f).toInt().coerceIn(150, 2500)

    /**
     * Effective wake-word confidence threshold based on sensitivity.
     */
    val effectiveWakeWordThreshold: Float
        get() = wakeWordSensitivity.coerceIn(0.01f, 0.50f)

    /**
     * Number of audio frames required for minimum speech duration.
     */
    fun minSpeechFrames(frameDurationMs: Int = 40): Int {
        return (vadMinSpeechMs / frameDurationMs).toInt().coerceAtLeast(1)
    }

    /**
     * Number of audio frames required for silence endpointing.
     */
    fun minSilenceFrames(frameDurationMs: Int = 40): Int {
        return (vadMinSilenceMs / frameDurationMs).toInt().coerceAtLeast(1)
    }

    /**
     * Number of padding frames prepended to speech segments.
     */
    fun speechPadFrames(frameDurationMs: Int = 40): Int {
        return (vadSpeechPadMs / frameDurationMs).toInt().coerceAtLeast(0)
    }
}
