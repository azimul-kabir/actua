package com.azimulkabir.actua.ui.transactions

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.budget.model.ActualTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TagAutocompleteFieldTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    /** Labels are read from the resources the field renders with, so the suite tracks translations. */
    private val strings get() = compose.activity

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

        compose.onNodeWithText(strings.getString(R.string.transaction_create_tag, "travel")).assertExists()
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
        compose.onNodeWithText(strings.getString(R.string.transaction_create_tag, "school")).assertDoesNotExist()
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

    @Test
    fun everyTagIsReachableByScrollingTheSuggestionRow() {
        val many = (1..12).map { ActualTag("m$it", "tag%02d".format(it), null, null, hidden = false) }
        var note = ""
        compose.setContent {
            MaterialTheme {
                TagAutocompleteField(value = note, tags = many, onValueChange = { note = it }, onCreateTag = { null })
            }
        }

        compose.onNodeWithText("#").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("#tag12"))
        compose.onNodeWithText("#tag12").assertIsDisplayed().performClick()

        compose.runOnIdle { assertEquals("#tag12 ", note) }
    }
}
