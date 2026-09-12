package com.azimulkabir.actua.data.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationUtilsTest {
    @Test fun `haversine matches known distances`() {
        val landmarks = LocationUtils.distanceMeters(
            Coordinates(-33.8568, 151.2153), Coordinates(-33.8523, 151.2108),
        )
        assertEquals(650.42, landmarks, 1.0)
        assertEquals(111_194.93, LocationUtils.distanceMeters(Coordinates(0.0, 0.0), Coordinates(1.0, 0.0)), 1.0)
        assertEquals(0.0, LocationUtils.distanceMeters(Coordinates(10.0, 10.0), Coordinates(10.0, 10.0)), 0.0)
    }

    @Test fun `validates upstream coordinate ranges`() {
        assertTrue(LocationUtils.isValid(-90.0, 180.0))
        assertFalse(LocationUtils.isValid(90.1, 0.0))
        assertFalse(LocationUtils.isValid(0.0, Double.NaN))
    }

    @Test fun `deduplicates same payee locations within 500 metres`() {
        val here = Coordinates(0.0, 0.0)
        val near = PayeeLocation("near", "p", 0.001, 0.0, 1)
        val far = PayeeLocation("far", "p", 0.04, 0.0, 2)
        assertTrue(LocationUtils.shouldRecord(here, emptyList()))
        assertTrue(LocationUtils.shouldRecord(here, listOf(far)))
        assertFalse(LocationUtils.shouldRecord(here, listOf(near, far)))
    }

    @Test fun `formats distance like Actual`() {
        assertEquals("328ft | 100m", LocationUtils.formatDistance(100.0))
    }
}
