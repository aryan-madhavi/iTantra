package com.astramesh.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    private val _ephemeralNodeId = MutableStateFlow<NodeId?>(null)
    val ephemeralNodeId: StateFlow<NodeId?> = _ephemeralNodeId.asStateFlow()

    val meshStatus: StateFlow<MeshStatus> = meshRepository.meshStatus

    init {
        viewModelScope.launch {
            _displayName.value = identityRepository.getDisplayName()
            val id = identityRepository.getLocalIdentity()
            _rootPublicKey.value = id.publicKey
            _ephemeralNodeId.value = identityRepository.getRotatingNodeId()
        }
    }

    fun updateDisplayName(name: String) {
        _displayName.value = name
        viewModelScope.launch {
            identityRepository.setDisplayName(name)
        }
    }

    fun emergencyWipeAllData(onComplete: () -> Unit) {
        viewModelScope.launch {
            identityRepository.wipeAllCryptographicKeys()
            meshRepository.stopMesh()
            onComplete()
        }
    }
}
