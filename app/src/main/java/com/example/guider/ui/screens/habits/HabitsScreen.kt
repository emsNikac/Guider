package com.example.guider.ui.screens.habits

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Path
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
import com.example.guider.domain.collections.ImmutableListSnapshot
import com.example.guider.domain.collections.ImmutableMapSnapshot
import com.example.guider.domain.collections.toImmutableSnapshot

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
    streaksByHabitId: ImmutableMapSnapshot<Long, Int>,
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
    val background = MaterialTheme.colorScheme.background
    val darkTheme = remember(background) { background.luminance() < 0.5f }
    val colorsByHabitId = remember(habits, darkTheme) {
        habits.associate { habit -> habit.id to habitColor(habit.colorHue, darkTheme) }
            .toImmutableSnapshot()
    }
    val dayScrollState = rememberScrollState()

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
        verticalArrangement = Arrangement.Top,
    ) {
        item(key = HABITS_HEADER_KEY) {
            SpacedListItem {
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
        }

        item(key = HABITS_CONTROLS_KEY) {
            SpacedListItem {
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
        }

        if (range == HabitTrackerRange.WEEK && habits.isNotEmpty()) {
            item(
                key = HABITS_MATRIX_HEADER_KEY,
                contentType = HABIT_MATRIX_HEADER_CONTENT_TYPE,
            ) {
                WeeklyHabitMatrixHeader(
                    days = period.days,
                    dayScrollState = dayScrollState,
                )
            }
            itemsIndexed(
                items = habits,
                key = { _, habit -> "habit_matrix_${habit.id}" },
                contentType = { _, _ -> HABIT_MATRIX_ROW_CONTENT_TYPE },
            ) { index, habit ->
                val isLast = index == habits.lastIndex
                WeeklyHabitMatrixRow(
                    habit = habit,
                    color = colorsByHabitId.getValue(habit.id),
                    days = period.days,
                    dayScrollState = dayScrollState,
                    isLast = isLast,
                    onToggleCompletion = onToggleCompletion,
                )
                if (isLast) Spacer(Modifier.height(ListItemSpacing))
            }
        } else {
            item(key = HABITS_MATRIX_KEY) {
                SpacedListItem {
                    StaticHabitMatrix(
                        habits = habits,
                        days = period.days,
                        range = range,
                        colorsByHabitId = colorsByHabitId,
                    )
                }
            }
        }

        item(key = HABITS_STREAKS_HEADER_KEY) {
            SpacedListItem(spacingAfter = habits.isNotEmpty()) {
                HabitStreaksHeader(
                    hasHabits = habits.isNotEmpty(),
                    onDeleteMenuRequested = { showDeletionPicker = true },
                )
            }
        }
        itemsIndexed(
            items = habits,
            key = { _, habit -> "habit_streak_${habit.id}" },
            contentType = { _, _ -> HABIT_STREAK_CONTENT_TYPE },
        ) { index, habit ->
            SpacedListItem(spacingAfter = index < habits.lastIndex) {
                HabitStreakCard(
                    habit = habit,
                    color = colorsByHabitId.getValue(habit.id),
                    streak = streaksByHabitId[habit.id] ?: 0,
                )
            }
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
            colorsByHabitId = colorsByHabitId,
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
private fun SpacedListItem(
    spacingAfter: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column {
        content()
        if (spacingAfter) Spacer(Modifier.height(ListItemSpacing))
    }
}

@Composable
private fun StaticHabitMatrix(
    habits: ImmutableListSnapshot<Habit>,
    days: ImmutableListSnapshot<HabitDay>,
    range: HabitTrackerRange,
    colorsByHabitId: ImmutableMapSnapshot<Long, Color>,
) {
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
            } else {
                CompactMonthHabitGrid(
                    habits = habits,
                    days = days,
                    colorsByHabitId = colorsByHabitId,
                )
            }
        }
    }
}

@Composable
private fun WeeklyHabitMatrixHeader(
    days: ImmutableListSnapshot<HabitDay>,
    dayScrollState: ScrollState,
) {
    LaunchedEffect(days.firstOrNull()?.key) {
        dayScrollState.scrollTo(0)
    }
    WeeklyMatrixSegment(first = true, last = false) {
        Column(modifier = Modifier.padding(top = 10.dp)) {
            Text(
                text = "Tap a day to record this week",
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp),
            ) {
                val dayColumnWidth =
                    ((maxWidth - WeeklyHabitNameColumnWidth) / 7).coerceAtLeast(26.dp)
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.width(WeeklyHabitNameColumnWidth)) {
                        MatrixCornerCell()
                    }
                    Row(modifier = Modifier.horizontalScroll(dayScrollState)) {
                        DayHeaderRow(days = days, dayColumnWidth = dayColumnWidth)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyHabitMatrixRow(
    habit: Habit,
    color: Color,
    days: ImmutableListSnapshot<HabitDay>,
    dayScrollState: ScrollState,
    isLast: Boolean,
    onToggleCompletion: (Long, Int) -> Unit,
) {
    WeeklyMatrixSegment(first = false, last = isLast) {
        Column {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val dayColumnWidth =
                    ((maxWidth - WeeklyHabitNameColumnWidth) / 7).coerceAtLeast(26.dp)
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.width(WeeklyHabitNameColumnWidth)) {
                        HabitNameCell(habit = habit, color = color)
                    }
                    Row(modifier = Modifier.horizontalScroll(dayScrollState)) {
                        HabitCompletionRow(
                            habit = habit,
                            color = color,
                            days = days,
                            dayColumnWidth = dayColumnWidth,
                            completionSize = WeeklyCompletionSize,
                            onToggleCompletion = onToggleCompletion,
                        )
                    }
                }
            }
            if (isLast) Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun WeeklyMatrixSegment(
    first: Boolean,
    last: Boolean,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(
        topStart = if (first) MatrixCornerRadius else 0.dp,
        topEnd = if (first) MatrixCornerRadius else 0.dp,
        bottomStart = if (last) MatrixCornerRadius else 0.dp,
        bottomEnd = if (last) MatrixCornerRadius else 0.dp,
    )
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundColor)
            .drawWithCache {
                val strokeWidth = 1.dp.toPx()
                val inset = strokeWidth / 2f
                val left = inset
                val top = inset
                val right = size.width - inset
                val bottom = size.height - inset
                val radius = (MatrixCornerRadius.toPx() - inset).coerceAtLeast(0f)
                val path = Path().apply {
                    if (first) {
                        moveTo(left, bottom)
                        lineTo(left, top + radius)
                        quadraticTo(left, top, left + radius, top)
                        lineTo(right - radius, top)
                        quadraticTo(right, top, right, top + radius)
                        lineTo(right, bottom)
                    } else {
                        moveTo(left, top)
                        lineTo(left, if (last) bottom - radius else bottom)
                        moveTo(right, top)
                        lineTo(right, if (last) bottom - radius else bottom)
                    }
                    if (last) {
                        moveTo(left, bottom - radius)
                        quadraticTo(left, bottom, left + radius, bottom)
                        lineTo(right - radius, bottom)
                        quadraticTo(right, bottom, right, bottom - radius)
                    }
                }
                onDrawBehind {
                    drawPath(path = path, color = borderColor, style = Stroke(strokeWidth))
                }
            },
    ) {
        content()
    }
}

@Composable
private fun CompactMonthHabitGrid(
    habits: ImmutableListSnapshot<Habit>,
    days: ImmutableListSnapshot<HabitDay>,
    colorsByHabitId: ImmutableMapSnapshot<Long, Color>,
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
                        val rowTops = FloatArray(habits.size) { habitIndex ->
                            habitIndex * rowStepPx + (rowHeightPx - squareSizePx) / 2f
                        }
                        val columnLefts = FloatArray(days.size) { dayIndex ->
                            dayIndex * columnWidthPx + (columnWidthPx - squareSizePx) / 2f
                        }
                        val cornerRadius = CornerRadius(2.dp.toPx())
                        val todayStroke = Stroke(width = 0.5.dp.toPx())
                        val borderInset = todayStroke.width / 2f
                        val borderCornerRadius = CornerRadius(
                            x = (cornerRadius.x - borderInset).coerceAtLeast(0f),
                            y = (cornerRadius.y - borderInset).coerceAtLeast(0f),
                        )
                        val unscheduledColor = outlineColor.copy(alpha = 0.12f)
                        val futureColor = outlineColor.copy(alpha = 0.18f)
                        val incompleteColor = outlineColor.copy(alpha = 0.42f)
                        val habitColors = Array(habits.size) { habitIndex ->
                            colorsByHabitId.getValue(habits[habitIndex].id)
                        }
                        val cellColors = Array(habits.size) { habitIndex ->
                            val habit = habits[habitIndex]
                            Array(days.size) { dayIndex ->
                                val day = days[dayIndex]
                                when {
                                    day.key in habit.completedDayKeys -> habitColors[habitIndex]
                                    !habit.isScheduledOn(day.key, day.weekday) -> unscheduledColor
                                    day.isFuture -> futureColor
                                    else -> incompleteColor
                                }
                            }
                        }
                        val todayIndex = days.indexOfFirst { day -> day.isToday }

                        onDrawBehind {
                            repeat(habits.size) { habitIndex ->
                                repeat(days.size) { dayIndex ->
                                    val cellTopLeft = Offset(
                                        x = columnLefts[dayIndex],
                                        y = rowTops[habitIndex],
                                    )
                                    drawRoundRect(
                                        color = cellColors[habitIndex][dayIndex],
                                        topLeft = cellTopLeft,
                                        size = Size(squareSizePx, squareSizePx),
                                        cornerRadius = cornerRadius,
                                    )
                                    if (dayIndex == todayIndex) {
                                        drawRoundRect(
                                            color = habitColors[habitIndex],
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
private fun HabitNameCell(habit: Habit, color: Color) {
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
    days: ImmutableListSnapshot<HabitDay>,
    dayColumnWidth: Dp,
) {
    Row {
        days.forEach { day ->
            Column(
                modifier = Modifier
                    .width(dayColumnWidth)
                    .height(MatrixHeaderHeight)
                    .clip(DayHeaderCellShape)
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
    color: Color,
    days: ImmutableListSnapshot<HabitDay>,
    dayColumnWidth: Dp,
    completionSize: Dp,
    onToggleCompletion: (Long, Int) -> Unit,
) {
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
    color: Color,
    streak: Int,
) {
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
    habits: ImmutableListSnapshot<Habit>,
    colorsByHabitId: ImmutableMapSnapshot<Long, Color>,
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
                    val color = colorsByHabitId.getValue(habit.id)
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
private val DayHeaderCellShape = RoundedCornerShape(10.dp)
private val ListItemSpacing = 16.dp
private val MatrixCornerRadius = 22.dp
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
private const val HABITS_MATRIX_HEADER_KEY = "habits_matrix_header"
private const val HABITS_STREAKS_HEADER_KEY = "habits_streaks_header"
private const val HABIT_MATRIX_HEADER_CONTENT_TYPE = "habit_matrix_header"
private const val HABIT_MATRIX_ROW_CONTENT_TYPE = "habit_matrix_row"
private const val HABIT_STREAK_CONTENT_TYPE = "habit_streak"
