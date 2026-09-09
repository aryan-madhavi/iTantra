package com.astramesh.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astramesh.domain.model.Chat
import com.astramesh.domain.repository.ChatRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ChatListViewModel(
    private val chatRepository: ChatRepository
) : ViewModel() {

    val chats: StateFlow<List<Chat>> = chatRepository.observeChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
