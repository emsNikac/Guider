package com.nikac.guider.domain.sleep

object SleepCycleCalculator {
    const val FALL_ASLEEP_MINUTES = 15
    const val CYCLE_MINUTES = 90
    const val MAX_VISIBLE_CYCLES = 6

    private const val MILLIS_PER_MINUTE = 60_000L

    fun suggestions(sleepAttemptAtEpochMillis: Long): List<SleepCycleSuggestion> =
        (1..MAX_VISIBLE_CYCLES).map { cycleCount ->
            val minutesUntilWake = FALL_ASLEEP_MINUTES + cycleCount * CYCLE_MINUTES
            SleepCycleSuggestion(
                cycleCount = cycleCount,
                wakeAtEpochMillis = sleepAttemptAtEpochMillis + minutesUntilWake * MILLIS_PER_MINUTE,
            )
        }

    fun effectiveSleepStart(activatedAtEpochMillis: Long): Long =
        activatedAtEpochMillis + FALL_ASLEEP_MINUTES * MILLIS_PER_MINUTE
}
