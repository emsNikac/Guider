package com.example.guider.models

import com.example.guider.R

enum class TaskCategory(
    val displayName: String,
    val colorRes: Int,
    val textColor: Int
) {
    HEALTH("HEALTH", R.color.health_color_category, R.color.health_color_font),
    WORK("WORK", R.color.work_color_category, R.color.work_color_font),
    MENTAL_HEALTH("MENTAL HEALTH", R.color.mental_color_category, R.color.mental_color_font),
    OTHER("OTHER", R.color.other_color_category, R.color.other_color_font)
}