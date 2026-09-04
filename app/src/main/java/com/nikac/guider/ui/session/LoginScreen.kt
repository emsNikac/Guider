package com.nikac.guider.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nikac.guider.R

@Composable
fun LoginScreen(state: AppSessionState, onGoogleSignIn: () -> Unit, onContinueAsGuest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(painterResource(R.drawable.tasks_nav_ic), contentDescription = null,
                modifier = Modifier.padding(18.dp).size(32.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.login_headline), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.login_subtitle), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LocalProgressCard(stringResource(R.string.guest_notice_title), stringResource(R.string.guest_notice_body))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GoogleSignInButton(state.canSignIn, state.isBusy, onGoogleSignIn)
            Button(
                onClick = onContinueAsGuest,
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text(stringResource(R.string.continue_as_guest)) }
            if (!state.canSignIn) {
                Text(stringResource(R.string.firebase_setup_notice), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SessionFeedback(state.message)
        }
        Text(stringResource(R.string.auth_scope_notice), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
