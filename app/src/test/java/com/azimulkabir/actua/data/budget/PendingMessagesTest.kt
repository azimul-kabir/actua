package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.sync.CrdtValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingMessagesTest {
    private val schema = mapOf("accounts" to setOf("id", "name"))

    @Test fun cellsForKnownSchemaAreApplied() {
        assertFalse(PendingMessages.shouldDefer(schema, "accounts", "name", "S:Checking"))
        assertFalse(PendingMessages.shouldDefer(schema, "accounts", "name", "0:"))
        assertFalse(PendingMessages.shouldDefer(schema, "accounts", "name", "N:12"))
    }

    @Test fun unknownTableOrColumnIsDeferred() {
        assertTrue(PendingMessages.shouldDefer(schema, "account_groups", "name", "S:Cash"))
        assertTrue(PendingMessages.shouldDefer(schema, "accounts", "future_column", "S:x"))
    }

    @Test fun unknownValueFormatIsDeferredEvenWhenTheColumnExists() {
        assertTrue(PendingMessages.shouldDefer(schema, "accounts", "name", "N16:abc"))
        assertTrue(PendingMessages.shouldDefer(schema, "accounts", "name", "X:abc"))
        assertTrue(PendingMessages.shouldDefer(schema, "accounts", "name", ""))
    }

    @Test fun datasetsActualNeverWritesToATableAreNotDeferred() {
        assertFalse(PendingMessages.shouldDefer(schema, "prefs", "anything", "S:x"))
        assertFalse(PendingMessages.shouldDefer(schema, "spreadsheet_cells", "anything", "S:x"))
    }

    @Test fun knownFormatsMatchTheDecoder() {
        listOf("0:", "N:1", "N:1.5", "S:", "S:text").forEach { assertTrue(it, CrdtValue.isKnownFormat(it)) }
        listOf("", "S", "SS:x", "N16:1", "Z:1").forEach { assertFalse(it, CrdtValue.isKnownFormat(it)) }
        assertEquals(CrdtValue.Text("a"), CrdtValue.deserialize("S:a"))
    }

    @Test fun migrationIsRecordedAndPorted() {
        assertTrue(BlankBudgetFactory.MIGRATIONS.contains(PendingMessages.MIGRATION))
        assertTrue(ActualMigrations.supports(PendingMessages.MIGRATION))
    }
}
