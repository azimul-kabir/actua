package com.azimulkabir.actua.ui.transactions

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.data.budget.model.ActualTag
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TagAutocompleteFieldTest {
    @get:Rule val compose = createComposeRule()

    private val tags = listOf(
        ActualTag("t1", "school", "#800080", null, hidden = false),
        ActualTag("t2", "groceries", "#00A000", null, hidden = false),
        ActualTag("t3", "archived", "#808080", null, hidden = true),
    )

    @Test
    fun typingHashOffersMatchingTags() {
        compose.setContent {
            MaterialTheme {
                TagAutocompleteField(value = "", tags = tags, onValueChange = {}, onCreateTag = { null })
            }
        }

        compose.onNode(hasSetTextAction()).performTextInput("#sch")

        compose.onNodeWithText("#school").assertExists()
    }

    @Test
    fun hiddenTagsAreExcludedFromSuggestions() {
        compose.setContent {
            MaterialTheme {
                TagAutocompleteField(value = "", tags = tags, onValueChange = {}, onCreateTag = { null })
            }
        }

        compose.onNode(hasSetTextAction()).performTextInput("#arch")

        compose.onNodeWithText("#archived").assertDoesNotExist()
    }

    @Test
    fun unmatchedTokenOffersCreateAction() {
        compose.setContent {
            MaterialTheme {
                TagAutocompleteField(value = "", tags = tags, onValueChange = {}, onCreateTag = { null })
            }
        }

        compose.onNode(hasSetTextAction()).performTextInput("#travel")

        compose.onNodeWithText("Create #travel").assertExists()
    }

    @Test
    fun tappingTrailingHashButtonOffersExistingTags() {
        compose.setContent {
            MaterialTheme {
                TagAutocompleteField(value = "", tags = tags, onValueChange = {}, onCreateTag = { null })
            }
        }

        compose.onNodeWithText("#").performClick()

        compose.onNodeWithText("#school").assertExists()
        compose.onNodeWithText("#groceries").assertExists()
        compose.onNodeWithText("#archived").assertDoesNotExist()
    }

    @Test
    fun escapedDoubleHashDoesNotOfferSuggestions() {
        compose.setContent {
            MaterialTheme {
                TagAutocompleteField(value = "", tags = tags, onValueChange = {}, onCreateTag = { null })
            }
        }

        compose.onNode(hasSetTextAction()).performTextInput("##school")

        compose.onNodeWithText("#school").assertDoesNotExist()
        compose.onNodeWithText("Create #school").assertDoesNotExist()
    }

    @Test
    fun underscoredTagSuggestionStaysOnOneLine() {
        val underscored = ActualTag("t4", "long_tag_name", "#0080FF", "A lengthy description", hidden = false)
        compose.setContent {
            MaterialTheme {
                TagAutocompleteField(value = "", tags = tags + underscored, onValueChange = {}, onCreateTag = { null })
            }
        }

        compose.onNode(hasSetTextAction()).performTextInput("#long")

        val height = compose.onNodeWithTag("tagSuggestion-t4", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.height
        assertTrue("Underscored tag suggestions must not wrap vertically", height <= with(compose.density) { 48.dp.toPx() })
    }
}
