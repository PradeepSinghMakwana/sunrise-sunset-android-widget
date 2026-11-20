package com.sunrisesunset.clock

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocationRepositoryInstrumentedTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun preferences() =
        context.getSharedPreferences(LocationPreferences.PREFS, Context.MODE_PRIVATE)

    @After
    fun cleanPreferences() {
        preferences().edit().clear().commit()
    }

    @Test
    fun missingPartialAndMalformedSavedCoordinatesAreNotAccepted() = runBlocking {
        val repository = LocationRepository(context)
        preferences().edit().clear().commit()
        assertFalse(repository.hasSavedLocation())

        preferences().edit().putString(LocationPreferences.LATITUDE, "22.7").commit()
        assertFalse(repository.hasSavedLocation())

        preferences().edit().putString(LocationPreferences.LONGITUDE, "75.8")
            .putString(LocationPreferences.LATITUDE, "broken").commit()
        assertFalse(repository.hasSavedLocation())

        preferences().edit().putString(LocationPreferences.LATITUDE, "NaN").commit()
        assertFalse(repository.hasSavedLocation())

        preferences().edit().putString(LocationPreferences.LATITUDE, "91").commit()
        assertFalse(repository.hasSavedLocation())
    }

    @Test
    fun validSavedCoordinatePairIsAccepted() = runBlocking {
        val repository = LocationRepository(context)
        preferences().edit()
            .putString(LocationPreferences.LATITUDE, "22.7196")
            .putString(LocationPreferences.LONGITUDE, "75.8577")
            .commit()
        assertTrue(repository.hasSavedLocation())
    }

    @Test
    fun savingValidLocationPersistsBothCoordinates() = runBlocking {
        val repository = LocationRepository(context)
        val location = Location("test").apply {
            latitude = 22.7196
            longitude = 75.8577
        }
        repository.save(location)
        assertTrue(repository.hasSavedLocation())
    }

    @Test(expected = IllegalArgumentException::class)
    fun savingInvalidLocationIsRejected() = runBlocking {
        LocationRepository(context).save(
            Location("test").apply {
                latitude = Double.NaN
                longitude = 75.8577
            }
        )
    }

    @Test
    fun locationLookupWithoutPermissionReturnsNullInsteadOfCrashing() = runBlocking {
        val repository = LocationRepository(context)
        val result = repository.findLocation()
        assertFalse("A denied permission must not produce an untrusted fix", result != null)
    }

    @Test
    fun cancellationRemovesTheOutstandingLocationListener() = runBlocking {
        val gateway = FakeLocationGateway(providerEnabled = true)
        val repository = LocationRepository(context, gateway)
        try {
            withTimeout(1_000) { repository.findLocation() }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            // Expected: fake provider deliberately never emits a fix.
        }
        assertEquals(1, gateway.requestCount)
        assertEquals(1, gateway.removeCount)
    }

    @Test
    fun providerSecurityAndRuntimeFailuresReturnNull() = runBlocking {
        val securityGateway = FakeLocationGateway(providerEnabled = true).apply {
            throwOnProviderCheck = true
        }
        assertNull(LocationRepository(context, securityGateway).findLocation())

        val runtimeGateway = FakeLocationGateway(providerEnabled = true).apply {
            throwOnRequest = true
        }
        assertNull(LocationRepository(context, runtimeGateway).findLocation())
    }

    @Test
    fun disabledNetworkProviderReturnsNullWithoutRegisteringListener() = runBlocking {
        val gateway = FakeLocationGateway(providerEnabled = false)
        assertNull(LocationRepository(context, gateway).findLocation())
        assertEquals(0, gateway.requestCount)
    }

    @Test
    fun invalidProviderFixIsIgnoredAndFirstValidFixCompletesOnlyOnce() = runBlocking {
        val gateway = FakeLocationGateway(providerEnabled = true)
        val repository = LocationRepository(context, gateway)
        val pending = async { repository.findLocation() }
        withTimeout(5_000) {
            while (gateway.requestCount == 0) delay(10)
        }

        gateway.emit(Location("test").apply {
            latitude = 91.0
            longitude = 75.8
        })
        assertFalse(pending.isCompleted)

        val expected = Location("test").apply {
            latitude = 22.7196
            longitude = 75.8577
        }
        gateway.emit(expected)
        val actual = pending.await()
        assertEquals(expected.latitude, actual?.latitude ?: Double.NaN, 0.000001)
        gateway.emit(Location("test").apply {
            latitude = 10.0
            longitude = 10.0
        })
        assertEquals(1, gateway.removeCount)
    }

    private class FakeLocationGateway(
        private val providerEnabled: Boolean,
        private val cachedLocation: Location? = null
    ) : LocationGateway {
        var requestCount = 0
        var removeCount = 0
        var throwOnProviderCheck = false
        var throwOnRequest = false
        private var listener: LocationListener? = null

        override fun isProviderEnabled(provider: String): Boolean {
            if (throwOnProviderCheck) throw SecurityException("permission denied")
            return providerEnabled && provider == LocationManager.NETWORK_PROVIDER
        }

        override fun getLastKnownLocation(provider: String): Location? =
            if (provider == LocationManager.NETWORK_PROVIDER) cachedLocation else null

        override fun requestLocationUpdates(provider: String, listener: LocationListener) {
            requestCount++
            if (throwOnRequest) throw IllegalStateException("provider unavailable")
            this.listener = listener
        }

        fun emit(location: Location) {
            listener?.onLocationChanged(location)
        }

        override fun removeUpdates(listener: LocationListener) {
            if (this.listener === listener) {
                removeCount++
                this.listener = null
            }
        }
    }


    @Test
    fun validCachedLocationIsReturnedWithoutRegisteringUpdates() = runBlocking {
        val cached = Location("cached").apply {
            latitude = 22.7196
            longitude = 75.8577
        }
        val gateway = FakeLocationGateway(
            providerEnabled = true,
            cachedLocation = cached
        )
        val result = LocationRepository(context, gateway).findLocation()
        assertEquals(cached.latitude, result?.latitude ?: Double.NaN, 0.000001)
        assertEquals(0, gateway.requestCount)
    }

    @Test
    fun invalidCachedLocationIsIgnoredAndFreshLookupIsAttempted() = runBlocking {
        val invalid = Location("cached").apply {
            latitude = Double.NaN
            longitude = 75.8577
        }
        val gateway = FakeLocationGateway(
            providerEnabled = true,
            cachedLocation = invalid
        )
        try {
            withTimeout(1_000) { LocationRepository(context, gateway).findLocation() }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            // The fake network provider intentionally never returns a replacement fix.
        }
        assertEquals(1, gateway.requestCount)
        assertEquals(1, gateway.removeCount)
    }

}
