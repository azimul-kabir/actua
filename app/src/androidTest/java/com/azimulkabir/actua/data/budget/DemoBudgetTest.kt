package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.model.ReportWidgetKind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class DemoBudgetTest {
    @Test fun demoBudgetIsLocalFeatureRichAndResettable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val activeBudget = ActiveBudgetStore(context)
        val previousBudgetId = activeBudget.budgetId
        runCatching { if (files.budgetDirectory(DemoBudgetManager.BUDGET_ID).exists()) files.deleteBudget(DemoBudgetManager.BUDGET_ID) }

        try {
            val metadata = DemoBudgetManager.recreate(files)
            assertEquals(DemoBudgetManager.BUDGET_ID, metadata.id)
            // The seeded name resolves through the demo labels, so it must match the resource.
            assertEquals(context.getString(R.string.demo_budget_name), metadata.budgetName)
            assertNull(metadata.cloudFileId)
            assertNull(metadata.groupId)
            assertTrue(JSONObject(files.metadataFile(metadata.id).readText()).getBoolean("demo"))
            ActualBudgetDatabase.validate(files.databaseFile(metadata.id))

            SQLiteDatabase.openDatabase(files.databaseFile(metadata.id).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                assertEquals(8, count(db, "SELECT COUNT(*) FROM accounts WHERE tombstone=0"))
                assertEquals(2, count(db, "SELECT COUNT(*) FROM accounts WHERE tombstone=0 AND offbudget=1 AND type='debt'"))
                assertEquals(20, count(db, "SELECT COUNT(*) FROM categories WHERE tombstone=0"))
                assertEquals(5, count(db, "SELECT COUNT(*) FROM category_groups WHERE tombstone=0"))
                assertEquals(13, count(db, "SELECT COUNT(*) FROM categories WHERE goal_def IS NOT NULL"))
                assertTrue(count(db, "SELECT COUNT(*) FROM transactions WHERE tombstone=0") >= 200)
                assertTrue(count(db, "SELECT COUNT(*) FROM transactions WHERE transferred_id IS NOT NULL") >= 2)
                // A year of history, but nothing in the future.
                val today = LocalDate.now()
                assertTrue(count(db, "SELECT MIN(date) FROM transactions WHERE tombstone=0 AND starting_balance_flag=0") <= ymd(YearMonth.now().minusMonths(11).atDay(1)))
                assertEquals(0, count(db, "SELECT COUNT(*) FROM transactions WHERE date > ${ymd(today)}"))
                assertEquals(12, count(db, "SELECT COUNT(DISTINCT month) FROM zero_budgets"))
                // Splits keep their parent total equal to their children.
                assertTrue(count(db, "SELECT COUNT(*) FROM transactions WHERE isParent=1") >= 11)
                assertEquals(
                    0,
                    count(db, "SELECT COUNT(*) FROM transactions p WHERE p.isParent=1 AND p.amount <> (SELECT SUM(c.amount) FROM transactions c WHERE c.parent_id=p.id)"),
                )
                assertTrue(count(db, "SELECT COUNT(*) FROM transactions WHERE reconciled=0") >= 2)
                assertEquals(8, count(db, "SELECT COUNT(*) FROM schedules WHERE tombstone=0"))
                assertTrue(count(db, "SELECT COUNT(*) FROM rules WHERE tombstone=0") >= 26)
                assertEquals(2, count(db, "SELECT COUNT(*) FROM preferences WHERE id LIKE 'actuali:credit_card:demo-account-%'"))
                assertEquals(2, count(db, "SELECT COUNT(*) FROM preferences WHERE id LIKE 'actuali:loan:demo-account-%'"))
                // Each loan payment is a transfer from checking carrying the loan's paired category.
                assertTrue(
                    count(
                        db,
                        "SELECT COUNT(*) FROM transactions t JOIN categories c ON c.id=t.category " +
                            "WHERE t.acct='demo-account-checking' AND t.transferred_id IS NOT NULL AND c.name IN ('Car Loan','Student Loan')",
                    ) >= 20,
                )
                assertEquals(4, count(db, "SELECT COUNT(*) FROM custom_reports WHERE tombstone=0"))
                assertTrue(count(db, "SELECT COUNT(*) FROM dashboard WHERE tombstone=0") >= 13)
                // Every dashboard report card points at a seeded saved report.
                assertEquals(
                    0,
                    count(db, "SELECT COUNT(*) FROM dashboard d WHERE d.type='custom-report' AND NOT EXISTS " +
                        "(SELECT 1 FROM custom_reports r WHERE d.meta LIKE '%' || r.id || '%')"),
                )
                assertEquals(1, count(db, "SELECT COUNT(*) FROM dashboard_pages WHERE tombstone=0"))
                assertEquals(
                    count(db, "SELECT COUNT(*) FROM payees WHERE tombstone=0"),
                    count(db, "SELECT COUNT(*) FROM payee_mapping"),
                )
            }

            // Mirror AppNavigation's eager reads. This specifically guards against
            // a demo DB that validates structurally but crashes on first render.
            activeBudget.budgetId = DemoBudgetManager.BUDGET_ID
            ActuaRepository(context).let { repository ->
                try {
                    val month = YearMonth.now().toString()
                    assertTrue(repository.isUsingActualBudget)
                    repository.budgetGroups(month)
                    repository.budgetOverview(month)
                    repository.accounts()
                    repository.transactions()
                    repository.categoryNames()
                    repository.payeeNames()
                    val widgets = repository.reports().dashboards.flatMap { it.widgets }
                    assertTrue(widgets.size >= 13)
                    assertTrue(widgets.none {
                        it.kind == ReportWidgetKind.UNSUPPORTED || it.kind == ReportWidgetKind.MISSING_REPORT
                    })
                    // The seeder writes Actuali's credit-card preference shape itself (#974).
                    assertEquals(
                        500000L,
                        repository.creditCards().single { it.accountId == "demo-account-credit" }.config.limitCents,
                    )
                    val cards = repository.creditCards().associateBy { it.accountId }
                    // The second card carries a balance on a fixed due day and stays inside its limit.
                    cards.getValue("demo-account-rewards").let { card ->
                        assertEquals(600000L, card.config.limitCents)
                        assertEquals(28, card.config.dueDay)
                        assertTrue(card.balanceCents < 0 && card.balanceCents > -600000L)
                    }
                    val loans = repository.loans().associateBy { it.accountId }
                    assertEquals(setOf("demo-account-car-loan", "demo-account-student-loan"), loans.keys)
                    loans.values.forEach { loan ->
                        assertTrue(loan.owedCents in 1 until loan.config.originalBalanceCents)
                        assertTrue(loan.totalPaidCents > 0)
                        assertTrue(loan.config.nextPaymentDate() != null)
                        assertTrue(loan.payoffMonth() != null)
                        assertTrue(repository.budgetGroups(month).flatMap { it.categories }.any { it.id == loan.config.categoryId })
                    }
                    // Everyday accounts never dip below zero, and the budget is not overspent.
                    val accounts = repository.accounts().associateBy { it.id }
                    listOf("demo-account-checking", "demo-account-savings", "demo-account-cash").forEach {
                        assertTrue("$it should hold money", accounts.getValue(it).balanceCents > 0)
                    }
                    repository.rules()
                    repository.rulesSupported()
                    repository.scheduleOwnedRuleIds()
                    repository.ruleEditorData()
                    assertEquals(8, repository.schedules().size)
                } finally {
                    repository.close()
                }
            }

            SQLiteDatabase.openDatabase(files.databaseFile(metadata.id).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL("DELETE FROM transactions")
                assertEquals(0, count(db, "SELECT COUNT(*) FROM transactions"))
            }

            DemoBudgetManager.recreate(files)
            SQLiteDatabase.openDatabase(files.databaseFile(metadata.id).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                assertTrue(count(db, "SELECT COUNT(*) FROM transactions WHERE tombstone=0") >= 200)
                val cloudIdentity = JSONObject(files.metadataFile(metadata.id).readText())
                assertFalse(cloudIdentity.has("cloudFileId"))
                assertFalse(cloudIdentity.has("groupId"))
            }
        } finally {
            activeBudget.budgetId = previousBudgetId
            runCatching { files.deleteBudget(DemoBudgetManager.BUDGET_ID) }
        }
    }

    private fun ymd(date: LocalDate) = date.year * 10000 + date.monthValue * 100 + date.dayOfMonth

    private fun count(db: SQLiteDatabase, sql: String): Int =
        db.rawQuery(sql, null).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }
}
