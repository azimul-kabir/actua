package com.azimulkabir.actua.data.budget

import org.junit.Assert.assertEquals
import org.junit.Test

class ActualTransactionFormPlanTest {
    @Test
    fun centsRoundsHalfAwayFromZero() {
        assertEquals(820L, ActualTransactionFormService.cents("8.20"))
        assertEquals(1L, ActualTransactionFormService.cents("0.005"))
        assertEquals(-1L, ActualTransactionFormService.cents("-0.005"))
        assertEquals(null, ActualTransactionFormService.cents("hello"))
    }

}
