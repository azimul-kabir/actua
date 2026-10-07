package com.azimulkabir.actua.data.budget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Actual's `addMovementNotes` text (#908). */
class BudgetMovementNoteTest {
    @Test fun amountsUseTheServersEnUsFormatWithTwoFractionDigits() {
        assertEquals("12.34", BudgetMovementNote.amountText(1_234, null))
        assertEquals("1,234,567.89", BudgetMovementNote.amountText(123_456_789, "EUR"))
        assertEquals("0.05", BudgetMovementNote.amountText(5, ""))
        // Zero-decimal currencies divide by 1 but still print two fraction digits, as upstream does.
        assertEquals("1,234.00", BudgetMovementNote.amountText(1_234, "JPY"))
        assertEquals("1,234.00", BudgetMovementNote.amountText(1_234, "krw"))
    }

    @Test fun lineMatchesUpstream() {
        assertEquals(
            "Reassigned 50.00 from Dining → To Budget on October 07",
            BudgetMovementNote.line(5_000, "Dining", BudgetMovementNote.TO_BUDGET, "USD", LocalDate.of(2026, 10, 7)),
        )
        assertEquals(
            "Reassigned 3.10 from Rent → Overbudgeted on January 31",
            BudgetMovementNote.line(310, "Rent", BudgetMovementNote.OVERBUDGETED, null, LocalDate.of(2027, 1, 31)),
        )
    }

    @Test fun appendsOnANewLine() {
        assertEquals("- first", BudgetMovementNote.append(null, "first"))
        assertEquals("- first", BudgetMovementNote.append("", "first"))
        assertEquals("Plan\n- second", BudgetMovementNote.append("Plan", "second"))
        assertEquals("budget-2026-10", BudgetMovementNote.noteId("2026-10"))
    }
}
