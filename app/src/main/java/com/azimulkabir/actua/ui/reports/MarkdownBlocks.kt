package com.azimulkabir.actua.ui.reports

/**
 * A run of inline Markdown text with its emphasis; [url] is set inside a link and [code] for
 * inline code.
 */
internal data class MarkdownSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strike: Boolean = false,
    val code: Boolean = false,
    val url: String? = null,
)

/** A block of the Markdown dashboard widget, parsed from the subset upstream's card renders. */
internal sealed interface MarkdownBlock {
    data class Heading(val level: Int, val spans: List<MarkdownSpan>) : MarkdownBlock
    /** Lines are joined with `\n`: like upstream's `remark-breaks`, every newline is a line break. */
    data class Paragraph(val spans: List<MarkdownSpan>) : MarkdownBlock
    data class ListItem(val marker: String, val depth: Int, val spans: List<MarkdownSpan>) : MarkdownBlock
    data class Quote(val spans: List<MarkdownSpan>) : MarkdownBlock
    data class Code(val text: String) : MarkdownBlock
    data class TableRow(val cells: List<List<MarkdownSpan>>, val header: Boolean) : MarkdownBlock
    data object Rule : MarkdownBlock
    /** An extra blank line, which upstream's `sequentialNewlinesPlugin` keeps as a break. */
    data object Blank : MarkdownBlock
}

/**
 * Parses the GitHub-flavoured Markdown that Actual's Markdown card renders (`react-markdown` with
 * `remark-gfm` and `remark-breaks`): ATX and setext headings, paragraphs, bullet, numbered and
 * task lists, block quotes, fenced code, tables and rules, with bold, italic, strikethrough, inline
 * code, links and bare URLs inline. Anything else is kept as plain text.
 */
internal object MarkdownBlocks {
    private val heading = Regex("^ {0,3}(#{1,6})(?:\\s+(.*?))?\\s*#*\\s*$")
    private val rule = Regex("^ {0,3}([-*_])(?:\\s*\\1){2,}\\s*$")
    private val listItem = Regex("^(\\s*)([-*+]|\\d{1,9}[.)])\\s+(.*)$")
    private val task = Regex("^\\[([ xX])]\\s+(.*)$")
    private val quote = Regex("^ {0,3}>\\s?(.*)$")
    private val fence = Regex("^ {0,3}(```|~~~)")
    private val tableSeparator = Regex("^\\s*\\|?\\s*:?-+:?\\s*(\\|\\s*:?-+:?\\s*)*\\|?\\s*$")

