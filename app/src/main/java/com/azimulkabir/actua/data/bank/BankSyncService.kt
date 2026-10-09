package com.azimulkabir.actua.data.bank

import com.azimulkabir.actua.data.budget.ActualBudgetDatabase
import com.azimulkabir.actua.data.budget.ActualEntityWriter
import com.azimulkabir.actua.data.budget.ActualTransactionWriter
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.data.importing.ImportRules
import com.azimulkabir.actua.data.network.ActualServerClient
import com.azimulkabir.actua.data.network.BankSyncDownload
import com.azimulkabir.actua.data.network.BankSyncTransaction
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

data class BankSyncResult(
    val accountsSynced: Int,
    val imported: Int,
    val matched: Int = 0,
    val problems: List<String>,
    /** Rows already imported under the same id that the bank changed, e.g. pending → booked. */
    val updated: Int = 0,
) {
    val summary: String get() = buildList {
        if (imported > 0) add("Imported $imported ${if (imported == 1) "transaction" else "transactions"}.")
        if (matched > 0) add("Matched $matched ${if (matched == 1) "transaction" else "transactions"} already entered manually.")
        if (updated > 0) add("Updated $updated ${if (updated == 1) "transaction" else "transactions"} from the bank.")
        if (imported == 0 && matched == 0 && updated == 0 && problems.isEmpty()) add(
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
        val todayDay = today().format(DateTimeFormatter.BASIC_ISO_DATE).toInt()
        fun startDateFor(id: String): String {
            // Actual's `getAccountOldestTransaction` ignores rows dated after today.
            val oldest = database.oldestTransactionDate(id, onOrBefore = todayDay)
            val day = oldest?.coerceAtLeast(floor) ?: floor
            return "%04d-%02d-%02d".format(day / 10000, day / 100 % 100, day % 100)
        }
        // Actual's `newAccount`: no transactions dated today or earlier. Its first sync gets a
        // starting-balance row, and GoCardless is only asked for balances then (#1003).
        val newAccounts = accounts.filter { database.oldestTransactionDate(it.id, onOrBefore = todayDay) == null }
            .mapTo(mutableSetOf()) { it.id }

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
                            serverUrl, token, requisitionId, account.externalId, startDateFor(account.id),
                            includeBalance = account.id in newAccounts,
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
        var updated = 0
        val ruleInputs by lazy { database.fetchRules() to database.ruleContext() }
        accounts.forEach { account ->
            val download = outcomes.getValue(account.id).getOrElse { error ->
                val failure = error as BankSyncFailure
                problems += "${account.name}: ${failure.problem}"
                failure.status?.let { entities.recordBankSyncStatus(account.id, it) }
                return@forEach
            }
            download.problem?.let { problems += "${account.name}: $it" }
            // The account's bank sync preferences from Actual's Bank Sync page (#1006). A custom
            // mapping that isn't valid JSON fails the account, as Actual's `mappingsFromString` does.
            val settings = runCatching { SyncSettings.read(database, account.id) }.getOrElse { error ->
                problems += "${account.name}: ${error.message ?: "its field mapping is invalid."}"
                entities.recordBankSyncStatus(account.id, "failed")
                return@forEach
            }
            val newAccount = account.id in newAccounts
            // Everything this account's download writes is committed together below (#1007).
            val batch = transactions.importBatch()
            if (newAccount) startingBalance(account.source, download, settings.importPending)?.let { amount ->
                val date = download.transactions.lastOrNull()?.date ?: todayDay
                entities.startingBalanceCells(account.id, amount, date).forEach { batch.cells(it.dataset, it.row, it.values) }
            }
            // "Import transactions" off keeps only the balance update, except on a first sync.
            val rows = if (!settings.importTransactions && !newAccount) emptyList()
                else download.transactions.filter { settings.importPending || it.booked }.map(settings::mapped)
            // Actual's `normalizeBankSyncTransactions`: the provider's id, or for a booked row
            // without one `<account>-<internalTransactionId>`; a row with neither is still imported
            // (and fuzzy-matched on later syncs). Each row is keyed by its position.
            val keyed = rows.mapIndexed { index, row ->
                val importedId = row.financialId
                    ?: row.internalTransactionId?.takeIf { row.booked }?.let { "${account.id}-$it" }
                KeyedRow("$index", importedId, row)
            }
            // Two rows under one id with different data stay out rather than guessing which is right.
            val (withId, withoutId) = keyed.partition { it.importedId != null }
            val unambiguousWithId = withId.groupBy { it.importedId }
                .filterValues { rows -> rows.distinctBy { listOf(it.row.date, it.row.amountCents, it.row.payeeName, it.row.notes) }.size == 1 }
                .values.map { it.first() }
            val conflicts = withId.mapTo(mutableSetOf()) { it.importedId }.size - unambiguousWithId.size
            if (conflicts > 0) problems += "${account.name}: skipped $conflicts conflicting bank transactions."
            val unambiguous = (unambiguousWithId + withoutId).sortedBy { it.key.index() }
            // Actual's exact `imported_id` match: a row the account already holds under the same id
            // goes through the update path below instead of being imported again (#1001). Ids that
            // only deleted rows carry are not imported again.
            // With "reimport deleted" on (Actual's default) an id only deleted rows carry is new again.
            val known = database.financialIdRows(account.id, unambiguousWithId.mapTo(mutableSetOf()) { it.importedId!! })
                .filterValues { it != null || !settings.reimportDeleted }
            // Download order, as Actual processes and inserts rows.
            val newRows = unambiguous.filterNot { it.importedId != null && it.importedId in known }.sortedBy { it.key.index() }
            val exactRows = unambiguous.filter { it.importedId != null && known[it.importedId] != null }
            // As in Actual's `matchTransactions`, rules run on every downloaded row first, with the
            // bank's name as imported_payee and the payee resolved by name (a new name gets a
            // provisional id). Matching then uses the post-rule values, and a payee is created only
            // for a name a row still uses, so a payee a rule replaced leaves no orphan.
            val draftRows = newRows + exactRows
            val drafts = draftRows.map { (_, importedId, row) ->
                ActualTransaction(
                    id = idFactory(), accountId = account.id, date = row.date,
                    amountCents = row.amountCents, payeeId = null, payeeName = null,
                    categoryId = null, categoryName = null, notes = importedNotes(row.notes).takeIf { settings.importNotes },
                    cleared = row.booked, reconciled = false, transferId = null,
                    isParent = false, parentId = null, tombstone = false, sortOrder = null,
                    importedPayee = row.payeeName.trim(), scheduleId = null, transferAccountId = null,
                    financialId = importedId, pending = !row.booked, rawSyncedData = row.rawJson,
                )
            }
            val ruled = if (drafts.isEmpty()) emptyMap() else draftRows.map { it.key }.zip(ImportRules.apply(
                drafts, ruleInputs.first, ruleInputs.second,
                existingPayeeId = { database.findPayeeByName(it)?.id },
                newId = idFactory,
                keepDeleted = true,
                idFactory = idFactory,
            )).toMap()
            fun ruledRow(row: KeyedRow) = ruled.getValue(row.key)
            fun payeeId(prepared: ImportRules.Prepared): String? =
                prepared.createPayeeName?.let(batch::payee) ?: prepared.transaction.payeeId
            // Actual's `sort_order ??= now - index * TRANSACTION_SORT_INCREMENT` over the added rows.
            val now = System.currentTimeMillis()
            var addedCount = 0
            fun nextSortOrder() = (now - addedCount++ * ActualTransactionWriter.TRANSACTION_SORT_INCREMENT).toDouble()

            /**
             * Actual's update path for a matched row (`reconcileTransactions`): the bank's id and
             * imported payee replace the stored ones, an empty payee, category or notes is filled
             * from the rule-processed bank row, `raw_synced_data` is kept if already set, and
             * `cleared` is OR'd and copied to a split's children. Returns whether anything changed.
             */
            fun updateMatched(original: ActualTransaction, prepared: ImportRules.Prepared, row: KeyedRow): Boolean {
                val bank = prepared.transaction
                val cleared = original.cleared || bank.cleared
                // "Update dates" moves a matched row (and a split's children) to the bank's date.
                val date = if (settings.updateDates) bank.date else original.date
                val linked = original.copy(
                    date = date,
                    financialId = row.importedId,
                    payeeId = original.payeeId ?: payeeId(prepared),
                    categoryId = original.categoryId ?: bank.categoryId,
                    notes = original.notes ?: bank.notes,
                    importedPayee = bank.importedPayee,
                    cleared = cleared,
                    pending = !row.row.booked && !cleared,
                    rawSyncedData = original.rawSyncedData ?: bank.rawSyncedData,
                )
                val updates = buildList {
                    if (ActualTransactionWriter.changedFields(original, linked).isNotEmpty()) add(original to linked)
                    if (original.isParent && (original.cleared != cleared || original.date != date)) {
                        database.fetchChildTransactions(original.id)
                            .map { it to it.copy(
                                cleared = if (original.cleared != cleared) cleared else it.cleared,
                                date = if (original.date != date) date else it.date,
                            ) }
                            .filter { (before, after) -> before != after }
                            .forEach { add(it) }
                    }
                }
                if (updates.isEmpty()) return false
                updates.forEach { (before, after) -> batch.update(before, after) }
                return true
            }

            // Rows already stored under their id: never touch a reconciled one (Actual skips it).
            exactRows.forEach { row ->
                val original = known[row.importedId]?.let(database::fetchTransactionRow) ?: return@forEach
                if (original.reconciled) return@forEach
                if (updateMatched(original, ruledRow(row), row)) updated++
            }

            // Fuzzy-match each new bank row against a local transaction (same account and amount,
            // close date) before inserting, so a manually entered transaction that posts a few days
            // apart from the bank's own date is reconciled instead of duplicated. As in Actual's
            // bank sync (`strictIdChecking` off), rows already imported under another id are
            // candidates too, since some providers send a new id for the same transaction. Rows
            // this download still sends under their own id are taken by that exact match.
            val downloadIds = keyed.mapNotNullTo(mutableSetOf()) { it.importedId }
            val candidatesByRow = newRows.associate { row ->
                val transaction = ruledRow(row).transaction
                row.key to database.fuzzyMatchCandidates(
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
                    BankSyncMatchRow(row.key, prepared.transaction.date, matchPayee)
                },
                candidatesByRow = candidatesByRow.mapValues { (_, candidates) ->
                    candidates.map { BankSyncMatchCandidate(it.id, it.date, it.payeeId, it.reconciled) }
                },
            )

            newRows.forEach { row ->
                val prepared = ruledRow(row)
                val bank = prepared.transaction
                val match = matches[row.key]
                when {
                    match == null -> {
                        // A row a rule deleted is not imported (Actual skips a tombstoned new row).
                        if (prepared.deleted) return@forEach
                        if (prepared.splitChildren.isEmpty()) {
                            batch.insert(bank.copy(payeeId = payeeId(prepared), sortOrder = nextSortOrder()))
                        } else {
                            val offBudget = database.fetchAccounts().any { it.id == bank.accountId && it.offBudget }
                            val parent = bank.copy(payeeId = null, categoryId = null, isParent = true, sortOrder = nextSortOrder())
                            // `makeSplitTransaction` gives children `sort_order = 0 - index`.
                            val children = prepared.splitChildren.mapIndexed { index, child ->
                                nextSortOrder()
                                child.transaction.copy(
                                    payeeId = child.pendingPayeeName?.let(batch::payee) ?: child.transaction.payeeId,
                                    categoryId = child.transaction.categoryId.takeUnless { offBudget },
                                    sortOrder = (0 - index).toDouble(),
                                )
                            }
                            batch.insertSplit(parent, children)
                        }
                        imported++
                    }
                    match.reconciled -> {
                        // Locked transaction: it's already accounted for, so don't duplicate the bank row.
                        matched++
                    }
                    else -> {
                        val original = database.fetchTransaction(match.id) ?: return@forEach
                        updateMatched(original, prepared, row)
                        matched++
                    }
                }
            }
            batch.cells("accounts", account.id, entities.bankSyncStatusCells(
                download.status,
                syncedAt = if (download.status == "ok") System.currentTimeMillis().toString() else null,
                // Actual writes `balance_current` on every sync except an account's first.
                balanceCurrent = download.balanceCents.takeUnless { newAccount },
            ))
            batch.commit()
        }
        return BankSyncResult(
            accountsSynced = outcomes.size, imported = imported, matched = matched, updated = updated, problems = problems,
        )
    }

    /**
     * The opening balance for an account's first sync, from `processBankSyncDownload`. The server's
     * `startingBalance` is the current balance; SimpleFIN and Enable Banking subtract the downloaded
     * rows from it (SimpleFIN with Actual's `parseInt(amount.replace('.', ''))`), and GoCardless
     * sends the opening balance itself. Null when the server sent no balance.
     */
    private fun startingBalance(source: String, download: BankSyncDownload, importPending: Boolean): Long? {
        val current = download.balanceCents ?: return null
        return when (source) {
            "simpleFin" -> download.transactions.fold<BankSyncTransaction, Long?>(current) { total, row ->
                val cents = row.amountText?.let(::simpleFinCents) ?: return@fold null
                total?.minus(cents)
            }
            "enableBanking" -> current - download.transactions.filter { importPending || it.booked }.sumOf { it.amountCents }
            else -> current
        }
    }

    /** JavaScript's `parseInt(text.replace('.', ''))`: the first `.` removed, leading digits read. */
    private fun simpleFinCents(text: String): Long? =
        Regex("""^\s*[+-]?\d+""").find(text.replaceFirst(".", ""))?.value?.trim()?.toLongOrNull()

    /**
     * Actual's per-account bank sync preferences (`sync-import-pending-<id>` and friends, stored as
     * the strings "true"/"false" in the synced `preferences` table) and `custom-sync-mappings-<id>`.
     */
    private class SyncSettings(
        val importPending: Boolean,
        val importNotes: Boolean,
        val importTransactions: Boolean,
        val updateDates: Boolean,
        val reimportDeleted: Boolean,
        /** Provider field for `date`, `payee` and `notes`, for payments and for deposits. */
        val payment: Map<String, String>,
        val deposit: Map<String, String>,
    ) {
        /** Actual's `trans[mapping.get(field)] ?? fallback`, read from the provider row. */
        fun mapped(row: BankSyncTransaction): BankSyncTransaction {
            val mapping = if (row.amountCents <= 0) payment else deposit
            if (mapping == DEFAULT_MAPPING) return row
            val raw = row.rawJson?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return row
            fun field(name: String): String? = mapping[name]?.let { key ->
                if (raw.has(key) && !raw.isNull(key)) raw.opt(key)?.toString() else null
            }
            val date = field("date")?.take(10)?.replace("-", "")?.toIntOrNull()?.takeIf { it in 19000101..29991231 }
            return row.copy(
                date = date ?: row.date,
                payeeName = field("payee") ?: row.payeeName,
                notes = field("notes"),
            )
        }

        companion object {
            private val DEFAULT_MAPPING = mapOf("date" to "date", "payee" to "payeeName", "notes" to "notes")

            fun read(database: ActualBudgetDatabase, accountId: String): SyncSettings {
                val keys = listOf("sync-import-pending", "sync-import-notes", "sync-import-transactions",
                    "sync-update-dates", "sync-reimport-deleted", "custom-sync-mappings").map { "$it-$accountId" }
                val prefs = database.fetchPreferences(keys)
                // Actual's `String(value ?? default) === 'true'`.
                fun flag(name: String, default: Boolean) = (prefs["$name-$accountId"] ?: default.toString()) == "true"
                val mappings = prefs["custom-sync-mappings-$accountId"]?.let(::parseMappings)
                return SyncSettings(
                    importPending = flag("sync-import-pending", true),
                    importNotes = flag("sync-import-notes", true),
                    importTransactions = flag("sync-import-transactions", true),
                    updateDates = flag("sync-update-dates", false),
                    reimportDeleted = flag("sync-reimport-deleted", true),
                    payment = mappings?.get("payment") ?: DEFAULT_MAPPING,
                    deposit = mappings?.get("deposit") ?: DEFAULT_MAPPING,
                )
            }

            /** `mappingsFromString`: `{"payment": {"date": …, "payee": …, "notes": …}, "deposit": {…}}`. */
            private fun parseMappings(text: String): Map<String, Map<String, String>> {
                val root = runCatching { JSONObject(text) }
                    .getOrElse { throw IllegalArgumentException("its custom field mapping can't be read. Fix it in Actual's Bank Sync settings.") }
                return root.keys().asSequence().associateWith { direction ->
                    val fields = root.optJSONObject(direction) ?: JSONObject()
                    fields.keys().asSequence().associateWith { fields.optString(it) }
                }
            }
        }
    }

    /** A downloaded row, its key in this download, and the `imported_id` Actual would give it. */
    private data class KeyedRow(val key: String, val importedId: String?, val row: BankSyncTransaction)

    private fun String.index(): Int = toInt()

    /** Actual trims imported notes and escapes `#` as `##`, so bank text never becomes a tag. */
    private fun importedNotes(notes: String?): String? =
        notes?.takeIf(String::isNotEmpty)?.trim()?.replace("#", "##")

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
