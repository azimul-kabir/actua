package com.azimulkabir.actua.ui.transactions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddStepWalkTest {
    private fun resolve(
        step: AddStep?,
        categoryApplies: Boolean = true,
        categoryFilled: Boolean = false,
        accountDecided: Boolean = false,
    ) = resolveAddStep(step, categoryApplies, categoryFilled, accountDecided)

    @Test fun theWalkStartsAtThePayeeAndIsNotChangedThere() {
        assertEquals(AddStep.Payee, resolve(AddStep.Payee, categoryFilled = true, accountDecided = true))
    }

    @Test fun anIdleWalkStaysIdle() {
        assertNull(resolve(null, categoryFilled = true, accountDecided = true))
    }

    @Test fun anEmptyCategoryOpensTheCategoryPicker() {
        assertEquals(AddStep.Category, resolve(AddStep.Category))
    }

    @Test fun aFilledCategorySkipsToTheAccount() {
        assertEquals(AddStep.Account, resolve(AddStep.Category, categoryFilled = true))
    }

    @Test fun aFilledCategoryAndADecidedAccountEndTheWalk() {
        assertNull(resolve(AddStep.Category, categoryFilled = true, accountDecided = true))
    }

    @Test fun aCategoryThatDoesNotApplyIsStillSkipped() {
        assertEquals(AddStep.Account, resolve(AddStep.Category, categoryApplies = false))
        assertNull(resolve(AddStep.Category, categoryApplies = false, accountDecided = true))
    }

    @Test fun anAccountFromContextSkipsTheAccountPicker() {
        assertNull(resolve(AddStep.Account, accountDecided = true))
    }

    @Test fun withoutAccountContextTheAccountPickerOpens() {
        assertEquals(AddStep.Account, resolve(AddStep.Account))
        assertEquals(AddStep.Account, resolve(AddStep.Category, categoryFilled = true))
    }

    @Test fun anEmptyCategoryWithADecidedAccountStillOpensTheCategory() {
        assertEquals(AddStep.Category, resolve(AddStep.Category, accountDecided = true))
    }
}
