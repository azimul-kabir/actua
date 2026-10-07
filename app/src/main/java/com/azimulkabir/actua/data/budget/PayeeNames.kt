package com.azimulkabir.actua.data.budget

import java.util.Locale

/** Payee-name comparison shared with Actual's `UNICODE_LOWER(name) = lower(?)` lookups. */
object PayeeNames {
    /** JavaScript `String.prototype.toLowerCase()`: locale-independent, full Unicode lower-casing. */
    fun unicodeLower(value: String): String = value.lowercase(Locale.ROOT)
}
