package com.azimulkabir.actua.data.importing

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

object XlsxStatementReader {
    fun read(bytes: ByteArray): ImportTable {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name in setOf("xl/sharedStrings.xml", "xl/worksheets/sheet1.xml")) {
                    entries[entry.name] = zip.readLimited(10 * 1024 * 1024)
                }
            }
        }
        val shared = entries["xl/sharedStrings.xml"]?.let(::parseXml)?.getElementsByTagName("si")?.let { nodes ->
            (0 until nodes.length).map { nodes.item(it).textContent }
        }.orEmpty()
        val sheet = entries["xl/worksheets/sheet1.xml"] ?: error("The workbook has no readable first worksheet")
        val nodes = parseXml(sheet).getElementsByTagName("row")
        val rows = (0 until nodes.length).map { rowIndex ->
            val cells = (nodes.item(rowIndex) as Element).getElementsByTagName("c")
            val values = mutableMapOf<Int, String>()
            for (index in 0 until cells.length) {
                val cell = cells.item(index) as Element
                val column = columnIndex(cell.getAttribute("r"))
                val raw = when (cell.getAttribute("t")) {
                    "inlineStr" -> cell.getElementsByTagName("is").item(0)?.textContent.orEmpty()
                    else -> cell.getElementsByTagName("v").item(0)?.textContent.orEmpty()
                }
                values[column] = if (cell.getAttribute("t") == "s") {
                    shared.getOrNull(raw.toIntOrNull() ?: -1).orEmpty()
                } else raw
            }
            (0..(values.keys.maxOrNull() ?: -1)).map { values[it].orEmpty() }
        }
        return ImportTableDetector.detect(rows)
    }

    private fun columnIndex(reference: String): Int = reference.takeWhile(Char::isLetter)
        .fold(0) { value, char -> value * 26 + (char.uppercaseChar() - 'A' + 1) }.minus(1).coerceAtLeast(0)

    private fun parseXml(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        isXIncludeAware = false
        isExpandEntityReferences = false
    }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    private fun InputStream.readLimited(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(output.size() + count <= maxBytes) { "The workbook worksheet is too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}

object ImportTableDetector {
    fun detect(rows: List<List<String>>): ImportTable {
        require(rows.isNotEmpty()) { "The statement is empty" }
        val headerIndex = rows.take(25).indices.maxByOrNull { index ->
            CsvTransactionCandidateSource.suggestedMapping(rows[index]).roles.count { it != ImportColumnRole.IGNORE }
        } ?: 0
        val headers = rows[headerIndex]
        val recognized = CsvTransactionCandidateSource.suggestedMapping(headers).roles.count { it != ImportColumnRole.IGNORE }
        require(recognized >= 2) { "Could not identify a statement header row" }
        return ImportTable(headers, rows.drop(headerIndex + 1))
    }
}

object PdfTextTableParser {
    fun parse(text: String): ImportTable {
        val rows = text.lineSequence().map(String::trim).filter(String::isNotBlank).map { line ->
            when {
                '|' in line -> line.split('|').map(String::trim)
                '\t' in line -> line.split('\t').map(String::trim)
                else -> line.split(Regex(" {2,}")).map(String::trim)
            }
        }.filter { it.size > 1 }.toList()
        require(rows.isNotEmpty()) {
            "This PDF has no readable transaction table. Use CSV/XLSX for scanned or image-only statements."
        }
        return ImportTableDetector.detect(rows)
    }
}
