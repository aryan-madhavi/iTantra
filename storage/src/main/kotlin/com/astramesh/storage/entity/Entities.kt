package com.astramesh.storage.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chats",
    indices = [Index(value = ["updatedAt"])]
)
data class ChatEntity(
    @PrimaryKey val id: String,
    val title: String,
    val type: String, // DIRECT, GROUP, BROADCAST, EMERGENCY
    val participantIdsJson: String,
    val lastMessageSnippet: String?,
    val unreadCount: Int,
    val updatedAt: Long
)

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["chatId"]),
        Index(value = ["timestamp"]),
        Index(value = ["status"])
    ]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: Long,
    val recipientId: Long,
    val timestamp: Long,
    val content: String,
    val contentType: String,
    val status: String,
    val priority: Int,
    val hopCount: Int,
    val ttl: Int,
    val attachmentId: String?
)

@Entity(
    tableName = "peers",
    indices = [Index(value = ["lastSeenTimestamp"])]
)
data class PeerEntity(
    @PrimaryKey val nodeId: Long,
    val deviceAddress: String,
    val displayName: String?,
    val rssi: Int,
    val linkQuality: Float,
    val hopDistance: Int,
    val directState: String,
    val trustLevel: String,
    val batteryLevel: Int,
    val lastSeenTimestamp: Long
)

@Entity(
    tableName = "routes",
    indices = [Index(value = ["expireTimestamp"])]
)
data class RouteEntity(
    @PrimaryKey val destination: Long,
    val nextHop: Long,
    val cost: Float,
    val hopCount: Int,
    val sequenceNumber: Long,
    val expireTimestamp: Long
)

@Entity(tableName = "attachments")
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val fileName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val localUri: String?,
    val encryptionKey: ByteArray?,
    val sha256Hash: ByteArray,
    val totalChunks: Int,
    val completedChunks: Int,
    val status: String
)

@Entity(
    tableName = "pending_queue",
    indices = [Index(value = ["nextRetryTimestamp"])]
)
data class PendingQueueEntity(
    @PrimaryKey val id: String,
    val destination: Long,
    val payload: ByteArray,
    val priority: Int,
    val attempts: Int,
    val nextRetryTimestamp: Long,
    val createdAt: Long
)

@Entity(tableName = "session_keys")
data class SessionKeyEntity(
    @PrimaryKey val peerNodeId: Long,
    val rootKey: ByteArray,
    val sendingChainKey: ByteArray?,
    val receivingChainKey: ByteArray?,
    val sendSequenceNumber: Long,
    val receiveSequenceNumber: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "audit_logs",
    indices = [Index(value = ["timestamp"])]
)
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val tag: String,
    val level: String,
    val message: String
)
