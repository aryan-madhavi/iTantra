package com.astramesh.data.repository

import com.astramesh.common.AstraResult
import com.astramesh.core.NodeId
import com.astramesh.crypto.AstraKeyPair
import com.astramesh.crypto.EphemeralIdDeriver
import com.astramesh.domain.repository.IdentityRepository

class IdentityRepositoryImpl(
    private var localKeyPair: AstraKeyPair = AstraKeyPair.generate(),
    private var displayName: String = "AstraNode"
) : IdentityRepository {

    override suspend fun getLocalIdentity(): AstraKeyPair = localKeyPair

    override suspend fun getDisplayName(): String = displayName

    override suspend fun setDisplayName(name: String): AstraResult<Unit> = AstraResult.of {
        displayName = name
    }

    override suspend fun getRotatingNodeId(): NodeId {
        val epoch = EphemeralIdDeriver.getCurrentEpoch()
        val rawId = EphemeralIdDeriver.deriveEphemeralId(localKeyPair.privateKey, epoch)
        return NodeId(rawId)
    }

    override suspend fun rotateIdentityEpoch(): NodeId {
        val epoch = EphemeralIdDeriver.getCurrentEpoch() + 1
        val rawId = EphemeralIdDeriver.deriveEphemeralId(localKeyPair.privateKey, epoch)
        return NodeId(rawId)
    }

    override suspend fun wipeAllCryptographicKeys(): AstraResult<Unit> = AstraResult.of {
        localKeyPair.privateKey.destroy()
        localKeyPair = AstraKeyPair.generate()
    }
}
