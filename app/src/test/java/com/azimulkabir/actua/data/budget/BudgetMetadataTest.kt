package com.azimulkabir.actua.data.budget

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetMetadataTest {
    @Test
    fun classifiesDemoBudgetCorrectly() {
        val metadataFromDemoId = BudgetMetadata(
            id = DemoBudgetManager.BUDGET_ID,
            budgetName = "Actua Demo Budget",
            cloudFileId = null,
            groupId = null,
            resetClock = null,
            lastUploaded = null,
            encryptKeyId = null,
        )
        assertTrue(metadataFromDemoId.isDemo)
        assertFalse(metadataFromDemoId.isServerBacked)
        assertFalse(metadataFromDemoId.isUserLocalOnly)

        val jsonWithDemoFlag = JSONObject()
            .put("id", "custom-demo-id")
            .put("budgetName", "Custom Demo")
            .put("demo", true)
        val metadataFromFlag = BudgetMetadata.fromJson(jsonWithDemoFlag)
        assertTrue(metadataFromFlag.isDemo)
        assertFalse(metadataFromFlag.isServerBacked)
        assertFalse(metadataFromFlag.isUserLocalOnly)

        val legacyDemoJson = JSONObject()
            .put("id", DemoBudgetManager.BUDGET_ID)
            .put("budgetName", DemoBudgetManager.BUDGET_NAME)
        val metadataFromLegacy = BudgetMetadata.fromJson(legacyDemoJson)
        assertTrue(metadataFromLegacy.isDemo)
        assertFalse(metadataFromLegacy.isServerBacked)
        assertFalse(metadataFromLegacy.isUserLocalOnly)
    }

    @Test
    fun classifiesServerBackedBudgetCorrectly() {
        val metadata = BudgetMetadata(
            id = "server-budget-123",
            budgetName = "My Server Budget",
            cloudFileId = "cloud-file-abc",
            groupId = "group-xyz",
            resetClock = false,
            lastUploaded = "2026-10-18",
            encryptKeyId = "enc-key-1",
        )
        assertFalse(metadata.isDemo)
        assertTrue(metadata.isServerBacked)
        assertFalse(metadata.isUserLocalOnly)

        val json = JSONObject()
            .put("id", "server-budget-456")
            .put("budgetName", "My Server Budget 2")
            .put("cloudFileId", "cloud-file-def")
            .put("groupId", "group-123")
            .put("resetClock", true)
            .put("lastUploaded", "2026-10-18")
            .put("encryptKeyId", "key-99")
        val parsed = BudgetMetadata.fromJson(json)
        assertFalse(parsed.isDemo)
        assertTrue(parsed.isServerBacked)
        assertFalse(parsed.isUserLocalOnly)
        assertEquals("server-budget-456", parsed.id)
        assertEquals("cloud-file-def", parsed.cloudFileId)
        assertEquals("group-123", parsed.groupId)
        assertEquals(true, parsed.resetClock)
        assertEquals("key-99", parsed.encryptKeyId)
    }

    @Test
    fun classifiesStandaloneUserLocalOnlyBudgetCorrectly() {
        val standalone = BudgetMetadata(
            id = "standalone-budget-789",
            budgetName = "My Standalone Budget",
            cloudFileId = null,
            groupId = null,
            resetClock = null,
            lastUploaded = null,
            encryptKeyId = null,
        )
        assertFalse(standalone.isDemo)
        assertFalse(standalone.isServerBacked)
        assertTrue(standalone.isUserLocalOnly)

        val json = JSONObject()
            .put("id", "standalone-budget-101")
            .put("budgetName", "My Offline Budget")
        val parsed = BudgetMetadata.fromJson(json)
        assertFalse(parsed.isDemo)
        assertFalse(parsed.isServerBacked)
        assertTrue(parsed.isUserLocalOnly)
        assertNull(parsed.cloudFileId)
        assertNull(parsed.groupId)
        assertNull(parsed.resetClock)
        assertNull(parsed.lastUploaded)
        assertNull(parsed.encryptKeyId)
    }

    @Test
    fun handlesBlankCloudFileIdSafelyAsUserLocalOnly() {
        val blankCloudId = BudgetMetadata(
            id = "standalone-with-blank-cloud",
            budgetName = "Blank Cloud Budget",
            cloudFileId = "   ",
            groupId = null,
            resetClock = null,
            lastUploaded = null,
            encryptKeyId = null,
        )
        assertFalse(blankCloudId.isDemo)
        assertFalse(blankCloudId.isServerBacked)
        assertTrue(blankCloudId.isUserLocalOnly)

        val jsonWithBlank = JSONObject()
            .put("id", "json-blank-cloud")
            .put("cloudFileId", "   ")
        val parsed = BudgetMetadata.fromJson(jsonWithBlank)
        assertFalse(parsed.isDemo)
        assertFalse(parsed.isServerBacked)
        assertTrue(parsed.isUserLocalOnly)
        assertNull(parsed.cloudFileId)
    }

    @Test(expected = BudgetFileException.InvalidMetadata::class)
    fun throwsWhenIdIsMissing() {
        BudgetMetadata.fromJson(JSONObject().put("budgetName", "No ID"))
    }

    @Test(expected = BudgetFileException.InvalidMetadata::class)
    fun throwsWhenIdIsBlank() {
        BudgetMetadata.fromJson(JSONObject().put("id", "   ").put("budgetName", "Blank ID"))
    }
}
