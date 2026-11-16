package com.azimulkabir.actua.ui.navigation

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class ReorderQueueTest {
    private class Fixture(scope: CoroutineScope) {
        val initial = listOf("a", "b", "c")
        val database = AtomicReference(initial)
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val failures = mutableListOf<Pair<Int?, Throwable?>>()
        var changes = 0
        var read: () -> List<String> = { database.get() }
        val main = Thread.currentThread()
        val queue = ReorderQueue(scope, initial,
            read = { assertNotSame(main, Thread.currentThread()); read() },
            onChanged = { assertSame(main, Thread.currentThread()); changes++ },
            onFailure = { label, error -> failures += label to error },
        )
        fun end(id: String, result: Boolean = true, before: () -> Unit = {}): Boolean = queue.submit(
            id.first().code,
            optimistic = { it.filterNot { item -> item == id } + id },
        ) {
            assertNotSame(main, Thread.currentThread())
            calls += id
            before()
            if (result) database.set(database.get().filterNot { it == id } + id)
            result
        }
        suspend fun settled() = withTimeout(5_000) { while (queue.view.saving) delay(1) }
    }

    @Test fun rapidMovesAreOptimisticAndWritesAreFifoOffMain() = runBlocking {
        val f = Fixture(this)
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        try {
            assertTrue(f.end("a") { started.complete(Unit); check(release.await(5, TimeUnit.SECONDS)) })
            withTimeout(5_000) { started.await() }
            assertTrue(f.end("b"))
            assertEquals(listOf("c", "a", "b"), f.queue.view.snapshot)
            assertEquals(listOf("a"), f.calls.toList())
            assertTrue(f.queue.view.saving)
            release.countDown()
            f.settled()
            assertEquals(listOf("a", "b"), f.calls.toList())
            assertEquals(listOf("c", "a", "b"), f.database.get())
            assertEquals(f.database.get(), f.queue.view.snapshot)
            assertEquals(2, f.changes)
        } finally { release.countDown(); f.queue.close() }
    }

    @Test fun olderRejectionDoesNotOverwriteANewerSuccessfulMove() = runBlocking {
        val f = Fixture(this)
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        try {
            f.end("a", result = false) { started.complete(Unit); check(release.await(5, TimeUnit.SECONDS)) }
            withTimeout(5_000) { started.await() }
            f.end("b")
            assertEquals(listOf("c", "a", "b"), f.queue.view.snapshot)
            release.countDown()
            f.settled()
            assertEquals(listOf("a", "c", "b"), f.queue.view.snapshot)
            assertEquals(f.database.get(), f.queue.view.snapshot)
            assertEquals(listOf('a'.code to null), f.failures)
            assertEquals(1, f.changes)
        } finally { release.countDown(); f.queue.close() }
    }

    @Test fun rejectedMovePublishesARevisionEvenWhenSourceOrderIsUnchanged() = runBlocking {
        val f = Fixture(this)
        try {
            assertTrue(f.end("a", result = false))
            f.settled()
            assertEquals(f.initial, f.queue.view.snapshot)
            assertTrue(f.queue.view.revision > 0)
            assertEquals(0, f.changes)
            assertEquals(1, f.failures.size)
        } finally { f.queue.close() }
    }

    @Test fun writeExceptionIsReportedAndDoesNotStopLaterMoves() = runBlocking {
        val f = Fixture(this)
        try {
            f.queue.submit(1, { it.reversed() }) { error("write failed") }
            f.end("b")
            f.settled()
            assertEquals(listOf("a", "c", "b"), f.queue.view.snapshot)
            assertEquals("write failed", f.failures.single().second!!.message)
            assertEquals(1, f.changes)
        } finally { f.queue.close() }
    }

    @Test fun aRefreshFinishingAfterANewerGestureCannotEraseIt() = runBlocking {
        val f = Fixture(this)
        val readStarted = CompletableDeferred<Unit>()
        val readRelease = CountDownLatch(1)
        val writeStarted = CompletableDeferred<Unit>()
        val writeRelease = CountDownLatch(1)
        try {
            var first = true
            f.read = {
                val snapshot = f.database.get()
                if (first) {
                    first = false
                    readStarted.complete(Unit)
                    check(readRelease.await(5, TimeUnit.SECONDS))
                }
                snapshot
            }
            f.queue.refresh()
            withTimeout(5_000) { readStarted.await() }
            f.end("a") { writeStarted.complete(Unit); check(writeRelease.await(5, TimeUnit.SECONDS)) }
            readRelease.countDown()
            withTimeout(5_000) { writeStarted.await() }
            assertEquals(listOf("b", "c", "a"), f.queue.view.snapshot)
            assertTrue(f.queue.view.saving)
            writeRelease.countDown()
            f.settled()
            assertEquals(listOf("b", "c", "a"), f.queue.view.snapshot)
        } finally { readRelease.countDown(); writeRelease.countDown(); f.queue.close() }
    }

    @Test fun disposalCancelsQueuedMovesAndPreventsOldBudgetCallbacks() = runBlocking {
        val old = Fixture(this)
        val next = Fixture(this)
        val started = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        try {
            old.end("a") {
                started.complete(Unit)
                check(release.await(5, TimeUnit.SECONDS))
                finished.complete(Unit)
            }
            withTimeout(5_000) { started.await() }
            old.end("b")
            val stopped = old.queue.close()
            assertFalse(old.end("c"))
            next.end("c")
            next.settled()
            release.countDown()
            withTimeout(5_000) { finished.await() }
            stopped?.join()
            assertEquals(listOf("a"), old.calls.toList())
            assertEquals(0, old.changes)
            assertTrue(old.failures.isEmpty())
            assertEquals(next.initial, next.queue.view.snapshot)
            assertEquals(1, next.changes)
        } finally { release.countDown(); old.queue.close(); next.queue.close() }
    }

    @Test fun failedRefreshReturnsToConfirmedDataAndReportsTheError() = runBlocking {
        val f = Fixture(this)
        try {
            f.read = { error("read failed") }
            f.queue.refresh()
            f.settled()
            assertEquals(f.initial, f.queue.view.snapshot)
            assertEquals("read failed", f.failures.single().second!!.message)
            assertFalse(f.queue.view.saving)
        } finally { f.queue.close() }
    }
}
