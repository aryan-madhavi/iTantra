package com.astramesh.feature.settings

import com.astramesh.crypto.AstraKeyPair
import com.astramesh.domain.model.MeshStatus
import com.astramesh.domain.repository.IdentityRepository
import com.astramesh.domain.repository.MeshRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

class FeatureSettingsModuleTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `settings view model initializes and updates identity display name`() = runTest {
        val identityRepo = mockk<IdentityRepository>(relaxed = true)
        val meshRepo = mockk<MeshRepository>(relaxed = true)
        val keyPair = AstraKeyPair.generate()

        coEvery { identityRepo.getDisplayName() } returns "Alice"
        coEvery { identityRepo.getLocalIdentity() } returns keyPair
        coEvery { identityRepo.getRotatingNodeId() } returns com.astramesh.core.NodeId.fromPublicKey(keyPair.publicKey)
        io.mockk.every { meshRepo.meshStatus } returns MutableStateFlow(MeshStatus())

        val viewModel = SettingsViewModel(identityRepo, meshRepo)
        testScheduler.advanceUntilIdle()

        viewModel.updateDisplayName("Bob")
        testScheduler.advanceUntilIdle()

        assertThat(viewModel.displayName.value).isEqualTo("Bob")
    }
}

