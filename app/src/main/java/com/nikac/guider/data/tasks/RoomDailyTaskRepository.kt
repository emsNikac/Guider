package com.nikac.guider.data.tasks

import com.nikac.guider.data.database.DailyTaskDao
import com.nikac.guider.data.database.DailyTaskEntity
import com.nikac.guider.data.database.GuiderDatabase
import com.nikac.guider.data.database.toModel
import com.nikac.guider.data.stateInWhileSubscribed
import com.nikac.guider.domain.tasks.DailyTaskRepository
import com.nikac.guider.models.DailyTask
import com.nikac.guider.models.TaskCategory
import com.nikac.guider.domain.sync.DataOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RoomDailyTaskRepository private constructor(
    private val dao: DailyTaskDao,
    private val owner: StateFlow<DataOwner>,
    private val onDataChanged: () -> Unit,
    override val tasks: StateFlow<List<DailyTask>>,
) : DailyTaskRepository {
    override suspend fun addTask(
        title: String,
        category: TaskCategory,
        linkedGoalId: Long?,
    ): DailyTask {
        val currentOwner = owner.value
        val entity = DailyTaskEntity(
            ownerId = currentOwner.localId,
            category = category.name,
            title = title.trim(),
            isFinished = false,
            createdDayKey = com.nikac.guider.domain.time.DayKeys.today(),
            completedDayKey = null,
            linkedGoalId = linkedGoalId,
            syncPending = currentOwner.usesCloud,
        )
        return entity.copy(id = dao.insert(entity)).toModel().also { notifyCloud(currentOwner) }
    }

    override suspend fun setFinished(taskId: Long, finished: Boolean, dayKey: Int) {
        val currentOwner = owner.value
        dao.setFinished(
            ownerId = currentOwner.localId,
            taskId = taskId,
            finished = finished,
            dayKey = dayKey,
            updatedAtEpochMillis = System.currentTimeMillis(),
            syncPending = currentOwner.usesCloud,
        )
        notifyCloud(currentOwner)
    }

    override suspend fun removeCompletedBefore(dayKey: Int) {
        val currentOwner = owner.value
        val changed = dao.archiveCompletedBefore(
            ownerId = currentOwner.localId,
            dayKey = dayKey,
            updatedAtEpochMillis = System.currentTimeMillis(),
            syncPending = currentOwner.usesCloud,
        )
        if (changed > 0) notifyCloud(currentOwner)
    }

    override suspend fun clearGoalLink(goalId: Long) {
        val currentOwner = owner.value
        dao.clearGoalLink(
            ownerId = currentOwner.localId,
            goalId = goalId,
            updatedAtEpochMillis = System.currentTimeMillis(),
            syncPending = currentOwner.usesCloud,
        )
        notifyCloud(currentOwner)
    }

    private fun notifyCloud(changedOwner: DataOwner) {
        if (changedOwner == owner.value && changedOwner.usesCloud) onDataChanged()
    }

    companion object {
        suspend fun create(
            database: GuiderDatabase,
            scope: CoroutineScope,
            owner: StateFlow<DataOwner>,
            onDataChanged: () -> Unit,
        ): RoomDailyTaskRepository {
            val dao = database.dailyTaskDao()
            val currentOwner = owner.value
            val archived = dao.archiveCompletedBefore(
                ownerId = currentOwner.localId,
                dayKey = com.nikac.guider.domain.time.DayKeys.today(),
                updatedAtEpochMillis = System.currentTimeMillis(),
                syncPending = currentOwner.usesCloud,
            )
            if (archived > 0 && currentOwner.usesCloud) onDataChanged()
            val tasks = owner.flatMapLatest { activeOwner ->
                dao.observeAll(activeOwner.localId)
            }
                .map { entities -> entities.map(DailyTaskEntity::toModel) }
                .flowOn(Dispatchers.Default)
                .distinctUntilChanged()
                .stateInWhileSubscribed(scope)
            return RoomDailyTaskRepository(
                dao = dao,
                owner = owner,
                onDataChanged = onDataChanged,
                tasks = tasks,
            )
        }
    }
}
