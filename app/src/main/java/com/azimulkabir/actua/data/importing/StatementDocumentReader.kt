package com.azimulkabir.actua.data.importing

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

object StatementDocumentReader {
    fun read(context: Context, bytes: ByteArray, format: StatementFormat): ImportTable = when (format) {
        StatementFormat.CSV -> CsvTransactionCandidateSource.inspect(bytes.toString(Charsets.UTF_8))
        StatementFormat.XLSX -> XlsxStatementReader.read(bytes)
        StatementFormat.PDF -> {
            PDFBoxResourceLoader.init(context.applicationContext)
            val text = PDDocument.load(bytes).use { PDFTextStripper().getText(it) }
            PdfTextTableParser.parse(text)
        }
    }
}
