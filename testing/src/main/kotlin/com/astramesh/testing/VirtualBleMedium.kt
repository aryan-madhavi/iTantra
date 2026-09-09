package com.astramesh.testing

import com.astramesh.core.NodeId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory RF propagation medium simulating Bluetooth Low Energy mesh topology.
 * Supports link latency, packet loss, RSSI attenuation, and connection graphs.
 */
class VirtualBleMedium(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    data class SimulatedPacket(
        val fromNode: NodeId,
        val toNode: NodeId,
        val payload: ByteArray,
        val rssi: Int
    )

    // Active links: nodeA -> set of reachable nodeBs with link quality (rssi, lossRate, latencyMs)
    data class LinkQuality(
        val rssi: Int = -60,
        val packetLossRate: Double = 0.0,
        val latencyMs: Long = 5L
    )

    private val topology = ConcurrentHashMap<NodeId, ConcurrentHashMap<NodeId, LinkQuality>>()
    private val nodeChannels = ConcurrentHashMap<NodeId, MutableSharedFlow<SimulatedPacket>>()

    fun registerNode(nodeId: NodeId): SharedFlow<SimulatedPacket> {
        val flow = nodeChannels.computeIfAbsent(nodeId) {
            MutableSharedFlow(extraBufferCapacity = 256)
        }
        return flow.asSharedFlow()
    }

    fun unregisterNode(nodeId: NodeId) {
        nodeChannels.remove(nodeId)
        topology.remove(nodeId)
        topology.values.forEach { it.remove(nodeId) }
    }

    fun addBidirectionalLink(
        nodeA: NodeId,
        nodeB: NodeId,
        rssi: Int = -60,
        lossRate: Double = 0.0,
        latencyMs: Long = 5L
    ) {
        addUnidirectionalLink(nodeA, nodeB, rssi, lossRate, latencyMs)
        addUnidirectionalLink(nodeB, nodeA, rssi, lossRate, latencyMs)
    }

    fun addUnidirectionalLink(
        from: NodeId,
        to: NodeId,
        rssi: Int = -60,
        lossRate: Double = 0.0,
        latencyMs: Long = 5L
    ) {
        topology.computeIfAbsent(from) { ConcurrentHashMap() }[to] = LinkQuality(rssi, lossRate, latencyMs)
    }

    fun removeLink(nodeA: NodeId, nodeB: NodeId) {
        topology[nodeA]?.remove(nodeB)
        topology[nodeB]?.remove(nodeA)
    }

    fun transmit(from: NodeId, to: NodeId, payload: ByteArray) {
        val link = topology[from]?.get(to) ?: return

        // Packet loss check
        if (link.packetLossRate > 0.0 && Math.random() < link.packetLossRate) {
            return // Dropped in virtual RF ether
        }

        val targetFlow = nodeChannels[to] ?: return

        scope.launch {
            if (link.latencyMs > 0) {
                delay(link.latencyMs)
            }
            targetFlow.emit(
                SimulatedPacket(
                    fromNode = from,
                    toNode = to,
                    payload = payload.copyOf(),
                    rssi = link.rssi
                )
            )
        }
    }

    fun broadcast(from: NodeId, payload: ByteArray) {
        val neighbors = topology[from] ?: return
        for ((to, link) in neighbors) {
            if (link.packetLossRate > 0.0 && Math.random() < link.packetLossRate) {
                continue
            }
            val targetFlow = nodeChannels[to] ?: continue
            scope.launch {
                if (link.latencyMs > 0) {
                    delay(link.latencyMs)
                }
                targetFlow.emit(
                    SimulatedPacket(
                        fromNode = from,
                        toNode = to,
                        payload = payload.copyOf(),
                        rssi = link.rssi
                    )
                )
            }
        }
    }

    fun isReachable(from: NodeId, to: NodeId): Boolean {
        return topology[from]?.containsKey(to) == true
    }
}
