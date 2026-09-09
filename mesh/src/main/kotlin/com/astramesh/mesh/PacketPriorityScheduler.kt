package com.astramesh.mesh

import com.astramesh.domain.model.MessagePriority
import java.util.concurrent.PriorityBlockingQueue

data class ScheduledPacket(
    val packet: AstraPacket,
    val priority: MessagePriority,
    val targetNextHop: com.astramesh.core.NodeId?,
    val timestamp: Long = System.currentTimeMillis()
) : Comparable<ScheduledPacket> {
    override fun compareTo(other: ScheduledPacket): Int {
        // Lower level number = higher priority
        val comp = priority.level.compareTo(other.priority.level)
        return if (comp != 0) comp else timestamp.compareTo(other.timestamp)
    }
}

/**
 * Priority queue scheduler guaranteeing immediate dispatch of EMERGENCY and CONTROL frames.
 */
class PacketPriorityScheduler {

    private val queue = PriorityBlockingQueue<ScheduledPacket>()

    fun enqueue(packet: AstraPacket, priority: MessagePriority, targetNextHop: com.astramesh.core.NodeId? = null) {
        queue.offer(ScheduledPacket(packet, priority, targetNextHop))
    }

    fun poll(): ScheduledPacket? = queue.poll()

    fun peek(): ScheduledPacket? = queue.peek()

    fun size(): Int = queue.size

    fun isEmpty(): Boolean = queue.isEmpty()

    fun clear() = queue.clear()
}
