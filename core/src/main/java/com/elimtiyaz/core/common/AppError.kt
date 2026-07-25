package com.elimtiyaz.core.common

/**
 * The set of recoverable errors the app recognizes. UI maps these to user-friendly
 * messages via [AppError.userMessage]. The HTTP/network classification powers the
 * offline banner and retry buttons.
 */
sealed class AppError {
    data class Http(val status: Int, val message: String) : AppError()
    data class Network(val message: String) : AppError()
    data class Auth(val message: String) : AppError()
    data class Permission(val permission: String) : AppError()
    data class NotFound(val what: String) : AppError()
    data class Validation(val fields: Map<String, String>) : AppError()
    data class Message(val text: String) : AppError()
    data class Unknown(val throwable: Throwable) : AppError()

    val userMessage: String
        get() = when (this) {
            is Http -> if (status == 401) "Session expirée, veuillez vous reconnecter." else "Erreur serveur ($status). Réessayez."
            is Network -> "Aucune connexion réseau. Vérifiez votre Wi-Fi ou vos données."
            is Auth -> message
            is Permission -> "Autorisation requise: $permission"
            is NotFound -> "Introuvable: $what"
            is Validation -> fields.values.firstOrNull() ?: "Données invalides."
            is Message -> text
            is Unknown -> "Une erreur inattendue s'est produite."
        }

    companion object {
        fun from(throwable: Throwable): AppError = when (throwable) {
            is java.net.UnknownHostException,
            is java.net.SocketTimeoutException,
            is java.net.ConnectException -> Network(throwable.message ?: "network error")
            is kotlinx.coroutines.CancellationException -> throw throwable  // never swallow cancellation
            else -> Unknown(throwable)
        }
    }
}
