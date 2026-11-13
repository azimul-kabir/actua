package com.azimulkabir.actua.ui.home

import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.ScheduleAmountOp
import com.azimulkabir.actua.data.schedules.ScheduleDateCondition
import com.azimulkabir.actua.data.schedules.ScheduleListItem
import com.azimulkabir.actua.data.schedules.ScheduleStatus
import com.azimulkabir.actua.data.schedules.ScheduledAmount
import com.azimulkabir.actua.data.schedules.ActualScheduleSummary
import com.azimulkabir.actua.model.BudgetCategory
import com.azimulkabir.actua.model.BudgetGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GreetingProjectionTest {
    private val today = LocalDate.of(2026, 11, 13)

    private fun figures(
        uncategorized: Int = 0,
        added: Int = 0,
        bills: Int = 0,
        groups: List<BudgetGroup> = emptyList(),
    ) = GreetingProjection.figures(uncategorized, added, bills, groups)

    private fun schedule(id: String, due: DayDate?, status: ScheduleStatus) = ScheduleListItem(
        ActualScheduleSummary(
            id, null, null, due, null, null, "checking", null, ScheduledAmount.Fixed(-1000), ScheduleAmountOp.EXACT,
            "is", due?.let { ScheduleDateCondition.Fixed(it) }, false, false, null, null, false, null, null, null,
        ),
        status, null, null,
    )

    @Test fun figures_with_nothing_to_say_are_left_out() {
        assertTrue(figures().isEmpty())
    }

    @Test fun figures_come_in_the_agreed_order() {
        val groups = listOf(BudgetGroup("Bills", listOf(BudgetCategory("Rent", 100, 0, id = "rent"))))
        assertEquals(
            listOf(
                GreetingFigure.CategoriesOnBudget(1, 1),
                GreetingFigure.Uncategorized(4),
                GreetingFigure.BillsDueThisWeek(2),
                GreetingFigure.AddedThisWeek(9),
            ),
            figures(uncategorized = 4, added = 9, bills = 2, groups = groups),
        )
    }

    @Test fun categories_on_budget_counts_visible_expense_categories_with_a_budgeted_amount() {
        val groups = listOf(
            BudgetGroup("Bills", listOf(
                BudgetCategory("Rent", 100, 90, availableCents = 1000, id = "rent"),
                BudgetCategory("Power", 50, 80, availableCents = -3000, id = "power"),
                BudgetCategory("Unbudgeted", 0, 0, id = "none"),
                BudgetCategory("Hidden", 10, 0, hidden = true, id = "hidden"),
            )),
            BudgetGroup("Hidden group", listOf(BudgetCategory("Secret", 10, 0, id = "s")), hidden = true),
            BudgetGroup("Income", listOf(BudgetCategory("Pay", 500, 0, isIncome = true, id = "pay")), isIncome = true),
        )
        assertEquals(listOf<GreetingFigure>(GreetingFigure.CategoriesOnBudget(1, 2)), figures(groups = groups))
    }

    @Test fun categories_on_budget_is_omitted_when_nothing_is_budgeted() {
        val groups = listOf(BudgetGroup("Bills", listOf(BudgetCategory("Rent", 0, 0, id = "rent"))))
        assertTrue(figures(groups = groups).isEmpty())
    }

    @Test fun bills_due_counts_unpaid_schedules_from_today_through_six_days_ahead() {
        val day = { offset: Int -> DayDate(2026, 11, 13).addingDays(offset) }
        val items = listOf(
            schedule("today", day(0), ScheduleStatus.DUE),
            schedule("last-day", day(6), ScheduleStatus.UPCOMING),
            schedule("too-far", day(7), ScheduleStatus.UPCOMING),
            schedule("overdue", day(-1), ScheduleStatus.MISSED),
            schedule("paid", day(2), ScheduleStatus.PAID),
            schedule("done", day(3), ScheduleStatus.COMPLETED),
            schedule("no-date", null, ScheduleStatus.SCHEDULED),
        )
        assertEquals(2, GreetingProjection.billsDueThisWeek(items, today))
        assertEquals(0, GreetingProjection.billsDueThisWeek(emptyList(), today))
    }
}
