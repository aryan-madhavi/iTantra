package com.astramesh.common

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Arrays

/**
 * Endian-safe byte manipulation utilities for AstraMesh binary frames and cryptographic operations.
 */
object ByteUtils {

    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    fun toHexString(bytes: ByteArray): String {
        val result = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            result[i * 2] = HEX_CHARS[v ushr 4]
            result[i * 2 + 1] = HEX_CHARS[v and 0x0F]
        }
        return String(result)
    }

    fun hexToByteArray(hex: String): ByteArray {
        val clean = hex.trim().replace(" ", "").lowercase()
        require(clean.length % 2 == 0) { "Hex string must have an even length, got length ${clean.length}" }
        val len = clean.length / 2
        val data = ByteArray(len)
        var i = 0
        while (i < len) {
            val high = Character.digit(clean[i * 2], 16)
            val low = Character.digit(clean[i * 2 + 1], 16)
            require(high != -1 && low != -1) { "Invalid hex character at index ${i * 2}" }
            data[i] = ((high shl 4) or low).toByte()
            i++
        }
        return data
    }

    fun putUInt16BE(value: Int, buffer: ByteArray, offset: Int) {
        require(offset + 2 <= buffer.size) { "Buffer overflow writing UInt16BE" }
        buffer[offset] = ((value ushr 8) and 0xFF).toByte()
        buffer[offset + 1] = (value and 0xFF).toByte()
    }

    fun getUInt16BE(buffer: ByteArray, offset: Int): Int {
        require(offset + 2 <= buffer.size) { "Buffer underflow reading UInt16BE" }
        return ((buffer[offset].toInt() and 0xFF) shl 8) or
                (buffer[offset + 1].toInt() and 0xFF)
    }

    fun putUInt32BE(value: Long, buffer: ByteArray, offset: Int) {
        require(offset + 4 <= buffer.size) { "Buffer overflow writing UInt32BE" }
        buffer[offset] = ((value ushr 24) and 0xFF).toByte()
        buffer[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        buffer[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        buffer[offset + 3] = (value and 0xFF).toByte()
    }

    fun getUInt32BE(buffer: ByteArray, offset: Int): Long {
        require(offset + 4 <= buffer.size) { "Buffer underflow reading UInt32BE" }
        return ((buffer[offset].toLong() and 0xFF) shl 24) or
                ((buffer[offset + 1].toLong() and 0xFF) shl 16) or
                ((buffer[offset + 2].toLong() and 0xFF) shl 8) or
                (buffer[offset + 3].toLong() and 0xFF)
    }

    fun putUInt64BE(value: Long, buffer: ByteArray, offset: Int) {
        require(offset + 8 <= buffer.size) { "Buffer overflow writing UInt64BE" }
        for (i in 7 downTo 0) {
            buffer[offset + (7 - i)] = ((value ushr (i * 8)) and 0xFF).toByte()
        }
    }

    fun getUInt64BE(buffer: ByteArray, offset: Int): Long {
        require(offset + 8 <= buffer.size) { "Buffer underflow reading UInt64BE" }
        var result = 0L
        for (i in 0..7) {
            result = (result shl 8) or (buffer[offset + i].toLong() and 0xFF)
        }
        return result
    }

    fun concat(vararg arrays: ByteArray): ByteArray {
        val totalLength = arrays.sumOf { it.size }
        val result = ByteArray(totalLength)
        var currentPos = 0
        for (arr in arrays) {
            System.arraycopy(arr, 0, result, currentPos, arr.size)
            currentPos += arr.size
        }
        return result
    }

    fun zeroize(buffer: ByteArray) {
        Arrays.fill(buffer, 0.toByte())
    }

    fun allocateBuffer(capacity: Int, byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN): ByteBuffer {
        return ByteBuffer.allocate(capacity).order(byteOrder)
    }
}
