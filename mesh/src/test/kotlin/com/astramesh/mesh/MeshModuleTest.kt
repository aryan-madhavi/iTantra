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

    @Test
    fun `multi-hop relay pipeline phone A to phone B to phone C for unicast direct message`() {
        // Node A, Node B, Node C topology: A <-> B <-> C (A cannot reach C directly)
        val cacheA = DuplicateDetectionCache()
        val cacheB = DuplicateDetectionCache()
        val cacheC = DuplicateDetectionCache()

        val tableA = RoutingTable(nodeA)
        val tableB = RoutingTable(nodeB)
        val tableC = RoutingTable(nodeC)

        // Node B has route to Node C
        tableB.updateRoute(destination = nodeC, nextHop = nodeC, cost = 1.0f, hopCount = 1, sequenceNumber = 1L)
        // Node C has route to Node B
        tableC.updateRoute(destination = nodeB, nextHop = nodeB, cost = 1.0f, hopCount = 1, sequenceNumber = 1L)

        val forwarderB = MessageForwarder(localNodeId = nodeB, deduplicationCache = cacheB, routingTable = tableB)
        val forwarderC = MessageForwarder(localNodeId = nodeC, deduplicationCache = cacheC, routingTable = tableC)

        // Step 1: Phone A generates packet for Phone C
        val originalPacket = AstraPacket(
            type = AstraPacketType.DATA_UNICAST,
            flags = AstraPacketFlags(relayAllowed = true, requiresAck = true),
            ttl = 5,
            hopCount = 0,
            sequenceNumber = 100L,
            packetId = PacketId(5001L),
            source = nodeA,
            destination = nodeC,
            visitedBloomFilter = 0,
            payload = "Hello from Phone A to Phone C via Relay B".toByteArray(Charsets.UTF_8)
        )

        // Step 2: Phone B receives packet from Phone A
        // Phone B performs opportunistic reverse route learning to Phone A
        tableB.updateRoute(destination = originalPacket.source, nextHop = nodeA, cost = 1.0f, hopCount = 1, sequenceNumber = originalPacket.sequenceNumber)
        val decisionB = forwarderB.processPacket(originalPacket, receivedFromNodeId = nodeA)

        assertThat(decisionB).isInstanceOf(ForwardingDecision.ForwardUnicast::class.java)
        val relayedPacket = (decisionB as ForwardingDecision.ForwardUnicast).packet
        assertThat(decisionB.nextHop).isEqualTo(nodeC)
        assertThat(relayedPacket.ttl).isEqualTo(4)
        assertThat(relayedPacket.hopCount).isEqualTo(1)
        assertThat(relayedPacket.source).isEqualTo(nodeA)
        assertThat(relayedPacket.destination).isEqualTo(nodeC)

        // Step 3: Phone C receives forwarded packet from Phone B
        // Phone C performs reverse route learning to Phone A via nextHop = Phone B
        tableC.updateRoute(destination = relayedPacket.source, nextHop = nodeB, cost = 2.0f, hopCount = 2, sequenceNumber = relayedPacket.sequenceNumber)
        val decisionC = forwarderC.processPacket(relayedPacket, receivedFromNodeId = nodeB)

        assertThat(decisionC).isInstanceOf(ForwardingDecision.ConsumeLocally::class.java)
        val consumedPacket = (decisionC as ForwardingDecision.ConsumeLocally).packet
        assertThat(consumedPacket.packetId).isEqualTo(PacketId(5001L))
        assertThat(consumedPacket.source).isEqualTo(nodeA)
        assertThat(consumedPacket.destination).isEqualTo(nodeC)

        // Verify Phone C now knows route back to Phone A via Node B!
        val returnRoute = tableC.getRoute(nodeA)
        assertThat(returnRoute).isNotNull()
        assertThat(returnRoute?.nextHop).isEqualTo(nodeB)
        assertThat(returnRoute?.hopCount).isEqualTo(2)
    }

    @Test
    fun `multi-hop broadcast and emergency flooding propagates with TTL decrement`() {
        val cacheB = DuplicateDetectionCache()
        val cacheC = DuplicateDetectionCache()
        val tableB = RoutingTable(nodeB)
        val tableC = RoutingTable(nodeC)

        val forwarderB = MessageForwarder(localNodeId = nodeB, deduplicationCache = cacheB, routingTable = tableB)
        val forwarderC = MessageForwarder(localNodeId = nodeC, deduplicationCache = cacheC, routingTable = tableC)

        val emergencyBroadcast = AstraPacket(
            type = AstraPacketType.DATA_BROADCAST,
            flags = AstraPacketFlags(isEmergency = true, relayAllowed = true),
            ttl = 3,
            hopCount = 0,
            sequenceNumber = 1L,
            packetId = PacketId(777L),
            source = nodeA,
            destination = NodeId.BROADCAST,
            visitedBloomFilter = 0,
            payload = "SOS EMERGENCY BROADCAST".toByteArray(Charsets.UTF_8)
        )

        // Hop 1: Node B receives from Node A
        val decisionB = forwarderB.processPacket(emergencyBroadcast, nodeA)
        assertThat(decisionB).isInstanceOf(ForwardingDecision.FloodBroadcast::class.java)
        val packetFromB = (decisionB as ForwardingDecision.FloodBroadcast).packet
        assertThat(packetFromB.ttl).isEqualTo(2)
        assertThat(packetFromB.hopCount).isEqualTo(1)

        // Hop 2: Node C receives from Node B
        val decisionC = forwarderC.processPacket(packetFromB, nodeB)
        assertThat(decisionC).isInstanceOf(ForwardingDecision.FloodBroadcast::class.java)
        val packetFromC = (decisionC as ForwardingDecision.FloodBroadcast).packet
        assertThat(packetFromC.ttl).isEqualTo(1)
        assertThat(packetFromC.hopCount).isEqualTo(2)

        // Duplicate arrival at Node B is dropped
        val duplicateDecision = forwarderB.processPacket(emergencyBroadcast, nodeA)
        assertThat(duplicateDecision).isInstanceOf(ForwardingDecision.Drop::class.java)
    }

    @Test
    fun `duplicate packet suppression drops repeated packet id`() {
        val cache = DuplicateDetectionCache()
        val table = RoutingTable(nodeB)
        val forwarder = MessageForwarder(nodeB, cache, table)

        val packet = AstraPacket(
            type = AstraPacketType.DATA_UNICAST,
            flags = AstraPacketFlags(),
            sequenceNumber = 1L,
            packetId = PacketId(12345L),
            source = nodeA,
            destination = nodeB,
            payload = "First arrival".toByteArray(Charsets.UTF_8)
        )

        val firstDecision = forwarder.processPacket(packet, nodeA)
        assertThat(firstDecision).isInstanceOf(ForwardingDecision.ConsumeLocally::class.java)

        val secondDecision = forwarder.processPacket(packet, nodeA)
        assertThat(secondDecision).isInstanceOf(ForwardingDecision.Drop::class.java)
        assertThat((secondDecision as ForwardingDecision.Drop).reason).contains("Duplicate")
    }

    @Test
    fun `loop detection drops packet circulating back to visited node`() {
        val cache = DuplicateDetectionCache()
        val table = RoutingTable(nodeB)
        val forwarder = MessageForwarder(nodeB, cache, table)

        // Node B is already marked in visited Bloom filter
        val bloomWithB = com.astramesh.routing.LoopDetector.addNode(0, nodeB)

        val loopingPacket = AstraPacket(
            type = AstraPacketType.DATA_UNICAST,
            flags = AstraPacketFlags(relayAllowed = true),
            ttl = 5,
            hopCount = 2,
            sequenceNumber = 1L,
            packetId = PacketId(9999L),
            source = nodeA,
            destination = nodeC,
            visitedBloomFilter = bloomWithB,
            payload = "Looping packet".toByteArray(Charsets.UTF_8)
        )

        val decision = forwarder.processPacket(loopingPacket, nodeA)
        assertThat(decision).isInstanceOf(ForwardingDecision.Drop::class.java)
        assertThat((decision as ForwardingDecision.Drop).reason).contains("Loop detected")
    }

    @Test
    fun `store and forward queue prioritizes emergency and handles expiration`() {
        val queue = StoreAndForwardQueue(maxCapacity = 10, maxAttempts = 3)

        // Enqueue bulk, direct, and emergency messages
        queue.enqueue(nodeC, "Bulk 1".toByteArray(Charsets.UTF_8), MessagePriority.BULK)
        queue.enqueue(nodeC, "Direct 1".toByteArray(Charsets.UTF_8), MessagePriority.DIRECT_MESSAGE)
        queue.enqueue(nodeC, "Emergency SOS".toByteArray(Charsets.UTF_8), MessagePriority.EMERGENCY)

        val polled = queue.pollForDestination(nodeC)
        assertThat(polled).hasSize(3)
        // Emergency must come first!
        assertThat(polled[0].priority).isEqualTo(MessagePriority.EMERGENCY)
        assertThat(polled[1].priority).isEqualTo(MessagePriority.DIRECT_MESSAGE)
        assertThat(polled[2].priority).isEqualTo(MessagePriority.BULK)

        // Test expiration pruning
        val now = System.currentTimeMillis()
        queue.enqueue(nodeC, "Expired message".toByteArray(Charsets.UTF_8), MessagePriority.BULK, ttlMillis = 100L)
        assertThat(queue.size()).isEqualTo(1)

        val pruned = queue.pruneExpired(now + 200L)
        assertThat(pruned).isEqualTo(1)
        assertThat(queue.size()).isEqualTo(0)
    }

    @Test
    fun `continuous 60 transmissions through BLE framing and reassembly without loss or corruption`() {
        val fragmenter = com.astramesh.ble.BleFrameFragmenter()
        val reassembler = com.astramesh.ble.BleFrameReassembler()

        for (i in 1..60) {
            val voiceText = "Voice Transmission Packet #$i with localized Indic speech payload text data and telemetry"
            val ithantra = com.astramesh.core.IthantraMessage(
                senderId = nodeA,
                messageType = if (i % 10 == 0) com.astramesh.core.MessageType.ALERT else com.astramesh.core.MessageType.NORMAL,
                language = com.astramesh.core.Language.HINDI,
                sequenceNumber = i.toLong(),
                timestamp = System.currentTimeMillis(),
                text = voiceText
            )
            val packet = AstraPacket(
                type = AstraPacketType.DATA_UNICAST,
                flags = AstraPacketFlags(requiresAck = true),
                sequenceNumber = i.toLong(),
                packetId = PacketId.generate(nodeA, i.toLong(), System.currentTimeMillis()),
                source = nodeA,
                destination = nodeB,
                payload = ithantra.toBinary()
            )

            val rawBytes = AstraPacket.serialize(packet)
            // Fragment into small MTU slices
            val slices = fragmenter.fragment(rawBytes, maxSliceSize = 35)
            assertThat(slices.size).isAtLeast(2)

            var reassembledBytes: ByteArray? = null
            for (slice in slices) {
                val res = reassembler.feedSlice(slice)
                if (res != null) {
                    reassembledBytes = res
                }
            }

            assertThat(reassembledBytes).isNotNull()
            val deserialized = AstraPacket.deserialize(reassembledBytes!!)
            val decodedIthantra = com.astramesh.core.IthantraMessage.fromBinary(deserialized.payload)
            assertThat(decodedIthantra.text).isEqualTo(voiceText)
            assertThat(decodedIthantra.sequenceNumber).isEqualTo(i.toLong())
        }

        // Verify reassembler does not leak memory
        assertThat(reassembler.activePartialCount()).isEqualTo(0)
    }

    @Test
    fun `reliable delivery manager bounds capacity and cleans up on ack under high volume`() {
        val manager = ReliableDeliveryManager(maxRetries = 3, maxCapacity = 50)

        // Track 100 packets
        for (i in 1..100) {
            val pkt = AstraPacket(
                type = AstraPacketType.DATA_UNICAST,
                flags = AstraPacketFlags(requiresAck = true),
                sequenceNumber = i.toLong(),
                packetId = PacketId(i.toLong()),
                source = nodeA,
                destination = nodeB,
                payload = "Msg $i".toByteArray()
            )
            manager.trackPacket(pkt, nodeB)
        }

        // Bounded capacity enforced
        assertThat(manager.pendingCount()).isAtMost(50)

        // ACKing removes from tracking immediately
        for (i in 60..100) {
            manager.acknowledgeSequence(i.toLong(), nodeB)
        }
        assertThat(manager.pendingCount()).isLessThan(50)
    }
}

