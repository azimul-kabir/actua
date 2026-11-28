package com.azimulkabir.actua.data.preferences

internal data class CategoryIcon(val source: String, val value: String) {
    override fun toString(): String = "$source:$value"

    companion object {
        fun parse(key: String): CategoryIcon? {
            val separator = key.indexOf(':')
            if (separator <= 0 || separator == key.lastIndex) return null
            val source = key.substring(0, separator)
            val value = key.substring(separator + 1)
            if (source.isBlank() || value.isBlank()) return null
            return CategoryIcon(source, value)
        }
    }
}

internal fun reservesCategoryIconSlot(icons: Map<String, String>, visibleIds: Sequence<String?>): Boolean =
    icons.isNotEmpty() && visibleIds.any { it in icons }
