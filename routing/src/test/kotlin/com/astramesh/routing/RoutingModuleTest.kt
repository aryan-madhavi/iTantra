package com.astramesh.routing

import com.astramesh.core.NodeId
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RoutingModuleTest {

    private val localNode = NodeId(0x1AAA000000000001L)

    @Test
    fun `composite metric calculation weights parameters appropriately`() {
        val goodLinkCost = RouteMetricCalculator.calculateLinkCost(
            hopCount = 1,
            linkQuality = 0.95f,
            congestionFactor = 0.05f,
            batteryLevel = 0.90f
        )

        val badLinkCost = RouteMetricCalculator.calculateLinkCost(
            hopCount = 3,
            linkQuality = 0.20f,
            congestionFactor = 0.80f,
            batteryLevel = 0.15f
        )

        assertThat(goodLinkCost).isLessThan(badLinkCost)
    }

    @Test
    fun `loop detector flags duplicate node entries in bloom filter`() {
        var bloom = 0
        val node1 = NodeId(0x1111222233334444L)
        val node2 = NodeId(0x5555666677778888L)
        val node3 = NodeId(0x1999AAAABBBBCCCCL)

        assertThat(LoopDetector.containsNode(bloom, node1)).isFalse()

        bloom = LoopDetector.addNode(bloom, node1)
        assertThat(LoopDetector.containsNode(bloom, node1)).isTrue()
        assertThat(LoopDetector.containsNode(bloom, node2)).isFalse()

        bloom = LoopDetector.addNode(bloom, node2)
        assertThat(LoopDetector.containsNode(bloom, node1)).isTrue()
        assertThat(LoopDetector.containsNode(bloom, node2)).isTrue()
        assertThat(LoopDetector.containsNode(bloom, node3)).isFalse()
    }

    @Test
    fun `routing table stores, updates and prunes expired routes`() {
        val table = RoutingTable(localNode)
        val dest = NodeId(0x1BBB000000000002L)
        val nextHop = NodeId(0x1CCC000000000003L)

        val inserted = table.updateRoute(
            destination = dest,
            nextHop = nextHop,
            cost = 1.5f,
            hopCount = 2,
            sequenceNumber = 100L,
            ttlMillis = 50L
        )
        assertThat(inserted).isTrue()

        val route = table.getRoute(dest)
        assertThat(route).isNotNull()
        assertThat(route?.nextHop).isEqualTo(nextHop)

        // Lower cost route with same sequence number replaces existing
        val betterNextHop = NodeId(0x1DDD000000000004L)
        val updated = table.updateRoute(
            destination = dest,
            nextHop = betterNextHop,
            cost = 1.0f,
            hopCount = 1,
            sequenceNumber = 100L,
            ttlMillis = 50L
        )
        assertThat(updated).isTrue()
        assertThat(table.getRoute(dest)?.nextHop).isEqualTo(betterNextHop)

        // Sleep to test expiration
        Thread.sleep(60L)
        assertThat(table.getRoute(dest)).isNull()
    }

    @Test
    fun `shortest path finder resolves multi-hop graph`() {
        val finder = ShortestPathFinder()
        val nodeA = NodeId(1L)
        val nodeB = NodeId(2L)
        val nodeC = NodeId(3L)
        val nodeD = NodeId(4L)

        // Graph:
        // A -> B (weight 1.0) -> D (weight 1.0) [Total: 2.0]
        // A -> C (weight 5.0) -> D (weight 5.0) [Total: 10.0]
        val graph = mapOf(
            nodeA to listOf(
                ShortestPathFinder.GraphEdge(nodeB, 1.0f),
                ShortestPathFinder.GraphEdge(nodeC, 5.0f)
            ),
            nodeB to listOf(
                ShortestPathFinder.GraphEdge(nodeD, 1.0f)
            ),
            nodeC to listOf(
                ShortestPathFinder.GraphEdge(nodeD, 5.0f)
            ),
            nodeD to emptyList()
        )

        val path = finder.findShortestPath(nodeA, nodeD, graph)
        assertThat(path).isEqualTo(listOf(nodeA, nodeB, nodeD))
    }
}
