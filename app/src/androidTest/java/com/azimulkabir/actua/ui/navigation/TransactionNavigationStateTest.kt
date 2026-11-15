package com.azimulkabir.actua.ui.navigation

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.azimulkabir.actua.model.Type
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TransactionNavigationStateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun saved_register_and_editor_return_state_survive_recreation_but_transient_state_does_not() {
        lateinit var state: TransactionNavigationState
        val restoration = StateRestorationTester(compose)
        restoration.setContent { state = rememberTransactionNavigationState() }
        compose.runOnIdle {
            state.transactionAccount.value = "Synthetic checking"
            state.transactionCategory.value = "Food"
            state.transactionMonth.value = "2026-11"
            state.transactionSearch.value = "test"
            state.lastOpenAccountId.value = "synthetic-id"
            state.editorReturnsToCategory.value = true
            state.editorReturnCategory.value = "category-id"
            state.editorReturnsToReportsDetail.value = DetailDestination.Reports
            state.addOrigin.value = MainDestination.Home
            state.editorReturnsToStatementDetail.value = true
            state.newTransactionType.value = Type.INCOME
            state.keptReceiptPhoto.value = File("synthetic-receipt.jpg")
            state.transactionsRefreshing.value = true
            state.reconcileOpen.value = true
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertEquals("Synthetic checking", state.transactionAccount.value)
            assertEquals("Food", state.transactionCategory.value)
            assertEquals("2026-11", state.transactionMonth.value)
            assertEquals("test", state.transactionSearch.value)
            assertEquals("synthetic-id", state.lastOpenAccountId.value)
            assertTrue(state.editorReturnsToCategory.value)
            assertEquals("category-id", state.editorReturnCategory.value)
            assertEquals(DetailDestination.Reports, state.editorReturnsToReportsDetail.value)
            assertEquals(MainDestination.Home, state.addOrigin.value)
            assertTrue(state.editorReturnsToStatementDetail.value)
            assertEquals(Type.EXPENSE, state.newTransactionType.value)
            assertNull(state.keptReceiptPhoto.value)
            assertFalse(state.transactionsRefreshing.value)
            assertFalse(state.reconcileOpen.value)
        }
    }
}
