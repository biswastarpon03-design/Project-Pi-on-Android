package com.example.ui

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.data.AuthFlowStage
import com.example.data.FirebaseAuthErrorHandler
import com.example.model.ChannelPlatform
import com.example.ui.components.PlatformGlyphIcon
import com.example.ui.components.platformAccentFor
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@Composable
fun AuthScreen(
  onAuthSuccess: () -> Unit,
  modifier: Modifier = Modifier,
  errorHandler: FirebaseAuthErrorHandler = FirebaseAuthErrorHandler.default,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val snackbarHostState = remember { SnackbarHostState() }

  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = onAuthSuccess,
      onUnauthenticated = {},
      scope = scope,
      errorHandler = errorHandler,
    )
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = {
      SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.testTag("auth_snackbar_host"),
      )
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      Surface(
        modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 1.dp,
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(28.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Image(
            painter = painterResource(id = R.drawable.project_pi_logo),
            contentDescription = stringResource(R.string.cd_project_pi_logo),
            contentScale = ContentScale.Crop,
            modifier =
              Modifier.size(84.dp)
                .clip(CircleShape)
                .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
          )

          Text(
            text = stringResource(R.string.auth_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
          )

          Text(
            text = stringResource(R.string.auth_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )

          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            ChannelPlatform.entries.forEach { platform ->
              val accent = platformAccentFor(platform)
              Surface(
                shape = RoundedCornerShape(999.dp),
                color = accent.container,
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                  PlatformGlyphIcon(
                    platform = platform,
                    tint = accent.foreground,
                    modifier = Modifier.size(13.dp),
                  )
                  Text(
                    text = platform.shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent.foreground,
                  )
                }
              }
            }
          }

          if (!errorMessage.isNullOrBlank()) {
            Surface(
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.errorContainer,
              modifier = Modifier.fillMaxWidth().testTag("auth_error_banner"),
            ) {
              Text(
                text = errorMessage.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(12.dp),
              )
            }
          }

          Button(
            onClick = {
              errorMessage = null
              isLoading = true
              onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                  isLoading = false
                  onAuthSuccess()
                },
                onAuthError = { msg ->
                  isLoading = false
                  errorMessage = msg
                },
                scope = scope,
                onAuthCancelled = { msg ->
                  isLoading = false
                  errorMessage = msg
                },
                snackbarHostState = snackbarHostState,
                errorHandler = errorHandler,
              )
            },
            enabled = !isLoading,
            shape = MaterialTheme.shapes.small,
            modifier =
              Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("google_sign_in_button"),
          ) {
            if (isLoading) {
              CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
              )
              Spacer(Modifier.width(10.dp))
            }
            Text(
              text = stringResource(R.string.sign_in_google),
              style = MaterialTheme.typography.labelLarge,
            )
          }
        }
      }
    }
  }
}

fun attemptAutoSignIn(
  context: Context,
  credentialManager: CredentialManager,
  onAuthSuccess: () -> Unit,
  onUnauthenticated: () -> Unit,
  scope: CoroutineScope,
  errorHandler: FirebaseAuthErrorHandler = FirebaseAuthErrorHandler.default,
) {
  if (Firebase.auth.currentUser != null) {
    onAuthSuccess()
    return
  }
  val clientId =
    try {
      context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
      errorHandler.handleAuthError(
        context = context,
        exception = e,
        stage = AuthFlowStage.SILENT_AUTO_SIGN_IN,
      )
      onUnauthenticated()
      return
    }
  val googleIdOption =
    GetGoogleIdOption.Builder()
      .setFilterByAuthorizedAccounts(true)
      .setServerClientId(clientId)
      .setAutoSelectEnabled(true)
      .build()
  val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
  scope.launch {
    try {
      val cred = credentialManager.getCredential(context, request).credential
      if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
        Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        onAuthSuccess()
      } else {
        onUnauthenticated()
      }
    } catch (e: Exception) {
      errorHandler.handleAuthError(
        context = context,
        exception = e,
        stage = AuthFlowStage.SILENT_AUTO_SIGN_IN,
      )
      onUnauthenticated()
    }
  }
}

fun onGoogleSignInClicked(
  context: Context,
  credentialManager: CredentialManager,
  onAuthSuccess: () -> Unit,
  onAuthError: (String) -> Unit,
  scope: CoroutineScope,
  onAuthCancelled: (String) -> Unit = {},
  snackbarHostState: SnackbarHostState? = null,
  errorHandler: FirebaseAuthErrorHandler = FirebaseAuthErrorHandler.default,
) {
  val clientId =
    try {
      context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
      val result =
        errorHandler.handleConfigurationError(
          context = context,
          stage = AuthFlowStage.INTERACTIVE_SIGN_IN,
          errorCode = "MISSING_DEFAULT_WEB_CLIENT_ID",
          userMessage = context.getString(R.string.auth_error_missing_web_client_id),
          detail = "R.string.default_web_client_id could not be resolved from google-services.json",
        )
      onAuthError(result.userMessage)
      if (snackbarHostState != null) {
        scope.launch {
          snackbarHostState.showSnackbar(
            message = result.userMessage,
            withDismissAction = true,
            duration = SnackbarDuration.Long,
          )
        }
      }
      return
    }
  val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
  val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()
  scope.launch {
    var stage = AuthFlowStage.INTERACTIVE_SIGN_IN
    try {
      val activityContext = context as? Activity
      val cred =
        if (activityContext != null) {
          credentialManager.getCredential(activityContext, request).credential
        } else {
          credentialManager.getCredential(context, request).credential
        }
      if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        stage = AuthFlowStage.FIREBASE_CREDENTIAL_EXCHANGE
        val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
        Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        onAuthSuccess()
      } else {
        val result =
          errorHandler.handleConfigurationError(
            context = context,
            stage = stage,
            errorCode = "UNEXPECTED_CREDENTIAL_TYPE",
            userMessage = context.getString(R.string.auth_error_unexpected_credential),
            detail = "Credential class=${cred::class.java.name}, type=${cred.type}",
          )
        onAuthError(result.userMessage)
        snackbarHostState?.showSnackbar(
          message = result.userMessage,
          withDismissAction = true,
          duration = SnackbarDuration.Long,
        )
      }
    } catch (e: GetCredentialCancellationException) {
      val result =
        if (snackbarHostState != null) {
          errorHandler.handleAndShowSnackbar(
            context = context,
            snackbarHostState = snackbarHostState,
            exception = e,
            stage = stage,
          )
        } else {
          errorHandler.handleAuthError(context = context, exception = e, stage = stage)
        }
      onAuthCancelled(result.userMessage)
    } catch (e: Exception) {
      val result =
        if (snackbarHostState != null) {
          errorHandler.handleAndShowSnackbar(
            context = context,
            snackbarHostState = snackbarHostState,
            exception = e,
            stage = stage,
          )
        } else {
          errorHandler.handleAuthError(context = context, exception = e, stage = stage)
        }
      onAuthError(result.userMessage)
    }
  }
}

fun signOutUser(
  credentialManager: CredentialManager,
  scope: CoroutineScope,
  context: Context? = null,
  errorHandler: FirebaseAuthErrorHandler = FirebaseAuthErrorHandler.default,
  onSignOutComplete: () -> Unit = {},
) {
  scope.launch {
    withContext(NonCancellable) {
      try {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
      } catch (e: Exception) {
        if (context != null) {
          errorHandler.handleAuthError(
            context = context,
            exception = e,
            stage = AuthFlowStage.SIGN_OUT,
          )
        }
      } finally {
        Firebase.auth.signOut()
        onSignOutComplete()
      }
    }
  }
}
