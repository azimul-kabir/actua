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
}
