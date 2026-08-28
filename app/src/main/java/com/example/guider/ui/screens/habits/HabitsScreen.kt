package com.example.guider.ui.screens.habits

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.guider.R
import com.example.guider.domain.habits.Habit
import com.example.guider.domain.habits.HabitStreakCalculator
import com.example.guider.domain.habits.HabitTrackerRange
import com.example.guider.domain.habits.HabitWeekday
import com.example.guider.domain.habits.isScheduledOn
import com.example.guider.ui.components.NavigationPillListBottomPadding
import com.example.guider.ui.components.navigationPillScrollEffect
import kotlinx.coroutines.delay

@Composable
fun HabitsRoute(
    modifier: Modifier = Modifier,
    viewModel: HabitsViewModel = viewModel(),
) {
    val habits by viewModel.habits.collectAsStateWithLifecycle()

    HabitsScreen(
        habits = habits,
        onAddHabit = viewModel::addHabit,
        onToggleCompletion = viewModel::toggleCompletion,
        onDeleteHabit = viewModel::deleteHabit,
        modifier = modifier,
    )
}

@Composable
private fun HabitsScreen(
    habits: List<Habit>,
    onAddHabit: (String, Set<HabitWeekday>) -> Unit,
    onToggleCompletion: (Long, Int) -> Unit,
    onDeleteHabit: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var range by rememberSaveable { mutableStateOf(HabitTrackerRange.WEEK) }
    var periodOffset by rememberSaveable { mutableIntStateOf(0) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showDeletionPicker by rememberSaveable { mutableStateOf(false) }
    var habitPendingDeletion by remember { mutableStateOf<Habit?>(null) }
    val nowEpochMillis = System.currentTimeMillis()
    val todayKey = HabitCalendar.dayKey(nowEpochMillis)
    val period = remember(range, periodOffset, todayKey) {
        HabitCalendar.period(
            range = range,
            offset = periodOffset,
            nowEpochMillis = nowEpochMillis,
        )
    }

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
        item(key = HABITS_HEADER_KEY) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Habits",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        text = "Small actions, repeated with intention.",
                        modifier = Modifier.padding(top = 3.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = { showAddDialog = true }) {
                    Text("Add habit")
                }
            }
        }

        item(key = HABITS_CONTROLS_KEY) {
            TrackerControls(
                range = range,
                periodTitle = period.title,
                periodOffset = periodOffset,
                onRangeSelected = { selectedRange ->
                    range = selectedRange
                    periodOffset = 0
                },
                onPrevious = { periodOffset -= 1 },
                onNext = { periodOffset += 1 },
                onToday = { periodOffset = 0 },
            )
        }

        item(key = HABITS_MATRIX_KEY) {
            HabitMatrix(
                habits = habits,
                days = period.days,
                range = range,
                onToggleCompletion = onToggleCompletion,
            )
        }

        item(key = HABITS_STREAKS_KEY) {
            HabitStreaks(
                habits = habits,
                nowEpochMillis = nowEpochMillis,
                todayKey = todayKey,
                onDeleteMenuRequested = { showDeletionPicker = true },
            )
        }
    }

    if (showAddDialog) {
        AddHabitDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, scheduledWeekdays ->
                onAddHabit(name, scheduledWeekdays)
                showAddDialog = false
            },
        )
    }

    if (showDeletionPicker) {
        HabitDeletionPickerDialog(
            habits = habits,
            onDismiss = { showDeletionPicker = false },
            onHabitSelected = { habit ->
                showDeletionPicker = false
                habitPendingDeletion = habit
            },
        )
    }

    habitPendingDeletion?.let { habit ->
        DeleteHabitDialog(
            habit = habit,
            onDismiss = { habitPendingDeletion = null },
            onConfirmed = {
                onDeleteHabit(habit.id)
                habitPendingDeletion = null
            },
        )
    }
}

