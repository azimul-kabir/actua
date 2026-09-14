package com.azimulkabir.actua.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagTextTest {
    @Test
    fun `finds Actual tags and skips escaped hashes`() {
        assertEquals(
            listOf(
                TagOccurrence("work", 6, 11),
                TagOccurrence("Home", 22, 27),
            ),
            findTagOccurrences("Lunch #work ##escaped #Home"),
        )
    }

    @Test
    fun `tag identity remains case sensitive`() {
        val colors = mapOf("Home" to "#336699")
        val occurrences = findTagOccurrences("#home #Home")
        assertNull(colors[occurrences[0].name])
        assertEquals("#336699", colors[occurrences[1].name])
    }

    @Test
    fun `valid hex colors parse and malformed values fall back`() {
        assertEquals(Color(0xFF336699), parseTagColor("#336699"))
        assertEquals(Color(0x80336699), parseTagColor("#80336699"))
        assertNull(parseTagColor(null))
        assertNull(parseTagColor(""))
        assertNull(parseTagColor("336699"))
        assertNull(parseTagColor("#xyzxyz"))
    }

    @Test
    fun `mixed note becomes plain and recognized tag segments without changing text`() {
        val notes = "Home-School-Office #school #unknown ##escaped #Adeeba"
        val segments = tagSegments(
            notes = notes,
            tagColors = mapOf(
                "school" to "#F2B632",
                "Adeeba" to "#6A1B9A",
            ),
        )

        assertEquals(notes, segments.joinToString("") { it.text })
        val tags = segments.filterIsInstance<TagNoteSegment.Tag>()
        assertEquals(listOf("school", "Adeeba"), tags.map { it.name })
        assertEquals(Color(0xFFF2B632), tags[0].color)
        assertEquals(Color(0xFF6A1B9A), tags[1].color)
        assertTrue(segments.filterIsInstance<TagNoteSegment.Plain>().any { "#unknown" in it.text })
        assertTrue(segments.filterIsInstance<TagNoteSegment.Plain>().any { "##escaped" in it.text })
    }

    @Test
    fun `unknown and malformed tag metadata stays plain text`() {
        val notes = "#unknown #broken"
        val segments = tagSegments(notes, mapOf("broken" to "not-a-color"))

        assertEquals(listOf(TagNoteSegment.Plain(notes)), segments)
    }

    @Test
    fun `adjacent recognized tags remain distinct chip segments`() {
        val segments = tagSegments(
            "#one#two",
            mapOf("one" to "#336699", "two" to "#CC5500"),
        )

        assertEquals(2, segments.size)
        assertTrue(segments.all { it is TagNoteSegment.Tag })
        assertEquals("#one#two", segments.joinToString("") { it.text })
    }

    @Test
    fun `chip colors stay tinted and foreground adapts for theme contrast`() {
        val yellow = Color(0xFFF1C40F)
        val purple = Color(0xFF3F176D)

        assertEquals(0.14f, tagChipBackground(yellow, darkTheme = false).alpha, 0.0001f)
        assertEquals(0.24f, tagChipBackground(purple, darkTheme = true).alpha, 0.0001f)
        assertTrue(tagChipForeground(yellow, darkTheme = false).luminance() < yellow.luminance())
        assertTrue(tagChipForeground(purple, darkTheme = true).luminance() > purple.luminance())
        assertFalse(tagChipForeground(yellow, darkTheme = false) == yellow)
        assertFalse(tagChipForeground(purple, darkTheme = true) == purple)
    }
}
