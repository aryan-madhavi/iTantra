package com.astramesh.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astramesh.common.AstraLog
import com.astramesh.core.NodeId
import com.astramesh.crypto.AstraPublicKey
import com.astramesh.domain.model.MeshStatus
import com.astramesh.domain.repository.IdentityRepository
import com.astramesh.domain.repository.MeshRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val identityRepository: IdentityRepository,
    private val meshRepository: MeshRepository
) : ViewModel() {

    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _rootPublicKey = MutableStateFlow<AstraPublicKey?>(null)
    val rootPublicKey: StateFlow<AstraPublicKey?> = _rootPublicKey.asStateFlow()

    private val _permanentNodeId = MutableStateFlow<NodeId?>(null)
    val permanentNodeId: StateFlow<NodeId?> = _permanentNodeId.asStateFlow()

    private val _publicKeyFingerprint = MutableStateFlow<String>("")
    val publicKeyFingerprint: StateFlow<String> = _publicKeyFingerprint.asStateFlow()

    private val _trustStatus = MutableStateFlow<String>("Verified Cryptographic Identity")
    val trustStatus: StateFlow<String> = _trustStatus.asStateFlow()

    val meshStatus: StateFlow<MeshStatus> = meshRepository.meshStatus

    init {
        loadIdentity()
    }

    private fun loadIdentity() {
        viewModelScope.launch {
            _displayName.value = identityRepository.getDisplayName()
            val id = identityRepository.getLocalIdentity()
            _rootPublicKey.value = id.publicKey
            val nodeId = identityRepository.getRotatingNodeId()
            _permanentNodeId.value = nodeId
            _publicKeyFingerprint.value = com.astramesh.common.ByteUtils.toHexString(id.publicKey.rawBytes).chunked(4).joinToString(":")
            AstraLog.d("SettingsViewModel", "IDENTITY Permanent ID loaded: ${nodeId.toHex()} Fingerprint: ${_publicKeyFingerprint.value}")
        }
    }

    fun updateDisplayName(name: String) {
        _displayName.value = name
        viewModelScope.launch {
            identityRepository.setDisplayName(name)
            AstraLog.d("SettingsViewModel", "IDENTITY Display name updated to: $name")
        }
    }

    fun resetIdentity(onComplete: () -> Unit) {
        viewModelScope.launch {
            AstraLog.d("SettingsViewModel", "IDENTITY Resetting permanent cryptographic identity")
            identityRepository.wipeAllCryptographicKeys()
            meshRepository.stopMesh()
            loadIdentity()
            meshRepository.startMesh()
            onComplete()
        }
    }

    fun exportIdentity(): String {
        val pubKeyHex = _rootPublicKey.value?.let { com.astramesh.common.ByteUtils.toHexString(it.rawBytes) } ?: ""
        val nodeIdHex = _permanentNodeId.value?.toHex() ?: ""
        AstraLog.d("SettingsViewModel", "IDENTITY Exported permanent identity: $nodeIdHex")
        return "ASTRA-ID:v1:$nodeIdHex:$pubKeyHex"
    }

    private val _isClearing = MutableStateFlow(false)
    val isClearing: StateFlow<Boolean> = _isClearing.asStateFlow()

    fun clearAppDataAndCache(onComplete: () -> Unit) {
        viewModelScope.launch {
            _isClearing.value = true
            AstraLog.i("SettingsViewModel", "CLEANUP Purging cache, buffers, queues, and messages without touching identity")
            try {
                meshRepository.clearAppDataAndCache()
            } catch (e: Exception) {
                AstraLog.e("SettingsViewModel", "Error clearing app data and cache", e)
            } finally {
                _isClearing.value = false
            }
            onComplete()
        }
    }

    fun emergencyWipeAllData(onComplete: () -> Unit) {
        viewModelScope.launch {
            AstraLog.d("SettingsViewModel", "IDENTITY Emergency wiping all cryptographic keys and data")
            identityRepository.wipeAllCryptographicKeys()
            meshRepository.stopMesh()
            onComplete()
        }
    }
}


