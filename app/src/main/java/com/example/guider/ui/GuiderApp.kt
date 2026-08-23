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
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.guider.R
import com.example.guider.models.DailyTask
import com.example.guider.models.TaskCategory
import com.example.guider.domain.goals.Goal
import com.example.guider.domain.goals.isActive
import com.example.guider.domain.time.DayKeys
import com.example.guider.ui.components.GuiderBottomBar
import com.example.guider.ui.screens.AddTaskDialog
import com.example.guider.ui.screens.DailyTasksScreen
import com.example.guider.ui.screens.FeatureOverviewScreen
import com.example.guider.ui.screens.goals.GoalsRoute
import com.example.guider.ui.screens.habits.HabitsRoute
import com.example.guider.ui.screens.sleep.SleepCalculatorRoute
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

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

@Composable
fun GuiderApp(
    destinationRequest: GuiderDestination? = null,
    onDestinationRequestConsumed: () -> Unit = {},
    viewModel: GuiderViewModel = viewModel(),
) {
    var selectedDestination by remember { mutableStateOf(GuiderDestination.DAILY_TASKS) }
    var selectedCategory by remember { mutableStateOf<TaskCategory?>(null) }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    val tasks by viewModel.tasks.collectAsState()
    val goals by viewModel.goals.collectAsState()

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshDayBoundContent()
            delay(DayKeys.millisUntilTomorrow() + 1_000L)
        }
    }

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
                    viewModel.setTaskFinished(id, isFinished)
                },
                goals = goals,
            )
        }
    }

    if (showAddTaskDialog) {
        AddTaskDialog(
            onDismiss = { showAddTaskDialog = false },
            availableGoals = goals.filter { it.isActive(DayKeys.today()) },
            onAddTask = { title, category, linkedGoalId ->
                viewModel.addTask(title, category, linkedGoalId)
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
    goals: List<Goal>,
    selectedCategory: TaskCategory?,
    onCategorySelected: (TaskCategory) -> Unit,
    onTaskCheckedChange: (Long, Boolean) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val appliedPadding = PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection),
        top = contentPadding.calculateTopPadding(),
        end = contentPadding.calculateEndPadding(layoutDirection),
        bottom = 0.dp,
    )
    val modifier = Modifier
        .padding(appliedPadding)
        .consumeWindowInsets(appliedPadding)

    when (destination) {
        GuiderDestination.DAILY_TASKS -> DailyTasksScreen(
            tasks = tasks,
            selectedCategory = selectedCategory,
            onCategorySelected = onCategorySelected,
            onTaskCheckedChange = onTaskCheckedChange,
            goalTitlesById = goals.associate { it.id to it.title },
            modifier = modifier,
        )

        GuiderDestination.SLEEP -> SleepCalculatorRoute(modifier = modifier)

        GuiderDestination.HABITS -> HabitsRoute(modifier = modifier)

        GuiderDestination.BIGGER_GOALS -> GoalsRoute(modifier = modifier)

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
