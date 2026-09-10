package com.astramesh.di

import android.content.Context
import com.astramesh.ble.BleAdvertiserManager
import com.astramesh.ble.BleConnectionPool
import com.astramesh.ble.BleScannerManager
import com.astramesh.ble.GattClientManager
import com.astramesh.ble.GattServerManager
import com.astramesh.core.NodeId
import com.astramesh.crypto.AstraKeyPair
import com.astramesh.domain.repository.IdentityRepository
import com.astramesh.mesh.MeshEngine
import com.astramesh.routing.RoutingTable
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MeshModule {

    @Provides
    @Singleton
    fun provideBleConnectionPool(): BleConnectionPool = BleConnectionPool()

    @Provides
    @Singleton
    fun provideBleScannerManager(): BleScannerManager = BleScannerManager()

    @Provides
    @Singleton
    fun provideBleAdvertiserManager(): BleAdvertiserManager = BleAdvertiserManager()

    @Provides
    @Singleton
    fun provideGattClientManager(
        @ApplicationContext context: Context,
        connectionPool: BleConnectionPool
    ): GattClientManager = GattClientManager(context, connectionPool)

    @Provides
    @Singleton
    fun provideGattServerManager(
        @ApplicationContext context: Context,
        identityRepository: IdentityRepository
    ): GattServerManager {
        val nodeId = runBlocking { identityRepository.getRotatingNodeId() }
        return GattServerManager(context, nodeId)
    }

    @Provides
    @Singleton
    fun provideRoutingTable(identityRepository: IdentityRepository): RoutingTable {
        val nodeId = runBlocking { identityRepository.getRotatingNodeId() }
        return RoutingTable(nodeId)
    }

    @Provides
    @Singleton
    fun provideMeshEngine(
        identityRepository: IdentityRepository,
        connectionPool: BleConnectionPool,
        scannerManager: BleScannerManager,
        advertiserManager: BleAdvertiserManager,
        gattClientManager: GattClientManager,
        gattServerManager: GattServerManager,
        peerRepository: com.astramesh.domain.repository.PeerRepository,
        messageRepository: com.astramesh.domain.repository.MessageRepository,
        chatRepository: com.astramesh.domain.repository.ChatRepository,
        routingTable: RoutingTable,
        voiceEngineManager: com.astramesh.services.VoiceEngineManager
    ): MeshEngine {
        val nodeId = runBlocking { identityRepository.getRotatingNodeId() }
        return MeshEngine(
            localNodeId = nodeId,
            connectionPool = connectionPool,
            scannerManager = scannerManager,
            advertiserManager = advertiserManager,
            gattClientManager = gattClientManager,
            gattServerManager = gattServerManager,
            routingTable = routingTable,
            peerRepository = peerRepository,
            messageRepository = messageRepository,
            chatRepository = chatRepository,
            speechSynthesizer = voiceEngineManager
        )
    }

    @Provides
    @Singleton
    fun provideVoiceEngineManager(
        @ApplicationContext context: Context
    ): com.astramesh.services.VoiceEngineManager {
        return com.astramesh.services.VoiceEngineManager(context)
    }
}
