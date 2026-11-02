package com.azimulkabir.actua.ui.components

import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroAmountFontSizeTest {
    @Test fun shortAmountsKeepTheStyleSize() {
        assertNull(heroAmountFontSize(11, ActuaHeroSize.Large))
        assertNull(heroAmountFontSize(11, ActuaHeroSize.Medium))
    }

    @Test fun longerAmountsStepDownInsteadOfStayingAtOneSize() {
        assertEquals(32.sp, heroAmountFontSize(12, ActuaHeroSize.Large))
        assertEquals(26.sp, heroAmountFontSize(18, ActuaHeroSize.Large))
        assertEquals(22.sp, heroAmountFontSize(19, ActuaHeroSize.Large))
        assertEquals(18.sp, heroAmountFontSize(40, ActuaHeroSize.Large))
        assertEquals(22.sp, heroAmountFontSize(18, ActuaHeroSize.Medium))
    }

    @Test fun sizeNeverGrowsAsTheTextGetsLonger() {
        for (size in ActuaHeroSize.entries) {
            var previous = Float.MAX_VALUE
            for (length in 1..60) {
                val current = heroAmountFontSize(length, size)?.value ?: Float.MAX_VALUE
                assertTrue("$size at $length", current <= previous)
                previous = current
            }
        }
    }
}
