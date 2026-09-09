package com.astramesh.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astramesh.core.ChatId
import com.astramesh.core.NodeId
import com.astramesh.domain.model.Message
import com.astramesh.domain.repository.MessageRepository
import com.astramesh.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConversationViewModel(
    val chatId: ChatId,
    val recipientId: NodeId,
    private val messageRepository: MessageRepository,
    private val sendMessageUseCase: SendMessageUseCase
) : ViewModel() {

    val messages: StateFlow<List<Message>> = messageRepository.observeMessages(chatId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    fun onInputTextChanged(newText: String) {
        _inputText.value = newText
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty()) return

        _inputText.value = ""
        viewModelScope.launch {
            sendMessageUseCase(chatId, recipientId, text)
        }
    }
}
