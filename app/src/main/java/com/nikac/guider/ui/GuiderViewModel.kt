package com.nikac.guider.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nikac.guider.AppFeature
import com.nikac.guider.GuiderApplication
import com.nikac.guider.domain.goals.Goal
import com.nikac.guider.domain.time.DayKeys
import com.nikac.guider.models.TaskCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GuiderViewModel(application: Application) : AndroidViewModel(application) {
    private val guiderApplication = application as GuiderApplication
    private val taskRepository = guiderApplication.dailyTaskRepository
    private val mutableGoals = MutableStateFlow<List<Goal>>(emptyList())

    val tasks = taskRepository.tasks
    val goals: StateFlow<List<Goal>> = mutableGoals.asStateFlow()

    init {
        viewModelScope.launch {
            guiderApplication.goalRepositoryState
                .filterNotNull()
                .first()
                .goals
                .collect(mutableGoals::emit)
        }
    }

    fun requestGoals() {
        guiderApplication.requestFeature(AppFeature.GOALS)
    }

    fun addTask(title: String, category: TaskCategory, linkedGoalId: Long?) {
        if (title.isNotBlank()) {
            viewModelScope.launch {
                taskRepository.addTask(title, category, linkedGoalId)
            }
        }
    }

    fun setTaskFinished(taskId: Long, finished: Boolean) {
        viewModelScope.launch {
            taskRepository.setFinished(taskId, finished, DayKeys.today())
        }
    }

    fun refreshDayBoundContent() {
        viewModelScope.launch {
            val today = DayKeys.today()
            taskRepository.removeCompletedBefore(today)
            guiderApplication.goalRepositoryState.value?.removeCompletedBefore(today)
        }
    }
}
