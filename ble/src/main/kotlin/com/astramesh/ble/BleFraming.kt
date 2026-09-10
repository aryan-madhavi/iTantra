package com.astramesh.ble

import com.astramesh.common.ByteUtils
import com.astramesh.common.Crc32
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * Raw binary slice representing a fragment of an AstraMesh packet over BLE.
 * Header:
 * - packetIndex (2 bytes UInt16)
 * - totalSlices (2 bytes UInt16)
 * - sliceSeq (2 bytes UInt16)
 * - crc32 (4 bytes UInt32)
 * - payload (variable)
 */
data class BleSlice(
    val packetIndex: Int,
    val totalSlices: Int,
    val sliceSeq: Int,
    val packetCrc: Long,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BleSlice) return false
        return packetIndex == other.packetIndex &&
                totalSlices == other.totalSlices &&
                sliceSeq == other.sliceSeq &&
                packetCrc == other.packetCrc &&
                payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = packetIndex
        result = 31 * result + totalSlices
        result = 31 * result + sliceSeq
        result = 31 * result + packetCrc.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

object BleFrameCodec {

    const val HEADER_SIZE = 10

    fun encodeSlice(slice: BleSlice): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_SIZE + slice.payload.size)
        buffer.putShort(slice.packetIndex.toShort())
        buffer.putShort(slice.totalSlices.toShort())
        buffer.putShort(slice.sliceSeq.toShort())
        buffer.putInt(slice.packetCrc.toInt())
        buffer.put(slice.payload)
        return buffer.array()
    }

    fun decodeSlice(data: ByteArray): BleSlice {
        require(data.size >= HEADER_SIZE) { "Data size ${data.size} smaller than BLE slice header $HEADER_SIZE" }
        val buffer = ByteBuffer.wrap(data)
        val packetIndex = buffer.short.toInt() and 0xFFFF
        val totalSlices = buffer.short.toInt() and 0xFFFF
        val sliceSeq = buffer.short.toInt() and 0xFFFF
        val packetCrc = buffer.int.toLong() and 0xFFFFFFFFL

        val payload = ByteArray(data.size - HEADER_SIZE)
        buffer.get(payload)

        return BleSlice(
            packetIndex = packetIndex,
            totalSlices = totalSlices,
            sliceSeq = sliceSeq,
            packetCrc = packetCrc,
            payload = payload
        )
    }
}

class BleFrameFragmenter {

    private var nextPacketIndex = 0

    @Synchronized
    fun fragment(packetBytes: ByteArray, maxSliceSize: Int): List<BleSlice> {
        require(maxSliceSize > BleFrameCodec.HEADER_SIZE) {
            "maxSliceSize $maxSliceSize must be greater than header size ${BleFrameCodec.HEADER_SIZE}"
        }
        val maxPayloadPerSlice = maxSliceSize - BleFrameCodec.HEADER_SIZE
        val totalSlices = (packetBytes.size + maxPayloadPerSlice - 1) / maxPayloadPerSlice
        val packetIndex = nextPacketIndex++ and 0xFFFF
        val crc = Crc32.calculate(packetBytes)

        val slices = mutableListOf<BleSlice>()
        var offset = 0
        var seq = 0

        while (offset < packetBytes.size) {
            val length = minOf(maxPayloadPerSlice, packetBytes.size - offset)
            val slicePayload = ByteArray(length)
            System.arraycopy(packetBytes, offset, slicePayload, 0, length)

            slices.add(
                BleSlice(
                    packetIndex = packetIndex,
                    totalSlices = totalSlices,
                    sliceSeq = seq,
                    packetCrc = crc,
                    payload = slicePayload
                )
            )

            offset += length
            seq++
        }

        return slices
    }
}

class BleFrameReassembler(
    private val maxPartialPackets: Int = 128
) {

    private data class PartialPacket(
        val packetIndex: Int,
        val totalSlices: Int,
        val expectedCrc: Long,
        val slices: Array<ByteArray?>,
        val receivedCount: Int,
        val createdAt: Long = System.currentTimeMillis()
    )

    private val partialPackets = ConcurrentHashMap<Int, PartialPacket>()

    val size: Int get() = partialPackets.size

    @Synchronized
    fun feedSlice(slice: BleSlice): ByteArray? {
        val existing = partialPackets[slice.packetIndex]

        val partial = if (existing == null) {
            // Guard against unbounded buildup
            if (partialPackets.size >= maxPartialPackets) {
                pruneStale(5_000L)
                if (partialPackets.size >= maxPartialPackets) {
                    val oldestKey = partialPackets.minByOrNull { it.value.createdAt }?.key
                    if (oldestKey != null) {
                        partialPackets.remove(oldestKey)
                    }
                }
            }

            val newPartial = PartialPacket(
                packetIndex = slice.packetIndex,
                totalSlices = slice.totalSlices,
                expectedCrc = slice.packetCrc,
                slices = arrayOfNulls(slice.totalSlices),
                receivedCount = 0
            )
            partialPackets[slice.packetIndex] = newPartial
            newPartial
        } else {
            existing
        }

        if (slice.sliceSeq < partial.totalSlices && partial.slices[slice.sliceSeq] == null) {
            partial.slices[slice.sliceSeq] = slice.payload
            val updated = partial.copy(receivedCount = partial.receivedCount + 1)
            partialPackets[slice.packetIndex] = updated

            if (updated.receivedCount == updated.totalSlices) {
                // Reassemble
                val completeSize = updated.slices.sumOf { it?.size ?: 0 }
                val completeBytes = ByteArray(completeSize)
                var currentOffset = 0
                for (s in updated.slices) {
                    if (s != null) {
                        System.arraycopy(s, 0, completeBytes, currentOffset, s.size)
                        currentOffset += s.size
                    }
                }

                partialPackets.remove(slice.packetIndex)

                // Verify CRC
                if (Crc32.verify(completeBytes, updated.expectedCrc)) {
                    return completeBytes
                }
            }
        }

        return null
    }

    fun pruneStale(timeoutMillis: Long = 10_000L) {
        val now = System.currentTimeMillis()
        partialPackets.entries.removeIf { now - it.value.createdAt > timeoutMillis }
    }

    fun clear() {
        partialPackets.clear()
    }
}

