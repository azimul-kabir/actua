package com.azimulkabir.actua.data.importing

/**
 * Chooses a [StatementFormat] for a file from its display name and MIME type.
 *
 * The extension wins because banking apps and mail clients often label statements
 * `application/octet-stream`; the MIME type is the fallback for files shared without a usable name.
 */
object StatementFormatDetector {
    fun detect(displayName: String?, mimeType: String?): StatementFormat? =
        byExtension(displayName) ?: byMimeType(mimeType)

    private fun byExtension(displayName: String?): StatementFormat? =
        when (displayName?.substringAfterLast('.', "")?.lowercase()) {
            "csv", "tsv", "txt" -> StatementFormat.CSV
            "xlsx" -> StatementFormat.XLSX
            "pdf" -> StatementFormat.PDF
            "ofx" -> StatementFormat.OFX
            "qfx" -> StatementFormat.QFX
            "qif" -> StatementFormat.QIF
            "xml" -> StatementFormat.CAMT053
            else -> null
        }

    private fun byMimeType(mimeType: String?): StatementFormat? =
        when (mimeType?.substringBefore(';')?.trim()?.lowercase()) {
            "text/csv", "text/comma-separated-values", "text/tab-separated-values", "application/csv",
            // Excel labels CSV exports this way; a real .xls fails to parse with a clear message.
            "application/vnd.ms-excel", "text/plain" -> StatementFormat.CSV
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> StatementFormat.XLSX
            "application/pdf" -> StatementFormat.PDF
            "application/x-ofx", "application/ofx" -> StatementFormat.OFX
            "application/vnd.intu.qfx" -> StatementFormat.QFX
            "application/x-qif", "application/qif" -> StatementFormat.QIF
            "application/xml", "text/xml" -> StatementFormat.CAMT053
            else -> null
        }
}
