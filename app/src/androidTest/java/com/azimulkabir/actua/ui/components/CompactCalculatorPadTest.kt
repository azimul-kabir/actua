package com.azimulkabir.actua.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The amount keypad's × ÷ = keys and five-column layout (#1028). */
@RunWith(AndroidJUnit4::class)
class CompactCalculatorPadTest {
    @get:Rule val compose = createComposeRule()

    @Test fun multiplyDivideAndEqualsWorkFromTheKeys() {
        val calculator = CalculatorAmountState()
        var value = 0L
        var done = 0
        compose.setContent {
            MaterialTheme {
                CompactCalculatorPad(calculator = calculator, showDisplay = false,
                    onValueChange = { value = it }, onDone = { done++ })
            }
        }
        // 12.00 × 1.5 = 18.00, then ÷ 4 = 4.50
        listOf("1", "2", "0", "0", "×", "1", ".", "5", "=", "÷", "4").forEach {
            compose.onNodeWithText(it).performClick()
        }
        compose.runOnIdle { assertEquals(450L, value) }
        compose.onNodeWithContentDescription("Equals").performClick()
        compose.runOnIdle {
            assertEquals(450L, value)
            assertEquals(0, done)
        }
        compose.onNodeWithText("✓").performClick()
        compose.runOnIdle { assertEquals(1, done) }
    }

    @Test fun doneSpansTwoColumnsAlignedWithTheKeysAbove() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(360.dp)) {
                    CompactCalculatorPad(calculator = CalculatorAmountState(), showDisplay = false, onDone = {})
                }
            }
        }
        val multiply = compose.onNodeWithText("×").getBoundsInRoot()
        val divide = compose.onNodeWithText("÷").getBoundsInRoot()
        val done = compose.onNodeWithText("✓").getBoundsInRoot()
        assertEquals(multiply.left.value, done.left.value, 0.5f)
        assertEquals(divide.right.value, done.right.value, 0.5f)
    }
}
