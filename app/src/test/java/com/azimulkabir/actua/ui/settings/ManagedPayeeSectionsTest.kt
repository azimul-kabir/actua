package com.azimulkabir.actua.ui.settings

import com.azimulkabir.actua.data.budget.model.ActualManagedPayee
import org.junit.Assert.assertEquals
import org.junit.Test

class ManagedPayeeSectionsTest {
    private fun payee(id: String, name: String, transfer: String? = null) =
        ActualManagedPayee(id, name, transfer, favorite = false, learnCategories = true)

    private val payees = listOf(
        payee("cafe", "Corner Café"),
        payee("grocer", "Grocer"),
        payee("t-savings", "Savings", transfer = "savings"),
    )

    @Test fun splitsOrdinaryAndTransferPayeesKeepingOrder() {
        val sections = managedPayeeSections(payees, "")
        assertEquals(listOf("cafe", "grocer"), sections.ordinary.map { it.id })
        assertEquals(listOf("t-savings"), sections.transfers.map { it.id })
    }

    @Test fun filtersBothSectionsByTrimmedCaseInsensitiveName() {
        assertEquals(listOf("cafe"), managedPayeeSections(payees, "  CAFÉ ").ordinary.map { it.id })
        val savings = managedPayeeSections(payees, "sav")
        assertEquals(emptyList<String>(), savings.ordinary.map { it.id })
        assertEquals(listOf("t-savings"), savings.transfers.map { it.id })
    }
}
