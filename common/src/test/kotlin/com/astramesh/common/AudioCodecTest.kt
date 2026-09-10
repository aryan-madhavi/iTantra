package com.astramesh.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AudioCodecTest {

    @Test
    fun `adpcm encode and decode maintains sample consistency`() {
        // Generate a 16-bit linear PCM sine wave
        val numSamples = 320
        val pcm = ByteArray(numSamples * 2)
        for (i in 0 until numSamples) {
            val sampleVal = (Math.sin(2.0 * Math.PI * i / 20.0) * 10000.0).toInt().toShort()
            pcm[i * 2] = (sampleVal.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((sampleVal.toInt() shr 8) and 0xFF).toByte()
        }

        val encoded = AudioCodec.encodeAdpcm(pcm)
        // 4 bytes header + (320 / 2) = 164 bytes
        assertThat(encoded.size).isEqualTo(164)

        val decoded = AudioCodec.decodeAdpcm(encoded)
        assertThat(decoded.size).isEqualTo(numSamples * 2)
    }

    @Test
    fun `adpcm handles empty data gracefully`() {
        val encodedEmpty = AudioCodec.encodeAdpcm(ByteArray(0))
        assertThat(encodedEmpty).isEmpty()

        val decodedEmpty = AudioCodec.decodeAdpcm(ByteArray(0))
        assertThat(decodedEmpty).isEmpty()
    }
}
