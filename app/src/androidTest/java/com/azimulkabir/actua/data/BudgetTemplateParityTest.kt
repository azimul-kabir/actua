package com.azimulkabir.actua.data

import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.ActualBudgetDatabase
import com.azimulkabir.actua.data.budget.BudgetDownloadService
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.network.ActualServerClient
import com.azimulkabir.actua.data.security.BudgetEncryptionKeyStore
import com.azimulkabir.actua.data.sync.ActualSyncClient
import com.azimulkabir.actua.model.BudgetTemplatePlanner
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Actua's side of the #668 budget-template check (docs/tools/budget-templates/README.md). The
 * `budget-templates-parity` workflow seeds pairs of identical budgets on a real actual-server and runs
 * Actual's template engine on one of each; this test downloads the other and runs the same three steps the
 * way the Budget screen does (`BudgetTemplatePlanner.preview` over `ActuaRepository.budgetGroups`,
 * `budgetOverview` and `budgetScheduleFunding`, then `applyBudgetTemplate`; month-end cleanup
 * through `previewCleanup`/`applyCleanup`), then syncs so a third client can compare the cells.
 * Skipped unless the workflow passes `templatesServerUrl`.
 */
class BudgetTemplateParityTest {
    @Test
    fun runTheActuaSideAndSync() {
        val arguments = InstrumentationRegistry.getArguments()
        val serverUrl = arguments.getString("templatesServerUrl")
        assumeTrue("Runs only from the budget-templates-parity workflow", !serverUrl.isNullOrBlank())
        val password = requireNotNull(arguments.getString("templatesPassword"))
        // The whole-units budget has Actual's synced `hideFraction` preference on.
        for (name in listOf("Templates actua", "Whole units actua")) {
            runBudget(requireNotNull(serverUrl), password, name)
        }
    }

    private fun runBudget(serverUrl: String, password: String, name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val server = ActualServerClient()
        val token = server.login(serverUrl, password)
        val remote = server.listFiles(serverUrl, token).single { it.name == name }
        val files = BudgetFileManager(context)
        val budget = BudgetDownloadService(server, files, BudgetEncryptionKeyStore(context))
            .download(serverUrl, token, remote)
        fun sync() = ActualBudgetDatabase.open(files.databaseFile(budget.id)).use { database ->
            ActualSyncClient(serverUrl, token, server, database, remote.fileId, requireNotNull(budget.groupId)).sync()
        }
        val activeBudgets = ActiveBudgetStore(context)
        val previousBudget = activeBudgets.budgetId
        try {
            // The download is the empty snapshot uploaded at creation; the seed arrives as messages.
            sync()
            activeBudgets.budgetId = budget.id
            val repository = ActuaRepository(context)
            try {
                applyTemplates(repository, "2026-08", overwrite = false)
                applyTemplates(repository, "2026-09", overwrite = true)
                repository.refreshCleanupDefinitions()
                assertTrue(repository.applyCleanup(repository.previewCleanup("2026-10")))
            } finally {
                repository.close()
            }
            assertTrue(sync().sentMessages > 0)
        } finally {
            activeBudgets.budgetId = previousBudget
            runCatching { files.deleteBudget(budget.id) }
        }
    }

    /** Budget screen → "Apply budget template" (or "Overwrite"), confirmed as previewed. */
    private fun applyTemplates(repository: ActuaRepository, month: String, overwrite: Boolean) {
        // Opening the template preview re-reads notes-managed templates first.
        assertTrue(repository.refreshNoteTemplates())
        val preview = BudgetTemplatePlanner.preview(
            repository.budgetGroups(month),
            month,
            repository.budgetOverview(month).toBudgetCents ?: Long.MAX_VALUE,
            overwrite,
            repository.budgetScheduleFunding(month),
            repository.budgetHidesFraction(),
        )
        assertTrue(repository.applyBudgetTemplate(preview))
    }
}
