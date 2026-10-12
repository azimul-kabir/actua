package com.azimulkabir.actua.data.importing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The Accounts tab's pending-capture badge follows every change to the review queue (#1050). */
@RunWith(AndroidJUnit4::class)
class PendingCaptureQueueTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val store = NotificationImportPreferences(instrumentation.targetContext)
    private val coffee = ImportCandidate(1, 20261012, "Coffee", "Imported from Bank", -500, sourceLabel = "Bank")

    @Before @After fun clear() = store.clearAll()

    @Test fun observedCountFollowsCapturesImportsAndDeletion() {
        val counts = mutableListOf<Int>()
        val stop = store.observeQueue { counts += store.queued().size }
        fun settle() = instrumentation.waitForIdleSync()

        val first = store.enqueue(coffee); settle()
        store.enqueue(coffee.copy(payee = "Fuel")); settle()
        store.removeQueued(requireNotNull(first.queueId)); settle()
        store.replaceQueue(emptyList()); settle()
        store.enqueue(coffee); settle()
        store.clearAll(); settle()
        assertEquals(listOf(1, 2, 1, 0, 1, 0), counts)

        stop()
        store.enqueue(coffee); settle()
        assertEquals(6, counts.size)
    }
}
