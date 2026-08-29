package com.example.guider.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.guider.models.TaskCategory

@Immutable
data class CategoryPalette(
    val container: Color,
    val content: Color,
)

@Composable
fun taskCategoryPalette(category: TaskCategory): CategoryPalette {
    val colorScheme = MaterialTheme.colorScheme
    val darkTheme = colorScheme.background.luminance() < 0.5f
    return remember(
        category,
        darkTheme,
        colorScheme.surfaceContainerHighest,
        colorScheme.onSurfaceVariant,
    ) {
        when (category) {
            TaskCategory.HEALTH -> if (darkTheme) {
                CategoryPalette(container = Color(0xFF294A6F), content = Color(0xFFD8E9FF))
            } else {
                CategoryPalette(container = Color(0xFFD8E9FF), content = Color(0xFF214B78))
            }

            TaskCategory.WORK -> if (darkTheme) {
                CategoryPalette(container = Color(0xFF31583B), content = Color(0xFFD9F0DD))
            } else {
                CategoryPalette(container = Color(0xFFD9F0DD), content = Color(0xFF285537))
            }

            TaskCategory.MENTAL_HEALTH -> if (darkTheme) {
                CategoryPalette(container = Color(0xFF694158), content = Color(0xFFFFD9EA))
            } else {
                CategoryPalette(container = Color(0xFFFFD9EA), content = Color(0xFF6C3B55))
            }

            TaskCategory.OTHER -> CategoryPalette(
                container = colorScheme.surfaceContainerHighest,
                content = colorScheme.onSurfaceVariant,
            )
        }
    }
}
