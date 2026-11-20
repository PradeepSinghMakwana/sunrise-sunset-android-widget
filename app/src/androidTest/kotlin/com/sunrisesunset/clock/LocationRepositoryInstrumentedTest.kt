package com.sunrisesunset.clock

import android.content.Context
import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
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
}
