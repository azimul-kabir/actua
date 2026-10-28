package com.azimulkabir.actua.data.bank

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Actual's `custom-sync-mappings-<id>` preference text, with synthetic values. */
class BankSyncMappingsTest {
    @Test fun `saved mappings read back as Actual's mappingsFromString would`() {
        val json = BankSyncMappings.toJson(
            payment = mapOf("date" to "originalDate", "payee" to "merchant.name", "notes" to "descriptionRaw"),
            deposit = BankSyncMappings.DEFAULT,
        )

        val parsed = BankSyncMappings.parse(json)

        assertEquals(mapOf("date" to "originalDate", "payee" to "merchant.name", "notes" to "descriptionRaw"), parsed["payment"])
        assertEquals(BankSyncMappings.DEFAULT, parsed["deposit"])
    }

    @Test fun `a missing field falls back to the default when saving`() {
        val root = JSONObject(BankSyncMappings.toJson(mapOf("payee" to "memo"), emptyMap()))

        assertEquals("memo", root.getJSONObject("payment").getString("payee"))
        assertEquals("date", root.getJSONObject("payment").getString("date"))
        assertEquals("notes", root.getJSONObject("deposit").getString("notes"))
    }

    @Test fun `text that is not JSON is rejected with a readable reason`() {
        try {
            BankSyncMappings.parse("not json")
            fail("expected a rejection")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("custom field mapping"))
        }
    }

    @Test fun `the preference id is per account`() {
        assertEquals("custom-sync-mappings-acct-1", BankSyncMappings.preferenceId("acct-1"))
        assertEquals("sync-import-pending-acct-1", BankSyncFlag.ImportPending.preferenceId("acct-1"))
    }
}
