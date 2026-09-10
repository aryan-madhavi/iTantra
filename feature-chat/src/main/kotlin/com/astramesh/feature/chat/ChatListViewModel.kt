package com.astramesh.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astramesh.domain.model.Chat
import com.astramesh.domain.model.ChatType
import com.astramesh.domain.repository.ChatRepository
import com.astramesh.domain.repository.PeerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ChatListViewModel(
    private val chatRepository: ChatRepository,
    private val peerRepository: PeerRepository? = null
) : ViewModel() {

    val chats: StateFlow<List<Chat>> = if (peerRepository != null) {
        combine(
            chatRepository.observeChats(),
            peerRepository.observeNearbyPeers()
        ) { chatList, peers ->
            val peerMap = peers.associateBy { it.nodeId.value }
            chatList.map { chat ->
                if (chat.type == ChatType.DIRECT) {
                    val directPeerId = chat.participantIds.firstOrNull { it.value != 0L }?.value
                        ?: chat.id.value.removePrefix("direct_").toLongOrNull()
                    val matchedPeer = if (directPeerId != null) peerMap[directPeerId] else null
                    val customName = matchedPeer?.displayName?.takeIf { !it.startsWith("Node-") }
                    if (customName != null && customName != chat.title) {
                        chat.copy(title = customName)
                    } else {
                        chat
                    }
                } else {
                    chat
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    } else {
        chatRepository.observeChats()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }
}
