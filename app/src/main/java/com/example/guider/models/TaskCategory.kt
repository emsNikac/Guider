package com.example.guider.models

import androidx.annotation.DrawableRes
import com.example.guider.R

enum class TaskCategory(
    val displayName: String,
    @DrawableRes val iconRes: Int
) {
    HEALTH("Health", R.drawable.health_icon),
    WORK("Work", R.drawable.work_icon),
    MENTAL_HEALTH("Mental health", R.drawable.mental_health_icon),
    OTHER("Other", R.drawable.other_icon)
}
