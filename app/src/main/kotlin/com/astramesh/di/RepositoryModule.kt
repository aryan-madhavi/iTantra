package com.astramesh.di

import com.astramesh.data.repository.ChatRepositoryImpl
import com.astramesh.data.repository.IdentityRepositoryImpl
import com.astramesh.data.repository.MeshRepositoryImpl
import com.astramesh.data.repository.MessageRepositoryImpl
import com.astramesh.data.repository.PeerRepositoryImpl
import com.astramesh.data.repository.RoutingRepositoryImpl
import com.astramesh.domain.repository.ChatRepository
import com.astramesh.domain.repository.IdentityRepository
import com.astramesh.domain.repository.MeshRepository
import com.astramesh.domain.repository.MessageRepository
import com.astramesh.domain.repository.PeerRepository
import com.astramesh.domain.repository.RoutingRepository
import com.astramesh.mesh.MeshEngine
import com.astramesh.storage.dao.ChatDao
import com.astramesh.storage.dao.MessageDao
import com.astramesh.storage.dao.PeerDao
import com.astramesh.storage.dao.RouteDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideIdentityRepository(): IdentityRepository {
        return IdentityRepositoryImpl()
    }

    @Provides
    @Singleton
    fun provideMessageRepository(messageDao: MessageDao): MessageRepository {
        return MessageRepositoryImpl(messageDao)
    }

    @Provides
    @Singleton
    fun provideChatRepository(chatDao: ChatDao): ChatRepository {
        return ChatRepositoryImpl(chatDao)
    }

    @Provides
    @Singleton
    fun providePeerRepository(peerDao: PeerDao): PeerRepository {
        return PeerRepositoryImpl(peerDao)
    }

    @Provides
    @Singleton
    fun provideRoutingRepository(routeDao: RouteDao): RoutingRepository {
        return RoutingRepositoryImpl(routeDao)
    }

    @Provides
    @Singleton
    fun provideMeshRepository(meshEngine: MeshEngine): MeshRepository {
        return MeshRepositoryImpl(meshEngine)
    }
}
