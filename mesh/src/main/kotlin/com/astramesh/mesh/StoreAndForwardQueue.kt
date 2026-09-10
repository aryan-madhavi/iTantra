package com.astramesh.mesh

import com.astramesh.core.NodeId
import com.astramesh.domain.model.MessagePriority
import java.util.concurrent.ConcurrentLinkedDeque

data class QueuedOfflineMessage(
    val id: String,
    val destination: NodeId,
    val payload: ByteArray,
    val priority: MessagePriority,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Store-and-Forward delay-tolerant network buffer.
 * Caches messages when no active route or neighbor connection is available.
 */
class StoreAndForwardQueue(
    private val maxCapacity: Int = 200,
    private val ttlMillis: Long = 5 * 60 * 1000L // 5 minutes default TTL
) {
    private val queue = ConcurrentLinkedDeque<QueuedOfflineMessage>()

    fun pruneExpired(now: Long = System.currentTimeMillis()): Int {
        var pruned = 0
        val iterator = queue.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (now - item.createdAt > ttlMillis) {
                iterator.remove()
                pruned++
            }
        }
        return pruned
    }

    fun enqueue(destination: NodeId, payload: ByteArray, priority: MessagePriority): Boolean {
        pruneExpired()

        // Deduplication: Avoid storing duplicate payload for the same destination
        val existing = queue.firstOrNull { it.destination == destination && it.payload.contentEquals(payload) }
        if (existing != null) {
            return false
        }

        while (queue.size >= maxCapacity) {
            // Drop lowest priority or oldest item
            queue.pollFirst()
        }
        val item = QueuedOfflineMessage(
            id = java.util.UUID.randomUUID().toString(),
            destination = destination,
            payload = payload,
            priority = priority
        )
        queue.addLast(item)
        return true
    }

    fun pollForDestination(destination: NodeId): List<QueuedOfflineMessage> {
        pruneExpired()
        val matches = mutableListOf<QueuedOfflineMessage>()
        val iterator = queue.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item.destination == destination || item.destination.isBroadcast) {
                matches.add(item)
                iterator.remove()
            }
        }
        return matches
    }

    fun size(): Int = queue.size

    fun clear() = queue.clear()
}
