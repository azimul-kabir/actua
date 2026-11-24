package com.azimulkabir.actua.data.budget

/**
 * Actual's `calcBufferedAmount`: holding [amountCents] adds to the manual hold [bufferedCents], never
 * releasing more than is held nor holding more than the positive part of [toBudgetCents].
 */
fun heldAfterHold(toBudgetCents: Long, bufferedCents: Long, amountCents: Long): Long =
    bufferedCents + minOf(maxOf(amountCents, -bufferedCents), maxOf(toBudgetCents, 0L))
