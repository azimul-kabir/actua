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
    fun theTileLabelIsShortAndTheDescriptionSaysAddInEveryLanguage() {
        // The plus icon already says "add", so the label stays short enough for a half-width tile, while the
        // description (read by TalkBack) keeps the full phrase.
        for (folder in listOf("values", "values-es", "values-pt-rBR")) {
            val strings = File("src/main/res/$folder/strings.xml").readText()
            fun value(name: String) = Regex("<string name=\"$name\">([^<]*)</string>").find(strings)?.groupValues?.get(1)
            val label = value("tile_add_transaction")
            val description = value("tile_add_transaction_description")
            assertTrue("$folder has a tile label", label != null)
            assertTrue("$folder has a tile description", description != null)
            assertTrue("$folder label '$label' fits a half-width tile", label!!.length <= 12)
            assertTrue("$folder description '$description' is longer than the label", description!!.length > label.length)
        }
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
