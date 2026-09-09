package com.astramesh.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ByteUtilsTest {

    @Test
    fun `hex conversion round trip matches`() {
        val original = byteArrayOf(0x00, 0x01, 0x0A, 0xFF.toByte(), 0x41, 0x53)
        val hex = ByteUtils.toHexString(original)
        assertThat(hex).isEqualTo("00010aff4153")

        val restored = ByteUtils.hexToByteArray(hex)
        assertThat(restored).isEqualTo(original)
    }

    @Test
    fun `uint16 big endian serialization and deserialization`() {
        val buffer = ByteArray(4)
        ByteUtils.putUInt16BE(0x1234, buffer, 1)

        assertThat(buffer[1]).isEqualTo(0x12.toByte())
        assertThat(buffer[2]).isEqualTo(0x34.toByte())

        val value = ByteUtils.getUInt16BE(buffer, 1)
        assertThat(value).isEqualTo(0x1234)
    }

    @Test
    fun `uint32 big endian serialization and deserialization`() {
        val buffer = ByteArray(8)
        ByteUtils.putUInt32BE(0xDEADBEEFL, buffer, 2)

        val value = ByteUtils.getUInt32BE(buffer, 2)
        assertThat(value).isEqualTo(0xDEADBEEFL)
    }

    @Test
    fun `uint64 big endian serialization and deserialization`() {
        val buffer = ByteArray(16)
        val originalValue = 0x0123456789ABCDEFL
        ByteUtils.putUInt64BE(originalValue, buffer, 4)

        val value = ByteUtils.getUInt64BE(buffer, 4)
        assertThat(value).isEqualTo(originalValue)
    }

    @Test
    fun `concat joins arrays in order`() {
        val a = byteArrayOf(1, 2)
        val b = byteArrayOf(3, 4, 5)
        val result = ByteUtils.concat(a, b)

        assertThat(result).isEqualTo(byteArrayOf(1, 2, 3, 4, 5))
    }

    @Test
    fun `zeroize overwrites byte array with zeros`() {
        val sensitive = byteArrayOf(0x42, 0x13, 0x37)
        ByteUtils.zeroize(sensitive)

        assertThat(sensitive).isEqualTo(byteArrayOf(0, 0, 0))
    }
}
