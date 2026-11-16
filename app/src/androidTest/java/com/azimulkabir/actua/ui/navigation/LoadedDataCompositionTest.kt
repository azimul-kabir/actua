package com.azimulkabir.actua.ui.navigation

import android.os.Looper
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** Exercise real composition/coroutine state, including blocking reads that finish after cancellation. */
class LoadedDataCompositionTest {
    @get:Rule val compose = createComposeRule()
    private val releases = mutableListOf<CountDownLatch>()

    @After fun unblockReaders() { releases.forEach(CountDownLatch::countDown) }

    // waitUntil advances Compose's test clock while observing IO work. Blocking the test thread
    // on a latch after a state change prevents the next frame (and its LaunchedEffect) from running.
    private fun releaseGate() = CountDownLatch(1).also(releases::add)

    @Test fun refreshKeepsTheLoadedValueAndReadsOffTheMainThread() {
        val version = mutableIntStateOf(0)
        val observed = AtomicReference<String>()
        val readOnMain = AtomicBoolean(false)
        val started = CountDownLatch(1)
        val release = releaseGate()
        compose.setContent {
            val currentVersion = version.intValue
            val value = rememberLoaded("budget", currentVersion, initial = "Loading") {
                readOnMain.set(readOnMain.get() || Thread.currentThread() == Looper.getMainLooper().thread)
                if (currentVersion == 1) {
                    started.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                }
                "Loaded $currentVersion"
            }
            SideEffect { observed.set(value) }
            Text(value)
        }
        compose.waitUntil(5_000) { observed.get() == "Loaded 0" }
        compose.runOnIdle { version.intValue = 1 }
        compose.waitUntil(5_000) { started.count == 0L }
        compose.onNodeWithText("Loaded 0").assertExists()
        compose.onNodeWithText("Loading").assertDoesNotExist()
        release.countDown()
        compose.waitUntil(5_000) { observed.get() == "Loaded 1" }
        assertFalse(readOnMain.get())
    }

    @Test fun changingSelectionDiscardsOldDataAndIgnoresACancelledRead() {
        val selection = mutableStateOf("account-a")
        val version = mutableIntStateOf(0)
        val observed = AtomicReference<String>()
        val obsoleteStarted = CountDownLatch(1)
        val obsoleteFinished = CountDownLatch(1)
        val obsoleteRelease = releaseGate()
        val nextStarted = CountDownLatch(1)
        val nextRelease = releaseGate()
        compose.setContent {
            val account = selection.value
            val currentVersion = version.intValue
            val value = rememberLoaded("budget", currentVersion, initial = "Loading", selectionKey = account) {
                if (account == "account-a" && currentVersion == 1) {
                    obsoleteStarted.countDown()
                    check(obsoleteRelease.await(10, TimeUnit.SECONDS))
                    obsoleteFinished.countDown()
                } else if (account == "account-b") {
                    nextStarted.countDown()
                    check(nextRelease.await(10, TimeUnit.SECONDS))
                }
                "$account/$currentVersion"
            }
            SideEffect { observed.set(value) }
            Text(value)
        }
        compose.waitUntil(5_000) { observed.get() == "account-a/0" }
        compose.runOnIdle { version.intValue = 1 }
        compose.waitUntil(5_000) { obsoleteStarted.count == 0L }
        compose.runOnIdle { selection.value = "account-b" }
        compose.waitUntil(5_000) { nextStarted.count == 0L }
        compose.onNodeWithText("Loading").assertExists()
        compose.onNodeWithText("account-a/0").assertDoesNotExist()
        obsoleteRelease.countDown()
        compose.waitUntil(5_000) { obsoleteFinished.count == 0L }
        compose.onNodeWithText("Loading").assertExists()
        compose.onNodeWithText("account-a/1").assertDoesNotExist()
        nextRelease.countDown()
        compose.waitUntil(5_000) { observed.get() == "account-b/1" }
    }

    @Test fun switchingBudgetsClearsTheSameSelectionsData() {
        val budget = mutableStateOf("budget-a")
        val observed = AtomicReference<String>()
        val started = CountDownLatch(1)
        val release = releaseGate()
        compose.setContent {
            val currentBudget = budget.value
            val value = rememberLoaded(currentBudget, initial = "Loading", selectionKey = "same-id") {
                if (currentBudget == "budget-b") {
                    started.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                }
                currentBudget
            }
            SideEffect { observed.set(value) }
            Text(value)
        }
        compose.waitUntil(5_000) { observed.get() == "budget-a" }
        compose.runOnIdle { budget.value = "budget-b" }
        compose.waitUntil(5_000) { started.count == 0L }
        compose.onNodeWithText("Loading").assertExists()
        compose.onNodeWithText("budget-a").assertDoesNotExist()
        release.countDown()
        compose.waitUntil(5_000) { observed.get() == "budget-b" }
    }
}
