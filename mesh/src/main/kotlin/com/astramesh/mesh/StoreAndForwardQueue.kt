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
    private val maxCapacity: Int = 1000
) {
    private val queue = ConcurrentLinkedDeque<QueuedOfflineMessage>()

    fun enqueue(destination: NodeId, payload: ByteArray, priority: MessagePriority): Boolean {
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
