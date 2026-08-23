package com.example.guider.domain.tasks

import com.example.guider.models.DailyTask
import com.example.guider.models.TaskCategory
import kotlinx.coroutines.flow.StateFlow

interface DailyTaskRepository {
    val tasks: StateFlow<List<DailyTask>>

    fun addTask(title: String, category: TaskCategory, linkedGoalId: Long? = null): DailyTask

    fun setFinished(taskId: Long, finished: Boolean, dayKey: Int)

    fun removeCompletedBefore(dayKey: Int)

    fun clearGoalLink(goalId: Long)
}
