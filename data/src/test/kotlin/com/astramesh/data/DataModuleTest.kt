package com.astramesh.data

import com.astramesh.core.ChatId
import com.astramesh.core.MessageId
import com.astramesh.core.NodeId
import com.astramesh.data.repository.MessageRepositoryImpl
import com.astramesh.domain.model.Message
import com.astramesh.domain.model.MessageContentType
import com.astramesh.domain.model.MessagePriority
import com.astramesh.domain.model.MessageStatus
import com.astramesh.storage.dao.MessageDao
import com.astramesh.storage.entity.MessageEntity
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DataModuleTest {

    private val messageDao = mockk<MessageDao>(relaxed = true)

    @Test
    fun `message repository maps domain message to entity and inserts into dao`() = runTest {
        val repo = MessageRepositoryImpl(messageDao)

        val message = Message(
            id = MessageId("msg_1"),
            chatId = ChatId("chat_1"),
            senderId = NodeId(10L),
            recipientId = NodeId(20L),
            timestamp = 1000L,
            content = "Testing Data Layer",
            contentType = MessageContentType.TEXT,
            status = MessageStatus.QUEUED,
            priority = MessagePriority.DIRECT_MESSAGE
        )

        val result = repo.insertMessage(message)
        assertThat(result.isSuccess).isTrue()

        coVerify {
            messageDao.insertMessage(
                match { entity ->
                    entity.id == "msg_1" &&
                            entity.chatId == "chat_1" &&
                            entity.content == "Testing Data Layer"
                }
            )
        }
    }

    @Test
    fun `message repository retrieves message by id and converts to domain`() = runTest {
        val repo = MessageRepositoryImpl(messageDao)

        val entity = MessageEntity(
            id = "msg_2",
            chatId = "chat_2",
            senderId = 30L,
            recipientId = 40L,
            timestamp = 2000L,
            content = "Retrieved Content",
            contentType = "TEXT",
            status = "DELIVERED",
            priority = 2,
            hopCount = 1,
            ttl = 6,
            attachmentId = null
        )

        coEvery { messageDao.getMessageById("msg_2") } returns entity

        val domainMsg = repo.getMessageById(MessageId("msg_2"))
        assertThat(domainMsg).isNotNull()
        assertThat(domainMsg?.id?.value).isEqualTo("msg_2")
        assertThat(domainMsg?.content).isEqualTo("Retrieved Content")
        assertThat(domainMsg?.status).isEqualTo(MessageStatus.DELIVERED)
    }
}
