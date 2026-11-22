package com.example.data

import android.content.Context
import android.util.Log
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

enum class AuthFlowStage(val value: String) {
  SILENT_AUTO_SIGN_IN("silent_auto_sign_in"),
  INTERACTIVE_SIGN_IN("interactive_sign_in"),
  FIREBASE_CREDENTIAL_EXCHANGE("firebase_credential_exchange"),
  SIGN_OUT("sign_out"),
}

data class AuthErrorResult(
  val category: String,
  val errorCode: String?,
  val userMessage: String,
  val logPayloadJson: String,
  val isCancellation: Boolean = false,
)

/**
 * Helper class for handling Firebase Authentication and Credential Manager failures.
 *
 * Logs structured error diagnostics (exception type, error code, stage, root cause, and device/app
 * metadata) to Logcat under [TAG] and displays user-friendly feedback in a Material 3 Snackbar.
 */
class FirebaseAuthErrorHandler(
  private val logger: (priority: Int, tag: String, message: String, throwable: Throwable?) -> Unit =
    { priority, tag, message, throwable ->
      when (priority) {
        Log.DEBUG -> Log.d(tag, message, throwable)
        Log.INFO -> Log.i(tag, message, throwable)
        Log.WARN -> Log.w(tag, message, throwable)
        else -> Log.e(tag, message, throwable)
      }
    }
) {

  fun handleAuthError(
    context: Context,
    exception: Throwable,
    stage: AuthFlowStage = AuthFlowStage.INTERACTIVE_SIGN_IN,
  ): AuthErrorResult {
    val causeChain = generateSequence(exception) { it.cause }.toList()
    val rootCause = causeChain.lastOrNull() ?: exception

    val authException = causeChain.filterIsInstance<FirebaseAuthException>().firstOrNull()
    val networkException = causeChain.filterIsInstance<FirebaseNetworkException>().firstOrNull()
    val cancellationException =
      causeChain.filterIsInstance<GetCredentialCancellationException>().firstOrNull()
    val noCredentialException = causeChain.filterIsInstance<NoCredentialException>().firstOrNull()
    val providerConfigException =
      causeChain.filterIsInstance<GetCredentialProviderConfigurationException>().firstOrNull()
    val interruptedException =
      causeChain.filterIsInstance<GetCredentialInterruptedException>().firstOrNull()
    val credentialException = causeChain.filterIsInstance<GetCredentialException>().firstOrNull()
    val firebaseException = causeChain.filterIsInstance<FirebaseException>().firstOrNull()

    val currentUserUid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()

    val (category, errorCode, userMessage, isCancellation) =
      when {
        cancellationException != null ->
          Quad(
            "credential_cancelled",
            cancellationException.type,
            context.getString(R.string.auth_error_cancelled),
            true,
          )
        noCredentialException != null ->
          Quad(
            "no_credential",
            noCredentialException.type,
            context.getString(R.string.auth_error_no_credential),
            false,
          )
        providerConfigException != null ->
          Quad(
            "credential_provider_configuration",
            providerConfigException.type,
            context.getString(R.string.auth_error_provider_config),
            false,
          )
        interruptedException != null ->
          Quad(
            "credential_interrupted",
            interruptedException.type,
            context.getString(
              R.string.auth_error_generic,
              interruptedException.errorMessage?.toString()
                ?: interruptedException.localizedMessage
                ?: interruptedException.type,
            ),
            false,
          )
        authException != null -> {
          val mapped = mapFirebaseAuthErrorCode(context, authException)
          Quad("firebase_auth_error", authException.errorCode, mapped, false)
        }
        networkException != null ->
          Quad(
            "network_error",
            "NETWORK_ERROR",
            context.getString(R.string.auth_error_network),
            false,
          )
        credentialException != null ->
          Quad(
            "credential_error",
            credentialException.type,
            context.getString(
              R.string.auth_error_generic,
              credentialException.errorMessage?.toString()
                ?: credentialException.localizedMessage
                ?: credentialException.type,
            ),
            false,
          )
        firebaseException != null ->
          Quad(
            "firebase_error",
            firebaseException::class.java.simpleName,
            context.getString(
              R.string.auth_error_generic,
              firebaseException.localizedMessage ?: firebaseException.message ?: "Firebase error",
            ),
            false,
          )
        exception is CancellationException ->
          Quad(
            "coroutine_cancelled",
            "CANCELLED",
            context.getString(R.string.auth_error_cancelled),
            true,
          )
        else ->
          Quad(
            "unexpected_error",
            exception::class.java.simpleName,
            context.getString(
              R.string.auth_error_generic,
              rootCause.localizedMessage ?: rootCause.message ?: rootCause::class.java.simpleName,
            ),
            false,
          )
      }

    val payloadJson =
      JSONObject()
        .apply {
          put("stage", stage.value)
          put("category", category)
          put("errorCode", errorCode)
          put("exceptionClass", exception::class.java.name)
          put("message", exception.message)
          put("rootCauseClass", rootCause::class.java.name)
          put("rootCauseMessage", rootCause.message)
          put("packageName", context.packageName)
          put("currentUserUid", currentUserUid)
          if (credentialException != null) {
            put("credentialType", credentialException.type)
            put("credentialErrorMessage", credentialException.errorMessage?.toString())
          }
          if (authException != null) {
            put("firebaseAuthErrorCode", authException.errorCode)
          }
        }
        .toString()

    val isExpectedSilentEmpty =
      stage == AuthFlowStage.SILENT_AUTO_SIGN_IN &&
        (noCredentialException != null || isCancellation)

    val logPriority =
      when {
        isExpectedSilentEmpty -> Log.INFO
        isCancellation -> Log.WARN
        else -> Log.ERROR
      }
    logger(
      logPriority,
      TAG,
      "Auth failure [${stage.value}] ($category / $errorCode): $payloadJson",
      if (isExpectedSilentEmpty) null else exception,
    )

    return AuthErrorResult(
      category = category,
      errorCode = errorCode,
      userMessage = userMessage,
      logPayloadJson = payloadJson,
      isCancellation = isCancellation,
    )
  }

  fun handleConfigurationError(
    context: Context,
    stage: AuthFlowStage,
    errorCode: String,
    userMessage: String,
    detail: String,
  ): AuthErrorResult {
    val payloadJson =
      JSONObject()
        .apply {
          put("stage", stage.value)
          put("category", "configuration_error")
          put("errorCode", errorCode)
          put("detail", detail)
          put("packageName", context.packageName)
        }
        .toString()

    logger(
      Log.ERROR,
      TAG,
      "Auth configuration failure [${stage.value}] ($errorCode): $payloadJson",
      null,
    )

    return AuthErrorResult(
      category = "configuration_error",
      errorCode = errorCode,
      userMessage = userMessage,
      logPayloadJson = payloadJson,
      isCancellation = false,
    )
  }

  suspend fun handleAndShowSnackbar(
    context: Context,
    snackbarHostState: SnackbarHostState,
    exception: Throwable,
    stage: AuthFlowStage = AuthFlowStage.INTERACTIVE_SIGN_IN,
    actionLabel: String? = null,
    duration: SnackbarDuration = SnackbarDuration.Long,
  ): AuthErrorResult {
    val result = handleAuthError(context = context, exception = exception, stage = stage)
    snackbarHostState.showSnackbar(
      message = result.userMessage,
      actionLabel = actionLabel,
      withDismissAction = true,
      duration = duration,
    )
    return result
  }

  private fun mapFirebaseAuthErrorCode(
    context: Context,
    exception: FirebaseAuthException,
  ): String {
    return when (exception.errorCode) {
      "ERROR_INVALID_CREDENTIAL",
      "ERROR_INVALID_CUSTOM_TOKEN",
      "ERROR_CUSTOM_TOKEN_MISMATCH" -> context.getString(R.string.auth_error_invalid_credential)
      "ERROR_USER_DISABLED" -> context.getString(R.string.auth_error_user_disabled)
      "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL",
      "ERROR_CREDENTIAL_ALREADY_IN_USE" -> context.getString(R.string.auth_error_account_conflict)
      "ERROR_OPERATION_NOT_ALLOWED" -> context.getString(R.string.auth_error_operation_not_allowed)
      "ERROR_NETWORK_REQUEST_FAILED" -> context.getString(R.string.auth_error_network)
      else ->
        context.getString(
          R.string.auth_error_generic,
          exception.localizedMessage ?: exception.errorCode,
        )
    }
  }

  private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

  companion object {
    const val TAG = "FirebaseAuthError"
    val default = FirebaseAuthErrorHandler()
  }
}
