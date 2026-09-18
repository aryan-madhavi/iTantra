package com.astramesh.testing

import com.astramesh.core.NodeId
import com.astramesh.domain.model.MessagePriority
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

class MultiNodeSimulationTest {

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
    fun testDirectDeliveryBetweenTwoNodes() {
        val nodeA = createNode(1L)
        val nodeB = createNode(2L)

        // Topology: A <-> B
        medium.addBidirectionalLink(nodeA.nodeId, nodeB.nodeId, latencyMs = 0L)
        nodeA.routingTable.updateRoute(nodeB.nodeId, nodeB.nodeId, cost = 1.0f, hopCount = 1, sequenceNumber = 1L)
        nodeB.routingTable.updateRoute(nodeA.nodeId, nodeA.nodeId, cost = 1.0f, hopCount = 1, sequenceNumber = 1L)

        val payload = "Hello Node B!".toByteArray()
        val pid = nodeA.sendPacket(nodeB.nodeId, payload)

        // Give coroutines time to propagate through channel
        Thread.sleep(300)

        assertThat(nodeB.packetsReceived.get()).isEqualTo(1L)
        val receivedPacket = nodeB.receivedPacketsHistory.firstOrNull()
        assertThat(receivedPacket).isNotNull()
        assertThat(receivedPacket!!.packetId).isEqualTo(pid)
        assertThat(receivedPacket.source).isEqualTo(nodeA.nodeId)
        assertThat(receivedPacket.destination).isEqualTo(nodeB.nodeId)
        assertThat(receivedPacket.payload).isEqualTo(payload)
        assertThat(receivedPacket.hopCount).isEqualTo(0)
    }

    @Test
    fun testFiveNodeLinearMeshRelay() {
        // Topology: A <-> B <-> C <-> D <-> E
        val nodeA = createNode(10L)
        val nodeB = createNode(20L)
        val nodeC = createNode(30L)
        val nodeD = createNode(40L)
        val nodeE = createNode(50L)

        medium.addBidirectionalLink(nodeA.nodeId, nodeB.nodeId, latencyMs = 0L)
        medium.addBidirectionalLink(nodeB.nodeId, nodeC.nodeId, latencyMs = 0L)
        medium.addBidirectionalLink(nodeC.nodeId, nodeD.nodeId, latencyMs = 0L)
        medium.addBidirectionalLink(nodeD.nodeId, nodeE.nodeId, latencyMs = 0L)

        // Configure routing tables for multi-hop path A -> E
        nodeA.routingTable.updateRoute(nodeE.nodeId, nodeB.nodeId, cost = 4.0f, hopCount = 4, sequenceNumber = 1L)
        nodeB.routingTable.updateRoute(nodeE.nodeId, nodeC.nodeId, cost = 3.0f, hopCount = 3, sequenceNumber = 1L)
        nodeC.routingTable.updateRoute(nodeE.nodeId, nodeD.nodeId, cost = 2.0f, hopCount = 2, sequenceNumber = 1L)
        nodeD.routingTable.updateRoute(nodeE.nodeId, nodeE.nodeId, cost = 1.0f, hopCount = 1, sequenceNumber = 1L)

        val testMessage = "AstraMesh multi-hop transmission across 4 relays!".toByteArray()
        val pid = nodeA.sendPacket(nodeE.nodeId, testMessage)

        Thread.sleep(300)

        assertThat(nodeE.packetsReceived.get()).isEqualTo(1L)
        val receivedPacket = nodeE.receivedPacketsHistory.firstOrNull()
        assertThat(receivedPacket).isNotNull()
        assertThat(receivedPacket!!.packetId).isEqualTo(pid)
        assertThat(receivedPacket.source).isEqualTo(nodeA.nodeId)
        assertThat(receivedPacket.destination).isEqualTo(nodeE.nodeId)
        assertThat(receivedPacket.payload).isEqualTo(testMessage)
        assertThat(receivedPacket.hopCount).isEqualTo(3)

        // Verify intermediate relay metrics
        assertThat(nodeB.packetsRelayed.get()).isEqualTo(1L)
        assertThat(nodeC.packetsRelayed.get()).isEqualTo(1L)
        assertThat(nodeD.packetsRelayed.get()).isEqualTo(1L)
    }

