package com.astramesh.ble

import com.astramesh.core.NodeId
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BleModuleTest {

    @Test
    fun `ble slice encoding and decoding preserves all header fields and payload`() {
        val payload = "AstraMesh Binary BLE Slice Test Payload".toByteArray(Charsets.UTF_8)
        val originalSlice = BleSlice(
            packetIndex = 42,
            totalSlices = 5,
            sliceSeq = 2,
            packetCrc = 0xCBF43926L,
            payload = payload
        )

        val encoded = BleFrameCodec.encodeSlice(originalSlice)
        assertThat(encoded.size).isEqualTo(BleFrameCodec.HEADER_SIZE + payload.size)

        val decoded = BleFrameCodec.decodeSlice(encoded)
        assertThat(decoded).isEqualTo(originalSlice)
    }

    @Test
    fun `ble frame fragmenter splits large packet and reassembler restores it`() {
        val largePacket = ByteArray(1200) { (it % 256).toByte() }
        val fragmenter = BleFrameFragmenter()
        val reassembler = BleFrameReassembler()

        // Max slice size 240 (payload 230 per slice)
        val slices = fragmenter.fragment(largePacket, maxSliceSize = 240)
        assertThat(slices.size).isEqualTo(6) // 1200 / 230 ~ 6 slices

        var reassembledPacket: ByteArray? = null
        // Deliver slices out of order
        val shuffled = slices.shuffled()
        for (slice in shuffled) {
            val result = reassembler.feedSlice(slice)
            if (result != null) {
                reassembledPacket = result
            }
        }

        assertThat(reassembledPacket).isNotNull()
        assertThat(reassembledPacket).isEqualTo(largePacket)
    }

    @Test
    fun `connection pool evicts oldest connection when reaching max limit`() {
        val pool = BleConnectionPool(maxConnections = 2)

        val handle1 = BleConnectionHandle(
            nodeId = NodeId(1L),
            deviceAddress = "AA:BB:CC:DD:EE:01",
            bluetoothGatt = null,
            lastActivityTimestamp = 1000L
        )
        val handle2 = BleConnectionHandle(
            nodeId = NodeId(2L),
            deviceAddress = "AA:BB:CC:DD:EE:02",
            bluetoothGatt = null,
            lastActivityTimestamp = 2000L
        )
        val handle3 = BleConnectionHandle(
            nodeId = NodeId(3L),
            deviceAddress = "AA:BB:CC:DD:EE:03",
            bluetoothGatt = null,
            lastActivityTimestamp = 3000L
        )

        pool.addConnection(handle1)
        pool.addConnection(handle2)
        assertThat(pool.count()).isEqualTo(2)

        // Adding 3rd connection should evict handle1 (oldest timestamp: 1000L)
        pool.addConnection(handle3)
        assertThat(pool.count()).isEqualTo(2)
        assertThat(pool.isConnected(NodeId(1L))).isFalse()
        assertThat(pool.isConnected(NodeId(2L))).isTrue()
        assertThat(pool.isConnected(NodeId(3L))).isTrue()
    }
}
