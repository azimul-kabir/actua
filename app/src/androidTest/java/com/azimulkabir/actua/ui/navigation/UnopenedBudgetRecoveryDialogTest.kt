package com.azimulkabir.actua.ui.navigation

import android.database.sqlite.SQLiteDatabase
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.budget.BudgetMetadata
import com.azimulkabir.actua.data.sync.SyncedLogMarkers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class UnopenedBudgetRecoveryDialogTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = BudgetFileManager(context)
    private lateinit var budget: BudgetMetadata

    @Before
    fun createServerBudgetWithAnUnsyncedEdit() {
        val created = files.createBudget("Unopened ${UUID.randomUUID()}")
        files.saveCloudRegistration(created.id, "cloud-${UUID.randomUUID()}", "group-1")
        SQLiteDatabase.openDatabase(files.databaseFile(created.id).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            val syncedThrough = db.rawQuery("SELECT MAX(timestamp) FROM messages_crdt", null).use {
                if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null
            }
            SyncedLogMarkers(context).record(created.id, syncedThrough)
            db.execSQL(
                "INSERT INTO messages_crdt (timestamp, dataset, row, `column`, value) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any>("2999-01-01T00:00:00.000Z-0000-aaaaaaaaaaaaaaaa", "accounts", "checking", "name", "S:Checking"),
            )
        }
        budget = requireNotNull(files.listLocalBudgets().firstOrNull { it.id == created.id })
    }

    @After
    fun removeBudget() {
        runCatching { files.deleteBudget(budget.id) }
    }

    @Test
    fun deletingNeedsConfirmationAndWarnsAboutUnsyncedChanges() {
        val reopened = mutableListOf<Boolean>()
        val messages = mutableListOf<String>()
        var dismissed = false
        compose.setContent {
            MaterialTheme {
                if (!dismissed) {
                    UnopenedBudgetRecoveryDialog(
                        budget = budget,
                        onDismiss = { dismissed = true },
                        onReopen = { reopened += it },
                        onMessage = { messages += it },
                    )
                }
            }
        }

        val unsynced = "This budget has 1 change that hasn’t synced to the server."
        compose.waitUntil(5_000) { compose.onAllNodesWithText(unsynced).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithTag("unopenedBudgetDelete").performClick()
        compose.onNodeWithText("Delete “${budget.budgetName}” from this device?").assertExists()
        compose.onNodeWithText(unsynced).assertExists()
        compose.onNodeWithText("The copy on your Actual server isn’t affected.").assertExists()
        assertTrue("Opening the delete step must not delete anything", files.budgetDirectory(budget.id).exists())

        compose.onNodeWithTag("unopenedBudgetDeleteConfirm").performClick()
        compose.waitUntil(5_000) { messages.isNotEmpty() }

        assertFalse(files.budgetDirectory(budget.id).exists())
        assertEquals(listOf("Deleted “${budget.budgetName}” from this device."), messages)
        assertEquals("Delete leaves the current budget selected", listOf(false), reopened)
    }

    @Test
    fun notNowKeepsTheBudget() {
        var dismissed = false
        compose.setContent {
            MaterialTheme {
                if (!dismissed) {
                    UnopenedBudgetRecoveryDialog(
                        budget = budget,
                        onDismiss = { dismissed = true },
                        onReopen = {},
                        onMessage = {},
                    )
                }
            }
        }

        compose.onNodeWithText("Not now").performClick()
        compose.waitForIdle()

        assertTrue(dismissed)
        assertTrue(files.budgetDirectory(budget.id).exists())
    }
}
