package com.astramesh.data.repository

import com.astramesh.common.AstraResult
import com.astramesh.core.ChatId
import com.astramesh.core.NodeId
import com.astramesh.domain.model.Chat
import com.astramesh.domain.model.ChatType
import com.astramesh.domain.repository.ChatRepository
import com.astramesh.storage.dao.ChatDao
import com.astramesh.storage.entity.ChatEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepositoryImpl(
    private val chatDao: ChatDao
) : ChatRepository {

    override fun observeChats(): Flow<List<Chat>> {
        return chatDao.observeAllChats().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getChatById(chatId: ChatId): Chat? {
        return chatDao.getChatById(chatId.value)?.toDomain()
    }

    override suspend fun insertOrUpdateChat(chat: Chat): AstraResult<Unit> = AstraResult.of {
        chatDao.insertOrUpdate(chat.toEntity())
    }

    override suspend fun markChatAsRead(chatId: ChatId): AstraResult<Unit> = AstraResult.of {
        chatDao.markAsRead(chatId.value)
    }

    override suspend fun deleteChat(chatId: ChatId): AstraResult<Unit> = AstraResult.of {
        chatDao.deleteChat(chatId.value)
    }

    private fun ChatEntity.toDomain(): Chat {
        val participants = try {
            participantIdsJson.removeSurrounding("[", "]")
                .split(",")
                .filter { it.isNotBlank() }
                .map { NodeId(it.trim().toLong()) }
        } catch (_: Exception) {
            emptyList()
        }

        return Chat(
            id = ChatId(id),
            title = title,
            type = ChatType.valueOf(type),
            participantIds = participants,
            lastMessage = null, // Can be resolved if needed
            unreadCount = unreadCount,
            updatedAt = updatedAt
        )
    }

    private fun Chat.toEntity(): ChatEntity {
        val participantsJson = participantIds.map { it.value }.joinToString(prefix = "[", postfix = "]")
        return ChatEntity(
            id = id.value,
            title = title,
            type = type.name,
            participantIdsJson = participantsJson,
            lastMessageSnippet = lastMessage?.content,
            unreadCount = unreadCount,
            updatedAt = updatedAt
        )
    }
}
