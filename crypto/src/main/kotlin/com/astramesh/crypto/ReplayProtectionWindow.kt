package com.astramesh.crypto

/**
 * 64-bit sliding window bitmap replay attack detector.
 * Prevents message injection, reordering duplicates, and past packet replay.
 */
class ReplayProtectionWindow(
    val windowSize: Int = 64
) {
    private var highestSequenceNumber: Long = -1L
    private var bitmap: Long = 0L

    @Synchronized
    fun isDuplicateOrReplay(sequenceNumber: Long): Boolean {
        if (highestSequenceNumber == -1L) {
            return false
        }

        if (sequenceNumber > highestSequenceNumber) {
            return false
        }

        val diff = highestSequenceNumber - sequenceNumber
        if (diff >= windowSize) {
            return true // Older than window capacity, strictly rejected
        }

        val mask = 1L shl diff.toInt()
        return (bitmap and mask) != 0L
    }

    @Synchronized
    fun markReceived(sequenceNumber: Long) {
        if (highestSequenceNumber == -1L) {
            highestSequenceNumber = sequenceNumber
            bitmap = 1L
            return
        }

        if (sequenceNumber > highestSequenceNumber) {
            val advance = sequenceNumber - highestSequenceNumber
            if (advance >= windowSize) {
                bitmap = 1L
            } else {
                bitmap = (bitmap shl advance.toInt()) or 1L
            }
            highestSequenceNumber = sequenceNumber
        } else {
            val diff = highestSequenceNumber - sequenceNumber
            if (diff < windowSize) {
                val mask = 1L shl diff.toInt()
                bitmap = bitmap or mask
            }
        }
    }

    @Synchronized
    fun reset() {
        highestSequenceNumber = -1L
        bitmap = 0L
    }
}
