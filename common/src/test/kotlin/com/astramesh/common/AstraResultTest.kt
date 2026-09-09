package com.astramesh.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AstraResultTest {

    @Test
    fun `success result holds value and transforms correctly`() {
        val result = AstraResult.Success(42)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.isFailure).isFalse()
        assertThat(result.getOrNull()).isEqualTo(42)
        assertThat(result.getOrThrow()).isEqualTo(42)

        val mapped = result.map { it * 2 }
        assertThat(mapped.getOrNull()).isEqualTo(84)

        val flatMapped = result.flatMap { AstraResult.Success("Value: $it") }
        assertThat(flatMapped.getOrNull()).isEqualTo("Value: 42")
    }

    @Test
    fun `failure result propagates error and handles defaults`() {
        val failure: AstraResult<Int> = AstraResult.Failure("Network timeout")

        assertThat(failure.isSuccess).isFalse()
        assertThat(failure.isFailure).isTrue()
        assertThat(failure.getOrNull() as Any?).isNull()
        assertThat(failure.getOrDefault(999)).isEqualTo(999)

        var handledError: AstraError? = null
        failure.onFailure { handledError = it }
        assertThat(handledError as Any?).isNotNull()
        assertThat(handledError?.message).isEqualTo("Network timeout")
    }

    @Test
    fun `of catches exceptions and returns failure`() {
        val result = AstraResult.of {
            throw IllegalArgumentException("Invalid parameter")
        }

        assertThat(result.isFailure).isTrue()
        assertThat(result.fold(onSuccess = { "success" }, onFailure = { it.message }))
            .isEqualTo("Invalid parameter")
    }
}
