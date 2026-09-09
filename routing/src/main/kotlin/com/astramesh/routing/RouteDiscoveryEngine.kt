package com.astramesh.routing

import com.astramesh.core.NodeId
import java.util.concurrent.ConcurrentHashMap

/**
 * Route request and discovery orchestration.
 */
data class RouteDiscoveryRequest(
    val requestId: Long,
    val source: NodeId,
    val destination: NodeId,
    val sequenceNumber: Long,
    val hopLimit: Int = 5
)

data class RouteDiscoveryReply(
    val requestId: Long,
    val destination: NodeId,
    val responder: NodeId,
    val accumulatedCost: Float,
    val hopCount: Int
)

class RouteDiscoveryEngine(
    private val localNodeId: NodeId,
    private val routingTable: RoutingTable
) {
    private val pendingRequests = ConcurrentHashMap<Long, RouteDiscoveryRequest>()
    private var nextRequestId = 1L

    @Synchronized
    fun createRouteRequest(destination: NodeId): RouteDiscoveryRequest {
        val req = RouteDiscoveryRequest(
            requestId = nextRequestId++,
            source = localNodeId,
            destination = destination,
            sequenceNumber = System.currentTimeMillis()
        )
        pendingRequests[req.requestId] = req
        return req
    }

    fun handleRouteReply(reply: RouteDiscoveryReply, arrivedViaNode: NodeId): Boolean {
        return routingTable.updateRoute(
            destination = reply.destination,
            nextHop = arrivedViaNode,
            cost = reply.accumulatedCost,
            hopCount = reply.hopCount,
            sequenceNumber = System.currentTimeMillis()
        )
    }
}
