package com.astramesh.testing

import com.astramesh.ble.BleFrameFragmenter
import com.astramesh.ble.BleFrameReassembler
import com.astramesh.ble.BleSlice
import com.astramesh.core.NodeId
import com.astramesh.core.PacketId
import com.astramesh.domain.model.MessagePriority
import com.astramesh.mesh.AstraPacket
import com.astramesh.mesh.AstraPacketFlags
import com.astramesh.mesh.AstraPacketType
import com.astramesh.mesh.DuplicateDetectionCache
import com.astramesh.mesh.ForwardingDecision
import com.astramesh.mesh.MessageForwarder
import com.astramesh.mesh.StoreAndForwardQueue
import com.astramesh.routing.RoutingTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * High-fidelity virtual node executing the actual AstraMesh packet engine, routing,
 * deduplication, fragmentation/reassembly, and store-and-forward queue over a VirtualBleMedium.
 */
class VirtualMeshNode(
    val nodeId: NodeId,
    private val medium: VirtualBleMedium,
    val routingTable: RoutingTable = RoutingTable(nodeId)
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val fragmenter = BleFrameFragmenter()
    private val reassembler = BleFrameReassembler()
    private val deduplicationCache = DuplicateDetectionCache(maxEntries = 1024)
    private val forwarder = MessageForwarder(nodeId, deduplicationCache, routingTable)
    val storeAndForwardQueue = StoreAndForwardQueue()

    private val _receivedPackets = MutableSharedFlow<AstraPacket>(extraBufferCapacity = 64)
    val receivedPackets: SharedFlow<AstraPacket> = _receivedPackets.asSharedFlow()
    val receivedPacketsHistory = java.util.Collections.synchronizedList(mutableListOf<AstraPacket>())

    private var sequenceCounter = 0L
    val packetsSent = AtomicLong(0)
    val packetsRelayed = AtomicLong(0)
    val packetsReceived = AtomicLong(0)
    val packetsDropped = AtomicLong(0)

    fun start() {
        val incomingFromMedium = medium.registerNode(nodeId)
        scope.launch {
            incomingFromMedium.collect { simulatedPacket ->
                handleInboundRaw(simulatedPacket.fromNode, simulatedPacket.payload)
            }
        }
    }

    fun stop() {
        medium.unregisterNode(nodeId)
        scope.cancel()
    }

    fun sendPacket(
        destination: NodeId,
        payload: ByteArray,
        priority: MessagePriority = MessagePriority.DIRECT_MESSAGE
    ): PacketId {
        val seq = sequenceCounter++
        val pid = PacketId.generate(nodeId, seq, System.currentTimeMillis())

        val packet = AstraPacket(
            type = if (destination.isBroadcast) AstraPacketType.DATA_BROADCAST else AstraPacketType.DATA_UNICAST,
            flags = AstraPacketFlags(
                isEmergency = priority == MessagePriority.EMERGENCY,
                requiresAck = !destination.isBroadcast,
                isEncrypted = false,
                relayAllowed = true
            ),
            ttl = if (priority == MessagePriority.EMERGENCY) 15 else 7,
            hopCount = 0,
            sequenceNumber = seq,
            packetId = pid,
            source = nodeId,
            destination = destination,
            visitedBloomFilter = 0,
            payload = payload
        )

        deduplicationCache.put(pid)
        packetsSent.incrementAndGet()

        if (destination.isBroadcast) {
            val raw = AstraPacket.serialize(packet)
            medium.broadcast(nodeId, raw)
        } else {
            val route = routingTable.getRoute(destination)
            if (route != null && medium.isReachable(nodeId, route.nextHop)) {
                val raw = AstraPacket.serialize(packet)
                medium.transmit(nodeId, route.nextHop, raw)
            } else {
                // Store and forward
                storeAndForwardQueue.enqueue(destination, payload, priority)
            }
        }
        return pid
    }

    fun onPeerConnected(peerId: NodeId) {
        // Flush store and forward queue
        val pending = storeAndForwardQueue.pollForDestination(peerId)
        for (item in pending) {
            if (AstraPacket.isAstraPacket(item.payload)) {
                val pkt = AstraPacket.deserialize(item.payload)
                val route = routingTable.getRoute(pkt.destination)
                if (route != null && medium.isReachable(nodeId, route.nextHop)) {
                    medium.transmit(nodeId, route.nextHop, item.payload)
                }
            } else {
                sendPacket(item.destination, item.payload, item.priority)
            }
        }
    }

    private fun handleInboundRaw(fromNeighbor: NodeId, rawBytes: ByteArray) {
        val packet = try {
            AstraPacket.deserialize(rawBytes)
        } catch (e: Exception) {
            packetsDropped.incrementAndGet()
            return
        }

        // Reverse route learning
        if (packet.source != nodeId) {
            routingTable.updateRoute(
                destination = packet.source,
                nextHop = fromNeighbor,
                cost = (packet.hopCount + 1).toFloat(),
                hopCount = packet.hopCount + 1,
                sequenceNumber = packet.sequenceNumber
            )
        }

        when (val decision = forwarder.processPacket(packet, fromNeighbor)) {
            is ForwardingDecision.ConsumeLocally -> {
                packetsReceived.incrementAndGet()
                receivedPacketsHistory.add(decision.packet)
                _receivedPackets.tryEmit(decision.packet)
            }
            is ForwardingDecision.ForwardUnicast -> {
                packetsRelayed.incrementAndGet()
                val serialized = AstraPacket.serialize(decision.packet)
                medium.transmit(nodeId, decision.nextHop, serialized)
            }
            is ForwardingDecision.FloodBroadcast -> {
                packetsReceived.incrementAndGet()
                receivedPacketsHistory.add(decision.packet)
                packetsRelayed.incrementAndGet()
                _receivedPackets.tryEmit(decision.packet)
                val serialized = AstraPacket.serialize(decision.packet)
                medium.broadcast(nodeId, serialized)
            }
            is ForwardingDecision.StoreAndForward -> {
                storeAndForwardQueue.enqueue(
                    destination = decision.packet.destination,
                    payload = AstraPacket.serialize(decision.packet),
                    priority = if (decision.packet.flags.isEmergency) MessagePriority.EMERGENCY else MessagePriority.DIRECT_MESSAGE
                )
            }
            is ForwardingDecision.Drop -> {
                packetsDropped.incrementAndGet()
            }
        }
    }
}
