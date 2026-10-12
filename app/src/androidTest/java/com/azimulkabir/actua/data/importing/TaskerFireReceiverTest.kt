package com.azimulkabir.actua.data.importing

import android.content.Intent
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The Tasker plugin's fire receiver queues through the automation path (#1040). */
@RunWith(AndroidJUnit4::class)
class TaskerFireReceiverTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = NotificationImportPreferences(context)

    @Before fun enable() {
        store.clearAll()
        store.automationEnabled = true
    }

    @After fun clear() = store.clearAll()

    @Test fun aSavedActionQueuesOneCandidate() {
        val token = requireNotNull(store.automationToken)
        fire(TaskerPlugin.bundleValues(mapOf("amount" to "-$12.34", "payee" to "Coffee"), token))

        val queued = store.queued().single()
        assertEquals(-1_234L, queued.amountCents)
        assertEquals("Coffee", queued.payee)
        assertNull(store.automationRejection)
    }

    @Test fun aStaleTokenQueuesNothingAndRecordsWhy() {
        fire(TaskerPlugin.bundleValues(mapOf("amount" to "5"), "not-the-token"))

        assertTrue(store.queued().isEmpty())
        assertEquals("Wrong token. ${TaskerPlugin.TOKEN_ADVICE}", store.automationRejection?.reason)
    }

    @Test fun otherActionsAreIgnored() {
        val token = requireNotNull(store.automationToken)
        TaskerFireReceiver().onReceive(context, intent(TaskerPlugin.bundleValues(mapOf("amount" to "5"), token))
            .setAction(TaskerPlugin.ACTION_EDIT_SETTING))
        assertTrue(store.queued().isEmpty())
    }

    private fun fire(values: Map<String, String>) = TaskerFireReceiver().onReceive(context, intent(values))

    private fun intent(values: Map<String, String>) = Intent(TaskerPlugin.ACTION_FIRE_SETTING)
        .putExtra(TaskerPlugin.EXTRA_BUNDLE, Bundle().apply { values.forEach { (k, v) -> putString(k, v) } })
}
