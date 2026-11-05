package com.azimulkabir.actua.data.receipts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptPhotoSizingTest {
    @Test fun aSmallImageIsDecodedWholeAndNotScaled() {
        assertEquals(1, ReceiptPhotoSizing.sampleSize(1200, 900))
        assertEquals(1200 to 900, ReceiptPhotoSizing.fitWithin(1200, 900))
    }

    @Test fun theSampleSizeKeepsTheLongestEdgeAtLeastTheCap() {
        assertEquals(1, ReceiptPhotoSizing.sampleSize(3199, 2000))
        assertEquals(2, ReceiptPhotoSizing.sampleSize(3200, 2400))
        assertEquals(4, ReceiptPhotoSizing.sampleSize(6400, 4800))
        assertEquals(4, ReceiptPhotoSizing.sampleSize(4000, 8000))
    }

    @Test fun aLargeImageIsScaledDownKeepingItsRatio() {
        assertEquals(1600 to 1200, ReceiptPhotoSizing.fitWithin(4000, 3000))
        assertEquals(900 to 1600, ReceiptPhotoSizing.fitWithin(1800, 3200))
    }

    @Test fun aVeryThinImageNeverCollapsesToZero() {
        assertEquals(1600 to 1, ReceiptPhotoSizing.fitWithin(100_000, 10))
    }

    @Test fun anImageExactlyAtTheCapIsNotScaled() {
        assertEquals(1600 to 800, ReceiptPhotoSizing.fitWithin(1600, 800))
    }

    @Test fun aHugeCameraImageIsSampledDownForRecognitionButKeepsDetail() {
        val sample = ReceiptPhotoSizing.sampleSize(8160, 6120, ReceiptPhotoSizing.RECOGNITION_EDGE)
        assertEquals(2, sample)
        val (width, height) = ReceiptPhotoSizing.fitWithin(8160 / sample, 6120 / sample, ReceiptPhotoSizing.RECOGNITION_EDGE)
        assertEquals(2400, maxOf(width, height))
    }

    @Test fun theRecognitionEdgeIsLargerThanTheKeptPhotoEdge() {
        assertTrue(ReceiptPhotoSizing.RECOGNITION_EDGE > ReceiptPhotoSizing.MAX_EDGE)
    }
}
