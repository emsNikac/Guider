package com.example.guider.ui.screens.habits

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.guider.domain.habits.HabitTrackerRange
import com.example.guider.domain.habits.HabitWeekday
import com.example.guider.domain.habits.isScheduledOn
import com.example.guider.ui.components.NavigationPillListBottomPadding
import com.example.guider.ui.components.navigationPillScrollEffect
import com.example.guider.ui.util.ImmutableListSnapshot

@Composable
fun HabitsRoute(
    modifier: Modifier = Modifier,
    viewModel: HabitsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HabitsScreen(
        habits = uiState.habits,
        streaksByHabitId = uiState.streaksByHabitId,
        range = uiState.range,
        periodOffset = uiState.periodOffset,
        period = uiState.period,
        onAddHabit = viewModel::addHabit,
        onToggleCompletion = viewModel::toggleCompletion,
        onDeleteHabit = viewModel::deleteHabit,
        onRangeSelected = viewModel::selectRange,
        onPreviousPeriod = viewModel::showPreviousPeriod,
        onNextPeriod = viewModel::showNextPeriod,
        onCurrentPeriod = viewModel::showCurrentPeriod,
        modifier = modifier,
    )
}

@Composable
private fun HabitsScreen(
    habits: ImmutableListSnapshot<Habit>,
    streaksByHabitId: Map<Long, Int>,
    range: HabitTrackerRange,
    periodOffset: Int,
    period: HabitPeriod,
    onAddHabit: (String, Set<HabitWeekday>) -> Unit,
    onToggleCompletion: (Long, Int) -> Unit,
    onDeleteHabit: (Long) -> Unit,
    onRangeSelected: (HabitTrackerRange) -> Unit,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onCurrentPeriod: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showDeletionPicker by rememberSaveable { mutableStateOf(false) }
    var habitPendingDeletion by remember { mutableStateOf<Habit?>(null) }

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
                onRangeSelected = onRangeSelected,
                onPrevious = onPreviousPeriod,
                onNext = onNextPeriod,
                onToday = onCurrentPeriod,
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

        item(key = HABITS_STREAKS_HEADER_KEY) {
            HabitStreaksHeader(
                hasHabits = habits.isNotEmpty(),
                onDeleteMenuRequested = { showDeletionPicker = true },
            )
        }
        items(
            items = habits,
            key = { habit -> "habit_streak_${habit.id}" },
            contentType = { HABIT_STREAK_CONTENT_TYPE },
        ) { habit ->
            HabitStreakCard(
                habit = habit,
                streak = streaksByHabitId[habit.id] ?: 0,
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
        val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val colorsByHabitId = remember(habits, darkTheme) {
            habits.associate { habit -> habit.id to habitColor(habit.colorHue, darkTheme) }
        }
        val outlineColor = MaterialTheme.colorScheme.outlineVariant
        val gridHeight = if (habits.isEmpty()) {
            0.dp
        } else {
            MonthMatrixRowHeight * habits.size + MonthMatrixRowSpacing * (habits.size - 1)
        }

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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp)
                    .height(gridHeight)
                    .drawWithCache {
                        val columnWidthPx = size.width / days.size
                        val squareSizePx = squareSize.toPx()
                        val rowHeightPx = MonthMatrixRowHeight.toPx()
                        val rowStepPx = rowHeightPx + MonthMatrixRowSpacing.toPx()
                        val cornerRadius = CornerRadius(2.dp.toPx())
                        val todayStroke = Stroke(width = 0.5.dp.toPx())
                        val borderInset = todayStroke.width / 2f
                        val borderCornerRadius = CornerRadius(
                            x = (cornerRadius.x - borderInset).coerceAtLeast(0f),
                            y = (cornerRadius.y - borderInset).coerceAtLeast(0f),
                        )

                        onDrawBehind {
                            habits.forEachIndexed { habitIndex, habit ->
                                val habitColor = colorsByHabitId.getValue(habit.id)
                                val top = habitIndex * rowStepPx + (rowHeightPx - squareSizePx) / 2f
                                days.forEachIndexed { dayIndex, day ->
                                    val completed = day.key in habit.completedDayKeys
                                    val scheduled = habit.isScheduledOn(day.key, day.weekday)
                                    val cellColor = when {
                                        completed -> habitColor
                                        !scheduled -> outlineColor.copy(alpha = 0.12f)
                                        else -> outlineColor.copy(
                                            alpha = if (day.isFuture) 0.18f else 0.42f,
                                        )
                                    }
                                    val cellTopLeft = Offset(
                                        x = dayIndex * columnWidthPx +
                                            (columnWidthPx - squareSizePx) / 2f,
                                        y = top,
                                    )
                                    drawRoundRect(
                                        color = cellColor,
                                        topLeft = cellTopLeft,
                                        size = Size(squareSizePx, squareSizePx),
                                        cornerRadius = cornerRadius,
                                    )
                                    if (day.isToday) {
                                        drawRoundRect(
                                            color = habitColor,
                                            topLeft = cellTopLeft + Offset(borderInset, borderInset),
                                            size = Size(
                                                squareSizePx - todayStroke.width,
                                                squareSizePx - todayStroke.width,
                                            ),
                                            cornerRadius = borderCornerRadius,
                                            style = todayStroke,
                                        )
                                    }
                                }
                            }
                        }
                    },
            )

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
                        val color = colorsByHabitId.getValue(habit.id)
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
    val periodIdentity = days.firstOrNull()?.key
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
    val incompleteDueDayCount = remember(dueDays, habit.completedDayKeys) {
        dueDays.count { day -> day.key !in habit.completedDayKeys }
    }
    var celebrationTrigger by remember(habit.id, periodIdentity) {
        mutableIntStateOf(0)
    }
    val celebrationTimeline = remember(habit.id, periodIdentity) { Animatable(0f) }
    LaunchedEffect(celebrationTrigger) {
        if (celebrationTrigger == 0) return@LaunchedEffect
        celebrationTimeline.snapTo(0f)
        celebrationTimeline.animateTo(
            targetValue = CELEBRATION_TOTAL_MILLIS.toFloat(),
            animationSpec = tween(
                durationMillis = CELEBRATION_TOTAL_MILLIS,
                easing = LinearEasing,
            ),
        )
    }
    Row {
        days.forEachIndexed { index, day ->
            val completed = day.key in habit.completedDayKeys
            val scheduled = habit.isScheduledOn(day.key, day.weekday)
            val completesWeek =
                !day.isFuture && scheduled && !completed && incompleteDueDayCount == 1
            HabitCompletionCell(
                habitName = habit.name,
                day = day,
                color = color,
                dayColumnWidth = dayColumnWidth,
                completionSize = completionSize,
                completed = completed,
                scheduled = scheduled,
                celebrationTimeline = celebrationTimeline,
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
    celebrationTimeline: Animatable<Float, AnimationVector1D>,
    celebrationIndex: Int,
    onToggle: () -> Unit,
) {
    val emptyAlpha = if (day.isFuture) 0.05f else 0.14f
    val cellColor = animateColorAsState(
        targetValue = when {
            completed -> color
            !scheduled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f)
            else -> color.copy(alpha = emptyAlpha)
        },
        animationSpec = tween(durationMillis = 280),
        label = "Habit completion fill",
    )
    Box(
        modifier = Modifier
            .width(dayColumnWidth)
            .height(MatrixRowHeight),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(completionSize)
                .graphicsLayer {
                    val pulse = celebrationPulse(
                        elapsedMillis = celebrationTimeline.value,
                        index = celebrationIndex,
                    )
                    val scale = 1f + pulse * 0.24f
                    scaleX = scale
                    scaleY = scale
                    translationY = -pulse * 7f
                    rotationZ = pulse * if (celebrationIndex % 2 == 0) 8f else -8f
                    shadowElevation = pulse * 18.dp.toPx()
                    shape = HabitCompletionShape
                    clip = true
                    ambientShadowColor = color
                    spotShadowColor = color
                }
                .toggleable(
                    value = completed,
                    enabled = !day.isFuture && scheduled,
                    role = Role.Checkbox,
                    onValueChange = { onToggle() },
                )
                .drawBehind { drawRect(cellColor.value) }
                .then(
                    if (day.isToday) {
                        Modifier.border(1.dp, color, HabitCompletionShape)
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
private fun HabitStreaksHeader(
    hasHabits: Boolean,
    onDeleteMenuRequested: () -> Unit,
) {
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
                enabled = hasHabits,
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
    }
}

@Composable
private fun HabitStreakCard(
    habit: Habit,
    streak: Int,
) {
    val color = habitColor(habit)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = HabitStreakShape,
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
    return remember(habit.colorHue, darkTheme) {
        habitColor(habit.colorHue, darkTheme)
    }
}

private fun habitColor(hue: Float, darkTheme: Boolean): Color = Color.hsl(
    hue = hue,
    saturation = if (darkTheme) 0.55f else 0.58f,
    lightness = if (darkTheme) 0.67f else 0.43f,
)

private fun celebrationPulse(elapsedMillis: Float, index: Int): Float {
    val localElapsed = elapsedMillis - index * CELEBRATION_STAGGER_MILLIS
    return when {
        localElapsed <= 0f -> 0f
        localElapsed < CELEBRATION_RISE_MILLIS -> FastOutSlowInEasing.transform(
            localElapsed / CELEBRATION_RISE_MILLIS,
        )
        localElapsed < CELEBRATION_CELL_MILLIS -> 1f - FastOutSlowInEasing.transform(
            (localElapsed - CELEBRATION_RISE_MILLIS) / CELEBRATION_FALL_MILLIS,
        )
        else -> 0f
    }
}

private val HabitCompletionShape = RoundedCornerShape(9.dp)
private val HabitStreakShape = RoundedCornerShape(16.dp)
private val WeeklyHabitNameColumnWidth = 96.dp
private val WeeklyCompletionSize = 24.dp
private val MatrixHeaderHeight = 52.dp
private val MatrixRowHeight = 54.dp
private val MonthMatrixRowHeight = 9.dp
private val MonthMatrixRowSpacing = 1.dp

private const val CELEBRATION_STAGGER_MILLIS = 65f
private const val CELEBRATION_RISE_MILLIS = 180f
private const val CELEBRATION_FALL_MILLIS = 620f
private const val CELEBRATION_CELL_MILLIS =
    CELEBRATION_RISE_MILLIS + CELEBRATION_FALL_MILLIS
private const val CELEBRATION_TOTAL_MILLIS = 1_190

private const val HABITS_HEADER_KEY = "habits_header"
private const val HABITS_CONTROLS_KEY = "habits_controls"
private const val HABITS_MATRIX_KEY = "habits_matrix"
private const val HABITS_STREAKS_HEADER_KEY = "habits_streaks_header"
private const val HABIT_STREAK_CONTENT_TYPE = "habit_streak"
