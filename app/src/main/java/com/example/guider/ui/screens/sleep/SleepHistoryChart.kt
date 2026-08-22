package com.example.guider.ui.screens.sleep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.guider.domain.sleep.SleepHistoryRange
import com.example.guider.domain.sleep.SleepRecord
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

private data class SleepChartDay(
    val label: String,
    val hours: Float?,
)

@Composable
fun SleepHistoryCard(
    records: List<SleepRecord>,
    modifier: Modifier = Modifier,
) {
    var range by rememberSaveable { mutableStateOf(SleepHistoryRange.WEEK) }
    val days = remember(records, range) {
        buildChartDays(records = records, range = range, nowEpochMillis = System.currentTimeMillis())
    }
    val recordedHours = days.mapNotNull { it.hours }
    val average = recordedHours.takeIf { it.isNotEmpty() }?.average()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sleep history",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = average?.let { "${formatHours(it)} average" } ?: "No completed sleep yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HistoryRangeSelector(
                    selected = range,
                    onSelected = { range = it },
                )
            }

            SleepLineChart(days = days)

            Text(
                text = "Sleep is grouped by the day you wake up.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistoryRangeSelector(
    selected: SleepHistoryRange,
    onSelected: (SleepHistoryRange) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(modifier = Modifier.padding(3.dp)) {
            SleepHistoryRange.entries.forEach { range ->
                val isSelected = range == selected
                Surface(
                    modifier = Modifier.clickable { onSelected(range) },
                    shape = RoundedCornerShape(9.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        androidx.compose.ui.graphics.Color.Transparent
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ) {
                    Text(
                        text = range.label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SleepLineChart(days: List<SleepChartDay>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val pointCenterColor = MaterialTheme.colorScheme.surfaceContainerLow
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val values = days.mapNotNull { it.hours }
    val maximumHours = max(10f, ceil((values.maxOrNull() ?: 0f) / 2f) * 2f)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(164.dp),
        ) {
            Column(
                modifier = Modifier
                    .height(148.dp)
                    .padding(vertical = 1.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf(maximumHours, maximumHours * 2 / 3, maximumHours / 3, 0f).forEach { hours ->
                    Text(
                        text = "${hours.toInt()}h",
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor,
                    )
                }
            }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 30.dp, top = 6.dp, bottom = 16.dp),
            ) {
                val gridSteps = 3
                repeat(gridSteps + 1) { step ->
                    val y = size.height * step / gridSteps
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                val xStep = if (days.size <= 1) 0f else size.width / (days.size - 1)
                val points = days.mapIndexedNotNull { index, day ->
                    day.hours?.let { hours ->
                        Offset(
                            x = index * xStep,
                            y = size.height * (1f - (hours / maximumHours).coerceIn(0f, 1f)),
                        )
                    }
                }
                if (points.size >= 2) {
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { point -> lineTo(point.x, point.y) }
                    }
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                points.forEach { point ->
                    drawCircle(color = lineColor, radius = 4.dp.toPx(), center = point)
                    drawCircle(
                        color = pointCenterColor,
                        radius = 1.5.dp.toPx(),
                        center = point,
                    )
                }
            }

            if (values.isEmpty()) {
                Text(
                    text = "Your completed nights will appear here",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodySmall,
                    color = labelColor,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 30.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            chartLabels(days).forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                )
            }
        }
    }
}

private fun buildChartDays(
    records: List<SleepRecord>,
    range: SleepHistoryRange,
    nowEpochMillis: Long,
): List<SleepChartDay> {
    val locale = Locale.getDefault()
    val labelFormat = SimpleDateFormat(
        if (range == SleepHistoryRange.WEEK) "EEE" else "d MMM",
        locale,
    )
    val today = Calendar.getInstance().apply {
        timeInMillis = nowEpochMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    return (range.dayCount - 1 downTo 0).map { daysAgo ->
        val start = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val hours = records
            .filter { it.endedAtEpochMillis in start.timeInMillis until end.timeInMillis }
            .sumOf { it.durationMillis }
            .takeIf { it > 0L }
            ?.div(MILLIS_PER_HOUR)
            ?.toFloat()
        SleepChartDay(label = labelFormat.format(start.time), hours = hours)
    }
}

private fun chartLabels(days: List<SleepChartDay>): List<String> =
    if (days.size <= 7) {
        days.map { it.label }
    } else {
        listOf(days.first().label, days[days.lastIndex / 2].label, days.last().label)
    }

private fun formatHours(hours: Double): String {
    val wholeHours = hours.toInt()
    val minutes = ((hours - wholeHours) * 60).toInt()
    return if (minutes == 0) "${wholeHours}h" else "${wholeHours}h ${minutes}m"
}

private const val MILLIS_PER_HOUR = 3_600_000.0
