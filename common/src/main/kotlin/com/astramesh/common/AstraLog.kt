package com.astramesh.common

import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Log severity levels.
 */
enum class AstraLogLevel(val priority: Int) {
    VERBOSE(2),
    DEBUG(3),
    INFO(4),
    WARN(5),
    ERROR(6)
}

/**
 * Structured log event representation for diagnostics and UI debug view.
 */
data class AstraLogEntry(
    val timestamp: Long,
    val level: AstraLogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
)

/**
 * Central structured logging facility for AstraMesh.
 * Maintains an in-memory bounded ring buffer of recent events for runtime diagnostics.
 */
object AstraLog {

    private const val MAX_RING_BUFFER_SIZE = 500
    private val ringBuffer = ConcurrentLinkedDeque<AstraLogEntry>()

    var minLevel: AstraLogLevel = AstraLogLevel.DEBUG
    var enableConsoleOutput: Boolean = true

    private val androidLogMethods by lazy {
        try {
            val logClass = Class.forName("android.util.Log")
            mapOf(
                AstraLogLevel.VERBOSE to logClass.getMethod("v", String::class.java, String::class.java),
                AstraLogLevel.DEBUG to logClass.getMethod("d", String::class.java, String::class.java),
                AstraLogLevel.INFO to logClass.getMethod("i", String::class.java, String::class.java),
                AstraLogLevel.WARN to logClass.getMethod("w", String::class.java, String::class.java),
                AstraLogLevel.ERROR to logClass.getMethod("e", String::class.java, String::class.java, Throwable::class.java)
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun v(tag: String, message: String, throwable: Throwable? = null) = log(AstraLogLevel.VERBOSE, tag, message, throwable)
    fun d(tag: String, message: String, throwable: Throwable? = null) = log(AstraLogLevel.DEBUG, tag, message, throwable)
    fun i(tag: String, message: String, throwable: Throwable? = null) = log(AstraLogLevel.INFO, tag, message, throwable)
    fun w(tag: String, message: String, throwable: Throwable? = null) = log(AstraLogLevel.WARN, tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log(AstraLogLevel.ERROR, tag, message, throwable)

    fun log(level: AstraLogLevel, tag: String, message: String, throwable: Throwable? = null) {
        if (level.priority < minLevel.priority) return

        val entry = AstraLogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            throwable = throwable
        )

        ringBuffer.addLast(entry)
        while (ringBuffer.size > MAX_RING_BUFFER_SIZE) {
            ringBuffer.pollFirst()
        }

        val method = androidLogMethods?.get(level)
        if (method != null) {
            try {
                if (level == AstraLogLevel.ERROR && throwable != null) {
                    method.invoke(null, tag, message, throwable)
                } else {
                    method.invoke(null, tag, message)
                }
            } catch (_: Throwable) {}
        }

        if (enableConsoleOutput) {
            val formatted = "[${entry.level.name}] [$tag] $message"
            if (level == AstraLogLevel.ERROR || level == AstraLogLevel.WARN) {
                System.err.println(formatted)
                throwable?.printStackTrace(System.err)
            } else {
                println(formatted)
                throwable?.printStackTrace(System.out)
            }
        }
    }

    fun getRecentLogs(): List<AstraLogEntry> {
        return ringBuffer.toList()
    }

    fun clearLogs() {
        ringBuffer.clear()
    }
}
