package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.rules.TagFilter
import com.azimulkabir.actua.ui.components.findTagOccurrences
import com.azimulkabir.actua.ui.transactions.notesContainTag
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tag parsing compared with Actual at 59fe126f (v26.9.0). Expected values were produced by running
 * Actual's own code on each note: `discoverTags`' `(?<!#)#([^#\s]+)` and `parseNotes` (tags found),
 * `renameTagInNotes` (rename `food` → `meal`), the `hasTags` AQL filter (case-sensitive `REGEXP`,
 * as **View transactions** uses), and `Condition.eval` for `hasTags` (rule runs, case-insensitive).
 * A null expectation is a known divergence filed as #985 and is not asserted.
 */
class TagParityCorpusTest {
    private data class Case(
        val notes: String,
        val tags: List<String>?,
        val renamed: String?,
        val viewMatchesFood: Boolean?,
        val ruleMatchesFood: Boolean?,
        val ruleMatchesUpperFood: Boolean?,
    )

    private val corpus = listOf(
        Case("#food", listOf("food"), "#meal", true, true, true),
        Case("lunch #food", listOf("food"), "lunch #meal", true, true, true),
        Case("#food #fun", listOf("food", "fun"), "#meal #fun", true, true, true),
        Case("a#food", listOf("food"), "a#meal", true, true, true),
        Case("##food", emptyList(), "##food", false, false, false),
        // #985: Actua reads `###food` as a tag in notes, views and rename.
        Case("###food", null, null, null, false, false),
        Case("#food#fun", listOf("food", "fun"), "#meal#fun", true, true, true),
        Case("#food.", listOf("food."), "#food.", false, false, false),
        // #985: View transactions ignores case; Actual's hasTags filter doesn't.
        Case("#Food", listOf("Food"), "#Food", null, true, true),
        // #985: rule matching treats only ASCII whitespace as a tag end.
        Case("#food x", listOf("food"), "#meal x", true, null, null),
        Case("#food\tx", listOf("food"), "#meal\tx", true, true, true),
        Case("#", emptyList(), "#", false, false, false),
        Case("# food", emptyList(), "# food", false, false, false),
        Case("#café", listOf("café"), "#café", false, false, false),
        Case("#a+b", listOf("a+b"), "#a+b", false, false, false),
        Case("x #food,y", listOf("food,y"), "x #food,y", false, false, false),
        Case("#food#", listOf("food"), "#meal#", true, true, true),
    )

    @Test fun tagsInNotesMatchActual() = corpus.forEach { case ->
        case.tags?.let { assertEquals(case.notes, it, findTagOccurrences(case.notes).map { tag -> tag.name }) }
    }

    @Test fun renameMatchesActual() = corpus.forEach { case ->
        case.renamed?.let { assertEquals(case.notes, it, renameTagInNotes(case.notes, "food", "meal")) }
    }

    @Test fun viewTransactionsMatchesActualsHasTagsFilter() = corpus.forEach { case ->
        case.viewMatchesFood?.let { assertEquals(case.notes, it, notesContainTag(case.notes, "food")) }
    }

    @Test fun ruleHasTagsMatchesActual() = corpus.forEach { case ->
        case.ruleMatchesFood?.let { assertEquals(case.notes, it, TagFilter.contains(case.notes, "#food")) }
        case.ruleMatchesUpperFood?.let { assertEquals(case.notes, it, TagFilter.contains(case.notes, "#FOOD")) }
    }

    /** Actual's `extractTagsForFilter`: whitespace- or `#`-separated words, deduplicated, one `#` each. */
    @Test fun filterValueTagsMatchActual() {
        assertEquals(listOf("#a", "#b"), TagFilter.extract("#a #b"))
        assertEquals(listOf("#a", "#b"), TagFilter.extract("a b"))
        assertEquals(listOf("#a"), TagFilter.extract("##a #a"))
        assertEquals(listOf("#a", "#b"), TagFilter.extract("#a#b"))
        assertEquals(emptyList<String>(), TagFilter.extract(""))
        assertEquals(emptyList<String>(), TagFilter.extract("#"))
    }
}
