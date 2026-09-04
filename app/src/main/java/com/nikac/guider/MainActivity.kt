package com.nikac.guider

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nikac.guider.data.auth.FirebaseAuthRepository
import com.nikac.guider.data.auth.GoogleCredentialProvider
import com.nikac.guider.data.settings.LocalAppPreferences
import com.nikac.guider.ui.GuiderApp
import com.nikac.guider.ui.GuiderDestination
import com.nikac.guider.ui.session.AppSessionViewModel
import com.nikac.guider.ui.session.LoginScreen
import com.nikac.guider.ui.session.SettingsScreen
import com.nikac.guider.ui.theme.AppTheme
import com.nikac.guider.notifications.WakeUpReceiver
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val destinationRequest = mutableStateOf<GuiderDestination?>(null)
    private val session: AppSessionViewModel by viewModels {
        viewModelFactory {
            initializer {
                AppSessionViewModel(LocalAppPreferences(application), FirebaseAuthRepository(application))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            session.state.value.isLoading
        }
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            val sessionState by session.state.collectAsStateWithLifecycle()
            val darkTheme = sessionState.themeMode.isDark(isSystemInDarkTheme())
            var showSettings by rememberSaveable { mutableStateOf(false) }
            val onGoogleSignIn = {
                session.signIn {
                    GoogleCredentialProvider.getIdToken(
                        activity = this@MainActivity,
                        webClientId = session.googleWebClientId,
                    )
                }
            }
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.argb(230, 255, 255, 255),
                        android.graphics.Color.argb(128, 27, 27, 27),
                    ) { darkTheme },
                )
            }
            LaunchedEffect(sessionState.hasAccess) {
                if (!sessionState.hasAccess) showSettings = false
            }
            AppTheme(darkTheme = darkTheme) {
                val guiderApplication = application as GuiderApplication
                val dailyTasksStatusFlow = remember(guiderApplication) {
                    guiderApplication.featureStatuses
                        .map { statuses -> statuses.getValue(AppFeature.DAILY_TASKS) }
                        .distinctUntilChanged()
                }
                val dailyTasksStatus by dailyTasksStatusFlow.collectAsStateWithLifecycle(
                    initialValue = guiderApplication.featureStatus(AppFeature.DAILY_TASKS),
                )
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when {
                        sessionState.isLoading -> GuiderLoadingScreen(failed = false, onRetry = {})
                        !sessionState.hasAccess -> LoginScreen(
                            state = sessionState,
                            onGoogleSignIn = onGoogleSignIn,
                            onContinueAsGuest = session::continueAsGuest,
                        )
                        dailyTasksStatus == FeatureLoadStatus.READY -> GuiderApp(
                            destinationRequest = destinationRequest.value,
                            onDestinationRequestConsumed = { destinationRequest.value = null },
                            onOpenSettings = { showSettings = true },
                        )
                        else -> GuiderLoadingScreen(
                            failed = dailyTasksStatus == FeatureLoadStatus.FAILED,
                            onRetry = { guiderApplication.requestFeature(AppFeature.DAILY_TASKS) },
                        )
                    }
                    if (showSettings && sessionState.hasAccess) {
                        SettingsScreen(
                            state = sessionState,
                            darkTheme = darkTheme,
                            onDismiss = { showSettings = false },
                            onThemeSelected = session::setThemeMode,
                            onGoogleSignIn = onGoogleSignIn,
                            onSignOut = session::signOut,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != WakeUpReceiver.ACTION_CONFIRM_WAKE_UP) return
        val guiderApplication = application as GuiderApplication
        intent.action = null
        lifecycleScope.launch {
            runCatching {
                guiderApplication.awaitFeature(AppFeature.SLEEP)
                guiderApplication.sleepRepository.finishHibernation(System.currentTimeMillis())
                guiderApplication.hibernationNotificationManager.cancelActiveSession()
                guiderApplication.hibernationPromptScheduler.cancel()
                destinationRequest.value = GuiderDestination.SLEEP
            }
        }
    }
}

@Composable
private fun GuiderLoadingScreen(
    failed: Boolean,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = if (failed) "Guider couldn't load" else "Preparing Guider",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (failed) {
            TextButton(onClick = onRetry) {
                Text("Try again")
            }
        }
    }
}
