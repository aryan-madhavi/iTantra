package com.astramesh.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * iTantra Binary Application Message Envelope.
 * Compact binary protocol for low-bandwidth D2D transmission.
 *
 * Wire format:
 * - Magic (2 bytes): 0x49, 0x54 ('I', 'T')
 * - senderId (8 bytes Long)
 * - messageType (1 byte)
 * - language (1 byte wireCode)
 * - sequenceNumber (4 bytes UInt32)
 * - timestamp (8 bytes Long)
 * - textLen (2 bytes UInt16)
 * - text (textLen bytes UTF-8)
 * - hopTtl (1 byte)
 * - gpsLatitude (4 bytes Float)
 * - gpsLongitude (4 bytes Float)
 * - crc16 (2 bytes Short, CRC-16 over all preceding bytes)
 */
data class IthantraMessage(
    val senderId: NodeId,
    val messageType: MessageType,
    val language: Language,
    val sequenceNumber: Long,
    val timestamp: Long,
    val text: String,
    val hopTtl: Byte = 7,
    val crc16: Short = 0,
    val gpsLatitude: Float = 0.0f,
    val gpsLongitude: Float = 0.0f
) {
    fun toBinary(): ByteArray {
        val textBytes = text.toByteArray(Charsets.UTF_8)
        require(textBytes.size <= 0xFFFF) { "Text length exceeds 65535 bytes" }

        // Size without CRC: 2(magic) + 8(senderId) + 1(type) + 1(lang) + 4(seq) + 8(ts) + 2(textLen) + textBytes.size + 1(ttl) + 4(lat) + 4(lon) = 35 + textBytes.size
        val payloadSizeWithoutCrc = 35 + textBytes.size
        val totalSize = payloadSizeWithoutCrc + 2 // +2 for crc16

        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
        buffer.put(MAGIC_0)
        buffer.put(MAGIC_1)
        buffer.putLong(senderId.value)
        buffer.put(messageType.value)
        buffer.put(language.wireCode)
        buffer.putInt((sequenceNumber and 0xFFFFFFFFL).toInt())
        buffer.putLong(timestamp)
        buffer.putShort((textBytes.size and 0xFFFF).toShort())
        buffer.put(textBytes)
        buffer.put(hopTtl)
        buffer.putFloat(gpsLatitude)
        buffer.putFloat(gpsLongitude)

        val computedCrc = CRC16.calculate(buffer.array(), 0, payloadSizeWithoutCrc)
        buffer.putShort(computedCrc)

        return buffer.array()
    }

    companion object {
        const val MAGIC_0: Byte = 0x49 // 'I'
        const val MAGIC_1: Byte = 0x54 // 'T'
        const val MIN_SIZE = 37 // 35 header/meta bytes + 0 text + 2 CRC

        fun isIthantraMessage(bytes: ByteArray): Boolean {
            return bytes.size >= MIN_SIZE && bytes[0] == MAGIC_0 && bytes[1] == MAGIC_1
        }

        fun fromBinary(bytes: ByteArray): IthantraMessage {
            require(isIthantraMessage(bytes)) {
                "Invalid IthantraMessage buffer: size ${bytes.size}, magic bytes mismatch"
            }

            // Verify CRC-16
            val payloadSizeWithoutCrc = bytes.size - 2
            val expectedCrc = ((bytes[bytes.size - 2].toInt() and 0xFF shl 8) or (bytes[bytes.size - 1].toInt() and 0xFF)).toShort()
            val computedCrc = CRC16.calculate(bytes, 0, payloadSizeWithoutCrc)
            if (computedCrc != expectedCrc) {
                throw IllegalArgumentException("CRC-16 mismatch: expected $expectedCrc, computed $computedCrc")
            }

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            buffer.get() // MAGIC_0
            buffer.get() // MAGIC_1

            val senderId = NodeId(buffer.long)
            val messageType = MessageType.fromByte(buffer.get())
            val language = Language.fromWireCode(buffer.get())
            val seqNo = buffer.int.toLong() and 0xFFFFFFFFL
            val ts = buffer.long
            val textLen = buffer.short.toInt() and 0xFFFF

            if (buffer.remaining() < textLen + 1 + 4 + 4 + 2) {
                throw IllegalArgumentException("Malformed packet: insufficient remaining bytes for textLen $textLen")
            }

            val textBytes = ByteArray(textLen)
            buffer.get(textBytes)
            val text = String(textBytes, Charsets.UTF_8)

            val hopTtl = buffer.get()
            val lat = buffer.float
            val lon = buffer.float
            val crc16 = buffer.short

            return IthantraMessage(
                senderId = senderId,
                messageType = messageType,
                language = language,
                sequenceNumber = seqNo,
                timestamp = ts,
                text = text,
                hopTtl = hopTtl,
                crc16 = crc16,
                gpsLatitude = lat,
                gpsLongitude = lon
            )
        }
    }
}
