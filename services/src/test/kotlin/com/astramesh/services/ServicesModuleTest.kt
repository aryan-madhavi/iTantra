package com.astramesh.services

import android.content.Context
import android.os.PowerManager
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class ServicesModuleTest {

    @Test
    fun `power optimization coordinator acquires and releases partial wakelock`() {
        val mockContext = mockk<Context>(relaxed = true)
        val mockPowerManager = mockk<PowerManager>(relaxed = true)
        val mockWakeLock = mockk<PowerManager.WakeLock>(relaxed = true)

        every { mockContext.getSystemService(Context.POWER_SERVICE) } returns mockPowerManager
        every { mockPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, any()) } returns mockWakeLock
        every { mockWakeLock.isHeld } returns true

        val coordinator = PowerOptimizationCoordinator(mockContext)

        coordinator.acquireTemporaryWakeLock("TestTag", 5000L)
        verify { mockWakeLock.acquire(5000L) }

        coordinator.releaseWakeLock()
        verify { mockWakeLock.release() }
    }
}
