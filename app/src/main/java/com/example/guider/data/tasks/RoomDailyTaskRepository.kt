package com.example.guider.data.tasks

import com.example.guider.data.database.DailyTaskDao
import com.example.guider.data.database.DailyTaskEntity
import com.example.guider.data.database.toModel
import com.example.guider.data.stateInWhileSubscribed
import com.example.guider.domain.tasks.DailyTaskRepository
import com.example.guider.models.DailyTask
import com.example.guider.models.TaskCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class RoomDailyTaskRepository private constructor(
    private val dao: DailyTaskDao,
    override val tasks: StateFlow<List<DailyTask>>,
) : DailyTaskRepository {
    override suspend fun addTask(
        title: String,
        category: TaskCategory,
        linkedGoalId: Long?,
    ): DailyTask {
        val entity = DailyTaskEntity(
            category = category.name,
            title = title.trim(),
            isFinished = false,
            createdDayKey = com.example.guider.domain.time.DayKeys.today(),
            completedDayKey = null,
            linkedGoalId = linkedGoalId,
        )
        return entity.copy(id = dao.insert(entity)).toModel()
    }

    override suspend fun setFinished(taskId: Long, finished: Boolean, dayKey: Int) {
        dao.setFinished(taskId, finished, dayKey)
    }

    override suspend fun removeCompletedBefore(dayKey: Int) {
        dao.removeCompletedBefore(dayKey)
    }

    override suspend fun clearGoalLink(goalId: Long) {
        dao.clearGoalLink(goalId)
    }

    companion object {
        suspend fun create(dao: DailyTaskDao, scope: CoroutineScope): RoomDailyTaskRepository {
            dao.removeCompletedBefore(com.example.guider.domain.time.DayKeys.today())
            val tasks = dao.observeAll()
                .map { entities -> entities.map(DailyTaskEntity::toModel) }
                .flowOn(Dispatchers.Default)
                .distinctUntilChanged()
                .stateInWhileSubscribed(scope)
            return RoomDailyTaskRepository(
                dao = dao,
                tasks = tasks,
            )
        }
    }
}
