package com.astramesh.ui

import com.astramesh.ui.theme.AstraBackground
import com.astramesh.ui.theme.AstraCyan
import com.astramesh.ui.theme.AstraEmerald
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UiModuleTest {

    @Test
    fun `theme colors are properly configured`() {
        assertThat(AstraBackground.value).isNotEqualTo(0UL)
        assertThat(AstraCyan.value).isNotEqualTo(0UL)
        assertThat(AstraEmerald.value).isNotEqualTo(0UL)
    }
}
