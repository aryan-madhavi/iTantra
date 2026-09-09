package com.astramesh.mesh

import com.astramesh.core.NodeId
import com.astramesh.core.PacketId
import com.astramesh.domain.model.MessagePriority
import com.astramesh.routing.RoutingTable
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MeshModuleTest {

    private val nodeA = NodeId(1L)
    private val nodeB = NodeId(2L)
    private val nodeC = NodeId(3L)

    @Test
    fun `astra packet serialization and deserialization with crc32 verification`() {
        val payload = "Testing AstraMesh Binary Packet Framing".toByteArray(Charsets.UTF_8)
        val pid = PacketId(0x1234567890ABCDEFL)

        val packet = AstraPacket(
            version = 1,
            type = AstraPacketType.DATA_UNICAST,
            flags = AstraPacketFlags(
                isEmergency = false,
                requiresAck = true,
                isCompressed = false,
                isEncrypted = true,
                relayAllowed = true
            ),
            ttl = 7,
            hopCount = 1,
            sequenceNumber = 42L,
            packetId = pid,
            source = nodeA,
            destination = nodeB,
            visitedBloomFilter = 0,
            payload = payload
        )

        val serialized = AstraPacket.serialize(packet)
        assertThat(serialized.size).isEqualTo(AstraPacket.HEADER_SIZE + payload.size + AstraPacket.TRAILER_SIZE)

        val deserialized = AstraPacket.deserialize(serialized)
        assertThat(deserialized).isEqualTo(packet)
    }

    @Test
    fun `message forwarder decides consumption when destination is local node`() {
        val cache = DuplicateDetectionCache()
        val table = RoutingTable(nodeB)
        val forwarder = MessageForwarder(localNodeId = nodeB, deduplicationCache = cache, routingTable = table)

        val packet = AstraPacket(
            type = AstraPacketType.DATA_UNICAST,
            flags = AstraPacketFlags(),
            sequenceNumber = 1L,
            packetId = PacketId(101L),
            source = nodeA,
            destination = nodeB,
            payload = "Direct to B".toByteArray(Charsets.UTF_8)
        )

        val decision = forwarder.processPacket(packet, nodeA)
        assertThat(decision).isInstanceOf(ForwardingDecision.ConsumeLocally::class.java)
    }

    @Test
    fun `message forwarder forwards packet when route is present`() {
        val cache = DuplicateDetectionCache()
        val table = RoutingTable(nodeB)
        // Node B knows how to reach Node C via direct link
        table.updateRoute(destination = nodeC, nextHop = nodeC, cost = 1f, hopCount = 1, sequenceNumber = 1L)

        val forwarder = MessageForwarder(localNodeId = nodeB, deduplicationCache = cache, routingTable = table)

        val packet = AstraPacket(
            type = AstraPacketType.DATA_UNICAST,
            flags = AstraPacketFlags(relayAllowed = true),
            ttl = 5,
            hopCount = 1,
            sequenceNumber = 2L,
            packetId = PacketId(102L),
            source = nodeA,
            destination = nodeC,
            payload = "Forward through B to C".toByteArray(Charsets.UTF_8)
        )

        val decision = forwarder.processPacket(packet, nodeA)
        assertThat(decision).isInstanceOf(ForwardingDecision.ForwardUnicast::class.java)
        val forwardDecision = decision as ForwardingDecision.ForwardUnicast
        assertThat(forwardDecision.nextHop).isEqualTo(nodeC)
        assertThat(forwardDecision.packet.ttl).isEqualTo(4)
        assertThat(forwardDecision.packet.hopCount).isEqualTo(2)
    }

    @Test
    fun `priority scheduler orders emergency before bulk`() {
        val scheduler = PacketPriorityScheduler()

        val bulkPacket = AstraPacket(
            type = AstraPacketType.ATTACHMENT_CHUNK,
            flags = AstraPacketFlags(),
            sequenceNumber = 1L,
            packetId = PacketId(1L),
            source = nodeA,
            destination = nodeB,
            payload = ByteArray(10)
        )

        val emergencyPacket = AstraPacket(
            type = AstraPacketType.DATA_BROADCAST,
            flags = AstraPacketFlags(isEmergency = true),
            sequenceNumber = 2L,
            packetId = PacketId(2L),
            source = nodeA,
            destination = NodeId.BROADCAST,
            payload = "SOS".toByteArray(Charsets.UTF_8)
        )

        scheduler.enqueue(bulkPacket, MessagePriority.BULK)
        scheduler.enqueue(emergencyPacket, MessagePriority.EMERGENCY)

        val firstOut = scheduler.poll()
        assertThat(firstOut?.priority).isEqualTo(MessagePriority.EMERGENCY)
        assertThat(firstOut?.packet?.packetId).isEqualTo(PacketId(2L))

        val secondOut = scheduler.poll()
        assertThat(secondOut?.priority).isEqualTo(MessagePriority.BULK)
    }

    @Test
    fun `reliable delivery manager tracks and retries unacknowledged packets`() {
        val manager = ReliableDeliveryManager(maxRetries = 2, initialBackoffMs = 50L)

        val packet = AstraPacket(
            type = AstraPacketType.DATA_UNICAST,
            flags = AstraPacketFlags(requiresAck = true),
            sequenceNumber = 1L,
            packetId = PacketId(999L),
            source = nodeA,
            destination = nodeB,
            payload = ByteArray(5)
        )

        manager.trackPacket(packet, nodeB)
        assertThat(manager.pendingCount()).isEqualTo(1)

        // Immediately before backoff
        val noRetries = manager.getPacketsForRetry(System.currentTimeMillis())
        assertThat(noRetries).isEmpty()

        // After backoff timeout
        val retries = manager.getPacketsForRetry(System.currentTimeMillis() + 100L)
        assertThat(retries).hasSize(1)
        assertThat(retries[0].packetId).isEqualTo(PacketId(999L))

        // Acknowledge removes it
        val ackSuccess = manager.acknowledge(PacketId(999L))
        assertThat(ackSuccess).isTrue()
        assertThat(manager.pendingCount()).isEqualTo(0)
    }
}
