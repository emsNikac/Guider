package com.example.guider.ui.screens.goals

import android.app.DatePickerDialog
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.guider.domain.goals.Goal
import com.example.guider.domain.goals.GoalHabitInput
import com.example.guider.domain.goals.GoalProgress
import com.example.guider.domain.goals.GoalType
import com.example.guider.domain.goals.isActive
import com.example.guider.domain.habits.Habit
import com.example.guider.domain.habits.HabitWeekday
import com.example.guider.domain.time.DayKeys
import com.example.guider.models.TaskCategory
import com.example.guider.ui.components.NavigationPillListBottomPadding
import com.example.guider.ui.components.navigationPillScrollEffect
import com.example.guider.util.LocalizedFormatters
import com.example.guider.ui.screens.AddTaskDialog
import com.example.guider.ui.util.ImmutableListSnapshot
import com.example.guider.R

@Composable
fun GoalsRoute(
    modifier: Modifier = Modifier,
    viewModel: GoalsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    GoalsScreen(
        uiState = uiState,
        onAddGoal = viewModel::addGoal,
        onToggleAchievement = viewModel::toggleAchievement,
        onAddDailyTask = viewModel::addDailyTask,
        onDeleteGoal = viewModel::deleteGoal,
        modifier = modifier,
    )
}

@Composable
private fun GoalsScreen(
    uiState: GoalsUiState,
    onAddGoal: (String, GoalType, List<GoalHabitInput>, Int?, Int?) -> Unit,
    onToggleAchievement: (Long) -> Unit,
    onAddDailyTask: (Long, String, TaskCategory) -> Unit,
    onDeleteGoal: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val goals = uiState.goals
    var showAddGoalDialog by rememberSaveable { mutableStateOf(false) }
    var taskGoal by remember { mutableStateOf<Goal?>(null) }
    var goalPendingDeletion by remember { mutableStateOf<Goal?>(null) }
    val oneTimeGoals = uiState.oneTimeGoals
    val periodicGoals = uiState.periodicGoals
    val linkedHabits = uiState.linkedHabits
    val periodicProgress = uiState.periodicProgress

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
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = GOALS_HEADER_KEY) {
            GoalsHeader(onAddGoal = { showAddGoalDialog = true })
        }

        item(key = GOALS_OVERVIEW_KEY) {
            GoalsOverview(
                activeGoalCount = uiState.activeGoalCount,
            )
        }

        if (goals.isEmpty()) {
            item(key = GOALS_EMPTY_KEY) {
                EmptyGoalsCard(onAddGoal = { showAddGoalDialog = true })
            }
        } else {
            item(key = ONE_TIME_HEADER_KEY) {
                GoalSectionTitle(
                    title = "One-time goals",
                    supportingText = "${uiState.activeOneTimeGoalCount} active",
                )
            }
            if (oneTimeGoals.isEmpty()) {
                item(key = ONE_TIME_EMPTY_KEY) {
                    EmptyGoalTypeRow("No one-time goals yet")
                }
            } else {
                items(
                    items = oneTimeGoals,
                    key = { "one_time_${it.id}" },
                    contentType = { ONE_TIME_GOAL_CONTENT_TYPE },
                ) { goal ->
                    OneTimeGoalCard(
                        goal = goal,
                        onToggle = { onToggleAchievement(goal.id) },
                        onDelete = { goalPendingDeletion = goal },
                    )
                }
            }

            item(key = PERIODIC_HEADER_KEY) {
                GoalSectionTitle(
                    title = "Periodic goals",
                    supportingText = "${uiState.activePeriodicGoalCount} active",
                )
            }
            if (periodicGoals.isEmpty()) {
                item(key = PERIODIC_EMPTY_KEY) {
                    EmptyGoalTypeRow("No periodic goals yet")
                }
            } else {
                items(
                    items = periodicGoals,
                    key = { "periodic_${it.id}" },
                    contentType = { PERIODIC_GOAL_CONTENT_TYPE },
                ) { goal ->
                    PeriodicGoalCard(
                        goal = goal,
                        habits = linkedHabits[goal.id] ?: EmptyHabitSnapshot,
                        progress = periodicProgress.getValue(goal.id),
                        onAddDailyTask = { taskGoal = goal },
                        onDelete = { goalPendingDeletion = goal },
                    )
                }
            }
        }
    }

    if (showAddGoalDialog) {
        AddGoalDialog(
            onDismiss = { showAddGoalDialog = false },
            onCreate = { title, type, habitInputs, startDayKey, endDayKey ->
                onAddGoal(title, type, habitInputs, startDayKey, endDayKey)
                showAddGoalDialog = false
            },
        )
    }

    taskGoal?.let { goal ->
        AddTaskDialog(
            availableGoals = ImmutableListSnapshot(listOf(goal)),
            initialLinkedGoalId = goal.id,
            goalSelectionEnabled = false,
            onDismiss = { taskGoal = null },
            onAddTask = { title, category, _ ->
                onAddDailyTask(goal.id, title, category)
                taskGoal = null
            },
        )
    }

    goalPendingDeletion?.let { goal ->
        DeleteGoalDialog(
            goal = goal,
            linkedHabitCount = linkedHabits[goal.id].orEmpty().size,
            onDismiss = { goalPendingDeletion = null },
            onConfirm = {
                onDeleteGoal(goal.id)
                goalPendingDeletion = null
            },
        )
    }
}

