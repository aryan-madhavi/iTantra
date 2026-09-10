package com.astramesh.domain.repository

import com.astramesh.common.AstraResult
import com.astramesh.core.AttachmentId
import com.astramesh.core.ChatId
import com.astramesh.core.MessageId
import com.astramesh.core.NodeId
import com.astramesh.crypto.AstraKeyPair
import com.astramesh.domain.model.Attachment
import com.astramesh.domain.model.Chat
import com.astramesh.domain.model.Message
import com.astramesh.domain.model.MeshStatus
import com.astramesh.domain.model.Peer
import com.astramesh.domain.model.PeerTrustLevel
import com.astramesh.domain.model.Route
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface MessageRepository {
    fun observeMessages(chatId: ChatId): Flow<List<Message>>
    suspend fun getMessageById(id: MessageId): Message?
    suspend fun insertMessage(message: Message): AstraResult<Unit>
    suspend fun updateMessageStatus(messageId: MessageId, status: com.astramesh.domain.model.MessageStatus): AstraResult<Unit>
    suspend fun deleteMessage(id: MessageId): AstraResult<Unit>
}

interface ChatRepository {
    fun observeChats(): Flow<List<Chat>>
    suspend fun getChatById(chatId: ChatId): Chat?
    suspend fun insertOrUpdateChat(chat: Chat): AstraResult<Unit>
    suspend fun markChatAsRead(chatId: ChatId): AstraResult<Unit>
    suspend fun deleteChat(chatId: ChatId): AstraResult<Unit>
}

interface PeerRepository {
    fun observeNearbyPeers(): Flow<List<Peer>>
    suspend fun getPeerByNodeId(nodeId: NodeId): Peer?
    suspend fun updatePeer(peer: Peer): AstraResult<Unit>
    suspend fun updateTrustLevel(nodeId: NodeId, trustLevel: PeerTrustLevel): AstraResult<Unit>
}

interface RoutingRepository {
    fun observeRoutes(): Flow<List<Route>>
    suspend fun getRouteFor(destination: NodeId): Route?
    suspend fun updateRoute(route: Route): AstraResult<Unit>
    suspend fun evictExpiredRoutes(currentTimeMillis: Long): AstraResult<Int>
}

interface IdentityRepository {
    suspend fun getLocalIdentity(): AstraKeyPair
    suspend fun getDisplayName(): String
    suspend fun setDisplayName(name: String): AstraResult<Unit>
    suspend fun getRotatingNodeId(): NodeId
    suspend fun rotateIdentityEpoch(): NodeId
    suspend fun wipeAllCryptographicKeys(): AstraResult<Unit>
}

interface AttachmentRepository {
    fun observeAttachment(id: AttachmentId): Flow<Attachment?>
    suspend fun getAttachment(id: AttachmentId): Attachment?
    suspend fun saveAttachment(attachment: Attachment): AstraResult<Unit>
    suspend fun updateProgress(id: AttachmentId, completedChunks: Int): AstraResult<Unit>
}

interface MeshRepository {
    val meshStatus: StateFlow<MeshStatus>
    suspend fun startMesh(): AstraResult<Unit>
    suspend fun stopMesh(): AstraResult<Unit>
    suspend fun sendPacket(recipientId: NodeId, payload: ByteArray, priority: com.astramesh.domain.model.MessagePriority): AstraResult<Unit>
    suspend fun broadcastEmergency(content: String): AstraResult<Unit>
    fun setPreferredLanguage(language: com.astramesh.core.Language)
}
