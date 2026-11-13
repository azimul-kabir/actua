package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import com.azimulkabir.actua.data.LOAN_ESCROW_PAYEE
import com.azimulkabir.actua.data.LOAN_INTEREST_PAYEE
import com.azimulkabir.actua.data.loans.LoanAmortization
import com.azimulkabir.actua.model.BudgetTarget
import com.azimulkabir.actua.model.LoanConfig
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * Localized display names for the demo budget, resolved once at seed time. Seeded row IDs stay
 * locale-neutral; only these display strings localize, mirroring #424's treatment.
 */
internal data class DemoBudgetLabels(
    val budgetName: String,
    val checkingAccount: String,
    val savingsAccount: String,
    val creditAccount: String,
    val investmentAccount: String,
    val cashAccount: String,
    val rewardsCardAccount: String,
    val carLoanAccount: String,
    val studentLoanAccount: String,
    val incomeGroup: String,
    val essentialsGroup: String,
    val lifestyleGroup: String,
    val debtGroup: String,
    val goalsGroup: String,
    val salaryCategory: String,
    val freelanceCategory: String,
    val interestIncomeCategory: String,
    val startingBalancesCategory: String,
    val rentCategory: String,
    val groceriesCategory: String,
    val utilitiesCategory: String,
    val transportCategory: String,
    val healthCategory: String,
    val insuranceCategory: String,
    val diningOutCategory: String,
    val entertainmentCategory: String,
    val shoppingCategory: String,
    val subscriptionsCategory: String,
    val giftsCategory: String,
    val carLoanCategory: String,
    val studentLoanCategory: String,
    val cardInterestCategory: String,
    val emergencyFundCategory: String,
    val vacationCategory: String,
    val employerPayee: String,
    val landlordPayee: String,
    val supermarketPayee: String,
    val utilitiesPayee: String,
    val fuelPayee: String,
    val restaurantPayee: String,
    val streamingPayee: String,
    val cornerGrocerPayee: String,
    val bigBoxStorePayee: String,
    val internetPayee: String,
    val mobilePayee: String,
    val transitPayee: String,
    val pizzaPayee: String,
    val sushiPayee: String,
    val cinemaPayee: String,
    val booksPayee: String,
    val airlinePayee: String,
    val indexFundPayee: String,
    val clientPayee: String,
    val bankInterestPayee: String,
    val pharmacyPayee: String,
    val clinicPayee: String,
    val insurancePayee: String,
    val onlineStorePayee: String,
    val cloudStoragePayee: String,
    val musicPayee: String,
    val giftShopPayee: String,
    val marketStallPayee: String,
    val rewardsBankPayee: String,
    val groceriesNote: String,
    val checkingNote: String,
    val carLoanNote: String,
    val rewardsCardNote: String,
    val salarySchedule: String,
    val rentSchedule: String,
    val streamingSchedule: String,
    val cardPaymentSchedule: String,
    val rewardsCardPaymentSchedule: String,
    val carLoanSchedule: String,
    val studentLoanSchedule: String,
    val insuranceSchedule: String,
    val overviewReport: String,
    val welcomeReport: String,
    val thisMonthReport: String,
    val spendingByCategoryReport: String,
    val monthlySpendingReport: String,
    val topPayeesReport: String,
    val spendingTrendReport: String,
    val netWorthCard: String,
    val cashFlowCard: String,
    val foodPerMonthCard: String,
    val whereTheMoneyWentCard: String,
    val budgetAnalysisCard: String,
    val ageOfMoneyCard: String,
    val calendarCard: String,
)

/** Seeds a local-only budget with realistic data that exercises Actua's main workflows. */
internal object DemoBudgetSeeder {
    const val BUDGET_ID = "demo"
    private const val HISTORY_MONTHS = 12
    /** Days before today whose transactions stay unreconciled, whatever month they fall in. */
    private const val UNRECONCILED_RECENT_DAYS = 10L
    private const val CARD_PAYMENT_DAY = 25
    private const val SALARY_CENTS = 390000L
    private const val INSURANCE_CENTS = 12000L

    /** A card that carries a balance: it starts owing this much and pays only part of it each month. */
    private const val REWARDS_OPENING_OWED_CENTS = 180000L
    private const val REWARDS_APR_PERCENT = 21.99
    private const val REWARDS_PAYMENT_DAY = 27
    private const val REWARDS_PAYMENT_CENTS = 25000L

    /**
     * Terms of a seeded loan. [paymentCents] includes [feesCents], as Actua's loan payments do, and
     * [earlierPayments] were made before the seeded history begins.
     */
    private class LoanTerms(
        val originalBalanceCents: Long,
        val annualRatePercent: Double,
        val paymentCents: Long,
        val feesCents: Long,
        val termMonths: Int,
        val paymentDay: Int,
        val earlierPayments: Int,
    ) {
        fun owedAfterEarlierPayments(): Long {
            var owed = originalBalanceCents
            repeat(earlierPayments) {
                owed -= paymentCents - LoanAmortization.periodicInterest(owed, annualRatePercent) - feesCents
            }
            return owed
        }
    }

