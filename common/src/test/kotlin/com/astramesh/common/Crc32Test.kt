package com.astramesh.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class Crc32Test {

    @Test
    fun `standard test vector 123456789 computes correct CRC-32`() {
        // Standard IEEE 802.3 test vector for string "123456789"
        val data = "123456789".toByteArray(Charsets.US_ASCII)
        val crc = Crc32.calculate(data)

        // Standard CRC-32 for "123456789" is 0xCBF43926 (3421780262)
        assertThat(crc).isEqualTo(0xCBF43926L)
        assertThat(Crc32.verify(data, 0xCBF43926L)).isTrue()
        assertThat(Crc32.verify(data, 0x11111111L)).isFalse()
    }

    @Test
    fun `empty array CRC is zero`() {
        val crc = Crc32.calculate(ByteArray(0))
        assertThat(crc).isEqualTo(0L)
    }

    @Test
    fun `offset and length slicing calculates expected sub-array CRC`() {
        val full = "PREFIX_123456789_SUFFIX".toByteArray(Charsets.US_ASCII)
        val offset = "PREFIX_".length
        val length = "123456789".length

        val crc = Crc32.calculate(full, offset, length)
        assertThat(crc).isEqualTo(0xCBF43926L)
    }
}
