package com.astramesh.routing

import com.astramesh.core.AstraNetworkConfig
import com.astramesh.core.NodeId
import com.astramesh.domain.model.Route
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe routing table managing active routes, hop metrics, and soft-state expirations.
 */
class RoutingTable(
    private val localNodeId: NodeId
) {
    private val routes = ConcurrentHashMap<NodeId, Route>()

    fun getRoute(destination: NodeId): Route? {
        val route = routes[destination] ?: return null
        if (System.currentTimeMillis() > route.expireTimestamp) {
            routes.remove(destination)
            return null
        }
        return route
    }

    fun getAllActiveRoutes(): List<Route> {
        val now = System.currentTimeMillis()
        return routes.values.filter { it.expireTimestamp > now }
    }

    fun updateRoute(
        destination: NodeId,
        nextHop: NodeId,
        cost: Float,
        hopCount: Int,
        sequenceNumber: Long,
        ttlMillis: Long = AstraNetworkConfig.ROUTE_EXPIRATION_MS
    ): Boolean {
        if (destination == localNodeId) return false // Do not route to self

        val existing = routes[destination]
        val now = System.currentTimeMillis()

        if (existing == null || existing.expireTimestamp <= now) {
            // New or expired route
            routes[destination] = Route(
                destination = destination,
                nextHop = nextHop,
                cost = cost,
                hopCount = hopCount,
                sequenceNumber = sequenceNumber,
                expireTimestamp = now + ttlMillis
            )
            return true
        }

        // Rule 1: Fresh sequence number takes precedence
        if (sequenceNumber > existing.sequenceNumber) {
            routes[destination] = Route(
                destination = destination,
                nextHop = nextHop,
                cost = cost,
                hopCount = hopCount,
                sequenceNumber = sequenceNumber,
                expireTimestamp = now + ttlMillis
            )
            return true
        }

        // Rule 2: Same sequence number, lower cost wins
        if (sequenceNumber == existing.sequenceNumber && cost < existing.cost) {
            routes[destination] = Route(
                destination = destination,
                nextHop = nextHop,
                cost = cost,
                hopCount = hopCount,
                sequenceNumber = sequenceNumber,
                expireTimestamp = now + ttlMillis
            )
            return true
        }

        // Refresh expiration if same next-hop reaffirmed
        if (existing.nextHop == nextHop) {
            routes[destination] = existing.copy(expireTimestamp = now + ttlMillis)
        }

        return false
    }

    fun invalidateRoute(destination: NodeId) {
        routes.remove(destination)
    }

    fun pruneExpired(): Int {
        val now = System.currentTimeMillis()
        var pruned = 0
        val iterator = routes.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.expireTimestamp <= now) {
                iterator.remove()
                pruned++
            }
        }
        return pruned
    }

    fun clear() {
        routes.clear()
    }
}
