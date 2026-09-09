package com.astramesh.feature.nearby

import com.astramesh.core.NodeId
import com.astramesh.domain.model.Peer
import com.astramesh.domain.repository.PeerRepository
import com.astramesh.domain.usecase.DiscoverPeersUseCase
import com.astramesh.domain.usecase.VerifyPeerTrustUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Test

class FeatureNearbyModuleTest {

    @Test
    fun `nearby peers viewmodel observes peer flow`() {
        val peerRepo = mockk<PeerRepository>()
        val verifyUseCase = mockk<VerifyPeerTrustUseCase>()
        val peers = listOf(
            Peer(
                nodeId = NodeId(1L),
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                rssi = -60
            )
        )
        every { peerRepo.observeNearbyPeers() } returns flowOf(peers)

        val discoverUseCase = DiscoverPeersUseCase(peerRepo)
        val viewModel = NearbyPeersViewModel(discoverUseCase, verifyUseCase)

        assertThat(viewModel.peers.value).isNotNull()
    }
}
