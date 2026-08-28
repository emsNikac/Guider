package com.example.guider.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.guider.AppFeature
import com.example.guider.FeatureLoadStatus
import com.example.guider.GuiderApplication
import com.example.guider.R
import com.example.guider.models.DailyTask
import com.example.guider.models.TaskCategory
import com.example.guider.domain.goals.isActive
import com.example.guider.domain.time.DayKeys
import com.example.guider.ui.components.GuiderBottomBar
import com.example.guider.ui.screens.AddTaskDialog
import com.example.guider.ui.screens.DailyTasksScreen
import com.example.guider.ui.screens.goals.GoalsRoute
import com.example.guider.ui.screens.habits.HabitsRoute
import com.example.guider.ui.screens.money.MoneyRoute
import com.example.guider.ui.screens.sleep.SleepCalculatorRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

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
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val goalTitlesById = remember(goals) { goals.associate { it.id to it.title } }
    val activeGoals = remember(goals) { goals.filter { it.isActive(DayKeys.today()) } }
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
            if (shouldAnimatePageChange(pagerState.currentPage, it.ordinal)) {
                pagerState.animateScrollToPage(
                    page = it.ordinal,
                    animationSpec = pageAnimationSpec,
                )
            } else {
                pagerState.scrollToPage(it.ordinal)
            }
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
                            if (shouldAnimatePageChange(pagerState.currentPage, destination.ordinal)) {
                                pagerState.animateScrollToPage(
                                    page = destination.ordinal,
                                    animationSpec = pageAnimationSpec,
                                )
                            } else {
                                pagerState.scrollToPage(destination.ordinal)
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (settledDestination == GuiderDestination.DAILY_TASKS) {
                FloatingActionButton(
                    onClick = {
                        viewModel.requestGoals()
                        showAddTaskDialog = true
                    },
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
            val destination = destinations[page]
            if (destination == GuiderDestination.DAILY_TASKS) {
                DailyTasksDestinationContent(
                    contentPadding = innerPadding,
                    tasks = tasks,
                    selectedCategory = selectedCategory,
                    onCategorySelected = { category ->
                        selectedCategory = if (selectedCategory == category) null else category
                    },
                    onTaskCheckedChange = { id, isFinished ->
                        viewModel.setTaskFinished(id, isFinished)
                    },
                    goalTitlesById = goalTitlesById,
                )
            } else {
                FeatureDestinationContent(
                    destination = destination,
                    isVisible = page == pagerState.settledPage,
                    contentPadding = innerPadding,
                )
            }
        }
    }

    if (showAddTaskDialog) {
        AddTaskDialog(
            onDismiss = { showAddTaskDialog = false },
            availableGoals = activeGoals,
            onAddTask = { title, category, linkedGoalId ->
                viewModel.addTask(title, category, linkedGoalId)
                selectedCategory = null
                showAddTaskDialog = false
            },
        )
    }
}

internal fun shouldAnimatePageChange(currentPage: Int, targetPage: Int): Boolean =
    abs(targetPage - currentPage) == 1

@Composable
private fun DailyTasksDestinationContent(
    contentPadding: PaddingValues,
    tasks: List<DailyTask>,
    goalTitlesById: Map<Long, String>,
    selectedCategory: TaskCategory?,
    onCategorySelected: (TaskCategory) -> Unit,
    onTaskCheckedChange: (Long, Boolean) -> Unit,
) {
    val modifier = destinationModifier(contentPadding)
    DailyTasksScreen(
        tasks = tasks,
        selectedCategory = selectedCategory,
        onCategorySelected = onCategorySelected,
        onTaskCheckedChange = onTaskCheckedChange,
        goalTitlesById = goalTitlesById,
        modifier = modifier,
    )
}

@Composable
private fun FeatureDestinationContent(
    destination: GuiderDestination,
    isVisible: Boolean,
    contentPadding: PaddingValues,
) {
    val modifier = destinationModifier(contentPadding)

    when (destination) {
        GuiderDestination.DAILY_TASKS -> error("Daily Tasks has its own destination content")

        GuiderDestination.SLEEP -> FeatureGate(
            feature = AppFeature.SLEEP,
            label = destination.label,
            modifier = modifier,
        ) {
            SleepCalculatorRoute(
                isVisible = isVisible,
                modifier = modifier,
            )
        }

        GuiderDestination.HABITS -> FeatureGate(
            feature = AppFeature.HABITS,
            label = destination.label,
            modifier = modifier,
        ) {
            HabitsRoute(modifier = modifier)
        }

        GuiderDestination.BIGGER_GOALS -> FeatureGate(
            feature = AppFeature.GOALS,
            label = destination.label,
            modifier = modifier,
        ) {
            GoalsRoute(modifier = modifier)
        }

        GuiderDestination.MONEY -> FeatureGate(
            feature = AppFeature.MONEY,
            label = destination.label,
            modifier = modifier,
        ) {
            MoneyRoute(modifier = modifier)
        }
    }
}

@Composable
private fun destinationModifier(contentPadding: PaddingValues): Modifier {
    val layoutDirection = LocalLayoutDirection.current
    val appliedPadding = PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection),
        top = contentPadding.calculateTopPadding(),
        end = contentPadding.calculateEndPadding(layoutDirection),
        bottom = 0.dp,
    )
    return Modifier
        .padding(appliedPadding)
        .consumeWindowInsets(appliedPadding)
}

@Composable
private fun FeatureGate(
    feature: AppFeature,
    label: String,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val application = LocalContext.current.applicationContext as GuiderApplication
    val statusFlow = remember(application, feature) {
        application.featureStatuses
            .map { statuses -> statuses.getValue(feature) }
            .distinctUntilChanged()
    }
    val status by statusFlow.collectAsStateWithLifecycle(
        initialValue = application.featureStatus(feature),
    )

    LaunchedEffect(application, feature) {
        application.requestFeature(feature)
    }

    when (status) {
        FeatureLoadStatus.READY -> content()
        FeatureLoadStatus.FAILED -> FeaturePlaceholder(
            label = label,
            failed = true,
            onRetry = { application.requestFeature(feature) },
            modifier = modifier,
        )
        FeatureLoadStatus.NOT_REQUESTED,
        FeatureLoadStatus.LOADING,
        -> FeaturePlaceholder(
            label = label,
            failed = false,
            onRetry = {},
            modifier = modifier,
        )
    }
}

@Composable
private fun FeaturePlaceholder(
    label: String,
    failed: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = if (failed) "$label couldn't load" else "Loading $label",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (failed) {
            Button(onClick = onRetry) {
                Text("Try again")
            }
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.56f))
        }
    }
}
