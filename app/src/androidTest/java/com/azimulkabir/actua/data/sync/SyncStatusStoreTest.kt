package com.azimulkabir.actua.data.sync

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncStatusStoreTest {
    @Test
    fun appOpenSyncRecordsTriggerDurationAndRefreshTime() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("actua-sync-status", Context.MODE_PRIVATE).edit().clear().commit()
        val store = SyncStatusStore(context)

        store.started("App open", now = 1_000L)
        assertTrue(store.read().running)
        assertEquals("App open", store.read().activeTrigger)

        store.succeeded(SyncOutcome(2, 3, 1, "timestamp"), now = 2_500L)
        val completed = store.read()
        assertFalse(completed.running)
        assertEquals(1_500L, completed.lastDurationMillis)
        assertEquals(2_500L, completed.lastForegroundRefreshMillis)
        assertEquals(2, completed.sentMessages)
        assertEquals(3, completed.receivedMessages)

        store.foregroundRefreshFinished(now = 3_000L)
        assertEquals(3_000L, store.read().lastForegroundRefreshMillis)
    }
}
