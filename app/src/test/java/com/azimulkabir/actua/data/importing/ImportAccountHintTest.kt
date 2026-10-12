package com.azimulkabir.actua.data.importing

import com.azimulkabir.actua.model.Account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportAccountHintTest {
    private fun account(name: String, id: String = name) = Account(name = name, balance = 0, type = "checking", id = id)

    private val checking = account("Checking")
    private val amexGold = account("Amex Gold")
    private val visa = account("Visa 1234")
    private val accounts = listOf(checking, amexGold, visa)

    @Test
    fun `exact name selects the account ignoring case and spaces`() {
        assertEquals(amexGold, accountForImportHint("Amex Gold", accounts))
        assertEquals(amexGold, accountForImportHint("  amex gold ", accounts))
    }

    @Test
    fun `a partial name does not match`() {
        assertNull(accountForImportHint("Amex", accounts))
    }

    @Test
    fun `digits still match the account whose name contains them`() {
        assertEquals(visa, accountForImportHint("1234", accounts))
    }

    @Test
    fun `a name shared by two accounts falls back to digits and otherwise selects nothing`() {
        val twins = listOf(account("Card", id = "a"), account("card", id = "b"))
        assertNull(accountForImportHint("Card", twins))
    }

    @Test
    fun `an account named with digits wins over a digit match elsewhere`() {
        val named = account("1234")
        assertEquals(named, accountForImportHint("1234", listOf(visa, named)))
    }

    @Test
    fun `digits shared by two accounts select nothing`() {
        assertNull(accountForImportHint("1234", listOf(visa, account("Savings 1234"))))
    }

    @Test
    fun `an unknown or blank hint selects nothing`() {
        assertNull(accountForImportHint("Brokerage", accounts))
        assertNull(accountForImportHint("  ", accounts))
    }

    @Test
    fun `the plugin offers open account names that select their own account`() {
        val closed = Account(name = "Old Card", balance = 0, type = "credit", id = "old", closed = true)
        val twins = listOf(account("Joint", id = "j1"), account("joint ", id = "j2"))
        assertEquals(
            listOf("Checking", "Amex Gold", "Visa 1234"),
            importHintAccountNames(accounts + closed + twins),
        )
    }

    @Test
    fun `memory keys use the hint, else the source`() {
        assertEquals("hint:9921", importAccountMemoryKey(" 9921 ", "com.bank.app"))
        assertEquals("hint:amex", importAccountMemoryKey("AMEX", "Tasker"))
        assertEquals("source:com.bank.app", importAccountMemoryKey(null, "com.bank.app"))
        assertEquals("source:Tasker: GPay", importAccountMemoryKey("  ", "Tasker: GPay"))
    }

    @Test
    fun `prefill prefers the hint's account, then the remembered hint, then the remembered source`() {
        val memory = mapOf("hint:9921" to visa.id, "source:com.bank.app" to checking.id, "hint:1234" to checking.id)
        fun prefill(hint: String?, source: String = "com.bank.app") =
            prefilledImportAccount(hint, source, accounts) { memory[it] }

        // "1234" names Visa 1234 directly, so the remembered Checking is ignored.
        assertEquals(visa, prefill("1234"))
        assertEquals(visa, prefill("9921"))
        assertEquals(checking, prefill(null))
        assertEquals(checking, prefill("5555"))
        assertNull(prefill(null, source = "other.app"))
    }

    @Test
    fun `remembered accounts that are closed or gone are not prefilled`() {
        val closedVisa = visa.copy(closed = true)
        val memory = mapOf("hint:9921" to visa.id, "source:com.bank.app" to "deleted-account")
        assertNull(prefilledImportAccount("9921", "com.bank.app", listOf(checking, closedVisa)) { memory[it] })
    }
}
