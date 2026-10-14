package com.azimulkabir.actua.data.importing

import java.time.LocalDate

/**
 * The Tasker/Locale action plugin "Queue transaction" (#1040): the same extras as the
 * [AutomationIntent] broadcast, configured in a form and stored by Tasker as a bundle. Constants
 * follow the Locale plugin API and Tasker's `TaskerPlugin.java` extensions.
 */
object TaskerPlugin {
    const val ACTION_EDIT_SETTING = "com.twofortyfouram.locale.intent.action.EDIT_SETTING"
    const val ACTION_FIRE_SETTING = "com.twofortyfouram.locale.intent.action.FIRE_SETTING"
    const val EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE"
    const val EXTRA_BLURB = "com.twofortyfouram.locale.intent.extra.BLURB"

    private const val TASKER_EXTRAS = "net.dinglisch.android.tasker.extras."
    /** Space-separated bundle keys whose `%variables` Tasker replaces before firing. */
    const val KEY_VARIABLE_REPLACE = TASKER_EXTRAS + "VARIABLE_REPLACE_KEYS"
    const val EXTRA_HOST_CAPABILITIES = TASKER_EXTRAS + "HOST_CAPABILITIES"
    const val CAPABILITY_SYNCHRONOUS = 32
    /** Asks Tasker to wait for the fire receiver's result, so a failure reaches the task. */
    const val EXTRA_REQUESTED_TIMEOUT = TASKER_EXTRAS + "REQUESTED_TIMEOUT"
    const val REQUESTED_TIMEOUT_MS = 3_000
    const val KEY_VARIABLES = TASKER_EXTRAS + "VARIABLES"
    const val VARIABLE_ERROR_MESSAGE = "%errmsg"

    /** Bundle keys are the broadcast extra names under this prefix. */
    private const val KEY_PREFIX = "com.azimulkabir.actua.tasker."

    /** The form's fields, in display order; the token is filled in, never typed. */
    val FIELDS = AutomationIntent.EXTRAS - AutomationIntent.EXTRA_TOKEN

    const val TOKEN_ADVICE = "Open the Actua action in Tasker and save it again"

    fun key(extra: String) = KEY_PREFIX + extra

    /** The bundle values Tasker stores for [fields] (extra name → text) and the current [token]. */
    fun bundleValues(fields: Map<String, String>, token: String): Map<String, String> = buildMap {
        put(key(AutomationIntent.EXTRA_TOKEN), token)
        FIELDS.forEach { extra -> fields[extra]?.trim()?.takeIf(String::isNotEmpty)?.let { put(key(extra), it) } }
    }

    /** Every stored field may hold `%variables`; the token never does. */
    fun replaceKeys(values: Map<String, String>): String =
        values.keys.filter { it != key(AutomationIntent.EXTRA_TOKEN) }.joinToString(" ")

    /** The form's fields read back from a stored bundle, for editing. */
    fun fieldsOf(values: Map<String, String?>): Map<String, String> =
        FIELDS.mapNotNull { extra -> values[key(extra)]?.let { extra to it } }.toMap()

    /** The one-line summary Tasker shows under the action, without the token. */
    fun blurb(fields: Map<String, String>): String =
        FIELDS.mapNotNull { extra -> fields[extra]?.trim()?.takeIf(String::isNotEmpty)?.let { "$extra: $it" } }
            .joinToString("\n")
            .ifEmpty { "Nothing set" }

    /** Whether [fields] are enough to save: an amount or alert text to parse. */
    fun canSave(fields: Map<String, String>): Boolean =
        listOf(AutomationIntent.EXTRA_AMOUNT, AutomationIntent.EXTRA_TEXT).any { !fields[it].isNullOrBlank() }

    /**
     * The broadcast extras for a fired bundle: a Tasker variable left unset (still `%name`) is
     * empty, and the amount keeps only digits, sign, comma and point, so "$ -12.34" is "-12.34".
     */
    fun extrasOf(values: Map<String, String?>): Map<String, String?> =
        AutomationIntent.EXTRAS.associateWith { extra ->
            values[key(extra)]?.trim()?.takeUnless { unsetVariable.matches(it) }
                // An amount can't hold text, so a variable anywhere in it, as in "-%amount", is unset (#1073).
                ?.takeUnless { extra == AutomationIntent.EXTRA_AMOUNT && unsetAmountVariable(values) != null }
                ?.let { if (extra == AutomationIntent.EXTRA_AMOUNT) cleanAmount(it) else it }
        }

    /**
     * The first number in [raw], keeping `,` thousands separators (#1080): "-$10.69", "−10.69",
     * "Rs.10.69" and "10.69 USD" all read as 10.69 with their sign. Minus signs before it are
     * counted, so "-%amount" with a refund of -10.69 ("--10.69") is +10.69, money in. Empty when
     * [raw] holds no digits.
     */
    fun cleanAmount(raw: String): String {
        val number = amountNumber.find(raw) ?: return ""
        val negative = raw.substring(0, number.range.first).count { it in minusSigns } % 2 == 1
        return (if (negative) "-" else "") + number.value
    }

    private val amountNumber = Regex("\\d[\\d,]*(?:\\.\\d+)?")
    private val minusSigns = setOf('-', '\u2212', '\u2012', '\u2013', '\u2014')

    /** The `%variable` Tasker left unreplaced in the stored amount, e.g. "%amount" in "-%amount". */
    fun unsetAmountVariable(values: Map<String, String?>): String? =
        values[key(AutomationIntent.EXTRA_AMOUNT)]?.let { variableReference.find(it)?.value }

    /**
     * Runs a fired bundle like [handleAutomationExtras], but names the Tasker variable when an
     * unset one left nothing to queue (#1073). The message has the variable's name, never a value.
     */
    fun fire(
        enabled: Boolean,
        expectedToken: String?,
        values: Map<String, String?>,
        today: LocalDate = LocalDate.now(),
        profile: FinancialMessageProfile = FinancialMessageParser.defaultProfile,
    ): AutomationResult {
        val extras = extrasOf(values)
        val result = handleAutomationExtras(enabled, expectedToken, extras, today, profile, TOKEN_ADVICE)
        val unset = unsetAmountVariable(values)
        val authorized = isAuthorizedAutomation(enabled, expectedToken, extras[AutomationIntent.EXTRA_TOKEN])
        if (!authorized || result.candidates.isNotEmpty() || extras[AutomationIntent.EXTRA_TEXT] != null) return result
        val storedAmount = values[key(AutomationIntent.EXTRA_AMOUNT)]?.trim().orEmpty()
        return when {
            unset != null -> result.copy(rejection = "Tasker variable $unset wasn't set when the action ran")
            // An amount was configured but held no digits once symbols were removed (#1080).
            storedAmount.isNotEmpty() && !unsetVariable.matches(storedAmount) &&
                extras[AutomationIntent.EXTRA_AMOUNT].isNullOrEmpty() ->
                result.copy(rejection = "The amount had no number in it")
            else -> result
        }
    }

    private val unsetVariable = Regex("%[A-Za-z0-9_]+")

    /** A Tasker variable name: a letter, then at least two letters, digits or underscores. */
    private val variableReference = Regex("%[A-Za-z][A-Za-z0-9_]{2,}")
}
