package com.astramesh.domain.model

import com.astramesh.core.AttachmentId
import com.astramesh.core.ChatId
import com.astramesh.core.MessageId
import com.astramesh.core.NodeId

enum class MessageContentType {
    TEXT,
    ATTACHMENT_INFO,
    AUDIO_NOTE,
    LOCATION,
    SYSTEM_ALERT
}

enum class MessageStatus {
    QUEUED,
    TRANSMITTING,
    SENT,
    RELAYED,
    DELIVERED,
    READ,
    FAILED
}

enum class MessagePriority(val level: Int) {
    EMERGENCY(0),
    CONTROL(1),
    DIRECT_MESSAGE(2),
    GROUP_MESSAGE(3),
    BULK(4)
}

data class Message(
    val id: MessageId,
    val chatId: ChatId,
    val senderId: NodeId,
    val recipientId: NodeId,
    val timestamp: Long,
    val content: String,
    val originalText: String = "",
    val originalLanguage: String = "",
    val translatedText: String? = null,
    val translatedLanguage: String? = null,
    val wasTranslated: Boolean = false,
    val contentType: MessageContentType = MessageContentType.TEXT,
    val status: MessageStatus = MessageStatus.QUEUED,
    val priority: MessagePriority = MessagePriority.DIRECT_MESSAGE,
    val hopCount: Int = 0,
    val ttl: Int = 7,
    val attachmentId: AttachmentId? = null
)

enum class ChatType {
    DIRECT,
    GROUP,
    BROADCAST,
    EMERGENCY
}

data class Chat(
    val id: ChatId,
    val title: String,
    val type: ChatType,
    val participantIds: List<NodeId>,
    val lastMessage: Message? = null,
    val unreadCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
