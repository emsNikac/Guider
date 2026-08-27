package com.example.guider.ui.screens.sleep

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.guider.domain.sleep.ActiveSleepSession
import com.example.guider.domain.sleep.SleepCycleCalculator
import com.example.guider.domain.sleep.SleepCycleSuggestion
import com.example.guider.ui.components.NavigationPillListBottomPadding
import com.example.guider.ui.components.navigationPillItem
import com.example.guider.ui.components.navigationPillScrollEffect
import kotlinx.coroutines.delay
import java.text.DateFormat as JavaDateFormat
import java.util.Calendar
import java.util.Date

@Composable
fun SleepCalculatorRoute(
    isVisible: Boolean = true,
    modifier: Modifier = Modifier,
    viewModel: SleepViewModel = viewModel(),
) {
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    var manualReferenceTime by rememberSaveable { mutableStateOf<Long?>(null) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var notificationPermissionDenied by rememberSaveable { mutableStateOf(false) }
    val deviceTime = rememberVisibleDeviceTime(
        enabled = isVisible && manualReferenceTime == null,
    )
    val referenceTime = manualReferenceTime ?: deviceTime
    val suggestions = remember(referenceTime) {
        SleepCycleCalculator.suggestions(referenceTime)
    }
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationPermissionDenied = !granted
        viewModel.activateHibernation()
    }

    SleepCalculatorScreen(
        referenceTimeEpochMillis = referenceTime,
        isUsingDeviceTime = manualReferenceTime == null,
        suggestions = suggestions,
        activeSession = activeSession,
        notificationPermissionDenied = notificationPermissionDenied,
        history = history,
        onChooseTime = { showTimePicker = true },
        onUseDeviceTime = { manualReferenceTime = null },
        onActivateHibernation = {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                notificationPermissionDenied = false
                viewModel.activateHibernation()
            }
        },
        onFinishHibernation = viewModel::finishHibernation,
        modifier = modifier,
    )

    if (showTimePicker) {
        ManualTimePickerDialog(
            initialTimeEpochMillis = referenceTime,
            onDismiss = { showTimePicker = false },
            onConfirm = { selectedTime ->
                manualReferenceTime = selectedTime
                showTimePicker = false
            },
        )
    }
}

