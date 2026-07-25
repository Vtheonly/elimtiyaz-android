package com.elimtiyaz.core.common

/**
 * Either Result — the universal return type of every suspend function and Flow
 * in the data layer. Avoids throwing exceptions across module boundaries.
 *
 * Use [Result.Success] for normal returns, [Result.Failure] for expected errors
 * (network down, 4xx, validation, permission). Reserve `throw` for programmer
 * mistakes only.
 */
sealed interface Result<out T> {
    data class Success<T>(val data: T) : Result<T>
    data class Failure(val error: AppError) : Result<Nothing>

    fun getOrNull(): T? = (this as? Success)?.data
    fun isSuccess(): Boolean = this is Success
    fun isFailure(): Boolean = this is Failure

    companion object {
        fun <T> success(data: T): Result<T> = Success(data)
        fun failure(error: AppError): Result<Nothing> = Failure(error)
        fun failure(message: String): Result<Nothing> = Failure(AppError.Message(message))

        inline fun <T> runCatching(block: () -> T): Result<T> = try {
            success(block())
        } catch (e: Throwable) {
            Failure(AppError.from(e))
        }
    }
}

inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> = when (this) {
    is Result.Success -> Result.success(transform(data))
    is Result.Failure -> this
}

inline fun <T> Result<T>.onSuccess(block: (T) -> Unit): Result<T> {
    if (this is Result.Success) block(data)
    return this
}

inline fun <T> Result<T>.onFailure(block: (AppError) -> Unit): Result<T> {
    if (this is Result.Failure) block(error)
    return this
}

inline fun <T> Result<T>.getOrDefault(default: T): T = when (this) {
    is Result.Success -> data
    is Result.Failure -> default
}
