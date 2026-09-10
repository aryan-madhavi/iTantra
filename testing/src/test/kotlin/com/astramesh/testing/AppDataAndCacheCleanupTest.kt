package com.astramesh.testing

import com.astramesh.ble.BleFrameCodec
import com.astramesh.ble.BleFrameFragmenter
import com.astramesh.ble.BleFrameReassembler
import com.astramesh.ble.BleSlice
import com.astramesh.core.NodeId
import com.astramesh.core.PacketId
import com.astramesh.domain.model.MessagePriority
import com.astramesh.mesh.AstraPacket
import com.astramesh.mesh.AstraPacketFlags
import com.astramesh.mesh.AstraPacketType
import com.astramesh.mesh.DuplicateDetectionCache
import com.astramesh.mesh.ReliableDeliveryManager
import com.astramesh.mesh.StoreAndForwardQueue
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class AppDataAndCacheCleanupTest {

    private lateinit var medium: VirtualBleMedium
    private val nodes = mutableListOf<VirtualMeshNode>()

    @Before
    fun setUp() {
        medium = VirtualBleMedium()
    }

    @After
    fun tearDown() {
        nodes.forEach { it.stop() }
        nodes.clear()
    }

    private fun createNode(idLong: Long): VirtualMeshNode {
        val node = VirtualMeshNode(NodeId(idLong), medium)
        node.start()
        nodes.add(node)
        return node
    }

    @Test
    fun testStoreAndForwardQueueTtlPruningAndCapacityLimit() {
        val queue = StoreAndForwardQueue(maxCapacity = 10, ttlMillis = 1000L)
        val dest = NodeId(12345L)

        // 1. Enqueue items up to and exceeding capacity
        for (i in 0 until 15) {
            val payload = "Message $i".toByteArray()
            queue.enqueue(dest, payload, MessagePriority.DIRECT_MESSAGE)
        }

        // Must be capped at maxCapacity (10)
        assertThat(queue.size()).isAtMost(10)

        // 2. Test Deduplication: Enqueuing same payload to same dest should be rejected
        val duplicateEnqueued = queue.enqueue(dest, "Message 14".toByteArray(), MessagePriority.DIRECT_MESSAGE)
        assertThat(duplicateEnqueued).isFalse()

        // 3. Test TTL expiration pruning
        val futureTime = System.currentTimeMillis() + 5000L
        val prunedCount = queue.pruneExpired(now = futureTime)
        assertThat(prunedCount).isAtLeast(1)
        assertThat(queue.size()).isEqualTo(0)

        // 4. Test clear
        queue.enqueue(dest, "Fresh message".toByteArray(), MessagePriority.DIRECT_MESSAGE)
        assertThat(queue.size()).isEqualTo(1)
        queue.clear()
        assertThat(queue.size()).isEqualTo(0)
    }

    @Test
    fun testReliableDeliveryManagerRetryExpiryAndLimits() {
        val rdm = ReliableDeliveryManager(
            maxRetries = 2,
            initialBackoffMs = 100L,
            maxPending = 5,
            packetTtlMs = 1000L
        )
        val src = NodeId(1L)
        val dest = NodeId(2L)

        // 1. Track packets up to capacity
        for (i in 0 until 10) {
            val packet = AstraPacket(
                type = AstraPacketType.DATA_UNICAST,
                flags = AstraPacketFlags(isEmergency = false, requiresAck = true, isEncrypted = false, relayAllowed = true),
                ttl = 7,
                hopCount = 0,
                sequenceNumber = i.toLong(),
                packetId = PacketId.generate(src, i.toLong(), System.currentTimeMillis()),
                source = src,
                destination = dest,
                visitedBloomFilter = 0,
                payload = "Packet $i".toByteArray()
            )
            rdm.trackPacket(packet, dest)
        }

        // Bounded capacity enforced
        assertThat(rdm.pendingCount()).isAtMost(5)

        // 2. Retry packets and verify exponential backoff
        val now = System.currentTimeMillis() + 200L
        val retries = rdm.getPacketsForRetry(now = now)
        assertThat(retries).isNotEmpty()

        // 3. Exceed retries / TTL and observe onPacketFailed callback
        val failedPackets = mutableListOf<PacketId>()
        val farFuture = System.currentTimeMillis() + 10_000L
        rdm.getPacketsForRetry(
            now = farFuture,
            onPacketFailed = { failedPackets.add(it.packetId) }
        )
        assertThat(failedPackets).isNotEmpty()
        assertThat(rdm.pendingCount()).isEqualTo(0)

        // 4. Test explicit clear
        rdm.trackPacket(
            AstraPacket(
                type = AstraPacketType.DATA_UNICAST,
                flags = AstraPacketFlags(isEmergency = false, requiresAck = true, isEncrypted = false, relayAllowed = true),
                ttl = 7,
                hopCount = 0,
                sequenceNumber = 99L,
                packetId = PacketId.generate(src, 99L, System.currentTimeMillis()),
                source = src,
                destination = dest,
                visitedBloomFilter = 0,
                payload = "Clear test".toByteArray()
            ),
            dest
        )
        assertThat(rdm.pendingCount()).isEqualTo(1)
        rdm.clear()
        assertThat(rdm.pendingCount()).isEqualTo(0)
    }

    @Test
    fun testBleFrameReassemblerClearAndBounding() {
        val reassembler = BleFrameReassembler(maxPartialPackets = 5)
        val fragmenter = BleFrameFragmenter()

        val largePayload = ByteArray(200) { it.toByte() }
        val slices = fragmenter.fragment(largePayload, maxSliceSize = 50)
        assertThat(slices.size).isAtLeast(4)

        // Feed only first slice of packet 0
        val complete1 = reassembler.feedSlice(slices[0])
        assertThat(complete1).isNull()
        assertThat(reassembler.size).isEqualTo(1)

        // Feed partial slices for multiple packets to test bounded limit
        for (i in 1..10) {
            val dummySlice = BleSlice(
                packetIndex = 100 + i,
                totalSlices = 10,
                sliceSeq = 0,
                packetCrc = 1234L,
                payload = byteArrayOf(1, 2, 3)
            )
            reassembler.feedSlice(dummySlice)
        }
        // Capacity must not exceed limit + 1 eviction
        assertThat(reassembler.size).isAtMost(6)

        // Test clear
        reassembler.clear()
        assertThat(reassembler.size).isEqualTo(0)
    }

    @Test
    fun testDuplicateDetectionCachePruningAndClear() {
        val cache = DuplicateDetectionCache(maxEntries = 100)
        val now = System.currentTimeMillis()
        val pid1 = PacketId.generate(NodeId(1L), 1L, now - 20_000L)
        val pid2 = PacketId.generate(NodeId(1L), 2L, now)

        cache.put(pid1, timestamp = now - 20_000L)
        cache.put(pid2, timestamp = now)
        assertThat(cache.size()).isEqualTo(2)

        // Prune entries older than 10 seconds
        val pruned = cache.pruneOlderThan(ttlMillis = 10_000L, now = now)
        assertThat(pruned).isAtLeast(1)
        assertThat(cache.contains(pid1)).isFalse()
        assertThat(cache.contains(pid2)).isTrue()

        // Clear
        cache.clear()
        assertThat(cache.size()).isEqualTo(0)
    }


    @Test
    fun testFullMeshClearAppDataAndCachePreservesIdentityAndResumesTransmissions() {
        // Topology: Node A (101) <-> Node B Relay (102) <-> Node C (103)
        val nodeA = createNode(101L)
        val nodeB = createNode(102L)
        val nodeC = createNode(103L)

        medium.addBidirectionalLink(nodeA.nodeId, nodeB.nodeId, latencyMs = 0L)
        medium.addBidirectionalLink(nodeB.nodeId, nodeC.nodeId, latencyMs = 0L)

        nodeA.routingTable.updateRoute(nodeC.nodeId, nodeB.nodeId, cost = 2.0f, hopCount = 2, sequenceNumber = 1L)
        nodeB.routingTable.updateRoute(nodeC.nodeId, nodeC.nodeId, cost = 1.0f, hopCount = 1, sequenceNumber = 1L)

        // 1. Transmit initial message
        val p1 = "Message before clearing".toByteArray()
        val pid1 = nodeA.sendPacket(nodeC.nodeId, p1)
        Thread.sleep(100)

        assertThat(nodeC.packetsReceived.get()).isAtLeast(1L)
        val receivedBefore = nodeC.receivedPacketsHistory.any { it.packetId == pid1 }
        assertThat(receivedBefore).isTrue()

        // 2. Perform "Clear App Data & Cache" on Node A and Node C
        val originalIdA = nodeA.nodeId
        val originalIdC = nodeC.nodeId

        nodeA.clearAppDataAndCache()
        nodeC.clearAppDataAndCache()

        // Cryptographic/permanent identity MUST be preserved
        assertThat(nodeA.nodeId).isEqualTo(originalIdA)
        assertThat(nodeC.nodeId).isEqualTo(originalIdC)

        // Buffers & statistics must be cleared
        assertThat(nodeA.packetsSent.get()).isEqualTo(0L)
        assertThat(nodeA.storeAndForwardQueue.size()).isEqualTo(0)
        assertThat(nodeC.packetsReceived.get()).isEqualTo(0L)
        assertThat(nodeC.receivedPacketsHistory).isEmpty()

        // 3. Re-establish routes after cache flush and test immediate resumption
        nodeA.routingTable.updateRoute(nodeC.nodeId, nodeB.nodeId, cost = 2.0f, hopCount = 2, sequenceNumber = 10L)
        nodeB.routingTable.updateRoute(nodeC.nodeId, nodeC.nodeId, cost = 1.0f, hopCount = 1, sequenceNumber = 10L)

        // Test Unicast PTT transmission after clearing
        val p2 = "PTT voice stream after cache purge".toByteArray()
        val pid2 = nodeA.sendPacket(nodeC.nodeId, p2, priority = MessagePriority.DIRECT_MESSAGE)
        Thread.sleep(100)

        assertThat(nodeC.packetsReceived.get()).isAtLeast(1L)
        val receivedPostUnicast = nodeC.receivedPacketsHistory.any { it.packetId == pid2 }
        assertThat(receivedPostUnicast).isTrue()

        // Test Emergency Broadcast transmission after clearing
        val emergencyPayload = "EMERGENCY BROADCAST SOS POST-CLEAR".toByteArray()
        val emergencyPid = nodeA.sendPacket(NodeId.BROADCAST, emergencyPayload, priority = MessagePriority.EMERGENCY)
        Thread.sleep(100)

        val bReceivedEmergency = nodeB.receivedPacketsHistory.any { it.packetId == emergencyPid }
        val cReceivedEmergency = nodeC.receivedPacketsHistory.any { it.packetId == emergencyPid }
        assertThat(bReceivedEmergency).isTrue()
        assertThat(cReceivedEmergency).isTrue()
    }
}
