package com.sunrisesunset.clock

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

class LocationRepository(private val context: Context) {
    suspend fun hasSavedLocation(): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(LocationPreferences.PREFS, Context.MODE_PRIVATE)
        prefs.contains(LocationPreferences.LATITUDE) && prefs.contains(LocationPreferences.LONGITUDE)
    }

    suspend fun findLocation(): Location? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val cached = withContext(Dispatchers.IO) {
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .firstNotNullOfOrNull { provider ->
                    try {
                        if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider) else null
                    } catch (_: SecurityException) {
                        null
                    } catch (_: RuntimeException) {
                        null
                    }
                }
        }
        if (cached != null) return cached

        return withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val completed = AtomicBoolean(false)
                lateinit var listener: LocationListener
                fun finish(location: Location?) {
                    if (completed.compareAndSet(false, true)) {
                        try {
                            manager.removeUpdates(listener)
                        } catch (_: Exception) {
                            // The listener may already have been removed by the platform.
                        }
                        if (continuation.isActive) continuation.resume(location)
                    }
                }
                listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) = finish(location)

                    @Deprecated("Deprecated by Android")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }
                continuation.invokeOnCancellation {
                    if (completed.compareAndSet(false, true)) {
                        try {
                            manager.removeUpdates(listener)
                        } catch (_: Exception) {
                            // Cancellation cleanup must not mask the original cancellation.
                        }
                    }
                }
                try {
                    if (!manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        finish(null)
                    } else {
                        manager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER,
                            0L,
                            0f,
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
    }

    suspend fun save(location: Location) = withContext(Dispatchers.IO) {
        context.getSharedPreferences(LocationPreferences.PREFS, Context.MODE_PRIVATE).edit()
            .putString(LocationPreferences.LATITUDE, location.latitude.toString())
            .putString(LocationPreferences.LONGITUDE, location.longitude.toString())
            .apply()
    }
}
