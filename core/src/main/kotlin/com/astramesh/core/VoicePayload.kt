package com.astramesh.core

import java.nio.ByteBuffer

enum class VoiceMode(val value: Byte) {
    PUSH_TO_TALK(0x1),
    WALKIE_TALKIE(0x2),
    EMERGENCY(0x3),
    CONTINUOUS(0x4);

    companion object {
        fun fromValue(value: Byte): VoiceMode {
            return entries.firstOrNull { it.value == value } ?: PUSH_TO_TALK
        }
    }
}

/**
 * Binary voice payload format for Push-to-Talk, Walkie-Talkie, Emergency, and Continuous audio stream.
 * Header:
 * - magic: 2 bytes (0x56, 0x50 -> 'V', 'P')
 * - mode (1 byte)
 * - sequence (2 bytes UInt16)
 * - isFinal (1 byte: 1 or 0)
 * - transcriptLength (2 bytes UInt16)
 * - transcriptBytes (variable)
 * - audioLength (4 bytes UInt32)
 * - audioBytes (variable compressed PCM/ADPCM)
 */
data class VoicePayload(
    val mode: VoiceMode,
    val sequence: Int,
    val isFinal: Boolean,
    val transcript: String = "",
    val audioData: ByteArray = ByteArray(0)
) {
    fun serialize(): ByteArray {
        val transcriptBytes = transcript.toByteArray(Charsets.UTF_8)
        val totalSize = 2 + 1 + 2 + 1 + 2 + transcriptBytes.size + 4 + audioData.size
        val buffer = ByteBuffer.allocate(totalSize)
        buffer.put(MAGIC_BYTE_0)
        buffer.put(MAGIC_BYTE_1)
        buffer.put(mode.value)
        buffer.putShort((sequence and 0xFFFF).toShort())
        buffer.put((if (isFinal) 1 else 0).toByte())
        buffer.putShort((transcriptBytes.size and 0xFFFF).toShort())
        buffer.put(transcriptBytes)
        buffer.putInt(audioData.size)
        buffer.put(audioData)
        return buffer.array()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VoicePayload) return false
        return mode == other.mode &&
                sequence == other.sequence &&
                isFinal == other.isFinal &&
                transcript == other.transcript &&
                audioData.contentEquals(other.audioData)
    }

    override fun hashCode(): Int {
        var result = mode.hashCode()
        result = 31 * result + sequence
        result = 31 * result + isFinal.hashCode()
        result = 31 * result + transcript.hashCode()
        result = 31 * result + audioData.contentHashCode()
        return result
    }

    companion object {
        const val MAGIC_BYTE_0: Byte = 0x56 // 'V'
        const val MAGIC_BYTE_1: Byte = 0x50 // 'P'
        const val MIN_HEADER_SIZE = 12

        fun isVoicePayload(bytes: ByteArray): Boolean {
            return bytes.size >= MIN_HEADER_SIZE &&
                    bytes[0] == MAGIC_BYTE_0 &&
                    bytes[1] == MAGIC_BYTE_1
        }

        fun deserialize(bytes: ByteArray): VoicePayload {
            if (!isVoicePayload(bytes)) {
                // Fallback for raw text payload
                return VoicePayload(
                    mode = VoiceMode.PUSH_TO_TALK,
                    sequence = 0,
                    isFinal = true,
                    transcript = String(bytes, Charsets.UTF_8),
                    audioData = ByteArray(0)
                )
            }
            return try {
                val buffer = ByteBuffer.wrap(bytes)
                val m0 = buffer.get()
                val m1 = buffer.get()
                if (m0 != MAGIC_BYTE_0 || m1 != MAGIC_BYTE_1) {
                    throw IllegalArgumentException("Invalid VoicePayload magic")
                }
                val mode = VoiceMode.fromValue(buffer.get())
                val sequence = buffer.short.toInt() and 0xFFFF
                val isFinal = buffer.get().toInt() == 1
                val transcriptLen = buffer.short.toInt() and 0xFFFF
                val transcriptBytes = ByteArray(transcriptLen)
                buffer.get(transcriptBytes)
                val transcript = String(transcriptBytes, Charsets.UTF_8)
                val audioLen = buffer.int
                val audioData = ByteArray(audioLen)
                buffer.get(audioData)

                VoicePayload(
                    mode = mode,
                    sequence = sequence,
                    isFinal = isFinal,
                    transcript = transcript,
                    audioData = audioData
                )
            } catch (e: Exception) {
                VoicePayload(
                    mode = VoiceMode.PUSH_TO_TALK,
                    sequence = 0,
                    isFinal = true,
                    transcript = String(bytes, Charsets.UTF_8),
                    audioData = ByteArray(0)
                )
            }
        }
    }
}
