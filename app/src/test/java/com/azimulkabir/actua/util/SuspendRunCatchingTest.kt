package com.azimulkabir.actua.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SuspendRunCatchingTest {
    @Test fun returnsTheValueOfASuccessfulBlock() = runBlocking {
        val result = suspendRunCatching { withContext(Dispatchers.Default) { 42 } }
        assertEquals(42, result.getOrNull())
    }

    @Test fun capturesOrdinaryFailures() = runBlocking {
        val result = suspendRunCatching<Int> { withContext(Dispatchers.Default) { throw IllegalStateException("bank said no") } }
        // Coroutines may hand back a copy of the exception with a recovered stack trace, so compare type and message.
        val captured = result.exceptionOrNull()
        assertTrue(captured is IllegalStateException)
        assertEquals("bank said no", captured?.message)
    }

    @Test fun rethrowsCancellationInsteadOfCapturingIt() = runBlocking {
        val cancellation = CancellationException("left the screen")
        try {
            suspendRunCatching<Int> { throw cancellation }
            throw AssertionError("cancellation was swallowed")
        } catch (thrown: CancellationException) {
            assertSame(cancellation, thrown)
        }
    }

    @Test fun aCancelledCoroutineStopsInsteadOfRunningTheFailureBranch() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var failureBranchRan = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            suspendRunCatching {
                started.complete(Unit)
                delay(60_000)
            }.onFailure { failureBranchRan = true }
        }
        started.await()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(failureBranchRan)
    }
}
