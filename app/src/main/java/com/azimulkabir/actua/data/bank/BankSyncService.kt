package com.azimulkabir.actua.data.bank

import com.azimulkabir.actua.data.budget.ActualBudgetDatabase
import com.azimulkabir.actua.data.budget.ActualEntityWriter
import com.azimulkabir.actua.data.budget.ActualTransactionWriter
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.data.network.ActualServerClient
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

data class BankSyncResult(val accountsSynced: Int, val imported: Int, val problems: List<String>) {
    val summary: String get() = buildList {
        if (imported > 0) add("Imported $imported ${if (imported == 1) "transaction" else "transactions"}.")
        if (imported == 0 && problems.isEmpty()) add(
            if (accountsSynced == 0) "No linked SimpleFIN accounts to sync."
            else "Everything is already up to date.",
        )
        addAll(problems)
    }.joinToString("\n\n")
}

/** Imports server-hosted SimpleFIN rows through the same CRDT writers as manual edits. */
class BankSyncService(
    private val database: ActualBudgetDatabase,
    private val transactions: ActualTransactionWriter,
    private val entities: ActualEntityWriter,
    private val server: ActualServerClient,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val today: () -> LocalDate = LocalDate::now,
) {
    fun sync(serverUrl: String, token: String, accountId: String? = null): BankSyncResult {
        val accounts = database.fetchBankSyncAccounts().filter { !it.closed && (accountId == null || it.id == accountId) }
        if (accounts.isEmpty()) return BankSyncResult(0, 0, emptyList())
        val starts = accounts.map { account ->
            val oldest = database.oldestTransactionDate(account.id)
            val floor = today().minusDays(89).format(DateTimeFormatter.BASIC_ISO_DATE).toInt()
            val day = oldest?.coerceAtLeast(floor) ?: floor
            "%04d-%02d-%02d".format(day / 10000, day / 100 % 100, day % 100)
        }
        val downloads = server.downloadSimpleFinTransactions(
            serverUrl, token, accounts.map { it.externalId }, starts,
        ).associateBy { it.externalAccountId }
        var imported = 0
        val problems = mutableListOf<String>()
        accounts.forEach { account ->
            val download = downloads[account.externalId]
            if (download == null) {
                problems += "${account.name}: the bank did not return this account."
                entities.recordBankSyncStatus(account.id, "account-missing")
                return@forEach
            }
            download.problem?.let { problems += "${account.name}: $it" }
            val unambiguous = download.transactions.groupBy { it.financialId }
                .filterValues { rows -> rows.distinctBy { listOf(it.date, it.amountCents, it.payeeName, it.notes) }.size == 1 }
                .values.map { it.first() }
            val conflicts = download.transactions.map { it.financialId }.toSet().size - unambiguous.size
            if (conflicts > 0) problems += "${account.name}: skipped $conflicts conflicting bank transactions."
            val existing = database.existingFinancialIds(account.id, unambiguous.mapTo(mutableSetOf()) { it.financialId })
            val inserts = unambiguous.filterNot { it.financialId in existing }.sortedBy { it.date }.mapNotNull { row ->
                val payee = transactions.resolveOrCreatePayee(row.payeeName)
                transactions.createTransaction(
                    ActualTransaction(
                        id = idFactory(), accountId = account.id, date = row.date,
                        amountCents = row.amountCents, payeeId = payee.id, payeeName = payee.name,
                        categoryId = null, categoryName = null, notes = row.notes,
                        cleared = row.booked, reconciled = false, transferId = null,
                        isParent = false, parentId = null, tombstone = false, sortOrder = null,
                        importedPayee = row.payeeName, scheduleId = null, transferAccountId = null,
                        financialId = row.financialId, pending = !row.booked,
                    ),
                    applyRules = true,
                )
            }
            imported += inserts.size
            entities.recordBankSyncStatus(
                account.id, download.status,
                syncedAt = if (download.status == "ok") System.currentTimeMillis().toString() else null,
            )
        }
        return BankSyncResult(downloads.size, imported, problems)
    }
}
