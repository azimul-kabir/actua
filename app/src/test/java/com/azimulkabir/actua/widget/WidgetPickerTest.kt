package com.azimulkabir.actua.widget

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the launcher's widget picker shows for each widget: a description and a populated preview. */
class WidgetPickerTest {
    private val res = File("src/main/res")
    private val locales = listOf("values", "values-es", "values-pt-rBR")
    private val infos = res.resolve("xml").listFiles { f -> f.name.startsWith("widget_") && f.name.endsWith("_info.xml") }!!.sortedBy { it.name }

    private fun attr(xml: String, name: String) = Regex("""android:$name="@(?:string|layout)/(\w+)"""").find(xml)?.groupValues?.get(1)

    private fun stringNames(locale: String): Set<String> =
        res.resolve(locale).listFiles { f -> f.name.endsWith(".xml") }!!
            .flatMap { Regex("""<string name="(\w+)"""").findAll(it.readText()).map { m -> m.groupValues[1] }.toList() }.toSet()

    @Test
    fun everyWidgetHasItsOwnOneLineDescription() {
        assertEquals("one provider info per widget", 8, infos.size)
        val descriptions = infos.map { attr(it.readText(), "description") }
        descriptions.forEach { assertTrue("description should be widget_description_*: $it", it!!.startsWith("widget_description_")) }
        assertEquals("every widget describes itself differently", descriptions.size, descriptions.toSet().size)
        for (locale in locales) {
            val names = stringNames(locale)
            descriptions.forEach { assertTrue("$it is missing from $locale", it in names) }
        }
    }

    @Test
    fun previewLayoutsExistAndSampleTextIsTranslated() {
        for (info in infos) {
            val xml = info.readText()
            val preview = attr(xml, "previewLayout")!!
            val initial = attr(xml, "initialLayout")!!
            assertTrue("$preview.xml is missing", res.resolve("layout/$preview.xml").exists())
            // The data widgets must not show their empty initial layout in the picker.
            if (initial != "widget_quick_transaction") assertFalse("${info.name} previews its empty initial layout", preview == initial)
        }
        for (locale in locales) {
            val names = stringNames(locale)
            val wanted = (1..3).flatMap { listOf("widget_sample_category_$it", "widget_sample_account_$it", "widget_sample_schedule_$it") }
            wanted.forEach { assertTrue("$it is missing from $locale", it in names) }
        }
    }

    @Test
    fun previewsUseOnlySyntheticText() {
        val sample = res.resolve("values/widget_sample.xml").readText()
        assertEquals("amounts are not translated", 12, Regex("""translatable="false"""").findAll(sample).count())
        res.resolve("layout").listFiles { f -> f.name.startsWith("widget_preview_") }!!.forEach { layout ->
            val text = layout.readText()
            assertFalse("${layout.name} hard-codes text", Regex("""android:text="[^@]""").containsMatchIn(text))
            assertFalse("${layout.name} must not be bound to a budget", text.contains("android:id="))
        }
    }
}
