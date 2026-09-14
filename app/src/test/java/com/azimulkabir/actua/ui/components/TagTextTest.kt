package com.azimulkabir.actua.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
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
    fun `foreground remains readable for light and dark colors`() {
        assertEquals(Color.Black, tagForeground(Color.White))
        assertEquals(Color.White, tagForeground(Color.Black))
        assertTrue(tagForeground(Color(0xFFF1C40F)) == Color.Black)
    }

    @Test
    fun `colored tag text preserves note and styles only recognized tags`() {
        val result = coloredTagText(
            notes = "Home-School-Office #school #unknown",
            tagColors = mapOf("school" to "#6A1B9A"),
        )

        assertEquals("Home-School-Office #school #unknown", result.text)
        assertEquals(1, result.spanStyles.size)
        val styledTag = result.spanStyles.single()
        assertEquals(19, styledTag.start)
        assertEquals(26, styledTag.end)
        assertEquals(Color(0xFF6A1B9A), styledTag.item.background)
        assertEquals(Color.White, styledTag.item.color)
    }
}
