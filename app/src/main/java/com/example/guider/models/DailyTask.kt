package com.example.guider.models

data class DailyTask(
    val id: Long,
    val taskCategory: TaskCategory,
    val title: String,
    val isFinished: Boolean,
    val createdDayKey: Int,
    val completedDayKey: Int? = null,
    val linkedGoalId: Long? = null,
)
