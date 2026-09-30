package com.azimulkabir.actua.data.importing

import android.content.Context
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NotificationImportPreferencesTest {
    @Test
    fun queueStoresNormalizedCandidatesAndCanBeDeleted() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("notification_import", Context.MODE_PRIVATE).edit().clear().commit()
        val store = NotificationImportPreferences(context)
        store.allowedPackages = setOf("com.example.bank", "com.example.sms")
        store.enabled = true
        store.enqueue(ImportCandidate(1, 20260913, "Cafe", "Imported from bank", -500, "ref-1",
            ImportConfidence.HIGH, "bank.package", "9876"))
        assertEquals("9876", store.queued().single().accountHint)
        assertEquals(setOf("com.example.bank", "com.example.sms"), store.allowedPackages)
        store.clearAll()
        assertFalse(store.enabled)
        assertEquals(emptyList<ImportCandidate>(), store.queued())
    }

    @Test
    fun automationReceiverQueuesOnlyAuthorizedBroadcasts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("notification_import", Context.MODE_PRIVATE).edit().clear().commit()
        val store = NotificationImportPreferences(context)
        fun send(token: String?) = AutomationIntentReceiver().onReceive(context,
            Intent(AutomationIntent.ACTION_QUEUE_TRANSACTION).putExtra("token", token)
                .putExtra("amount", -12.34).putExtra("payee", "Coffee").putExtra("date", "2026-09-30"))

        send("anything")
        assertEquals(emptyList<ImportCandidate>(), store.queued())

        store.automationEnabled = true
        val token = store.automationToken
        assertNotNull(token)
        send("wrong")
        assertEquals(emptyList<ImportCandidate>(), store.queued())
        send(token)
        val queued = store.queued().single()
        assertEquals(-1_234L, queued.amountCents)
        assertEquals(20260930, queued.date)
        assertEquals("Coffee", queued.payee)

        assertNotEquals(token, store.regenerateAutomationToken())
        send(token)
        assertEquals(1, store.queued().size)

        store.clearAll()
        assertFalse(store.automationEnabled)
    }
}
