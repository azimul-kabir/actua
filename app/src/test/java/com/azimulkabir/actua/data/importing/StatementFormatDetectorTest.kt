package com.azimulkabir.actua.data.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatementFormatDetectorTest {
    @Test
    fun extensionDecidesTheFormatCaseInsensitively() {
        assertEquals(StatementFormat.CSV, StatementFormatDetector.detect("statement.CSV", null))
        assertEquals(StatementFormat.CSV, StatementFormatDetector.detect("export.tsv", null))
        assertEquals(StatementFormat.XLSX, StatementFormatDetector.detect("march.xlsx", null))
        assertEquals(StatementFormat.PDF, StatementFormatDetector.detect("march.pdf", null))
        assertEquals(StatementFormat.OFX, StatementFormatDetector.detect("download.ofx", null))
        assertEquals(StatementFormat.QFX, StatementFormatDetector.detect("download.qfx", null))
        assertEquals(StatementFormat.QIF, StatementFormatDetector.detect("download.qif", null))
        assertEquals(StatementFormat.CAMT053, StatementFormatDetector.detect("camt.xml", null))
    }

    @Test
    fun extensionBeatsAGenericMimeType() {
        assertEquals(
            StatementFormat.QFX,
            StatementFormatDetector.detect("download.qfx", "application/octet-stream"),
        )
        assertEquals(StatementFormat.PDF, StatementFormatDetector.detect("x.pdf", "text/plain"))
    }

    @Test
    fun mimeTypeIsUsedWhenTheNameHasNoKnownExtension() {
        assertEquals(StatementFormat.PDF, StatementFormatDetector.detect("statement", "application/pdf"))
        assertEquals(StatementFormat.CSV, StatementFormatDetector.detect(null, "text/csv; charset=utf-8"))
        assertEquals(StatementFormat.OFX, StatementFormatDetector.detect(null, "application/x-ofx"))
        assertEquals(StatementFormat.QFX, StatementFormatDetector.detect(null, "application/vnd.intu.qfx"))
        assertEquals(StatementFormat.QIF, StatementFormatDetector.detect(null, "application/x-qif"))
        assertEquals(StatementFormat.CAMT053, StatementFormatDetector.detect("download", "application/xml"))
        assertEquals(
            StatementFormat.XLSX,
            StatementFormatDetector.detect(
                "download",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            ),
        )
    }

    @Test
    fun unrecognisedFilesAreNotGuessed() {
        assertNull(StatementFormatDetector.detect("photo.png", "image/png"))
        assertNull(StatementFormatDetector.detect("blob", "application/octet-stream"))
        assertNull(StatementFormatDetector.detect(null, null))
    }
}
