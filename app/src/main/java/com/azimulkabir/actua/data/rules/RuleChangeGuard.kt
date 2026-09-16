package com.azimulkabir.actua.data.rules

/**
 * Ported from Actual's `shouldApplyRuleChange` (desktop-client
 * components/transactions/table/utils.ts): decides whether a rule-derived
 * field value should overwrite a value the user already entered, or a
 * manual value should survive a matching rule.
 */
object RuleChangeGuard {
    fun shouldApplyRuleChange(field: String, currentValue: Any?, nextValue: Any?): Boolean {
        if (currentValue == null || currentValue == "" || currentValue == 0L || currentValue == 0 || currentValue == false) {
            return true
        }
        if (field != "notes" || currentValue !is String || nextValue !is String || nextValue == currentValue) {
            return false
        }
        val index = nextValue.indexOf(currentValue)
        if (index == -1) return false
        val prepended = nextValue.substring(0, index)
        val appended = nextValue.substring(index + currentValue.length)
        val alreadyApplied = (prepended.isEmpty() || currentValue.startsWith(prepended)) &&
            (appended.isEmpty() || currentValue.endsWith(appended))
        return !alreadyApplied
    }
}