    private val CAR_LOAN = LoanTerms(2200000, 6.5, 44550, 1500, 60, 5, 8)
    private val STUDENT_LOAN = LoanTerms(3500000, 4.5, 36300, 0, 120, 12, 20)

    fun seed(database: SQLiteDatabase, labels: DemoBudgetLabels, now: LocalDate = LocalDate.now()) {
        database.beginTransaction()
        try {
            // BlankBudgetFactory intentionally ships starter categories and Actual's default dashboard
            // for newly-created budgets. The demo is curated, so replace only those starter rows while
            // preserving schema/migrations.
            database.execSQL("DELETE FROM category_mapping")
            database.execSQL("DELETE FROM categories")
            database.execSQL("DELETE FROM category_groups")
            database.execSQL("DELETE FROM dashboard")
            database.execSQL("DELETE FROM dashboard_pages")

            val checking = "demo-account-checking"
            val savings = "demo-account-savings"
            val cash = "demo-account-cash"
            val credit = "demo-account-credit"
            val rewards = "demo-account-rewards"
            val investment = "demo-account-investment"
            val carLoanAccount = "demo-account-car-loan"
            val studentLoanAccount = "demo-account-student-loan"

            insertAccount(database, checking, labels.checkingAccount, "checking", 0, 1.0)
            insertAccount(database, savings, labels.savingsAccount, "savings", 0, 2.0)
            insertAccount(database, cash, labels.cashAccount, "other", 0, 3.0)
            insertAccount(database, credit, labels.creditAccount, "credit", 0, 4.0)
            insertAccount(database, rewards, labels.rewardsCardAccount, "credit", 0, 5.0)
            insertAccount(database, investment, labels.investmentAccount, "investment", 1, 6.0)
            // Loans are off-budget debt accounts; their payments budget through a paired category.
            insertAccount(database, carLoanAccount, labels.carLoanAccount, "debt", 1, 7.0)
            insertAccount(database, studentLoanAccount, labels.studentLoanAccount, "debt", 1, 8.0)

            val incomeGroup = group(database, labels.incomeGroup, true, 1.0)
            val essentialsGroup = group(database, labels.essentialsGroup, false, 2.0)
            val lifestyleGroup = group(database, labels.lifestyleGroup, false, 3.0)
            val debtGroup = group(database, labels.debtGroup, false, 4.0)
            val goalsGroup = group(database, labels.goalsGroup, false, 5.0)

            val salary = category(database, labels.salaryCategory, incomeGroup, true, 1.0)
            val freelance = category(database, labels.freelanceCategory, incomeGroup, true, 2.0)
            val interestIncome = category(database, labels.interestIncomeCategory, incomeGroup, true, 3.0)
            val starting = category(database, labels.startingBalancesCategory, incomeGroup, true, 4.0)
            val rent = category(database, labels.rentCategory, essentialsGroup, false, 1.0)
            val groceries = category(database, labels.groceriesCategory, essentialsGroup, false, 2.0)
            val utilities = category(database, labels.utilitiesCategory, essentialsGroup, false, 3.0)
            val transport = category(database, labels.transportCategory, essentialsGroup, false, 4.0)
            val health = category(database, labels.healthCategory, essentialsGroup, false, 5.0)
            val insurance = category(database, labels.insuranceCategory, essentialsGroup, false, 6.0)
            val dining = category(database, labels.diningOutCategory, lifestyleGroup, false, 1.0)
            val entertainment = category(database, labels.entertainmentCategory, lifestyleGroup, false, 2.0)
            val shopping = category(database, labels.shoppingCategory, lifestyleGroup, false, 3.0)
            val subscriptions = category(database, labels.subscriptionsCategory, lifestyleGroup, false, 4.0)
            val gifts = category(database, labels.giftsCategory, lifestyleGroup, false, 5.0)
            val carLoanCategory = category(database, labels.carLoanCategory, debtGroup, false, 1.0)
            val studentLoanCategory = category(database, labels.studentLoanCategory, debtGroup, false, 2.0)
            val cardInterest = category(database, labels.cardInterestCategory, debtGroup, false, 3.0)
            val emergency = category(database, labels.emergencyFundCategory, goalsGroup, false, 1.0)
            val vacation = category(database, labels.vacationCategory, goalsGroup, false, 2.0)

            val monthStart = now.withDayOfMonth(1).toString()
            setTarget(database, groceries, BudgetTarget(BudgetTarget.Type.FIXED, 55000, startingDate = monthStart))
            setTarget(database, emergency, BudgetTarget(BudgetTarget.Type.FIXED, 35000, startingDate = monthStart))
            setTarget(database, vacation, BudgetTarget(BudgetTarget.Type.BY_DATE, 500000, YearMonth.from(now).plusMonths(6).toString()))
            setTarget(database, insurance, BudgetTarget(BudgetTarget.Type.FIXED, INSURANCE_CENTS, startingDate = monthStart))
            setTarget(database, health, BudgetTarget(BudgetTarget.Type.FIXED, 8000, startingDate = monthStart))
            setTarget(database, subscriptions, BudgetTarget(BudgetTarget.Type.FIXED, 4000, startingDate = monthStart))
            // The loan categories budget exactly what the loan screen expects to pay each month.
            setTarget(database, carLoanCategory, BudgetTarget(BudgetTarget.Type.FIXED, CAR_LOAN.paymentCents, startingDate = monthStart))
            setTarget(database, studentLoanCategory, BudgetTarget(BudgetTarget.Type.FIXED, STUDENT_LOAN.paymentCents, startingDate = monthStart))
            setTarget(
                database, cardInterest,
                BudgetTarget(BudgetTarget.Type.HISTORICAL, historicalMode = BudgetTarget.HistoricalMode.AVERAGE, historicalMonths = 3),
            )
            setTarget(
                database, shopping,
                BudgetTarget(BudgetTarget.Type.LIMIT, 12000, limitPeriod = BudgetTarget.LimitPeriod.MONTHLY),
            )
            setTargets(
                database, dining,
                listOf(
                    BudgetTarget(BudgetTarget.Type.LIMIT, 14000, limitPeriod = BudgetTarget.LimitPeriod.MONTHLY),
                    BudgetTarget(BudgetTarget.Type.REFILL),
                ),
            )
            setTarget(
                database, transport,
                BudgetTarget(
                    BudgetTarget.Type.FIXED, 3500, startingDate = monthStart,
                    period = BudgetTarget.Period.WEEK,
                ),
            )
            setTarget(
                database, entertainment,
                BudgetTarget(
                    BudgetTarget.Type.HISTORICAL,
                    historicalMode = BudgetTarget.HistoricalMode.AVERAGE, historicalMonths = 3,
                ),
            )

            val paycheck = payee(database, labels.employerPayee)
            val client = payee(database, labels.clientPayee)
            val bankInterest = payee(database, labels.bankInterestPayee)
            val landlord = payee(database, labels.landlordPayee)
            val supermarket = payee(database, labels.supermarketPayee)
            val cornerGrocer = payee(database, labels.cornerGrocerPayee)
            val marketStall = payee(database, labels.marketStallPayee)
            val bigBox = payee(database, labels.bigBoxStorePayee)
            val utilityCo = payee(database, labels.utilitiesPayee)
            val internet = payee(database, labels.internetPayee)
            val mobile = payee(database, labels.mobilePayee)
            val fuel = payee(database, labels.fuelPayee)
            val transit = payee(database, labels.transitPayee)
            val pharmacy = payee(database, labels.pharmacyPayee)
            val clinic = payee(database, labels.clinicPayee)
            val insuranceCo = payee(database, labels.insurancePayee)
            val restaurant = payee(database, labels.restaurantPayee)
            val pizza = payee(database, labels.pizzaPayee)
            val sushi = payee(database, labels.sushiPayee)
            val streaming = payee(database, labels.streamingPayee)
            val cinema = payee(database, labels.cinemaPayee)
            val books = payee(database, labels.booksPayee)
            val onlineStore = payee(database, labels.onlineStorePayee)
            val cloudStorage = payee(database, labels.cloudStoragePayee)
            val music = payee(database, labels.musicPayee)
            val giftShop = payee(database, labels.giftShopPayee)
            val airline = payee(database, labels.airlinePayee)
            val indexFund = payee(database, labels.indexFundPayee)
            val rewardsBank = payee(database, labels.rewardsBankPayee)
            // Same names the loan screen resolves when it records a payment, so they are reused.
            val loanInterest = payee(database, LOAN_INTEREST_PAYEE)
            val loanFees = payee(database, LOAN_ESCROW_PAYEE)
            val transferPayees = listOf(checking, savings, cash, credit, rewards, investment, carLoanAccount, studentLoanAccount)
                .associateWith { transferPayee(database, it) }
            fun transferPayeeOf(account: String) = requireNotNull(transferPayees[account])

            val firstMonth = YearMonth.from(now).minusMonths(HISTORY_MONTHS - 1L)
            val openingDate = firstMonth.minusMonths(1).atDay(1)
            opening(database, checking, starting, openingDate, 450000)
            opening(database, savings, starting, openingDate, 150000)
            opening(database, cash, starting, openingDate, 20000)
            opening(database, investment, starting, openingDate, 300000)
            // The rewards card starts with a balance it keeps carrying, so it accrues interest.
            var rewardsOwed = REWARDS_OPENING_OWED_CENTS
            opening(database, rewards, starting, openingDate, -rewardsOwed)
            // Loans started before the seeded history: replay those payments for the balance at its start.
            var carOwed = CAR_LOAN.owedAfterEarlierPayments()
            var studentOwed = STUDENT_LOAN.owedAfterEarlierPayments()
            opening(database, carLoanAccount, starting, openingDate, -carOwed)
            opening(database, studentLoanAccount, starting, openingDate, -studentOwed)

            // A year of everyday activity, oldest month first so running balances (the card and the
            // loans) carry forward. Amounts vary deterministically by month so reports have texture
            // without making the demo differ between resets. Nothing is dated after today.
            for (monthsAgo in HISTORY_MONTHS - 1 downTo 0) {
                val base = YearMonth.from(now).minusMonths(monthsAgo.toLong()).atDay(1)
                var cardCharges = 0L
                fun vary(min: Long, max: Long, seed: Int): Long = min + Math.floorMod(monthsAgo * 7919L + seed * 104729L, max - min + 1)
                fun dateOf(day: Int): LocalDate? = safeDay(base, day).takeUnless { it.isAfter(now) }
                fun cleared(date: LocalDate) = monthsAgo > 0 || date.isBefore(now.minusDays(3))
                // Recent days stay unreconciled even just after a month change, so the demo always has something to reconcile.
                fun reconciled(date: LocalDate) = monthsAgo > 0 && date.isBefore(now.minusDays(UNRECONCILED_RECENT_DAYS))
                fun spend(day: Int, account: String, category: String, payee: String, cents: Long) {
                    val date = dateOf(day) ?: return
                    txn(database, account, category, payee, date, -cents, cleared(date), reconciled(date))
                    if (account == credit && day < CARD_PAYMENT_DAY) cardCharges += cents
                    if (account == rewards) rewardsOwed += cents
                }
                fun loanPayment(terms: LoanTerms, day: Int, loan: String, category: String, owed: Long): Long {
                    val date = dateOf(day) ?: return owed
                    val interest = LoanAmortization.periodicInterest(owed, terms.annualRatePercent)
                    // The lender's charges land on the loan; the transfer from checking carries the category.
                    txn(database, loan, null, loanInterest, date, -interest, true, reconciled(date))
                    if (terms.feesCents > 0) txn(database, loan, null, loanFees, date, -terms.feesCents, true, reconciled(date))
                    transfer(
                        database, checking, loan, transferPayeeOf(loan), transferPayeeOf(checking), date,
                        terms.paymentCents, reconciled(date), category,
                    )
                    return owed - (terms.paymentCents - interest - terms.feesCents)
                }

                dateOf(1)?.let { txn(database, checking, salary, paycheck, it, SALARY_CENTS, true, reconciled(it)) }
                dateOf(2)?.let { date ->
                    transfer(database, checking, cash, transferPayeeOf(cash), transferPayeeOf(checking), date, 10000, reconciled(date))
                }
                spend(3, checking, rent, landlord, 110000)
                spend(4, checking, groceries, supermarket, vary(9500, 13500, 1))
                carOwed = loanPayment(CAR_LOAN, CAR_LOAN.paymentDay, carLoanAccount, carLoanCategory, carOwed)
                spend(6, rewards, cardInterest, rewardsBank, LoanAmortization.periodicInterest(rewardsOwed, REWARDS_APR_PERCENT))
                spend(6, rewards, subscriptions, cloudStorage, 999)
                spend(7, credit, dining, restaurant, vary(1200, 2000, 2))
                dateOf(8)?.let { date ->
                    val household = vary(4000, 6500, 3); val treats = vary(1500, 3200, 4)
                    split(database, credit, bigBox, date, listOf(groceries to -household, entertainment to -treats), cleared(date), reconciled(date))
                    cardCharges += household + treats
                }
                spend(9, checking, utilities, utilityCo, vary(7000, 9500, 5))
                spend(9, rewards, shopping, onlineStore, vary(4000, 10000, 19))
                spend(10, credit, transport, fuel, vary(4500, 6000, 6))
                spend(10, checking, insurance, insuranceCo, INSURANCE_CENTS)
                spend(11, credit, groceries, cornerGrocer, vary(3000, 5500, 7))
                spend(12, credit, entertainment, streaming, 1299)
                studentOwed = loanPayment(STUDENT_LOAN, STUDENT_LOAN.paymentDay, studentLoanAccount, studentLoanCategory, studentOwed)
                spend(13, checking, groceries, supermarket, vary(10000, 14000, 8))
                spend(14, checking, utilities, internet, 6500)
                spend(14, cash, transport, transit, vary(500, 1500, 20))
                spend(15, credit, transport, transit, vary(2500, 3500, 9))
                dateOf(15)?.let { txn(database, checking, freelance, client, it, vary(20000, 35000, 21), cleared(it), reconciled(it)) }
                spend(16, credit, dining, pizza, vary(2400, 3800, 10))
                spend(16, credit, subscriptions, music, 1099)
                spend(17, checking, health, pharmacy, vary(1500, 3500, 22))
                spend(18, checking, utilities, mobile, 3500)
                spend(19, credit, entertainment, cinema, vary(1800, 3200, 11))
                spend(19, rewards, dining, restaurant, vary(4000, 9000, 23))
                spend(20, checking, groceries, supermarket, vary(9000, 13000, 12))
                spend(21, credit, dining, restaurant, vary(900, 1600, 13))
                if (monthsAgo % 2 == 0) spend(21, rewards, gifts, giftShop, vary(2500, 6500, 24))
                spend(22, credit, transport, fuel, vary(4500, 6000, 14))
                spend(22, cash, groceries, marketStall, vary(1500, 3000, 25))
                if (monthsAgo % 3 == 1) spend(23, rewards, health, clinic, vary(4000, 8000, 26))
                spend(24, credit, dining, sushi, vary(4000, 6000, 15))
                spend(24, cash, dining, pizza, vary(800, 1800, 27))
                if (monthsAgo == 5) spend(17, credit, vacation, airline, 64800)
                dateOf(CARD_PAYMENT_DAY)?.let { date ->
                    transfer(database, checking, credit, transferPayeeOf(credit), transferPayeeOf(checking), date, cardCharges, reconciled(date))
                }
                spend(26, credit, groceries, cornerGrocer, vary(2500, 4500, 16))
                dateOf(REWARDS_PAYMENT_DAY)?.let { date ->
                    // Only part of the balance is cleared, which is what makes the card accrue interest.
                    transfer(database, checking, rewards, transferPayeeOf(rewards), transferPayeeOf(checking), date, REWARDS_PAYMENT_CENTS, reconciled(date))
                    rewardsOwed -= REWARDS_PAYMENT_CENTS
                }
                spend(27, credit, entertainment, books, vary(1500, 3000, 17))
                dateOf(28)?.let { date ->
                    transfer(database, checking, savings, transferPayeeOf(savings), transferPayeeOf(checking), date, 35000, reconciled(date))
                    txn(database, savings, interestIncome, bankInterest, date, vary(300, 900, 28), true, reconciled(date))
                    // Off-budget growth keeps Net Worth trending like a real portfolio.
                    txn(database, investment, null, indexFund, date, vary(9000, 24000, 18), true, reconciled(date))
                }
            }

            // Every seeded month is fully allocated; the oldest month also seeds the savings goals.
            repeat(HISTORY_MONTHS) { i ->
                val ym = YearMonth.from(now).minusMonths(i.toLong())
                val oldest = i == HISTORY_MONTHS - 1
                budget(database, ym, rent, 110000)
                budget(database, ym, groceries, 55000)
                budget(database, ym, utilities, 19000)
                budget(database, ym, transport, 16000)
                budget(database, ym, health, 8000)
                budget(database, ym, insurance, INSURANCE_CENTS)
                budget(database, ym, dining, 14000)
                budget(database, ym, entertainment, 11000)
                budget(database, ym, shopping, 12000)
                budget(database, ym, subscriptions, 4000)
                budget(database, ym, gifts, 5000)
                budget(database, ym, carLoanCategory, CAR_LOAN.paymentCents)
                budget(database, ym, studentLoanCategory, STUDENT_LOAN.paymentCents)
                budget(database, ym, cardInterest, 3000)
                budget(database, ym, emergency, if (oldest) 335000 else 35000)
                budget(database, ym, vacation, if (oldest) 130000 else 30000)
            }

            note(database, groceries, labels.groceriesNote)
            note(database, carLoanCategory, labels.carLoanNote)
            note(database, "account-$checking", labels.checkingNote)
            note(database, "account-$rewards", labels.rewardsCardNote)

            // Credit-card configuration uses the same preference key read by Actua.
            database.execSQL(
                "INSERT OR REPLACE INTO preferences(id,value) VALUES(?,?)",
                arrayOf<Any?>("actuali:credit_card:$credit", JSONObject().put("statementDay", 20).put("dueOffsetDays", 15).put("limit", 500000).toString()),
            )
            // This card's due date is a fixed day of the month rather than an offset from the statement.
            database.execSQL(
                "INSERT OR REPLACE INTO preferences(id,value) VALUES(?,?)",
                arrayOf<Any?>(
                    "actuali:credit_card:$rewards",
                    JSONObject().put("statementDay", 5).put("dueOffsetDays", 23).put("dueDay", REWARDS_PAYMENT_DAY + 1).put("limit", 600000).toString(),
                ),
            )
            // Loan terms use the preference key Actua and Actuali read for tracked loans.
            seedLoan(database, carLoanAccount, CAR_LOAN, carLoanCategory, openingDate)
            seedLoan(database, studentLoanAccount, STUDENT_LOAN, studentLoanCategory, openingDate)

            seedRules(
                database,
                listOf(
                    supermarket to groceries, cornerGrocer to groceries, marketStall to groceries, streaming to entertainment,
                    cinema to entertainment, fuel to transport, transit to transport, internet to utilities,
                    pharmacy to health, clinic to health, insuranceCo to insurance, onlineStore to shopping,
                    cloudStorage to subscriptions, music to subscriptions, giftShop to gifts,
                    rewardsBank to cardInterest, client to freelance, bankInterest to interestIncome,
                ),
            )
            seedSchedules(
                database, labels, now, checking, paycheck, landlord, streaming, credit, insuranceCo,
                transferPayeeOf(credit), transferPayeeOf(rewards), transferPayeeOf(carLoanAccount), transferPayeeOf(studentLoanAccount),
            )
            val reports = seedCustomReports(database, labels)
            seedDashboard(database, labels, now, reports, groceries, dining)

            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    private fun seedLoan(db: SQLiteDatabase, account: String, terms: LoanTerms, category: String, openingDate: LocalDate) {
        val config = LoanConfig(
            originalBalanceCents = terms.originalBalanceCents,
            annualRatePercent = terms.annualRatePercent,
            minimumPaymentCents = terms.paymentCents,
            escrowOrFeesCents = terms.feesCents.takeIf { it > 0 },
            categoryId = category,
            termMonths = terms.termMonths,
            paymentDay = terms.paymentDay,
            startDate = openingDate.minusMonths(terms.earlierPayments.toLong()).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE).toInt(),
        )
        db.execSQL(
            "INSERT OR REPLACE INTO preferences(id,value) VALUES(?,?)",
            arrayOf<Any?>(ActualBudgetDatabase.LOAN_PREFERENCE_PREFIX + account, ActualBudgetDatabase.encodeLoanConfig(config)),
        )
    }

    private fun insertAccount(db: SQLiteDatabase, id: String, name: String, type: String, offBudget: Int, order: Double) =
        db.execSQL("INSERT OR REPLACE INTO accounts(id,name,type,offbudget,closed,tombstone,sort_order) VALUES(?,?,?,?,0,0,?)", arrayOf<Any?>(id, name, type, offBudget, order))

    private fun group(db: SQLiteDatabase, name: String, income: Boolean, order: Double): String = id().also {
        db.execSQL("INSERT INTO category_groups(id,name,is_income,sort_order,tombstone,hidden) VALUES(?,?,?,?,0,0)", arrayOf<Any?>(it, name, if (income) 1 else 0, order))
    }

    private fun category(db: SQLiteDatabase, name: String, group: String, income: Boolean, order: Double): String = id().also {
        db.execSQL("INSERT INTO categories(id,name,is_income,cat_group,sort_order,tombstone,hidden,template_settings) VALUES(?,?,?,?,?,0,0,?)", arrayOf<Any?>(it, name, if (income) 1 else 0, group, order, "{\"source\":\"notes\"}"))
        db.execSQL("INSERT OR REPLACE INTO category_mapping(id,transferId) VALUES(?,?)", arrayOf(it, it))
    }

    private fun setTarget(db: SQLiteDatabase, categoryId: String, target: BudgetTarget) {
        db.execSQL("UPDATE categories SET goal_def=?, template_settings=? WHERE id=?", arrayOf(target.toGoalDef(), "{\"source\":\"ui\"}", categoryId))
    }

    private fun setTargets(db: SQLiteDatabase, categoryId: String, targets: List<BudgetTarget>) {
        val goalDef = com.azimulkabir.actua.model.BudgetAutomationDocument.encode(targets)
        db.execSQL("UPDATE categories SET goal_def=?, template_settings=? WHERE id=?", arrayOf(goalDef, "{\"source\":\"ui\"}", categoryId))
    }

    private fun payee(db: SQLiteDatabase, name: String): String = id().also {
        db.execSQL("INSERT INTO payees(id,name,tombstone) VALUES(?,?,0)", arrayOf(it, name))
    }

    private fun transferPayee(db: SQLiteDatabase, accountId: String): String = id().also {
        db.execSQL("INSERT INTO payees(id,name,tombstone,transfer_acct) VALUES(?,NULL,0,?)", arrayOf(it, accountId))
    }

    private fun opening(db: SQLiteDatabase, account: String, category: String, date: LocalDate, amount: Long) =
        txn(db, account, category, null, date, amount, true, true, starting = true)

    private fun txn(db: SQLiteDatabase, account: String, category: String?, payee: String?, date: LocalDate, amount: Long,
                    cleared: Boolean, reconciled: Boolean, starting: Boolean = false) {
        db.execSQL(
            "INSERT INTO transactions(id,acct,category,amount,description,date,starting_balance_flag,tombstone,cleared,reconciled,sort_order) VALUES(?,?,?,?,?,?,?,0,?,?,?)",
            arrayOf<Any?>(id(), account, category, amount, payee, date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE).toInt(), if (starting) 1 else 0, if (cleared) 1 else 0, if (reconciled) 1 else 0, System.nanoTime().toDouble()),
        )
    }

