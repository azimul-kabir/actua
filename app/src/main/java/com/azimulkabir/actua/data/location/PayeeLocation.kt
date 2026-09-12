package com.azimulkabir.actua.data.location

import com.azimulkabir.actua.data.budget.model.ActualPayee
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class Coordinates(val latitude: Double, val longitude: Double) {
    init { require(LocationUtils.isValid(this)) { "Invalid latitude or longitude" } }
}

data class PayeeLocation(
    val id: String,
    val payeeId: String,
    val latitude: Double,
    val longitude: Double,
    val createdAt: Long,
    val tombstone: Boolean = false,
)

data class NearbyPayee(
    val payee: ActualPayee,
    val location: PayeeLocation,
    val distanceMeters: Double,
)

object LocationUtils {
    const val DEFAULT_MAX_DISTANCE_METERS = 500.0
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun isValid(coordinates: Coordinates) = isValid(coordinates.latitude, coordinates.longitude)

    fun isValid(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

    fun distanceMeters(first: Coordinates, second: Coordinates): Double {
        val phi1 = Math.toRadians(first.latitude)
        val phi2 = Math.toRadians(second.latitude)
        val deltaPhi = Math.toRadians(second.latitude - first.latitude)
        val deltaLambda = Math.toRadians(second.longitude - first.longitude)
        val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) +
            cos(phi1) * cos(phi2) * sin(deltaLambda / 2) * sin(deltaLambda / 2)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun formatDistance(meters: Double): String =
        "${(meters * 3.28084).roundToInt()}ft | ${meters.roundToInt()}m"

    fun shouldRecord(
        coordinates: Coordinates,
        existing: List<PayeeLocation>,
        dedupeDistanceMeters: Double = DEFAULT_MAX_DISTANCE_METERS,
    ): Boolean = existing.none { location ->
        isValid(location.latitude, location.longitude) && distanceMeters(
            coordinates, Coordinates(location.latitude, location.longitude),
        ) <= dedupeDistanceMeters
    }
}
