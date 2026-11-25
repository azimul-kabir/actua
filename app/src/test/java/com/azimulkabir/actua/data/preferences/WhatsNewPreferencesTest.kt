package com.azimulkabir.actua.data.preferences

import com.azimulkabir.actua.data.preferences.WhatsNewPreferences.Companion.shouldShow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewPreferencesTest {

    @Test
    fun freshInstallNeverShowsTheCard() {
        assertFalse(shouldShow(lastSeenVersionCode = null, currentVersionCode = 85, freshInstall = true))
    }

    @Test
    fun updateFromAVersionBeforeTheCardExistedShowsIt() {
        assertTrue(shouldShow(lastSeenVersionCode = null, currentVersionCode = 85, freshInstall = false))
    }

    @Test
    fun newerVersionShowsTheCard() {
        assertTrue(shouldShow(lastSeenVersionCode = 84, currentVersionCode = 85, freshInstall = false))
    }

    @Test
    fun sameVersionNeverShowsTheCardAgain() {
        assertFalse(shouldShow(lastSeenVersionCode = 85, currentVersionCode = 85, freshInstall = false))
    }

    @Test
    fun downgradeDoesNotShowTheCard() {
        assertFalse(shouldShow(lastSeenVersionCode = 86, currentVersionCode = 85, freshInstall = false))
    }

    @Test
    fun assetNamePrefersLanguageAndCountryThenLanguageThenEnglish() {
        val available = setOf("en.txt", "pt-BR.txt", "zh-CN.txt", "fr.txt")
        assertEquals("pt-BR.txt", WhatsNewNotes.assetName("pt", "BR", available))
        assertEquals("zh-CN.txt", WhatsNewNotes.assetName("zh", "CN", available))
        assertEquals("fr.txt", WhatsNewNotes.assetName("fr", "CA", available))
        assertEquals("fr.txt", WhatsNewNotes.assetName("fr", "", available))
        assertEquals("en.txt", WhatsNewNotes.assetName("de", "DE", available))
    }

    @Test
    fun assetNameIsNullWhenNothingIsBundled() {
        assertNull(WhatsNewNotes.assetName("en", "US", emptySet()))
    }

    @Test
    fun parseKeepsOneEntryPerNonBlankLine() {
        assertEquals(listOf("• One", "• Two"), WhatsNewNotes.parse("• One\r\n\n  • Two  \n"))
    }
}
