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

    fun put(packetId: PacketId) {
        cache[packetId] = System.currentTimeMillis()
    }

    fun containsOrPut(packetId: PacketId): Boolean {
        synchronized(cache) {
            if (cache.containsKey(packetId)) {
                return true
            }
            cache[packetId] = System.currentTimeMillis()
            return false
        }
    }

    fun size(): Int = cache.size

    fun clear() = cache.clear()
}
