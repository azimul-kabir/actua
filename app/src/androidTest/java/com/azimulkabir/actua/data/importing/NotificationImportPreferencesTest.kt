package com.azimulkabir.actua.data.importing

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
