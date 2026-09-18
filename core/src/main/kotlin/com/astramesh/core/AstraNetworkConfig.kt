package com.astramesh.core

import java.util.UUID

/**
 * Protocol and network parameters for AstraMesh BLE communication.
 */
object AstraNetworkConfig {

    // Magic Bytes: "AS" in ASCII (0x41, 0x53)
    const val MAGIC_BYTE_0: Byte = 0x41
    const val MAGIC_BYTE_1: Byte = 0x53

    // Protocol Version
    const val PROTOCOL_VERSION: Int = 1

    // BLE Service and Characteristic UUIDs
    val ASTRA_SERVICE_UUID: UUID = UUID.fromString("0000a578-0000-1000-8000-00805f9b34fb")
    val ASTRA_TX_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000a579-0000-1000-8000-00805f9b34fb")
    val ASTRA_RX_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000a57a-0000-1000-8000-00805f9b34fb")
    val ASTRA_INFO_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000a57b-0000-1000-8000-00805f9b34fb")
    val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // BLE MTU & Slicing
    const val DEFAULT_BLE_ATT_MTU: Int = 23
    const val REQUESTED_BLE_MTU: Int = 517
    const val MAX_FRAME_PAYLOAD_SIZE: Int = 500
    const val ATTACHMENT_CHUNK_SIZE: Int = 480

    // Mesh Limits
    const val DEFAULT_TTL: Int = 7
    const val MAX_HOP_COUNT: Int = 15
    const val MAX_CONCURRENT_CONNECTIONS: Int = 7

    // Intervals & Timeouts (Milliseconds)
    const val HEARTBEAT_INTERVAL_MS: Long = 5_000L
    const val ROUTE_EXPIRATION_MS: Long = 60_000L
    // Peer staleness timeout: 15 seconds (aligned with 3x HEARTBEAT_INTERVAL_MS / BLE advertise timeout)
    const val PEER_STALENESS_TIMEOUT_MS: Long = 15_000L
    const val RECONNECT_BASE_DELAY_MS: Long = 1_000L
    const val RECONNECT_MAX_DELAY_MS: Long = 30_000L
    const val FRAME_REASSEMBLY_TIMEOUT_MS: Long = 10_000L
    const val STORE_AND_FORWARD_RETENTION_MS: Long = 7 * 24 * 60 * 60 * 1000L // 7 days

    // Metric Weights for Shortest Path calculation:
    // Cost = w_hop * Hops + w_lqi * (1 - LQI) + w_cong * Congestion + w_bat * (1 - Battery)
    const val WEIGHT_HOP: Float = 0.35f
    const val WEIGHT_LQI: Float = 0.35f
    const val WEIGHT_CONGESTION: Float = 0.15f
    const val WEIGHT_BATTERY: Float = 0.15f
}
