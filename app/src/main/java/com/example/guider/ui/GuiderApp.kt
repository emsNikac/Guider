package com.example.guider.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.example.guider.R
import com.example.guider.models.DailyTask
import com.example.guider.models.TaskCategory
import com.example.guider.ui.components.GuiderBottomBar
import com.example.guider.ui.screens.AddTaskDialog
import com.example.guider.ui.screens.DailyTasksScreen
import com.example.guider.ui.screens.FeatureOverviewScreen
import com.example.guider.ui.screens.sleep.SleepCalculatorRoute

enum class GuiderDestination(
    val label: String,
    @DrawableRes val iconRes: Int,
) {
    DAILY_TASKS("Daily tasks", R.drawable.tasks_nav_ic),
    SLEEP("Sleep calculator", R.drawable.sleep_nav_ic),
    HABITS("Habits", R.drawable.habits_nav_ic),
    BIGGER_GOALS("Bigger goals", R.drawable.calendar_nav_ic),
    MONEY("Money management", R.drawable.money_nav_ic),
}

private val starterTasks = listOf(
    DailyTask(1, TaskCategory.HEALTH, "Drink water", false),
    DailyTask(2, TaskCategory.HEALTH, "Take a 30 minute walk", true),
    DailyTask(3, TaskCategory.WORK, "Write the project outline", false),
    DailyTask(4, TaskCategory.MENTAL_HEALTH, "Meditate for 10 minutes", false),
    DailyTask(5, TaskCategory.MENTAL_HEALTH, "Read before bed", false),
    DailyTask(6, TaskCategory.OTHER, "Call family", true),
)

@Composable
fun GuiderApp(
    destinationRequest: GuiderDestination? = null,
    onDestinationRequestConsumed: () -> Unit = {},
) {
    var selectedDestination by remember { mutableStateOf(GuiderDestination.DAILY_TASKS) }
    var selectedCategory by remember { mutableStateOf<TaskCategory?>(null) }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var nextTaskId by remember { mutableIntStateOf(starterTasks.size + 1) }
    val tasks = remember { mutableStateListOf<DailyTask>().apply { addAll(starterTasks) } }

    LaunchedEffect(destinationRequest) {
        destinationRequest?.let {
            selectedDestination = it
            onDestinationRequestConsumed()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            GuiderBottomBar(
                selectedDestination = selectedDestination,
                onDestinationSelected = { selectedDestination = it },
            )
        },
        floatingActionButton = {
            if (selectedDestination == GuiderDestination.DAILY_TASKS) {
                FloatingActionButton(
                    onClick = { showAddTaskDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.add_ic),
                        contentDescription = "Add daily task",
                    )
                }
            }
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = selectedDestination,
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(tween(280)) { direction * it / 3 } + fadeIn(tween(220)))
                    .togetherWith(
                        slideOutHorizontally(tween(280)) { -direction * it / 3 } + fadeOut(tween(180)),
                    )
            },
            label = "Main destination",
        ) { destination ->
            DestinationContent(
                destination = destination,
                contentPadding = innerPadding,
                tasks = tasks,
                selectedCategory = selectedCategory,
                onCategorySelected = { category ->
                    selectedCategory = if (selectedCategory == category) null else category
                },
                onTaskCheckedChange = { id, isFinished ->
                    val index = tasks.indexOfFirst { it.id == id }
                    if (index >= 0) {
                        tasks[index] = tasks[index].copy(isFinished = isFinished)
                    }
                },
            )
        }
    }

    if (showAddTaskDialog) {
        AddTaskDialog(
            onDismiss = { showAddTaskDialog = false },
            onAddTask = { title, category ->
                tasks += DailyTask(
                    id = nextTaskId++,
                    taskCategory = category,
                    title = title,
                    isFinished = false,
                )
                selectedCategory = null
                showAddTaskDialog = false
            },
        )
    }
}

@Composable
private fun DestinationContent(
    destination: GuiderDestination,
    contentPadding: PaddingValues,
    tasks: List<DailyTask>,
    selectedCategory: TaskCategory?,
    onCategorySelected: (TaskCategory) -> Unit,
    onTaskCheckedChange: (Int, Boolean) -> Unit,
) {
    val modifier = Modifier
        .padding(contentPadding)
        .consumeWindowInsets(contentPadding)

    when (destination) {
        GuiderDestination.DAILY_TASKS -> DailyTasksScreen(
            tasks = tasks,
            selectedCategory = selectedCategory,
            onCategorySelected = onCategorySelected,
            onTaskCheckedChange = onTaskCheckedChange,
            modifier = modifier,
        )

        GuiderDestination.SLEEP -> SleepCalculatorRoute(modifier = modifier)

        GuiderDestination.HABITS -> FeatureOverviewScreen(
            title = "Habits",
            subtitle = "Turn small routines into steady progress.",
            cardTitle = "Build consistency gently",
            cardBody = "Your repeatable habits, streaks, and simple weekly check-ins will live here.",
            features = listOf("Daily habit check-ins", "Flexible schedules", "Progress and streaks"),
            iconRes = destination.iconRes,
            modifier = modifier,
        )

        GuiderDestination.BIGGER_GOALS -> FeatureOverviewScreen(
            title = "Bigger goals",
            subtitle = "Break long-term plans into achievable milestones.",
            cardTitle = "Make the big picture manageable",
            cardBody = "This space will connect meaningful goals to milestones and the next useful action.",
            features = listOf("Goal milestones", "Target dates", "Progress overview"),
            iconRes = destination.iconRes,
            modifier = modifier,
        )

        GuiderDestination.MONEY -> FeatureOverviewScreen(
            title = "Money management",
            subtitle = "Keep everyday spending and saving easy to understand.",
            cardTitle = "Know where your money goes",
            cardBody = "Budgets, transactions, and savings goals will come together in one clear view.",
            features = listOf("Simple budgets", "Expense categories", "Savings goals"),
            iconRes = destination.iconRes,
            modifier = modifier,
        )
    }
}
