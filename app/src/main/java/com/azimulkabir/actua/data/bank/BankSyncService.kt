package com.azimulkabir.actua.data.bank

import com.azimulkabir.actua.data.budget.ActualBudgetDatabase
import com.azimulkabir.actua.data.budget.ActualEntityWriter
import com.azimulkabir.actua.data.budget.ActualTransactionWriter
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.data.importing.ImportRules
import com.azimulkabir.actua.data.network.ActualServerClient
import com.azimulkabir.actua.data.network.BankSyncDownload
import com.azimulkabir.actua.data.network.BankSyncTransaction
import java.net.SocketTimeoutException
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

/** Imports server-hosted bank-sync rows (SimpleFIN, GoCardless, and experimental Enable Banking) through the same CRDT writers as manual edits. */
class BankSyncService(
    private val database: ActualBudgetDatabase,
    private val transactions: ActualTransactionWriter,
    private val entities: ActualEntityWriter,
    private val server: ActualServerClient,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val today: () -> LocalDate = LocalDate::now,
    /** Actual's experimental `enableBanking` flag; Enable Banking accounts only sync while it is on. */
    private val enableBankingEnabled: Boolean = false,
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
        // A SimpleFIN timeout fails the whole batch; Actual reports it as TIMED_OUT for each account.
        val simpleFinResult: Result<Map<String, BankSyncDownload>> = if (simpleFin.isEmpty()) Result.success(emptyMap()) else runCatching {
            server.downloadSimpleFinTransactions(
                serverUrl, token, simpleFin.map { it.externalId }, simpleFin.map { startDateFor(it.id) },
            ).associateBy { it.externalAccountId }
        }.onFailure { if (!it.isTimeout()) throw it }

        val problems = mutableListOf<String>()
        val outcomes = accounts.associate { account ->
            val outcome: Result<BankSyncDownload> = when (account.source) {
                "simpleFin" -> simpleFinResult.fold(
                    onSuccess = { downloads ->
                        // Actual's `simpleFinBatchSync` treats an account left out of the batch
                        // response as ACCOUNT_MISSING and stores `account-missing`.
                        downloads[account.externalId]?.let { Result.success(it) } ?: Result.failure(BankSyncFailure(
                            "SimpleFIN did not return this account. Try syncing again, and relink it if this keeps happening.",
                            status = "account-missing",
                        ))
                    },
                    onFailure = { Result.failure(downloadFailure(it)) },
                )
                "goCardless" -> {
                    val requisitionId = account.requisitionId
                    // Actual skips accounts without a bank link, so no sync status is stored.
                    if (requisitionId == null) Result.failure(BankSyncFailure("this account is missing its GoCardless connection.", status = null))
                    else runCatching {
                        server.downloadGoCardlessTransactions(
                            serverUrl, token, requisitionId, account.externalId, startDateFor(account.id), today,
                        )
                    }.recoverCatching { throw downloadFailure(it) }
                }
                // Experimental: with the flag off this falls through to the unsupported-provider message below.
                "enableBanking" -> if (enableBankingEnabled) runCatching {
                    server.downloadEnableBankingTransactions(serverUrl, token, account.externalId, startDateFor(account.id))
                }.recoverCatching { throw downloadFailure(it) } else unsupportedProvider(account)
                // Actual syncs these, but Actua can't yet; leave the stored status to Actual.
                else -> unsupportedProvider(account)
            }
            account.id to outcome
        }
        var imported = 0
        var matched = 0
        val ruleInputs by lazy { database.fetchRules() to database.ruleContext() }
        accounts.forEach { account ->
            val download = outcomes.getValue(account.id).getOrElse { error ->
                val failure = error as BankSyncFailure
                problems += "${account.name}: ${failure.problem}"
                failure.status?.let { entities.recordBankSyncStatus(account.id, it) }
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
            // As in Actual's `matchTransactions`, rules run on every downloaded row first, with the
            // bank's name as imported_payee and the payee resolved by name (a new name gets a
            // provisional id). Matching then uses the post-rule values, and a payee is created only
            // for a name a row still uses, so a payee a rule replaced leaves no orphan.
            val drafts = newRows.map { row ->
                ActualTransaction(
                    id = idFactory(), accountId = account.id, date = row.date,
                    amountCents = row.amountCents, payeeId = null, payeeName = null,
                    categoryId = null, categoryName = null, notes = row.notes,
                    cleared = row.booked, reconciled = false, transferId = null,
                    isParent = false, parentId = null, tombstone = false, sortOrder = null,
                    importedPayee = row.payeeName, scheduleId = null, transferAccountId = null,
                    financialId = row.financialId, pending = !row.booked,
                )
            }
            val ruled = if (drafts.isEmpty()) emptyMap() else ImportRules.apply(
                drafts, ruleInputs.first, ruleInputs.second,
                existingPayeeId = { database.findPayeeByName(it)?.id },
                newId = { UUID.randomUUID().toString() },
                keepDeleted = true,
            ).associateBy { it.transaction.financialId!! }
            fun ruledRow(row: BankSyncTransaction) = ruled.getValue(row.financialId)
            fun payeeId(prepared: ImportRules.Prepared): String? =
                prepared.createPayeeName?.let { transactions.resolveOrCreatePayee(it).id } ?: prepared.transaction.payeeId

            // Fuzzy-match each new bank row against a local transaction (same account and amount,
            // close date) before inserting, so a manually entered transaction that posts a few days
            // apart from the bank's own date is reconciled instead of duplicated. As in Actual's
            // bank sync (`strictIdChecking` off), rows already imported under another id are
            // candidates too, since some providers send a new id for the same transaction. Rows
            // this download still sends under their own id are taken by that exact match.
            val downloadIds = download.transactions.mapTo(mutableSetOf()) { it.financialId }
            val candidatesByRow = newRows.associate { row ->
                val transaction = ruledRow(row).transaction
                row.financialId to database.fuzzyMatchCandidates(
                    account.id, transaction.amountCents,
                    dateFrom = transaction.date.shiftDays(-7), dateTo = transaction.date.shiftDays(7),
                ).filterNot { it.financialId != null && it.financialId in downloadIds }
            }
            val matches = BankSyncMatcher.match(
                rows = newRows.map { row ->
                    val prepared = ruledRow(row)
                    // A new payee's provisional id never matches; a renamed existing payee does.
                    val matchPayee = prepared.createPayeeName?.let { database.findPayeeByName(it)?.id }
                        ?: prepared.transaction.payeeId.takeIf { prepared.createPayeeName == null }
                    BankSyncMatchRow(row.financialId, prepared.transaction.date, matchPayee)
                },
                candidatesByRow = candidatesByRow.mapValues { (_, candidates) ->
                    candidates.map { BankSyncMatchCandidate(it.id, it.date, it.payeeId, it.reconciled) }
                },
            )

            newRows.forEach { row ->
                val prepared = ruledRow(row)
                val bank = prepared.transaction
                val match = matches[row.financialId]
                when {
                    match == null -> {
                        // A row a rule deleted is not imported (Actual skips a tombstoned new row).
                        if (prepared.deleted) return@forEach
                        val created = transactions.createTransaction(bank.copy(payeeId = payeeId(prepared)), applyRules = false)
                        if (created != null) imported++
                    }
                    match.reconciled -> {
                        // Locked transaction: it's already accounted for, so don't duplicate the bank row.
                        matched++
                    }
                    else -> {
                        // Actual fills the matched transaction's empty payee, category and notes
                        // from the rule-processed bank row (`reconcileTransactions`).
                        val original = database.fetchTransaction(match.id) ?: return@forEach
                        val linked = original.copy(
                            financialId = row.financialId,
                            payeeId = original.payeeId ?: payeeId(prepared),
                            categoryId = original.categoryId ?: bank.categoryId,
                            notes = original.notes ?: bank.notes,
                            importedPayee = bank.importedPayee,
                            cleared = original.cleared || bank.cleared,
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
        return BankSyncResult(accountsSynced = outcomes.size, imported = imported, matched = matched, problems = problems)
    }

    private fun unsupportedProvider(account: ActualBudgetDatabase.BankSyncAccount): Result<BankSyncDownload> =
        Result.failure(BankSyncFailure(
            "Actua can't sync ${providerName(account.source)} accounts yet. Sync this account in Actual.",
            status = null,
        ))

    /** Why an account could not be downloaded, and the `bank_sync_status` to store (null leaves it unchanged). */
    internal class BankSyncFailure(val problem: String, val status: String?) : Exception(problem)

    internal companion object {
        /**
         * Maps a download error to Actual's per-account result: a timeout is TIMED_OUT
         * (`timed-out`), any other error is `failed` (loot-core `getBankSyncStatusFromError`).
         */
        fun downloadFailure(error: Throwable): BankSyncFailure =
            if (error.isTimeout()) BankSyncFailure("the bank took too long to respond. Try syncing again.", "timed-out")
            else BankSyncFailure(error.message ?: "Bank sync failed.", "failed")

        private fun Throwable.isTimeout(): Boolean = generateSequence(this) { it.cause }.any { it is SocketTimeoutException }

        private fun providerName(source: String): String = when (source) {
            "pluggyai" -> "Pluggy.ai"
            "enableBanking" -> "Enable Banking"
            "akahu" -> "Akahu"
            else -> source
        }
    }

    private fun Int.shiftDays(days: Long): Int {
        val date = LocalDate.of(this / 10000, this / 100 % 100, this % 100).plusDays(days)
        return date.year * 10000 + date.monthValue * 100 + date.dayOfMonth
    }
}
