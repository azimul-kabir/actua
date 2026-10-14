package com.azimulkabir.actua.data.importing

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskerPluginTest {
    private val token = "0123456789abcdef"
    private val form = mapOf("amount" to "-%amount", "payee" to "%payee", "account" to "Amex Gold", "notes" to "  ")

    @Test
    fun `saved bundle holds the token and every filled field`() {
        val values = TaskerPlugin.bundleValues(form, token)
        assertEquals(
            mapOf(
                TaskerPlugin.key("token") to token, TaskerPlugin.key("amount") to "-%amount",
                TaskerPlugin.key("payee") to "%payee", TaskerPlugin.key("account") to "Amex Gold",
            ),
            values,
        )
    }

    @Test
    fun `every field but the token gets Tasker variable replacement`() {
        val keys = TaskerPlugin.replaceKeys(TaskerPlugin.bundleValues(form, token)).split(" ").toSet()
        assertEquals(setOf("amount", "payee", "account").map(TaskerPlugin::key).toSet(), keys)
        assertFalse(keys.any { it.contains(' ') })
    }

    @Test
    fun `a stored bundle reads back into the form without the token`() {
        val stored = TaskerPlugin.bundleValues(form, token) + (TaskerPlugin.KEY_VARIABLE_REPLACE to "ignored")
        assertEquals(mapOf("amount" to "-%amount", "payee" to "%payee", "account" to "Amex Gold"), TaskerPlugin.fieldsOf(stored))
    }

    @Test
    fun `blurb lists the filled fields and never the token`() {
        val blurb = TaskerPlugin.blurb(form)
        assertEquals("amount: -%amount\npayee: %payee\naccount: Amex Gold", blurb)
        assertFalse(token in blurb)
        assertEquals("Nothing set", TaskerPlugin.blurb(emptyMap()))
    }

    @Test
    fun `saving needs an amount or alert text`() {
        assertTrue(TaskerPlugin.canSave(mapOf("amount" to "%amount")))
        assertTrue(TaskerPlugin.canSave(mapOf("text" to "%antext")))
        assertFalse(TaskerPlugin.canSave(mapOf("payee" to "Coffee", "amount" to " ")))
    }

    @Test
    fun `fired values strip currency from the amount and drop unset variables`() {
        val extras = TaskerPlugin.extrasOf(mapOf(
            TaskerPlugin.key("token") to token, TaskerPlugin.key("amount") to "-$ 1,234.50",
            TaskerPlugin.key("payee") to "%payee", TaskerPlugin.key("notes") to "Lunch at %place",
        ))
        assertEquals(token, extras["token"])
        assertEquals("-1,234.50", extras["amount"])
        assertNull(extras["payee"])
        assertEquals("Lunch at %place", extras["notes"])
        assertEquals(AutomationIntent.EXTRAS.toSet(), extras.keys)
    }

    @Test
    fun `a fired bundle queues like the equivalent broadcast`() {
        val today = LocalDate.of(2026, 10, 12)
        val fired = TaskerPlugin.extrasOf(TaskerPlugin.bundleValues(
            mapOf("amount" to "-$25.00", "payee" to "Coffee", "account" to "Amex Gold"), token,
        ))
        val result = handleAutomationExtras(true, token, fired, today, tokenAdvice = TaskerPlugin.TOKEN_ADVICE)
        val candidate = result.candidates.single()
        assertNull(result.rejection)
        assertEquals(-2_500L, candidate.amountCents)
        assertEquals("Coffee", candidate.payee)
        assertEquals("Amex Gold", candidate.accountHint)
        assertEquals("Tasker", candidate.sourceLabel)
    }

    @Test
    fun `a stale plugin token asks to save the action again`() {
        val fired = TaskerPlugin.extrasOf(TaskerPlugin.bundleValues(mapOf("amount" to "1"), "old-token"))
        val result = handleAutomationExtras(true, token, fired, tokenAdvice = TaskerPlugin.TOKEN_ADVICE)
        assertTrue(result.candidates.isEmpty())
        assertEquals("Wrong token. Open the Actua action in Tasker and save it again", result.rejection)
    }

    @Test
    fun `an unset amount variable is reported as missing`() {
        val fired = TaskerPlugin.extrasOf(TaskerPlugin.bundleValues(mapOf("amount" to "%amount"), token))
        assertEquals("No amount or text was provided", handleAutomationExtras(true, token, fired).rejection)
    }

    @Test
    fun `a signed amount with an unset variable names the variable`() {
        val result = TaskerPlugin.fire(true, token, TaskerPlugin.bundleValues(mapOf("amount" to "-%amount"), token))
        assertTrue(result.candidates.isEmpty())
        assertEquals("Tasker variable %amount wasn't set when the action ran", result.rejection)
        assertNull(TaskerPlugin.extrasOf(TaskerPlugin.bundleValues(mapOf("amount" to "-%amount"), token))["amount"])
    }

    @Test
    fun `a replaced signed amount still queues`() {
        // Tasker has already replaced -%amount with -10.69 by the time the action fires.
        val result = TaskerPlugin.fire(true, token, TaskerPlugin.bundleValues(
            mapOf("amount" to "-10.69", "payee" to "%payee", "account" to "Fold Credit"), token,
        ), LocalDate.of(2026, 10, 13))
        val candidate = result.candidates.single()
        assertEquals(-1_069L, candidate.amountCents)
        assertEquals("Tasker", candidate.payee)
        assertEquals("Fold Credit", candidate.accountHint)
    }

    @Test
    fun `an unset amount variable never hides a token or switch problem`() {
        val values = TaskerPlugin.bundleValues(mapOf("amount" to "-%amount"), "old-token")
        assertEquals("Wrong token. Open the Actua action in Tasker and save it again",
            TaskerPlugin.fire(true, token, values).rejection)
        assertEquals("Received while Accept transactions from Tasker was off",
            TaskerPlugin.fire(false, token, values).rejection)
    }

    @Test
    fun `alert text is still parsed when the amount variable is unset`() {
        val result = TaskerPlugin.fire(true, token, TaskerPlugin.bundleValues(
            mapOf("amount" to "%amount", "text" to "Paid $4.00 to Bakery"), token,
        ))
        assertEquals(-400L, result.candidates.single().amountCents)
        assertNull(result.rejection)
    }

    @Test
    fun `amounts keep their number and sign whatever surrounds them`() {
        assertEquals("-10.69", TaskerPlugin.cleanAmount("-$10.69"))
        assertEquals("-10.69", TaskerPlugin.cleanAmount("--10.69"))
        assertEquals("-10.69", TaskerPlugin.cleanAmount("\u221210.69"))
        assertEquals("10.69", TaskerPlugin.cleanAmount("Rs.10.69"))
        assertEquals("50", TaskerPlugin.cleanAmount("Tk.50"))
        assertEquals("10.69", TaskerPlugin.cleanAmount("10.69 USD"))
        assertEquals("-1,234.50", TaskerPlugin.cleanAmount("-$ 1,234.50"))
        assertEquals("", TaskerPlugin.cleanAmount("USD"))
    }

    @Test
    fun `a doubled minus or currency prefix still queues a debit`() {
        val today = LocalDate.of(2026, 10, 14)
        val doubled = TaskerPlugin.fire(true, token, TaskerPlugin.bundleValues(mapOf("amount" to "--10.69"), token), today)
        assertEquals(-1_069L, doubled.candidates.single().amountCents)
        val rupees = TaskerPlugin.fire(true, token, TaskerPlugin.bundleValues(
            mapOf("amount" to "Rs.10.69", "type" to "debit"), token,
        ), today)
        assertEquals(-1_069L, rupees.candidates.single().amountCents)
    }

    @Test
    fun `an amount without digits says so`() {
        val result = TaskerPlugin.fire(true, token, TaskerPlugin.bundleValues(mapOf("amount" to "USD"), token))
        assertTrue(result.candidates.isEmpty())
        assertEquals("The amount had no number in it", result.rejection)
    }
}
