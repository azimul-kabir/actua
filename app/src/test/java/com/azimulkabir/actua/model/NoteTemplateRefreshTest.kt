package com.azimulkabir.actua.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Actual's `storeNoteTemplates` before applying templates (#855). */
class NoteTemplateRefreshTest {
    private fun category(id: String, note: String?, goalDef: String? = null, source: String? = "notes") =
        NoteTemplateRefresh.Category(id, goalDef, source, note)

    @Test fun noteWithoutAStoredDefinitionIsStored() {
        val plan = NoteTemplateRefresh.plan(listOf(category("food", "#template 75")))

        val stored = BudgetAutomationDocument.decode(plan.goalDefs.getValue("food"), "notes").supported
        assertEquals(listOf(BudgetTarget(BudgetTarget.Type.FIXED, 7_500, priority = 0, simple = true)), stored)
        assertTrue(plan.resets.isEmpty())
    }

    @Test fun categoriesWithoutTemplateSettingsAreTreatedAsNotesManaged() {
        assertEquals(setOf("food"), NoteTemplateRefresh.plan(listOf(category("food", "#template 75", source = null))).goalDefs.keys)
    }

    @Test fun uiManagedCategoriesAreLeftAlone() {
        val plan = NoteTemplateRefresh.plan(listOf(
            category("ui", "#template 75", source = "ui"),
            category("ui-cleared", "", goalDef = """[{"type":"simple","monthly":5}]""", source = "ui"),
        ))

        assertTrue(plan.isEmpty)
    }

    @Test fun anUnchangedDefinitionIsNotRewritten() {
        val stored = requireNotNull(BudgetAutomationDocument.encode(BudgetNoteAutomationParser.parse("#template 20").targets))

        assertTrue(NoteTemplateRefresh.plan(listOf(category("food", "#template 20", goalDef = stored))).isEmpty)
    }

    @Test fun aNoteWithoutDirectivesClearsTheDefinitionAndMalformedNotesKeepIt() {
        val stored = """[{"directive":"template","type":"simple","monthly":20,"priority":0}]"""
        val plan = NoteTemplateRefresh.plan(listOf(
            category("cleared", "just a note", goalDef = stored),
            category("malformed", "#template nonsense here", goalDef = stored),
            category("empty", null),
        ))

        assertEquals(setOf("cleared"), plan.resets)
        assertTrue(plan.goalDefs.isEmpty())
    }
}
