package com.azimulkabir.actua.data.importing

/**
 * One account's share of an import: its candidates, and the indexes into them to add even though
 * they match an existing transaction (Actual's `forceAddTransaction`).
 */
data class ImportGroup(val accountId: String, val candidates: List<ImportCandidate>, val addAsNew: Set<Int> = emptySet())

/** A reviewed row ready to import, with the account chosen for it (null: none yet). */
data class ReviewedCandidate(val candidate: ImportCandidate, val accountId: String?, val addAsNew: Boolean = false)

/**
 * Splits [rows] into one [ImportGroup] per account, in order of first appearance and keeping each
 * account's rows in review order; rows without an account are returned in [ImportGroups.unassigned].
 */
fun groupImportRows(rows: List<ReviewedCandidate>): ImportGroups {
    val groups = rows.filter { it.accountId != null }.groupBy { requireNotNull(it.accountId) }.map { (accountId, inAccount) ->
        ImportGroup(
            accountId,
            inAccount.map(ReviewedCandidate::candidate),
            inAccount.withIndex().filter { it.value.addAsNew }.mapTo(mutableSetOf()) { it.index },
        )
    }
    return ImportGroups(groups, rows.filter { it.accountId == null })
}

data class ImportGroups(val groups: List<ImportGroup>, val unassigned: List<ReviewedCandidate>) {
    val importCount: Int get() = groups.sumOf { it.candidates.size }
}