    @Test
    fun testBroadcastFloodingWithLoopDetection() {
        // Topology triangle: A <-> B, B <-> C, C <-> A
        val nodeA = createNode(100L)
        val nodeB = createNode(200L)
        val nodeC = createNode(300L)

        medium.addBidirectionalLink(nodeA.nodeId, nodeB.nodeId, latencyMs = 0L)
        medium.addBidirectionalLink(nodeB.nodeId, nodeC.nodeId, latencyMs = 0L)
        medium.addBidirectionalLink(nodeC.nodeId, nodeA.nodeId, latencyMs = 0L)

        val alertPayload = "EMERGENCY BROADCAST".toByteArray()
        nodeA.sendPacket(NodeId.BROADCAST, alertPayload, priority = MessagePriority.EMERGENCY)

        Thread.sleep(300)

        assertThat(nodeB.packetsReceived.get()).isAtLeast(1L)
        assertThat(nodeC.packetsReceived.get()).isAtLeast(1L)

        val bReceived = nodeB.receivedPacketsHistory.firstOrNull()
        val cReceived = nodeC.receivedPacketsHistory.firstOrNull()
        assertThat(bReceived).isNotNull()
        assertThat(cReceived).isNotNull()
        assertThat(bReceived!!.payload).isEqualTo(alertPayload)
        assertThat(cReceived!!.payload).isEqualTo(alertPayload)

        // Node A should NOT receive its own broadcast
        assertThat(nodeA.packetsReceived.get()).isEqualTo(0L)
    }

    @Test
    fun testStoreAndForwardDelayTolerantNetworkDelivery() {
        val nodeA = createNode(500L)
        val nodeB = createNode(600L)

        // Initially no link between A and B
        val dtnPayload = "Stored packet awaiting contact".toByteArray()
        nodeA.sendPacket(nodeB.nodeId, dtnPayload)

        // Verify it was enqueued in StoreAndForwardQueue
        assertThat(nodeA.storeAndForwardQueue.size()).isEqualTo(1)

        // Now link becomes available (Peer encounter)
        medium.addBidirectionalLink(nodeA.nodeId, nodeB.nodeId, latencyMs = 0L)
        nodeA.routingTable.updateRoute(nodeB.nodeId, nodeB.nodeId, cost = 1.0f, hopCount = 1, sequenceNumber = 1L)
        nodeA.onPeerConnected(nodeB.nodeId)

        Thread.sleep(300)

        assertThat(nodeB.packetsReceived.get()).isEqualTo(1L)
        val received = nodeB.receivedPacketsHistory.firstOrNull()
        assertThat(received).isNotNull()
        assertThat(received!!.payload).isEqualTo(dtnPayload)
        assertThat(nodeA.storeAndForwardQueue.size()).isEqualTo(0)
    }

    @Test
    fun testTtlExpirationDropsPacket() {
        val nodeA = createNode(700L)
        val nodeB = createNode(800L)
        val nodeC = createNode(900L)

        medium.addBidirectionalLink(nodeA.nodeId, nodeB.nodeId, latencyMs = 0L)
        medium.addBidirectionalLink(nodeB.nodeId, nodeC.nodeId, latencyMs = 0L)

        nodeA.routingTable.updateRoute(nodeC.nodeId, nodeB.nodeId, cost = 2.0f, hopCount = 2, sequenceNumber = 1L)
        nodeB.routingTable.updateRoute(nodeC.nodeId, nodeC.nodeId, cost = 1.0f, hopCount = 1, sequenceNumber = 1L)

        val payload = "TTL constrained packet".toByteArray()
        val packet = com.astramesh.mesh.AstraPacket(
            type = com.astramesh.mesh.AstraPacketType.DATA_UNICAST,
            flags = com.astramesh.mesh.AstraPacketFlags(relayAllowed = true),
            ttl = 1, // Only 1 hop allowed (A -> B). B will drop it when trying to forward to C.
            hopCount = 0,
            sequenceNumber = 1L,
            packetId = com.astramesh.core.PacketId.generate(nodeA.nodeId, 1L, System.currentTimeMillis()),
            source = nodeA.nodeId,
            destination = nodeC.nodeId,
            visitedBloomFilter = com.astramesh.routing.LoopDetector.addNode(0, nodeA.nodeId),
            payload = payload
        )

        medium.transmit(nodeA.nodeId, nodeB.nodeId, com.astramesh.mesh.AstraPacket.serialize(packet))

        Thread.sleep(300)

        assertThat(nodeB.packetsDropped.get()).isEqualTo(1L)
        assertThat(nodeC.packetsReceived.get()).isEqualTo(0L)
    }

    @Test
    fun testRoutingLoopIsDropped() {
        val nodeA = createNode(1000L)
        val nodeB = createNode(1100L)

        medium.addBidirectionalLink(nodeA.nodeId, nodeB.nodeId, latencyMs = 0L)
        
        // Node A routes to B, but Node B routes to B! (Misconfiguration)
        nodeA.routingTable.updateRoute(NodeId(9999L), nodeB.nodeId, cost = 1.0f, hopCount = 1, sequenceNumber = 1L)
        nodeB.routingTable.updateRoute(NodeId(9999L), nodeB.nodeId, cost = 1.0f, hopCount = 1, sequenceNumber = 1L)

        nodeA.sendPacket(NodeId(9999L), "Looping packet".toByteArray())

        Thread.sleep(300)

        // Node B should drop it because nextHop == itself
        assertThat(nodeB.packetsDropped.get()).isEqualTo(1L)
    }
}
