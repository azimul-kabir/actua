package com.azimulkabir.actua.data.schedules

import com.azimulkabir.actua.data.budget.model.ActualAccount
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToLong

/** Detects recurring transactions using the same sweeps and ranking as Actuali iOS. */
object ScheduleDiscovery {
    data class Candidate(val id: String, val date: DayDate, val amount: Long, val payeeId: String, val accountId: String)
    data class Match(val rank: Double, val amount: Long, val accountId: String, val payeeId: String,
        val config: RecurConfig, val exactDate: Boolean, val exactAmount: Boolean)
    data class Proposal(
        val id: String = UUID.randomUUID().toString(), val accountId: String, val payeeId: String,
        val amount: Long, val config: RecurConfig, val exactDate: Boolean, val exactAmount: Boolean,
    ) {
        val formFields get() = ScheduleFormFields(payeeId = payeeId, accountId = accountId,
            amount = ScheduledAmount.Fixed(amount),
            amountOp = if (exactAmount) ScheduleAmountOp.EXACT else ScheduleAmountOp.APPROXIMATE,
            date = ScheduleDateCondition.Recurring(config), postsTransaction = false)
    }

    fun approxThreshold(amount: Long): Long = (abs(amount).toDouble() * 0.075).roundToLong()
    fun rank(first: DayDate, second: DayDate) = 1.0 / (abs(first.daysUntil(second)).toDouble() + 1.0)

    fun discover(accounts: List<ActualAccount>, loadCandidates: (String, Int) -> List<Candidate>,
        latestDate: (String) -> DayDate?): List<Proposal> {
        val matches = mutableListOf<Match>()
        accounts.filterNot(ActualAccount::closed).forEach { account ->
            val latest = latestDate(account.id) ?: return@forEach
            val candidates = loadCandidates(account.id, latest.addingMonths(-9).yyyymmdd)
            if (candidates.isEmpty()) return@forEach
            val index = CandidateIndex(candidates)
            sweeps(latest).forEach { matches += run(it, index, account.id) }
        }
        return matches.groupBy(Match::payeeId).values.mapNotNull { it.maxByOrNull(Match::rank) }
            .map { Proposal(accountId = it.accountId, payeeId = it.payeeId, amount = it.amount,
                config = it.config, exactDate = it.exactDate, exactAmount = it.exactAmount) }.sortedBy(Proposal::payeeId)
    }

    data class Sweep(val start: DayDate, val dayCount: Int, val makeConfig: (DayDate) -> RecurConfig?)
    fun sweeps(latest: DayDate): List<Sweep> {
        val weekday = listOf("SU", "MO", "TU", "WE", "TH", "FR", "SA")[latest.weekday - 1]
        fun recur(frequency: RecurConfig.Frequency, start: DayDate, interval: Int = 1,
            patterns: List<RecurConfig.Pattern> = emptyList()) = RecurConfig(frequency, interval, start, patterns)
        return listOf(
            Sweep(latest.addingDays(-28), 14) { recur(RecurConfig.Frequency.WEEKLY, it) },
            Sweep(latest.addingDays(-49), 14) { recur(RecurConfig.Frequency.WEEKLY, it, 2) },
            Sweep(latest.addingMonths(-4), 62) { start -> start.takeIf { it.day <= 28 }?.let { recur(RecurConfig.Frequency.MONTHLY, it) } },
            Sweep(latest.addingMonths(-3), 1) { recur(RecurConfig.Frequency.MONTHLY, it, patterns = listOf(RecurConfig.Pattern("day", -1))) },
            Sweep(latest.addingMonths(-4), 1) { recur(RecurConfig.Frequency.MONTHLY, it, patterns = listOf(RecurConfig.Pattern("day", -1))) },
            Sweep(latest.addingDays(-56), 14) { recur(RecurConfig.Frequency.MONTHLY, it,
                patterns = listOf(RecurConfig.Pattern(weekday, 1), RecurConfig.Pattern(weekday, 3))) },
            Sweep(latest.addingMonths(-8), 14) { recur(RecurConfig.Frequency.MONTHLY, it,
                patterns = listOf(RecurConfig.Pattern(weekday, 2), RecurConfig.Pattern(weekday, 4))) },
        )
    }

    private fun run(sweep: Sweep, index: CandidateIndex, accountId: String): List<Match> = buildList {
        repeat(sweep.dayCount) { offset ->
            val start = sweep.start.addingDays(offset); val config = sweep.makeConfig(start) ?: return@repeat
            val dates = ScheduleRecurrence.upcomingDates(config, 3, start)
            if (dates.size == 3) addAll(match(dates.map { it to index.near(it, 2) }, config, accountId))
        }
    }

    fun match(occurrences: List<Pair<DayDate, List<Candidate>>>, config: RecurConfig, accountId: String): List<Match> {
        val reversed = occurrences.reversed(); val anchor = reversed.firstOrNull() ?: return emptyList()
        return buildList { anchor.second.forEach { transaction ->
            val threshold = approxThreshold(transaction.amount); val found = mutableListOf<Pair<Candidate, Double>>()
            for ((date, candidates) in reversed.drop(1)) {
                val hit = candidates.firstOrNull { it.payeeId == transaction.payeeId &&
                    it.amount in (transaction.amount - threshold)..(transaction.amount + threshold) } ?: break
                found += hit to rank(date, hit.date)
            }
            if (found.size != reversed.size - 1) return@forEach
            val total = found.sumOf { it.second } + rank(anchor.first, transaction.date)
            add(Match(total, transaction.amount, accountId, transaction.payeeId, config,
                total == occurrences.size.toDouble(), found.all { it.first.amount == transaction.amount }))
        } }
    }

    class CandidateIndex(candidates: List<Candidate>) {
        private val byDate = candidates.groupBy { it.date.yyyymmdd }
        fun near(date: DayDate, days: Int) = (-days..days).flatMap { byDate[date.addingDays(it).yyyymmdd].orEmpty() }
    }
}
