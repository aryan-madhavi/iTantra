package com.astramesh.mesh

import com.astramesh.core.NodeId
import com.astramesh.routing.LoopDetector

sealed class ForwardingDecision {
    data class ConsumeLocally(val packet: AstraPacket) : ForwardingDecision()
    data class ForwardUnicast(val packet: AstraPacket, val nextHop: NodeId) : ForwardingDecision()
    data class FloodBroadcast(val packet: AstraPacket) : ForwardingDecision()
    data class StoreAndForward(val packet: AstraPacket) : ForwardingDecision()
    data class Drop(val reason: String) : ForwardingDecision()
}

/**
 * Evaluates packets arriving over BLE interfaces and decides routing actions:
 * - Duplicate suppression
 * - Loop prevention (Bloom filter)
 * - Self-origin drop
 * - Local consumption
 * - Multi-hop unicast forwarding
 * - Multi-hop broadcast / emergency flooding
 * - Offline Store-and-Forward fallback
 */
class MessageForwarder(
    private val localNodeId: NodeId,
    private val deduplicationCache: DuplicateDetectionCache,
    private val routingTable: com.astramesh.routing.RoutingTable
) {
    fun processPacket(packet: AstraPacket, receivedFromNodeId: NodeId): ForwardingDecision {
        // Step 0: Check if packet was originated by self
        if (packet.source == localNodeId) {
            return ForwardingDecision.Drop("Packet ${packet.packetId} originated by local node $localNodeId")
        }

        // Step 1: Duplicate check (LRU + time-based expiration)
        if (deduplicationCache.containsOrPut(packet.packetId)) {
            return ForwardingDecision.Drop("Duplicate packet ${packet.packetId}")
        }

        // Step 2: Loop detection via visited Bloom filter
        if (LoopDetector.containsNode(packet.visitedBloomFilter, localNodeId)) {
            return ForwardingDecision.Drop("Loop detected in packet ${packet.packetId}")
        }

        // Step 3: Check if destined for local node
        if (packet.destination == localNodeId) {
            return ForwardingDecision.ConsumeLocally(packet)
        }

        // Step 4: Check for Broadcast / Emergency flood
        if (packet.destination.isBroadcast) {
            // If TTL expired (<= 1), consume locally but do not relay further
            if (packet.ttl <= 1) {
                return ForwardingDecision.ConsumeLocally(packet)
            }

            // Decrement TTL, increment hopCount, update Bloom filter for relay
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
        if (route == null) {
            // Buffer packet until a route or direct connection becomes available
            return ForwardingDecision.StoreAndForward(packet)
        }

        val updatedBloom = LoopDetector.addNode(packet.visitedBloomFilter, localNodeId)
        val forwardedPacket = packet.copy(
            ttl = packet.ttl - 1,
            hopCount = packet.hopCount + 1,
            visitedBloomFilter = updatedBloom
        )

        return ForwardingDecision.ForwardUnicast(forwardedPacket, route.nextHop)
    }
}
