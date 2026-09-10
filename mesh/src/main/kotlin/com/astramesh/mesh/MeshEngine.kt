package com.astramesh.mesh

import com.astramesh.ble.BleAdvertiserManager
import com.astramesh.ble.BleConnectionPool
import com.astramesh.ble.BleFrameFragmenter
import com.astramesh.ble.BleFrameReassembler
import com.astramesh.ble.BlePowerMode
import com.astramesh.ble.BleScannerManager
import com.astramesh.ble.GattClientManager
import com.astramesh.ble.GattServerManager
import com.astramesh.common.AstraLog
import com.astramesh.common.AstraResult
import com.astramesh.core.AstraNetworkConfig
import com.astramesh.core.NodeId
import com.astramesh.core.PacketId
import com.astramesh.domain.model.DirectConnectionState
import com.astramesh.domain.model.MeshStatus
import com.astramesh.domain.model.MessagePriority
import com.astramesh.domain.model.Peer
import com.astramesh.domain.model.PeerTrustLevel
import com.astramesh.domain.repository.PeerRepository
import com.astramesh.routing.RoutingTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Main mesh orchestrator coordinating BLE, Routing, Packet handling, Store-and-Forward, and Reliability.
 */
class MeshEngine(
    val localNodeId: NodeId,
    private val advertiserManager: BleAdvertiserManager,
    private val scannerManager: BleScannerManager,
    private val gattClientManager: GattClientManager,
    private val gattServerManager: GattServerManager,
    private val connectionPool: BleConnectionPool,
    val routingTable: RoutingTable,
    private val peerRepository: PeerRepository? = null,
    private val messageRepository: com.astramesh.domain.repository.MessageRepository? = null,
    private val chatRepository: com.astramesh.domain.repository.ChatRepository? = null,
    private val deduplicationCache: DuplicateDetectionCache = DuplicateDetectionCache(),
    private val priorityScheduler: PacketPriorityScheduler = PacketPriorityScheduler(),
    private val storeAndForwardQueue: StoreAndForwardQueue = StoreAndForwardQueue(),
    private val reliableDeliveryManager: ReliableDeliveryManager = ReliableDeliveryManager()
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val fragmenter = BleFrameFragmenter()
    private val reassembler = BleFrameReassembler()
    private val forwarder = MessageForwarder(localNodeId, deduplicationCache, routingTable)

    private val _incomingPackets = MutableSharedFlow<AstraPacket>(extraBufferCapacity = 64)
    val incomingPackets: SharedFlow<AstraPacket> = _incomingPackets.asSharedFlow()

    private val _meshStatus = MutableStateFlow(MeshStatus())
    val meshStatus: StateFlow<MeshStatus> = _meshStatus.asStateFlow()

    private val sentPacketsCounter = AtomicLong(0)
    private val relayedPacketsCounter = AtomicLong(0)
    private val receivedPacketsCounter = AtomicLong(0)
    private val droppedPacketsCounter = AtomicLong(0)

    private var nextSequenceNumber: Long = 0L

    fun start(): AstraResult<Unit> {
        AstraLog.i("MeshEngine", "Starting AstraMesh Engine for node $localNodeId")

        // 1. Start Peripheral GATT Server
        gattServerManager.startServer()

        // 2. Start BLE Advertising
        advertiserManager.startAdvertising(localNodeId, BlePowerMode.BALANCED)

        // 3. Start BLE Scanning
        scannerManager.startScanning(BlePowerMode.BALANCED)

        // 4. Listen to inbound client slices
        scope.launch {
            gattClientManager.incomingSlices.collect { (fromNode, slice) ->
                handleInboundSlice(slice, fromNode)
            }
        }

        // 4b. Listen to inbound server slices (from remote Centrals writing to our GATT server TX characteristic)
        scope.launch {
            gattServerManager.serverIncomingSlices.collect { (deviceAddress, slice) ->
                val fromNode = routingTable.getAllActiveRoutes()
                    .firstOrNull { it.nextHop.toHex() in deviceAddress || deviceAddress.contains(it.nextHop.toHex()) }
                    ?.destination ?: NodeId(deviceAddress.hashCode().toLong())
                handleInboundSlice(slice, fromNode)
            }
        }

        // 5. Listen to discovered peers
        scope.launch {
            scannerManager.discoveredPeers.collect { peer ->
                handleDiscoveredPeer(peer.nodeId, peer.deviceAddress, peer.rssi)
            }
        }

        updateStatus(isAdvertising = true, isScanning = true)
        return AstraResult.Success(Unit)
    }

    fun stop() {
        AstraLog.i("MeshEngine", "Stopping AstraMesh Engine")
        advertiserManager.stopAdvertising()
        scannerManager.stopScanning()
        gattServerManager.stopServer()
        connectionPool.clear()
        scope.cancel()
        updateStatus(isAdvertising = false, isScanning = false)
    }

    fun sendPacket(
        destination: NodeId,
        payload: ByteArray,
        priority: MessagePriority = MessagePriority.DIRECT_MESSAGE
    ): AstraResult<PacketId> {
        val seq = nextSequenceNumber++
        val pid = PacketId.generate(localNodeId, seq, System.currentTimeMillis())
        AstraLog.d("MeshEngine", "SEND destination=$destination pid=$pid priority=$priority payloadSize=${payload.size}")

        val packet = AstraPacket(
            type = if (destination.isBroadcast) AstraPacketType.DATA_BROADCAST else AstraPacketType.DATA_UNICAST,
            flags = AstraPacketFlags(
                isEmergency = priority == MessagePriority.EMERGENCY,
                requiresAck = !destination.isBroadcast,
                isEncrypted = true,
                relayAllowed = true
            ),
            ttl = if (priority == MessagePriority.EMERGENCY) 15 else AstraNetworkConfig.DEFAULT_TTL,
            hopCount = 0,
            sequenceNumber = seq,
            packetId = pid,
            source = localNodeId,
            destination = destination,
            visitedBloomFilter = 0,
            payload = payload
        )

        deduplicationCache.put(pid)
        dispatchOrQueue(packet, priority)
        sentPacketsCounter.incrementAndGet()
        updateStatus()

        return AstraResult.Success(pid)
    }

    private fun dispatchOrQueue(packet: AstraPacket, priority: MessagePriority) {
        if (packet.destination.isBroadcast) {
            // Flood to all active connections
            for (conn in connectionPool.getAll()) {
                sendPacketOverConnection(packet, conn.nodeId)
            }
            return
        }

        val route = routingTable.getRoute(packet.destination)
        if (route != null && connectionPool.isConnected(route.nextHop)) {
            sendPacketOverConnection(packet, route.nextHop)
            if (packet.flags.requiresAck) {
                reliableDeliveryManager.trackPacket(packet, route.nextHop)
            }
        } else {
            // Store-and-Forward
            storeAndForwardQueue.enqueue(packet.destination, packet.payload, priority)
        }
    }

    private fun sendPacketOverConnection(packet: AstraPacket, targetNodeId: NodeId) {
        val handle = connectionPool.get(targetNodeId) ?: return
        val maxSlice = handle.negotiatedMtu - 3
        val rawBytes = AstraPacket.serialize(packet)
        val slices = fragmenter.fragment(rawBytes, maxSlice)

        for (slice in slices) {
            gattClientManager.writeSlice(targetNodeId, slice)
        }
    }

    private fun handleInboundSlice(slice: com.astramesh.ble.BleSlice, fromNode: NodeId) {
        AstraLog.d("MeshEngine", "RECV slice packetIndex=${slice.packetIndex} sliceSeq=${slice.sliceSeq}/${slice.totalSlices} from $fromNode")
        val completePacketBytes = reassembler.feedSlice(slice) ?: return
        AstraLog.d("MeshEngine", "REASSEMBLE packetIndex=${slice.packetIndex} totalBytes=${completePacketBytes.size} from $fromNode")

        val packet = try {
            AstraPacket.deserialize(completePacketBytes)
        } catch (e: Exception) {
            AstraLog.e("MeshEngine", "ERROR Failed to deserialize packet from $fromNode", e)
            droppedPacketsCounter.incrementAndGet()
            return
        }

        when (val decision = forwarder.processPacket(packet, fromNode)) {
            is ForwardingDecision.ConsumeLocally -> {
                receivedPacketsCounter.incrementAndGet()
                _incomingPackets.tryEmit(decision.packet)
                persistIncomingMessage(decision.packet)
                updateStatus()
            }
            is ForwardingDecision.ForwardUnicast -> {
                relayedPacketsCounter.incrementAndGet()
                sendPacketOverConnection(decision.packet, decision.nextHop)
                updateStatus()
            }
            is ForwardingDecision.FloodBroadcast -> {
                receivedPacketsCounter.incrementAndGet()
                relayedPacketsCounter.incrementAndGet()
                _incomingPackets.tryEmit(decision.packet)
                persistIncomingMessage(decision.packet)
                for (conn in connectionPool.getAll()) {
                    if (conn.nodeId != fromNode) {
                        sendPacketOverConnection(decision.packet, conn.nodeId)
                    }
                }
                updateStatus()
            }
            is ForwardingDecision.Drop -> {
                droppedPacketsCounter.incrementAndGet()
                updateStatus()
            }
        }
    }

    private fun persistIncomingMessage(packet: AstraPacket) {
        val messageRepo = messageRepository ?: return
        val chatRepo = chatRepository ?: return

        scope.launch {
            val isVoice = com.astramesh.core.VoicePayload.isVoicePayload(packet.payload)
            val (contentType, contentStr) = if (isVoice) {
                val voicePayload = com.astramesh.core.VoicePayload.deserialize(packet.payload)
                val snippet = if (voicePayload.transcript.isNotBlank()) {
                    "[Voice Note]: ${voicePayload.transcript}"
                } else {
                    "[Voice Note ${voicePayload.mode.name}]"
                }
                Pair(com.astramesh.domain.model.MessageContentType.AUDIO_NOTE, snippet)
            } else {
                Pair(com.astramesh.domain.model.MessageContentType.TEXT, String(packet.payload, Charsets.UTF_8))
            }

            val chatId = if (packet.destination.isBroadcast) {
                com.astramesh.core.ChatId("chat_broadcast")
            } else {
                com.astramesh.core.ChatId("direct_${packet.source.value}")
            }

            val message = com.astramesh.domain.model.Message(
                id = com.astramesh.core.MessageId(java.util.UUID.randomUUID().toString()),
                chatId = chatId,
                senderId = packet.source,
                recipientId = packet.destination,
                timestamp = System.currentTimeMillis(),
                content = contentStr,
                contentType = contentType,
                status = com.astramesh.domain.model.MessageStatus.DELIVERED,
                priority = if (packet.flags.isEmergency) com.astramesh.domain.model.MessagePriority.EMERGENCY else com.astramesh.domain.model.MessagePriority.DIRECT_MESSAGE
            )

            messageRepo.insertMessage(message)

            val existingChat = chatRepo.getChatById(chatId) ?: com.astramesh.domain.model.Chat(
                id = chatId,
                title = packet.source.toHex(),
                type = if (packet.destination.isBroadcast) com.astramesh.domain.model.ChatType.BROADCAST else com.astramesh.domain.model.ChatType.DIRECT,
                participantIds = listOf(localNodeId, packet.source)
            )
            chatRepo.insertOrUpdateChat(
                existingChat.copy(
                    lastMessage = message,
                    updatedAt = message.timestamp,
                    unreadCount = existingChat.unreadCount + 1
                )
            )
            AstraLog.d("MeshEngine", "UI_UPDATE persisted incoming message=${message.id} from=${packet.source} isVoice=$isVoice")
        }
    }

    private fun handleDiscoveredPeer(nodeId: NodeId, deviceAddress: String, rssi: Int) {
        if (!connectionPool.isConnected(nodeId)) {
            gattClientManager.connectPeer(nodeId, deviceAddress)
        }
        routingTable.updateRoute(
            destination = nodeId,
            nextHop = nodeId,
            cost = 1.0f,
            hopCount = 1,
            sequenceNumber = System.currentTimeMillis()
        )

        scope.launch {
            peerRepository?.updatePeer(
                Peer(
                    nodeId = nodeId,
                    deviceAddress = deviceAddress,
                    displayName = "Node-${nodeId.toHex()}",
                    rssi = rssi,
                    linkQuality = 1.0f,
                    hopDistance = 1,
                    directState = DirectConnectionState.DISCONNECTED,
                    trustLevel = PeerTrustLevel.UNVERIFIED,
                    batteryLevel = 100,
                    lastSeenTimestamp = System.currentTimeMillis()
                )
            )
        }

        // Flush store-and-forward queue for this peer
        val pending = storeAndForwardQueue.pollForDestination(nodeId)
        for (item in pending) {
            sendPacket(item.destination, item.payload, item.priority)
        }
    }

    private fun updateStatus(
        isAdvertising: Boolean = _meshStatus.value.isAdvertising,
        isScanning: Boolean = _meshStatus.value.isScanning
    ) {
        _meshStatus.value = MeshStatus(
            isAdvertising = isAdvertising,
            isScanning = isScanning,
            activeConnectionsCount = connectionPool.count(),
            totalPeersDiscovered = routingTable.getAllActiveRoutes().size,
            packetsSent = sentPacketsCounter.get(),
            packetsRelayed = relayedPacketsCounter.get(),
            packetsReceived = receivedPacketsCounter.get(),
            packetsDropped = droppedPacketsCounter.get()
        )
    }
}
