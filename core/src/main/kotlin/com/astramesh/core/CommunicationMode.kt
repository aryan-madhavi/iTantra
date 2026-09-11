package com.astramesh.core

/**
 * Shared walkie-talkie communication mode across the entire application.
 *
 * BROADCAST: Default walkie-talkie mode. Transmissions are addressed to all reachable mesh nodes (NodeId.BROADCAST).
 * DIRECT: Explicit point-to-point mode targeting a specific recipient peer.
 * EMERGENCY: High-priority distress beacon mode with mesh-wide flooding.
 */
enum class CommunicationMode(val displayName: String, val wireCode: Byte) {
    BROADCAST("Broadcast", 0),
    DIRECT("Direct", 1),
    EMERGENCY("Emergency", 2);

    val isBroadcast: Boolean get() = this == BROADCAST
    val isDirect: Boolean get() = this == DIRECT
    val isEmergency: Boolean get() = this == EMERGENCY

    companion object {
        fun fromWireCode(code: Byte): CommunicationMode {
            return entries.firstOrNull { it.wireCode == code } ?: BROADCAST
        }
    }
}
