package com.astramesh.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import com.astramesh.core.AstraNetworkConfig
import com.astramesh.core.NodeId
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe BLE connection handle holding active GATT connection and link metrics.
 */
data class BleConnectionHandle(
    val nodeId: NodeId,
    val deviceAddress: String,
    val bluetoothGatt: BluetoothGatt?,
    var negotiatedMtu: Int = AstraNetworkConfig.DEFAULT_BLE_ATT_MTU,
    var currentRssi: Int = -70,
    var lastActivityTimestamp: Long = System.currentTimeMillis()
)

/**
 * Manages concurrent Central and Peripheral BLE connections within hardware limits.
 */
@SuppressLint("MissingPermission")
class BleConnectionPool(
    private val maxConnections: Int = AstraNetworkConfig.MAX_CONCURRENT_CONNECTIONS
) {
    private val connections = ConcurrentHashMap<NodeId, BleConnectionHandle>()

    fun get(nodeId: NodeId): BleConnectionHandle? = connections[nodeId]

    fun getAll(): List<BleConnectionHandle> = connections.values.toList()

    fun count(): Int = connections.size

    fun isConnected(nodeId: NodeId): Boolean = connections.containsKey(nodeId)

    @Synchronized
    fun addConnection(handle: BleConnectionHandle): Boolean {
        if (connections.size >= maxConnections && !connections.containsKey(handle.nodeId)) {
            // Evict oldest idle connection
            val oldest = connections.values.minByOrNull { it.lastActivityTimestamp }
            if (oldest != null) {
                removeConnection(oldest.nodeId)
            }
        }
        connections[handle.nodeId] = handle
        return true
    }

    @Synchronized
    fun removeConnection(nodeId: NodeId): BleConnectionHandle? {
        val handle = connections.remove(nodeId)
        handle?.bluetoothGatt?.close()
        return handle
    }

    fun updateMtu(nodeId: NodeId, mtu: Int) {
        connections[nodeId]?.let {
            it.negotiatedMtu = mtu
            it.lastActivityTimestamp = System.currentTimeMillis()
        }
    }

    fun updateRssi(nodeId: NodeId, rssi: Int) {
        connections[nodeId]?.let {
            it.currentRssi = rssi
            it.lastActivityTimestamp = System.currentTimeMillis()
        }
    }

    fun clear() {
        for (handle in connections.values) {
            handle.bluetoothGatt?.close()
        }
        connections.clear()
    }
}
