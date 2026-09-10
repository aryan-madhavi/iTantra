package com.astramesh.core

enum class MessageType(val value: Byte) {
    NORMAL(0),
    ALERT(1),
    ACK(2),
    SOS(3);

    companion object {
        fun fromByte(value: Byte): MessageType {
            return entries.firstOrNull { it.value == value } ?: NORMAL
        }
    }
}
