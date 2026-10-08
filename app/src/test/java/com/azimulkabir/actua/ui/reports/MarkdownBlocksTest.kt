package com.azimulkabir.actua.ui.reports

import org.junit.Assert.assertEquals
import org.junit.Test

/** The Markdown subset upstream's `MarkdownCard` renders (`remark-gfm`, `remark-breaks`), actua#957. */
class MarkdownBlocksTest {
    private fun text(spans: List<MarkdownSpan>) = spans.joinToString("") { it.text }

    @Test fun `the PWA's default widget content renders a heading and bold text`() {
        val blocks = MarkdownBlocks.parse("### Text Widget\n\nEdit this widget to change the **markdown** content.")
        val heading = blocks[0] as MarkdownBlock.Heading
        assertEquals(3, heading.level)
        assertEquals("Text Widget", text(heading.spans))
        val paragraph = blocks[1] as MarkdownBlock.Paragraph
        assertEquals("Edit this widget to change the markdown content.", text(paragraph.spans))
        assertEquals(listOf(false, true, false), paragraph.spans.map { it.bold })
        assertEquals(2, blocks.size)
    }

    @Test fun `every newline inside a paragraph is a line break`() {
        val paragraph = MarkdownBlocks.parse("first\nsecond").single() as MarkdownBlock.Paragraph
        assertEquals("first\nsecond", text(paragraph.spans))
    }

    @Test fun `extra blank lines are kept as breaks`() {
        assertEquals(
            listOf("Paragraph", "Blank", "Paragraph"),
            MarkdownBlocks.parse("a\n\n\nb").map { it::class.simpleName },
        )
    }

    @Test fun `lists, tasks, quotes, rules and code`() {
        val blocks = MarkdownBlocks.parse("- one\n  - nested\n1. first\n- [x] done\n> quoted\n---\n```\nval x = 1\n```")
        assertEquals(MarkdownBlock.ListItem("•", 0, listOf(MarkdownSpan("one"))), blocks[0])
        assertEquals(1, (blocks[1] as MarkdownBlock.ListItem).depth)
        assertEquals("1.", (blocks[2] as MarkdownBlock.ListItem).marker)
        assertEquals("☑", (blocks[3] as MarkdownBlock.ListItem).marker)
        assertEquals("quoted", text((blocks[4] as MarkdownBlock.Quote).spans))
        assertEquals(MarkdownBlock.Rule, blocks[5])
        assertEquals(MarkdownBlock.Code("val x = 1"), blocks[6])
    }

    @Test fun `setext headings and tables`() {
        val blocks = MarkdownBlocks.parse("Title\n===\n| A | B |\n|---|:-:|\n| 1 | 2 |")
        assertEquals(1, (blocks[0] as MarkdownBlock.Heading).level)
        val header = blocks[1] as MarkdownBlock.TableRow
        assertEquals(true, header.header)
        assertEquals(listOf("A", "B"), header.cells.map(::text))
        assertEquals(listOf("1", "2"), (blocks[2] as MarkdownBlock.TableRow).cells.map(::text))
    }

    @Test fun `inline emphasis, code, links and bare urls`() {
        val spans = MarkdownBlocks.inline("*it* ~~gone~~ `code` [site](https://example.com) see https://actualbudget.org.")
        assertEquals(true, spans.first { it.text == "it" }.italic)
        assertEquals(true, spans.first { it.text == "gone" }.strike)
        assertEquals(true, spans.first { it.text == "code" }.code)
        assertEquals("https://example.com", spans.first { it.text == "site" }.url)
        assertEquals("https://actualbudget.org", spans.first { it.text == "https://actualbudget.org" }.url)
        assertEquals(".", spans.last().text)
    }

    @Test fun `unmatched delimiters and snake_case stay literal`() {
        assertEquals("2 * 3 and snake_case_name", text(MarkdownBlocks.inline("2 * 3 and snake_case_name")))
        assertEquals("**not bold", text(MarkdownBlocks.inline("**not bold")))
        assertEquals("*escaped*", text(MarkdownBlocks.inline("\\*escaped\\*")))
    }
}
