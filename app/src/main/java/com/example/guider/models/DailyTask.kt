package com.example.guider.models

data class DailyTask(
    val id: Int,
    val taskCategory: TaskCategory,
    val title: String,
    val isFinished: Boolean
)
