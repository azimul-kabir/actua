package com.azimulkabir.actua.ui.transactions

import com.azimulkabir.actua.data.schedules.ScheduleListItem
import com.azimulkabir.actua.data.schedules.ScheduleStatus
import com.azimulkabir.actua.model.Transaction

private val upcomingStatuses = setOf(ScheduleStatus.DUE, ScheduleStatus.UPCOMING)

/** Projects each due/upcoming schedule occurrence as a synthetic, unposted [Transaction] row. */
fun upcomingTransactionsFrom(schedules: List<ScheduleListItem>): List<Transaction> =
    schedules.mapNotNull { item ->
        if (item.status !in upcomingStatuses) return@mapNotNull null
        val date = item.schedule.nextDate ?: return@mapNotNull null
        val accountName = item.accountName ?: return@mapNotNull null
        val amountCents = item.schedule.postAmount
        Transaction(
            id = "upcoming-${item.schedule.id}-${date.yyyymmdd}",
            date = date.yyyymmdd.toString(),
            payee = item.title,
            category = "Upcoming",
            account = accountName,
            amount = (amountCents / 100).toInt(),
            cleared = false,
            amountCents = amountCents,
            scheduleId = item.schedule.id,
            isUpcoming = true,
        )
    }
