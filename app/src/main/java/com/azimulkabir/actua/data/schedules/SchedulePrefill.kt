package com.azimulkabir.actua.data.schedules

import com.azimulkabir.actua.model.Transaction
import kotlin.math.abs

/**
 * Values copied from an existing transaction into the new-schedule form. Nothing is written until the
 * user saves the schedule through the normal path.
 */
data class SchedulePrefill(
    val payeeName: String,
    val accountName: String,
    /** Signed cents: negative for an expense, positive for income. */
    val amountCents: Long,
    val startDate: DayDate,
) {
    companion object {
        /** True when [transaction] can seed a schedule. Transfers are excluded: they have no payee to schedule. */
        fun isAvailable(transaction: Transaction): Boolean =
            transaction.transferAccount == null && transaction.transferAccountId == null &&
                !transaction.isUpcoming && transaction.amountCents != 0L

        /** Splits prefill their total amount; the category is not part of a schedule's fields. */
        fun from(transaction: Transaction): SchedulePrefill? {
            if (!isAvailable(transaction)) return null
            return SchedulePrefill(
                payeeName = transaction.payee,
                accountName = transaction.account,
                amountCents = if (transaction.amountCents < 0) -abs(transaction.amountCents) else abs(transaction.amountCents),
                // Stored dates are yyyymmdd; ISO is accepted too so a reformatted date still reads.
                startDate = transaction.date.toIntOrNull()?.let(DayDate::fromYyyymmdd)
                    ?: DayDate.fromIso(transaction.date) ?: DayDate.today(),
            )
        }
    }
}
