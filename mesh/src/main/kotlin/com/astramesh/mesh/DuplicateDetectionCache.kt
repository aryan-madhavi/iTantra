package com.astramesh.mesh

import com.astramesh.core.PacketId
import java.util.Collections

/**
 * Thread-safe LRU cache storing recently processed Packet IDs to prevent loops and duplicate forwards.
 */
class DuplicateDetectionCache(
    private val maxEntries: Int = 2048
) {
    private val cache = Collections.synchronizedMap(
        object : LinkedHashMap<PacketId, Long>(maxEntries, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PacketId, Long>?): Boolean {
                return size > maxEntries
            }
        }
    )

    fun contains(packetId: PacketId): Boolean = cache.containsKey(packetId)

    fun put(packetId: PacketId, timestamp: Long = System.currentTimeMillis()) {
        cache[packetId] = timestamp
    }

    fun containsOrPut(packetId: PacketId, timestamp: Long = System.currentTimeMillis()): Boolean {
        synchronized(cache) {
            if (cache.containsKey(packetId)) {
                return true
            }
            cache[packetId] = timestamp
            return false
        }
    }

    fun pruneOlderThan(ttlMillis: Long = 10 * 60 * 1000L, now: Long = System.currentTimeMillis()): Int {
        val cutoff = now - ttlMillis
        synchronized(cache) {
            val iterator = cache.entries.iterator()
            var pruned = 0
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.value < cutoff) {
                    iterator.remove()
                    pruned++
                }
            }
            return pruned
        }
    }


    fun size(): Int = cache.size

    fun clear() = cache.clear()
}

