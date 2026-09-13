package com.azimulkabir.actua.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import kotlin.coroutines.resume

sealed interface CurrentLocationResult {
    data class Success(
        val coordinates: Coordinates,
        val accuracyMeters: Float,
    ) : CurrentLocationResult

    data object PermissionDenied : CurrentLocationResult
    data object ServicesDisabled : CurrentLocationResult
    data object Timeout : CurrentLocationResult
    data object Unavailable : CurrentLocationResult
    data class Inaccurate(val accuracyMeters: Float) : CurrentLocationResult
}

object ForegroundLocationPermission {
    val permissions: Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    fun isGranted(context: Context): Boolean =
        permissions.any { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
}

object LocationSamplePolicy {
    const val DEFAULT_MAX_ACCURACY_METERS = 500f

    fun isUsable(
        latitude: Double,
        longitude: Double,
        hasAccuracy: Boolean,
        accuracyMeters: Float,
        maxAccuracyMeters: Float = DEFAULT_MAX_ACCURACY_METERS,
    ): Boolean =
        hasAccuracy && accuracyMeters.isFinite() &&
            accuracyMeters in 0f..maxAccuracyMeters &&
            LocationUtils.isValid(latitude, longitude)

    fun isUsable(location: Location, maxAccuracyMeters: Float = DEFAULT_MAX_ACCURACY_METERS): Boolean =
        isUsable(
            latitude = location.latitude,
            longitude = location.longitude,
            hasAccuracy = location.hasAccuracy(),
            accuracyMeters = location.accuracy,
            maxAccuracyMeters = maxAccuracyMeters,
        )
}

object LocationProviderPolicy {
    const val RECENT_LOCATION_MAX_AGE_MILLIS = 60_000L

    fun activeProviders(enabledProviders: List<String>): List<String> =
        enabledProviders
            .asSequence()
            .filterNot { it == LocationManager.PASSIVE_PROVIDER }
            .distinct()
            .sortedBy { provider ->
                when (provider) {
                    "fused" -> 0
                    LocationManager.NETWORK_PROVIDER -> 1
                    LocationManager.GPS_PROVIDER -> 2
                    else -> 3
                }
            }
            .toList()

    fun isRecent(ageMillis: Long, maxAgeMillis: Long = RECENT_LOCATION_MAX_AGE_MILLIS): Boolean =
        ageMillis in 0..maxAgeMillis
}

/**
 * Foreground-only, one-shot device location access.
 *
 * No background listener is retained and no third-party location service is used. Callers are
 * expected to request foreground permission immediately before invoking this provider when needed.
 */
class AndroidLocationProvider(context: Context) {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    suspend fun currentCoordinates(
        timeoutMillis: Long = 15_000L,
        maxAccuracyMeters: Float = LocationSamplePolicy.DEFAULT_MAX_ACCURACY_METERS,
    ): CurrentLocationResult {
        if (!ForegroundLocationPermission.isGranted(appContext)) {
            return CurrentLocationResult.PermissionDenied
        }

        val enabledProviders = runCatching { locationManager.getProviders(true) }
            .getOrDefault(emptyList())
            .distinct()
        val activeProviders = LocationProviderPolicy.activeProviders(enabledProviders)
        if (activeProviders.isEmpty()) return CurrentLocationResult.ServicesDisabled

        recentCachedLocation(enabledProviders, maxAccuracyMeters)?.let { location ->
            return CurrentLocationResult.Success(
                coordinates = Coordinates(location.latitude, location.longitude),
                accuracyMeters = location.accuracy,
            )
        }

        val acquisition = withTimeoutOrNull(timeoutMillis) {
            requestAnyCurrentLocation(activeProviders, maxAccuracyMeters)
        } ?: return CurrentLocationResult.Timeout

        acquisition.location?.let { location ->
            return CurrentLocationResult.Success(
                coordinates = Coordinates(location.latitude, location.longitude),
                accuracyMeters = location.accuracy,
            )
        }
        return acquisition.bestRejectedAccuracy?.let(CurrentLocationResult::Inaccurate)
            ?: CurrentLocationResult.Unavailable
    }

    @Suppress("MissingPermission")
    private fun recentCachedLocation(
        providers: List<String>,
        maxAccuracyMeters: Float,
    ): Location? {
        val nowElapsedNanos = SystemClock.elapsedRealtimeNanos()
        val nowWallMillis = System.currentTimeMillis()
        return providers
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .filter { LocationSamplePolicy.isUsable(it, maxAccuracyMeters) }
            .map { location ->
                location to locationAgeMillis(location, nowElapsedNanos, nowWallMillis)
            }
            .filter { (_, ageMillis) -> LocationProviderPolicy.isRecent(ageMillis) }
            .minWithOrNull(
                compareBy<Pair<Location, Long>> { it.second }
                    .thenBy { it.first.accuracy },
            )
            ?.first
    }

    private fun locationAgeMillis(
        location: Location,
        nowElapsedNanos: Long,
        nowWallMillis: Long,
    ): Long {
        val sampleElapsedNanos = location.elapsedRealtimeNanos
        if (sampleElapsedNanos > 0L && nowElapsedNanos >= sampleElapsedNanos) {
            return (nowElapsedNanos - sampleElapsedNanos) / 1_000_000L
        }
        return nowWallMillis - location.time
    }

    private data class LocationAcquisition(
        val location: Location?,
        val bestRejectedAccuracy: Float?,
    )

    private suspend fun requestAnyCurrentLocation(
        providers: List<String>,
        maxAccuracyMeters: Float,
    ): LocationAcquisition = coroutineScope {
        val results = Channel<Location?>(Channel.UNLIMITED)
        val requests = providers.map { provider ->
            launch { results.send(requestCurrentLocation(provider)) }
        }
        var bestRejectedAccuracy: Float? = null
        try {
            var remaining = providers.size
            while (remaining > 0) {
                val location = results.receive()
                remaining -= 1
                if (location != null && LocationSamplePolicy.isUsable(location, maxAccuracyMeters)) {
                    return@coroutineScope LocationAcquisition(location, bestRejectedAccuracy)
                }
                if (location?.hasAccuracy() == true && location.accuracy.isFinite()) {
                    bestRejectedAccuracy = minOf(
                        bestRejectedAccuracy ?: Float.POSITIVE_INFINITY,
                        location.accuracy,
                    )
                }
            }
            LocationAcquisition(null, bestRejectedAccuracy)
        } finally {
            requests.forEach { it.cancel() }
            results.cancel()
        }
    }

    @Suppress("MissingPermission")
    private suspend fun requestCurrentLocation(provider: String): Location? = withContext(Dispatchers.Main.immediate) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            suspendCancellableCoroutine { continuation ->
                val cancellationSignal = CancellationSignal()
                continuation.invokeOnCancellation { cancellationSignal.cancel() }
                val executor = Executor { command -> command.run() }
                runCatching {
                    locationManager.getCurrentLocation(provider, cancellationSignal, executor) { location ->
                        if (continuation.isActive) continuation.resume(location)
                    }
                }.onFailure {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        } else {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        locationManager.removeUpdates(this)
                        if (continuation.isActive) continuation.resume(location)
                    }

                    @Deprecated("Deprecated in Android")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) {
                        locationManager.removeUpdates(this)
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
                continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }
                runCatching {
                    locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }.onFailure {
                    locationManager.removeUpdates(listener)
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
    }
}
