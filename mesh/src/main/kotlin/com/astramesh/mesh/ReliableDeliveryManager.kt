package com.astramesh.mesh

import com.astramesh.core.NodeId
import com.astramesh.core.PacketId
import java.util.concurrent.ConcurrentHashMap

data class UnacknowledgedPacket(
    val packet: AstraPacket,
    val targetNodeId: NodeId,
    val sentTimestamp: Long,
    val retryCount: Int = 0,
    val nextRetryTimestamp: Long
)

/**
 * Manages End-to-End and Hop-by-Hop delivery confirmations (ACK/NACK) with exponential backoff retries.
 */
class ReliableDeliveryManager(
    private val maxRetries: Int = 3,
    private val initialBackoffMs: Long = 1000L,
    private val maxPending: Int = 128,
    private val packetTtlMs: Long = 60_000L // 60 seconds TTL
) {
    private val pendingAcks = ConcurrentHashMap<PacketId, UnacknowledgedPacket>()

    fun pruneExpired(now: Long = System.currentTimeMillis(), onPacketFailed: ((AstraPacket) -> Unit)? = null): Int {
        var pruned = 0
        val iterator = pendingAcks.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.sentTimestamp > packetTtlMs) {
                onPacketFailed?.invoke(entry.value.packet)
                iterator.remove()
                pruned++
            }
        }
        return pruned
    }

    fun trackPacket(packet: AstraPacket, targetNodeId: NodeId) {
        if (!packet.flags.requiresAck) return
        pruneExpired()

        while (pendingAcks.size >= maxPending) {
            val oldest = pendingAcks.values.minByOrNull { it.sentTimestamp } ?: break
            pendingAcks.remove(oldest.packet.packetId)
        }

        pendingAcks[packet.packetId] = UnacknowledgedPacket(
            packet = packet,
            targetNodeId = targetNodeId,
            sentTimestamp = System.currentTimeMillis(),
            retryCount = 0,
            nextRetryTimestamp = System.currentTimeMillis() + initialBackoffMs
        )
    }

    fun acknowledge(packetId: PacketId): Boolean {
        return pendingAcks.remove(packetId) != null
    }

    fun acknowledgeSequence(seqNo: Long, fromNodeId: NodeId): Boolean {
        val iterator = pendingAcks.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val unack = entry.value
            if (unack.packet.sequenceNumber == seqNo && (unack.packet.destination == fromNodeId || unack.targetNodeId == fromNodeId)) {
                iterator.remove()
                return true
            }
        }
        return false
    }

    fun getPacketsForRetry(
        now: Long = System.currentTimeMillis(),
        onPacketFailed: ((AstraPacket) -> Unit)? = null
    ): List<AstraPacket> {
        val toRetry = mutableListOf<AstraPacket>()
        val iterator = pendingAcks.entries.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            val unack = entry.value

            if (now - unack.sentTimestamp > packetTtlMs) {
                onPacketFailed?.invoke(unack.packet)
                iterator.remove()
                continue
            }

            if (now >= unack.nextRetryTimestamp) {
                if (unack.retryCount >= maxRetries) {
                    onPacketFailed?.invoke(unack.packet)
                    iterator.remove() // Exceeded retries, fail
                } else {
                    val nextBackoff = initialBackoffMs * (1L shl (unack.retryCount + 1))
                    entry.setValue(
                        unack.copy(
                            retryCount = unack.retryCount + 1,
                            nextRetryTimestamp = now + nextBackoff
                        )
                    )
                    toRetry.add(unack.packet)
                }
            }
        }

        return toRetry
    }

    fun pendingCount(): Int = pendingAcks.size

    fun clear() = pendingAcks.clear()
}
