package com.nikac.guider.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HabitsScrollBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun monthGridScrollWithBaselineProfile() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.Require,
        ),
        iterations = 10,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            waitForDailyTasks()
            openDestination("Habits")
            checkNotNull(device.wait(Until.findObject(By.text("Month")), UI_TIMEOUT_MILLIS)).click()
            device.waitForIdle()
        },
        measureBlock = { scrollCurrentScreen() },
    )
}
