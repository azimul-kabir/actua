package com.azimulkabir.actua.data.budget

/**
 * Actual's "Suggested Payees" (`getPayeeSuggestions` in desktop-client `PayeeAutocomplete.tsx`):
 * every favorite payee by name, topped up to [MAX_SUGGESTIONS] with the most-used payees of the
 * last [COMMON_WINDOW_DAYS] days (`getCommonPayees`), also by name.
 */
object PayeeSuggestions {
    const val MAX_SUGGESTIONS = 5
    const val COMMON_WINDOW_DAYS = 12 * 7

    /** [common] is in usage order (most used first); both lists are payee names. */
    fun suggest(favorites: List<String>, common: List<String>): List<String> {
        val favoriteNames = favorites.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
        val additional = if (favoriteNames.size < MAX_SUGGESTIONS) {
            common.distinct().filterNot { it in favoriteNames }
                .take(MAX_SUGGESTIONS - favoriteNames.size)
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
        } else {
            emptyList()
        }
        return favoriteNames + additional
    }
}
