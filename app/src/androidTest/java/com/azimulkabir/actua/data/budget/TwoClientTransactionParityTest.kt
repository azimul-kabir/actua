package com.azimulkabir.actua.data.budget

import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.data.network.ActualServerClient
import com.azimulkabir.actua.data.security.BudgetEncryptionKeyStore
import com.azimulkabir.actua.data.sync.ActualSyncClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Actua's side of the #663 two-client check (docs/tools/two-client/README.md). The
 * `two-client-parity` workflow seeds a budget on a real actual-server and makes each scenario's edit
 * through Actual's loot-core in the "Upstream …" accounts; this test downloads that budget, makes
 * the same edits through Actua's form service and writer in the "Actua …" accounts, and syncs, so a
 * third client can compare the rows. It is skipped unless the workflow passes `twoClientServerUrl`.
 *
 * Scenario n writes on 2026-08-n and may move rows to 2026-09-n; keep in step with upstream.mjs.
 */
class TwoClientTransactionParityTest {
    @Test
    fun makeTheActuaSideEditsAndSync() {
        val arguments = InstrumentationRegistry.getArguments()
        val serverUrl = arguments.getString("twoClientServerUrl")
        assumeTrue("Runs only from the two-client-parity workflow", !serverUrl.isNullOrBlank())
        val password = requireNotNull(arguments.getString("twoClientPassword"))
        val budgetName = arguments.getString("twoClientBudget") ?: "Two-client parity"
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val server = ActualServerClient()
        val token = server.login(requireNotNull(serverUrl), password)
        val remote = server.listFiles(serverUrl, token).single { it.name == budgetName }
        val files = BudgetFileManager(context)
        val budget = BudgetDownloadService(server, files, BudgetEncryptionKeyStore(context))
            .download(serverUrl, token, remote)
        try {
            ActualBudgetDatabase.open(files.databaseFile(budget.id)).use { database ->
                fun sync() = ActualSyncClient(
                    serverUrl, token, server, database, remote.fileId, requireNotNull(budget.groupId),
                ).sync()
                sync()
                ActuaSide(database).run()
                assertTrue(sync().sentMessages > 0)
            }
        } finally {
            files.deleteBudget(budget.id)
        }
    }

    private class ActuaSide(private val database: ActualBudgetDatabase) {
        private val writer = ActualTransactionWriter(database)
        private val forms = ActualTransactionFormService(database, writer)
        private val accounts = database.fetchAccounts().associate { it.name to it.id }
        private val categories = database.fetchCategoryGroups().flatMap { it.categories }.associate { it.name to it.id }
        private val food = categories.getValue("Parity Food")
        private val rent = categories.getValue("Parity Rent")

        private fun account(role: String) = accounts.getValue("Actua $role")
        private fun aug(day: Int) = 20260800 + day
        private fun sep(day: Int) = 20260900 + day

        private fun form(
            type: ActualTransactionType,
            amount: String,
            date: Int,
            notes: String,
            payee: String = "",
            category: String? = null,
            cleared: Boolean = false,
            account: String = "Checking",
            transferTo: String? = null,
            splits: List<ActualSplitLineForm> = emptyList(),
        ) = ActualTransactionForm(
            accountId = account(account), type = type, amount = amount, payeeName = payee,
            transferToAccountId = transferTo?.let(::account), categoryId = category, notes = notes, date = date,
            cleared = cleared, splits = splits, categoryIsExplicit = category != null,
        )

        private fun expense(amount: String, date: Int, notes: String, category: String? = food, cleared: Boolean = false) =
            form(ActualTransactionType.EXPENSE, amount, date, notes, STORE, category, cleared)

        private fun transfer(to: String, amount: String, date: Int, notes: String, cleared: Boolean = false, category: String? = null) =
            form(ActualTransactionType.TRANSFER, amount, date, notes, category = category, cleared = cleared, transferTo = to)

        private fun split(date: Int, notes: String, payee: String = STORE, cleared: Boolean = false, children: List<ActualTransaction> = emptyList()) =
            form(
                ActualTransactionType.EXPENSE, "10", date, notes, payee, null, cleared,
                splits = listOf(
                    // An inherited child payee is left blank, as ActuaRepository.toTransaction does.
                    ActualSplitLineForm(childId = children.firstOrNull { it.categoryId == food }?.id, categoryId = food, amount = "6"),
                    ActualSplitLineForm(
                        childId = children.firstOrNull { it.categoryId == rent }?.id, categoryId = rent, amount = "4",
                        payeeName = LANDLORD, notes = "rent part",
                    ),
                ),
            )

        private fun saved(id: String?) = requireNotNull(database.fetchTransaction(requireNotNull(id)))
        private fun topLevel(date: Int, predicate: (ActualTransaction) -> Boolean) =
            database.fetchTransactions(account("Checking")).single { it.date == date && it.parentId == null && predicate(it) }
        private fun transferLeg(date: Int) = topLevel(date) { it.transferId != null }
        private fun splitParent(date: Int) = topLevel(date) { it.isParent }

        fun run() {
            // 1
            forms.save(expense("12.34", aug(1), "s1"))
            // 2
            val t2 = saved(forms.save(expense("12.34", aug(2), "s2")))
            forms.save(form(ActualTransactionType.EXPENSE, "20", sep(2), "s2 edited", LANDLORD, rent, cleared = true), t2)
            // 3
            forms.save(form(ActualTransactionType.INCOME, "50", aug(3), "s3", STORE))
            // 4
            forms.save(transfer("Savings", "25", aug(4), "s4", cleared = true))
            // 5
            forms.save(transfer("Savings", "25", aug(5), "s5", cleared = true))
            forms.save(transfer("Savings", "30", sep(5), "s5 edited"), transferLeg(aug(5)))
            // 6
            forms.save(transfer("Brokerage", "40", aug(6), "s6", category = food))
            // 7
            forms.save(transfer("Savings", "10", aug(7), "s7"))
            writer.deleteTransaction(transferLeg(aug(7)))
            // 8
            forms.save(transfer("Savings", "10", aug(8), "s8"))
            forms.save(expense("10", aug(8), "s8"), transferLeg(aug(8)))
            // 9
            val t9 = saved(forms.save(expense("15", aug(9), "s9", cleared = true)))
            forms.save(transfer("Savings", "15", aug(9), "s9", cleared = true), t9)
            // 10
            forms.save(split(aug(10), "s10"))
            // 11
            forms.save(split(aug(11), "s11"))
            val s11 = splitParent(aug(11))
            forms.save(split(sep(11), "s11", MARKET, cleared = true, children = database.fetchChildTransactions(s11.id)), s11)
            // 12
            forms.save(split(aug(12), "s12"))
            writer.deleteTransaction(splitParent(aug(12)))
            // 13
            forms.save(expense("0", aug(13), "s13"))
            // 14
            val t14 = saved(forms.save(expense("5", aug(14), "s14")))
            writer.setCleared(t14, true)
            // 15
            forms.save(transfer("Savings", "7", aug(15), "s15"))
            writer.setCleared(transferLeg(aug(15)), true)
            // 16
            val t16 = saved(forms.save(expense("8", aug(16), "s16")))
            forms.save(expense("8", aug(16), "s16").copy(accountId = account("Savings")), t16)
            // 17
            val t17 = saved(forms.save(expense("9", aug(17), "s17")))
            forms.save(expense("9", aug(17), "s17").copy(accountId = account("Brokerage")), t17)
        }
    }

    private companion object {
        const val STORE = "Parity Store"
        const val LANDLORD = "Parity Landlord"
        const val MARKET = "Parity Market"
    }
}
