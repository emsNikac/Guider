package com.example.guider.domain.tasks

import com.example.guider.models.DailyTask
import com.example.guider.models.TaskCategory
import kotlinx.coroutines.flow.StateFlow

interface DailyTaskRepository {
    val tasks: StateFlow<List<DailyTask>>

    suspend fun addTask(
        title: String,
        category: TaskCategory,
        linkedGoalId: Long? = null,
    ): DailyTask

    suspend fun setFinished(taskId: Long, finished: Boolean, dayKey: Int)

    suspend fun removeCompletedBefore(dayKey: Int)

    suspend fun clearGoalLink(goalId: Long)
}
