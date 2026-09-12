package com.astramesh.mesh

import com.astramesh.core.AstraNetworkConfig
import com.astramesh.core.NodeId
import com.astramesh.domain.model.MessagePriority
import java.util.concurrent.ConcurrentLinkedQueue

data class QueuedOfflineMessage(
    val id: String,
    val destination: NodeId,
    val payload: ByteArray,
    val priority: MessagePriority,
    val createdAt: Long = System.currentTimeMillis(),
    val expireTimestamp: Long = System.currentTimeMillis() + AstraNetworkConfig.STORE_AND_FORWARD_RETENTION_MS,
    var attempts: Int = 0
)

/**
 * Priority-aware Store-and-Forward delay-tolerant buffer.
 * Caches messages when no active route or neighbor connection is currently available.
 * Evicts lowest-priority, oldest messages when capacity is exceeded.
 */
class StoreAndForwardQueue(
    private val maxCapacity: Int = 1000,
    private val maxAttempts: Int = 5
) {
    private val queue = ConcurrentLinkedQueue<QueuedOfflineMessage>()

    @Synchronized
    fun enqueue(
        destination: NodeId,
        payload: ByteArray,
        priority: MessagePriority,
        ttlMillis: Long = AstraNetworkConfig.STORE_AND_FORWARD_RETENTION_MS
    ): Boolean {
        val now = System.currentTimeMillis()
        pruneExpired(now)

        if (queue.size >= maxCapacity) {
            // Find lowest priority message to evict (never evict EMERGENCY if non-EMERGENCY exists)
            val nonEmergency = queue.filter { it.priority != MessagePriority.EMERGENCY }
            val itemToEvict = if (nonEmergency.isNotEmpty()) {
                nonEmergency.minByOrNull { it.createdAt }
            } else {
                queue.minByOrNull { it.createdAt }
            }

            if (itemToEvict != null) {
                // If trying to insert lower priority than what we'd evict, reject unless inserting emergency
                if (priority == MessagePriority.BULK && itemToEvict.priority == MessagePriority.EMERGENCY) {
                    return false
                }
                queue.remove(itemToEvict)
            }
        }

        val item = QueuedOfflineMessage(
            id = java.util.UUID.randomUUID().toString(),
            destination = destination,
            payload = payload,
            priority = priority,
            createdAt = now,
            expireTimestamp = now + ttlMillis,
            attempts = 0
        )
        queue.add(item)
        return true
    }

    @Synchronized
    fun pollForDestination(destination: NodeId, now: Long = System.currentTimeMillis()): List<QueuedOfflineMessage> {
        val matches = mutableListOf<QueuedOfflineMessage>()
        val iterator = queue.iterator()

        while (iterator.hasNext()) {
            val item = iterator.next()
            if (now > item.expireTimestamp || item.attempts >= maxAttempts) {
                iterator.remove()
                continue
            }

            if (item.destination == destination || item.destination.isBroadcast) {
                item.attempts++
                matches.add(item)
                iterator.remove()
            }
        }

        // Return sorted by priority: EMERGENCY -> DIRECT_MESSAGE -> CONTROL -> BULK
        return matches.sortedBy { it.priority.ordinal }
    }

    @Synchronized
    fun pruneExpired(now: Long = System.currentTimeMillis()): Int {
        var pruned = 0
        val iterator = queue.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (now > item.expireTimestamp || item.attempts >= maxAttempts) {
                iterator.remove()
                pruned++
            }
        }
        return pruned
    }

    fun size(): Int = queue.size

    fun clear() = queue.clear()
}
