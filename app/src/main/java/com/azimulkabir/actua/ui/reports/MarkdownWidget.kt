package com.azimulkabir.actua.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Upstream's `MarkdownCard`: renders the widget's Markdown with the app's text styles (no WebView)
 * and its `text_align` (`left`, `center` or `right`). Links open in the browser.
 */
@Composable
internal fun MarkdownWidget(content: String, textAlign: String?) {
    val blocks = remember(content) { MarkdownBlocks.parse(content) }
    val align = when (textAlign) {
        "center" -> TextAlign.Center
        "right" -> TextAlign.End
        else -> TextAlign.Start
    }
    val body = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = align)
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    fun annotated(spans: List<MarkdownSpan>): AnnotatedString = buildAnnotatedString {
        spans.forEach { span ->
            val style = SpanStyle(
                fontWeight = if (span.bold) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                textDecoration = if (span.strike) TextDecoration.LineThrough else null,
                fontFamily = if (span.code) FontFamily.Monospace else null,
                background = if (span.code) codeBackground else androidx.compose.ui.graphics.Color.Unspecified,
            )
            if (span.url != null) {
                withLink(LinkAnnotation.Url(span.url, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)))) {
                    withStyle(style) { append(span.text) }
                }
            } else {
                withStyle(style) { append(span.text) }
            }
        }
    }
    if (blocks.isEmpty()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> Text(
                    annotated(block.spans),
                    Modifier.fillMaxWidth().semantics { heading() },
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = align),
                )
                is MarkdownBlock.Paragraph -> Text(annotated(block.spans), Modifier.fillMaxWidth(), style = body)
                is MarkdownBlock.ListItem -> Row(Modifier.fillMaxWidth().padding(start = (block.depth * 16).dp)) {
                    Text(block.marker, style = body.copy(textAlign = TextAlign.Start))
                    Spacer(Modifier.width(6.dp))
                    Text(annotated(block.spans), Modifier.weight(1f), style = body)
                }
                is MarkdownBlock.Quote -> Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                    Spacer(Modifier.width(10.dp))
                    Text(annotated(block.spans), Modifier.weight(1f), style = body.copy(fontStyle = FontStyle.Italic))
                }
                is MarkdownBlock.Code -> Text(
                    block.text,
                    Modifier.fillMaxWidth().background(codeBackground, MaterialTheme.shapes.small).padding(8.dp),
                    style = body.copy(fontFamily = FontFamily.Monospace, textAlign = TextAlign.Start),
                )
                is MarkdownBlock.TableRow -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    block.cells.forEach { cell ->
                        Text(
                            annotated(cell), Modifier.weight(1f),
                            style = if (block.header) body.copy(fontWeight = FontWeight.Bold) else body,
                        )
                    }
                }
                MarkdownBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = 4.dp))
                MarkdownBlock.Blank -> Spacer(Modifier.height(8.dp))
            }
        }
    }
}
