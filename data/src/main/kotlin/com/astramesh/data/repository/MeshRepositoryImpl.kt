package com.astramesh.data.repository

import com.astramesh.common.AstraResult
import com.astramesh.core.NodeId
import com.astramesh.domain.model.MeshStatus
import com.astramesh.domain.model.MessagePriority
import com.astramesh.domain.repository.IdentityRepository
import com.astramesh.domain.repository.MeshRepository
import com.astramesh.mesh.MeshEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MeshRepositoryImpl(
    private val meshEngine: MeshEngine,
    private val identityRepository: IdentityRepository? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : MeshRepository {

    override val meshStatus: StateFlow<MeshStatus> get() = meshEngine.meshStatus
    override val preferredLanguage: StateFlow<com.astramesh.core.Language> get() = meshEngine.preferredLanguageFlow

    override suspend fun startMesh(): AstraResult<Unit> = meshEngine.start()

    override suspend fun stopMesh(): AstraResult<Unit> = AstraResult.of { meshEngine.stop() }

    override suspend fun sendPacket(
        recipientId: NodeId,
        payload: ByteArray,
        priority: MessagePriority
    ): AstraResult<Unit> {
        val result = meshEngine.sendPacket(recipientId, payload, priority)
        return result.map { }
    }

    override suspend fun broadcastEmergency(content: String): AstraResult<Unit> {
        val payload = content.toByteArray(Charsets.UTF_8)
        val result = meshEngine.sendPacket(NodeId.BROADCAST, payload, MessagePriority.EMERGENCY)
        return result.map { }
    }

    override fun setPreferredLanguage(language: com.astramesh.core.Language) {
        meshEngine.preferredLanguage = language
        identityRepository?.let { idRepo ->
            coroutineScope.launch {
                idRepo.setPreloadLanguage(language)
            }
        }
    }
}
