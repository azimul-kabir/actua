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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
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

    fun isUsable(location: Location, maxAccuracyMeters: Float = DEFAULT_MAX_ACCURACY_METERS): Boolean =
        location.hasAccuracy() && location.accuracy.isFinite() &&
            location.accuracy in 0f..maxAccuracyMeters &&
            LocationUtils.isValid(location.latitude, location.longitude)
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
        timeoutMillis: Long = 10_000L,
        maxAccuracyMeters: Float = LocationSamplePolicy.DEFAULT_MAX_ACCURACY_METERS,
    ): CurrentLocationResult {
        if (!ForegroundLocationPermission.isGranted(appContext)) {
            return CurrentLocationResult.PermissionDenied
        }

        val provider = preferredEnabledProvider() ?: return CurrentLocationResult.ServicesDisabled
        val location = withTimeoutOrNull(timeoutMillis) { requestCurrentLocation(provider) }
            ?: return CurrentLocationResult.Timeout
        location ?: return CurrentLocationResult.Unavailable

        if (!LocationSamplePolicy.isUsable(location, maxAccuracyMeters)) {
            return if (location.hasAccuracy() && location.accuracy.isFinite()) {
                CurrentLocationResult.Inaccurate(location.accuracy)
            } else {
                CurrentLocationResult.Unavailable
            }
        }

        return CurrentLocationResult.Success(
            coordinates = Coordinates(location.latitude, location.longitude),
            accuracyMeters = location.accuracy,
        )
    }

    private fun preferredEnabledProvider(): String? {
        val candidates = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        )
        return candidates.firstOrNull { provider ->
            runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
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
