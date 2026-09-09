package com.astramesh.common

import java.util.concurrent.atomic.AtomicLong

/**
 * Interface abstracting time queries to support deterministic replay protection, TTL calculations, and time-shifted testing.
 */
interface TimeProvider {
    /**
     * Current wall-clock Unix epoch timestamp in milliseconds.
     */
    fun currentTimeMillis(): Long

    /**
     * Current monotonic nanosecond timestamp (suitable for latency and timeout measurement).
     */
    fun nanoTime(): Long
}

/**
 * Standard production time provider using system clocks.
 */
class SystemTimeProvider : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
    override fun nanoTime(): Long = System.nanoTime()
}

/**
 * Virtual time provider for deterministic unit and stress testing.
 */
class VirtualTimeProvider(initialMillis: Long = 1_700_000_000_000L) : TimeProvider {
    private val currentMillis = AtomicLong(initialMillis)
    private val currentNanos = AtomicLong(initialMillis * 1_000_000L)

    override fun currentTimeMillis(): Long = currentMillis.get()
    override fun nanoTime(): Long = currentNanos.get()

    fun advanceTimeMillis(deltaMillis: Long) {
        currentMillis.addAndGet(deltaMillis)
        currentNanos.addAndGet(deltaMillis * 1_000_000L)
    }

    fun setTimeMillis(millis: Long) {
        currentMillis.set(millis)
        currentNanos.set(millis * 1_000_000L)
    }
}
