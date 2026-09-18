package com.astramesh.domain.usecase

import com.astramesh.common.AstraResult
import com.astramesh.core.ChatId
import com.astramesh.core.MessageId
import com.astramesh.core.NodeId
import com.astramesh.domain.model.Chat
import com.astramesh.domain.model.ChatType
import com.astramesh.domain.model.Message
import com.astramesh.domain.model.MessageContentType
import com.astramesh.domain.model.MessagePriority
import com.astramesh.domain.model.MessageStatus
import com.astramesh.domain.model.Peer
import com.astramesh.domain.model.PeerTrustLevel
import com.astramesh.domain.repository.ChatRepository
import com.astramesh.domain.repository.IdentityRepository
import com.astramesh.domain.repository.MessageRepository
import com.astramesh.domain.repository.MeshRepository
import com.astramesh.domain.repository.PeerRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class SendMessageUseCase(
    private val messageRepository: MessageRepository,
    private val chatRepository: ChatRepository,
    private val identityRepository: IdentityRepository,
    private val meshRepository: MeshRepository
) {
    suspend operator fun invoke(
        chatId: ChatId,
        recipientId: NodeId,
        content: String,
        priority: MessagePriority = MessagePriority.DIRECT_MESSAGE
    ): AstraResult<Message> {
        val senderId = identityRepository.getRotatingNodeId()
        val message = Message(
            id = MessageId(UUID.randomUUID().toString()),
            chatId = chatId,
            senderId = senderId,
            recipientId = recipientId,
            timestamp = System.currentTimeMillis(),
            content = content,
            contentType = MessageContentType.TEXT,
            status = MessageStatus.QUEUED,
            priority = priority
        )

        val insertResult = messageRepository.insertMessage(message)
        if (insertResult.isFailure) return AstraResult.Failure((insertResult as AstraResult.Failure).error)

        // Update chat thread
        val existingChat = chatRepository.getChatById(chatId) ?: Chat(
            id = chatId,
            title = recipientId.toHex(),
            type = if (recipientId.isBroadcast) ChatType.BROADCAST else ChatType.DIRECT,
            participantIds = listOf(senderId, recipientId)
        )
        chatRepository.insertOrUpdateChat(
            existingChat.copy(
                lastMessage = message,
                updatedAt = message.timestamp
            )
        )

        com.astramesh.common.AstraLog.d("SendMessageUseCase", "MSG_SEND id=${message.id} senderId=$senderId recipientId=$recipientId")
        // Dispatch through mesh
        val payload = content.toByteArray(Charsets.UTF_8)
        val sendResult = meshRepository.sendPacket(recipientId, payload, priority)
        if (sendResult.isSuccess) {
            messageRepository.updateMessageStatus(message.id, MessageStatus.SENT)
        }

        return AstraResult.Success(message)
    }

    suspend fun sendVoiceMessage(
        chatId: ChatId,
        recipientId: NodeId,
        voicePayload: com.astramesh.core.VoicePayload,
        priority: MessagePriority = MessagePriority.DIRECT_MESSAGE
    ): AstraResult<Message> {
        val senderId = identityRepository.getRotatingNodeId()
        val textSnippet = if (voicePayload.transcript.isNotBlank()) {
            "[Voice Note]: ${voicePayload.transcript}"
        } else {
            "[Voice Note ${voicePayload.mode.name}]"
        }

        val message = Message(
            id = MessageId(UUID.randomUUID().toString()),
            chatId = chatId,
            senderId = senderId,
            recipientId = recipientId,
            timestamp = System.currentTimeMillis(),
            content = textSnippet,
            contentType = MessageContentType.AUDIO_NOTE,
            status = MessageStatus.QUEUED,
            priority = priority
        )

        val insertResult = messageRepository.insertMessage(message)
        if (insertResult.isFailure) return AstraResult.Failure((insertResult as AstraResult.Failure).error)

        // Update chat thread
        val existingChat = chatRepository.getChatById(chatId) ?: Chat(
            id = chatId,
            title = recipientId.toHex(),
            type = if (recipientId.isBroadcast) ChatType.BROADCAST else ChatType.DIRECT,
            participantIds = listOf(senderId, recipientId)
        )
        chatRepository.insertOrUpdateChat(
            existingChat.copy(
                lastMessage = message,
                updatedAt = message.timestamp
            )
        )

        // Dispatch serialized binary voice payload through mesh
        val payloadBytes = voicePayload.serialize()
        val sendResult = meshRepository.sendPacket(recipientId, payloadBytes, priority)
        if (sendResult.isSuccess) {
            messageRepository.updateMessageStatus(message.id, MessageStatus.SENT)
        }

        return AstraResult.Success(message)
    }

    suspend fun sendIthantraVoiceMessage(
        chatId: ChatId,
        recipientId: NodeId,
        text: String,
        language: com.astramesh.core.Language = com.astramesh.core.Language.HINDI,
        isEmergency: Boolean = false,
        gpsLatitude: Float = 0.0f,
        gpsLongitude: Float = 0.0f
    ): AstraResult<Message> {
        val senderId = identityRepository.getRotatingNodeId()
        val detectedEmergency = isEmergency || com.astramesh.core.EmergencyClassifier.isEmergency(text, language)
        val msgType = if (detectedEmergency) com.astramesh.core.MessageType.ALERT else com.astramesh.core.MessageType.NORMAL
        val priority = if (detectedEmergency) MessagePriority.EMERGENCY else MessagePriority.DIRECT_MESSAGE

        val seqNo = System.currentTimeMillis() and 0xFFFFFFFFL
        val ithantraMessage = com.astramesh.core.IthantraMessage(
            senderId = senderId,
            messageType = msgType,
            language = language,
            sequenceNumber = seqNo,
            timestamp = System.currentTimeMillis(),
            text = text,
            hopTtl = 7,
            gpsLatitude = gpsLatitude,
            gpsLongitude = gpsLongitude
        )

        val snippet = "[Voice Note]: $text"
        val message = Message(
            id = MessageId(UUID.randomUUID().toString()),
            chatId = chatId,
            senderId = senderId,
            recipientId = recipientId,
            timestamp = ithantraMessage.timestamp,
            content = snippet,
            contentType = if (detectedEmergency) MessageContentType.SYSTEM_ALERT else MessageContentType.AUDIO_NOTE,
            status = MessageStatus.QUEUED,
            priority = priority
        )

        val insertResult = messageRepository.insertMessage(message)
        if (insertResult.isFailure) return AstraResult.Failure((insertResult as AstraResult.Failure).error)

        val existingChat = chatRepository.getChatById(chatId) ?: Chat(
            id = chatId,
            title = recipientId.toHex(),
            type = if (recipientId.isBroadcast) ChatType.BROADCAST else ChatType.DIRECT,
            participantIds = listOf(senderId, recipientId)
        )
        chatRepository.insertOrUpdateChat(
            existingChat.copy(
                lastMessage = message,
                updatedAt = message.timestamp
            )
        )

        val payloadBytes = ithantraMessage.toBinary()
        val sendResult = meshRepository.sendPacket(recipientId, payloadBytes, priority)
        if (sendResult.isSuccess) {
            messageRepository.updateMessageStatus(message.id, MessageStatus.SENT)
        }

        return AstraResult.Success(message)
    }

    suspend fun sendVoiceMessageByMode(
        mode: com.astramesh.core.CommunicationMode,
        directRecipientId: NodeId?,
        text: String,
        language: com.astramesh.core.Language = com.astramesh.core.Language.HINDI,
        gpsLatitude: Float = 0.0f,
        gpsLongitude: Float = 0.0f
    ): AstraResult<Message> {
        val (chatId, recipientId, isEmergency) = when (mode) {
            com.astramesh.core.CommunicationMode.BROADCAST -> Triple(
                ChatId("chat_broadcast"),
                NodeId.BROADCAST,
                false
            )
            com.astramesh.core.CommunicationMode.DIRECT -> {
                val target = directRecipientId ?: NodeId.BROADCAST
                Triple(
                    if (target.isBroadcast) ChatId("chat_broadcast") else ChatId("direct_${target.value}"),
                    target,
                    false
                )
            }
            com.astramesh.core.CommunicationMode.EMERGENCY -> Triple(
                ChatId("chat_emergency_broadcast"),
                NodeId.BROADCAST,
                true
            )
        }
        return sendIthantraVoiceMessage(
            chatId = chatId,
            recipientId = recipientId,
            text = text,
            language = language,
            isEmergency = isEmergency,
            gpsLatitude = gpsLatitude,
            gpsLongitude = gpsLongitude
        )
    }
}

