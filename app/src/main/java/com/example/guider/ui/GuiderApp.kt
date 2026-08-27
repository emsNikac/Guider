package com.example.guider.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.guider.ui.screens.goals.GoalsRoute
import com.example.guider.ui.screens.habits.HabitsRoute
import com.example.guider.ui.screens.money.MoneyRoute
import com.example.guider.ui.screens.sleep.SleepCalculatorRoute
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    var selectedCategory by remember { mutableStateOf<TaskCategory?>(null) }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    val tasks by viewModel.tasks.collectAsState()
    val goals by viewModel.goals.collectAsState()
    val destinations = GuiderDestination.entries
    val pagerState = rememberPagerState(
        initialPage = GuiderDestination.DAILY_TASKS.ordinal,
        pageCount = { destinations.size },
    )
    val navigationDestination = destinations[pagerState.targetPage]
    val settledDestination = destinations[pagerState.settledPage]
    val pageAnimationSpec = remember {
        tween<Float>(durationMillis = 280, easing = FastOutSlowInEasing)
    }
    val coroutineScope = rememberCoroutineScope()
    var navigationJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshDayBoundContent()
            delay(DayKeys.millisUntilTomorrow() + 1_000L)
        }
    }

    LaunchedEffect(destinationRequest) {
        destinationRequest?.let {
            pagerState.animateScrollToPage(
                page = it.ordinal,
                animationSpec = pageAnimationSpec,
            )
            onDestinationRequestConsumed()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            GuiderBottomBar(
                selectedDestination = navigationDestination,
                onDestinationSelected = { destination ->
                    if (destination.ordinal != pagerState.targetPage) {
                        navigationJob?.cancel()
                        navigationJob = coroutineScope.launch {
                            pagerState.animateScrollToPage(
                                page = destination.ordinal,
                                animationSpec = pageAnimationSpec,
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (settledDestination == GuiderDestination.DAILY_TASKS) {
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
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { page -> destinations[page] },
            beyondViewportPageCount = 1,
        ) { page ->
            DestinationContent(
                destination = destinations[page],
                isVisible = page == pagerState.settledPage,
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
    isVisible: Boolean,
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

        GuiderDestination.SLEEP -> SleepCalculatorRoute(
            isVisible = isVisible,
            modifier = modifier,
        )

        GuiderDestination.HABITS -> HabitsRoute(modifier = modifier)

        GuiderDestination.BIGGER_GOALS -> GoalsRoute(modifier = modifier)

        GuiderDestination.MONEY -> MoneyRoute(modifier = modifier)
    }
}
