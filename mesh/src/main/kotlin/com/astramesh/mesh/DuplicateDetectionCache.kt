package com.astramesh.mesh

import com.astramesh.core.PacketId
import java.util.Collections

/**
 * Thread-safe LRU cache storing recently processed Packet IDs with expiration to prevent loops,
 * duplicate packet forwards, and redundant message synthesis/storage.
 */
class DuplicateDetectionCache(
    private val maxEntries: Int = 2048,
    private val defaultTtlMillis: Long = 300_000L // 5 minutes retention
) {
    private val cache = Collections.synchronizedMap(
        object : LinkedHashMap<PacketId, Long>(maxEntries, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PacketId, Long>?): Boolean {
                return size > maxEntries
            }
        }
    )

    fun contains(packetId: PacketId, now: Long = System.currentTimeMillis()): Boolean {
        val timestamp = cache[packetId] ?: return false
        if (now - timestamp > defaultTtlMillis) {
            cache.remove(packetId)
            return false
        }
        return true
    }

    fun put(packetId: PacketId, now: Long = System.currentTimeMillis()) {
        cache[packetId] = now
    }

    /**
     * Checks if packetId is already in cache and not expired.
     * If present and valid, returns true (is duplicate).
     * If not present or expired, records packetId and returns false.
     */
    fun containsOrPut(packetId: PacketId, now: Long = System.currentTimeMillis()): Boolean {
        synchronized(cache) {
            val timestamp = cache[packetId]
            if (timestamp != null && (now - timestamp) <= defaultTtlMillis) {
                return true
            }
            cache[packetId] = now
            return false
        }
    }

    fun pruneExpired(now: Long = System.currentTimeMillis()): Int {
        var pruned = 0
        synchronized(cache) {
            val iterator = cache.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value > defaultTtlMillis) {
                    iterator.remove()
                    pruned++
                }
            }
        }
        return pruned
    }

    fun size(): Int = cache.size

    fun clear() = cache.clear()
}
