package com.azimulkabir.actua.data.budget

import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.network.ActualHttpResponse
import com.azimulkabir.actua.data.network.ActualServerClient
import com.azimulkabir.actua.data.network.ActualServerException
import com.azimulkabir.actua.data.sync.CrdtValue
import com.azimulkabir.actua.data.sync.HlcTimestamp
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/** Renaming a budget like Actual's `saveMetadataPrefs` + `savePrefs`, actua#1352. */
class BudgetRenameServiceTest {
    private class Env(val files: BudgetFileManager, val budget: BudgetMetadata) {
        val requests = mutableListOf<Pair<String, JSONObject>>()
        fun server(status: Int = 200, body: String = """{"status":"ok"}""") = ActualServerClient { request ->
            requests += request.url.path to JSONObject(request.body!!.decodeToString())
            ActualHttpResponse(status, body.encodeToByteArray())
        }

        fun prefsMessages() = ActualBudgetDatabase.open(files.databaseFile(budget.id)).use { database ->
            database.getMessagesSince(HlcTimestamp.ZERO.toString()).filter { it.dataset == "prefs" }
        }

        fun messageCount() = ActualBudgetDatabase.open(files.databaseFile(budget.id)).use {
            it.getMessagesSince(HlcTimestamp.ZERO.toString()).size
        }
    }

    @Test
    fun serverBudgetIsRenamedOnTheServerThenLoggedAndSavedLocally() = withBudget(serverBacked = true) { env ->
        val metadataBefore = JSONObject(env.files.metadataFile(env.budget.id).readText())

        val renamed = BudgetRenameService(env.server(), env.files).rename("https://actual.test", "tok", env.budget.id, "  Household  ")

        assertEquals("Household", renamed.budgetName)
        assertEquals("Household", env.files.metadata(env.budget.id)?.budgetName)
        // Every other metadata key is kept.
        val after = JSONObject(env.files.metadataFile(env.budget.id).readText())
        metadataBefore.keys().asSequence().filter { it != "budgetName" }.forEach { assertEquals(metadataBefore.get(it), after.get(it)) }

        assertEquals(1, env.requests.size)
        assertEquals("/sync/update-user-filename", env.requests.single().first)
        val body = env.requests.single().second
        assertEquals("tok", body.getString("token"))
        assertEquals(CLOUD_FILE_ID, body.getString("fileId"))
        assertEquals("Household", body.getString("name"))

        // Actual's savePrefs sends dataset "prefs", row "budgetName", column "value".
        val message = env.prefsMessages().single()
        assertEquals("budgetName", message.row)
        assertEquals("value", message.column)
        assertEquals(CrdtValue.serialize("Household"), message.value)
    }

    @Test
    fun serverRefusalLeavesTheOldNameAndLogsNothing() = withBudget(serverBacked = true) { env ->
        val oldName = env.files.metadata(env.budget.id)?.budgetName
        val before = env.messageCount()

        assertThrows(ActualServerException.FileAccessDenied::class.java) {
            BudgetRenameService(env.server(403, "file-access-not-allowed"), env.files)
                .rename("https://actual.test", "tok", env.budget.id, "Stolen")
        }
        assertThrows(ActualServerException.FileNotFound::class.java) {
            BudgetRenameService(env.server(400, "file-not-found"), env.files)
                .rename("https://actual.test", "tok", env.budget.id, "Gone")
        }

        assertEquals(oldName, env.files.metadata(env.budget.id)?.budgetName)
        assertEquals(before, env.messageCount())
        assertTrue(env.prefsMessages().isEmpty())
    }

    @Test
    fun standaloneBudgetIsRenamedLocallyWithoutAnyRequest() = withBudget(serverBacked = false) { env ->
        val before = env.messageCount()

        val renamed = BudgetRenameService(env.server(), env.files).rename("https://actual.test", null, env.budget.id, "Cash only")

        assertEquals("Cash only", renamed.budgetName)
        assertEquals("Cash only", env.files.metadata(env.budget.id)?.budgetName)
        assertTrue(env.requests.isEmpty())
        assertEquals(before, env.messageCount())
    }

    @Test
    fun serverBudgetNeedsASignedInToken() = withBudget(serverBacked = true) { env ->
        val oldName = env.files.metadata(env.budget.id)?.budgetName

        assertThrows(IllegalArgumentException::class.java) {
            BudgetRenameService(env.server(), env.files).rename("https://actual.test", null, env.budget.id, "New")
        }

        assertEquals(oldName, env.files.metadata(env.budget.id)?.budgetName)
        assertTrue(env.requests.isEmpty())
    }

    @Test
    fun blankNamesAndTheDemoBudgetAreRefused() = withBudget(serverBacked = false) { env ->
        val service = BudgetRenameService(env.server(), env.files)
        val oldName = env.files.metadata(env.budget.id)?.budgetName

        assertThrows(IllegalArgumentException::class.java) { service.rename("https://actual.test", null, env.budget.id, "   ") }
        assertEquals(oldName, env.files.metadata(env.budget.id)?.budgetName)

        val demo = DemoBudgetManager.recreate(env.files)
        try {
            assertThrows(IllegalArgumentException::class.java) { service.rename("https://actual.test", null, demo.id, "Renamed demo") }
        } finally {
            runCatching { env.files.deleteBudget(demo.id) }
        }
    }

    @Test
    fun renamedMetadataFileIsReplacedAtomicallyWithNoLeftoverTemporaryFile() = withBudget(serverBacked = false) { env ->
        env.files.renameBudget(env.budget.id, "Atomic")

        assertEquals("Atomic", env.files.metadata(env.budget.id)?.budgetName)
        assertNull(env.files.budgetDirectory(env.budget.id).listFiles()?.firstOrNull { it.name.endsWith(".tmp") })
    }

    private fun withBudget(serverBacked: Boolean, block: (Env) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val budget = files.createBudget("Rename ${UUID.randomUUID()}")
        try {
            if (serverBacked) files.saveCloudRegistration(budget.id, CLOUD_FILE_ID, "group-1")
            block(Env(files, files.metadata(budget.id)!!))
        } finally {
            runCatching { files.deleteBudget(budget.id) }
        }
    }

    private companion object {
        const val CLOUD_FILE_ID = "cloud-rename-file"
    }
}
