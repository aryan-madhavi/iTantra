package com.astramesh.domain

import com.astramesh.common.AstraResult
import com.astramesh.core.ChatId
import com.astramesh.core.MessageId
import com.astramesh.core.NodeId
import com.astramesh.domain.model.Chat
import com.astramesh.domain.model.Message
import com.astramesh.domain.model.MessagePriority
import com.astramesh.domain.model.MessageStatus
import com.astramesh.domain.repository.ChatRepository
import com.astramesh.domain.repository.IdentityRepository
import com.astramesh.domain.repository.MessageRepository
import com.astramesh.domain.repository.MeshRepository
import com.astramesh.domain.usecase.SendMessageUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DomainModuleTest {

    private val messageRepo = mockk<MessageRepository>(relaxed = true)
    private val chatRepo = mockk<ChatRepository>(relaxed = true)
    private val identityRepo = mockk<IdentityRepository>(relaxed = true)
    private val meshRepo = mockk<MeshRepository>(relaxed = true)

    @org.junit.Before
    fun setUp() {
        io.mockk.clearMocks(messageRepo, chatRepo, identityRepo, meshRepo)
    }

    @Test
    fun `send message use case persists and dispatches packet`() = runTest {
        val senderNodeId = NodeId(0x1111222233334444L)
        val recipientNodeId = NodeId(0x5555666677778888L)
        val chatId = ChatId("chat_123")

        coEvery { identityRepo.getRotatingNodeId() } returns senderNodeId
        coEvery { messageRepo.insertMessage(any()) } returns AstraResult.Success(Unit)
        coEvery { chatRepo.getChatById(chatId) } returns null
        coEvery { chatRepo.insertOrUpdateChat(any()) } returns AstraResult.Success(Unit)
        coEvery { meshRepo.sendPacket(any(), any(), any()) } returns AstraResult.Success(Unit)
        coEvery { messageRepo.updateMessageStatus(any(), any()) } returns AstraResult.Success(Unit)

        val useCase = SendMessageUseCase(messageRepo, chatRepo, identityRepo, meshRepo)
        val result = useCase(chatId, recipientNodeId, "Hello Mesh", MessagePriority.DIRECT_MESSAGE)

        assertThat(result.isSuccess).isTrue()
        val message = (result as AstraResult.Success).value
        assertThat(message.content).isEqualTo("Hello Mesh")
        assertThat(message.senderId).isEqualTo(senderNodeId)
        assertThat(message.recipientId).isEqualTo(recipientNodeId)

        coVerify { messageRepo.insertMessage(any()) }
        coVerify { meshRepo.sendPacket(recipientNodeId, any(), MessagePriority.DIRECT_MESSAGE) }
        coVerify { messageRepo.updateMessageStatus(message.id, MessageStatus.SENT) }
    }

    @Test
    fun `two-stage emergency broadcast use case dispatches stage 1 immediate sos beacon`() = runTest {
        val senderNodeId = NodeId(0x1999888877776666L)
        coEvery { identityRepo.getRotatingNodeId() } returns senderNodeId
        coEvery { messageRepo.insertMessage(any()) } returns AstraResult.Success(Unit)
        coEvery { chatRepo.getChatById(any()) } returns null
        coEvery { chatRepo.insertOrUpdateChat(any()) } returns AstraResult.Success(Unit)
        coEvery { meshRepo.sendPacket(any(), any(), any()) } returns AstraResult.Success(Unit)

        val emergencyUseCase = com.astramesh.domain.usecase.EmergencyBroadcastUseCase(
            meshRepository = meshRepo,
            messageRepository = messageRepo,
            chatRepository = chatRepo,
            identityRepository = identityRepo
        )

        val stage1Result = emergencyUseCase.sendImmediateSosBeacon(com.astramesh.core.Language.HINDI)
        assertThat(stage1Result.isSuccess).isTrue()
        val stage1Message = (stage1Result as AstraResult.Success).value
        assertThat(stage1Message.priority).isEqualTo(MessagePriority.EMERGENCY)
        assertThat(stage1Message.recipientId).isEqualTo(NodeId.BROADCAST)
        assertThat(stage1Message.ttl).isEqualTo(15)

        val payloadSlot = io.mockk.slot<ByteArray>()
        coVerify(exactly = 1) {
            meshRepo.sendPacket(
                NodeId.BROADCAST,
                capture(payloadSlot),
                MessagePriority.EMERGENCY
            )
        }

        val captured = payloadSlot.captured
        assertThat(com.astramesh.core.IthantraMessage.isIthantraMessage(captured)).isTrue()
        val ithantra = com.astramesh.core.IthantraMessage.fromBinary(captured)
        assertThat(ithantra.messageType).isEqualTo(com.astramesh.core.MessageType.SOS)
        assertThat(ithantra.hopTtl).isEqualTo(15.toByte())
    }

    @Test
    fun `two-stage emergency broadcast use case dispatches stage 2 voice briefing`() = runTest {
        val senderNodeId = NodeId(0x1999888877776666L)
        coEvery { identityRepo.getRotatingNodeId() } returns senderNodeId
        coEvery { messageRepo.insertMessage(any()) } returns AstraResult.Success(Unit)
        coEvery { chatRepo.getChatById(any()) } returns null
        coEvery { chatRepo.insertOrUpdateChat(any()) } returns AstraResult.Success(Unit)
        coEvery { meshRepo.sendPacket(any(), any(), any()) } returns AstraResult.Success(Unit)

        val emergencyUseCase = com.astramesh.domain.usecase.EmergencyBroadcastUseCase(
            meshRepository = meshRepo,
            messageRepository = messageRepo,
            chatRepository = chatRepo,
            identityRepository = identityRepo
        )

        val briefingText = "Building collapsed, 3 survivors trapped on 2nd floor, need medical assistance."
        val stage2Result = emergencyUseCase.sendVoiceBriefing(briefingText, com.astramesh.core.Language.ENGLISH)
        assertThat(stage2Result.isSuccess).isTrue()
        val stage2Message = (stage2Result as AstraResult.Success).value
        assertThat(stage2Message.priority).isEqualTo(MessagePriority.EMERGENCY)
        assertThat(stage2Message.content).contains(briefingText)

        val payloadSlot = io.mockk.slot<ByteArray>()
        coVerify(exactly = 1) {
            meshRepo.sendPacket(
                NodeId.BROADCAST,
                capture(payloadSlot),
                MessagePriority.EMERGENCY
            )
        }

        val captured = payloadSlot.captured
        assertThat(com.astramesh.core.IthantraMessage.isIthantraMessage(captured)).isTrue()
        val ithantra = com.astramesh.core.IthantraMessage.fromBinary(captured)
        assertThat(ithantra.messageType).isEqualTo(com.astramesh.core.MessageType.ALERT)
        assertThat(ithantra.text).contains("Building collapsed")
        assertThat(ithantra.hopTtl).isEqualTo(15.toByte())
    }

    @Test
    fun `discover peers use case evicts stale peers older than timeout`() = runTest {
        val peerRepo = mockk<com.astramesh.domain.repository.PeerRepository>()
        val now = System.currentTimeMillis()
        val activePeer = com.astramesh.domain.model.Peer(
            nodeId = NodeId(0x1111L),
            deviceAddress = "AA:BB:CC:DD:EE:01",
            displayName = "ActiveNode",
            lastSeenTimestamp = now - 2000L // 2 seconds ago (active)
        )
        val stalePeer = com.astramesh.domain.model.Peer(
            nodeId = NodeId(0x2222L),
            deviceAddress = "AA:BB:CC:DD:EE:02",
            displayName = "StaleNode",
            lastSeenTimestamp = now - 25000L // 25 seconds ago (stale > 15s)
        )

        coEvery { peerRepo.observeNearbyPeers() } returns kotlinx.coroutines.flow.flowOf(listOf(activePeer, stalePeer))

        val useCase = com.astramesh.domain.usecase.DiscoverPeersUseCase(peerRepo)
        val filteredPeers = useCase(timeoutMs = 15000L).first()

        assertThat(filteredPeers.size).isEqualTo(1)
        assertThat(filteredPeers[0].nodeId).isEqualTo(activePeer.nodeId)
    }
}
