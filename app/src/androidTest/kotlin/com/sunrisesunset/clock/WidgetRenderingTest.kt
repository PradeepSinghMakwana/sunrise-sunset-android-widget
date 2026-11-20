package com.sunrisesunset.clock

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class WidgetRenderingTest {
    private lateinit var context: Context
    private val preferences by lazy {
        context.getSharedPreferences(LocationPreferences.PREFS, Context.MODE_PRIVATE)
    }

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        preferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun missingLocationRendersFallbackAndNoSolarEvents() {
        val root = renderWidget()
        assertEquals("—", text(root, R.id.sunrise_time))
        assertEquals("—", text(root, R.id.sunset_time))
        assertEquals(
            "Open app to set approximate location",
            text(root, R.id.date_text)
        )
    }

    @Test
    fun partialMalformedAndOutOfRangePreferencesNeverRenderSolarTimes() {
        val invalidCases = listOf(
            mapOf(LocationPreferences.LATITUDE to "22.7"),
            mapOf(LocationPreferences.LONGITUDE to "75.8"),
            mapOf(
                LocationPreferences.LATITUDE to "not-a-number",
                LocationPreferences.LONGITUDE to "75.8"
            ),
            mapOf(
                LocationPreferences.LATITUDE to "NaN",
                LocationPreferences.LONGITUDE to "75.8"
            ),
            mapOf(
                LocationPreferences.LATITUDE to "91",
                LocationPreferences.LONGITUDE to "75.8"
            ),
            mapOf(
                LocationPreferences.LATITUDE to "22.7",
                LocationPreferences.LONGITUDE to "181"
            )
        )
        for (values in invalidCases) {
            preferences.edit().clear().apply {
                values.forEach { (key, value) -> putString(key, value) }
            }.commit()
            val root = renderWidget()
            assertEquals("—", text(root, R.id.sunrise_time))
            assertEquals("—", text(root, R.id.sunset_time))
            assertEquals("Open app to set approximate location", text(root, R.id.date_text))
        }
    }

    @Test
    fun validLocationRendersBothSolarEventsAndDate() {
        saveCoordinates(22.7196, 75.8577)
        val root = renderWidget()
        assertNotEquals("—", text(root, R.id.sunrise_time))
        assertNotEquals("—", text(root, R.id.sunset_time))
        assertTrue(text(root, R.id.date_text).isNotBlank())
        assertNotEquals("Open app to set approximate location", text(root, R.id.date_text))
    }

    @Test
    fun polarLocationRendersNoEventMarkerInsteadOfInvalidTime() {
        saveCoordinates(89.0, 0.0)
        val root = renderWidget()
        assertEquals("—", text(root, R.id.sunrise_time))
        assertEquals("—", text(root, R.id.sunset_time))
    }

    @Test
    fun widgetContainsClockAndBothSolarLabels() {
        val root = renderWidget()
        val clock = root.findViewById<android.widget.TextClock>(R.id.digital_clock)
        assertNotNull(clock)
        assertEquals("hh:mm:ss a", clock.format12Hour.toString())
        assertEquals("HH:mm:ss", clock.format24Hour.toString())
        assertEquals("☀ SUNRISE", text(root, R.id.sunrise_label))
        assertEquals("☾ SUNSET", text(root, R.id.sunset_label))
    }

    private fun saveCoordinates(latitude: Double, longitude: Double) {
        preferences.edit()
            .putString(LocationPreferences.LATITUDE, latitude.toString())
            .putString(LocationPreferences.LONGITUDE, longitude.toString())
            .commit()
    }

    private fun renderWidget(): android.view.View {
        val views = SunClockWidgetProvider.buildRemoteViews(context)
        return views.apply(context, LinearLayout(context))
    }

    private fun text(root: android.view.View, id: Int): String =
        root.findViewById<TextView>(id).text.toString()
}
