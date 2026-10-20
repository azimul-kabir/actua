package com.azimulkabir.actua.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the string catalogs as locales are added: every translatable key must exist in every
 * locale, placeholder tokens must match between locales, apostrophes must be escaped, and
 * pt-BR plurals must be complete.
 *
 * Parses the real `src/main/res` tree on the JVM, so it needs neither a device nor the
 * generated `R` class. Later localization phases extract strings into per-feature
 * `strings*.xml` files; this test scans all of them, so file layout stays free.
 */
class StringResourceParityTest {

    private val resDir = File("src/main/res")

    /** Locale directories that must stay in parity; "values" is the default (English) catalog. */
    private val localeDirs = listOf("values", "values-pt-rBR")

    private val locales by lazy { localeDirs.associateWith { parseLocale(File(resDir, it)) } }

    @Test
    fun stringKeysExistInEveryLocale() {
        val english = stringsOf("values")
        val portuguese = stringsOf("values-pt-rBR")
        val missing = english.keys - portuguese.keys
        assertTrue("values-pt-rBR/strings*.xml is missing ${missing.size} key(s): $missing", missing.isEmpty())
        val extra = portuguese.keys - english.keys
        assertTrue("values-pt-rBR/strings*.xml has key(s) with no English counterpart: $extra", extra.isEmpty())
    }

    @Test
    fun pluralKeysExistInEveryLocale() {
        val english = pluralsOf("values").keys
        val portuguese = pluralsOf("values-pt-rBR").keys
        val missing = english - portuguese
        assertTrue("values-pt-rBR/strings*.xml is missing plural(s): $missing", missing.isEmpty())
        val extra = portuguese - english
        assertTrue("values-pt-rBR/strings*.xml has plural(s) with no English counterpart: $extra", extra.isEmpty())
    }

    /** Regression guard: Android's tag is `<plurals>`; an earlier `"plural"` match made every plurals map empty. */
    @Test
    fun parseLocaleParsesPlurals() {
        assertTrue(
            "parseLocale parsed no plurals from src/main/res/values — plural assertions would pass vacuously",
            parseLocale(File("src/main/res/values")).second.isNotEmpty(),
        )
    }

    @Test
    fun apostrophesAreEscaped() {
        localeDirs.forEach { dir ->
            stringsOf(dir).forEach { (name, text) ->
                assertEscapedApostrophes("$dir string '$name'", text)
            }
            pluralsOf(dir).forEach { (name, items) ->
                items.forEach { (quantity, text) ->
                    assertEscapedApostrophes("$dir plural '$name' quantity '$quantity'", text)
                }
            }
        }
    }

    @Test
    fun placeholderTokensAreWellFormedAndMatchBetweenLocales() {
        localeDirs.forEach { dir ->
            stringsOf(dir).forEach { (name, text) ->
                assertValidPlaceholders("$dir string '$name'", text, isPlural = false)
            }
            pluralsOf(dir).forEach { (name, items) ->
                items.forEach { (quantity, text) ->
                    assertValidPlaceholders("$dir plural '$name' quantity '$quantity'", text, isPlural = true)
                }
            }
        }
        stringsOf("values").forEach { (name, text) ->
            assertEquals(
                "placeholder tokens of string '$name' differ between locales",
                tokens(text),
                tokens(stringsOf("values-pt-rBR").getValue(name)),
            )
        }
        pluralsOf("values").forEach { (name, items) ->
            assertEquals(
                "placeholder tokens of plural '$name' (all quantities) differ between locales",
                items.flatMap { (_, text) -> tokens(text) }.sorted(),
                pluralsOf("values-pt-rBR").getValue(name)
                    .flatMap { (_, text) -> tokens(text) }.sorted(),
            )
        }
    }

    @Test
    fun portuguesePluralsDefineOneAndOther() {
        pluralsOf("values-pt-rBR").forEach { (name, items) ->
            val quantities = items.map { it.first }.toSet()
            assertTrue("values-pt-rBR plural '$name' lacks 'one' (has $quantities)", "one" in quantities)
            assertTrue("values-pt-rBR plural '$name' lacks 'other' (has $quantities)", "other" in quantities)
        }
    }

    @Test
    fun resourceNamesAreLowerSnakeCase() {
        localeDirs.forEach { dir ->
            (stringsOf(dir).keys + pluralsOf(dir).keys).forEach { name ->
                assertTrue("$dir resource name '$name' must match ^[a-z][a-z0-9_]*$", NAME.matches(name))
            }
        }
    }

