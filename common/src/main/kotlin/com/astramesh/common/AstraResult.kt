package com.astramesh.common

/**
 * Robust monadic Result type for AstraMesh domain and protocol operations.
 * Enforces explicit error handling without throwing unchecked exceptions across module boundaries.
 */
sealed class AstraResult<out T> {
    data class Success<out T>(val value: T) : AstraResult<T>()
    data class Failure(val error: AstraError) : AstraResult<Nothing>() {
        constructor(message: String, cause: Throwable? = null) : this(AstraError.General(message, cause))
    }

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw error.cause ?: IllegalStateException(error.message)
    }

    fun getOrDefault(defaultValue: @UnsafeVariance T): T = when (this) {
        is Success -> value
        is Failure -> defaultValue
    }

    inline fun <R> map(transform: (value: T) -> R): AstraResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    inline fun <R> flatMap(transform: (value: T) -> AstraResult<R>): AstraResult<R> = when (this) {
        is Success -> transform(value)
        is Failure -> this
    }

    inline fun onSuccess(action: (value: T) -> Unit): AstraResult<T> {
        if (this is Success) action(value)
        return this
    }

    inline fun onFailure(action: (error: AstraError) -> Unit): AstraResult<T> {
        if (this is Failure) action(error)
        return this
    }

    inline fun <R> fold(
        onSuccess: (value: T) -> R,
        onFailure: (error: AstraError) -> R
    ): R = when (this) {
        is Success -> onSuccess(value)
        is Failure -> onFailure(error)
    }

    companion object {
        inline fun <T> of(block: () -> T): AstraResult<T> = try {
            Success(block())
        } catch (t: Throwable) {
            Failure(AstraError.General(t.message ?: "Unknown error", t))
        }
    }
}

/**
 * Structured errors categorization for telemetry, routing, crypto, and BLE.
 */
sealed class AstraError(open val message: String, open val cause: Throwable? = null) {
    data class General(override val message: String, override val cause: Throwable? = null) : AstraError(message, cause)
    data class Crypto(override val message: String, override val cause: Throwable? = null) : AstraError(message, cause)
    data class Ble(override val message: String, val statusCode: Int? = null, override val cause: Throwable? = null) : AstraError(message, cause)
    data class Routing(override val message: String, override val cause: Throwable? = null) : AstraError(message, cause)
    data class Protocol(override val message: String, override val cause: Throwable? = null) : AstraError(message, cause)
    data class Storage(override val message: String, override val cause: Throwable? = null) : AstraError(message, cause)
    data class Network(override val message: String, override val cause: Throwable? = null) : AstraError(message, cause)
    data class Security(override val message: String, override val cause: Throwable? = null) : AstraError(message, cause)
}
