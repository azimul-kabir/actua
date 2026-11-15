package com.azimulkabir.actua.ui.navigation

import android.os.StrictMode
import android.os.strictmode.Violation
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BudgetFileManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class RepositoryStateTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun launchAndBudgetSwitchOpenOffMainAndCloseThePreviousRepository() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val active = ActiveBudgetStore(context)
        val previous = active.budgetId
        val first = files.createBudget("First ${UUID.randomUUID()}")
        val second = files.createBudget("Second ${UUID.randomUUID()}")
        val violations = CopyOnWriteArrayList<Violation>()
        val latest = AtomicReference<RepositoryState?>()
        val sawLoading = AtomicBoolean()
        val version = mutableIntStateOf(0)
        val showing = mutableStateOf(true)
        var previousPolicy: StrictMode.ThreadPolicy? = null
        try {
            active.budgetId = first.id
            composeRule.runOnUiThread {
                previousPolicy = StrictMode.getThreadPolicy()
                StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .penaltyListener({ it.run() }) { violations += it }
                    .build())
            }
            composeRule.setContent {
                if (showing.value) {
                    val state = rememberRepository(context, version.intValue)
                    if (state == null) sawLoading.set(true)
                    latest.set(state)
                }
            }
            composeRule.waitUntil(15_000) { latest.get()?.repository?.activeMetadata?.id == first.id }
            assertTrue(sawLoading.get())
            val old = requireNotNull(latest.get()).repository
            active.budgetId = second.id
            composeRule.runOnIdle { version.intValue++ }
            composeRule.waitUntil(15_000) { latest.get()?.repository?.activeMetadata?.id == second.id }
            composeRule.waitForIdle()
            assertEquals(1, latest.get()?.version)
            assertThrows(IllegalStateException::class.java) { old.accounts() }

            val budgetReadsOnMain = violations.filter { violation ->
                violation.stackTrace.any {
                    it.className.startsWith("com.azimulkabir.actua.data.ActuaRepository") ||
                        it.className.startsWith("com.azimulkabir.actua.data.budget.BudgetOpenProbe") ||
                        it.className.startsWith("com.azimulkabir.actua.data.budget.ActualBudgetDatabase") ||
                        it.className.startsWith("com.azimulkabir.actua.data.budget.BudgetFileManager")
                }
            }
            assertTrue(budgetReadsOnMain.joinToString("\n") { it.stackTraceToString() }, budgetReadsOnMain.isEmpty())
        } finally {
            composeRule.runOnUiThread { previousPolicy?.let(StrictMode::setThreadPolicy) }
            composeRule.runOnIdle { showing.value = false }
            composeRule.waitForIdle()
            active.budgetId = previous
            files.deleteBudget(first.id)
            files.deleteBudget(second.id)
        }
    }
}
