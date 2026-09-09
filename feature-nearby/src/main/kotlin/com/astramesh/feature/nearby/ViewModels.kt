package com.astramesh.feature.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astramesh.core.NodeId
import com.astramesh.domain.model.Peer
import com.astramesh.domain.model.PeerTrustLevel
import com.astramesh.domain.model.Route
import com.astramesh.domain.repository.PeerRepository
import com.astramesh.domain.repository.RoutingRepository
import com.astramesh.domain.usecase.DiscoverPeersUseCase
import com.astramesh.domain.usecase.VerifyPeerTrustUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NearbyPeersViewModel(
    discoverPeersUseCase: DiscoverPeersUseCase,
    private val verifyPeerTrustUseCase: VerifyPeerTrustUseCase
) : ViewModel() {

    val peers: StateFlow<List<Peer>> = discoverPeersUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateTrust(nodeId: NodeId, newTrustLevel: PeerTrustLevel) {
        viewModelScope.launch {
            verifyPeerTrustUseCase(nodeId, newTrustLevel)
        }
    }
}

class MeshTopologyViewModel(
    routingRepository: RoutingRepository,
    peerRepository: PeerRepository
) : ViewModel() {

    val routes: StateFlow<List<Route>> = routingRepository.observeRoutes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val peers: StateFlow<List<Peer>> = peerRepository.observeNearbyPeers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
