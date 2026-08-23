package com.example.guider.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.guider.GuiderApplication
import com.example.guider.domain.time.DayKeys
import com.example.guider.models.TaskCategory

class GuiderViewModel(application: Application) : AndroidViewModel(application) {
    private val guiderApplication = application as GuiderApplication
    private val taskRepository = guiderApplication.dailyTaskRepository
    private val goalRepository = guiderApplication.goalRepository

    val tasks = taskRepository.tasks
    val goals = goalRepository.goals

    fun addTask(title: String, category: TaskCategory, linkedGoalId: Long?) {
        if (title.isNotBlank()) taskRepository.addTask(title, category, linkedGoalId)
    }

    fun setTaskFinished(taskId: Long, finished: Boolean) {
        taskRepository.setFinished(taskId, finished, DayKeys.today())
    }

    fun refreshDayBoundContent() {
        val today = DayKeys.today()
        taskRepository.removeCompletedBefore(today)
        goalRepository.removeCompletedBefore(today)
    }
}
