package com.azimulkabir.actua.macrobenchmark

import android.content.Intent
import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/**
 * The side-by-side `benchmarkRelease` build (see :app's build.gradle.kts), never the released app: these journeys
 * reset and reseed the demo budget of whatever they target.
 */
internal const val PACKAGE_NAME = "com.azimulkabir.actua.benchmark"
private const val TIMEOUT_MS = 5_000L
// A release-shaped cold start on a freshly installed physical device can take longer
// than the steady-state action timeout while the first database is initialized.
private const val SETUP_TIMEOUT_MS = 30_000L
private const val MAX_SCROLLS_TO_CLEAR_TAB_BAR = 3
private const val SETTLE_POLLS = 12
private const val SETTLE_POLL_MS = 150L

// The floating tab bar occupies roughly the bottom 12% of the screen; a row below this fraction is under it.
private const val TAB_BAR_BAND_TOP = 0.85

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
    device.clickClearOfTabBar(By.text("Connection & Data"))
    // A slow first launch (the app is also installing its profile) can need far longer than a normal action, and a
    // skipped demo leaves every journey looking at "No budget open", so wait for the button and fail if it is missing.
    val demoButton = checkNotNull(device.wait(Until.findObject(By.textContains("demo budget")), SETUP_TIMEOUT_MS)) {
        "Neither \"Try demo budget\" nor \"Reset demo budget\" was found"
    }
    demoButton.click()
    // The demo is created off the main thread and only becomes the active budget when that finishes; the screen
    // switches to its "Reset demo budget" button then, and leaving earlier would abandon it.
    check(device.wait(Until.hasObject(By.text("Reset demo budget")), SETUP_TIMEOUT_MS)) {
        "The demo budget did not become the active budget"
    }
    device.waitForIdle()
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

/**
 * Clicks a row that may sit under the floating tab bar. A tap on the bar's band lands on the bar, not on the row
 * (on a 1920 px screen "Connection & Data" is last in Manage and starts under it), so scroll the list up until the
 * row is clear of it.
 */
internal fun UiDevice.clickClearOfTabBar(selector: BySelector, timeoutMs: Long = TIMEOUT_MS) {
    var target = checkNotNull(wait(Until.findObject(selector), timeoutMs)) { "No element matched $selector" }
    repeat(MAX_SCROLLS_TO_CLEAR_TAB_BAR) {
        if (target.visibleBounds.centerY() < displayHeight * TAB_BAR_BAND_TOP) return@repeat
        swipe(displayWidth / 2, displayHeight * 3 / 4, displayWidth / 2, displayHeight / 3, 16)
        waitForIdle()
        target = checkNotNull(wait(Until.findObject(selector), timeoutMs)) { "No element matched $selector" }
        target = settled(selector, target, timeoutMs)
    }
    target.click()
    waitForIdle()
}

/**
 * The list keeps flinging after a swipe, and UiAutomator's idle check does not see a Compose fling. A tap on a moving
 * row only stops the scroll, so wait until the row's bounds stop changing before clicking it.
 */
private fun UiDevice.settled(selector: BySelector, first: UiObject2, timeoutMs: Long): UiObject2 {
    var current = first
    var bounds = current.visibleBounds
    repeat(SETTLE_POLLS) {
        SystemClock.sleep(SETTLE_POLL_MS)
        current = checkNotNull(wait(Until.findObject(selector), timeoutMs)) { "No element matched $selector" }
        val now = current.visibleBounds
        if (now == bounds) return current
        bounds = now
    }
    return current
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
    ensureDemoSeeded()
    // The app resumes on the tab it was last on (Manage, right after a re-seed), so go to Budget explicitly.
    device.navigateToTab("Budget")
}

/**
 * Seeds the demo budget again if the app shows "No budget open". Before each test the library resets the app's
 * compiled profile, and below Android 14 it does that by uninstalling and reinstalling the app, which deletes the
 * demo budget the class's @BeforeClass created. The check is cheap when the demo is there.
 */
internal fun MacrobenchmarkScope.ensureDemoSeeded() {
    if (device.hasObject(By.text("No budget open"))) {
        seedDemoBudget()
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
    }
}

internal fun MacrobenchmarkScope.navigateToTab(label: String) = device.navigateToTab(label)

/**
 * The full transaction list. There is no Transactions tab by default: it opens from the "All accounts" row at the
 * top of Accounts. Re-tapping the Accounts tab returns to its root first, so this works from any state.
 */
internal fun MacrobenchmarkScope.openAllTransactions() {
    navigateToTab("Accounts")
    click(By.descStartsWith("All accounts,"))
}

internal fun MacrobenchmarkScope.click(selector: BySelector, timeoutMs: Long = TIMEOUT_MS) =
    device.click(selector, timeoutMs)

/**
 * Back pressed before a sheet or keypad has finished appearing can be dropped or close the wrong thing, which on a slow
 * device leaves the app one screen deeper for the next iteration, so wait for what was opened before going back.
 */
internal fun MacrobenchmarkScope.awaitVisible(selector: BySelector, what: String, timeoutMs: Long = TIMEOUT_MS) {
    check(device.wait(Until.hasObject(selector), timeoutMs)) { "$what did not appear" }
}

internal fun MacrobenchmarkScope.awaitGone(selector: BySelector, what: String, timeoutMs: Long = TIMEOUT_MS) {
    check(device.wait(Until.gone(selector), timeoutMs)) { "$what did not go away" }
}

/** Opens a category's details sheet from the Budget screen and closes it again. */
internal fun MacrobenchmarkScope.openCategoryDetailsAndBack() {
    click(By.text("Groceries"))
    awaitVisible(By.desc("Close sheet"), "The category details sheet")
    navigateBack()
    awaitGone(By.desc("Close sheet"), "The category details sheet")
}

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
