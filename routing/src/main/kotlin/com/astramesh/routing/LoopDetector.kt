package com.astramesh.routing

import com.astramesh.core.NodeId

/**
 * Loop detector utilizing a 32-bit compact Bloom filter header field.
 * Fast zero-allocation check preventing circulating packet storms.
 */
object LoopDetector {

    private const val BLOOM_SIZE_BITS = 32

    private fun hash1(nodeId: NodeId): Int {
        val v = nodeId.value
        val h = (v xor (v ushr 32)).toInt()
        return Math.floorMod(h, BLOOM_SIZE_BITS)
    }

    private fun hash2(nodeId: NodeId): Int {
        val v = nodeId.value * -0x61c8864680b583ebL
        val h = ((v xor (v ushr 32))).toInt()
        return Math.floorMod(h, BLOOM_SIZE_BITS)
    }

    fun containsNode(bloomFilter: Int, nodeId: NodeId): Boolean {
        val bit1 = 1 shl hash1(nodeId)
        val bit2 = 1 shl hash2(nodeId)
        return (bloomFilter and bit1) != 0 && (bloomFilter and bit2) != 0
    }

    fun addNode(bloomFilter: Int, nodeId: NodeId): Int {
        val bit1 = 1 shl hash1(nodeId)
        val bit2 = 1 shl hash2(nodeId)
        return bloomFilter or bit1 or bit2
    }
}
