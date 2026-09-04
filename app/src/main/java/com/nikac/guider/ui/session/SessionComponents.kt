package com.nikac.guider.ui.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nikac.guider.R

@Composable
internal fun GoogleSignInButton(enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(painterResource(R.drawable.google_g), contentDescription = null, tint = Color.Unspecified)
        }
        Text(
            stringResource(if (busy) R.string.auth_working else R.string.google_sign_in),
            modifier = Modifier.padding(start = 12.dp),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun LocalProgressCard(title: String, body: String) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SessionFeedback(message: SessionMessage?) {
    if (message == null) return
    val text = when (message) {
        SessionMessage.CONFIGURATION -> R.string.auth_error_configuration
        SessionMessage.NETWORK -> R.string.auth_error_network
        SessionMessage.NO_ACCOUNT -> R.string.auth_error_no_account
        SessionMessage.PROVIDER_UNAVAILABLE -> R.string.auth_error_provider
        SessionMessage.SIGN_IN_FAILED -> R.string.auth_error_sign_in
        SessionMessage.SIGN_OUT_CLEANUP -> R.string.auth_error_sign_out_cleanup
        SessionMessage.PREFERENCES_FAILED -> R.string.preferences_error
    }
    Text(
        text = stringResource(text),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}
