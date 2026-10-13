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
        assertEquals("Accounts", tabLabelWithCaptures("Accounts", 0))
        assertEquals("Accounts, 1 captured transaction waiting", tabLabelWithCaptures("Accounts", 1))
        assertEquals("Accounts, 120 captured transactions waiting", tabLabelWithCaptures("Accounts", 120))
    }
}