@Composable
private fun TrackerControls(
    range: HabitTrackerRange,
    periodTitle: String,
    periodOffset: Int,
    onRangeSelected: (HabitTrackerRange) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Tracker",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
            )
            HabitRangeSelector(
                selected = range,
                onSelected = onRangeSelected,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onPrevious) {
                Text("‹", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = periodTitle,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (periodOffset == 0) {
                TextButton(onClick = onNext, enabled = false) {
                    Text("›", style = MaterialTheme.typography.titleLarge)
                }
            } else {
                TextButton(onClick = onToday) {
                    Text("Today")
                }
                TextButton(onClick = onNext) {
                    Text("›", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun HabitRangeSelector(
    selected: HabitTrackerRange,
    onSelected: (HabitTrackerRange) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(modifier = Modifier.padding(3.dp)) {
            HabitTrackerRange.entries.forEach { range ->
                val isSelected = range == selected
                Surface(
                    modifier = Modifier.clickable { onSelected(range) },
                    shape = RoundedCornerShape(9.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ) {
                    Text(
                        text = range.label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitMatrix(
    habits: List<Habit>,
    days: List<HabitDay>,
    range: HabitTrackerRange,
    onToggleCompletion: (Long, Int) -> Unit,
) {
    val dayScrollState = rememberScrollState()
    val dayKeys = remember(days) { days.map(HabitDay::key) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = if (range == HabitTrackerRange.WEEK) {
                    "Tap a day to record this week"
                } else {
                    "Read-only monthly contribution overview"
                },
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (habits.isEmpty()) {
                Text(
                    text = "Add your first habit to begin tracking.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (range == HabitTrackerRange.MONTH) {
                CompactMonthHabitGrid(
                    habits = habits,
                    days = days,
                )
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val habitNameWidth = WeeklyHabitNameColumnWidth
                    val dayColumnWidth =
                        ((maxWidth - habitNameWidth) / 7).coerceAtLeast(26.dp)

                    LaunchedEffect(dayKeys) {
                        dayScrollState.scrollTo(0)
                    }

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.width(habitNameWidth)) {
                            MatrixCornerCell()
                            habits.forEach { habit -> HabitNameCell(habit) }
                        }
                        Column(modifier = Modifier.horizontalScroll(dayScrollState)) {
                            DayHeaderRow(
                                days = days,
                                dayColumnWidth = dayColumnWidth,
                            )
                            habits.forEach { habit ->
                                HabitCompletionRow(
                                    habit = habit,
                                    days = days,
                                    dayColumnWidth = dayColumnWidth,
                                    completionSize = WeeklyCompletionSize,
                                    onToggleCompletion = onToggleCompletion,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactMonthHabitGrid(
    habits: List<Habit>,
    days: List<HabitDay>,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
    ) {
        val dayColumnWidth = maxWidth / days.size
        val squareSize = (dayColumnWidth - 1.dp).coerceIn(3.dp, 8.dp)
        val markerDays = remember(days) {
            days.filterIndexed { index, _ -> index % 7 == 0 }
        }
        val legendRows = remember(habits) { habits.chunked(2) }

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                markerDays.forEach { day ->
                    Text(
                        text = day.dayNumber,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(
                modifier = Modifier.padding(top = 3.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                habits.forEach { habit ->
                    val color = habitColor(habit)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(MonthMatrixRowHeight),
                    ) {
                        days.forEach { day ->
                            val completed = day.key in habit.completedDayKeys
                            val scheduled = habit.isScheduledOn(day.key, day.weekday)
                            Box(
                                modifier = Modifier
                                    .width(dayColumnWidth)
                                    .height(MonthMatrixRowHeight),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(squareSize)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(
                                            if (completed) {
                                                color
                                            } else if (!scheduled) {
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant.copy(
                                                    alpha = if (day.isFuture) 0.18f else 0.42f,
                                                )
                                            },
                                        )
                                        .then(
                                            if (day.isToday) {
                                                Modifier.border(0.5.dp, color, RoundedCornerShape(2.dp))
                                            } else {
                                                Modifier
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "Habit colors",
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            legendRows.forEach { legendRow ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    legendRow.forEach { habit ->
                        val color = habitColor(habit)
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(color, CircleShape),
                            )
                            Text(
                                text = habit.name,
                                modifier = Modifier.padding(start = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (legendRow.size == 1) {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MatrixCornerCell() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(MatrixHeaderHeight)
            .padding(start = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = "Habit",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HabitNameCell(habit: Habit) {
    val color = habitColor(habit)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(MatrixRowHeight)
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(color, CircleShape),
        )
        Text(
            text = habit.name,
            modifier = Modifier.padding(start = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DayHeaderRow(
    days: List<HabitDay>,
    dayColumnWidth: Dp,
) {
    Row {
        days.forEach { day ->
            Column(
                modifier = Modifier
                    .width(dayColumnWidth)
                    .height(MatrixHeaderHeight)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (day.isToday) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = day.dayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = day.dayNumber,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun HabitCompletionRow(
    habit: Habit,
    days: List<HabitDay>,
    dayColumnWidth: Dp,
    completionSize: Dp,
    onToggleCompletion: (Long, Int) -> Unit,
) {
    val color = habitColor(habit)
    val dayKeys = remember(days) { days.map(HabitDay::key) }
    val dueDays = remember(
        habit.scheduledWeekdays,
        habit.activeStartDayKey,
        habit.activeEndDayKey,
        days,
    ) {
        days.filter { day ->
            !day.isFuture && habit.isScheduledOn(day.key, day.weekday)
        }
    }
    var celebrationTrigger by remember(habit.id, dayKeys) {
        mutableIntStateOf(0)
    }
    Row {
        days.forEachIndexed { index, day ->
            val completed = day.key in habit.completedDayKeys
            val scheduled = habit.isScheduledOn(day.key, day.weekday)
            val completesWeek = scheduled && !completed && dueDays.all { visibleDay ->
                visibleDay.key == day.key || visibleDay.key in habit.completedDayKeys
            }
            HabitCompletionCell(
                habitName = habit.name,
                day = day,
                color = color,
                dayColumnWidth = dayColumnWidth,
                completionSize = completionSize,
                completed = completed,
                scheduled = scheduled,
                celebrationTrigger = celebrationTrigger,
                celebrationIndex = index,
                onToggle = {
                    if (completesWeek) celebrationTrigger += 1
                    onToggleCompletion(habit.id, day.key)
                },
            )
        }
    }
}

@Composable
private fun HabitCompletionCell(
    habitName: String,
    day: HabitDay,
    color: Color,
    dayColumnWidth: Dp,
    completionSize: Dp,
    completed: Boolean,
    scheduled: Boolean,
    celebrationTrigger: Int,
    celebrationIndex: Int,
    onToggle: () -> Unit,
) {
    val emptyAlpha = if (day.isFuture) 0.05f else 0.14f
    val cellColor by animateColorAsState(
        targetValue = when {
            completed -> color
            !scheduled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f)
            else -> color.copy(alpha = emptyAlpha)
        },
        animationSpec = tween(durationMillis = 280),
        label = "Habit completion fill",
    )
    val celebrationPulse = remember { Animatable(0f) }
    LaunchedEffect(celebrationTrigger) {
        if (celebrationTrigger == 0) return@LaunchedEffect
        celebrationPulse.snapTo(0f)
        delay(celebrationIndex * 65L)
        celebrationPulse.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        )
        celebrationPulse.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing),
        )
    }
    val shape = RoundedCornerShape(9.dp)
    val pulse = celebrationPulse.value

    Box(
        modifier = Modifier
            .width(dayColumnWidth)
            .height(MatrixRowHeight),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(completionSize)
                .shadow(
                    elevation = (pulse * 18f).dp,
                    shape = shape,
                    ambientColor = color,
                    spotColor = color,
                )
                .graphicsLayer {
                    val scale = 1f + pulse * 0.24f
                    scaleX = scale
                    scaleY = scale
                    translationY = -pulse * 7f
                    rotationZ = pulse * if (celebrationIndex % 2 == 0) 8f else -8f
                }
                .clip(shape)
                .toggleable(
                    value = completed,
                    enabled = !day.isFuture && scheduled,
                    role = Role.Checkbox,
                    onValueChange = { onToggle() },
                )
                .background(cellColor)
                .then(
                    if (day.isToday) {
                        Modifier.border(1.dp, color, shape)
                    } else {
                        Modifier
                    },
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = "$habitName, ${day.fullLabel}"
                    stateDescription = when {
                        !scheduled -> "Not scheduled"
                        day.isFuture -> "Not available yet"
                        completed -> "Completed"
                        else -> "Not completed"
                    }
                },
        )
    }
}

@Composable
private fun HabitStreaks(
    habits: List<Habit>,
    nowEpochMillis: Long,
    todayKey: Int,
    onDeleteMenuRequested: () -> Unit,
) {
    val recentDayKeys = remember(todayKey) {
        HabitCalendar.recentDayKeys(
            nowEpochMillis = nowEpochMillis,
            count = 366,
        )
    }
    val todayWeekday = remember(todayKey) {
        com.example.guider.domain.habits.HabitWeekday.fromCalendarValue(
            com.example.guider.domain.time.DayKeys.weekday(todayKey),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Current streaks",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
            )
            IconButton(
                onClick = onDeleteMenuRequested,
                enabled = habits.isNotEmpty(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.delete_ic),
                    contentDescription = "Delete a habit",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
        Text(
            text = "An unfinished today won’t break an active streak. A missed past day resets it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        habits.forEach { habit ->
            val streak = remember(
                habit.completedDayKeys,
                habit.scheduledWeekdays,
                habit.activeStartDayKey,
                habit.activeEndDayKey,
                todayKey,
            ) {
                HabitStreakCalculator.currentStreak(
                    completedDayKeys = habit.completedDayKeys,
                    dayKeysNewestFirst = recentDayKeys.filter { dayKey ->
                        com.example.guider.domain.habits.HabitWeekday.fromCalendarValue(
                            com.example.guider.domain.time.DayKeys.weekday(dayKey),
                        ).let { weekday -> habit.isScheduledOn(dayKey, weekday) }
                    },
                    allowIncompleteFirstDay = habit.isScheduledOn(todayKey, todayWeekday),
                )
            }
            val color = habitColor(habit)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = color.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(color, CircleShape),
                    )
                    Text(
                        text = habit.name,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 9.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = if (streak == 1) "1 check-in" else "$streak check-ins",
                        style = MaterialTheme.typography.titleSmall,
                        color = color,
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitDeletionPickerDialog(
    habits: List<Habit>,
    onDismiss: () -> Unit,
    onHabitSelected: (Habit) -> Unit,
) {
    var selectedHabit by remember { mutableStateOf<Habit?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select a habit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Choose which habit you want to delete.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                habits.forEach { habit ->
                    val selected = selectedHabit?.id == habit.id
                    val color = habitColor(habit)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedHabit = habit },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) {
                            color.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        border = BorderStroke(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) color else MaterialTheme.colorScheme.outlineVariant,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(color, CircleShape),
                            )
                            Text(
                                text = habit.name,
                                modifier = Modifier.padding(start = 9.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedHabit?.let(onHabitSelected) },
                enabled = selectedHabit != null,
            ) {
                Text("Continue")
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
private fun DeleteHabitDialog(
    habit: Habit,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit,
) {
    var confirmationArmed by rememberSaveable(habit.id) { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Delete habit?",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "Delete “${habit.name}” and all of its tracking history?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    if (confirmationArmed) {
                        Button(
                            onClick = onConfirmed,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) {
                            Text("Are you sure?")
                        }
                    } else {
                        TextButton(
                            onClick = { confirmationArmed = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Confirm")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddHabitDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Set<HabitWeekday>) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var scheduledWeekdays by remember { mutableStateOf(HabitWeekday.entries.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a habit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Habit name") },
                    singleLine = true,
                )
                Text(
                    text = "Scheduled days",
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    HabitWeekday.entries.forEach { weekday ->
                        val selected = weekday in scheduledWeekdays
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .semantics {
                                    contentDescription = weekday.name
                                        .lowercase()
                                        .replaceFirstChar(Char::uppercase)
                                }
                                .toggleable(
                                    value = selected,
                                    role = Role.Checkbox,
                                    onValueChange = {
                                        scheduledWeekdays = scheduledWeekdays
                                            .toMutableSet()
                                            .apply {
                                                if (!add(weekday)) remove(weekday)
                                            }
                                    },
                                ),
                            shape = CircleShape,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            contentColor = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        ) {
                            Box(
                                modifier = Modifier.height(34.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = weekday.shortLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
                Text(
                    text = if (scheduledWeekdays.isEmpty()) {
                        "Choose at least one day."
                    } else {
                        "Guider will assign a unique color to this habit."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (scheduledWeekdays.isEmpty()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), scheduledWeekdays) },
                enabled = name.isNotBlank() && scheduledWeekdays.isNotEmpty(),
            ) {
                Text("Add habit")
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
private fun habitColor(habit: Habit): Color {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return Color.hsl(
        hue = habit.colorHue,
        saturation = if (darkTheme) 0.55f else 0.58f,
        lightness = if (darkTheme) 0.67f else 0.43f,
    )
}

private val WeeklyHabitNameColumnWidth = 96.dp
private val WeeklyCompletionSize = 24.dp
private val MatrixHeaderHeight = 52.dp
private val MatrixRowHeight = 54.dp
private val MonthMatrixRowHeight = 9.dp

private const val HABITS_HEADER_KEY = "habits_header"
private const val HABITS_CONTROLS_KEY = "habits_controls"
private const val HABITS_MATRIX_KEY = "habits_matrix"
private const val HABITS_STREAKS_KEY = "habits_streaks"
