package com.azimulkabir.actua.data.importing

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

sealed interface StatementReadResult {
    data class Table(val table: ImportTable) : StatementReadResult
    data class Candidates(val parseResult: ImportParseResult) : StatementReadResult
}

object StatementDocumentReader {
    fun read(context: Context, bytes: ByteArray, format: StatementFormat): StatementReadResult = when (format) {
        StatementFormat.CSV -> StatementReadResult.Table(CsvTransactionCandidateSource.inspect(bytes.toString(Charsets.UTF_8)))
        StatementFormat.XLSX -> StatementReadResult.Table(XlsxStatementReader.read(bytes))
        StatementFormat.PDF -> {
            PDFBoxResourceLoader.init(context.applicationContext)
            val text = PDDocument.load(bytes).use { PDFTextStripper().getText(it) }
            StatementReadResult.Table(PdfTextTableParser.parse(text))
        }
        StatementFormat.OFX, StatementFormat.QFX -> StatementReadResult.Candidates(OfxStatementParser.parse(bytes))
        StatementFormat.QIF -> StatementReadResult.Candidates(QifStatementParser.parse(bytes))
        StatementFormat.CAMT053 -> StatementReadResult.Candidates(Camt053StatementParser.parse(bytes))
        StatementFormat.SHARED_TEXT, StatementFormat.NOTIFICATION ->
            error("Message imports do not use the document reader")
    }

    fun readTable(context: Context, bytes: ByteArray, format: StatementFormat): ImportTable =
        when (val result = read(context, bytes, format)) {
            is StatementReadResult.Table -> result.table
            is StatementReadResult.Candidates -> error("Format $format produces direct candidates, not a table")
        }
}
