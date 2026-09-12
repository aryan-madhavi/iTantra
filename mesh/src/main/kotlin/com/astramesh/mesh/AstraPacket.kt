package com.astramesh.mesh

import com.astramesh.common.ByteUtils
import com.astramesh.common.Crc32
import com.astramesh.core.AstraNetworkConfig
import com.astramesh.core.NodeId
import com.astramesh.core.PacketId
import java.nio.ByteBuffer

enum class AstraPacketType(val value: Int) {
    BEACON_DISCOVERY(0x1),
    LINK_HEARTBEAT(0x2),
    ROUTE_REQUEST(0x3),
    ROUTE_REPLY(0x4),
    DATA_UNICAST(0x5),
    DATA_BROADCAST(0x6),
    DATA_ACK(0x7),
    DATA_NACK(0x8),
    KEY_EXCHANGE(0x9),
    ATTACHMENT_CHUNK(0xA);

    companion object {
        fun fromValue(value: Int): AstraPacketType {
            return entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unknown AstraPacketType: $value")
        }
    }
}

data class AstraPacketFlags(
    val isEmergency: Boolean = false,
    val requiresAck: Boolean = false,
    val isCompressed: Boolean = false,
    val isEncrypted: Boolean = true,
    val relayAllowed: Boolean = true
) {
    fun toByte(): Byte {
        var b = 0
        if (isEmergency) b = b or (1 shl 0)
        if (requiresAck) b = b or (1 shl 1)
        if (isCompressed) b = b or (1 shl 2)
        if (isEncrypted) b = b or (1 shl 3)
        if (relayAllowed) b = b or (1 shl 4)
        return b.toByte()
    }

    companion object {
        fun fromByte(byte: Byte): AstraPacketFlags {
            val b = byte.toInt() and 0xFF
            return AstraPacketFlags(
                isEmergency = (b and (1 shl 0)) != 0,
                requiresAck = (b and (1 shl 1)) != 0,
                isCompressed = (b and (1 shl 2)) != 0,
                isEncrypted = (b and (1 shl 3)) != 0,
                relayAllowed = (b and (1 shl 4)) != 0
            )
        }
    }
}

/**
 * AstraMesh binary packet definition.
 * Total Header: 40 bytes + variable payload + 4 bytes CRC-32.
 */
data class AstraPacket(
    val version: Int = AstraNetworkConfig.PROTOCOL_VERSION,
    val type: AstraPacketType,
    val flags: AstraPacketFlags,
    val ttl: Int = AstraNetworkConfig.DEFAULT_TTL,
    val hopCount: Int = 0,
    val sequenceNumber: Long,
    val packetId: PacketId,
    val source: NodeId,
    val destination: NodeId,
    val visitedBloomFilter: Int = 0,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AstraPacket) return false
        return packetId == other.packetId &&
                version == other.version &&
                type == other.type &&
                flags == other.flags &&
                ttl == other.ttl &&
                hopCount == other.hopCount &&
                sequenceNumber == other.sequenceNumber &&
                source == other.source &&
                destination == other.destination &&
                visitedBloomFilter == other.visitedBloomFilter &&
                payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = packetId.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + sequenceNumber.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + destination.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }

    companion object {
        const val HEADER_SIZE = 38
        const val TRAILER_SIZE = 4 // CRC-32

        fun serialize(packet: AstraPacket): ByteArray {
            val totalSize = HEADER_SIZE + packet.payload.size + TRAILER_SIZE
            val buffer = ByteBuffer.allocate(totalSize)

            // Magic (2 bytes)
            buffer.put(AstraNetworkConfig.MAGIC_BYTE_0)
            buffer.put(AstraNetworkConfig.MAGIC_BYTE_1)

            // Version (4 bits) | Type (4 bits) (1 byte)
            val verAndType = ((packet.version and 0x0F) shl 4) or (packet.type.value and 0x0F)
            buffer.put(verAndType.toByte())

            // Flags (1 byte)
            buffer.put(packet.flags.toByte())

            // TTL (1 byte)
            buffer.put((packet.ttl and 0xFF).toByte())

            // Hop Count (1 byte)
            buffer.put((packet.hopCount and 0xFF).toByte())

            // Sequence Number (2 bytes UInt16)
            buffer.putShort((packet.sequenceNumber and 0xFFFF).toShort())

            // Packet ID (8 bytes UInt64)
            buffer.putLong(packet.packetId.value)

            // Source Node ID (8 bytes UInt64)
            buffer.putLong(packet.source.value)

            // Destination Node ID (8 bytes UInt64)
            buffer.putLong(packet.destination.value)

            // Visited Bloom Filter (4 bytes UInt32)
            buffer.putInt(packet.visitedBloomFilter)

            // Payload Length (2 bytes UInt16)
            buffer.putShort((packet.payload.size and 0xFFFF).toShort())

            // Payload
            buffer.put(packet.payload)

            // Calculate CRC-32 over header + payload
            val crc = Crc32.calculate(buffer.array(), 0, HEADER_SIZE + packet.payload.size)
            buffer.putInt(crc.toInt())

            return buffer.array()
        }

        fun isAstraPacket(bytes: ByteArray): Boolean {
            return bytes.size >= HEADER_SIZE + TRAILER_SIZE &&
                   bytes[0] == AstraNetworkConfig.MAGIC_BYTE_0 &&
                   bytes[1] == AstraNetworkConfig.MAGIC_BYTE_1
        }

        fun deserialize(bytes: ByteArray): AstraPacket {
            require(bytes.size >= HEADER_SIZE + TRAILER_SIZE) {
                "Packet bytes length ${bytes.size} below minimum packet size ${HEADER_SIZE + TRAILER_SIZE}"
            }

            // Verify CRC-32
            val expectedCrc = ByteUtils.getUInt32BE(bytes, bytes.size - TRAILER_SIZE)
            val computedCrc = Crc32.calculate(bytes, 0, bytes.size - TRAILER_SIZE)
            if (computedCrc != expectedCrc) {
                throw IllegalArgumentException("CRC-32 mismatch: computed $computedCrc, expected $expectedCrc")
            }

            val buffer = ByteBuffer.wrap(bytes)

            // Verify Magic
            val magic0 = buffer.get()
            val magic1 = buffer.get()
            if (magic0 != AstraNetworkConfig.MAGIC_BYTE_0 || magic1 != AstraNetworkConfig.MAGIC_BYTE_1) {
                throw IllegalArgumentException("Invalid packet magic: $magic0, $magic1")
            }

            val verAndType = buffer.get().toInt() and 0xFF
            val version = verAndType ushr 4
            val typeValue = verAndType and 0x0F
            val type = AstraPacketType.fromValue(typeValue)

            val flags = AstraPacketFlags.fromByte(buffer.get())
            val ttl = buffer.get().toInt() and 0xFF
            val hopCount = buffer.get().toInt() and 0xFF
            val seq = buffer.short.toLong() and 0xFFFF
            val packetId = PacketId(buffer.long)
            val source = NodeId(buffer.long)
            val destination = NodeId(buffer.long)
            val bloom = buffer.int
            val payloadLen = buffer.short.toInt() and 0xFFFF

            val payload = ByteArray(payloadLen)
            buffer.get(payload)

            return AstraPacket(
                version = version,
                type = type,
                flags = flags,
                ttl = ttl,
                hopCount = hopCount,
                sequenceNumber = seq,
                packetId = packetId,
                source = source,
                destination = destination,
                visitedBloomFilter = bloom,
                payload = payload
            )
        }
    }
}
