package com.azimulkabir.actua.data.importing

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportPreferencesTest {
    @Test
    fun profilesAndBoundedHistoryRoundTripLocally() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("transaction_import", Context.MODE_PRIVATE).edit().clear().commit()
        val store = ImportPreferences(context)
        val mapping = ImportColumnMapping(
            listOf(ImportColumnRole.DATE, ImportColumnRole.PAYEE, ImportColumnRole.AMOUNT),
            "dd/MM/yyyy", expensesArePositive = true,
        )
        store.saveProfile("My bank", mapping)
        assertEquals(mapping, store.profile("My bank"))

        repeat(22) { index ->
            store.addHistory(ImportHistoryEntry("statement-$index.csv", StatementFormat.CSV, "Checking",
                imported = 2, skipped = 1, timestampMillis = index.toLong()))
        }
        assertEquals(20, store.history().size)
        assertEquals("statement-21.csv", store.history().first().sourceName)
        store.clearHistory()
        assertEquals(emptyList<ImportHistoryEntry>(), store.history())
    }
}
