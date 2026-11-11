package com.azimulkabir.actua.data.reports

import java.lang.ref.SoftReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportDataCacheTest {
    private var now = 1_000L
    private val ttl = 120_000L
    private val loads = AtomicInteger()
    private val key = ReportDataCache.Key("budget-a", "clock-1")

    private fun cache() = ReportDataCache<List<String>>(ttlMillis = ttl, nowMillis = { now })

    private fun load(): List<String> = listOf("load ${loads.incrementAndGet()}")

    @Test
    fun aSecondAskWithinTheTimeLimitIsServedFromMemory() {
        val cache = cache()
        val first = cache.get(key, ::load)
        now += ttl - 1
        val second = cache.get(key, ::load)
        assertSame(first, second)
        assertEquals(1, loads.get())
    }

    @Test
    fun theTimeLimitIsABackstop() {
        val cache = cache()
        val first = cache.get(key, ::load)
        now += ttl
        assertNotSame(first, cache.get(key, ::load))
        assertEquals(2, loads.get())
    }

    @Test
    fun changedDataIsReadAgain() {
        val cache = cache()
        cache.get(key, ::load)
        // A write or sync changes the clock, so the token and so the key.
        val changed = cache.get(key.copy(token = "clock-2"), ::load)
        assertEquals(listOf("load 2"), changed)
        assertEquals("the old key is not served for the new data", listOf("load 3"), cache.get(key, ::load))
    }

    @Test
    fun anotherBudgetIsNeverServedTheFirstOnesData() {
        val cache = cache()
        cache.get(key, ::load)
        assertEquals(listOf("load 2"), cache.get(key.copy(budgetId = "budget-b"), ::load))
    }

    @Test
    fun aFailedLoadIsNotCachedAndLeavesWhatIsHeldAlone() {
        val cache = cache()
        val held = cache.get(key, ::load)
        val failing = key.copy(token = "clock-2")
        var failed = false
        try {
            cache.get(failing) { throw IllegalStateException("disk error") }
        } catch (e: IllegalStateException) {
            failed = true
        }
        assertTrue(failed)
        assertSame("the held data is still served", held, cache.get(key, ::load))
        assertEquals("the failure was not remembered", listOf("load 2"), cache.get(failing, ::load))
    }

    @Test
    fun dataThatWasGarbageCollectedIsLoadedAgain() {
        val collected = ReportDataCache<List<String>>(
            ttlMillis = ttl, nowMillis = { now },
            reference = { value -> object : SoftReference<List<String>>(value) { override fun get(): List<String>? = null } },
        )
        collected.get(key, ::load)
        collected.get(key, ::load)
        assertEquals(2, loads.get())
    }

    @Test
    fun clearForgetsWhatIsHeld() {
        val cache = cache()
        cache.get(key, ::load)
        cache.clear()
        cache.get(key, ::load)
        assertEquals(2, loads.get())
    }

    @Test
    fun widgetsAskingAtTheSameTimeCauseOneLoad() {
        val cache = ReportDataCache<List<String>>()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val results = (1..4).map {
                pool.submit<List<String>> {
                    start.await()
                    cache.get(key) { Thread.sleep(150); load() }
                }
            }
            start.countDown()
            val values = results.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, loads.get())
            assertTrue("every caller got the one loaded value", values.all { it === values.first() })
        } finally {
            pool.shutdownNow()
        }
    }
}
