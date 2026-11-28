package com.azimulkabir.actua.ui.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.preferences.BudgetDisplayOptions
import com.azimulkabir.actua.model.BudgetCategory
import com.azimulkabir.actua.model.BudgetGroup
import com.azimulkabir.actua.model.BudgetOverview
import com.azimulkabir.actua.ui.components.CategoryIconEditor
import com.azimulkabir.actua.ui.components.LocalCategoryEmojis
import com.azimulkabir.actua.ui.components.LocalCategoryIconEditor
import com.azimulkabir.actua.ui.components.formatMoneyCents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BudgetCategoryIconsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun categorySlotsFollowTheOptionAndLeaveAmountColumnsInPlace() {
        val options = mutableStateOf(BudgetDisplayOptions(showOverview = false, showGroupTotals = false))
        val icons = mutableStateOf(mapOf("dining" to "🍕"))
        var editedCategory: String? = null
        val groups = listOf(BudgetGroup("Shared expenses", listOf(
            BudgetCategory(name = "Dining", id = "dining", assigned = 87, spent = 13),
            BudgetCategory(name = "Groceries", id = "groceries", assigned = 104, spent = 0),
        )))
        compose.setContent {
            MaterialTheme {
                val editor = CategoryIconEditor(icons.value, set = { id, _ -> editedCategory = id })
                CompositionLocalProvider(
                    LocalCategoryEmojis provides icons.value,
                    LocalCategoryIconEditor provides editor,
                ) {
                    Box(Modifier.requiredWidth(360.dp)) {
                        BudgetScreen(
                            groups = groups,
                            overview = BudgetOverview(0L, 19_100L, 0L, 19_100L),
                            budgetView = "Table",
                            displayOptions = options.value,
                        )
                    }
                }
            }
        }
        val dining = compose.onNodeWithText("Dining", useUnmergedTree = true)
        val groceries = compose.onNodeWithText("Groceries", useUnmergedTree = true)
        val amount = compose.onNodeWithText(formatMoneyCents(8_700L, false), useUnmergedTree = true)
        val originalDining = dining.getUnclippedBoundsInRoot()
        val originalGroceries = groceries.getUnclippedBoundsInRoot()
        val originalAmount = amount.getUnclippedBoundsInRoot()

        compose.runOnIdle { options.value = options.value.copy(showCategoryIcons = true) }
        val offset = dining.getUnclippedBoundsInRoot().left - originalDining.left
        assertTrue("The icon slot should move the name right", offset > 0.dp)
        assertEquals(offset, groceries.getUnclippedBoundsInRoot().left - originalGroceries.left)
        assertEquals(originalAmount.left, amount.getUnclippedBoundsInRoot().left)

        compose.runOnIdle { icons.value = emptyMap() }
        assertEquals(originalDining, dining.getUnclippedBoundsInRoot())
        assertEquals(originalGroceries, groceries.getUnclippedBoundsInRoot())

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        dining.performTouchInput { longClick() }
        compose.onNodeWithText(context.getString(R.string.category_icon)).performClick()
        compose.runOnIdle { assertNull(editedCategory) }
        compose.onNodeWithText("🍕").performClick()
        compose.runOnIdle { assertEquals("dining", editedCategory) }
    }
}