class EmergencyBroadcastUseCase(
    private val meshRepository: MeshRepository,
    private val messageRepository: MessageRepository,
    private val chatRepository: ChatRepository,
    private val identityRepository: IdentityRepository
) {
    /**
     * Stage 1: Immediate emergency distress beacon.
     * Dispatches MessageType.SOS immediately with maximum TTL (15) and MessagePriority.EMERGENCY
     * without waiting for microphone, STT, or translation.
     */
    suspend fun sendImmediateSosBeacon(
        language: com.astramesh.core.Language = com.astramesh.core.Language.HINDI,
        gpsLatitude: Float = 0.0f,
        gpsLongitude: Float = 0.0f
    ): AstraResult<Message> {
        val senderId = identityRepository.getRotatingNodeId()
        val emergencyChatId = ChatId("chat_emergency_broadcast")
        val seqNo = System.currentTimeMillis() and 0xFFFFFFFFL
        val defaultAlertText = language.getDefaultEmergencyText()

        val ithantra = com.astramesh.core.IthantraMessage(
            senderId = senderId,
            messageType = com.astramesh.core.MessageType.SOS,
            language = language,
            sequenceNumber = seqNo,
            timestamp = System.currentTimeMillis(),
            text = defaultAlertText,
            hopTtl = 15,
            gpsLatitude = gpsLatitude,
            gpsLongitude = gpsLongitude
        )

        val message = Message(
            id = MessageId(UUID.randomUUID().toString()),
            chatId = emergencyChatId,
            senderId = senderId,
            recipientId = NodeId.BROADCAST,
            timestamp = System.currentTimeMillis(),
            content = "[SOS DISTRESS BEACON]: $defaultAlertText",
            contentType = MessageContentType.SYSTEM_ALERT,
            status = MessageStatus.SENT,
            priority = MessagePriority.EMERGENCY,
            ttl = 15
        )

        messageRepository.insertMessage(message)
        val chat = Chat(
            id = emergencyChatId,
            title = "EMERGENCY BROADCAST",
            type = ChatType.EMERGENCY,
            participantIds = listOf(senderId, NodeId.BROADCAST),
            lastMessage = message,
            updatedAt = message.timestamp
        )
        chatRepository.insertOrUpdateChat(chat)

        val sendResult = meshRepository.sendPacket(NodeId.BROADCAST, ithantra.toBinary(), MessagePriority.EMERGENCY)
        return if (sendResult.isSuccess) AstraResult.Success(message) else AstraResult.Failure("Failed to dispatch SOS beacon")
    }

    /**
     * Stage 2: Emergency voice briefing message.
     * Dispatches recorded/transcribed briefing with MessageType.ALERT, maximum TTL (15), and MessagePriority.EMERGENCY.
     */
    suspend fun sendVoiceBriefing(
        transcript: String,
        language: com.astramesh.core.Language = com.astramesh.core.Language.HINDI,
        gpsLatitude: Float = 0.0f,
        gpsLongitude: Float = 0.0f
    ): AstraResult<Message> {
        val senderId = identityRepository.getRotatingNodeId()
        val emergencyChatId = ChatId("chat_emergency_broadcast")
        val seqNo = System.currentTimeMillis() and 0xFFFFFFFFL
        val text = transcript.trim().ifBlank { language.getDefaultEmergencyText() }

        val ithantra = com.astramesh.core.IthantraMessage(
            senderId = senderId,
            messageType = com.astramesh.core.MessageType.ALERT,
            language = language,
            sequenceNumber = seqNo,
            timestamp = System.currentTimeMillis(),
            text = text,
            hopTtl = 15,
            gpsLatitude = gpsLatitude,
            gpsLongitude = gpsLongitude
        )

        val snippet = "[Emergency Briefing]: $text"
        val message = Message(
            id = MessageId(UUID.randomUUID().toString()),
            chatId = emergencyChatId,
            senderId = senderId,
            recipientId = NodeId.BROADCAST,
            timestamp = System.currentTimeMillis(),
            content = snippet,
            contentType = MessageContentType.SYSTEM_ALERT,
            status = MessageStatus.SENT,
            priority = MessagePriority.EMERGENCY,
            ttl = 15
        )

        messageRepository.insertMessage(message)
        val chat = Chat(
            id = emergencyChatId,
            title = "EMERGENCY BROADCAST",
            type = ChatType.EMERGENCY,
            participantIds = listOf(senderId, NodeId.BROADCAST),
            lastMessage = message,
            updatedAt = message.timestamp
        )
        chatRepository.insertOrUpdateChat(chat)

        val sendResult = meshRepository.sendPacket(NodeId.BROADCAST, ithantra.toBinary(), MessagePriority.EMERGENCY)
        return if (sendResult.isSuccess) AstraResult.Success(message) else AstraResult.Failure("Failed to dispatch voice briefing")
    }

    suspend operator fun invoke(
        alertMessage: String,
        language: com.astramesh.core.Language = com.astramesh.core.Language.HINDI
    ): AstraResult<Unit> {
        val res = sendImmediateSosBeacon(language)
        return if (res.isSuccess) AstraResult.Success(Unit) else AstraResult.Failure("Failed to dispatch SOS")
    }
}

class DiscoverPeersUseCase(
    private val peerRepository: PeerRepository
) {
    operator fun invoke(timeoutMs: Long = com.astramesh.core.AstraNetworkConfig.PEER_STALENESS_TIMEOUT_MS): Flow<List<Peer>> {
        val ticker = kotlinx.coroutines.flow.flow {
            while (true) {
                emit(System.currentTimeMillis())
                kotlinx.coroutines.delay(2500L)
            }
        }
        return kotlinx.coroutines.flow.combine(
            peerRepository.observeNearbyPeers(),
            ticker
        ) { peers, now ->
            peers.filter { (now - it.lastSeenTimestamp) <= timeoutMs }
        }
    }
}

class VerifyPeerTrustUseCase(
    private val peerRepository: PeerRepository
) {
    suspend operator fun invoke(nodeId: NodeId, newTrustLevel: PeerTrustLevel): AstraResult<Unit> {
        return peerRepository.updateTrustLevel(nodeId, newTrustLevel)
    }
}
