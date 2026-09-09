package com.astramesh.data.repository

import com.astramesh.common.AstraResult
import com.astramesh.core.NodeId
import com.astramesh.domain.model.DirectConnectionState
import com.astramesh.domain.model.Peer
import com.astramesh.domain.model.PeerTrustLevel
import com.astramesh.domain.repository.PeerRepository
import com.astramesh.storage.dao.PeerDao
import com.astramesh.storage.entity.PeerEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PeerRepositoryImpl(
    private val peerDao: PeerDao
) : PeerRepository {

    override fun observeNearbyPeers(): Flow<List<Peer>> {
        return peerDao.observeAllPeers().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getPeerByNodeId(nodeId: NodeId): Peer? {
        return peerDao.getPeerByNodeId(nodeId.value)?.toDomain()
    }

    override suspend fun updatePeer(peer: Peer): AstraResult<Unit> = AstraResult.of {
        peerDao.insertOrUpdate(peer.toEntity())
    }

    override suspend fun updateTrustLevel(nodeId: NodeId, trustLevel: PeerTrustLevel): AstraResult<Unit> = AstraResult.of {
        peerDao.updateTrustLevel(nodeId.value, trustLevel.name)
    }

    private fun PeerEntity.toDomain(): Peer {
        return Peer(
            nodeId = NodeId(nodeId),
            deviceAddress = deviceAddress,
            displayName = displayName,
            rssi = rssi,
            linkQuality = linkQuality,
            hopDistance = hopDistance,
            directState = DirectConnectionState.valueOf(directState),
            trustLevel = PeerTrustLevel.valueOf(trustLevel),
            batteryLevel = batteryLevel,
            lastSeenTimestamp = lastSeenTimestamp
        )
    }

    private fun Peer.toEntity(): PeerEntity {
        return PeerEntity(
            nodeId = nodeId.value,
            deviceAddress = deviceAddress,
            displayName = displayName,
            rssi = rssi,
            linkQuality = linkQuality,
            hopDistance = hopDistance,
            directState = directState.name,
            trustLevel = trustLevel.name,
            batteryLevel = batteryLevel,
            lastSeenTimestamp = lastSeenTimestamp
        )
    }
}
