package com.example.guider.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

internal const val TARGET_PACKAGE = "com.example.guider"
internal const val UI_TIMEOUT_MILLIS = 10_000L

private val destinations = listOf(
    "Sleep calculator",
    "Habits",
    "Bigger goals",
    "Money management",
)

internal fun MacrobenchmarkScope.waitForDailyTasks() {
    check(device.wait(Until.hasObject(By.text("Daily tasks")), UI_TIMEOUT_MILLIS)) {
        "Guider did not finish loading"
    }
}

internal fun MacrobenchmarkScope.navigateAcrossAllScreens() {
    destinations.forEach(::openDestination)
    destinations.asReversed().drop(1).forEach(::openDestination)
    openDestination("Daily tasks")
}

internal fun MacrobenchmarkScope.exerciseCommonJourneys() {
    openDestination("Sleep calculator")
    scrollCurrentScreen()

    openDestination("Habits")
    device.wait(Until.findObject(By.text("Month")), UI_TIMEOUT_MILLIS)?.click()
    device.waitForIdle()
    scrollCurrentScreen()

    openDestination("Bigger goals")
    scrollCurrentScreen()

    openDestination("Money management")
    scrollCurrentScreen()

    openDestination("Daily tasks")
    scrollCurrentScreen()
}

private fun MacrobenchmarkScope.openDestination(label: String) {
    val navigationItem = device.wait(
        Until.findObject(By.desc(label)),
        UI_TIMEOUT_MILLIS,
    )
    checkNotNull(navigationItem) { "No bottom-navigation item found for $label" }
    navigationItem.click()
    check(device.wait(Until.hasObject(By.text(label)), UI_TIMEOUT_MILLIS)) {
        "$label did not become visible"
    }
    device.waitForIdle()
}

private fun MacrobenchmarkScope.scrollCurrentScreen() {
    val centerX = device.displayWidth / 2
    val lowerY = device.displayHeight * 3 / 4
    val upperY = device.displayHeight / 3
    device.swipe(centerX, lowerY, centerX, upperY, 12)
    device.waitForIdle()
    device.swipe(centerX, upperY, centerX, lowerY, 12)
    device.waitForIdle()
}
