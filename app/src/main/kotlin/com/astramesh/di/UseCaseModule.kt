package com.astramesh.di

import com.astramesh.domain.repository.ChatRepository
import com.astramesh.domain.repository.IdentityRepository
import com.astramesh.domain.repository.MeshRepository
import com.astramesh.domain.repository.MessageRepository
import com.astramesh.domain.repository.PeerRepository
import com.astramesh.domain.usecase.DiscoverPeersUseCase
import com.astramesh.domain.usecase.EmergencyBroadcastUseCase
import com.astramesh.domain.usecase.SendMessageUseCase
import com.astramesh.domain.usecase.VerifyPeerTrustUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    @Singleton
    fun provideSendMessageUseCase(
        messageRepository: MessageRepository,
        chatRepository: ChatRepository,
        identityRepository: IdentityRepository,
        meshRepository: MeshRepository
    ): SendMessageUseCase = SendMessageUseCase(
        messageRepository = messageRepository,
        chatRepository = chatRepository,
        identityRepository = identityRepository,
        meshRepository = meshRepository
    )

    @Provides
    @Singleton
    fun provideEmergencyBroadcastUseCase(
        meshRepository: MeshRepository,
        messageRepository: MessageRepository,
        chatRepository: ChatRepository,
        identityRepository: IdentityRepository
    ): EmergencyBroadcastUseCase = EmergencyBroadcastUseCase(
        meshRepository = meshRepository,
        messageRepository = messageRepository,
        chatRepository = chatRepository,
        identityRepository = identityRepository
    )

    @Provides
    @Singleton
    fun provideDiscoverPeersUseCase(
        peerRepository: PeerRepository
    ): DiscoverPeersUseCase = DiscoverPeersUseCase(peerRepository)

    @Provides
    @Singleton
    fun provideVerifyPeerTrustUseCase(
        peerRepository: PeerRepository
    ): VerifyPeerTrustUseCase = VerifyPeerTrustUseCase(peerRepository)
}
