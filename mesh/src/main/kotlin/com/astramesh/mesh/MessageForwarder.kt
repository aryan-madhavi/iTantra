package com.astramesh.mesh

import com.astramesh.core.NodeId
import com.astramesh.routing.LoopDetector

sealed class ForwardingDecision {
    data class ConsumeLocally(val packet: AstraPacket) : ForwardingDecision()
    data class ForwardUnicast(val packet: AstraPacket, val nextHop: NodeId) : ForwardingDecision()
    data class FloodBroadcast(val packet: AstraPacket) : ForwardingDecision()
    data class Drop(val reason: String) : ForwardingDecision()
}

/**
 * Evaluates packets arriving over BLE interfaces and decides routing actions.
 */
class MessageForwarder(
    private val localNodeId: NodeId,
    private val deduplicationCache: DuplicateDetectionCache,
    private val routingTable: com.astramesh.routing.RoutingTable
) {
    fun processPacket(packet: AstraPacket, receivedFromNodeId: NodeId): ForwardingDecision {
        // Step 1: Duplicate check
        if (deduplicationCache.containsOrPut(packet.packetId)) {
            return ForwardingDecision.Drop("Duplicate packet ${packet.packetId}")
        }

        // Step 2: Loop detection via visited Bloom filter
        if (LoopDetector.containsNode(packet.visitedBloomFilter, localNodeId)) {
            return ForwardingDecision.Drop("Loop detected in packet ${packet.packetId}")
        }

        // Step 3: Check if destined for us
        if (packet.destination == localNodeId) {
            return ForwardingDecision.ConsumeLocally(packet)
        }

        // Step 4: Check for Broadcast
        if (packet.destination.isBroadcast) {
            // Local node consumes it AND forwards it if TTL > 1
            if (packet.ttl <= 1) {
                return ForwardingDecision.ConsumeLocally(packet)
            }
            val updatedBloom = LoopDetector.addNode(packet.visitedBloomFilter, localNodeId)
            val forwardingPacket = packet.copy(
                ttl = packet.ttl - 1,
                hopCount = packet.hopCount + 1,
                visitedBloomFilter = updatedBloom
            )
            return ForwardingDecision.FloodBroadcast(forwardingPacket)
        }

        // Step 5: Unicast forwarding
        if (!packet.flags.relayAllowed) {
            return ForwardingDecision.Drop("Packet ${packet.packetId} does not allow relaying")
        }

        if (packet.ttl <= 1) {
            return ForwardingDecision.Drop("TTL expired for packet ${packet.packetId}")
        }

        val route = routingTable.getRoute(packet.destination)
            ?: return ForwardingDecision.Drop("No route known for destination ${packet.destination}")

        val updatedBloom = LoopDetector.addNode(packet.visitedBloomFilter, localNodeId)
        val forwardedPacket = packet.copy(
            ttl = packet.ttl - 1,
            hopCount = packet.hopCount + 1,
            visitedBloomFilter = updatedBloom
        )

        return ForwardingDecision.ForwardUnicast(forwardedPacket, route.nextHop)
    }
}
