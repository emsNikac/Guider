package com.example.guider.data.tasks

import android.content.Context
import androidx.core.content.edit
import com.example.guider.domain.tasks.DailyTaskRepository
import com.example.guider.domain.time.DayKeys
import com.example.guider.models.DailyTask
import com.example.guider.models.TaskCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class SharedPreferencesDailyTaskRepository(context: Context) : DailyTaskRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableTasks = MutableStateFlow(readTasks())

    override val tasks: StateFlow<List<DailyTask>> = mutableTasks.asStateFlow()

    init {
        if (!preferences.contains(KEY_TASKS)) persist(mutableTasks.value)
        removeCompletedBefore(DayKeys.today())
    }

    @Synchronized
    override fun addTask(
        title: String,
        category: TaskCategory,
        linkedGoalId: Long?,
    ): DailyTask {
        removeCompletedBefore(DayKeys.today())
        val task = DailyTask(
            id = (mutableTasks.value.maxOfOrNull(DailyTask::id) ?: 0L) + 1L,
            taskCategory = category,
            title = title.trim(),
            isFinished = false,
            createdDayKey = DayKeys.today(),
            linkedGoalId = linkedGoalId,
        )
        persist(mutableTasks.value + task)
        return task
    }

    @Synchronized
    override fun setFinished(taskId: Long, finished: Boolean, dayKey: Int) {
        val updated = mutableTasks.value.map { task ->
            if (task.id != taskId) task
            else task.copy(
                isFinished = finished,
                completedDayKey = if (finished) dayKey else null,
            )
        }
        persist(updated)
    }

    @Synchronized
    override fun removeCompletedBefore(dayKey: Int) {
        val current = mutableTasks.value
        val updated = current.filterNot { task ->
            task.isFinished && task.completedDayKey?.let { it < dayKey } == true
        }
        if (updated != current) persist(updated)
    }

    @Synchronized
    override fun clearGoalLink(goalId: Long) {
        val current = mutableTasks.value
        val updated = current.map { task ->
            if (task.linkedGoalId == goalId) task.copy(linkedGoalId = null) else task
        }
        if (updated != current) persist(updated)
    }

    private fun readTasks(): List<DailyTask> {
        val encoded = preferences.getString(KEY_TASKS, null) ?: return starterTasks()
        return runCatching {
            val array = JSONArray(encoded)
            buildList {
                repeat(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    add(
                        DailyTask(
                            id = item.getLong(JSON_ID),
                            taskCategory = TaskCategory.valueOf(item.getString(JSON_CATEGORY)),
                            title = item.getString(JSON_TITLE),
                            isFinished = item.getBoolean(JSON_FINISHED),
                            createdDayKey = item.getInt(JSON_CREATED_DAY),
                            completedDayKey = if (item.isNull(JSON_COMPLETED_DAY)) null
                            else item.getInt(JSON_COMPLETED_DAY),
                            linkedGoalId = if (item.isNull(JSON_LINKED_GOAL_ID)) null
                            else item.getLong(JSON_LINKED_GOAL_ID),
                        ),
                    )
                }
            }
        }.getOrElse { starterTasks() }
    }

    private fun persist(tasks: List<DailyTask>) {
        preferences.edit { putString(KEY_TASKS, tasksToJson(tasks).toString()) }
        mutableTasks.value = tasks
    }

    private fun tasksToJson(tasks: List<DailyTask>): JSONArray = JSONArray().apply {
        tasks.forEach { task ->
            put(
                JSONObject()
                    .put(JSON_ID, task.id)
                    .put(JSON_CATEGORY, task.taskCategory.name)
                    .put(JSON_TITLE, task.title)
                    .put(JSON_FINISHED, task.isFinished)
                    .put(JSON_CREATED_DAY, task.createdDayKey)
                    .put(JSON_COMPLETED_DAY, task.completedDayKey ?: JSONObject.NULL)
                    .put(JSON_LINKED_GOAL_ID, task.linkedGoalId ?: JSONObject.NULL),
            )
        }
    }

    private fun starterTasks(): List<DailyTask> {
        val today = DayKeys.today()
        return listOf(
            DailyTask(1L, TaskCategory.HEALTH, "Drink water", false, today),
            DailyTask(2L, TaskCategory.HEALTH, "Take a 30 minute walk", false, today),
            DailyTask(3L, TaskCategory.WORK, "Write the project outline", false, today),
            DailyTask(4L, TaskCategory.MENTAL_HEALTH, "Meditate for 10 minutes", false, today),
            DailyTask(5L, TaskCategory.MENTAL_HEALTH, "Read before bed", false, today),
            DailyTask(6L, TaskCategory.OTHER, "Call family", false, today),
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "daily_task_tracking"
        const val KEY_TASKS = "tasks"
        const val JSON_ID = "id"
        const val JSON_CATEGORY = "category"
        const val JSON_TITLE = "title"
        const val JSON_FINISHED = "finished"
        const val JSON_CREATED_DAY = "createdDay"
        const val JSON_COMPLETED_DAY = "completedDay"
        const val JSON_LINKED_GOAL_ID = "linkedGoalId"
    }
}
