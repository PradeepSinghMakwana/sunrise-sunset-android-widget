package com.sunrisesunset.clock

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Small adapter boundary so provider errors, callbacks, and cancellation can be tested
 * deterministically without depending on physical location hardware.
 */
interface LocationGateway {
    fun isProviderEnabled(provider: String): Boolean
    fun getLastKnownLocation(provider: String): Location?
    fun requestLocationUpdates(provider: String, listener: LocationListener)
    fun removeUpdates(listener: LocationListener)
}

@SuppressLint("MissingPermission")
private class AndroidLocationGateway(context: Context) : LocationGateway {
    private val manager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    override fun isProviderEnabled(provider: String): Boolean =
        manager.isProviderEnabled(provider)

    override fun getLastKnownLocation(provider: String): Location? =
        manager.getLastKnownLocation(provider)

    override fun requestLocationUpdates(provider: String, listener: LocationListener) {
        manager.requestLocationUpdates(provider, 0L, 0f, listener)
    }

    override fun removeUpdates(listener: LocationListener) {
        manager.removeUpdates(listener)
    }
}

class LocationRepository(
    private val context: Context,
    private val locationGateway: LocationGateway = AndroidLocationGateway(context)
) {
    suspend fun hasSavedLocation(): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(LocationPreferences.PREFS, Context.MODE_PRIVATE)
        val latitude = prefs.getString(LocationPreferences.LATITUDE, null)?.toDoubleOrNull()
        val longitude = prefs.getString(LocationPreferences.LONGITUDE, null)?.toDoubleOrNull()
        latitude != null && longitude != null &&
            SolarCalculator.hasValidCoordinates(latitude, longitude)
    }

    suspend fun findLocation(): Location? {
        val cached = findCachedLocation()
        if (cached != null) {
            return cached
        }
        return requestNetworkLocation()
    }

    private suspend fun findCachedLocation(): Location? = withContext(Dispatchers.IO) {
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .firstNotNullOfOrNull { provider ->
                try {
                    if (locationGateway.isProviderEnabled(provider)) {
                        locationGateway.getLastKnownLocation(provider)?.takeIf {
                            SolarCalculator.hasValidCoordinates(it.latitude, it.longitude)
                        }
                    } else {
                        null
                    }
                } catch (_: SecurityException) {
                    null
                } catch (_: RuntimeException) {
                    null
                }
            }
    }

    private suspend fun requestNetworkLocation(): Location? =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val completed = AtomicBoolean(false)
                lateinit var listener: LocationListener
                fun finish(location: Location?) {
                    if (completed.compareAndSet(false, true)) {
                        try {
                            locationGateway.removeUpdates(listener)
                        } catch (_: Exception) {
                            // The listener may already have been removed by the platform.
                        }
                        if (continuation.isActive) {
                            continuation.resume(location)
                        }
                    }
                }
                listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (SolarCalculator.hasValidCoordinates(location.latitude, location.longitude)) {
                            finish(location)
                        }
                    }

                    @Deprecated("Deprecated by Android")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }
                continuation.invokeOnCancellation {
                    if (completed.compareAndSet(false, true)) {
                        try {
                            locationGateway.removeUpdates(listener)
                        } catch (_: Exception) {
                            // Cancellation cleanup must not mask the original cancellation.
                        }
                    }
                }
                try {
                    if (!locationGateway.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        finish(null)
                    } else {
                        locationGateway.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER,
                            listener
                        )
                    }
                } catch (_: SecurityException) {
                    finish(null)
                } catch (_: RuntimeException) {
                    finish(null)
                }
            }
        }

    suspend fun save(location: Location) = withContext(Dispatchers.IO) {
        require(SolarCalculator.hasValidCoordinates(location.latitude, location.longitude)) {
            "Cannot save an invalid latitude/longitude pair"
        }
        context.getSharedPreferences(LocationPreferences.PREFS, Context.MODE_PRIVATE).edit()
            .putString(LocationPreferences.LATITUDE, location.latitude.toString())
            .putString(LocationPreferences.LONGITUDE, location.longitude.toString())
            .apply()
    }
}
