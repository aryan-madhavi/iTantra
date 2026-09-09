package com.astramesh.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TimeProviderTest {

    @Test
    fun `virtual time provider advances deterministically`() {
        val provider = VirtualTimeProvider(initialMillis = 1000L)

        assertThat(provider.currentTimeMillis()).isEqualTo(1000L)
        assertThat(provider.nanoTime()).isEqualTo(1000_000_000L)

        provider.advanceTimeMillis(500L)
        assertThat(provider.currentTimeMillis()).isEqualTo(1500L)
        assertThat(provider.nanoTime()).isEqualTo(1500_000_000L)

        provider.setTimeMillis(5000L)
        assertThat(provider.currentTimeMillis()).isEqualTo(5000L)
    }

    @Test
    fun `system time provider returns positive timestamps`() {
        val provider = SystemTimeProvider()
        assertThat(provider.currentTimeMillis()).isGreaterThan(1_700_000_000_000L)
        assertThat(provider.nanoTime()).isGreaterThan(0L)
    }
}
