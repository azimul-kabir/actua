package com.azimulkabir.actua.model

/**
 * Actual's `storeNoteTemplates`, run before templates are previewed or applied: every category
 * whose definition isn't UI-managed has its `goal_def` re-read from its note, so a note written
 * by another client is honoured. A category whose note no longer has a `#template`/`#goal`
 * directive has its `goal_def` cleared (`resetCategoryGoalDefsWithNoTemplates`). Unlike Actual,
 * a malformed or partly unsupported note leaves the prior definition untouched, as editing the
 * note in Actua does.
 */
object NoteTemplateRefresh {
    data class Category(val id: String, val goalDef: String?, val templateSource: String?, val note: String?)

    data class Plan(val goalDefs: Map<String, String?>, val resets: Set<String>) {
        val isEmpty: Boolean get() = goalDefs.isEmpty() && resets.isEmpty()
    }

    fun plan(categories: List<Category>): Plan {
        val goalDefs = mutableMapOf<String, String?>()
        val resets = mutableSetOf<String>()
        // Actual treats a missing template_settings source as notes-managed.
        categories.filter { it.templateSource != "ui" }.forEach { category ->
            val note = category.note.orEmpty()
            if (!note.contains("#template", ignoreCase = true) && !note.contains("#goal", ignoreCase = true)) {
                if (category.goalDef != null) resets += category.id
                return@forEach
            }
            val parsed = BudgetNoteAutomationParser.parse(note)
            if (!parsed.valid) return@forEach
            val current = BudgetAutomationDocument.decode(category.goalDef, "notes")
            if (category.goalDef != null && current.unsupportedTypes.isEmpty() && current.supported == parsed.targets) {
                return@forEach
            }
            goalDefs[category.id] = BudgetAutomationDocument.encode(parsed.targets)
        }
        return Plan(goalDefs, resets)
    }
}
