package com.azimulkabir.actua.widget

import com.azimulkabir.actua.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/**
 * A non-default widget style recolors views by id, from the lists in [WidgetStyles]. A view that is missing
 * from them keeps its layout's default color, which can be unreadable on a styled widget, and nothing fails
 * when that happens. This checks the layouts against the lists, so a new view has to be listed (or named in
 * [intentionallyUnstyled] with a reason) before it can ship.
 */
class WidgetStyleCoverageTest {
    private val android = "http://schemas.android.com/apk/res/android"

    /** One view that a style has to color: its tag, its id name and the layout file it is in. */
    private data class Declared(val layout: String, val tag: String, val id: String?)

    /**
     * Views the styles deliberately leave alone, each with why. The charts are bitmaps drawn with
     * [WidgetStyles.chartColors] (the style's colors, at the moment of the update), so there is nothing to recolor by id.
     */
    private val intentionallyUnstyled = mapOf(
        "widget_is_chart" to "Income and spending chart, a bitmap drawn in the style's colors",
        "widget_trend_chart" to "Spending trend chart, a bitmap drawn in the style's colors",
    )

    private val textIds: Set<Int> get() =
        (WidgetStyles.onBackgroundIds + WidgetStyles.secondaryIds + WidgetStyles.primaryIds + WidgetStyles.buttonIds).toSet()

    private val idValues: Map<String, Int> =
        R.id::class.java.fields.filter { it.type == Int::class.javaPrimitiveType }.associate { it.name to it.getInt(null) }

    /** The views in [xml] that need a style color: text, buttons, progress bars and images. */
    private fun declaredViews(layout: String, xml: String): List<Declared> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val root = factory.newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement
        val views = mutableListOf<Declared>()
        fun walk(element: Element) {
            val tag = element.tagName.substringAfterLast('.')
            if (tag in setOf("TextView", "Button", "TextClock", "ProgressBar", "ImageView")) {
                val id = element.getAttributeNS(android, "id").substringAfter("/", "").ifEmpty { null }
                views += Declared(layout, tag, id)
            }
            val children = element.childNodes
            for (index in 0 until children.length) (children.item(index) as? Element)?.let(::walk)
        }
        walk(root)
        return views
    }

    /** What is wrong with [views]: each message names the layout and the view, so the fix is obvious. */
    private fun problems(views: List<Declared>): List<String> = views.mapNotNull { view ->
        val where = "${view.layout}: <${view.tag}>"
        val id = view.id
        if (id == null) return@mapNotNull "$where has no android:id, so a style cannot color it"
        if (id in intentionallyUnstyled) return@mapNotNull null
        val value = idValues[id] ?: return@mapNotNull "$where @+id/$id is not a known id"
        when (view.tag) {
            "ProgressBar" ->
                if (value !in WidgetStyles.progressIds) "$where @+id/$id is not in WidgetStyles.progressIds" else null
            "ImageView" -> "$where @+id/$id is an image: list it in intentionallyUnstyled with a reason, or color it in WidgetStyles"
            else -> if (value !in textIds) "$where @+id/$id is not in a text color list in WidgetStyles" else null
        }
    }

    private fun widgetLayouts(): List<File> = File("src/main/res/layout")
        .listFiles { file -> file.name.startsWith("widget_") && file.extension == "xml" }
        .orEmpty()
        // The picker's static previews (before Android 15) never go through WidgetStyles.apply.
        .filterNot { it.name.startsWith("widget_preview_") }
        .sortedBy { it.name }

    @Test
    fun everyStyledViewInTheWidgetLayoutsIsInAStyleList() {
        val layouts = widgetLayouts()
        assertTrue("found the widget layouts", layouts.size > 20)
        val found = layouts.flatMap { declaredViews(it.name, it.readText()) }
        assertTrue("found the views to check", found.size > 50)

        val all = found.flatMap { listOfNotNull(it.id) }.toSet()
        assertEquals("every allow-listed id still exists in a layout, so the list stays honest", emptySet<String>(), intentionallyUnstyled.keys - all)

        assertEquals(emptyList<String>(), problems(found))
    }

    @Test
    fun aNewTextViewThatIsInNoStyleListIsReportedWithItsLayoutAndId() {
        val layout = """
            <FrameLayout xmlns:android="$android">
                <TextView android:id="@+id/widget_title" />
                <TextView android:id="@+id/widget_updated" />
                <TextView android:id="@+id/widget_title" />
            </FrameLayout>
        """.trimIndent()
        assertEquals("a listed view passes", emptyList<String>(), problems(declaredViews("widget_ok.xml", layout)))

        val withNewView = layout.replace("</FrameLayout>", "<TextView android:id=\"@+id/widget_updated_at\" /></FrameLayout>")
        val reported = problems(declaredViews("widget_new.xml", withNewView))
        assertEquals(1, reported.size)
        assertTrue(reported.single(), reported.single().contains("widget_new.xml") && reported.single().contains("widget_updated_at"))
    }

    @Test
    fun aViewWithoutAnIdAndAnUnlistedProgressBarAreReported() {
        val layout = """
            <FrameLayout xmlns:android="$android">
                <TextView />
                <ProgressBar android:id="@+id/widget_updated" />
            </FrameLayout>
        """.trimIndent()
        val reported = problems(declaredViews("widget_bad.xml", layout))
        assertEquals(2, reported.size)
        assertTrue(reported[0], reported[0].contains("no android:id"))
        assertTrue(reported[1], reported[1].contains("progressIds"))
    }

    @Test
    fun anUnlistedImageMustBeNamedInTheAllowListWithAReason() {
        val layout = """
            <FrameLayout xmlns:android="$android">
                <ImageView android:id="@+id/widget_trend_chart" />
                <ImageView android:id="@+id/widget_title" />
            </FrameLayout>
        """.trimIndent()
        val reported = problems(declaredViews("widget_img.xml", layout))
        assertEquals(1, reported.size)
        assertTrue(reported.single(), reported.single().contains("widget_title") && reported.single().contains("intentionallyUnstyled"))
    }
}
