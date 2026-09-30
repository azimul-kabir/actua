package com.azimulkabir.actua.data.bank

import com.azimulkabir.actua.data.budget.ActualBudgetDatabase
import com.azimulkabir.actua.data.budget.ActualEntityWriter
import com.azimulkabir.actua.data.budget.ActualTransactionWriter
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.data.network.ActualServerClient
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

data class BankSyncResult(val accountsSynced: Int, val imported: Int, val matched: Int = 0, val problems: List<String>) {
    val summary: String get() = buildList {
        if (imported > 0) add("Imported $imported ${if (imported == 1) "transaction" else "transactions"}.")
        if (matched > 0) add("Matched $matched ${if (matched == 1) "transaction" else "transactions"} already entered manually.")
        if (imported == 0 && matched == 0 && problems.isEmpty()) add(
            if (accountsSynced == 0) "No linked bank accounts to sync."
            else "Everything is already up to date.",
        )
        addAll(problems)
    }.joinToString("\n\n")
}

/** Imports server-hosted bank-sync rows (SimpleFIN, GoCardless) through the same CRDT writers as manual edits. */
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
        if (accounts.isEmpty()) return BankSyncResult(accountsSynced = 0, imported = 0, problems = emptyList())
        val floor = today().minusDays(89).format(DateTimeFormatter.BASIC_ISO_DATE).toInt()
        fun startDateFor(id: String): String {
            val oldest = database.oldestTransactionDate(id)
            val day = oldest?.coerceAtLeast(floor) ?: floor
            return "%04d-%02d-%02d".format(day / 10000, day / 100 % 100, day % 100)
        }
        val today = today().format(DateTimeFormatter.ISO_LOCAL_DATE)

        val simpleFin = accounts.filter { it.source == "simpleFin" }
        val simpleFinDownloads = if (simpleFin.isEmpty()) emptyMap() else server.downloadSimpleFinTransactions(
            serverUrl, token, simpleFin.map { it.externalId }, simpleFin.map { startDateFor(it.id) },
        ).associateBy { it.externalAccountId }

        val problems = mutableListOf<String>()
        val reported = mutableSetOf<String>()
        val downloads = accounts.associate { account ->
            val download = when (account.source) {
                "simpleFin" -> simpleFinDownloads[account.externalId]
                "goCardless" -> {
                    val requisitionId = account.requisitionId
                    if (requisitionId == null) {
                        problems += "${account.name}: this account is missing its GoCardless connection."
                        reported += account.id
                        null
                    } else runCatching {
                        server.downloadGoCardlessTransactions(
                            serverUrl, token, requisitionId, account.externalId, startDateFor(account.id), today,
                        )
                    }.getOrElse {
                        problems += "${account.name}: ${it.message ?: "GoCardless sync failed."}"
                        reported += account.id
                        null
                    }
                }
                else -> null
            }
            account.id to download
        }
        var imported = 0
        var matched = 0
        accounts.forEach { account ->
            val download = downloads[account.id]
            if (download == null) {
                if (account.id !in reported) problems += "${account.name}: the bank did not return this account."
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
            val newRows = unambiguous.filterNot { it.financialId in existing }.sortedBy { it.date }
            val payeeByRow = newRows.associateWith { transactions.resolveOrCreatePayee(it.payeeName) }

            // Fuzzy-match each new bank row against a local transaction (same account and amount,
            // close date) before inserting, so a manually entered transaction that posts a few days
            // apart from the bank's own date is reconciled instead of duplicated. As in Actual's
            // bank sync (`strictIdChecking` off), rows already imported under another id are
            // candidates too, since some providers send a new id for the same transaction. Rows
            // this download still sends under their own id are taken by that exact match.
            val downloadIds = download.transactions.mapTo(mutableSetOf()) { it.financialId }
            val candidatesByRow = newRows.associate { row ->
                row.financialId to database.fuzzyMatchCandidates(
                    account.id, row.amountCents, dateFrom = row.date.shiftDays(-7), dateTo = row.date.shiftDays(7),
                ).filterNot { it.financialId != null && it.financialId in downloadIds }
            }
            val matches = BankSyncMatcher.match(
                rows = newRows.map { BankSyncMatchRow(it.financialId, it.date, payeeByRow.getValue(it).id) },
                candidatesByRow = candidatesByRow.mapValues { (_, candidates) ->
                    candidates.map { BankSyncMatchCandidate(it.id, it.date, it.payeeId, it.reconciled) }
                },
            )

            newRows.forEach { row ->
                val payee = payeeByRow.getValue(row)
                val match = matches[row.financialId]
                when {
                    match == null -> {
                        val created = transactions.createTransaction(
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
                        if (created != null) imported++
                    }
                    match.reconciled -> {
                        // Locked transaction: it's already accounted for, so don't duplicate the bank row.
                        matched++
                    }
                    else -> {
                        val original = database.fetchTransaction(match.id) ?: return@forEach
                        val linked = original.copy(
                            financialId = row.financialId,
                            payeeId = original.payeeId ?: payee.id,
                            notes = original.notes ?: row.notes,
                            importedPayee = row.payeeName,
                            cleared = original.cleared || row.booked,
                            pending = !row.booked,
                        )
                        transactions.mutate(updates = listOf(original to linked))
                        matched++
                    }
                }
            }
            entities.recordBankSyncStatus(
                account.id, download.status,
                syncedAt = if (download.status == "ok") System.currentTimeMillis().toString() else null,
            )
        }
        return BankSyncResult(accountsSynced = downloads.size, imported = imported, matched = matched, problems = problems)
    }

    private fun Int.shiftDays(days: Long): Int {
        val date = LocalDate.of(this / 10000, this / 100 % 100, this % 100).plusDays(days)
        return date.year * 10000 + date.monthValue * 100 + date.dayOfMonth
    }
}
