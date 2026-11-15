package com.azimulkabir.actua.ui.navigation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NavigationMutationRunnerTest {
    private class Fixture(val scope: CoroutineScope) {
        val feedback = NavigationMutationFeedback()
        val events = mutableListOf<String>()
        var balance = 100L
        fun runner(locale: String = "en") = NavigationMutationRunner(
            scope, feedback, { "$locale:$it" }, "Incomplete %s", "Failed %s",
            onInvalidated = { events += "invalidate" },
            categoryAvailableCents = { events += "read:$balance"; mapOf("Food" to balance) },
            ioDispatcher = Dispatchers.Unconfined,
            onRejected = { events += "rejected:$it" },
            onFailed = { label, _ -> events += "failed:$label" },
        )
    }

    @Test fun async_variants_invalidate_before_completion() = runBlocking {
        for (async in listOf(false, true)) {
            val f = Fixture(this)
            val runner = f.runner()
            val action = { f.events += "write"; true }
            val complete = { f.events += "complete"; Unit }
            val job = if (async) runner.mutateAsync(1, action, complete) else runner.mutate(1, complete, action)
            job.join()
            assertEquals(listOf("write", "invalidate", "complete"), f.events)
            assertNull(f.feedback.error.value)
        }
    }

    @Test fun impact_variants_snapshot_around_write_and_publish_before_completion() = runBlocking {
        for (async in listOf(false, true)) {
            val f = Fixture(this)
            val runner = f.runner()
            val action = { f.events += "write"; f.balance = 75; true }
            val complete = {
                assertEquals(1, f.feedback.impactCues.value.size)
                f.events += "complete"
                Unit
            }
            val job = if (async) runner.mutateAsyncWithImpactCue(1, setOf("Food"), action, complete)
                else runner.mutateWithImpactCue(1, setOf("Food"), complete, action)
            job.join()
            assertEquals(listOf("read:100", "write", "invalidate", "read:75", "complete"), f.events)
        }
    }

    @Test fun rejected_or_throwing_actions_never_invalidate_or_complete() = runBlocking {
        for (impact in listOf(false, true)) {
            for (throws in listOf(false, true)) {
                val f = Fixture(this)
                val action: () -> Boolean = { if (throws) error("write failed") else false }
                val complete = { fail("must not complete") }
                val job = if (impact) f.runner().mutateWithImpactCue(2, setOf("Food"), complete, action)
                    else f.runner().mutate(2, complete, action)
                job.join()
                assertFalse("invalidate" in f.events)
                assertEquals(if (throws) "write failed" else "Incomplete en:2", f.feedback.error.value)
                assertEquals(if (throws) "failed:en:2" else "rejected:en:2", f.events.last())
            }
        }
    }

    @Test fun blank_exception_uses_localized_fallback() = runBlocking {
        val f = Fixture(this)
        f.runner("pt").mutate(3) { throw IllegalStateException(" ") }.join()
        assertEquals("Failed pt:3", f.feedback.error.value)
    }

    @Test fun sync_returns_result_and_invalidates_only_success_on_calling_thread() = runBlocking {
        val f = Fixture(this)
        val runner = f.runner()
        val thread = Thread.currentThread()
        assertTrue(runner.mutateSync(1) { assertSame(thread, Thread.currentThread()); true })
        assertFalse(runner.mutateSync(1) { false })
        assertFalse(runner.mutateSync(1) { error("failed") })
        assertEquals(1, f.events.count { it == "invalidate" })
    }

    @Test fun new_composition_inputs_keep_feedback_but_use_updated_locale() = runBlocking {
        val f = Fixture(this)
        f.runner("en").mutate(1) { false }.join()
        f.runner("es").mutate(1) { false }.join()
        assertEquals("Incomplete es:1", f.feedback.error.value)
    }

    @Test fun disposed_scope_does_not_start_new_writes() = runBlocking {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        val f = Fixture(scope)
        scope.cancel()
        f.runner().mutate(1) { fail("disposed scope must not write"); true }.join()
        assertTrue(f.events.isEmpty())
        assertNull(f.feedback.error.value)
    }
}
