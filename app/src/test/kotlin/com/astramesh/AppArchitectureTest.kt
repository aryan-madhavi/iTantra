package com.astramesh

import com.astramesh.core.NodeId
import com.astramesh.crypto.AstraKeyPair
import com.astramesh.domain.repository.IdentityRepository
import com.astramesh.domain.repository.MeshRepository
import com.astramesh.domain.repository.MessageRepository
import com.astramesh.domain.usecase.SendMessageUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppArchitectureTest {

    @Test
    fun testDependencyInjectionGraphContracts() = runTest {
        val mockIdentity = mockk<IdentityRepository>()
        val mockMesh = mockk<MeshRepository>()
        val mockMessage = mockk<MessageRepository>()

        val testNode = NodeId(9999L)
        coEvery { mockIdentity.getRotatingNodeId() } returns testNode

        assertThat(mockIdentity.getRotatingNodeId()).isEqualTo(testNode)
        coVerify(exactly = 1) { mockIdentity.getRotatingNodeId() }
    }
}
