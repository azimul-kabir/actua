package com.azimulkabir.actua.data.bank

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** One imported bank row awaiting a match against existing local transactions, keyed by its financial id. */
data class BankSyncMatchRow(val financialId: String, val date: Int, val payeeId: String?)

/** A local transaction (manually entered, or imported under another id) that could reconcile with an imported bank row. */
data class BankSyncMatchCandidate(val id: String, val date: Int, val payeeId: String?, val reconciled: Boolean)

/**
 * Reproduces upstream Actual's `matchTransactions` fuzzy-matching passes (loot-core
 * `packages/loot-core/src/server/accounts/sync.ts`), so a bank-sync import can reconcile with a
 * manually entered transaction that posted on a different date instead of creating a duplicate.
 *
 * Each row's candidates (same account/amount within the caller's date window, excluding rows
 * the same download matches by exact id) are considered closest-date-first. A payee-matching pass runs for every
 * row before any row falls back to its closest date-only candidate, so a high-fidelity match
 * elsewhere never loses its candidate to a lower-fidelity match processed earlier.
 */
object BankSyncMatcher {
    fun match(
        rows: List<BankSyncMatchRow>,
        candidatesByRow: Map<String, List<BankSyncMatchCandidate>>,
    ): Map<String, BankSyncMatchCandidate> {
        val byClosestDate = rows.associate { row ->
            row.financialId to candidatesByRow[row.financialId].orEmpty().sortedBy { dayDistance(row.date, it.date) }
        }
        val claimed = mutableSetOf<String>()
        val result = mutableMapOf<String, BankSyncMatchCandidate>()

        rows.forEach { row ->
            val candidate = byClosestDate.getValue(row.financialId)
                .firstOrNull { it.id !in claimed && row.payeeId != null && it.payeeId == row.payeeId }
            if (candidate != null) {
                claimed += candidate.id
                result[row.financialId] = candidate
            }
        }
        rows.forEach { row ->
            if (row.financialId in result) return@forEach
            val candidate = byClosestDate.getValue(row.financialId).firstOrNull { it.id !in claimed }
            if (candidate != null) {
                claimed += candidate.id
                result[row.financialId] = candidate
            }
        }
        return result
    }

    private fun dayDistance(a: Int, b: Int): Long = abs(ChronoUnit.DAYS.between(a.toLocalDate(), b.toLocalDate()))

    private fun Int.toLocalDate(): LocalDate = LocalDate.of(this / 10000, this / 100 % 100, this % 100)
}
