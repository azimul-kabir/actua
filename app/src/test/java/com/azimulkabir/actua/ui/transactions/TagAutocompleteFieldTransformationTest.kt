package com.azimulkabir.actua.ui.transactions

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagAutocompleteFieldTransformationTest {
    @Test fun `recognized tags are styled without changing the displayed text`() {
        val transformation = tagHighlightTransformation(mapOf("school" to "#800080"), darkTheme = false)

        val transformed = transformation.filter(AnnotatedString("Pickup #school today"))

        assertEquals("Pickup #school today", transformed.text.text)
        val spans = transformed.text.spanStyles
        assertEquals(1, spans.size)
        assertEquals(7, spans[0].start)
        assertEquals(14, spans[0].end)
    }

    @Test fun `unrecognized and escaped tokens are left unstyled`() {
        val transformation = tagHighlightTransformation(mapOf("school" to "#800080"), darkTheme = false)

        val transformed = transformation.filter(AnnotatedString("#unknown ##escaped"))

        assertTrue(transformed.text.spanStyles.isEmpty())
    }

    @Test fun `offset mapping is identity since styling never changes text length`() {
        val transformation = tagHighlightTransformation(mapOf("school" to "#800080"), darkTheme = false)

        val transformed = transformation.filter(AnnotatedString("Pickup #school today"))

        assertEquals(5, transformed.offsetMapping.originalToTransformed(5))
        assertEquals(5, transformed.offsetMapping.transformedToOriginal(5))
    }

    @Test fun `styled color matches the same theme-aware contrast used after saving`() {
        val purple = Color(0xFF800080)
        val transformation = tagHighlightTransformation(mapOf("school" to "#800080"), darkTheme = true)

        val transformed = transformation.filter(AnnotatedString("#school"))

        val style = transformed.text.spanStyles.single().item
        assertTrue(style.color != purple)
    }

    @Test fun `recognized tags get a pill background matching the post-save chip`() {
        val transformation = tagHighlightTransformation(mapOf("school" to "#800080"), darkTheme = false)

        val transformed = transformation.filter(AnnotatedString("#school"))

        val style = transformed.text.spanStyles.single().item
        assertTrue(style.background != Color.Unspecified)
    }
}
