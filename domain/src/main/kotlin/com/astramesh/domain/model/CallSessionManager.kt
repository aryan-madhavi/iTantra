package com.astramesh.domain.model

import com.astramesh.core.NodeId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class IncomingCall(
    val callerNodeId: NodeId,
    val callerDisplayName: String,
    val timestamp: Long = System.currentTimeMillis()
)

object CallSessionManager {
    private val _incomingCall = MutableStateFlow<IncomingCall?>(null)
    val incomingCall: StateFlow<IncomingCall?> = _incomingCall.asStateFlow()

    private val _activeCallPeer = MutableStateFlow<NodeId?>(null)
    val activeCallPeer: StateFlow<NodeId?> = _activeCallPeer.asStateFlow()

    fun triggerIncomingCall(callerNodeId: NodeId, callerDisplayName: String) {
        if (_activeCallPeer.value != null && _activeCallPeer.value != callerNodeId) {
            // Already in an active call with another peer
            return
        }
        _incomingCall.value = IncomingCall(callerNodeId, callerDisplayName)
    }

    fun dismissIncomingCall() {
        _incomingCall.value = null
    }

    fun acceptCall(callerNodeId: NodeId) {
        _activeCallPeer.value = callerNodeId
        _incomingCall.value = null
    }

    fun declineCall(callerNodeId: NodeId) {
        if (_incomingCall.value?.callerNodeId == callerNodeId) {
            _incomingCall.value = null
        }
    }

    fun endCall() {
        _activeCallPeer.value = null
        _incomingCall.value = null
    }
}
