package com.nikac.guider.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.nikac.guider.R
import com.nikac.guider.domain.settings.ThemeMode

@Composable
fun SettingsScreen(
    state: AppSessionState,
    darkTheme: Boolean,
    onDismiss: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onGoogleSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    // A full-screen dialog keeps the pager and each tab's scroll position alive underneath.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
    )) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 24.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(R.drawable.back_ic), stringResource(R.string.settings_back))
                    }
                    Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
                }
                Column(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.appearance_title), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.appearance_subtitle), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.selectableGroup().padding(vertical = 4.dp)) {
                            ThemeMode.entries.forEach { mode ->
                                val (title, detail) = when (mode) {
                                    ThemeMode.SYSTEM -> R.string.theme_system to R.string.theme_system_detail
                                    ThemeMode.LIGHT -> R.string.theme_light to R.string.theme_light_detail
                                    ThemeMode.DARK -> R.string.theme_dark to R.string.theme_dark_detail
                                }
                                Row(
                                    Modifier.fillMaxWidth().selectable(
                                        selected = mode == state.themeMode,
                                        enabled = !state.isSavingTheme,
                                        role = Role.RadioButton,
                                        onClick = { onThemeSelected(mode) },
                                    ).padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = mode == state.themeMode, onClick = null, enabled = !state.isSavingTheme)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                                        Text(stringResource(detail), style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    Text(stringResource(R.string.account_title), style = MaterialTheme.typography.titleLarge)
                    AccountSection(state, onGoogleSignIn, onSignOut)
                    LocalProgressCard(stringResource(R.string.local_progress_notice), stringResource(R.string.local_progress_detail))
                    SessionFeedback(state.message)
                }
            }
        }
    }
}

@Composable
private fun AccountSection(state: AppSessionState, onGoogleSignIn: () -> Unit, onSignOut: () -> Unit) {
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    val user = state.user
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(user?.displayName?.takeIf { it.isNotBlank() }
                ?: stringResource(if (user == null) R.string.guest_account else R.string.google_account),
                style = MaterialTheme.typography.titleMedium)
            if (user != null) {
                user.email?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text(stringResource(R.string.account_signed_in), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
                OutlinedButton(onClick = { confirmSignOut = true }, enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sign_out)) }
            } else {
                GoogleSignInButton(state.canSignIn, state.isBusy, onGoogleSignIn)
                if (!state.canSignIn) {
                    Text(stringResource(R.string.firebase_setup_notice), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (confirmSignOut && user != null) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.sign_out_title)) },
            text = { Text(stringResource(R.string.sign_out_detail)) },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; onSignOut() }, enabled = !state.isBusy) {
                    Text(stringResource(R.string.sign_out))
                }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.cancel_action)) } },
        )
    }
}
