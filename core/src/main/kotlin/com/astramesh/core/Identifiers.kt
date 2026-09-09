package com.astramesh.core

import com.astramesh.common.ByteUtils
import com.astramesh.crypto.AstraPublicKey
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * 64-bit identifier representing a node in the AstraMesh network.
 */
@JvmInline
value class NodeId(val value: Long) : Comparable<NodeId> {

    val isBroadcast: Boolean get() = value == BROADCAST_VALUE

    fun toHex(): String {
        val buf = ByteArray(8)
        ByteUtils.putUInt64BE(value, buf, 0)
        return ByteUtils.toHexString(buf)
    }

    override fun compareTo(other: NodeId): Int = value.compareTo(other.value)

    override fun toString(): String = if (isBroadcast) "NodeId[BROADCAST]" else "NodeId[${toHex()}]"

    companion object {
        const val BROADCAST_VALUE: Long = -1L // 0xFFFFFFFFFFFFFFFFL

        val BROADCAST = NodeId(BROADCAST_VALUE)

        fun fromHex(hex: String): NodeId {
            val bytes = ByteUtils.hexToByteArray(hex)
            require(bytes.size == 8) { "NodeId hex must represent 8 bytes, got ${bytes.size}" }
            return NodeId(ByteUtils.getUInt64BE(bytes, 0))
        }

        fun fromPublicKey(publicKey: AstraPublicKey): NodeId {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(publicKey.rawBytes)
            return NodeId(ByteUtils.getUInt64BE(hash, 0))
        }
    }
}

/**
 * 64-bit deterministic packet identifier ($H(\text{Origin} \parallel \text{Seq} \parallel \text{Timestamp})$).
 */
@JvmInline
value class PacketId(val value: Long) : Comparable<PacketId> {

    fun toHex(): String {
        val buf = ByteArray(8)
        ByteUtils.putUInt64BE(value, buf, 0)
        return ByteUtils.toHexString(buf)
    }

    override fun compareTo(other: PacketId): Int = value.compareTo(other.value)

    override fun toString(): String = "PacketId[${toHex()}]"

    companion object {
        fun generate(originNodeId: NodeId, sequenceNumber: Long, timestampMillis: Long): PacketId {
            val buffer = ByteBuffer.allocate(24)
            buffer.putLong(originNodeId.value)
            buffer.putLong(sequenceNumber)
            buffer.putLong(timestampMillis)

            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(buffer.array())
            return PacketId(ByteUtils.getUInt64BE(hash, 0))
        }

        fun fromHex(hex: String): PacketId {
            val bytes = ByteUtils.hexToByteArray(hex)
            require(bytes.size == 8) { "PacketId hex must represent 8 bytes, got ${bytes.size}" }
            return PacketId(ByteUtils.getUInt64BE(bytes, 0))
        }
    }
}

/**
 * Strongly-typed domain identifiers.
 */
@JvmInline
value class ChatId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class MessageId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class AttachmentId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class SessionId(val value: String) {
    override fun toString(): String = value
}
