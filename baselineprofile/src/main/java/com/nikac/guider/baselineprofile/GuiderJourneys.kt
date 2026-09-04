package com.nikac.guider.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

internal const val TARGET_PACKAGE = "com.nikac.guider"
internal const val UI_TIMEOUT_MILLIS = 10_000L

private val destinations = listOf(
    "Sleep calculator",
    "Habits",
    "Bigger goals",
    "Money management",
)

private val distantDestinations = listOf(
    "Money management",
    "Sleep calculator",
    "Bigger goals",
    "Daily tasks",
    "Habits",
    "Money management",
    "Daily tasks",
)

internal fun MacrobenchmarkScope.waitForDailyTasks() {
    val entry = device.wait(
        Until.findObject(By.text(Pattern.compile("Daily tasks|Continue as guest"))),
        UI_TIMEOUT_MILLIS,
    )
    checkNotNull(entry) {
        "Guider did not show the welcome screen or daily tasks"
    }
    if (entry.text == "Continue as guest") entry.click()
    check(device.wait(Until.hasObject(By.text("Daily tasks")), UI_TIMEOUT_MILLIS)) {
        "Guider did not finish loading"
    }
}

internal fun MacrobenchmarkScope.navigateAcrossAllScreens() {
    destinations.forEach(::openDestination)
    destinations.asReversed().drop(1).forEach(::openDestination)
    openDestination("Daily tasks")
}

internal fun MacrobenchmarkScope.navigateDistantScreens() {
    distantDestinations.forEach(::openDestination)
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

internal fun MacrobenchmarkScope.openDestination(label: String) {
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

internal fun MacrobenchmarkScope.scrollCurrentScreen() {
    val centerX = device.displayWidth / 2
    val lowerY = device.displayHeight * 3 / 4
    val upperY = device.displayHeight / 3
    device.swipe(centerX, lowerY, centerX, upperY, 12)
    device.waitForIdle()
    device.swipe(centerX, upperY, centerX, lowerY, 12)
    device.waitForIdle()
}
