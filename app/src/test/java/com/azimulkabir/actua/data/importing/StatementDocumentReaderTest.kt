package com.azimulkabir.actua.data.importing

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatementDocumentReaderTest {
    @Test
    fun `reads sparse xlsx cells shared strings and excel dates`() {
        val shared = """<?xml version="1.0"?><sst><si><t>Date</t></si><si><t>Description</t></si><si><t>Amount</t></si><si><t>Cafe</t></si></sst>"""
        val sheet = """<?xml version="1.0"?><worksheet><sheetData>
            <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c></row>
            <row r="2"><c r="A2"><v>46322</v></c><c r="B2" t="s"><v>3</v></c><c r="C2"><v>-4.25</v></c></row>
        </sheetData></worksheet>"""
        val table = XlsxStatementReader.read(xlsx(shared, sheet))
        val result = CsvTransactionCandidateSource.parse(table)
        assertEquals(listOf("Date", "Description", "Amount"), table.headers)
        assertEquals(20261027, result.candidates.single().date)
        assertEquals("Cafe", result.candidates.single().payee)
        assertEquals(-425L, result.candidates.single().amountCents)
    }

    @Test
    fun `finds statement headers after pdf preamble`() {
        val table = PdfTextTableParser.parse(
            "Account statement\nGenerated 13 Sep 2026\nDate  Description  Amount\n2026-09-13  Cafe  -4.25"
        )
        assertEquals(listOf("Date", "Description", "Amount"), table.headers)
        assertEquals("Cafe", CsvTransactionCandidateSource.parse(table).candidates.single().payee)
    }

    @Test
    fun `rejects pdf text without a recognizable table`() {
        val error = runCatching { PdfTextTableParser.parse("Scanned statement") }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("no readable transaction table"))
    }

    private fun xlsx(shared: String, sheet: String): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("xl/sharedStrings.xml")); zip.write(shared.toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml")); zip.write(sheet.toByteArray()); zip.closeEntry()
        }
    }.toByteArray()
}