@Composable
private fun SleepCalculatorScreen(
    referenceTimeEpochMillis: Long,
    isUsingDeviceTime: Boolean,
    suggestions: List<SleepCycleSuggestion>,
    activeSession: ActiveSleepSession?,
    notificationPermissionDenied: Boolean,
    history: List<com.example.guider.domain.sleep.SleepRecord>,
    onChooseTime: () -> Unit,
    onUseDeviceTime: () -> Unit,
    onActivateHibernation: () -> Unit,
    onFinishHibernation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .navigationPillScrollEffect(),
        contentPadding = PaddingValues(
            start = 24.dp,
            top = 22.dp,
            end = 24.dp,
            bottom = NavigationPillListBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        navigationPillItem("sleep_header") {
            Text(
                text = "Sleep calculator",
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = "Wake near the end of a sleep cycle.",
                modifier = Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        navigationPillItem("sleep_reference_time") {
            ReferenceTimeCard(
                referenceTimeEpochMillis = referenceTimeEpochMillis,
                isUsingDeviceTime = isUsingDeviceTime,
                onChooseTime = onChooseTime,
                onUseDeviceTime = onUseDeviceTime,
            )
        }

        navigationPillItem("sleep_suggestion_title") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Suggested wake-up times",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "Includes 15 minutes to fall asleep and 90 minutes per cycle.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        suggestions.take(4).chunked(2).forEach { rowSuggestions ->
            navigationPillItem(
                itemKey = "sleep_cycle_row_${rowSuggestions.first().cycleCount}",
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowSuggestions.forEach { suggestion ->
                        SmallCycleTile(
                            suggestion = suggestion,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        navigationPillItem("sleep_recommended_cycles") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                suggestions.drop(4).forEach { suggestion ->
                    RecommendedCycleTile(
                        suggestion = suggestion,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        navigationPillItem("sleep_divider") {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        navigationPillItem("sleep_hibernation") {
            HibernationCard(
                activeSession = activeSession,
                notificationPermissionDenied = notificationPermissionDenied,
                onActivate = onActivateHibernation,
                onFinish = onFinishHibernation,
            )
        }

        navigationPillItem("sleep_history") {
            SleepHistoryCard(records = history)
        }
    }
}

@Composable
private fun ReferenceTimeCard(
    referenceTimeEpochMillis: Long,
    isUsingDeviceTime: Boolean,
    onChooseTime: () -> Unit,
    onUseDeviceTime: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, top = 14.dp, end = 10.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isUsingDeviceTime) "Device time" else "Manual start time",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = formatTime(referenceTimeEpochMillis),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = if (isUsingDeviceTime) "Refreshes each visible minute" else "Calculations are paused on this time",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onChooseTime) {
                    Text(if (isUsingDeviceTime) "Set time" else "Change")
                }
                if (!isUsingDeviceTime) {
                    TextButton(onClick = onUseDeviceTime) {
                        Text("Use now")
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallCycleTile(
    suggestion: SleepCycleSuggestion,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(82.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(suggestion.wakeAtEpochMillis),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "${suggestion.cycleCount} ${if (suggestion.cycleCount == 1) "cycle" else "cycles"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecommendedCycleTile(
    suggestion: SleepCycleSuggestion,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val containerColor = if (suggestion.cycleCount == 5) {
        MaterialTheme.colorScheme.primaryContainer
    } else if (isDark) {
        Color(0xFF28525D)
    } else {
        Color(0xFFD8EDF3)
    }
    val contentColor = if (suggestion.cycleCount == 5) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else if (isDark) {
        Color(0xFFD8EDF3)
    } else {
        Color(0xFF234E58)
    }

    Surface(
        modifier = modifier.height(118.dp),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.18f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                shape = CircleShape,
                color = contentColor.copy(alpha = 0.12f),
                contentColor = contentColor,
            ) {
                Text(
                    text = "Recommended",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(
                text = formatTime(suggestion.wakeAtEpochMillis),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${suggestion.cycleCount} cycles • ${formatCycleDuration(suggestion.cycleCount)}",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun HibernationCard(
    activeSession: ActiveSleepSession?,
    notificationPermissionDenied: Boolean,
    onActivate: () -> Unit,
    onFinish: () -> Unit,
) {
    val active = activeSession != null
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = if (active) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        contentColor = if (active) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (active) "Hibernation is active" else "Track tonight’s sleep",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = if (activeSession != null) {
                    "Sleep time started at ${formatTime(activeSession.sleepStartsAtEpochMillis)}. " +
                        "We’ll check in after five cycles; you can also stop sooner below."
                } else {
                    "Tracking begins 15 minutes after activation. Your timestamps are saved even if the app closes."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (active) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (notificationPermissionDenied && active) {
                Text(
                    text = "Notifications are disabled. Return here and use the wake button to save this sleep.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (active) {
                OutlinedButton(
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("I’m awake — save sleep")
                }
            } else {
                Button(
                    onClick = onActivate,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Activate hibernation")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualTimePickerDialog(
    initialTimeEpochMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val context = LocalContext.current
    val initialCalendar = remember(initialTimeEpochMillis) {
        Calendar.getInstance().apply { timeInMillis = initialTimeEpochMillis }
    }
    val timePickerState = rememberTimePickerState(
        initialHour = initialCalendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = initialCalendar.get(Calendar.MINUTE),
        is24Hour = DateFormat.is24HourFormat(context),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set calculation time") },
        text = { TimeInput(state = timePickerState) },
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = Calendar.getInstance().apply {
                        timeInMillis = initialTimeEpochMillis
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onConfirm(selected.timeInMillis)
                },
            ) {
                Text("Use time")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun rememberVisibleDeviceTime(enabled: Boolean): Long {
    var deviceTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(enabled, lifecycleOwner) {
        if (!enabled) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val now = System.currentTimeMillis()
                deviceTime = now
                val untilNextMinute = MINUTE_MILLIS - now % MINUTE_MILLIS
                delay(untilNextMinute.coerceAtLeast(1_000L))
            }
        }
    }
    return deviceTime
}

private fun formatTime(epochMillis: Long): String =
    JavaDateFormat.getTimeInstance(JavaDateFormat.SHORT).format(Date(epochMillis))

private fun formatCycleDuration(cycles: Int): String {
    val totalMinutes = SleepCycleCalculator.FALL_ASLEEP_MINUTES +
        cycles * SleepCycleCalculator.CYCLE_MINUTES
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (minutes == 0) "${hours}h" else "${hours}h ${minutes}m"
}

private const val MINUTE_MILLIS = 60_000L
