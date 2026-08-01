package com.example.core

/**
 * Result type — mirrors the desktop `src/core/result.ts`.
 * Every fallible operation in the data layer returns `Result<T>` instead of throwing.
 */
sealed class Result<out T> {
    data class Ok<out T>(val value: T) : Result<T>()
    data class Err(val error: AppError) : Result<Nothing>()

    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Ok -> Ok(transform(value))
        is Err -> this
    }
    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Ok -> transform(value)
        is Err -> this
    }
    inline fun onSuccess(block: (T) -> Unit): Result<T> { if (this is Ok) block(value); return this }
    inline fun onFailure(block: (AppError) -> Unit): Result<T> { if (this is Err) block(error); return this }
    fun getOrNull(): T? = (this as? Ok)?.value
    fun errorOrNull(): AppError? = (this as? Err)?.error
    val isOk: Boolean get() = this is Ok
    val isErr: Boolean get() = this is Err
}

data class AppError(
    val code: String,
    val message: String,
    val userMessage: String,
    val cause: Any? = null,
)

object Errors {
    const val CODE_NETWORK      = "ERR_NETWORK"
    const val CODE_TIMEOUT      = "ERR_TIMEOUT"
    const val CODE_NOT_FOUND    = "ERR_NOT_FOUND"
    const val CODE_VALIDATION   = "ERR_VALIDATION"
    const val CODE_UNAUTHORIZED = "ERR_UNAUTHORIZED"
    const val CODE_FORBIDDEN    = "ERR_FORBIDDEN"
    const val CODE_CONFLICT     = "ERR_CONFLICT"
    const val CODE_SERVER       = "ERR_SERVER"
    const val CODE_OFFLINE      = "ERR_OFFLINE"
    const val CODE_UNKNOWN      = "ERR_UNKNOWN"

    fun network(message: String, userMessage: String = "Erreur réseau. Vérifiez votre connexion.") = AppError(CODE_NETWORK, message, userMessage)
    fun timeout(message: String, userMessage: String = "La requête a expiré. Réessayez.") = AppError(CODE_TIMEOUT, message, userMessage)
    fun notFound(message: String, userMessage: String = "Ressource introuvable.") = AppError(CODE_NOT_FOUND, message, userMessage)
    fun validation(message: String, userMessage: String = "Données invalides.") = AppError(CODE_VALIDATION, message, userMessage)
    fun unauthorized(message: String, userMessage: String = "Authentification requise.") = AppError(CODE_UNAUTHORIZED, message, userMessage)
    fun forbidden(message: String, userMessage: String = "Action non autorisée.") = AppError(CODE_FORBIDDEN, message, userMessage)
    fun conflict(message: String, userMessage: String = "Conflit de données.") = AppError(CODE_CONFLICT, message, userMessage)
    fun server(message: String, userMessage: String = "Erreur serveur. Réessayez plus tard.") = AppError(CODE_SERVER, message, userMessage)
    fun offline(message: String, userMessage: String = "Hors ligne. Synchronisation en attente.") = AppError(CODE_OFFLINE, message, userMessage)
    fun unknown(message: String, userMessage: String = "Une erreur inattendue s'est produite.") = AppError(CODE_UNKNOWN, message, userMessage)

    fun fromException(e: Throwable): AppError {
        val message = e.message ?: e::class.simpleName ?: "unknown"
        return when (e) {
            is java.net.UnknownHostException, is java.net.ConnectException -> network(message)
            is java.net.SocketTimeoutException -> timeout(message)
            else -> unknown(message)
        }
    }

    fun fromSupabase(errorCode: String?, message: String): AppError = when {
        errorCode == "23505" || message.contains("duplicate key", ignoreCase = true) -> conflict("Duplicate key: $message")
        errorCode == "23503" || message.contains("foreign key", ignoreCase = true) -> validation("Foreign key violation: $message")
        errorCode == "42501" || message.contains("permission denied", ignoreCase = true) || message.contains("RLS", ignoreCase = true) -> forbidden("RLS denied: $message")
        errorCode == "PGRST116" -> notFound("Not found: $message")
        errorCode == "401" || message.contains("JWT", ignoreCase = true) || message.contains("auth", ignoreCase = true) -> unauthorized("Auth error: $message")
        message.contains("network", ignoreCase = true) || message.contains("fetch", ignoreCase = true) -> network("Network error: $message")
        message.contains("timeout", ignoreCase = true) -> timeout("Timeout: $message")
        else -> server("Server error ($errorCode): $message")
    }
}
