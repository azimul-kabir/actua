package com.azimulkabir.actua.data.importing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Remembered import accounts are per budget and go with "Delete notification data" (#1045). */
@RunWith(AndroidJUnit4::class)
class ImportAccountMemoryTest {
    private val store = NotificationImportPreferences(InstrumentationRegistry.getInstrumentation().targetContext)

    @Before @After fun clear() = store.clearAll()

    @Test fun choicesAreRememberedPerBudgetAndReplaced() {
        store.rememberAccounts("budget-a", mapOf("hint:9921" to "amex", "source:com.bank.app" to "checking"))
        store.rememberAccounts("budget-a", mapOf("hint:9921" to "visa"))

        assertEquals("visa", store.rememberedAccount("budget-a", "hint:9921"))
        assertEquals("checking", store.rememberedAccount("budget-a", "source:com.bank.app"))
        assertNull(store.rememberedAccount("budget-b", "hint:9921"))
    }

    @Test fun deletingNotificationDataForgetsThem() {
        store.rememberAccounts("budget-a", mapOf("hint:9921" to "amex"))
        store.clearAll()
        assertNull(store.rememberedAccount("budget-a", "hint:9921"))
    }

    @Test fun defaultAccountIsPerBudgetAndClearable() {
        store.setDefaultAccount("budget-a", "checking")
        assertEquals("checking", store.defaultAccount("budget-a"))
        assertEquals("checking", store.rememberedAccount("budget-a", DEFAULT_IMPORT_ACCOUNT_KEY))
        assertNull(store.defaultAccount("budget-b"))
        store.setDefaultAccount("budget-a", null)
        assertNull(store.defaultAccount("budget-a"))
    }
}
