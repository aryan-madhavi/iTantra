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
    private val initialBackoffMs: Long = 1000L
) {
    private val pendingAcks = ConcurrentHashMap<PacketId, UnacknowledgedPacket>()

    fun trackPacket(packet: AstraPacket, targetNodeId: NodeId) {
        if (!packet.flags.requiresAck) return

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

    fun getPacketsForRetry(now: Long = System.currentTimeMillis()): List<AstraPacket> {
        val toRetry = mutableListOf<AstraPacket>()
        val iterator = pendingAcks.entries.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            val unack = entry.value

            if (now >= unack.nextRetryTimestamp) {
                if (unack.retryCount >= maxRetries) {
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
