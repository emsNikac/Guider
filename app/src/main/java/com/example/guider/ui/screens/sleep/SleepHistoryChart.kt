package com.example.guider.ui.screens.sleep

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.guider.domain.sleep.SleepHistoryRange
import com.example.guider.domain.sleep.SleepRecord
import com.example.guider.domain.time.DayKeys
import com.example.guider.util.LocalizedFormatters
import com.example.guider.domain.collections.ImmutableListSnapshot
import com.example.guider.domain.collections.toImmutableSnapshot
import kotlin.math.ceil
import kotlin.math.max

@Immutable
private data class SleepChartDay(
    val label: String,
    val hours: Float?,
)

@Composable
fun SleepHistoryCard(
    records: ImmutableListSnapshot<SleepRecord>,
    modifier: Modifier = Modifier,
) {
    var range by rememberSaveable { mutableStateOf(SleepHistoryRange.WEEK) }
    val days = remember(records, range) {
        buildChartDays(records = records, range = range, nowEpochMillis = System.currentTimeMillis())
            .toImmutableSnapshot()
    }
    val average = remember(days) {
        var total = 0.0
        var count = 0
        days.forEach { day ->
            day.hours?.let { hours ->
                total += hours
                count++
            }
        }
        if (count == 0) null else total / count
    }
    val averageLabel = remember(average) {
        average?.let { "${formatHours(it)} average" } ?: "No completed sleep yet"
    }

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
                        text = averageLabel,
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
private fun SleepLineChart(days: ImmutableListSnapshot<SleepChartDay>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val pointCenterColor = MaterialTheme.colorScheme.surfaceContainerLow
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val values = remember(days) { days.mapNotNull { it.hours } }
    val maximumHours = remember(values) {
        max(10f, ceil((values.maxOrNull() ?: 0f) / 2f) * 2f)
    }
    val axisLabels = remember(maximumHours) {
        listOf(maximumHours, maximumHours * 2 / 3, maximumHours / 3, 0f)
            .map { hours -> "${hours.toInt()}h" }
    }
    val visibleChartLabels = remember(days) { chartLabels(days) }

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
                axisLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor,
                    )
                }
            }
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 30.dp, top = 6.dp, bottom = 16.dp)
                    .drawWithCache {
                        val gridSteps = 3
                        val gridYPositions = FloatArray(gridSteps + 1) { step ->
                            size.height * step / gridSteps
                        }
                        val xStep = if (days.size <= 1) 0f else size.width / (days.size - 1)
                        val chartHeight = size.height
                        val points = buildList {
                            days.forEachIndexed { index, day ->
                                day.hours?.let { hours ->
                                    add(
                                        Offset(
                                            x = index * xStep,
                                            y = chartHeight *
                                                (1f - (hours / maximumHours).coerceIn(0f, 1f)),
                                        ),
                                    )
                                }
                            }
                        }
                        val path = if (points.size >= 2) {
                            Path().apply {
                                moveTo(points[0].x, points[0].y)
                                for (index in 1 until points.size) {
                                    lineTo(points[index].x, points[index].y)
                                }
                            }
                        } else {
                            null
                        }
                        val gridStrokeWidth = 1.dp.toPx()
                        val lineStyle = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        val pointRadius = 4.dp.toPx()
                        val pointCenterRadius = 1.5.dp.toPx()

                        onDrawBehind {
                            gridYPositions.forEach { y ->
                                drawLine(
                                    color = gridColor,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = gridStrokeWidth,
                                )
                            }
                            path?.let {
                                drawPath(path = it, color = lineColor, style = lineStyle)
                            }
                            points.forEach { point ->
                                drawCircle(color = lineColor, radius = pointRadius, center = point)
                                drawCircle(
                                    color = pointCenterColor,
                                    radius = pointCenterRadius,
                                    center = point,
                                )
                            }
                        }
                    }
            )

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
            visibleChartLabels.forEach { label ->
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
    val labelPattern = if (range == SleepHistoryRange.WEEK) "EEE" else "d MMM"
    val todayDayKey = DayKeys.today(nowEpochMillis)
    val durationsByWakeDay = HashMap<Int, Long>(records.size)
    records.forEach { record ->
        val wakeDayKey = DayKeys.today(record.endedAtEpochMillis)
        durationsByWakeDay[wakeDayKey] =
            durationsByWakeDay.getOrDefault(wakeDayKey, 0L) + record.durationMillis
    }

    return (range.dayCount - 1 downTo 0).map { daysAgo ->
        val dayKey = DayKeys.addDays(todayDayKey, -daysAgo)
        val hours = durationsByWakeDay.getOrDefault(dayKey, 0L)
            .takeIf { it > 0L }
            ?.div(MILLIS_PER_HOUR)
            ?.toFloat()
        SleepChartDay(
            label = LocalizedFormatters.formatDate(labelPattern, DayKeys.toEpochMillis(dayKey)),
            hours = hours,
        )
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
