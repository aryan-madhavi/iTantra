package com.astramesh.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astramesh.common.AstraLog
import com.astramesh.core.ChatId
import com.astramesh.core.NodeId
import com.astramesh.core.VoiceMode
import com.astramesh.core.VoicePayload
import com.astramesh.domain.model.Message
import com.astramesh.domain.model.MessagePriority
import com.astramesh.domain.repository.MessageRepository
import com.astramesh.domain.usecase.SendMessageUseCase
import com.astramesh.services.VoiceEngineManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

enum class ContinuousState {
    IDLE,
    LISTENING,
    SPEECH_DETECTED,
    TRANSCRIBING,
    THINKING,
    SPEAKING,
    CONNECTED,
    DISCONNECTED
}

class ConversationViewModel(
    val chatId: ChatId,
    val recipientId: NodeId,
    private val messageRepository: MessageRepository,
    private val sendMessageUseCase: SendMessageUseCase,
    private val voiceEngineManager: VoiceEngineManager? = null
) : ViewModel() {

    val messages: StateFlow<List<Message>> = messageRepository.observeMessages(chatId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isRecordingPtt = MutableStateFlow(false)
    val isRecordingPtt: StateFlow<Boolean> = _isRecordingPtt.asStateFlow()

    private val _currentVoiceMode = MutableStateFlow(VoiceMode.PUSH_TO_TALK)
    val currentVoiceMode: StateFlow<VoiceMode> = _currentVoiceMode.asStateFlow()

    private val _isContinuousModeActive = MutableStateFlow(false)
    val isContinuousModeActive: StateFlow<Boolean> = _isContinuousModeActive.asStateFlow()

    private val _continuousState = MutableStateFlow(ContinuousState.IDLE)
    val continuousState: StateFlow<ContinuousState> = _continuousState.asStateFlow()

    private val _liveRms = MutableStateFlow(0)
    val liveRms: StateFlow<Int> = _liveRms.asStateFlow()

    private val _recordingDurationSec = MutableStateFlow(0)
    val recordingDurationSec: StateFlow<Int> = _recordingDurationSec.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _isReceiverSpeaking = MutableStateFlow(false)
    val isReceiverSpeaking: StateFlow<Boolean> = _isReceiverSpeaking.asStateFlow()

    private val _currentSpeaker = MutableStateFlow<String?>(null)
    val currentSpeaker: StateFlow<String?> = _currentSpeaker.asStateFlow()

    private val recordedAudioBuffer = ByteArrayOutputStream()
    private var pttSequence = 0
    private var timerJob: Job? = null

    fun onInputTextChanged(newText: String) {
        _inputText.value = newText
    }

    fun setVoiceMode(mode: VoiceMode) {
        _currentVoiceMode.value = mode
        AstraLog.d("ConversationViewModel", "VOICE_MODE Selected mode=${mode.name}")
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty()) return

        _inputText.value = ""
        viewModelScope.launch {
            AstraLog.d("ConversationViewModel", "SEND text length=${text.length} recipient=$recipientId")
            sendMessageUseCase(chatId, recipientId, text)
        }
    }

    /**
     * Push-To-Talk / Walkie-Talkie: Hold to Talk
     */
    fun startPtt() {
        if (_isRecordingPtt.value) return
        _isRecordingPtt.value = true
        _recordingDurationSec.value = 0
        recordedAudioBuffer.reset()
        pttSequence = 0

        val modeTag = when (_currentVoiceMode.value) {
            VoiceMode.WALKIE_TALKIE -> "WALKIE"
            VoiceMode.EMERGENCY -> "SOS"
            else -> "PTT"
        }
        AstraLog.d("ConversationViewModel", "$modeTag START recording initiated")

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive && _isRecordingPtt.value) {
                delay(1000)
                _recordingDurationSec.value += 1
            }
        }

        voiceEngineManager?.startRecording(viewModelScope) { chunk, rms ->
            _liveRms.value = rms
            synchronized(recordedAudioBuffer) {
                recordedAudioBuffer.write(chunk)
            }
        }
    }

    /**
     * Push-To-Talk / Walkie-Talkie: Release to Send
     */
    fun stopPtt() {
        if (!_isRecordingPtt.value) return
        _isRecordingPtt.value = false
        _liveRms.value = 0
        timerJob?.cancel()
        timerJob = null

        voiceEngineManager?.stopRecording()
        val mode = _currentVoiceMode.value
        val modeTag = when (mode) {
            VoiceMode.WALKIE_TALKIE -> "WALKIE"
            VoiceMode.EMERGENCY -> "SOS"
            else -> "PTT"
        }
        AstraLog.d("ConversationViewModel", "$modeTag STOP recording ended duration=${_recordingDurationSec.value}s")

        val audioBytes = synchronized(recordedAudioBuffer) {
            recordedAudioBuffer.toByteArray()
        }

        if (audioBytes.isNotEmpty()) {
            val priority = if (mode == VoiceMode.EMERGENCY) MessagePriority.EMERGENCY else MessagePriority.DIRECT_MESSAGE
            val payload = VoicePayload(
                mode = mode,
                sequence = pttSequence++,
                isFinal = true,
                transcript = "",
                audioData = audioBytes
            )
            viewModelScope.launch {
                AstraLog.d("ConversationViewModel", "SEND $modeTag audioBytes=${audioBytes.size} destination=$recipientId priority=$priority")
                sendMessageUseCase.sendVoiceMessage(chatId, recipientId, payload, priority)
            }
        }
    }

    /**
     * Trigger Emergency Broadcast SOS Voice/Alert
     */
    fun sendEmergencySos(alertText: String = "EMERGENCY SOS BROADCAST") {
        AstraLog.d("ConversationViewModel", "SOS Broadcasting emergency alert to mesh")
        viewModelScope.launch {
            val payload = VoicePayload(
                mode = VoiceMode.EMERGENCY,
                sequence = pttSequence++,
                isFinal = true,
                transcript = alertText,
                audioData = ByteArray(0)
            )
            sendMessageUseCase.sendVoiceMessage(chatId, recipientId, payload, MessagePriority.EMERGENCY)
        }
    }

    /**
     * Toggle Continuous Hands-Free Voice Mode (Mic -> VAD -> STT -> Transmission)
     */
    fun toggleContinuousMode() {
        val nextState = !_isContinuousModeActive.value
        _isContinuousModeActive.value = nextState
        AstraLog.d("ConversationViewModel", "CONTINUOUS mode active=$nextState")

        if (nextState) {
            _continuousState.value = ContinuousState.LISTENING
            voiceEngineManager?.startStt { transcript ->
                if (transcript.isNotBlank()) {
                    _liveTranscript.value = transcript
                    _continuousState.value = ContinuousState.TRANSCRIBING
                    AstraLog.d("ConversationViewModel", "CONTINUOUS Recognized transcript='$transcript'")

                    val payload = VoicePayload(
                        mode = VoiceMode.CONTINUOUS,
                        sequence = pttSequence++,
                        isFinal = true,
                        transcript = transcript,
                        audioData = ByteArray(0)
                    )
                    viewModelScope.launch {
                        _continuousState.value = ContinuousState.SPEAKING
                        sendMessageUseCase.sendVoiceMessage(chatId, recipientId, payload)
                        delay(1500)
                        if (_isContinuousModeActive.value) {
                            _continuousState.value = ContinuousState.LISTENING
                        }
                    }
                }
            }
        } else {
            _continuousState.value = ContinuousState.IDLE
            _liveTranscript.value = ""
            voiceEngineManager?.stopStt()
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        voiceEngineManager?.shutdown()
    }
}

