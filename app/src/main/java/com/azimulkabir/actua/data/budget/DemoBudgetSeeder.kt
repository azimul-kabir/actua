package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import com.azimulkabir.actua.model.BudgetTarget
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** Seeds a local-only budget with realistic data that exercises Actua's main workflows. */
internal object DemoBudgetSeeder {
    const val BUDGET_ID = "demo"
    const val BUDGET_NAME = "Actua Demo Budget"
    private const val HISTORY_MONTHS = 12
    private const val CARD_PAYMENT_DAY = 25

    fun seed(database: SQLiteDatabase, now: LocalDate = LocalDate.now()) {
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
            val credit = "demo-account-credit"
            val investment = "demo-account-investment"

            insertAccount(database, checking, "Everyday Checking", "checking", 0, 1.0)
            insertAccount(database, savings, "Emergency Savings", "savings", 0, 2.0)
            insertAccount(database, credit, "Everyday Credit Card", "credit", 0, 3.0)
            insertAccount(database, investment, "Investment Account", "investment", 1, 4.0)

            val incomeGroup = group(database, "Income", true, 1.0)
            val essentialsGroup = group(database, "Essentials", false, 2.0)
            val lifestyleGroup = group(database, "Lifestyle", false, 3.0)
            val goalsGroup = group(database, "Goals", false, 4.0)

            val salary = category(database, "Salary", incomeGroup, true, 1.0)
            val starting = category(database, "Starting Balances", incomeGroup, true, 2.0)
            val rent = category(database, "Rent", essentialsGroup, false, 1.0)
            val groceries = category(database, "Groceries", essentialsGroup, false, 2.0)
            val utilities = category(database, "Utilities", essentialsGroup, false, 3.0)
            val transport = category(database, "Transport", essentialsGroup, false, 4.0)
            val dining = category(database, "Dining Out", lifestyleGroup, false, 1.0)
            val entertainment = category(database, "Entertainment", lifestyleGroup, false, 2.0)
            val emergency = category(database, "Emergency Fund", goalsGroup, false, 1.0)
            val vacation = category(database, "Vacation", goalsGroup, false, 2.0)

            setTarget(database, groceries, BudgetTarget(BudgetTarget.Type.FIXED, 55000, startingDate = now.withDayOfMonth(1).toString()))
            setTarget(database, emergency, BudgetTarget(BudgetTarget.Type.FIXED, 35000, startingDate = now.withDayOfMonth(1).toString()))
            setTarget(database, vacation, BudgetTarget(BudgetTarget.Type.BY_DATE, 500000, YearMonth.from(now).plusMonths(6).toString()))
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
                    BudgetTarget.Type.FIXED, 3500, startingDate = now.withDayOfMonth(1).toString(),
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

            val paycheck = payee(database, "Employer")
            val landlord = payee(database, "Landlord")
            val supermarket = payee(database, "Fresh Market")
            val cornerGrocer = payee(database, "Corner Grocer")
            val bigBox = payee(database, "Big Box Store")
            val utilityCo = payee(database, "City Utilities")
            val internet = payee(database, "Fiber Net")
            val mobile = payee(database, "Mobile Co")
            val fuel = payee(database, "Fuel Station")
            val transit = payee(database, "City Transit")
            val restaurant = payee(database, "Neighborhood Cafe")
            val pizza = payee(database, "Slice House")
            val sushi = payee(database, "Sakura Sushi")
            val streaming = payee(database, "StreamBox")
            val cinema = payee(database, "Cineplex")
            val books = payee(database, "Page Turner Books")
            val airline = payee(database, "SkyWay Airlines")
            val indexFund = payee(database, "Index Fund")
            val cardPayment = payee(database, "Credit Card Payment")
            val transferPayees = listOf(checking, savings, credit, investment).associateWith { transferPayee(database, it) }
            fun transferPayeeOf(account: String) = requireNotNull(transferPayees[account])

            val firstMonth = YearMonth.from(now).minusMonths(HISTORY_MONTHS - 1L)
            val openingDate = firstMonth.minusMonths(1).atDay(1)
            opening(database, checking, starting, openingDate, 450000)
            opening(database, savings, starting, openingDate, 150000)
            opening(database, investment, starting, openingDate, 300000)

            // A year of everyday activity. Amounts vary deterministically by month so reports have
            // texture without making the demo differ between resets. Nothing is dated after today.
            repeat(HISTORY_MONTHS) { monthsAgo ->
                val base = YearMonth.from(now).minusMonths(monthsAgo.toLong()).atDay(1)
                var cardCharges = 0L
                fun vary(min: Long, max: Long, seed: Int): Long = min + Math.floorMod(monthsAgo * 7919L + seed * 104729L, max - min + 1)
                fun dateOf(day: Int): LocalDate? = safeDay(base, day).takeUnless { it.isAfter(now) }
                fun cleared(date: LocalDate) = monthsAgo > 0 || date.isBefore(now.minusDays(3))
                fun spend(day: Int, account: String, category: String, payee: String, cents: Long) {
                    val date = dateOf(day) ?: return
                    txn(database, account, category, payee, date, -cents, cleared(date), monthsAgo > 0)
                    if (account == credit && day < CARD_PAYMENT_DAY) cardCharges += cents
                }

                dateOf(1)?.let { txn(database, checking, salary, paycheck, it, 290000, true, monthsAgo > 0) }
                spend(3, checking, rent, landlord, 110000)
                spend(4, checking, groceries, supermarket, vary(9500, 13500, 1))
                spend(7, credit, dining, restaurant, vary(1200, 2000, 2))
                dateOf(8)?.let { date ->
                    val household = vary(4000, 6500, 3); val treats = vary(1500, 3200, 4)
                    split(database, credit, bigBox, date, listOf(groceries to -household, entertainment to -treats), cleared(date), monthsAgo > 0)
                    cardCharges += household + treats
                }
                spend(9, checking, utilities, utilityCo, vary(7000, 9500, 5))
                spend(10, credit, transport, fuel, vary(4500, 6000, 6))
                spend(11, credit, groceries, cornerGrocer, vary(3000, 5500, 7))
                spend(12, credit, entertainment, streaming, 1299)
                spend(13, checking, groceries, supermarket, vary(10000, 14000, 8))
                spend(14, checking, utilities, internet, 6500)
                spend(15, credit, transport, transit, vary(2500, 3500, 9))
                spend(16, credit, dining, pizza, vary(2400, 3800, 10))
                spend(18, checking, utilities, mobile, 3500)
                spend(19, credit, entertainment, cinema, vary(1800, 3200, 11))
                spend(20, checking, groceries, supermarket, vary(9000, 13000, 12))
                spend(21, credit, dining, restaurant, vary(900, 1600, 13))
                spend(22, credit, transport, fuel, vary(4500, 6000, 14))
                spend(24, credit, dining, sushi, vary(4000, 6000, 15))
                if (monthsAgo == 5) spend(17, credit, vacation, airline, 64800)
                dateOf(CARD_PAYMENT_DAY)?.let { date ->
                    transfer(database, checking, credit, transferPayeeOf(credit), transferPayeeOf(checking), date, cardCharges, monthsAgo > 0)
                }
                spend(26, credit, groceries, cornerGrocer, vary(2500, 4500, 16))
                spend(27, credit, entertainment, books, vary(1500, 3000, 17))
                dateOf(28)?.let { date ->
                    transfer(database, checking, savings, transferPayeeOf(savings), transferPayeeOf(checking), date, 35000, monthsAgo > 0)
                    // Off-budget growth keeps Net Worth trending like a real portfolio.
                    txn(database, investment, null, indexFund, date, vary(9000, 24000, 18), true, monthsAgo > 0)
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
                budget(database, ym, dining, 14000)
                budget(database, ym, entertainment, 11000)
                budget(database, ym, emergency, if (oldest) 335000 else 35000)
                budget(database, ym, vacation, if (oldest) 130000 else 30000)
            }

            note(database, groceries, "Monthly household groceries. Try changing the target or moving money into this category.")
            note(database, "account-$checking", "Demo checking account. It includes cleared, uncleared, and reconciled transactions.")

            // Credit-card configuration uses the same preference key read by Actua.
            database.execSQL(
                "INSERT OR REPLACE INTO preferences(id,value) VALUES(?,?)",
                arrayOf<Any?>("actuali:credit_card:$credit", JSONObject().put("statementDay", 20).put("dueOffsetDays", 15).put("limit", 500000).toString()),
            )

            seedRules(
                database,
                listOf(
                    supermarket to groceries, cornerGrocer to groceries, streaming to entertainment,
                    cinema to entertainment, fuel to transport, transit to transport, internet to utilities,
                ),
            )
            seedSchedules(database, now, checking, credit, paycheck, landlord, streaming, cardPayment)
            val reports = seedCustomReports(database)
            seedDashboard(database, now, reports, groceries, dining)

            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
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
    ) {
        val sourceId = id()
        val targetId = id()
        val ymd = date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE).toInt()
        val sortOrder = System.nanoTime().toDouble()
        db.execSQL(
            "INSERT INTO transactions(id,acct,category,amount,description,date,transferred_id,tombstone,cleared,reconciled,sort_order) VALUES(?,?,?,?,?,?,?,0,1,?,?)",
            arrayOf<Any?>(sourceId, fromAccount, null, -amount, fromPayee, ymd, targetId, if (reconciled) 1 else 0, sortOrder),
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

    private fun seedSchedules(db: SQLiteDatabase, now: LocalDate, checking: String, credit: String, paycheck: String, landlord: String, streaming: String, cardPayment: String) {
        schedule(db, "Monthly salary", checking, paycheck, 290000, now.plusMonths(1).withDayOfMonth(1), "monthly", true)
        schedule(db, "Rent", checking, landlord, -110000, now.plusMonths(1).withDayOfMonth(3), "monthly", false)
        schedule(db, "Streaming subscription", credit, streaming, -1299, now.plusMonths(1).withDayOfMonth(12), "monthly", true)
        schedule(db, "Credit card payment", checking, cardPayment, -25000, now.plusMonths(1).withDayOfMonth(25), "monthly", false)
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

    private fun seedCustomReports(db: SQLiteDatabase): List<String> {
        fun report(name: String, graph: String, mode: String, groupBy: String, range: String): String = id().also {
            db.execSQL(
                "INSERT INTO custom_reports(id,name,date_static,date_range,mode,group_by,balance_type,graph_type,interval,include_current,conditions,conditions_op,tombstone) " +
                    "VALUES(?,?,0,?,?,?,'Payment',?,'Monthly',1,'[]','and',0)",
                arrayOf<Any?>(it, name, range, mode, groupBy, graph),
            )
        }
        return listOf(
            report("Spending by category", "DonutGraph", "total", "Category", "Last 3 months"),
            report("Monthly spending", "StackedBarGraph", "time", "Group", "Last 6 months"),
            report("Top payees", "BarGraph", "total", "Payee", "Last 3 months"),
            report("Spending trend", "LineGraph", "time", "Group", "Last 12 months"),
        )
    }

    private fun seedDashboard(db: SQLiteDatabase, now: LocalDate, reports: List<String>, groceries: String, dining: String) {
        val page = "demo-dashboard-main"
        db.execSQL("INSERT OR REPLACE INTO dashboard_pages(id,name,tombstone) VALUES(?,?,0)", arrayOf(page, "Overview"))
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
        card("markdown-card", JSONObject().put("content", "**Welcome to Actua Demo** 👋\n\nThis budget is stored only on this device. Try targets, rules, schedules, reconciliation, credit cards and reports."))
        card("spending-card", JSONObject().put("name", "This Month").put("mode", "single-month"))
        card("net-worth-card", JSONObject().put("name", "Net Worth"))
        card("cash-flow-card", JSONObject().put("name", "Cash Flow").put("timeFrame", lastSixMonths))
        card(
            "summary-card",
            JSONObject().put("name", "Food per month")
                .put("content", JSONObject().put("type", "avgPerMonth").toString())
                .put("conditions", JSONArray().put(JSONObject().put("field", "category").put("op", "oneOf").put("value", JSONArray().put(groceries).put(dining))))
                .put("conditionsOp", "and")
                .put("timeFrame", JSONObject().put("start", YearMonth.from(now).minusMonths(2).toString()).put("end", YearMonth.from(now).toString()).put("mode", "sliding-window")),
        )
        reports.forEach { card("custom-report", JSONObject().put("id", it)) }
        card("sankey-card", JSONObject().put("name", "Where the money went").put("timeFrame", lastSixMonths))
        card("budget-analysis-card", JSONObject().put("name", "Budget Analysis").put("timeFrame", lastSixMonths))
        card("age-of-money-card", JSONObject().put("name", "Age of Money"))
        card("calendar-card", JSONObject().put("name", "Calendar"))
    }

    private fun safeDay(base: LocalDate, day: Int): LocalDate = base.withDayOfMonth(minOf(day, base.lengthOfMonth()))
    private fun id(): String = UUID.randomUUID().toString().lowercase()
}
