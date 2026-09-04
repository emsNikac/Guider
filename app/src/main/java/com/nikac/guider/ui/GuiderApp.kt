package com.nikac.guider.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nikac.guider.AppFeature
import com.nikac.guider.FeatureLoadStatus
import com.nikac.guider.GuiderApplication
import com.nikac.guider.R
import com.nikac.guider.models.DailyTask
import com.nikac.guider.models.TaskCategory
import com.nikac.guider.domain.goals.isActive
import com.nikac.guider.domain.time.DayKeys
import com.nikac.guider.ui.components.GuiderBottomBar
import com.nikac.guider.ui.screens.AddTaskDialog
import com.nikac.guider.ui.screens.DailyTasksScreen
import com.nikac.guider.ui.screens.goals.GoalsRoute
import com.nikac.guider.ui.screens.habits.HabitsRoute
import com.nikac.guider.ui.screens.money.MoneyRoute
import com.nikac.guider.ui.screens.sleep.SleepCalculatorRoute
import com.nikac.guider.domain.collections.ImmutableListSnapshot
import com.nikac.guider.domain.collections.ImmutableMapSnapshot
import com.nikac.guider.domain.collections.toImmutableSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    onOpenSettings: () -> Unit = {},
    viewModel: GuiderViewModel = viewModel(),
) {
    var selectedCategory by remember { mutableStateOf<TaskCategory?>(null) }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val taskSnapshot = remember(tasks) { tasks.toImmutableSnapshot() }
    val goalTitlesById = remember(goals) {
        goals.associate { it.id to it.title }.toImmutableSnapshot()
    }
    val activeGoals = remember(goals) {
        goals.filter { it.isActive(DayKeys.today()) }.toImmutableSnapshot()
    }
    val application = LocalContext.current.applicationContext as GuiderApplication
    val destinations = GuiderDestination.entries
    val pagerState = rememberPagerState(
        initialPage = GuiderDestination.DAILY_TASKS.ordinal,
        pageCount = { destinations.size },
    )
    val destinationStateHolder = rememberSaveableStateHolder()
    val pageContentAlpha = remember { Animatable(1f) }
    var isFadeTransitionActive by remember { mutableStateOf(false) }
    val pageAnimationSpec = remember {
        tween<Float>(durationMillis = 280, easing = FastOutSlowInEasing)
    }
    val coroutineScope = rememberCoroutineScope()
    var navigationJob by remember { mutableStateOf<Job?>(null) }

    suspend fun navigateToPage(targetPage: Int) {
        when (pageTransition(pagerState.currentPage, targetPage)) {
            PageTransition.NONE -> Unit
            PageTransition.SLIDE -> pagerState.animateScrollToPage(
                page = targetPage,
                animationSpec = pageAnimationSpec,
            )
            PageTransition.FADE_THROUGH -> {
                try {
                    isFadeTransitionActive = true
                    withFrameNanos { }
                    pageContentAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = 70,
                            easing = FastOutLinearInEasing,
                        ),
                    )
                    pagerState.scrollToPage(targetPage)
                    withFrameNanos { }
                    pageContentAlpha.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = 130,
                            easing = LinearOutSlowInEasing,
                        ),
                    )
                } finally {
                    withContext(NonCancellable) {
                        pageContentAlpha.snapTo(1f)
                        isFadeTransitionActive = false
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshDayBoundContent()
            delay(DayKeys.millisUntilTomorrow() + 1_000L)
        }
    }

    LaunchedEffect(application) {
        delay(FEATURE_WARMUP_DELAY_MILLIS)
        application.warmFeaturesDuringIdle()
    }

    LaunchedEffect(destinationRequest) {
        destinationRequest?.let {
            navigateToPage(it.ordinal)
            onDestinationRequestConsumed()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            val navigationDestination = destinations[pagerState.targetPage]
            GuiderBottomBar(
                selectedDestination = navigationDestination,
                onDestinationSelected = { destination ->
                    if (destination.ordinal != pagerState.targetPage) {
                        val previousNavigation = navigationJob
                        navigationJob = coroutineScope.launch {
                            previousNavigation?.cancelAndJoin()
                            navigateToPage(destination.ordinal)
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            val settledDestination = destinations[pagerState.settledPage]
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
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isFadeTransitionActive) {
                        Modifier.graphicsLayer { alpha = pageContentAlpha.value }
                    } else {
                        Modifier
                    },
                ),
            key = { page -> destinations[page] },
            // Keep only the adjacent destination composed so tap-driven navigation does not
            // have to build the entire next screen inside the 280 ms pager animation.
            beyondViewportPageCount = 1,
        ) { page ->
            val destination = destinations[page]
            destinationStateHolder.SaveableStateProvider(destination.name) {
                if (destination == GuiderDestination.DAILY_TASKS) {
                    DailyTasksDestinationContent(
                        contentPadding = innerPadding,
                        tasks = taskSnapshot,
                        selectedCategory = selectedCategory,
                        onCategorySelected = { category ->
                            selectedCategory = if (selectedCategory == category) null else category
                        },
                        onTaskCheckedChange = { id, isFinished ->
                            viewModel.setTaskFinished(id, isFinished)
                        },
                        goalTitlesById = goalTitlesById,
                        onOpenSettings = onOpenSettings,
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

internal enum class PageTransition {
    NONE,
    SLIDE,
    FADE_THROUGH,
}

internal fun pageTransition(currentPage: Int, targetPage: Int): PageTransition = when {
    currentPage == targetPage -> PageTransition.NONE
    abs(targetPage - currentPage) == 1 -> PageTransition.SLIDE
    else -> PageTransition.FADE_THROUGH
}

private const val FEATURE_WARMUP_DELAY_MILLIS = 1_500L

@Composable
private fun DailyTasksDestinationContent(
    contentPadding: PaddingValues,
    tasks: ImmutableListSnapshot<DailyTask>,
    goalTitlesById: ImmutableMapSnapshot<Long, String>,
    selectedCategory: TaskCategory?,
    onCategorySelected: (TaskCategory) -> Unit,
    onTaskCheckedChange: (Long, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val modifier = Modifier.destinationModifier(contentPadding)
    DailyTasksScreen(
        tasks = tasks,
        selectedCategory = selectedCategory,
        onCategorySelected = onCategorySelected,
        onTaskCheckedChange = onTaskCheckedChange,
        onOpenSettings = onOpenSettings,
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
    val modifier = Modifier.destinationModifier(contentPadding)

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
private fun Modifier.destinationModifier(contentPadding: PaddingValues): Modifier {
    val layoutDirection = LocalLayoutDirection.current
    val appliedPadding = PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection),
        top = contentPadding.calculateTopPadding(),
        end = contentPadding.calculateEndPadding(layoutDirection),
        bottom = 0.dp,
    )
    return this
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
            LinearProgressIndicator(
                progress = { 0.35f },
                modifier = Modifier.fillMaxWidth(0.56f),
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}
