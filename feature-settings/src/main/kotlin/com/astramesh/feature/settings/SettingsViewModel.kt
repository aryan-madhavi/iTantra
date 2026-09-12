package com.astramesh.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astramesh.common.AstraLog
import com.astramesh.core.NodeId
import com.astramesh.core.SttVadConfig
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
    private val meshRepository: MeshRepository,
    private val voiceEngineManager: com.astramesh.services.VoiceEngineManager? = null
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

    // -------------------------------------------------------
    // STT / VAD Configuration State (fed from VoiceEngineManager)
    // -------------------------------------------------------
    val sttVadConfig: StateFlow<SttVadConfig> = voiceEngineManager?.sttVadConfigFlow
        ?: MutableStateFlow(SttVadConfig())

    fun setEnableKeywordDetection(enabled: Boolean) {
        voiceEngineManager?.sttVadConfig = voiceEngineManager?.sttVadConfig?.copy(enableKeywordDetection = enabled) ?: return
        AstraLog.d("SettingsViewModel", "STT_VAD keywordDetection=$enabled")
    }

    fun setWakeWordSensitivity(value: Float) {
        voiceEngineManager?.sttVadConfig = voiceEngineManager?.sttVadConfig?.copy(wakeWordSensitivity = value) ?: return
        AstraLog.d("SettingsViewModel", "STT_VAD wakeWordSensitivity=$value")
    }

    fun setCommandCaptureTimeoutMs(value: Long) {
        voiceEngineManager?.sttVadConfig = voiceEngineManager?.sttVadConfig?.copy(commandCaptureTimeoutMs = value) ?: return
        AstraLog.d("SettingsViewModel", "STT_VAD commandCaptureTimeoutMs=$value")
    }

    fun setVadThreshold(value: Float) {
        voiceEngineManager?.sttVadConfig = voiceEngineManager?.sttVadConfig?.copy(vadThreshold = value) ?: return
        AstraLog.d("SettingsViewModel", "STT_VAD vadThreshold=$value (effectiveRms=${voiceEngineManager?.sttVadConfig?.effectiveRmsThreshold})")
    }

    fun setVadMinSilenceMs(value: Long) {
        voiceEngineManager?.sttVadConfig = voiceEngineManager?.sttVadConfig?.copy(vadMinSilenceMs = value) ?: return
        AstraLog.d("SettingsViewModel", "STT_VAD vadMinSilenceMs=$value")
    }

    fun setVadMinSpeechMs(value: Long) {
        voiceEngineManager?.sttVadConfig = voiceEngineManager?.sttVadConfig?.copy(vadMinSpeechMs = value) ?: return
        AstraLog.d("SettingsViewModel", "STT_VAD vadMinSpeechMs=$value")
    }

    fun setVadSpeechPadMs(value: Long) {
        voiceEngineManager?.sttVadConfig = voiceEngineManager?.sttVadConfig?.copy(vadSpeechPadMs = value) ?: return
        AstraLog.d("SettingsViewModel", "STT_VAD vadSpeechPadMs=$value")
    }

    fun setPreferredLanguage(language: com.astramesh.core.Language) {
        viewModelScope.launch {
            meshRepository.setPreferredLanguage(language)
            AstraLog.d("SettingsViewModel", "SETTINGS Preferred language updated to: ${language.name}")
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

    fun emergencyWipeAllData(onComplete: () -> Unit) {
        viewModelScope.launch {
            AstraLog.d("SettingsViewModel", "IDENTITY Emergency wiping all cryptographic keys and data")
            identityRepository.wipeAllCryptographicKeys()
            meshRepository.stopMesh()
            onComplete()
        }
    }
}

