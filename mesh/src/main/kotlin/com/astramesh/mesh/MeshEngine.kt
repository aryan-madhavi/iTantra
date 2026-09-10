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
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
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
    private val speechSynthesizer: com.astramesh.domain.repository.SpeechSynthesizer? = null,
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

    @Volatile
    var preferredLanguage: com.astramesh.core.Language = com.astramesh.core.Language.HINDI

    private val sentPacketsCounter = AtomicLong(0)
    private val relayedPacketsCounter = AtomicLong(0)
    private val receivedPacketsCounter = AtomicLong(0)
    private val droppedPacketsCounter = AtomicLong(0)

    private var nextSequenceNumber: Long = 0L

    @Volatile
    private var isStarted = false

    @Synchronized
    fun start(): AstraResult<Unit> {
        if (isStarted) {
            AstraLog.d("MeshEngine", "MeshEngine is already running. Skipping duplicate start().")
            return AstraResult.Success(Unit)
        }
        isStarted = true
        AstraLog.i("MeshEngine", "[LIFECYCLE] Starting AstraMesh Engine for node $localNodeId")

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

        // 6. Periodic reliability retry & pruning worker (bounded retries, no infinite loop)
        scope.launch {
            while (isActive && isStarted) {
                delay(2000L)
                try {
                    val retries = reliableDeliveryManager.getPacketsForRetry()
                    for (pkt in retries) {
                        AstraLog.d("MeshEngine", "[RETRY] Retrying packet ${pkt.packetId} seq=${pkt.sequenceNumber} to ${pkt.destination}")
                        sendPacketOverConnection(pkt, pkt.destination)
                    }
                    reassembler.pruneStale(10_000L)
                } catch (e: Exception) {
                    AstraLog.e("MeshEngine", "Error in reliability retry worker", e)
                }
            }
        }

        updateStatus(isAdvertising = true, isScanning = true)
        return AstraResult.Success(Unit)
    }

    @Synchronized
    fun stop() {
        if (!isStarted) return
        isStarted = false
        AstraLog.i("MeshEngine", "[LIFECYCLE] Stopping AstraMesh Engine")
        advertiserManager.stopAdvertising()
        scannerManager.stopScanning()
        gattServerManager.stopServer()
        connectionPool.clear()
        scope.coroutineContext.cancelChildren()
        updateStatus(isAdvertising = false, isScanning = false)
    }

    fun sendPacket(
        destination: NodeId,
        payload: ByteArray,
        priority: MessagePriority = MessagePriority.DIRECT_MESSAGE
    ): AstraResult<PacketId> {
        val seq = nextSequenceNumber++
        val pid = PacketId.generate(localNodeId, seq, System.currentTimeMillis())

        val isAckOrControl = priority == MessagePriority.CONTROL ||
            (com.astramesh.core.IthantraMessage.isIthantraMessage(payload) &&
             try { com.astramesh.core.IthantraMessage.fromBinary(payload).messageType == com.astramesh.core.MessageType.ACK } catch (e: Exception) { false })

        val requiresAck = !destination.isBroadcast && !isAckOrControl

        AstraLog.d("MeshEngine", "[TX] destination=$destination pid=$pid seq=$seq priority=$priority requiresAck=$requiresAck payloadSize=${payload.size}")

        val packet = AstraPacket(
            type = if (destination.isBroadcast) AstraPacketType.DATA_BROADCAST else AstraPacketType.DATA_UNICAST,
            flags = AstraPacketFlags(
                isEmergency = priority == MessagePriority.EMERGENCY,
                requiresAck = requiresAck,
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
            val isIthantra = com.astramesh.core.IthantraMessage.isIthantraMessage(packet.payload)
            val isVoice = com.astramesh.core.VoicePayload.isVoicePayload(packet.payload)

            if (isIthantra) {
                val ithantra = try {
                    com.astramesh.core.IthantraMessage.fromBinary(packet.payload)
                } catch (e: Exception) {
                    AstraLog.e("MeshEngine", "Failed to decode IthantraMessage", e)
                    return@launch
                }

                // -------------------------------------------------------------
                // CASE 1: RECEIVED PACKET IS AN ACK (TERMINATE RELIABILITY CHAIN)
                // -------------------------------------------------------------
                if (ithantra.messageType == com.astramesh.core.MessageType.ACK) {
                    AstraLog.d("MeshEngine", "[RX ACK] Received ACK for seq=${ithantra.sequenceNumber} from ${packet.source}")
                    reliableDeliveryManager.acknowledgeSequence(ithantra.sequenceNumber, packet.source)

                    // Find matching message in repository and mark as DELIVERED
                    val directChatId = com.astramesh.core.ChatId("direct_${packet.source.value}")
                    val messages = messageRepo.observeMessages(directChatId).first()
                    val unconfirmed = messages.firstOrNull { it.senderId == localNodeId && it.status != com.astramesh.domain.model.MessageStatus.DELIVERED }
                    if (unconfirmed != null) {
                        messageRepo.updateMessageStatus(unconfirmed.id, com.astramesh.domain.model.MessageStatus.DELIVERED)
                    }

                    // DO NOT ACK AN ACK! DO NOT TRIGGER TTS! RETURN IMMEDIATELY!
                    return@launch
                }

                // -------------------------------------------------------------
                // CASE 2: RECEIVED PACKET IS A NORMAL / ALERT / SOS VOICE MESSAGE
                // -------------------------------------------------------------
                AstraLog.d("MeshEngine", "[RX] Received voice message seq=${ithantra.sequenceNumber} type=${ithantra.messageType} from ${packet.source}")

                // 1. Send EXACTLY ONE ACK back to sender (requiresAck = false)
                val listenerLang = preferredLanguage // Dynamic listener preference
                val ackMsg = com.astramesh.core.IthantraMessage(
                    senderId = localNodeId,
                    messageType = com.astramesh.core.MessageType.ACK,
                    language = listenerLang,
                    sequenceNumber = ithantra.sequenceNumber,
                    timestamp = System.currentTimeMillis(),
                    text = "ACK"
                )
                AstraLog.d("MeshEngine", "[TX ACK] Sending ACK for seq=${ithantra.sequenceNumber} to ${packet.source}")
                sendPacket(packet.source, ackMsg.toBinary(), com.astramesh.domain.model.MessagePriority.CONTROL)

                // 2. Local Translation if listener language differs
                val isEmergency = ithantra.messageType == com.astramesh.core.MessageType.ALERT ||
                        ithantra.messageType == com.astramesh.core.MessageType.SOS ||
                        com.astramesh.core.EmergencyClassifier.isEmergency(ithantra.text, ithantra.language)

                val textToSpeak: String
                val speechLanguage: com.astramesh.core.Language
                val snippet: String

                if (ithantra.language == listenerLang) {
                    textToSpeak = ithantra.text
                    speechLanguage = listenerLang
                    snippet = "[Voice Note]: ${ithantra.text}"
                } else {
                    val translated = com.astramesh.core.OfflineTranslationEngine.translate(
                        ithantra.text,
                        ithantra.language,
                        listenerLang
                    )
                    textToSpeak = translated
                    speechLanguage = listenerLang
                    snippet = "[Voice Note ${ithantra.language.englishName} -> ${listenerLang.englishName}]: $translated"
                }

                // 3. Persist voice note in DB
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
                    timestamp = ithantra.timestamp,
                    content = snippet,
                    contentType = if (isEmergency) com.astramesh.domain.model.MessageContentType.SYSTEM_ALERT else com.astramesh.domain.model.MessageContentType.AUDIO_NOTE,
                    status = com.astramesh.domain.model.MessageStatus.DELIVERED,
                    priority = if (isEmergency) com.astramesh.domain.model.MessagePriority.EMERGENCY else com.astramesh.domain.model.MessagePriority.DIRECT_MESSAGE
                )
                messageRepo.insertMessage(message)

                // 4. Update Chat with contact title
                val peer = peerRepository?.getPeerByNodeId(packet.source)
                val customName = peer?.displayName?.takeIf { !it.startsWith("Node-") }
                val chatTitle = customName ?: "Node-${packet.source.toHex().take(8)}"

                val existingChat = chatRepo.getChatById(chatId) ?: com.astramesh.domain.model.Chat(
                    id = chatId,
                    title = chatTitle,
                    type = if (packet.destination.isBroadcast) com.astramesh.domain.model.ChatType.BROADCAST else com.astramesh.domain.model.ChatType.DIRECT,
                    participantIds = listOf(localNodeId, packet.source)
                )
                chatRepo.insertOrUpdateChat(
                    existingChat.copy(
                        title = if (customName != null) customName else existingChat.title,
                        lastMessage = message,
                        updatedAt = message.timestamp,
                        unreadCount = existingChat.unreadCount + 1
                    )
                )

                // 5. Synthesize speech on COMPLETE reassembled text
                try {
                    AstraLog.d("MeshEngine", "VOICE_PLAYBACK synthesizing speech text='$textToSpeak' lang=${speechLanguage.name} emergency=$isEmergency")
                    speechSynthesizer?.synthesizeAndPlay(
                        text = textToSpeak,
                        language = speechLanguage,
                        isEmergency = isEmergency
                    )
                } catch (e: Exception) {
                    AstraLog.e("MeshEngine", "TTS playback failed", e)
                }
                return@launch
            }

            if (isVoice) {
                val voicePayload = com.astramesh.core.VoicePayload.deserialize(packet.payload)
                val isEmergency = packet.flags.isEmergency || voicePayload.mode == com.astramesh.core.VoiceMode.EMERGENCY
                val textToSpeak = voicePayload.transcript.takeIf { it.isNotBlank() }
                val snippet = if (textToSpeak != null) "[Voice Note]: $textToSpeak" else "[Voice Note ${voicePayload.mode.name}]"

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
                    content = snippet,
                    contentType = if (isEmergency) com.astramesh.domain.model.MessageContentType.SYSTEM_ALERT else com.astramesh.domain.model.MessageContentType.AUDIO_NOTE,
                    status = com.astramesh.domain.model.MessageStatus.DELIVERED,
                    priority = if (isEmergency) com.astramesh.domain.model.MessagePriority.EMERGENCY else com.astramesh.domain.model.MessagePriority.DIRECT_MESSAGE
                )
                messageRepo.insertMessage(message)

                val peer = peerRepository?.getPeerByNodeId(packet.source)
                val customName = peer?.displayName?.takeIf { !it.startsWith("Node-") }
                val chatTitle = customName ?: "Node-${packet.source.toHex().take(8)}"

                val existingChat = chatRepo.getChatById(chatId) ?: com.astramesh.domain.model.Chat(
                    id = chatId,
                    title = chatTitle,
                    type = if (packet.destination.isBroadcast) com.astramesh.domain.model.ChatType.BROADCAST else com.astramesh.domain.model.ChatType.DIRECT,
                    participantIds = listOf(localNodeId, packet.source)
                )
                chatRepo.insertOrUpdateChat(
                    existingChat.copy(
                        title = if (customName != null) customName else existingChat.title,
                        lastMessage = message,
                        updatedAt = message.timestamp,
                        unreadCount = existingChat.unreadCount + 1
                    )
                )

                textToSpeak?.let { text ->
                    try {
                        val translated = if (preferredLanguage == com.astramesh.core.Language.HINDI) {
                            text
                        } else {
                            com.astramesh.core.OfflineTranslationEngine.translate(text, com.astramesh.core.Language.HINDI, preferredLanguage)
                        }
                        speechSynthesizer?.synthesizeAndPlay(translated, preferredLanguage, isEmergency)
                    } catch (e: Exception) {
                        AstraLog.e("MeshEngine", "TTS playback failed", e)
                    }
                }
                return@launch
            }

            // Fallback standard text
            val text = String(packet.payload, Charsets.UTF_8)
            val chatId = if (packet.destination.isBroadcast) com.astramesh.core.ChatId("chat_broadcast") else com.astramesh.core.ChatId("direct_${packet.source.value}")
            val message = com.astramesh.domain.model.Message(
                id = com.astramesh.core.MessageId(java.util.UUID.randomUUID().toString()),
                chatId = chatId,
                senderId = packet.source,
                recipientId = packet.destination,
                timestamp = System.currentTimeMillis(),
                content = text,
                contentType = com.astramesh.domain.model.MessageContentType.TEXT,
                status = com.astramesh.domain.model.MessageStatus.DELIVERED,
                priority = if (packet.flags.isEmergency) com.astramesh.domain.model.MessagePriority.EMERGENCY else com.astramesh.domain.model.MessagePriority.DIRECT_MESSAGE
            )
            messageRepo.insertMessage(message)

            val peer = peerRepository?.getPeerByNodeId(packet.source)
            val customName = peer?.displayName?.takeIf { !it.startsWith("Node-") }
            val chatTitle = customName ?: "Node-${packet.source.toHex().take(8)}"

            val existingChat = chatRepo.getChatById(chatId) ?: com.astramesh.domain.model.Chat(
                id = chatId,
                title = chatTitle,
                type = if (packet.destination.isBroadcast) com.astramesh.domain.model.ChatType.BROADCAST else com.astramesh.domain.model.ChatType.DIRECT,
                participantIds = listOf(localNodeId, packet.source)
            )
            chatRepo.insertOrUpdateChat(
                existingChat.copy(
                    title = if (customName != null) customName else existingChat.title,
                    lastMessage = message,
                    updatedAt = message.timestamp,
                    unreadCount = existingChat.unreadCount + 1
                )
            )
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
            val existing = peerRepository?.getPeerByNodeId(nodeId)
            val displayName = existing?.displayName ?: "Node-${nodeId.toHex().take(8)}"
            peerRepository?.updatePeer(
                Peer(
                    nodeId = nodeId,
                    deviceAddress = deviceAddress,
                    displayName = displayName,
                    rssi = rssi,
                    linkQuality = 1.0f,
                    hopDistance = 1,
                    directState = DirectConnectionState.DISCONNECTED,
                    trustLevel = existing?.trustLevel ?: PeerTrustLevel.UNVERIFIED,
                    batteryLevel = existing?.batteryLevel ?: 100,
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
