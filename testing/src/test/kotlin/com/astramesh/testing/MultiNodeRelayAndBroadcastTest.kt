package com.astramesh.testing

import com.astramesh.core.ChatId
import com.astramesh.core.IthantraMessage
import com.astramesh.core.Language
import com.astramesh.core.MessageId
import com.astramesh.core.MessageType
import com.astramesh.core.NodeId
import com.astramesh.domain.model.MessageContentType
import com.astramesh.domain.model.MessagePriority
import com.astramesh.domain.usecase.SendMessageUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class MultiNodeRelayAndBroadcastTest {

    private lateinit var medium: VirtualBleMedium
    private val nodes = mutableListOf<VirtualMeshNode>()

    @Before
    fun setUp() {
        medium = VirtualBleMedium()
    }

    @After
    fun tearDown() {
        nodes.forEach { it.stop() }
        nodes.clear()
    }

    private fun createNode(idLong: Long): VirtualMeshNode {
        val node = VirtualMeshNode(NodeId(idLong), medium)
        node.start()
        nodes.add(node)
        return node
    }

    @Test
    fun testTrueBroadcastReachesAllNodes() {
        // Topology Star: Node A connected to B, C, and D
        val nodeA = createNode(10L)
        val nodeB = createNode(20L)
        val nodeC = createNode(30L)
        val nodeD = createNode(40L)

        medium.addBidirectionalLink(nodeA.nodeId, nodeB.nodeId, latencyMs = 0L)
        medium.addBidirectionalLink(nodeA.nodeId, nodeC.nodeId, latencyMs = 0L)
        medium.addBidirectionalLink(nodeA.nodeId, nodeD.nodeId, latencyMs = 0L)

        val broadcastPayload = "Voice broadcast to all mesh nodes".toByteArray()
        val pid = nodeA.sendPacket(NodeId.BROADCAST, broadcastPayload, priority = MessagePriority.GROUP_MESSAGE)

        Thread.sleep(150)

        // All 3 receiver nodes must receive the broadcast
        assertThat(nodeB.packetsReceived.get()).isAtLeast(1L)
        assertThat(nodeC.packetsReceived.get()).isAtLeast(1L)
        assertThat(nodeD.packetsReceived.get()).isAtLeast(1L)

        val bPkt = nodeB.receivedPacketsHistory.firstOrNull { it.packetId == pid }
        val cPkt = nodeC.receivedPacketsHistory.firstOrNull { it.packetId == pid }
        val dPkt = nodeD.receivedPacketsHistory.firstOrNull { it.packetId == pid }

        assertThat(bPkt).isNotNull()
        assertThat(cPkt).isNotNull()
        assertThat(dPkt).isNotNull()

        assertThat(bPkt!!.payload).isEqualTo(broadcastPayload)
        assertThat(cPkt!!.payload).isEqualTo(broadcastPayload)
        assertThat(dPkt!!.payload).isEqualTo(broadcastPayload)

        // Node A should not receive its own broadcast
        assertThat(nodeA.packetsReceived.get()).isEqualTo(0L)
    }

    @Test
    fun testMultiHopLinearRelay() {
        // Topology: A <-> B (Relay) <-> C
        val nodeA = createNode(100L)
        val nodeB = createNode(200L) // Relay
        val nodeC = createNode(300L)

        medium.addBidirectionalLink(nodeA.nodeId, nodeB.nodeId, latencyMs = 0L)
        medium.addBidirectionalLink(nodeB.nodeId, nodeC.nodeId, latencyMs = 0L)

        // Node A knows route to C via B
        nodeA.routingTable.updateRoute(nodeC.nodeId, nodeB.nodeId, cost = 2.0f, hopCount = 2, sequenceNumber = 1L)
        // Relay Node B knows direct route to C
        nodeB.routingTable.updateRoute(nodeC.nodeId, nodeC.nodeId, cost = 1.0f, hopCount = 1, sequenceNumber = 1L)

        val voicePayload = "Multi-hop relay voice payload from A to C".toByteArray()
        val pid = nodeA.sendPacket(nodeC.nodeId, voicePayload)

        Thread.sleep(150)

        // Relay B forwarded it
        assertThat(nodeB.packetsRelayed.get()).isEqualTo(1L)
        // Node C consumed it
        assertThat(nodeC.packetsReceived.get()).isEqualTo(1L)

        val received = nodeC.receivedPacketsHistory.firstOrNull { it.packetId == pid }
        assertThat(received).isNotNull()
        assertThat(received!!.source).isEqualTo(nodeA.nodeId)
        assertThat(received.destination).isEqualTo(nodeC.nodeId)
        assertThat(received.payload).isEqualTo(voicePayload)
        assertThat(received.hopCount).isEqualTo(1) // incremented by 1 hop at relay B
    }

    @Test
    fun testReversePathRouteLearningAndTwoWayUnicast() {
        // Topology: A <-> B (Relay) <-> C
        val nodeA = createNode(111L)
        val nodeB = createNode(222L) // Relay
        val nodeC = createNode(333L)

        medium.addBidirectionalLink(nodeA.nodeId, nodeB.nodeId, latencyMs = 0L)
        medium.addBidirectionalLink(nodeB.nodeId, nodeC.nodeId, latencyMs = 0L)

        // Only Node A knows route to C via B initially.
        // Neither B nor C have pre-configured routes back to A!
        nodeA.routingTable.updateRoute(nodeC.nodeId, nodeB.nodeId, cost = 2.0f, hopCount = 2, sequenceNumber = 1L)
        nodeB.routingTable.updateRoute(nodeC.nodeId, nodeC.nodeId, cost = 1.0f, hopCount = 1, sequenceNumber = 1L)

        assertThat(nodeC.routingTable.getRoute(nodeA.nodeId)).isNull()
        assertThat(nodeB.routingTable.getRoute(nodeA.nodeId)).isNull()

        // Node A sends packet to C
        val forwardPayload = "Hello from Node A".toByteArray()
        val pidA = nodeA.sendPacket(nodeC.nodeId, forwardPayload)

        Thread.sleep(150)

        // Verify Node C received packet
        assertThat(nodeC.packetsReceived.get()).isEqualTo(1L)

        // REVERSE-PATH VERIFICATION:
        // Relay B and Destination C must have automatically learned route back to Node A!
        val routeAtB = nodeB.routingTable.getRoute(nodeA.nodeId)
        assertThat(routeAtB).isNotNull()
        assertThat(routeAtB!!.nextHop).isEqualTo(nodeA.nodeId)

        val routeAtC = nodeC.routingTable.getRoute(nodeA.nodeId)
        assertThat(routeAtC).isNotNull()
        assertThat(routeAtC!!.nextHop).isEqualTo(nodeB.nodeId)
        assertThat(routeAtC.hopCount).isEqualTo(2)

        // Now Node C sends a unicast reply back to Node A using the auto-learned route!
        val replyPayload = "Reply from Node C to Node A".toByteArray()
        val pidC = nodeC.sendPacket(nodeA.nodeId, replyPayload)

        Thread.sleep(150)

        // Node A must successfully receive Node C's reply via relay B!
        assertThat(nodeA.packetsReceived.get()).isEqualTo(1L)
        val replyAtA = nodeA.receivedPacketsHistory.firstOrNull { it.packetId == pidC }
        assertThat(replyAtA).isNotNull()
        assertThat(replyAtA!!.source).isEqualTo(nodeC.nodeId)
        assertThat(replyAtA.destination).isEqualTo(nodeA.nodeId)
        assertThat(replyAtA.payload).isEqualTo(replyPayload)
    }

    @Test
    fun testEmergencyBroadcastFloodingWithTtl15() {
        // Topology 4-node chain: A <-> B <-> C <-> D
        val nodeA = createNode(1000L)
        val nodeB = createNode(2000L)
        val nodeC = createNode(3000L)
        val nodeD = createNode(4000L)

        medium.addBidirectionalLink(nodeA.nodeId, nodeB.nodeId, latencyMs = 0L)
        medium.addBidirectionalLink(nodeB.nodeId, nodeC.nodeId, latencyMs = 0L)
        medium.addBidirectionalLink(nodeC.nodeId, nodeD.nodeId, latencyMs = 0L)

        val emergencyText = "CRITICAL SOS: FLOODING EMERGENCY"
        val emergencyPayload = emergencyText.toByteArray()
        val pid = nodeA.sendPacket(NodeId.BROADCAST, emergencyPayload, priority = MessagePriority.EMERGENCY)

        Thread.sleep(200)

        // All nodes along the chain must receive the emergency broadcast
        assertThat(nodeB.packetsReceived.get()).isAtLeast(1L)
        assertThat(nodeC.packetsReceived.get()).isAtLeast(1L)
        assertThat(nodeD.packetsReceived.get()).isAtLeast(1L)

        // Relay nodes must have forwarded it
        assertThat(nodeB.packetsRelayed.get()).isAtLeast(1L)
        assertThat(nodeC.packetsRelayed.get()).isAtLeast(1L)

        val pktAtD = nodeD.receivedPacketsHistory.firstOrNull { it.packetId == pid }
        assertThat(pktAtD).isNotNull()
        assertThat(pktAtD!!.flags.isEmergency).isTrue()
        assertThat(pktAtD.hopCount).isEqualTo(3) // A -> B -> C -> D (3 hops traversed)
        assertThat(pktAtD.ttl).isEqualTo(12) // 15 - 3 = 12
    }

    @Test
    fun testLoopPreventionInTriangleMesh() {
        // Fully-connected triangle topology: A <-> B <-> C <-> A
        val nodeA = createNode(501L)
        val nodeB = createNode(502L)
        val nodeC = createNode(503L)

        medium.addBidirectionalLink(nodeA.nodeId, nodeB.nodeId, latencyMs = 0L)
        medium.addBidirectionalLink(nodeB.nodeId, nodeC.nodeId, latencyMs = 0L)
        medium.addBidirectionalLink(nodeC.nodeId, nodeA.nodeId, latencyMs = 0L)

        val broadcastMsg = "Testing loop prevention in mesh".toByteArray()
        val pid = nodeA.sendPacket(NodeId.BROADCAST, broadcastMsg)

        Thread.sleep(200)

        // B and C should receive it exactly once in their history, with subsequent loops dropped
        val bPackets = nodeB.receivedPacketsHistory.filter { it.packetId == pid }
        val cPackets = nodeC.receivedPacketsHistory.filter { it.packetId == pid }

        assertThat(bPackets.size).isEqualTo(1)
        assertThat(cPackets.size).isEqualTo(1)

        // Node A should NOT receive its own broadcast
        val aPackets = nodeA.receivedPacketsHistory.filter { it.packetId == pid }
        assertThat(aPackets.size).isEqualTo(0)

        // Drop counters should have recorded duplicate / loop attempts
        val totalDropped = nodeA.packetsDropped.get() + nodeB.packetsDropped.get() + nodeC.packetsDropped.get()
        assertThat(totalDropped).isAtLeast(1L)
    }

    @Test
    fun testPttDoesNotTriggerEmergencyAutomaticallyOnDistressWords() = runBlocking {
        val mockMeshRepo = object : com.astramesh.domain.repository.MeshRepository {
            var lastRecipient: NodeId? = null
            var lastPriority: MessagePriority? = null
            var lastPayload: ByteArray? = null

            override val meshStatus = kotlinx.coroutines.flow.MutableStateFlow(com.astramesh.domain.model.MeshStatus())
            override suspend fun startMesh() = com.astramesh.common.AstraResult.Success(Unit)
            override suspend fun stopMesh() = com.astramesh.common.AstraResult.Success(Unit)
            override suspend fun sendPacket(recipientId: NodeId, payload: ByteArray, priority: MessagePriority): com.astramesh.common.AstraResult<Unit> {
                lastRecipient = recipientId
                lastPriority = priority
                lastPayload = payload
                return com.astramesh.common.AstraResult.Success(Unit)
            }
            override suspend fun broadcastEmergency(content: String) = com.astramesh.common.AstraResult.Success(Unit)
            override fun setPreferredLanguage(language: Language) {}
        }

        val mockMessageRepo = object : com.astramesh.domain.repository.MessageRepository {
            val messages = mutableListOf<com.astramesh.domain.model.Message>()
            override suspend fun insertMessage(message: com.astramesh.domain.model.Message): com.astramesh.common.AstraResult<Unit> {
                messages.add(message)
                return com.astramesh.common.AstraResult.Success(Unit)
            }
            override fun observeMessages(chatId: ChatId) = flowOf(messages)
            override suspend fun getMessageById(id: MessageId): com.astramesh.domain.model.Message? = messages.find { it.id == id }
            override suspend fun updateMessageStatus(messageId: MessageId, status: com.astramesh.domain.model.MessageStatus) = com.astramesh.common.AstraResult.Success(Unit)
            override suspend fun deleteMessage(id: MessageId) = com.astramesh.common.AstraResult.Success(Unit)
        }

        val mockChatRepo = object : com.astramesh.domain.repository.ChatRepository {
            override fun observeChats() = flowOf(emptyList<com.astramesh.domain.model.Chat>())
            override suspend fun getChatById(chatId: ChatId): com.astramesh.domain.model.Chat? = null
            override suspend fun insertOrUpdateChat(chat: com.astramesh.domain.model.Chat) = com.astramesh.common.AstraResult.Success(Unit)
            override suspend fun markChatAsRead(chatId: ChatId) = com.astramesh.common.AstraResult.Success(Unit)
            override suspend fun deleteChat(chatId: ChatId) = com.astramesh.common.AstraResult.Success(Unit)
        }

        val mockIdentityRepo = object : com.astramesh.domain.repository.IdentityRepository {
            override suspend fun getLocalIdentity(): com.astramesh.crypto.AstraKeyPair = com.astramesh.crypto.AstraKeyPair.generate()
            override suspend fun getDisplayName(): String = "TestNode"
            override suspend fun setDisplayName(name: String) = com.astramesh.common.AstraResult.Success(Unit)
            override suspend fun getRotatingNodeId(): NodeId = NodeId(999L)
            override suspend fun rotateIdentityEpoch(): NodeId = NodeId(999L)
            override suspend fun wipeAllCryptographicKeys() = com.astramesh.common.AstraResult.Success(Unit)
        }

        val useCase = SendMessageUseCase(mockMessageRepo, mockChatRepo, mockIdentityRepo, mockMeshRepo)

        // 1. PTT message with distress words ("help emergency fire police") but isEmergency = false
        val resultNormal = useCase.sendIthantraVoiceMessage(
            chatId = ChatId("chat_broadcast"),
            recipientId = NodeId.BROADCAST,
            text = "Please help me, we need police and fire support at checkpoint",
            language = Language.ENGLISH,
            isEmergency = false
        )

        assertThat(resultNormal.isSuccess).isTrue()
        val normalMsg = (resultNormal as com.astramesh.common.AstraResult.Success).value
        assertThat(normalMsg.contentType).isEqualTo(MessageContentType.AUDIO_NOTE)
        assertThat(normalMsg.priority).isEqualTo(MessagePriority.GROUP_MESSAGE)

        // Verify serialized IthantraMessage inside payload is NORMAL, NOT ALERT
        val ithantraMsgNormal = IthantraMessage.fromBinary(mockMeshRepo.lastPayload!!)
        assertThat(ithantraMsgNormal.messageType).isEqualTo(MessageType.NORMAL)

        // 2. Explicit Emergency SOS with isEmergency = true
        val resultEmergency = useCase.sendIthantraVoiceMessage(
            chatId = ChatId("chat_emergency_broadcast"),
            recipientId = NodeId.BROADCAST,
            text = "MAYDAY MAYDAY SOS",
            language = Language.ENGLISH,
            isEmergency = true
        )

        assertThat(resultEmergency.isSuccess).isTrue()
        val emergencyMsg = (resultEmergency as com.astramesh.common.AstraResult.Success).value
        assertThat(emergencyMsg.contentType).isEqualTo(MessageContentType.SYSTEM_ALERT)
        assertThat(emergencyMsg.priority).isEqualTo(MessagePriority.EMERGENCY)

        val ithantraMsgEmergency = IthantraMessage.fromBinary(mockMeshRepo.lastPayload!!)
        assertThat(ithantraMsgEmergency.messageType).isEqualTo(MessageType.ALERT)
    }
}