@Composable
private fun GoalsHeader(onAddGoal: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Bigger goals",
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = "Turn direction into repeatable action.",
                modifier = Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onAddGoal) {
            Text("Add goal")
        }
    }
}

@Composable
private fun GoalsOverview(
    activeGoalCount: Int,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp)) {
            Text(text = activeGoalCount.toString(), style = MaterialTheme.typography.headlineSmall)
            Text(
                text = if (activeGoalCount == 1) "active goal" else "active goals",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
            )
        }
    }
}

@Composable
private fun GoalSectionTitle(
    title: String,
    supportingText: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = supportingText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OneTimeGoalCard(
    goal: Goal,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val achieved = goal.achievedDayKey != null
    ElevatedCard(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (achieved) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = achieved,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 7.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = goal.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (achieved) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (achieved) TextDecoration.LineThrough else null,
                )
                Text(
                    text = if (achieved) {
                        "Achieved · clears at the end of today"
                    } else {
                        "Unachieved · tap when complete"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (achieved) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            GoalTypeBadge(type = GoalType.ONE_TIME)
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.delete_ic),
                    contentDescription = "Delete ${goal.title}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun PeriodicGoalCard(
    goal: Goal,
    habits: ImmutableListSnapshot<Habit>,
    progress: GoalProgress,
    onAddDailyTask: () -> Unit,
    onDelete: () -> Unit,
) {
    val todayDayKey = remember { DayKeys.today() }
    val isActive = remember(goal, todayDayKey) { goal.isActive(todayDayKey) }
    val periodLabel = remember(goal, todayDayKey) { goalPeriodLabel(goal, todayDayKey) }
    val animatedProgress by animateFloatAsState(
        targetValue = progress.fraction,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "${goal.title} consistency",
    )

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = goal.title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (progress.expectedCheckIns == 0) {
                            "No scheduled check-ins in this period"
                        } else {
                            "${progress.completedCheckIns} of ${progress.expectedCheckIns} total check-ins"
                        },
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GoalTypeBadge(type = GoalType.PERIODIC)
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(R.drawable.delete_ic),
                        contentDescription = "Delete ${goal.title}",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Text(
                text = periodLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .weight(1f)
                        .height(7.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Text(
                    text = "${progress.percentage}%",
                    modifier = Modifier.padding(start = 10.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    text = "Goal habits",
                    style = MaterialTheme.typography.labelLarge,
                )
                if (habits.isEmpty()) {
                    Text(
                        text = "No linked habits remain.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    habits.forEach { habit -> LinkedHabitRow(habit) }
                }
            }
            OutlinedButton(
                onClick = onAddDailyTask,
                modifier = Modifier.fillMaxWidth(),
                enabled = isActive,
            ) {
                Text(
                    if (isActive) {
                        "Add a daily task for this goal"
                    } else {
                        "Goal period ended"
                    },
                )
            }
        }
    }
}

@Composable
private fun LinkedHabitRow(habit: Habit) {
    val schedule = remember(habit.scheduledWeekdays) {
        scheduleLabel(habit.scheduledWeekdays)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Text(
                text = habit.name,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = schedule,
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun GoalTypeBadge(type: GoalType) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (type == GoalType.PERIODIC) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (type == GoalType.PERIODIC) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
    ) {
        Text(
            text = type.label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun EmptyGoalsCard(onAddGoal: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text("What are you moving toward?", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Create a one-time finish line or a periodic goal powered by habits.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onAddGoal,
                modifier = Modifier.padding(top = 5.dp),
            ) {
                Text("Create your first goal")
            }
        }
    }
}

@Composable
private fun EmptyGoalTypeRow(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class HabitDraft(
    val key: Int,
    val name: String = "",
    val weekdays: Set<HabitWeekday> = HabitWeekday.entries.toSet(),
)

@Composable
private fun AddGoalDialog(
    onDismiss: () -> Unit,
    onCreate: (String, GoalType, List<GoalHabitInput>, Int?, Int?) -> Unit,
) {
    val todayDayKey = remember { DayKeys.today() }
    var title by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(GoalType.ONE_TIME) }
    var startDayKey by rememberSaveable { mutableIntStateOf(todayDayKey) }
    var endDayKey by rememberSaveable {
        mutableIntStateOf(DayKeys.addDays(todayDayKey, DEFAULT_GOAL_PERIOD_DAYS - 1))
    }
    val habitDrafts = remember { mutableStateListOf(HabitDraft(key = 0)) }
    var nextDraftKey by remember { mutableIntStateOf(1) }
    val valid = title.isNotBlank() && (
        type == GoalType.ONE_TIME || habitDrafts.all {
            it.name.isNotBlank() && it.weekdays.isNotEmpty()
        }
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(vertical = 22.dp)) {
                Text(
                    text = "Create a bigger goal",
                    modifier = Modifier.padding(horizontal = 22.dp),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "Choose a finish line or build consistency through scheduled habits.",
                    modifier = Modifier.padding(start = 22.dp, top = 4.dp, end = 22.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(top = 16.dp),
                    contentPadding = PaddingValues(horizontal = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(key = "goal_type") {
                        GoalTypeSelector(
                            selected = type,
                            onSelected = { type = it },
                        )
                    }
                    item(key = "goal_title") {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Goal name") },
                            placeholder = {
                                Text(if (type == GoalType.PERIODIC) "Gain muscle" else "Finish my portfolio")
                            },
                            singleLine = true,
                        )
                    }

                    if (type == GoalType.PERIODIC) {
                        item(key = "goal_period") {
                            GoalPeriodSelector(
                                startDayKey = startDayKey,
                                endDayKey = endDayKey,
                                onStartSelected = { selectedDayKey ->
                                    startDayKey = selectedDayKey
                                    if (endDayKey < selectedDayKey) endDayKey = selectedDayKey
                                },
                                onEndSelected = { endDayKey = it },
                            )
                        }
                        item(key = "habit_intro") {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Goal habits", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "These appear in Habits automatically. Pick the days each one is available.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(
                            items = habitDrafts,
                            key = HabitDraft::key,
                        ) { draft ->
                            HabitDraftEditor(
                                draft = draft,
                                canRemove = habitDrafts.size > 1,
                                onChanged = { updated ->
                                    val index = habitDrafts.indexOfFirst { it.key == updated.key }
                                    if (index >= 0) habitDrafts[index] = updated
                                },
                                onRemove = { habitDrafts.removeAll { it.key == draft.key } },
                            )
                        }
                        item(key = "add_habit") {
                            TextButton(
                                onClick = {
                                    habitDrafts += HabitDraft(key = nextDraftKey++)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("+ Add another habit")
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, top = 16.dp, end = 22.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = {
                            onCreate(
                                title.trim(),
                                type,
                                if (type == GoalType.PERIODIC) {
                                    habitDrafts.map {
                                        GoalHabitInput(it.name.trim(), it.weekdays)
                                    }
                                } else {
                                    emptyList()
                                },
                                startDayKey.takeIf { type == GoalType.PERIODIC },
                                endDayKey.takeIf { type == GoalType.PERIODIC },
                            )
                        },
                        enabled = valid,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text("Create goal")
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalTypeSelector(
    selected: GoalType,
    onSelected: (GoalType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("Goal type", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GoalType.entries.forEach { type ->
                val isSelected = selected == type
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(78.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .clickable { onSelected(type) }
                        .semantics {
                            this.selected = isSelected
                            role = Role.RadioButton
                        },
                    shape = RoundedCornerShape(13.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(type.label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (type == GoalType.ONE_TIME) {
                                "Achieved or unachieved"
                            } else {
                                "Measured by habits"
                            },
                            modifier = Modifier.padding(top = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalPeriodSelector(
    startDayKey: Int,
    endDayKey: Int,
    onStartSelected: (Int) -> Unit,
    onEndSelected: (Int) -> Unit,
) {
    val context = LocalContext.current
    val durationDays = remember(startDayKey, endDayKey) {
        DayKeys.inclusiveDayCount(startDayKey, endDayKey)
    }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("Goal period", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Every scheduled habit occurrence in this range contributes equally.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DateChoice(
                label = "Starts",
                dayKey = startDayKey,
                onClick = {
                    showDatePicker(
                        context = context,
                        initialDayKey = startDayKey,
                        onSelected = onStartSelected,
                    )
                },
                modifier = Modifier.weight(1f),
            )
            DateChoice(
                label = "Ends",
                dayKey = endDayKey,
                onClick = {
                    showDatePicker(
                        context = context,
                        initialDayKey = endDayKey,
                        minimumDayKey = startDayKey,
                        onSelected = onEndSelected,
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "$durationDays days total",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun DateChoice(
    label: String,
    dayKey: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formattedDay = remember(dayKey) { formatDayKey(dayKey) }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(62.dp),
        shape = RoundedCornerShape(13.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(formattedDay, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun showDatePicker(
    context: android.content.Context,
    initialDayKey: Int,
    minimumDayKey: Int? = null,
    onSelected: (Int) -> Unit,
) {
    val year = initialDayKey / 10_000
    val monthIndex = initialDayKey / 100 % 100 - 1
    val dayOfMonth = initialDayKey % 100
    DatePickerDialog(
        context,
        { _, selectedYear, selectedMonthIndex, selectedDayOfMonth ->
            onSelected(
                selectedYear * 10_000 +
                    (selectedMonthIndex + 1) * 100 +
                    selectedDayOfMonth,
            )
        },
        year,
        monthIndex,
        dayOfMonth,
    ).apply {
        minimumDayKey?.let { datePicker.minDate = DayKeys.toEpochMillis(it) }
    }.show()
}

@Composable
private fun HabitDraftEditor(
    draft: HabitDraft,
    canRemove: Boolean,
    onChanged: (HabitDraft) -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Habit",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                )
                if (canRemove) {
                    TextButton(onClick = onRemove) { Text("Remove") }
                }
            }
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onChanged(draft.copy(name = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Habit name") },
                placeholder = { Text("Go to the gym") },
                singleLine = true,
            )
            Text("Scheduled days", style = MaterialTheme.typography.labelMedium)
            WeekdaySelector(
                selected = draft.weekdays,
                onToggle = { weekday ->
                    val updated = draft.weekdays.toMutableSet().apply {
                        if (!add(weekday)) remove(weekday)
                    }
                    onChanged(draft.copy(weekdays = updated))
                },
            )
            if (draft.weekdays.isEmpty()) {
                Text(
                    text = "Choose at least one day.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun WeekdaySelector(
    selected: Set<HabitWeekday>,
    onToggle: (HabitWeekday) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HabitWeekday.entries.forEach { weekday ->
            val isSelected = weekday in selected
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .clickable { onToggle(weekday) }
                    .semantics {
                        contentDescription = weekday.name.lowercase().replaceFirstChar(Char::uppercase)
                        this.selected = isSelected
                        role = Role.Checkbox
                    },
                shape = CircleShape,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                Box(
                    modifier = Modifier.height(34.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(weekday.shortLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private fun scheduleLabel(weekdays: Set<HabitWeekday>): String = when {
    weekdays.size == HabitWeekday.entries.size -> "Every day"
    weekdays == WorkWeekdays -> "Weekdays"
    else -> weekdays
        .sortedBy(HabitWeekday::ordinal)
        .joinToString(" · ") { it.name.take(3).lowercase().replaceFirstChar(Char::uppercase) }
}

@Composable
private fun DeleteGoalDialog(
    goal: Goal,
    linkedHabitCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete goal?") },
        text = {
            Text(
                text = if (linkedHabitCount == 0) {
                    "Delete “${goal.title}”? Daily tasks already linked to it will be kept as regular tasks."
                } else {
                    "Delete “${goal.title}” and its $linkedHabitCount linked " +
                        (if (linkedHabitCount == 1) "habit? " else "habits? ") +
                        "Linked daily tasks will be kept as regular tasks."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun goalPeriodLabel(goal: Goal, todayDayKey: Int): String {
    val startDayKey = goal.startDayKey ?: goal.createdDayKey
    val endDayKey = goal.endDayKey ?: startDayKey
    val status = if (endDayKey < todayDayKey) " · Ended" else ""
    return "${formatDayKey(startDayKey)} – ${formatDayKey(endDayKey)} · " +
        "${DayKeys.inclusiveDayCount(startDayKey, endDayKey)} days$status"
}

private fun formatDayKey(dayKey: Int): String =
    LocalizedFormatters.formatDate("MMM d, yyyy", DayKeys.toEpochMillis(dayKey))

private const val GOALS_HEADER_KEY = "goals_header"
private const val GOALS_OVERVIEW_KEY = "goals_overview"
private const val GOALS_EMPTY_KEY = "goals_empty"
private const val ONE_TIME_HEADER_KEY = "one_time_header"
private const val ONE_TIME_EMPTY_KEY = "one_time_empty"
private const val PERIODIC_HEADER_KEY = "periodic_header"
private const val PERIODIC_EMPTY_KEY = "periodic_empty"
private const val ONE_TIME_GOAL_CONTENT_TYPE = "one_time_goal"
private const val PERIODIC_GOAL_CONTENT_TYPE = "periodic_goal"
private const val DEFAULT_GOAL_PERIOD_DAYS = 14
private val WorkWeekdays = HabitWeekday.entries.take(5).toSet()
private val EmptyHabitSnapshot = ImmutableListSnapshot<Habit>(emptyList())
