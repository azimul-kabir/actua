package com.azimulkabir.actua.data.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationSamplePolicyTest {
    @Test fun acceptsValidAccurateSample() {
        assertTrue(LocationSamplePolicy.isUsable(23.8103, 90.4125, true, 25f))
    }

    @Test fun rejectsSampleOutsideAccuracyLimit() {
        assertFalse(LocationSamplePolicy.isUsable(23.8103, 90.4125, true, 501f))
    }

    @Test fun rejectsSampleWithoutAccuracy() {
        assertFalse(LocationSamplePolicy.isUsable(23.8103, 90.4125, false, 25f))
    }

    @Test fun rejectsInvalidCoordinatesAndAccuracy() {
        assertFalse(LocationSamplePolicy.isUsable(91.0, 90.4125, true, 25f))
        assertFalse(LocationSamplePolicy.isUsable(23.8103, 181.0, true, 25f))
        assertFalse(LocationSamplePolicy.isUsable(23.8103, 90.4125, true, Float.NaN))
    }
}
