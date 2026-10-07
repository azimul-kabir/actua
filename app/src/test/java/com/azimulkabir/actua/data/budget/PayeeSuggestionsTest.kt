package com.azimulkabir.actua.data.budget

import org.junit.Assert.assertEquals
import org.junit.Test

/** Port of desktop-client `getPayeeSuggestions` (#897). */
class PayeeSuggestionsTest {
    @Test fun favoritesByNameThenCommonPayeesUpToFive() {
        assertEquals(
            listOf("apple", "Zoo", "Bakery", "Cafe", "deli"),
            PayeeSuggestions.suggest(
                favorites = listOf("Zoo", "apple"),
                common = listOf("deli", "Zoo", "Cafe", "Bakery", "Extra"),
            ),
        )
    }

    @Test fun everyFavoriteIsShownEvenBeyondFive() {
        val favorites = listOf("f", "e", "d", "c", "b", "a")
        assertEquals(favorites.sorted(), PayeeSuggestions.suggest(favorites, listOf("common")))
    }

    @Test fun noFavoritesOrCommonPayeesMeansNoSuggestions() {
        assertEquals(emptyList<String>(), PayeeSuggestions.suggest(emptyList(), emptyList()))
        assertEquals(listOf("Cafe"), PayeeSuggestions.suggest(emptyList(), listOf("Cafe")))
    }
}