    fun parse(content: String): List<MarkdownBlock> {
        val lines = content.replace("\r\n", "\n").split('\n')
        val blocks = mutableListOf<MarkdownBlock>()
        val paragraph = mutableListOf<String>()
        val quoteLines = mutableListOf<String>()
        var blankRun = 0

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) blocks += MarkdownBlock.Paragraph(inline(paragraph.joinToString("\n")))
            paragraph.clear()
        }
        fun flushQuote() {
            if (quoteLines.isNotEmpty()) blocks += MarkdownBlock.Quote(inline(quoteLines.joinToString("\n")))
            quoteLines.clear()
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) {
                flushParagraph(); flushQuote()
                // The first blank line only separates blocks; each further one is a visible break.
                if (blocks.isNotEmpty() && blankRun++ > 0) blocks += MarkdownBlock.Blank
                i++; continue
            }
            blankRun = 0
            val fenceMatch = fence.find(line)
            if (fenceMatch != null) {
                flushParagraph(); flushQuote()
                val marker = fenceMatch.groupValues[1]
                val code = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith(marker)) code += lines[i++]
                blocks += MarkdownBlock.Code(code.joinToString("\n"))
                i++; continue
            }
            // Setext headings: a `===` or `---` line under paragraph text.
            if (paragraph.isNotEmpty() && (line.trim().matches(Regex("=+")) || line.trim().matches(Regex("-+")))) {
                val level = if (line.trim().startsWith("=")) 1 else 2
                blocks += MarkdownBlock.Heading(level, inline(paragraph.joinToString("\n")))
                paragraph.clear(); i++; continue
            }
            val headingMatch = heading.find(line)
            if (headingMatch != null) {
                flushParagraph(); flushQuote()
                blocks += MarkdownBlock.Heading(headingMatch.groupValues[1].length, inline(headingMatch.groupValues[2]))
                i++; continue
            }
            if (rule.matches(line)) {
                flushParagraph(); flushQuote()
                blocks += MarkdownBlock.Rule
                i++; continue
            }
            val quoteMatch = quote.find(line)
            if (quoteMatch != null) {
                flushParagraph()
                quoteLines += quoteMatch.groupValues[1]
                i++; continue
            }
            val match = listItem.find(line)
            if (match != null) {
                flushParagraph(); flushQuote()
                val depth = match.groupValues[1].replace("\t", "    ").length / 2
                val rawMarker = match.groupValues[2]
                var text = match.groupValues[3]
                var marker = if (rawMarker.first().isDigit()) rawMarker.dropLast(1) + "." else "•"
                task.find(text)?.let { taskMatch ->
                    marker = if (taskMatch.groupValues[1].isBlank()) "☐" else "☑"
                    text = taskMatch.groupValues[2]
                }
                // Indented continuation lines belong to the item.
                val itemLines = mutableListOf(text)
                while (i + 1 < lines.size && lines[i + 1].isNotBlank() && lines[i + 1].startsWith(" ") &&
                    listItem.find(lines[i + 1]) == null
                ) itemLines += lines[++i].trim()
                blocks += MarkdownBlock.ListItem(marker, depth, inline(itemLines.joinToString("\n")))
                i++; continue
            }
            if ('|' in line && i + 1 < lines.size && tableSeparator.matches(lines[i + 1])) {
                flushParagraph(); flushQuote()
                blocks += MarkdownBlock.TableRow(cells(line), header = true)
                i += 2
                while (i < lines.size && lines[i].isNotBlank() && '|' in lines[i]) {
                    blocks += MarkdownBlock.TableRow(cells(lines[i]), header = false)
                    i++
                }
                continue
            }
            flushQuote()
            paragraph += line.trim()
            i++
        }
        flushParagraph(); flushQuote()
        while (blocks.lastOrNull() == MarkdownBlock.Blank) blocks.removeAt(blocks.lastIndex)
        return blocks
    }

    private fun cells(line: String): List<List<MarkdownSpan>> =
        line.trim().removePrefix("|").removeSuffix("|").split('|').map { inline(it.trim()) }

    /** Inline emphasis, code, links and bare URLs; unmatched delimiters stay literal. */
    fun inline(text: String): List<MarkdownSpan> {
        val spans = mutableListOf<MarkdownSpan>()
        val buffer = StringBuilder()
        var bold = false
        var italic = false
        var strike = false

        fun flush() {
            if (buffer.isNotEmpty()) spans += MarkdownSpan(buffer.toString(), bold, italic, strike)
            buffer.clear()
        }
        fun closes(delimiter: String, from: Int) = text.indexOf(delimiter, from) > from

        var i = 0
        while (i < text.length) {
            val c = text[i]
            val previous = text.getOrNull(i - 1)
            when {
                c == '\\' && i + 1 < text.length && text[i + 1] in "\\`*_{}[]()#+-.!~>|" -> {
                    buffer.append(text[i + 1]); i += 2
                }
                c == '`' && text.indexOf('`', i + 1) > i -> {
                    val end = text.indexOf('`', i + 1)
                    flush()
                    spans += MarkdownSpan(text.substring(i + 1, end), bold, italic, strike, code = true)
                    i = end + 1
                }
                c == '[' && link(text, i) != null -> {
                    val (label, url, end) = link(text, i)!!
                    flush()
                    spans += inline(label).map {
                        it.copy(bold = it.bold || bold, italic = it.italic || italic, strike = it.strike || strike, url = url)
                    }
                    i = end
                }
                (text.startsWith("https://", i) || text.startsWith("http://", i)) &&
                    (previous == null || previous.isWhitespace() || previous == '(') -> {
                    var end = i
                    while (end < text.length && !text[end].isWhitespace() && text[end] != '<') end++
                    while (end > i && text[end - 1] in ".,;:!?)") end--
                    flush()
                    spans += MarkdownSpan(text.substring(i, end), bold, italic, strike, url = text.substring(i, end))
                    i = end
                }
                text.startsWith("**", i) || text.startsWith("__", i) -> {
                    val delimiter = text.substring(i, i + 2)
                    if (bold || closes(delimiter, i + 2)) { flush(); bold = !bold } else buffer.append(delimiter)
                    i += 2
                }
                text.startsWith("~~", i) -> {
                    if (strike || closes("~~", i + 2)) { flush(); strike = !strike } else buffer.append("~~")
                    i += 2
                }
                // `_` inside a word (snake_case) is literal, as in GFM.
                (c == '*' || c == '_') && !(c == '_' && previous?.isLetterOrDigit() == true &&
                    text.getOrNull(i + 1)?.isLetterOrDigit() == true) -> {
                    if (italic || closes(c.toString(), i + 1)) { flush(); italic = !italic } else buffer.append(c)
                    i++
                }
                else -> { buffer.append(c); i++ }
            }
        }
        flush()
        return spans
    }

    /** `[label](url)` starting at [start]: the label, url and the index just past `)`. */
    private fun link(text: String, start: Int): Triple<String, String, Int>? {
        val labelEnd = text.indexOf("](", start + 1).takeIf { it > start } ?: return null
        val urlEnd = text.indexOf(')', labelEnd + 2).takeIf { it > labelEnd + 2 } ?: return null
        val url = text.substring(labelEnd + 2, urlEnd).trim().substringBefore(' ')
        return Triple(text.substring(start + 1, labelEnd), url, urlEnd + 1)
    }
}
