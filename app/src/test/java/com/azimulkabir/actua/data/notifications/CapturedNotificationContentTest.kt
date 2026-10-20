package com.azimulkabir.actua.data.notifications

import com.azimulkabir.actua.model.Account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CapturedNotificationContentTest {
    private val amex = Account(name = "Amex Gold", balance = 0, type = "credit", id = "amex")

    @Test
    fun `a row with a known account offers Import into it`() {
        val content = capturedNotificationContent("Coffee", "−5.00", amex, "Captured transaction", "Choose an account in Actua")
        assertEquals("Coffee", content.title)
        assertEquals("−5.00 · Amex Gold", content.text)
        assertEquals("amex", content.importAccountId)
    }

    @Test
    fun `a row without an account only asks to choose one`() {
        val content = capturedNotificationContent("Coffee", "−5.00", null, "Captured transaction", "Choose an account in Actua")
        assertEquals("−5.00 · Choose an account in Actua", content.text)
        assertNull(content.importAccountId)
    }

    @Test
    fun `a blank payee still gets a title`() {
        assertEquals(
            "Captured transaction",
            capturedNotificationContent("  ", "1.00", null, "Captured transaction", "Choose an account in Actua").title,
        )
    }
}
