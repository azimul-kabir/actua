package com.azimulkabir.actua.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.budget.DemoBudgetManager
import com.azimulkabir.actua.data.navigation.TabBarLayout
import com.azimulkabir.actua.data.navigation.TabItem
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.data.preferences.TabBarPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Regression coverage for #1647: going back, switching tabs and re-tapping a tab keep the user's place. Uses the demo
 * budget so AppNavigation has real accounts, cards and statements to open (see SettingsBackNavigationTest).
 */
class NavigationKeepsPlaceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = BudgetFileManager(context)
    private val activeBudget = ActiveBudgetStore(context)
    private val tabBarPreferences = TabBarPreferences(context)
    private val displayPreferences = DisplayPreferences(context)
    private var previousBudgetId: String? = null
    private var previousHideTabBarOnScroll = true
    private val strings get() = composeRule.activity

    @Before fun seedDemoBudget() {
        previousBudgetId = activeBudget.budgetId
        previousHideTabBarOnScroll = displayPreferences.hideTabBarOnScroll
        runCatching { if (files.budgetDirectory(DemoBudgetManager.BUDGET_ID).exists()) files.deleteBudget(DemoBudgetManager.BUDGET_ID) }
        DemoBudgetManager.recreate(files)
        activeBudget.budgetId = DemoBudgetManager.BUDGET_ID
        tabBarPreferences.restoreDefaults()
    }

    @After fun restorePreviousBudget() {
        tabBarPreferences.restoreDefaults()
        displayPreferences.hideTabBarOnScroll = previousHideTabBarOnScroll
        activeBudget.budgetId = previousBudgetId
        runCatching { files.deleteBudget(DemoBudgetManager.BUDGET_ID) }
    }

    // Back from a full-screen destination opened from a Settings sub-page returns to that sub-page.

    @Test fun backFromCreditCardsOpenedFromTransactionsSettingsReturnsThere() {
        start()
        openSettingsPage(R.string.settings_page_transactions_accounts)
        settingsRow(R.string.settings_credit_cards).performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.fs_payment_reminders)).assertExists()

        pressBack()

        assertOnPage(R.string.settings_keep_last_transaction_date)
    }

    @Test fun backFromLoansOpenedFromTransactionsSettingsReturnsThere() {
        start()
        openSettingsPage(R.string.settings_page_transactions_accounts)
        settingsRow(R.string.settings_loans).performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.settings_keep_last_transaction_date)).assertDoesNotExist()

        pressBack()

        assertOnPage(R.string.settings_keep_last_transaction_date)
    }

    @Test fun backFromPayeeLocationsOpenedFromPrivacySettingsReturnsThere() {
        start()
        openSettingsPage(R.string.settings_page_privacy)
        settingsRow(R.string.settings_payee_locations).performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.settings_hide_in_recents)).assertDoesNotExist()

        pressBack()

        assertOnPage(R.string.settings_hide_in_recents)
    }

    @Test fun backFromCreditCardsOpenedFromTheManageRootStillReturnsToTheRoot() {
        start()
        settingsRow(R.string.settings_credit_cards).performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.fs_payment_reminders)).assertExists()

        pressBack()

        composeRule.onNodeWithText(text(R.string.settings_bills_calendar)).assertExists()
    }

    // The back gesture goes where the screen's own back arrow goes.

    @Test fun backFromCreditCardsOpenedFromBillsCalendarReturnsToTheCalendar() {
        start()
        settingsRow(R.string.settings_bills_calendar).performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.fs_card_bills)).performClick()
        composeRule.onNodeWithContentDescription(text(R.string.fs_configure_cards)).performClick()
        composeRule.onNodeWithText(text(R.string.fs_payment_reminders)).assertExists()

        pressBack()

        composeRule.onNodeWithText(text(R.string.fs_card_bills)).assertExists()
        composeRule.onNodeWithText(text(R.string.fs_payment_reminders)).assertDoesNotExist()
    }

    @Test fun backFromAStatementTransactionReturnsToTheStatement() {
        start()
        settingsRow(R.string.settings_credit_cards).performScrollTo().performClick()
        val statements = text(R.string.fs_view_recent_statements)
        composeRule.waitUntil(TIMEOUT) { composeRule.onAllNodesWithContentDescription(statements).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onAllNodesWithContentDescription(statements).onFirst().performClick()
        // A statement row shows its period ("Oct 21 – Nov 20"); a transaction row shows a negative amount.
        clickFirst(hasClickAction() and hasText(" – ", substring = true))
        composeRule.onNodeWithText(text(R.string.fs_statement_details)).assertExists()
        clickFirst(hasClickAction() and hasText("−", substring = true))
        composeRule.onNodeWithContentDescription("Cancel", useUnmergedTree = true).assertExists()

        pressBack()

        composeRule.onNodeWithText(text(R.string.fs_statement_details)).assertExists()
    }

    // Each Settings page has its own scroll position.

    @Test fun aSettingsChildPageOpensAtItsTopAndBackRestoresTheParentsScroll() {
        // A large font makes the Budget settings page taller than any screen, so it really scrolls.
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                MaterialTheme { AppNavigation() }
            }
        }
        tab("Manage").performClick()
        openSettingsPage(R.string.settings_budget)
        val holidaysRow = settingsRow(R.string.settings_holidays_row_title)
        holidaysRow.performScrollTo()
        val back = text(R.string.fs_back)
        composeRule.onNodeWithContentDescription(back).assertIsNotDisplayed()
        val rowTop = holidaysRow.fetchSemanticsNode().boundsInRoot.top

        holidaysRow.performClick()
        composeRule.onNodeWithContentDescription(back).assertIsDisplayed()

        pressBack()

        composeRule.onNodeWithContentDescription(back).assertIsNotDisplayed()
        assertEquals(rowTop, settingsRow(R.string.settings_holidays_row_title).fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    // Switching tabs keeps each tab's place, also after the tab was re-tapped earlier.

    @Test fun switchingTabsKeepsTheSettingsPageAfterAnEarlierRetap() {
        start()
        tab("Manage").performClick() // re-tap while on the Manage root
        openSettingsPage(R.string.settings_page_display)

        tab("Budget").performClick()
        tab("Manage").performClick()

        assertOnPage(R.string.settings_number_format)
    }

    @Test fun switchingTabsKeepsHomesScrollAfterAnEarlierRetap() {
        tabBarPreferences.save(
            TabBarLayout(
                order = listOf(TabItem.HOME, TabItem.BUDGET, TabItem.ACCOUNTS, TabItem.TRANSACTIONS, TabItem.ADD, TabItem.REPORTS, TabItem.MANAGE),
                hidden = setOf(TabItem.TRANSACTIONS, TabItem.ADD),
            ),
        )
        // Scrolling down would otherwise slide the tab bar away before the tab switch.
        displayPreferences.hideTabBarOnScroll = false
        composeRule.setContent { MaterialTheme { AppNavigation() } }
        tab("Home").performClick()
        tab("Home").performClick() // re-tap while on Home
        val recent = text(R.string.home_recent_activity)
        composeRule.onNodeWithTag("homeList").performScrollToNode(hasText(recent))
        val top = composeRule.onNodeWithText(recent).fetchSemanticsNode().boundsInRoot.top

        tab("Budget").performClick()
        tab("Home").performClick()

        assertEquals(top, composeRule.onNodeWithText(recent).fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    // Re-tapping the current tab goes to its root, once per tap, also after the activity is recreated.

    @Test fun retappingManageGoesToTheManageRoot() {
        start()
        openSettingsPage(R.string.settings_page_display)

        tab("Manage").performClick()

        composeRule.onNodeWithText(text(R.string.settings_bills_calendar)).assertExists()
        composeRule.onNodeWithText(text(R.string.settings_number_format)).assertDoesNotExist()
    }

    @Test fun retappingStillWorksAfterRecreation() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { MaterialTheme { AppNavigation() } }
        tab("Manage").performClick()
        tab("Manage").performClick()
        tab("Manage").performClick() // two re-taps on the Manage root
        openSettingsPage(R.string.settings_page_display)

        restoration.emulateSavedInstanceStateRestore()
        assertOnPage(R.string.settings_number_format)
        tab("Manage").performClick()

        composeRule.onNodeWithText(text(R.string.settings_bills_calendar)).assertExists()
    }

    private fun start() {
        composeRule.setContent { MaterialTheme { AppNavigation() } }
        tab("Manage").performClick()
    }

    private fun text(id: Int) = strings.getString(id)

    private fun tab(label: String) = composeRule.onNodeWithContentDescription(label, useUnmergedTree = true)

    /** A row on the shown Settings page; matching inside the scrolling page keeps the tab bar's labels out. */
    private fun settingsRow(id: Int) = composeRule.onNode(hasText(text(id)) and hasAnyAncestor(hasScrollAction()))

    /** From the Manage root, opens Settings and then one of its pages. */
    private fun openSettingsPage(id: Int) {
        composeRule.onNodeWithContentDescription(text(R.string.settings_open_settings)).performClick()
        settingsRow(id).performScrollTo().performClick()
    }

    /** The given row is on screen and the Manage root is not, so back or a tab switch did not reset to the root. */
    private fun assertOnPage(rowId: Int) {
        composeRule.onNodeWithText(text(rowId)).assertExists()
        composeRule.onNodeWithText(text(R.string.settings_bills_calendar)).assertDoesNotExist()
    }

    /** Lists load off the main thread, which the test rule does not wait for, so wait for the row first. */
    private fun clickFirst(matcher: SemanticsMatcher) {
        composeRule.waitUntil(TIMEOUT) { composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onAllNodes(matcher).onFirst().performClick()
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
