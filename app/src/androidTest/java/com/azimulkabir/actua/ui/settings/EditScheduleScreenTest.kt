package com.azimulkabir.actua.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.data.schedules.ActualScheduleSummary
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.ScheduleAmountOp
import com.azimulkabir.actua.data.schedules.ScheduleDateCondition
import com.azimulkabir.actua.data.schedules.ScheduleFormFields
import com.azimulkabir.actua.data.schedules.ScheduleListItem
import com.azimulkabir.actua.data.schedules.ScheduleStatus
import com.azimulkabir.actua.data.schedules.ScheduledAmount
import com.azimulkabir.actua.model.Account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditScheduleScreenTest {
    @get:Rule val compose = createComposeRule()

    private val day = DayDate(2026, 10, 15)
    private val accounts = listOf(Account(name = "Checking", balance = 0, type = "checking", id = "checking"))
    private val rent = ScheduleListItem(
        ActualScheduleSummary(
            "rent", "Rent", null, day, null, null, "checking", null,
            ScheduledAmount.Fixed(-1_250), ScheduleAmountOp.EXACT, "is", ScheduleDateCondition.Fixed(day),
            false, false, null, null, false, null, null, null,
        ),
        ScheduleStatus.UPCOMING,
        "Checking",
        "Landlord",
    )

    @Test fun switchingToIncomeAndAutoAddSavesTheSameSchedule() {
        var saved: ScheduleFormFields? = null
        var savedPayee: String? = null
        setContent(onSave = { fields, payee -> saved = fields; savedPayee = payee })

        compose.onNodeWithText("Expense").assertExists()
        compose.onNodeWithText("Switch to income").performClick()
        compose.onNodeWithText("Income").assertExists()
        compose.onNodeWithText("Automatically add transaction").performScrollTo().performClick()
        compose.onNodeWithText("Save").performClick()

        val fields = saved!!
        assertEquals("Landlord", savedPayee)
        assertEquals("Rent", fields.name)
        assertEquals("checking", fields.accountId)
        assertEquals(ScheduledAmount.Fixed(1_250), fields.amount)
        assertEquals(ScheduleAmountOp.EXACT, fields.amountOp)
        assertEquals(ScheduleDateCondition.Fixed(day), fields.date)
        assertTrue(fields.postsTransaction)
    }

    @Test fun betweenShowsBothBoundsAndSavesARange() {
        var saved: ScheduleFormFields? = null
        setContent(onSave = { fields, _ -> saved = fields })

        compose.onNodeWithText("Between").performClick()
        compose.onNodeWithText("From").assertExists()
        compose.onNodeWithText("To").assertExists()
        compose.onNodeWithText("Save").performClick()

        assertEquals(ScheduleAmountOp.BETWEEN, saved?.amountOp)
        assertEquals(ScheduledAmount.Range(-1_250, -1_250), saved?.amount)
    }

    @Test fun deleteIsReachableFromTheHeader() {
        var deleted = false
        setContent(onDelete = { deleted = true })

        compose.onNodeWithContentDescription("Delete schedule").performClick()
        compose.onNodeWithText("Delete schedule").performClick()

        assertTrue(deleted)
    }

    private fun setContent(
        onSave: (ScheduleFormFields, String) -> Unit = { _, _ -> },
        onDelete: () -> Unit = {},
    ) {
        compose.setContent {
            MaterialTheme {
                EditScheduleScreen(
                    item = rent,
                    accounts = accounts,
                    payeeOptions = listOf("Landlord"),
                    hideDecimalPlaces = false,
                    conventionalAmountEntry = false,
                    onBack = {},
                    onSave = onSave,
                    onDelete = onDelete,
                )
            }
        }
    }
}
