package com.astramesh.data.repository

import com.astramesh.common.AstraResult
import com.astramesh.core.AttachmentId
import com.astramesh.core.ChatId
import com.astramesh.core.MessageId
import com.astramesh.core.NodeId
import com.astramesh.domain.model.Message
import com.astramesh.domain.model.MessageContentType
import com.astramesh.domain.model.MessagePriority
import com.astramesh.domain.model.MessageStatus
import com.astramesh.domain.repository.MessageRepository
import com.astramesh.storage.dao.MessageDao
import com.astramesh.storage.entity.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MessageRepositoryImpl(
    private val messageDao: MessageDao
) : MessageRepository {

    override fun observeMessages(chatId: ChatId): Flow<List<Message>> {
        return messageDao.observeMessages(chatId.value).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getMessageById(id: MessageId): Message? {
        return messageDao.getMessageById(id.value)?.toDomain()
    }

    override suspend fun insertMessage(message: Message): AstraResult<Unit> = AstraResult.of {
        messageDao.insertMessage(message.toEntity())
    }

    override suspend fun updateMessageStatus(messageId: MessageId, status: MessageStatus): AstraResult<Unit> = AstraResult.of {
        messageDao.updateStatus(messageId.value, status.name)
    }

    override suspend fun deleteMessage(id: MessageId): AstraResult<Unit> = AstraResult.of {
        messageDao.deleteMessage(id.value)
    }

    override suspend fun clearAllMessages(): AstraResult<Unit> = AstraResult.of {
        messageDao.clearAll()
    }

    private fun MessageEntity.toDomain(): Message {
        return Message(
            id = MessageId(id),
            chatId = ChatId(chatId),
            senderId = NodeId(senderId),
            recipientId = NodeId(recipientId),
            timestamp = timestamp,
            content = content,
            contentType = MessageContentType.valueOf(contentType),
            status = MessageStatus.valueOf(status),
            priority = MessagePriority.entries.firstOrNull { it.level == priority } ?: MessagePriority.DIRECT_MESSAGE,
            hopCount = hopCount,
            ttl = ttl,
            attachmentId = attachmentId?.let { AttachmentId(it) }
        )
    }

    private fun Message.toEntity(): MessageEntity {
        return MessageEntity(
            id = id.value,
            chatId = chatId.value,
            senderId = senderId.value,
            recipientId = recipientId.value,
            timestamp = timestamp,
            content = content,
            contentType = contentType.name,
            status = status.name,
            priority = priority.level,
            hopCount = hopCount,
            ttl = ttl,
            attachmentId = attachmentId?.value
        )
    }
}
