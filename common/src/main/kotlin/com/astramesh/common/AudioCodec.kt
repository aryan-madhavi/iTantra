package com.astramesh.common

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-performance IMA-ADPCM / Mu-Law audio compressor for low-bandwidth BLE voice streaming.
 * Compresses 16-bit linear PCM (8000Hz/16000Hz) down to 4-bit ADPCM (4:1 compression ratio).
 */
object AudioCodec {

    private val INDEX_TABLE = intArrayOf(
        -1, -1, -1, -1, 2, 4, 6, 8,
        -1, -1, -1, -1, 2, 4, 6, 8
    )

    private val STEP_TABLE = intArrayOf(
        7, 8, 9, 10, 11, 12, 13, 14, 16, 17,
        19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
        50, 55, 60, 66, 73, 80, 88, 97, 107, 118,
        130, 143, 157, 173, 190, 209, 230, 253, 279, 307,
        337, 371, 408, 449, 494, 544, 598, 658, 724, 796,
        876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066,
        2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358,
        5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899,
        15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767
    )

    /**
     * Compresses 16-bit Linear PCM (Little Endian) to 4-bit IMA-ADPCM.
     */
    fun encodeAdpcm(pcmData: ByteArray): ByteArray {
        if (pcmData.isEmpty()) return ByteArray(0)
        val shortBuffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val numSamples = shortBuffer.remaining()
        val output = ByteArray(numSamples / 2 + 4) // 4 bytes header: initial predictor (short) + index (byte) + reserved
        val outBuf = ByteBuffer.wrap(output)

        var predictor = if (numSamples > 0) shortBuffer.get(0).toInt() else 0
        var stepIndex = 0

        outBuf.putShort(predictor.toShort())
        outBuf.put(stepIndex.toByte())
        outBuf.put(0.toByte()) // padding

        var outByte = 0
        var isHighNibble = false

        for (i in 0 until numSamples) {
            val sample = shortBuffer.get(i).toInt()
            val step = STEP_TABLE[stepIndex]
            var diff = sample - predictor
            var sign = 0
            if (diff < 0) {
                sign = 8
                diff = -diff
            }

            var delta = 0
            var vpdiff = step shr 3

            if (diff >= step) {
                delta = delta or 4
                diff -= step
                vpdiff += step
            }
            val step2 = step shr 1
            if (diff >= step2) {
                delta = delta or 2
                diff -= step2
                vpdiff += step2
            }
            val step4 = step shr 2
            if (diff >= step4) {
                delta = delta or 1
                vpdiff += step4
            }

            delta = delta or sign
            if (sign != 0) {
                predictor -= vpdiff
            } else {
                predictor += vpdiff
            }
            predictor = predictor.coerceIn(-32768, 32767)

            stepIndex += INDEX_TABLE[delta]
            stepIndex = stepIndex.coerceIn(0, 88)

            if (!isHighNibble) {
                outByte = delta and 0x0F
                isHighNibble = true
            } else {
                outByte = outByte or ((delta and 0x0F) shl 4)
                outBuf.put(outByte.toByte())
                isHighNibble = false
            }
        }

        if (isHighNibble) {
            outBuf.put(outByte.toByte())
        }

        val result = ByteArray(outBuf.position())
        System.arraycopy(output, 0, result, 0, result.size)
        return result
    }

    /**
     * Decodes 4-bit IMA-ADPCM back into 16-bit Linear PCM (Little Endian).
     */
    fun decodeAdpcm(adpcmData: ByteArray): ByteArray {
        if (adpcmData.size < 4) return ByteArray(0)
        val inBuf = ByteBuffer.wrap(adpcmData)
        var predictor = inBuf.short.toInt()
        var stepIndex = inBuf.get().toInt() and 0xFF
        inBuf.get() // padding

        val encodedLength = adpcmData.size - 4
        val pcmOut = ByteArray(encodedLength * 4) // 2 samples per byte * 2 bytes per 16-bit sample
        val pcmBuf = ByteBuffer.wrap(pcmOut).order(ByteOrder.LITTLE_ENDIAN)

        while (inBuf.hasRemaining()) {
            val byte = inBuf.get().toInt() and 0xFF
            val nibble1 = byte and 0x0F
            val nibble2 = (byte shr 4) and 0x0F

            for (delta in intArrayOf(nibble1, nibble2)) {
                val step = STEP_TABLE[stepIndex.coerceIn(0, 88)]
                var vpdiff = step shr 3

                if ((delta and 4) != 0) vpdiff += step
                if ((delta and 2) != 0) vpdiff += (step shr 1)
                if ((delta and 1) != 0) vpdiff += (step shr 2)

                if ((delta and 8) != 0) {
                    predictor -= vpdiff
                } else {
                    predictor += vpdiff
                }
                predictor = predictor.coerceIn(-32768, 32767)

                stepIndex += INDEX_TABLE[delta]
                stepIndex = stepIndex.coerceIn(0, 88)

                pcmBuf.putShort(predictor.toShort())
            }
        }

        val result = ByteArray(pcmBuf.position())
        System.arraycopy(pcmOut, 0, result, 0, result.size)
        return result
    }
}
