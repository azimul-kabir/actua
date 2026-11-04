package com.azimulkabir.actua.data.receipts

import com.azimulkabir.actua.model.SplitLine

/**
 * Turns a scanned receipt's tax and line items into the lines of the existing split editor. The lines are
 * only a suggestion: nothing is saved, every line can be edited or removed, and when the amounts do not add
 * up to the total the editor shows the difference and does not save until the user resolves it. No amount
 * is invented or hidden to make the lines add up.
 */
object ReceiptSplits {
    /**
     * [category] is the transaction's category so far (blank when none), used for the remainder and for
     * items; the tax line gets no category, for the user to pick or leave. Returns an empty list when fewer
     * than two lines would result.
     */
    fun build(
        totalCents: Long,
        taxCents: Long?,
        items: List<ReceiptItem>,
        includeTax: Boolean,
        includeItems: Boolean,
        category: String,
        taxNote: String,
    ): List<SplitLine> {
        val tax = taxCents?.takeIf { includeTax && it > 0 }
        val itemLines = if (includeItems) {
            items.filter { it.cents > 0 }.map { SplitLine(category = category, amountCents = it.cents, notes = it.name) }
        } else {
            emptyList()
        }
        val lines = if (itemLines.isEmpty()) {
            // Tax only: what is left of the total after the tax stays on the transaction's category.
            if (tax == null || tax >= totalCents) return emptyList()
            listOf(SplitLine(category = category, amountCents = totalCents - tax), taxLine(tax, taxNote))
        } else {
            itemLines + listOfNotNull(tax?.let { taxLine(it, taxNote) })
        }
        return lines.takeIf { it.size >= 2 }.orEmpty()
    }

    private fun taxLine(cents: Long, note: String) = SplitLine(category = "", amountCents = cents, notes = note)

    /** The part of [totalCents] the [lines] do not cover (negative when they exceed it); zero when they add up. */
    fun difference(totalCents: Long, lines: List<SplitLine>): Long =
        totalCents - lines.sumOf { if (it.isOpposite) -it.amountCents else it.amountCents }
}
