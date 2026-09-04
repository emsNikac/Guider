package com.nikac.guider.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nikac.guider.domain.habits.HabitWeekday

@Composable
internal fun HabitWeekdaySelector(
    selectedWeekdays: Set<HabitWeekday>,
    onWeekdayToggled: (HabitWeekday) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        HabitWeekday.entries.forEach { weekday ->
            val selected = weekday in selectedWeekdays
            Surface(
                modifier = Modifier
                    .size(WeekdayButtonSize)
                    .clip(CircleShape)
                    .semantics {
                        contentDescription = weekday.name
                            .lowercase()
                            .replaceFirstChar(Char::uppercase)
                    }
                    .toggleable(
                        value = selected,
                        role = Role.Checkbox,
                        onValueChange = { onWeekdayToggled(weekday) },
                    ),
                shape = CircleShape,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = weekday.shortLabel,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

private val WeekdayButtonSize = 30.dp