    private fun transfer(
        db: SQLiteDatabase,
        fromAccount: String,
        toAccount: String,
        fromPayee: String,
        toPayee: String,
        date: LocalDate,
        amount: Long,
        reconciled: Boolean,
        category: String? = null,
    ) {
        val sourceId = id()
        val targetId = id()
        val ymd = date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE).toInt()
        val sortOrder = System.nanoTime().toDouble()
        db.execSQL(
            "INSERT INTO transactions(id,acct,category,amount,description,date,transferred_id,tombstone,cleared,reconciled,sort_order) VALUES(?,?,?,?,?,?,?,0,1,?,?)",
            arrayOf<Any?>(sourceId, fromAccount, category, -amount, fromPayee, ymd, targetId, if (reconciled) 1 else 0, sortOrder),
        )
        db.execSQL(
            "INSERT INTO transactions(id,acct,category,amount,description,date,transferred_id,tombstone,cleared,reconciled,sort_order) VALUES(?,?,?,?,?,?,?,0,1,?,?)",
            arrayOf<Any?>(targetId, toAccount, null, amount, toPayee, ymd, sourceId, if (reconciled) 1 else 0, sortOrder + 1),
        )
    }

    private fun split(
        db: SQLiteDatabase,
        account: String,
        payee: String,
        date: LocalDate,
        parts: List<Pair<String, Long>>,
        cleared: Boolean,
        reconciled: Boolean,
    ) {
        val parentId = id()
        val ymd = date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE).toInt()
        val sortOrder = System.nanoTime().toDouble()
        val flags = arrayOf<Any?>(if (cleared) 1 else 0, if (reconciled) 1 else 0)
        db.execSQL(
            "INSERT INTO transactions(id,isParent,isChild,acct,category,amount,description,date,tombstone,cleared,reconciled,sort_order) VALUES(?,1,0,?,NULL,?,?,?,0,?,?,?)",
            arrayOf<Any?>(parentId, account, parts.sumOf { it.second }, payee, ymd, *flags, sortOrder),
        )
        parts.forEachIndexed { index, (category, amount) ->
            db.execSQL(
                "INSERT INTO transactions(id,isParent,isChild,parent_id,acct,category,amount,description,date,tombstone,cleared,reconciled,sort_order) VALUES(?,0,1,?,?,?,?,?,?,0,?,?,?)",
                arrayOf<Any?>(id(), parentId, account, category, amount, payee, ymd, *flags, sortOrder - (index + 1) / 100.0),
            )
        }
    }

    private fun budget(db: SQLiteDatabase, month: YearMonth, category: String, amount: Long) {
        val key = month.year * 100 + month.monthValue
        db.execSQL("INSERT OR REPLACE INTO zero_budgets(id,month,category,amount,carryover) VALUES(?,?,?,?,0)", arrayOf<Any?>("$key-$category", key, category, amount))
        db.execSQL("INSERT OR IGNORE INTO created_budgets(month) VALUES(?)", arrayOf(month.toString()))
    }

    private fun note(db: SQLiteDatabase, id: String, text: String) = db.execSQL("INSERT OR REPLACE INTO notes(id,note) VALUES(?,?)", arrayOf(id, text))

    private fun seedRules(db: SQLiteDatabase, payeeCategories: List<Pair<String, String>>) {
        payeeCategories.forEach { (payee, category) ->
            val conditions = JSONArray().put(JSONObject().put("op", "is").put("field", "description").put("type", "id").put("value", payee))
            val actions = JSONArray().put(JSONObject().put("op", "set").put("field", "category").put("type", "id").put("value", category))
            db.execSQL("INSERT INTO rules(id,stage,conditions,actions,tombstone,conditions_op) VALUES(?,NULL,?,?,0,'and')", arrayOf(id(), conditions.toString(), actions.toString()))
        }
    }

    private fun seedSchedules(
        db: SQLiteDatabase, labels: DemoBudgetLabels, now: LocalDate, checking: String, paycheck: String, landlord: String,
        streaming: String, credit: String, insuranceCo: String,
        cardTransfer: String, rewardsTransfer: String, carLoanTransfer: String, studentLoanTransfer: String,
    ) {
        schedule(db, labels.salarySchedule, checking, paycheck, SALARY_CENTS, nextDate(now, 1), "monthly", true)
        schedule(db, labels.rentSchedule, checking, landlord, -110000, nextDate(now, 3), "monthly", false)
        schedule(db, labels.streamingSchedule, credit, streaming, -1299, nextDate(now, 12), "monthly", true)
        schedule(db, labels.insuranceSchedule, checking, insuranceCo, -INSURANCE_CENTS, nextDate(now, 10), "monthly", true)
        // Card and loan payments are transfers, so posting one moves money between the two accounts.
        schedule(db, labels.cardPaymentSchedule, checking, cardTransfer, -55000, nextDate(now, CARD_PAYMENT_DAY), "monthly", false)
        schedule(db, labels.rewardsCardPaymentSchedule, checking, rewardsTransfer, -REWARDS_PAYMENT_CENTS, nextDate(now, REWARDS_PAYMENT_DAY), "monthly", false)
        schedule(db, labels.carLoanSchedule, checking, carLoanTransfer, -CAR_LOAN.paymentCents, nextDate(now, CAR_LOAN.paymentDay), "monthly", false)
        schedule(db, labels.studentLoanSchedule, checking, studentLoanTransfer, -STUDENT_LOAN.paymentCents, nextDate(now, STUDENT_LOAN.paymentDay), "monthly", false)
    }

    /** The next [day] of the month that is still ahead, matching the history that already posted this month's. */
    private fun nextDate(now: LocalDate, day: Int): LocalDate {
        val thisMonth = safeDay(now.withDayOfMonth(1), day)
        return if (thisMonth.isAfter(now)) thisMonth else safeDay(now.plusMonths(1).withDayOfMonth(1), day)
    }

    private fun schedule(db: SQLiteDatabase, name: String, account: String, payee: String, amount: Long, next: LocalDate, period: String, autoPost: Boolean) {
        val scheduleId = id(); val ruleId = id(); val nextId = id()
        val dateValue = JSONObject().put("start", next.toString()).put("frequency", period).put("interval", 1)
        val conditions = JSONArray()
            .put(JSONObject().put("op", "is").put("field", "payee").put("value", payee))
            .put(JSONObject().put("op", "is").put("field", "account").put("value", account))
            .put(JSONObject().put("op", "isapprox").put("field", "date").put("value", dateValue))
            .put(JSONObject().put("op", "isapprox").put("field", "amount").put("value", amount))
        val actions = JSONArray().put(JSONObject().put("op", "link-schedule").put("value", scheduleId))
        db.execSQL("INSERT INTO rules(id,stage,conditions,actions,tombstone,conditions_op) VALUES(?,NULL,?,?,0,'and')", arrayOf(ruleId, conditions.toString(), actions.toString()))
        db.execSQL("INSERT INTO schedules(id,rule,active,completed,posts_transaction,tombstone,name) VALUES(?,?,1,0,?,0,?)", arrayOf<Any?>(scheduleId, ruleId, if (autoPost) 1 else 0, name))
        val ymd = next.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE).toInt()
        val ts = System.currentTimeMillis()
        db.execSQL("INSERT INTO schedules_next_date(id,schedule_id,local_next_date,local_next_date_ts,base_next_date,base_next_date_ts,tombstone) VALUES(?,?,?,?,?,?,0)", arrayOf<Any?>(nextId, scheduleId, ymd, ts, ymd, ts))
    }

    private fun seedCustomReports(db: SQLiteDatabase, labels: DemoBudgetLabels): List<String> {
        fun report(name: String, graph: String, mode: String, groupBy: String, range: String): String = id().also {
            db.execSQL(
                "INSERT INTO custom_reports(id,name,date_static,date_range,mode,group_by,balance_type,graph_type,interval,include_current,conditions,conditions_op,tombstone) " +
                    "VALUES(?,?,0,?,?,?,'Payment',?,'Monthly',1,'[]','and',0)",
                arrayOf<Any?>(it, name, range, mode, groupBy, graph),
            )
        }
        return listOf(
            report(labels.spendingByCategoryReport, "DonutGraph", "total", "Category", "Last 3 months"),
            report(labels.monthlySpendingReport, "StackedBarGraph", "time", "Group", "Last 6 months"),
            report(labels.topPayeesReport, "BarGraph", "total", "Payee", "Last 3 months"),
            report(labels.spendingTrendReport, "LineGraph", "time", "Group", "Last 12 months"),
        )
    }

    private fun seedDashboard(db: SQLiteDatabase, labels: DemoBudgetLabels, now: LocalDate, reports: List<String>, groceries: String, dining: String) {
        val page = "demo-dashboard-main"
        db.execSQL("INSERT OR REPLACE INTO dashboard_pages(id,name,tombstone) VALUES(?,?,0)", arrayOf(page, labels.overviewReport))
        var y = 0
        fun card(type: String, meta: JSONObject?, width: Int = 12, height: Int = 2) {
            db.execSQL(
                "INSERT INTO dashboard(id,type,width,height,x,y,meta,tombstone,dashboard_page_id) VALUES(?,?,?,?,?,?,?,0,?)",
                arrayOf<Any?>(id(), type, width, height, 0, y, meta?.toString(), page),
            )
            y += height
        }
        val lastSixMonths = JSONObject().put("start", YearMonth.from(now).minusMonths(5).toString())
            .put("end", YearMonth.from(now).toString()).put("mode", "sliding-window")
        card("markdown-card", JSONObject().put("content", labels.welcomeReport))
        card("spending-card", JSONObject().put("name", labels.thisMonthReport).put("mode", "single-month"))
        card("net-worth-card", JSONObject().put("name", labels.netWorthCard))
        card("cash-flow-card", JSONObject().put("name", labels.cashFlowCard).put("timeFrame", lastSixMonths))
        card(
            "summary-card",
            JSONObject().put("name", labels.foodPerMonthCard)
                .put("content", JSONObject().put("type", "avgPerMonth").toString())
                .put("conditions", JSONArray().put(JSONObject().put("field", "category").put("op", "oneOf").put("value", JSONArray().put(groceries).put(dining))))
                .put("conditionsOp", "and")
                .put("timeFrame", JSONObject().put("start", YearMonth.from(now).minusMonths(2).toString()).put("end", YearMonth.from(now).toString()).put("mode", "sliding-window")),
        )
        reports.forEach { card("custom-report", JSONObject().put("id", it)) }
        card("sankey-card", JSONObject().put("name", labels.whereTheMoneyWentCard).put("timeFrame", lastSixMonths))
        card("budget-analysis-card", JSONObject().put("name", labels.budgetAnalysisCard).put("timeFrame", lastSixMonths))
        card("age-of-money-card", JSONObject().put("name", labels.ageOfMoneyCard))
        card("calendar-card", JSONObject().put("name", labels.calendarCard))
    }

    private fun safeDay(base: LocalDate, day: Int): LocalDate = base.withDayOfMonth(minOf(day, base.lengthOfMonth()))
    private fun id(): String = UUID.randomUUID().toString().lowercase()
}
