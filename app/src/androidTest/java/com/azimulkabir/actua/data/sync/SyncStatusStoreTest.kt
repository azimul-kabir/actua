package com.azimulkabir.actua.data.sync

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.sync.SYNC_TRIGGER_APP_OPEN
import com.azimulkabir.actua.data.sync.SYNC_TRIGGER_MANUAL
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
        assertEquals(SYNC_TRIGGER_APP_OPEN, store.read().activeTrigger)
        // Legacy persisted labels normalize to the same stable code.
        store.started("Manual", now = 1_100L)
        assertEquals(SYNC_TRIGGER_MANUAL, store.read().activeTrigger)

        store.started(SYNC_TRIGGER_APP_OPEN, now = 2_000L)
        store.succeeded(SyncOutcome(2, 3, 1, "timestamp"), now = 2_500L)
        val completed = store.read()
        assertFalse(completed.running)
        assertEquals(500L, completed.lastDurationMillis)
        assertEquals(2_500L, completed.lastForegroundRefreshMillis)
        assertEquals(2, completed.sentMessages)
        assertEquals(3, completed.receivedMessages)

        store.foregroundRefreshFinished(now = 3_000L)
        assertEquals(3_000L, store.read().lastForegroundRefreshMillis)
    }

    @Test
    fun failuresPersistStableCodesForDisplay() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("actua-sync-status", Context.MODE_PRIVATE).edit().clear().commit()
        val store = SyncStatusStore(context)

        store.failed(java.io.IOException("connection refused"))
        assertEquals(SyncErrorCode.NETWORK.persistedValue, store.read().error)

        store.failed(SyncStatusException.EncryptionKeyUnavailable)
        assertEquals(SyncErrorCode.ENCRYPTION_KEY_UNAVAILABLE.persistedValue, store.read().error)
    }
}
