package com.azimulkabir.actua.ui.navigation

import com.azimulkabir.actua.data.notifications.CapturedTransactionNotifications
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The "Review captured" launcher shortcut opens the same page as a capture notification (#1065). */
class ReviewCapturedShortcutTest {
    @Test
    fun `review captured shortcut sends the capture review action to MainActivity`() {
        val shortcuts = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/xml/shortcuts.xml")).documentElement.getElementsByTagName("shortcut")
        val shortcut = (0 until shortcuts.length).map { shortcuts.item(it) as Element }
            .single { it.getAttribute("android:shortcutId") == "review_captured" }
        val intent = shortcut.getElementsByTagName("intent").item(0) as Element

        assertEquals(CapturedTransactionNotifications.ACTION_REVIEW, intent.getAttribute("android:action"))
        assertEquals("com.azimulkabir.actua.MainActivity", intent.getAttribute("android:targetClass"))
    }

    @Test
    fun `accounts tab label says how many captures wait`() {
        // The spoken label comes from the navigation_tab_captures_waiting plural: the tab name,
        // then the count, with English singular/plural exactly as the old hard-coded string built
        // them ("Accounts, 1 captured transaction waiting" / "... 120 captured transactions waiting").
        val items = navigationPluralItems("navigation_tab_captures_waiting")
        assertEquals("%1\$s, %2\$d captured transaction waiting", items.getValue("one"))
        assertEquals("%1\$s, %2\$d captured transactions waiting", items.getValue("other"))
    }
}

/** Reads a plural's quantity→format map from the default (English) navigation catalog. */
private fun navigationPluralItems(name: String): Map<String, String> {
    val root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(File("src/main/res/values/strings_navigation.xml")).documentElement
    val plurals = (0 until root.getElementsByTagName("plurals").length)
        .map { root.getElementsByTagName("plurals").item(it) as Element }
        .single { it.getAttribute("name") == name }
    val items = plurals.getElementsByTagName("item")
    return (0 until items.length).associate { i ->
        val item = items.item(i) as Element
        item.getAttribute("quantity") to item.textContent
    }
}
