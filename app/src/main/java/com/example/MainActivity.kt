package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ResponseRepository
import com.example.data.UserPreferencesRepository
import com.example.ui.AuthScreen
import com.example.ui.ProjectPiScreen
import com.example.ui.ProjectPiViewModel
import com.example.ui.signOutUser
import com.example.ui.theme.ProjectPiTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      ProjectPiTheme(darkTheme = isSystemInDarkTheme()) {
        AppNavigation()
      }
    }
  }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
  val currentUser by
    auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val user = currentUser

  if (user == null) {
    AuthScreen(onAuthSuccess = {})
  } else {
    key(user.uid) {
      AuthenticatedProjectPiApp(currentUserId = user.uid)
    }
  }
}

@Composable
private fun AuthenticatedProjectPiApp(currentUserId: String) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  val viewModel: ProjectPiViewModel =
    viewModel(
      key = currentUserId,
      factory =
        viewModelFactory {
          initializer {
            val app =
              checkNotNull(this[APPLICATION_KEY]) {
                "APPLICATION_KEY missing from CreationExtras"
              }
            val databaseId = app.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            ProjectPiViewModel(
              preferencesRepository = UserPreferencesRepository(app.applicationContext),
              responseRepository = ResponseRepository(db),
              currentUserId = currentUserId,
            )
          }
        },
    )

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val lifecycleOwner = LocalLifecycleOwner.current

  DisposableEffect(lifecycleOwner, currentUserId) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> viewModel.setForeground(true)
        Lifecycle.Event.ON_PAUSE -> viewModel.setForeground(false)
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  ProjectPiScreen(
    uiState = uiState,
    onSelectTab = viewModel::selectTab,
    onSelectFeedFilter = viewModel::selectFeedFilter,
    onRefreshFeed = viewModel::refreshFeed,
    onWebViewPostsExtracted = viewModel::mergeWebViewPosts,
    onSaveResponse = viewModel::saveResponse,
    onUpdateResponse = viewModel::updateResponse,
    onDeleteResponse = viewModel::deleteResponse,
    onClearResponseError = viewModel::clearResponseError,
    onSearchQueryChange = viewModel::updateSearchQuery,
    onToggleJoined = viewModel::toggleChannelJoined,
    onOpenDetails = viewModel::openChannelDetails,
    onDismissDetails = viewModel::dismissChannelDetails,
    onSignOut = {
      viewModel.setForeground(false)
      signOutUser(
        credentialManager = credentialManager,
        scope = scope,
        context = context.applicationContext,
      )
    },
  )
}
