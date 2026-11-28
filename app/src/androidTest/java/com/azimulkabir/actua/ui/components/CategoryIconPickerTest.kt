package com.azimulkabir.actua.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryIconPickerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun editDialogSavesAndClearsAnIconIndependentlyOfTheName() {
        val key = mutableStateOf<String?>(null)
        var renameCount = 0
        compose.setContent {
            MaterialTheme {
                val editor = CategoryIconEditor(
                    key.value?.let { mapOf("dining" to it.removePrefix("emoji:")) }.orEmpty(),
                    set = { _, newKey -> key.value = newKey },
                )
                CompositionLocalProvider(LocalCategoryIconEditor provides editor) {
                    EditCategoryDialog("Rename category", "Dining", "dining", {}, { renameCount++ })
                }
            }
        }

        fun node(res: Int) = compose.onNodeWithText(
            InstrumentationRegistry.getInstrumentation().targetContext.getString(res),
        )

        node(R.string.category_icon).performClick()
        val field = node(R.string.category_icon_enter_emoji)
        val confirm = node(R.string.category_icon_use_emoji)
        field.performTextReplacement("🍕🍔")
        confirm.assertIsNotEnabled()
        field.performTextReplacement("👩‍💻")
        confirm.assertIsEnabled().performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("emoji:👩‍💻", key.value)
            assertEquals(0, renameCount)
        }

        node(R.string.category_icon).performClick()
        node(R.string.category_icon_none).performClick()
        node(R.string.category_icon_remove).performClick()
        compose.runOnIdle {
            assertEquals(null, key.value)
            assertEquals(0, renameCount)
        }
    }
}
