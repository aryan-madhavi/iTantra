package com.astramesh.domain.model

import com.astramesh.core.AttachmentId
import com.astramesh.core.MessageId
import com.astramesh.core.NodeId

enum class PeerTrustLevel {
    UNVERIFIED,
    VERIFIED_IN_PERSON,
    TRUSTED,
    BLOCKED
}

enum class DirectConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING
}

data class Peer(
    val nodeId: NodeId,
    val deviceAddress: String,
    val displayName: String? = null,
    val rssi: Int = -100,
    val linkQuality: Float = 0f, // 0.0 to 1.0
    val hopDistance: Int = 1,
    val directState: DirectConnectionState = DirectConnectionState.DISCONNECTED,
    val trustLevel: PeerTrustLevel = PeerTrustLevel.UNVERIFIED,
    val batteryLevel: Int = 100,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

data class Route(
    val destination: NodeId,
    val nextHop: NodeId,
    val cost: Float,
    val hopCount: Int,
    val sequenceNumber: Long,
    val expireTimestamp: Long
)

enum class AttachmentTransferStatus {
    PENDING,
    UPLOADING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class Attachment(
    val id: AttachmentId,
    val messageId: MessageId,
    val fileName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val localUri: String? = null,
    val encryptionKey: ByteArray? = null,
    val sha256Hash: ByteArray,
    val totalChunks: Int,
    val completedChunks: Int = 0,
    val status: AttachmentTransferStatus = AttachmentTransferStatus.PENDING
) {
    val progress: Float get() = if (totalChunks > 0) completedChunks.toFloat() / totalChunks.toFloat() else 0f

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Attachment) return false
        return id == other.id &&
                messageId == other.messageId &&
                fileName == other.fileName &&
                mimeType == other.mimeType &&
                fileSizeBytes == other.fileSizeBytes &&
                localUri == other.localUri &&
                sha256Hash.contentEquals(other.sha256Hash)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + messageId.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + sha256Hash.contentHashCode()
        return result
    }
}

data class MeshStatus(
    val isAdvertising: Boolean = false,
    val isScanning: Boolean = false,
    val activeConnectionsCount: Int = 0,
    val totalPeersDiscovered: Int = 0,
    val packetsSent: Long = 0L,
    val packetsRelayed: Long = 0L,
    val packetsReceived: Long = 0L,
    val packetsDropped: Long = 0L
)
