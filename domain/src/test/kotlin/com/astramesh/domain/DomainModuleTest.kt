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
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DomainModuleTest {

    private val messageRepo = mockk<MessageRepository>(relaxed = true)
    private val chatRepo = mockk<ChatRepository>(relaxed = true)
    private val identityRepo = mockk<IdentityRepository>(relaxed = true)
    private val meshRepo = mockk<MeshRepository>(relaxed = true)

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
}