    @Test
    fun portugueseWidgetMarkersArePreserved() {
        val portuguese = stringsOf("values-pt-rBR")
        assertTrue(
            "widget_expense must keep its leading '− ' marker",
            portuguese.getValue("widget_expense").startsWith("− "),
        )
        assertTrue(
            "widget_income must keep its leading '+ ' marker",
            portuguese.getValue("widget_income").startsWith("+ "),
        )
        assertTrue(
            "widget_transfer must keep its leading '⇄ ' marker",
            portuguese.getValue("widget_transfer").startsWith("⇄ "),
        )
    }

    private fun stringsOf(dir: String): Map<String, String> = locales.getValue(dir).first

    private fun pluralsOf(dir: String): Map<String, List<Pair<String, String>>> = locales.getValue(dir).second

    /**
     * Parses every `strings*.xml` in [dir] into (string name → raw text) and
     * (plural name → quantity→text pairs). Fails when a name is defined twice within the
     * locale. Strings marked `translatable="false"` are skipped: they have no translated
     * counterpart (e.g. `app_name`).
     */
    private fun parseLocale(dir: File): Pair<Map<String, String>, Map<String, List<Pair<String, String>>>> {
        val files = dir.listFiles { file -> file.name.startsWith("strings") && file.name.endsWith(".xml") }
            ?.sortedBy { it.name }
            .orEmpty()
        assertTrue(
            "no strings*.xml found under ${dir.path} — is the working directory the app module?",
            files.isNotEmpty(),
        )
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val strings = LinkedHashMap<String, String>()
        val plurals = LinkedHashMap<String, List<Pair<String, String>>>()
        val definedIn = HashMap<String, String>()
        files.forEach { file ->
            val root = builder.parse(file).documentElement
            assertEquals("root element of ${file.name}", "resources", root.tagName)
            var node = root.firstChild
            while (node != null) {
                if (node is Element) when (node.tagName) {
                    "string" -> if (node.getAttribute("translatable") != "false") {
                        val name = node.getAttribute("name")
                        rememberName(name, file, definedIn)
                        strings[name] = node.textContent
                    }
                    "plurals" -> {
                        val name = node.getAttribute("name")
                        rememberName(name, file, definedIn)
                        plurals[name] = childElements(node)
                            .map { it.getAttribute("quantity") to it.textContent }
                    }
                }
                node = node.nextSibling
            }
        }
        return strings to plurals
    }

    private fun rememberName(name: String, file: File, definedIn: MutableMap<String, String>) {
        val previous = definedIn.put(name, file.name)
        assertNull("'$name' is defined in both $previous and ${file.name} within one locale", previous)
    }

    private fun childElements(element: Element): List<Element> {
        val elements = mutableListOf<Element>()
        var node = element.firstChild
        while (node != null) {
            if (node is Element) elements += node
            node = node.nextSibling
        }
        return elements
    }

    /** Android string resources require straight apostrophes to be escaped as `\'` (curly `’` is fine). */
    private fun assertEscapedApostrophes(where: String, text: String) {
        assertFalse("$where has an unescaped apostrophe: \"$text\"", UNESCAPED_APOSTROPHE.containsMatchIn(text))
    }

    /** Every `%` must be `%%`, `%N$[sdf]`, or — in plurals, where the count is auto-supplied — bare `%d`. */
    private fun assertValidPlaceholders(where: String, text: String, isPlural: Boolean) {
        val valid = if (isPlural) PLURAL_PLACEHOLDERS else STRING_PLACEHOLDERS
        assertFalse("$where has a stray '%': \"$text\"", text.replace(valid, "").contains('%'))
    }

    /**
     * Format tokens with bare `%d` normalized to `%1$d`, sorted so failures read as clean diffs.
     * Compared by equality: passing "%1$d" through String.replace would trip Java's group
     * reference parsing (`$d`) in the replacement.
     */
    private fun tokens(text: String): List<String> =
        PLACEHOLDER_TOKEN.findAll(text)
            .map { if (it.value == "%d") "%1\$d" else it.value }
            .sorted()
            .toList()

    private companion object {
        val STRING_PLACEHOLDERS = Regex("%%|%\\d+\\$[sdf]")
        val PLURAL_PLACEHOLDERS = Regex("%%|%\\d+\\$[sdf]|%d")
        val PLACEHOLDER_TOKEN = Regex("%%|%\\d+\\$[sdf]|%d")
        val UNESCAPED_APOSTROPHE = Regex("(?<!\\\\)'")
        val NAME = Regex("^[a-z][a-z0-9_]*$")
    }
}
