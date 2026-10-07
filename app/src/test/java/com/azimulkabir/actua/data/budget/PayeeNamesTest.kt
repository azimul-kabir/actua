package com.azimulkabir.actua.data.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Actual's `UNICODE_LOWER` uses JavaScript `toLowerCase()` (#898). */
class PayeeNamesTest {
    @Test fun foldsNonAsciiCaseLikeJavaScript() {
        assertEquals("café", PayeeNames.unicodeLower("CAFÉ"))
        assertEquals("ärzte", PayeeNames.unicodeLower("ÄRZTE"))
        assertEquals("магазин", PayeeNames.unicodeLower("МАГАЗИН"))
    }

    @Test fun ignoresTheDeviceLocale() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"))
            // Turkish lower-casing would give a dotless ı; JavaScript's toLowerCase() doesn't.
            assertEquals("istanbul", PayeeNames.unicodeLower("ISTANBUL"))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test fun keepsAccentsDistinct() {
        // Upstream lower-cases only; it doesn't strip diacritics.
        assertNotEquals(PayeeNames.unicodeLower("Cafe"), PayeeNames.unicodeLower("Café"))
    }
}
