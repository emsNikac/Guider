package com.nikac.guider.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nikac.guider.models.DailyTask
import com.nikac.guider.R
import com.nikac.guider.models.TaskCategory
import com.nikac.guider.domain.goals.Goal
import com.nikac.guider.ui.components.NavigationPillListBottomPadding
import com.nikac.guider.ui.components.navigationPillScrollEffect
import com.nikac.guider.ui.theme.taskCategoryPalette
import com.nikac.guider.domain.collections.ImmutableListSnapshot
import com.nikac.guider.domain.collections.ImmutableMapSnapshot
import com.nikac.guider.domain.collections.toImmutableSnapshot
import com.nikac.guider.util.LocalizedFormatters
import java.util.Locale

@Composable
fun DailyTasksScreen(
    tasks: ImmutableListSnapshot<DailyTask>,
    selectedCategory: TaskCategory?,
    onCategorySelected: (TaskCategory) -> Unit,
    onTaskCheckedChange: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    goalTitlesById: ImmutableMapSnapshot<Long, String> = ImmutableMapSnapshot(emptyMap()),
    onOpenSettings: () -> Unit = {},
) {
    val filteredTasks = remember(tasks, selectedCategory) {
        selectedCategory?.let { category ->
            tasks.filter { it.taskCategory == category }
        } ?: tasks
    }
    val completeCount = remember(tasks) { tasks.count { it.isFinished } }
    val filteredCompleteCount = remember(filteredTasks) {
        filteredTasks.count { it.isFinished }
    }
    val taskCountsByCategory = remember(tasks) {
        tasks.groupingBy(DailyTask::taskCategory).eachCount().toImmutableSnapshot()
    }
    val completionSummary = remember(filteredCompleteCount, filteredTasks.size) {
        "$filteredCompleteCount of ${filteredTasks.size} complete"
    }
    val taskListState = rememberLazyListState()
    val dateLabel = remember {
        LocalizedFormatters.formatDate("EEEE, MMMM d", System.currentTimeMillis())
    }

    LaunchedEffect(selectedCategory) {
        taskListState.scrollToItem(0)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(start = 24.dp, top = 12.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DailyHeader(dateLabel = dateLabel, onOpenSettings = onOpenSettings)
            CompactProgressRow(
                completeCount = completeCount,
                totalCount = tasks.size,
            )
            CategorySection(
                taskCountsByCategory = taskCountsByCategory,
                selectedCategory = selectedCategory,
                onCategorySelected = onCategorySelected,
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(start = 24.dp, top = 12.dp, end = 24.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        SectionTitle(
            title = selectedCategory?.displayName ?: "Today's list",
            supportingText = completionSummary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
        )

        LazyColumn(
            state = taskListState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .navigationPillScrollEffect(),
            contentPadding = PaddingValues(
                start = 24.dp,
                top = 5.dp,
                end = 24.dp,
                bottom = NavigationPillListBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (filteredTasks.isEmpty()) {
                item(key = DAILY_TASKS_EMPTY_KEY) {
                    EmptyTaskCard(category = selectedCategory)
                }
            } else {
                items(
                    items = filteredTasks,
                    key = { it.id },
                    contentType = { DAILY_TASK_CONTENT_TYPE },
                ) { task ->
                    DailyTaskCard(
                        task = task,
                        linkedGoalTitle = task.linkedGoalId?.let(goalTitlesById::get),
                        onCheckedChange = { onTaskCheckedChange(task.id, it) },
                    )
                }
            }
        }
    }
}

private const val DAILY_TASKS_EMPTY_KEY = "daily_tasks_empty"
private const val DAILY_TASK_CONTENT_TYPE = "daily_task"

@Composable
private fun DailyHeader(dateLabel: String, onOpenSettings: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        // Let the button span the header instead of forcing the date into a 48 dp row.
        Column(
            modifier = Modifier.padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = dateLabel,
                modifier = Modifier.padding(end = 56.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = "Daily tasks",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "One thing at a time",
                    modifier = Modifier.padding(bottom = 3.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.TopEnd).size(48.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.settings_ic),
                contentDescription = stringResource(R.string.settings_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CompactProgressRow(
    completeCount: Int,
    totalCount: Int,
) {
    val progress = if (totalCount == 0) 0f else completeCount.toFloat() / totalCount
    val animatedProgress = animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "Today's task progress",
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Today's progress",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "$completeCount / $totalCount",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            LinearProgressIndicator(
                progress = { animatedProgress.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun CategorySection(
    taskCountsByCategory: ImmutableMapSnapshot<TaskCategory, Int>,
    selectedCategory: TaskCategory?,
    onCategorySelected: (TaskCategory) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        SectionTitle(
            title = "Categories",
            supportingText = selectedCategory?.let { "Tap again to show all" },
        )
        TaskCategoryRows.forEach { rowCategories ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowCategories.forEach { category ->
                    CategoryTile(
                        category = category,
                        taskCount = taskCountsByCategory[category] ?: 0,
                        selected = selectedCategory == category,
                        onClick = { onCategorySelected(category) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowCategories.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(
    category: TaskCategory,
    taskCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = taskCategoryPalette(category)
    Card(
        onClick = onClick,
        modifier = modifier.height(62.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = palette.container,
            contentColor = palette.content,
        ),
        border = if (selected) BorderStroke(2.dp, palette.content) else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(category.iconRes),
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = palette.content,
            )
            Text(
                text = taskCount.toString(),
                modifier = Modifier.padding(start = 10.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = category.displayName,
                modifier = Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DailyTaskCard(
    task: DailyTask,
    linkedGoalTitle: String?,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bodyLarge = MaterialTheme.typography.bodyLarge
    val titleStyle = remember(bodyLarge) {
        bodyLarge.copy(fontWeight = FontWeight.Medium)
    }
    ElevatedCard(
        onClick = { onCheckedChange(!task.isFinished) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = task.isFinished,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = task.title,
                    style = titleStyle,
                    color = if (task.isFinished) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (task.isFinished) TextDecoration.LineThrough else null,
                )
                CategoryBadge(category = task.taskCategory)
                linkedGoalTitle?.let { title ->
                    Text(
                        text = "Goal · $title",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryBadge(category: TaskCategory) {
    val palette = taskCategoryPalette(category)
    val uppercaseName = remember(category) {
        category.displayName.uppercase(Locale.getDefault())
    }
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = palette.container,
        contentColor = palette.content,
    ) {
        Text(
            text = uppercaseName,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun EmptyTaskCard(category: TaskCategory?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "No ${category?.displayName?.lowercase() ?: "daily"} tasks yet",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Use the add button to create one.",
                modifier = Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
        supportingText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onAddTask: (String, TaskCategory, Long?) -> Unit,
    availableGoals: ImmutableListSnapshot<Goal> = ImmutableListSnapshot(emptyList()),
    initialLinkedGoalId: Long? = null,
    goalSelectionEnabled: Boolean = true,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(TaskCategory.HEALTH) }
    var linkedGoalId by rememberSaveable(initialLinkedGoalId) {
        mutableStateOf(initialLinkedGoalId)
    }
    val initialGoalTitle = remember(availableGoals, initialLinkedGoalId) {
        availableGoals.firstOrNull { it.id == initialLinkedGoalId }?.title
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialLinkedGoalId == null) "Add a daily task" else "Add a goal task")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (!goalSelectionEnabled && initialGoalTitle != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Text(
                            text = "Linked to $initialGoalTitle",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                androidx.compose.material3.OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Task name") },
                    singleLine = true,
                )
                if (goalSelectionEnabled) {
                    GoalLinkSelector(
                        goals = availableGoals,
                        selectedGoalId = linkedGoalId,
                        onSelected = { linkedGoalId = it },
                    )
                }
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelLarge,
                )
                TaskCategoryRows.forEach { categories ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        categories.forEach { option ->
                            CategoryChoice(
                                category = option,
                                selected = category == option,
                                onClick = { category = option },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = { onAddTask(title.trim(), category, linkedGoalId) },
                enabled = title.isNotBlank(),
            ) {
                Text("Add task")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun GoalLinkSelector(
    goals: ImmutableListSnapshot<Goal>,
    selectedGoalId: Long?,
    onSelected: (Long?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("Linked goal (optional)", style = MaterialTheme.typography.labelLarge)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            item(key = NO_GOAL_CHOICE_KEY, contentType = GOAL_LINK_CONTENT_TYPE) {
                GoalLinkChoice(
                    title = "No goal",
                    selected = selectedGoalId == null,
                    onClick = { onSelected(null) },
                )
            }
            items(
                items = goals,
                key = Goal::id,
                contentType = { GOAL_LINK_CONTENT_TYPE },
            ) { goal ->
                GoalLinkChoice(
                    title = goal.title,
                    selected = selectedGoalId == goal.id,
                    onClick = { onSelected(goal.id) },
                )
            }
        }
        if (goals.isEmpty()) {
            Text(
                text = "Create an active goal to link future tasks.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val TaskCategoryRows = TaskCategory.entries.chunked(2)
private const val NO_GOAL_CHOICE_KEY = "no_goal"
private const val GOAL_LINK_CONTENT_TYPE = "goal_link_choice"

@Composable
private fun GoalLinkChoice(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

@Composable
private fun CategoryChoice(
    category: TaskCategory,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = taskCategoryPalette(category)
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = palette.container,
        contentColor = palette.content,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) palette.content else palette.content.copy(alpha = 0.24f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(category.iconRes),
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = palette.content,
            )
            Text(
                text = category.displayName,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selected) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
