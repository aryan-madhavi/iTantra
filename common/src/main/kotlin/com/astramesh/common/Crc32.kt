package com.astramesh.common

/**
 * High-performance IEEE 802.3 CRC-32 calculator with precomputed lookup table.
 * Used for frame validation and fragment reassembly checksums in the AstraMesh protocol.
 */
object Crc32 {

    private val TABLE = LongArray(256) { i ->
        var entry = i.toLong()
        for (j in 0 until 8) {
            entry = if ((entry and 1L) != 0L) {
                (entry ushr 1) xor 0xEDB88320L
            } else {
                entry ushr 1
            }
        }
        entry
    }

    fun calculate(data: ByteArray, offset: Int = 0, length: Int = data.size): Long {
        require(offset >= 0 && length >= 0 && offset + length <= data.size) {
            "Invalid offset $offset or length $length for array of size ${data.size}"
        }
        var crc = 0xFFFFFFFFL
        for (i in offset until (offset + length)) {
            val index = ((crc xor (data[i].toLong() and 0xFF)) and 0xFF).toInt()
            crc = (crc ushr 8) xor TABLE[index]
        }
        return (crc xor 0xFFFFFFFFL) and 0xFFFFFFFFL
    }

    fun verify(data: ByteArray, expectedCrc: Long, offset: Int = 0, length: Int = data.size): Boolean {
        return calculate(data, offset, length) == expectedCrc
    }
}
