package com.azimulkabir.actua.data.security

import com.azimulkabir.actua.MainActivity
import com.azimulkabir.actua.ui.lock.AppLockActivity
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Audit of the activities other apps and the system can start (#1341). Every exported activity either
 * is [MainActivity] (which locks itself), extends [AppLockActivity] (so its content sits behind the
 * app lock), or is on the short list below of entry points that show nothing. A new exported
 * activity fails here until it is gated or consciously added to that list.
 */
class AppLockEntryPointsTest {
    private val manifests = listOf("src/main/AndroidManifest.xml", "src/playstore/AndroidManifest.xml")

    /** Activities that show no budget data: they forward to [MainActivity] and finish. */
    private val showNothing = setOf("com.azimulkabir.actua.data.budget.gdrive.GoogleDriveAuthCallbackActivity")

    private fun activities(): List<Element> = manifests.flatMap { path ->
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val nodes = document.getElementsByTagName("activity")
        (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.androidAttribute(name: String): String? =
        getAttributeNS("http://schemas.android.com/apk/res/android", name).takeIf { it.isNotEmpty() }
            ?: getAttribute("android:$name").takeIf { it.isNotEmpty() }

    private fun className(element: Element): String {
        val name = requireNotNull(element.androidAttribute("name")) { "an activity without a name" }
        return if (name.startsWith(".")) "com.azimulkabir.actua$name" else name
    }

    @Test
    fun everyExportedActivityIsGatedByTheAppLock() {
        val exported = activities().filter { it.androidAttribute("exported") == "true" }.map(::className)
        assertTrue("the manifest declares exported activities", exported.isNotEmpty())
        val ungated = exported.filterNot { name ->
            val type = Class.forName(name, false, javaClass.classLoader)
            name in showNothing ||
                MainActivity::class.java.isAssignableFrom(type) ||
                AppLockActivity::class.java.isAssignableFrom(type)
        }
        assertEquals("exported activities outside the app lock: $ungated", emptyList<String>(), ungated)
    }

    @Test
    fun theWidgetTaskerAndDisconnectScreensSitBehindTheLock() {
        val names = listOf(
            "com.azimulkabir.actua.widget.WidgetConfigurationActivity",
            "com.azimulkabir.actua.widget.ScheduleWidgetConfigurationActivity",
            "com.azimulkabir.actua.widget.WidgetLookActivity",
            "com.azimulkabir.actua.tasker.TaskerEditActivity",
            "com.azimulkabir.actua.DisconnectResetActivity",
        )
        val declared = activities().map(::className).toSet()
        names.forEach { name ->
            assertTrue("$name is declared", name in declared)
            assertTrue("$name extends AppLockActivity", AppLockActivity::class.java.isAssignableFrom(Class.forName(name, false, javaClass.classLoader)))
        }
    }

    @Test
    fun theBackgroundCaptureEntryPointsAreReceiversAndServicesThatShowNothing() {
        // They keep queueing while the app is locked, so they must never become screens; see docs/APP_LOCK.md.
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(manifests.first()))
        fun declared(tag: String): List<String> {
            val nodes = document.getElementsByTagName(tag)
            return (0 until nodes.length).map { className(nodes.item(it) as Element) }
        }
        val receivers = declared("receiver")
        assertTrue(receivers.contains("com.azimulkabir.actua.data.importing.AutomationIntentReceiver"))
        assertTrue(receivers.contains("com.azimulkabir.actua.data.importing.TaskerFireReceiver"))
        assertTrue(declared("service").contains("com.azimulkabir.actua.data.importing.FinancialNotificationListenerService"))
    }
}
