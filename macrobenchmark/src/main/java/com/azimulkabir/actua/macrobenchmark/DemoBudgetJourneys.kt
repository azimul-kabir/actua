package com.azimulkabir.actua.macrobenchmark

import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

internal const val PACKAGE_NAME = "com.azimulkabir.actua"
private const val TIMEOUT_MS = 5_000L
// A release-shaped cold start on a freshly installed physical device can take longer
// than the steady-state action timeout while the first database is initialized.
private const val SETUP_TIMEOUT_MS = 30_000L

/**
 * One-time (per test class) setup that loads the app's built-in demo budget (see
 * `DemoBudgetSeeder`/`DemoBudgetManager` in :app) so every benchmark measures scrolling,
 * expand/collapse, search, etc. against a realistic, populated dataset rather than an empty
 * database — see #321's "a tiny empty database is not representative" requirement.
 *
 * Runs as plain UiAutomator against a normally-launched app, outside any
 * `MacrobenchmarkRule.measureRepeated` block, so it never counts toward measured metrics.
 * Safe to call more than once: if the demo budget is already active this just resets it to its
 * original sample data (same "Try demo budget" / "Reset demo budget" button either way).
 */
internal fun seedDemoBudget() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val device = UiDevice.getInstance(instrumentation)
    val context = instrumentation.targetContext
    val intent = checkNotNull(context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)) {
        "$PACKAGE_NAME is not installed on this device"
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), SETUP_TIMEOUT_MS)
    device.waitForIdle()

    // A completely cold, unwarmed process (no JIT/profile warm-up yet, unlike the
    // measureRepeated journeys below) can take noticeably longer than TIMEOUT_MS to render its
    // first real frame here, so this first wait gets the same generous budget as the rest of
    // setup rather than the steady-state per-action timeout.
    device.navigateToTab("Manage", timeoutMs = SETUP_TIMEOUT_MS)
    device.click(By.text("Connection & Data"))
    val demoButton = device.wait(Until.findObject(By.textContains("demo budget")), TIMEOUT_MS)
    if (demoButton != null) {
        demoButton.click()
        device.wait(Until.hasObject(By.textContains("Demo budget")), SETUP_TIMEOUT_MS)
        device.waitForIdle()
    }
    device.pressBack()
}

internal fun UiDevice.navigateToTab(label: String, timeoutMs: Long = TIMEOUT_MS) {
    // The bottom nav item's Icon carries `contentDescription = item.label`, but when labels are
    // visible (the default; see AppNavigation.kt's `showBottomNavigationLabels`) Compose's
    // semantics merging drops that description in favor of the sibling Text, so the merged node
    // ends up with an empty content-desc and the label only reachable as text. Try text first
    // since that's the common case, and fall back to desc for icon-only mode.
    val tab = wait(Until.findObject(By.text(label)), timeoutMs)
        ?: checkNotNull(wait(Until.findObject(By.desc(label)), timeoutMs)) {
            "Bottom nav tab \"$label\" was not found"
        }
    tab.click()
    waitForIdle()
}

internal fun UiDevice.click(selector: BySelector, timeoutMs: Long = TIMEOUT_MS) {
    val target = checkNotNull(wait(Until.findObject(selector), timeoutMs)) {
        "No element matched $selector"
    }
    target.click()
    waitForIdle()
}

internal fun MacrobenchmarkScope.launchToBudget() {
    pressHome()
    startActivityAndWait()
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.navigateToTab(label: String) = device.navigateToTab(label)

internal fun MacrobenchmarkScope.click(selector: BySelector, timeoutMs: Long = TIMEOUT_MS) =
    device.click(selector, timeoutMs)

internal fun MacrobenchmarkScope.navigateBack() {
    device.pressBack()
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.scrollMainList() {
    // UiAutomator only exposes `scrollable` when a Compose LazyColumn currently has
    // scroll range. The compact demo's Accounts list can fit on a Pixel 8, even though
    // the same journey is intentionally useful with a larger real budget. Drive the
    // gesture over the content area instead so the benchmark remains valid for both
    // layouts; a non-scrollable list simply absorbs the gesture.
    val centerX = device.displayWidth / 2
    val topY = device.displayHeight * 3 / 4
    val bottomY = device.displayHeight / 3
    repeat(3) {
        device.swipe(centerX, topY, centerX, bottomY, 16)
        device.waitForIdle()
    }
    repeat(3) {
        device.swipe(centerX, bottomY, centerX, topY, 16)
        device.waitForIdle()
    }
}
