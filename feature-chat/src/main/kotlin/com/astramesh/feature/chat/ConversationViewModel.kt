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
import java.util.concurrent.atomic.AtomicBoolean

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
    private val peerRepository: com.astramesh.domain.repository.PeerRepository? = null,
    private val meshRepository: com.astramesh.domain.repository.MeshRepository? = null,
    private val voiceEngineManager: VoiceEngineManager? = null
) : ViewModel() {

    val recipientDisplayName = MutableStateFlow<String?>(null)



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
    private val hasSentCurrentPttUtterance = AtomicBoolean(false)

    private val _speechLanguage = MutableStateFlow(voiceEngineManager?.preferredLanguage ?: com.astramesh.core.Language.HINDI)
    val speechLanguage: StateFlow<com.astramesh.core.Language> = _speechLanguage.asStateFlow()

    init {
        viewModelScope.launch {
            peerRepository?.observeNearbyPeers()?.collect { peers ->
                val peer = peers.find { it.nodeId == recipientId }
                val name = peer?.displayName
                if (name != null && !name.startsWith("Node-")) {
                    recipientDisplayName.value = name
                } else if (name != null && recipientDisplayName.value == null) {
                    recipientDisplayName.value = name
                }
            }
        }
        viewModelScope.launch {
            var lastHandledTimestamp = System.currentTimeMillis()
            messages.collect { messageList ->
                if (messageList.isEmpty()) return@collect
                val latest = messageList.last()
                if (latest.timestamp > lastHandledTimestamp && latest.senderId == recipientId) {
                    lastHandledTimestamp = latest.timestamp
                    if (latest.content.contains("[CALL_ENDED]") || latest.content.contains("[CALL_DECLINED]")) {
                        if (_isContinuousModeActive.value) {
                            toggleContinuousMode()
                        }
                    } else if (_isContinuousModeActive.value && (latest.content.contains("[Voice Note") || !latest.content.startsWith("["))) {
                        playVoiceMessage(latest.content)
                    }
                }
            }
        }
    }

    fun setSpeechLanguage(language: com.astramesh.core.Language) {
        _speechLanguage.value = language
        voiceEngineManager?.preferredLanguage = language
        meshRepository?.setPreferredLanguage(language)
        AstraLog.d("ConversationViewModel", "SPEECH_LANG set to ${language.name}")
    }

    fun onInputTextChanged(newText: String) {
        _inputText.value = newText
    }

    fun setVoiceMode(mode: VoiceMode) {
        _currentVoiceMode.value = mode
        if (mode == VoiceMode.CONTINUOUS) {
            if (!_isContinuousModeActive.value) {
                toggleContinuousMode()
            }
        } else {
            if (_isContinuousModeActive.value) {
                toggleContinuousMode()
            }
        }
        AstraLog.d("ConversationViewModel", "VOICE_MODE Selected mode=${mode.name}")
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty()) return

        _inputText.value = ""
        viewModelScope.launch {
            sendMessageUseCase(
                chatId = chatId,
                recipientId = recipientId,
                content = text,
                priority = MessagePriority.DIRECT_MESSAGE
            )
        }
    }

    /**
     * Push-To-Talk / Walkie-Talkie: Hold to Record.
     * Decouples partial UI caption updates from final sentence transmission.
     */
    fun startPtt(mode: VoiceMode = VoiceMode.PUSH_TO_TALK) {
        if (_isRecordingPtt.value) return
        _isRecordingPtt.value = true
        _currentVoiceMode.value = mode
        _recordingDurationSec.value = 0
        _liveRms.value = 0
        _liveTranscript.value = ""
        hasSentCurrentPttUtterance.set(false)
        synchronized(recordedAudioBuffer) {
            recordedAudioBuffer.reset()
        }

        val modeTag = when (mode) {
            VoiceMode.EMERGENCY -> "SOS"
            else -> "PTT"
        }
        AstraLog.d("ConversationViewModel", "$modeTag START recording initiated lang=${_speechLanguage.value.name}")

        // Start live duration counter
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive && _isRecordingPtt.value) {
                delay(1000)
                _recordingDurationSec.value += 1
            }
        }

        // Start Live STT with exclusive microphone access
        voiceEngineManager?.startStt(
            language = _speechLanguage.value,
            onRmsChanged = { rms -> _liveRms.value = rms },
            onPartialResult = { partialTranscript ->
                // Strictly UI captioning preview — never transmitted across mesh
                if (partialTranscript.isNotBlank()) {
                    _liveTranscript.value = partialTranscript
                }
            },
            onFinalResult = { finalSentence ->
                // Event-driven immediate sentence transmission on VAD SPEECH_END
                if (finalSentence.isNotBlank() && _isRecordingPtt.value) {
                    if (hasSentCurrentPttUtterance.compareAndSet(false, true)) {
                        _liveTranscript.value = finalSentence
                        AstraLog.d("ConversationViewModel", "$modeTag sentence finalized on VAD SPEECH_END: '$finalSentence'")
                        transmitVoiceSentence(finalSentence, mode)
                    }
                }
            }
        )
    }

    /**
     * Push-To-Talk / Walkie-Talkie: Release to Send.
     */
    fun stopPtt() {
        if (!_isRecordingPtt.value) return
        _isRecordingPtt.value = false
        _liveRms.value = 0
        val duration = _recordingDurationSec.value
        timerJob?.cancel()
        timerJob = null

        val mode = _currentVoiceMode.value
        val modeTag = when (mode) {
            VoiceMode.EMERGENCY -> "SOS"
            else -> "PTT"
        }
        AstraLog.d("ConversationViewModel", "$modeTag STOP recording ended duration=${duration}s")

        viewModelScope.launch {
            val recognizedText = voiceEngineManager?.stopSttAndAwaitResult(timeoutMs = 1200L) ?: ""

            // If not already transmitted on VAD SPEECH_END, transmit now on button release
            if (hasSentCurrentPttUtterance.compareAndSet(false, true)) {
                val transcript = _liveTranscript.value.trim()
                val rawText = if (recognizedText.isNotBlank()) {
                    recognizedText
                } else if (transcript.isNotBlank()) {
                    transcript
                } else if (duration > 0) {
                    // Localized fallback if user spoke but STT engine did not output text
                    if (mode == VoiceMode.EMERGENCY) _speechLanguage.value.getDefaultEmergencyText() else _speechLanguage.value.getDefaultVoiceNoteText()
                } else {
                    ""
                }

                if (rawText.isNotBlank()) {
                    transmitVoiceSentence(rawText, mode)
                }
            } else {
                AstraLog.d("ConversationViewModel", "$modeTag Utterance already transmitted immediately on VAD SPEECH_END; skipping duplicate release transmission")
            }
        }
    }

    /**
     * Helper to perform language adjustments and transmit a completed voice sentence.
     */
    private fun transmitVoiceSentence(rawText: String, mode: VoiceMode) {
        var textToSend = rawText.trim()
        if (textToSend.isBlank()) return

        // If user selected English speech, translate Devanagari phonetics to English text
        if (_speechLanguage.value == com.astramesh.core.Language.ENGLISH && textToSend.any { it in 'ऀ'..'ॿ' }) {
            val translatedEng = voiceEngineManager?.translateText(textToSend, com.astramesh.core.Language.HINDI, com.astramesh.core.Language.ENGLISH) ?: ""
            if (translatedEng.isNotBlank()) {
                textToSend = translatedEng
            }
        }

        val isEmergency = (mode == VoiceMode.EMERGENCY)
        val modeTag = when (mode) {
            VoiceMode.EMERGENCY -> "SOS"
            VoiceMode.CONTINUOUS -> "CONTINUOUS"
            VoiceMode.PUSH_TO_TALK -> "PTT"
        }

        AstraLog.d("ConversationViewModel", "SEND $modeTag sentence text='$textToSend' lang=${_speechLanguage.value.name} isEmergency=$isEmergency")
        viewModelScope.launch {
            sendMessageUseCase.sendIthantraVoiceMessage(
                chatId = chatId,
                recipientId = recipientId,
                text = textToSend,
                language = _speechLanguage.value,
                isEmergency = isEmergency
            )
        }
    }

    /**
     * Trigger Emergency Broadcast SOS Voice/Alert
     */
    fun sendEmergencySos(alertText: String? = null) {
        val finalAlert = alertText ?: _speechLanguage.value.getDefaultEmergencyText()
        AstraLog.d("ConversationViewModel", "SOS Broadcasting emergency alert to mesh: text='$finalAlert'")
        viewModelScope.launch {
            sendMessageUseCase.sendIthantraVoiceMessage(
                chatId = chatId,
                recipientId = recipientId,
                text = finalAlert,
                language = _speechLanguage.value,
                isEmergency = true
            )
        }
    }

    /**
     * Toggle Continuous Hands-Free Phone Mode (Mic -> VAD -> STT -> Immediate Transmission per Sentence)
     */
    fun toggleContinuousMode() {
        val nextState = !_isContinuousModeActive.value
        _isContinuousModeActive.value = nextState
        AstraLog.d("ConversationViewModel", "CONTINUOUS mode active=$nextState")

        if (nextState) {
            _currentVoiceMode.value = VoiceMode.CONTINUOUS
            _continuousState.value = ContinuousState.LISTENING
            _liveTranscript.value = ""
            com.astramesh.domain.model.CallSessionManager.acceptCall(recipientId)

            viewModelScope.launch {
                sendMessageUseCase(
                    chatId = chatId,
                    recipientId = recipientId,
                    content = "[CALL_INVITE]: Phone call initiated",
                    priority = MessagePriority.DIRECT_MESSAGE
                )
            }

            voiceEngineManager?.startStt(
                language = _speechLanguage.value,
                onRmsChanged = { rms -> _liveRms.value = rms },
                onPartialResult = { partial ->
                    // Live UI preview only
                    if (partial.isNotBlank()) {
                        _liveTranscript.value = partial
                        _continuousState.value = ContinuousState.TRANSCRIBING
                    }
                },
                onFinalResult = { finalSentence ->
                    // Single clean sentence sent immediately upon pause detection (VAD SPEECH_END)
                    if (finalSentence.isNotBlank() && _isContinuousModeActive.value) {
                        _liveTranscript.value = finalSentence
                        _continuousState.value = ContinuousState.SPEAKING
                        AstraLog.d("ConversationViewModel", "CONTINUOUS sentence recognized on SPEECH_END: '$finalSentence'")

                        viewModelScope.launch {
                            transmitVoiceSentence(finalSentence, VoiceMode.CONTINUOUS)
                            delay(1200)
                            if (_isContinuousModeActive.value) {
                                _continuousState.value = ContinuousState.LISTENING
                                _liveTranscript.value = ""
                            }
                        }
                    }
                }
            )
        } else {
            _currentVoiceMode.value = VoiceMode.PUSH_TO_TALK
            _continuousState.value = ContinuousState.IDLE
            _liveTranscript.value = ""
            com.astramesh.domain.model.CallSessionManager.endCall()
            viewModelScope.launch {
                sendMessageUseCase(
                    chatId = chatId,
                    recipientId = recipientId,
                    content = "[CALL_ENDED]: Phone call ended",
                    priority = MessagePriority.DIRECT_MESSAGE
                )
            }
            voiceEngineManager?.stopStt()
        }
    }

    /**
     * Synthesizes and plays a voice message transcript aloud
     */
    fun playVoiceMessage(text: String, language: com.astramesh.core.Language = _speechLanguage.value) {
        val cleanText = text.replace(Regex("^\\[.*?\\]:?\\s*"), "").trim()
        viewModelScope.launch {
            voiceEngineManager?.speakText(cleanText, language)
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        voiceEngineManager?.stopStt()
    }
}
