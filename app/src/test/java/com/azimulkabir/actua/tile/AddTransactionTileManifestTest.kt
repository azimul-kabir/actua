package com.azimulkabir.actua.tile

import com.azimulkabir.actua.ui.navigation.AddTransactionActions
import com.azimulkabir.actua.widget.WidgetActions
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Quick Settings tile (#1362) is declared so only System UI can bind it, and opens the add-transaction flow. */
class AddTransactionTileManifestTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    private fun tileService(): String {
        val start = manifest.indexOf("android:name=\".tile.AddTransactionTileService\"")
        assertTrue("the tile service is declared", start >= 0)
        return manifest.substring(manifest.lastIndexOf("<service", start), manifest.indexOf("</service>", start))
    }

    @Test
    fun theTileIsBoundOnlyBySystemUi() {
        val service = tileService()
        assertTrue(service.contains("android:permission=\"android.permission.BIND_QUICK_SETTINGS_TILE\""))
        assertTrue(service.contains("<action android:name=\"android.service.quicksettings.action.QS_TILE\" />"))
        assertTrue(service.contains("android:exported=\"true\""))
        assertTrue(service.contains("android:label=\"@string/tile_add_transaction\""))
        assertTrue(service.contains("android:icon=\"@drawable/ic_tile_add_transaction\""))
    }

    @Test
    fun theTileUsesTheAddTransactionActionThatExplainsAMissingBudget() {
        assertTrue(WidgetActions.ADD_EXPENSE in AddTransactionActions)
        assertEquals(
            setOf(WidgetActions.ADD_EXPENSE, WidgetActions.ADD_INCOME, WidgetActions.ADD_TRANSFER),
            AddTransactionActions,
        )
    }
}
