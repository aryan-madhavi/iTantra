package com.astramesh.routing

import com.astramesh.core.NodeId
import java.util.PriorityQueue

/**
 * Dijkstra shortest path solver over local topology graph.
 */
class ShortestPathFinder {

    data class GraphEdge(val target: NodeId, val weight: Float)

    fun findShortestPath(
        source: NodeId,
        destination: NodeId,
        adjacencyList: Map<NodeId, List<GraphEdge>>
    ): List<NodeId>? {
        if (source == destination) return listOf(source)

        val distances = mutableMapOf<NodeId, Float>().withDefault { Float.MAX_VALUE }
        val previous = mutableMapOf<NodeId, NodeId>()
        val pq = PriorityQueue<Pair<NodeId, Float>>(compareBy { it.second })

        distances[source] = 0f
        pq.add(source to 0f)

        while (pq.isNotEmpty()) {
            val (current, currentDist) = pq.poll()
            if (current == destination) break

            if (currentDist > distances.getValue(current)) continue

            for (edge in adjacencyList[current].orEmpty()) {
                val newDist = currentDist + edge.weight
                if (newDist < distances.getValue(edge.target)) {
                    distances[edge.target] = newDist
                    previous[edge.target] = current
                    pq.add(edge.target to newDist)
                }
            }
        }

        if (!previous.containsKey(destination)) return null

        val path = mutableListOf<NodeId>()
        var curr: NodeId? = destination
        while (curr != null) {
            path.add(0, curr)
            curr = previous[curr]
        }

        return path
    }
}
