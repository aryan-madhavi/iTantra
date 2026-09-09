package com.astramesh.feature.chat

import com.astramesh.core.ChatId
import com.astramesh.core.NodeId
import com.astramesh.domain.model.Chat
import com.astramesh.domain.model.ChatType
import com.astramesh.domain.repository.ChatRepository
import com.astramesh.domain.repository.MessageRepository
import com.astramesh.domain.usecase.SendMessageUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FeatureChatModuleTest {

    private val chatRepo = mockk<ChatRepository>()
    private val messageRepo = mockk<MessageRepository>()
    private val sendMessageUseCase = mockk<SendMessageUseCase>()

    @Test
    fun `chat list view model observes chat threads`() = runTest {
        val chats = listOf(
            Chat(
                id = ChatId("c1"),
                title = "Alice",
                type = ChatType.DIRECT,
                participantIds = listOf(NodeId(1L), NodeId(2L))
            )
        )
        every { chatRepo.observeChats() } returns flowOf(chats)

        val viewModel = ChatListViewModel(chatRepo)
        assertThat(viewModel.chats.value).isNotNull()
    }
}
