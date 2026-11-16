package com.azimulkabir.actua.data

import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.ui.navigation.NavigationMutationFeedback
import com.azimulkabir.actua.ui.navigation.NavigationMutationRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RepositoryBackgroundOperationTest {
    @Test fun cancelledSearchKeepsTheConnectionUntilBlockingWorkReturns() =
        checkSearchAfterClose(cancelCaller = true)

    @Test fun budgetSwitchDiscardsOldSearchResultsEvenIfItsCallerRemainsActive() =
        checkSearchAfterClose(cancelCaller = false)

    private fun checkSearchAfterClose(cancelCaller: Boolean) = withRepository { repository ->
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val published = AtomicBoolean()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val job = scope.launch {
            repository.withBackgroundOperation {
                entered.countDown()
                check(resume.await(15, TimeUnit.SECONDS))
                repository.transactions("Checking")
            }
            published.set(true)
        }
        try {
            assertTrue(entered.await(15, TimeUnit.SECONDS))
            if (cancelCaller) job.cancel()
            repository.close()
            repository.accounts()
            resume.countDown()
            runBlocking { job.join() }
            assertTrue(job.isCancelled)
            assertFalse(published.get())
            assertThrows(IllegalStateException::class.java) { repository.accounts() }
        } finally {
            resume.countDown()
            scope.cancel()
            runBlocking { job.join() }
        }
    }

    @Test fun cancelledMutationFinishesItsLocalWriteBeforeClosing() = withRepository { repository ->
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val feedback = NavigationMutationFeedback()
        val published = AtomicBoolean()
        val runner = NavigationMutationRunner(
            scope = scope,
            feedback = feedback,
            labelFor = { "Synthetic account" },
            incompleteFormat = "%s incomplete",
            failedFormat = "%s failed",
            onInvalidated = { published.set(true) },
            categoryAvailableCents = { emptyMap() },
            repository = repository,
        )
        val job = runner.mutate(0) {
            entered.countDown()
            check(resume.await(15, TimeUnit.SECONDS))
            repository.createAccount("Retained write", offBudget = false, startingBalance = "0")
        }
        try {
            assertTrue(entered.await(15, TimeUnit.SECONDS))
            job.cancel()
            repository.close()
            resume.countDown()
            runBlocking { job.join() }
            assertFalse(published.get())
            assertEquals(null, feedback.error.value)
            assertThrows(IllegalStateException::class.java) { repository.accounts() }
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val reopened = ActuaRepository(context)
            try {
                assertTrue(reopened.accounts().any { it.name == "Retained write" })
            } finally { reopened.close() }
        } finally {
            resume.countDown()
            scope.cancel()
            runBlocking { job.join() }
        }
    }

    @Test fun closeWaitsForEveryConcurrentOperationIncludingExceptionalCompletion() = withRepository { repository ->
        val entered = CountDownLatch(2)
        val resumeReport = CountDownLatch(1)
        val resumeCallback = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val report = executor.submit {
                assertThrows(CancellationException::class.java) {
                    repository.withRetainedAccess {
                        entered.countDown()
                        check(resumeReport.await(15, TimeUnit.SECONDS))
                        repository.reports()
                    }
                }
            }
            val callback = executor.submit {
                assertThrows(IllegalArgumentException::class.java) {
                    repository.withRetainedAccess {
                        entered.countDown()
                        check(resumeCallback.await(15, TimeUnit.SECONDS))
                        repository.accounts()
                        throw IllegalArgumentException("Synthetic callback failure")
                    }
                }
            }
            assertTrue(entered.await(15, TimeUnit.SECONDS))
            repository.close()
            resumeReport.countDown()
            report.get(15, TimeUnit.SECONDS)
            // Finishing one operation must not close the connection still used by the other.
            repository.accounts()
            resumeCallback.countDown()
            callback.get(15, TimeUnit.SECONDS)
            assertThrows(IllegalStateException::class.java) { repository.accounts() }
        } finally {
            resumeReport.countDown()
            resumeCallback.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS))
        }
    }

    @Test fun staleCallbacksAndDispatchedOperationsCannotStartAfterClose() = withRepository { repository ->
        repository.close()
        var started = false
        assertThrows(CancellationException::class.java) {
            repository.withRetainedAccess { started = true }
        }
        val result = runBlocking {
            runCatching { repository.withBackgroundOperation { started = true } }
        }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertFalse(started)
    }

    private fun withRepository(test: (ActuaRepository) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val active = ActiveBudgetStore(context)
        val previous = active.budgetId
        val budget = files.createBudget("Background ${UUID.randomUUID()}")
        active.budgetId = budget.id
        val repository = ActuaRepository(context)
        try {
            assertEquals(budget.id, repository.activeMetadata?.id)
            test(repository)
        } finally {
            repository.close()
            active.budgetId = previous
            files.deleteBudget(budget.id)
        }
    }
}
