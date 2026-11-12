package com.azimulkabir.actua.ui.navigation

import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.data.rules.Rule
import com.azimulkabir.actua.data.rules.RuleEditorData

/** What the Rules screen reads, loaded together in one pass. */
internal data class RulesData(
    val rules: List<Rule>,
    val supported: Boolean,
    val scheduleOwnedRuleIds: Set<String>,
    val editorData: RuleEditorData,
) {
    companion object {
        /**
         * What shows before the first load. Rules count as supported so the screen does not
         * flash its "not supported" message for a budget that supports them.
         */
        val Initial = RulesData(
            rules = emptyList(),
            supported = true,
            scheduleOwnedRuleIds = emptySet(),
            editorData = RuleEditorData(),
        )

        /** Blocking: call off the main thread. */
        fun load(repository: ActuaRepository) = RulesData(
            rules = repository.rules(),
            supported = repository.rulesSupported(),
            scheduleOwnedRuleIds = repository.scheduleOwnedRuleIds(),
            editorData = repository.ruleEditorData(),
        )
    }
}
