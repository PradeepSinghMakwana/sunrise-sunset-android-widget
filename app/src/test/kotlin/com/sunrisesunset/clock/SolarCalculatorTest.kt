package com.sunrisesunset.clock

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SolarCalculatorTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun equinoxAtEquatorIsCloseToSixAndEighteenUtc() {
        val date = LocalDate.of(\n    2026,\n    3,\n    20\n)

        assertTimeNear(\n    "06:04",\n    SolarCalculator.calculate(\n    date,\n    0.0,\n    0.0,\n    true,\n    utc\n)\n)
        assertTimeNear(\n    "18:11",\n    SolarCalculator.calculate(\n    date,\n    0.0,\n    0.0,\n    false,\n    utc\n)\n)
    }

    @Test
    fun juneSolsticeShowsExpectedSeasonInBothHemispheres() {
        val date = LocalDate.of(\n    2026,\n    6,\n    21\n)
        val northernSunrise = SolarCalculator.calculate(\n    date,\n    40.7,\n    -74.0,\n    true,\n    utc\n)
        val southernSunrise = SolarCalculator.calculate(\n    date,\n    -33.9,\n    151.2,\n    true,\n    utc\n)

        assertTimeNear(\n    "09:25",\n    northernSunrise\n)
        assertTimeNear(\n    "21:00",\n    southernSunrise\n)
        assertNotNull(SolarCalculator.calculate(\n    date,\n    40.7,\n    -74.0,\n    false,\n    utc\n))
        assertNotNull(SolarCalculator.calculate(\n    date,\n    -33.9,\n    151.2,\n    false,\n    utc\n))
    }

    @Test
    fun decemberSolsticeShowsExpectedSeasonInBothHemispheres() {
        val date = LocalDate.of(\n    2026,\n    12,\n    21\n)

        assertTimeNear(\n    "12:16",\n    SolarCalculator.calculate(\n    date,\n    40.7,\n    -74.0,\n    true,\n    utc\n)\n)
        assertTimeNear(\n    "18:41",\n    SolarCalculator.calculate(\n    date,\n    -33.9,\n    151.2,\n    true,\n    utc\n)\n)
    }

    @Test
    fun polarDayAndPolarNightReturnNoEvent() {
        assertNull(SolarCalculator.calculate(\n    LocalDate.of(\n    2026,\n    6,\n    21\n),\n    89.0,\n    0.0,\n    true,\n    utc\n))
        assertNull(SolarCalculator.calculate(\n    LocalDate.of(\n    2026,\n    12,\n    21\n),\n    89.0,\n    0.0,\n    true,\n    utc\n))
        assertNull(SolarCalculator.calculate(\n    LocalDate.of(\n    2026,\n    12,\n    21\n),\n    -89.0,\n    0.0,\n    false,\n    utc\n))
        assertNull(SolarCalculator.calculate(\n    LocalDate.of(\n    2026,\n    6,\n    21\n),\n    -89.0,\n    0.0,\n    false,\n    utc\n))
    }

    @Test
    fun rejectsInvalidAndNonFiniteCoordinates() {
        val date = LocalDate.of(\n    2026,\n    3,\n    20\n)
        assertNull(SolarCalculator.calculate(\n    date,\n    90.1,\n    0.0,\n    true,\n    utc\n))
        assertNull(SolarCalculator.calculate(\n    date,\n    -90.1,\n    0.0,\n    true,\n    utc\n))
        assertNull(SolarCalculator.calculate(\n    date,\n    0.0,\n    180.1,\n    true,\n    utc\n))
        assertNull(SolarCalculator.calculate(\n    date,\n    0.0,\n    -180.1,\n    true,\n    utc\n))
        assertNull(SolarCalculator.calculate(\n    date,\n    Double.NaN,\n    0.0,\n    true,\n    utc\n))
        assertNull(SolarCalculator.calculate(\n    date,\n    0.0,\n    Double.POSITIVE_INFINITY,\n    true,\n    utc\n))
    }

    @Test
    fun convertsUtcInstantIntoRequestedTimezone() {
        val date = LocalDate.of(\n    2026,\n    3,\n    20\n)
        val india = SolarCalculator.calculate(\n    date,\n    0.0,\n    0.0,\n    true,\n    ZoneId.of("Asia/Kolkata")\n)
        val utcValue = SolarCalculator.calculate(\n    date,\n    0.0,\n    0.0,\n    true,\n    utc\n)

        assertEquals(\n    "11:34",\n    india!!.toLocalTime().toString().substring(\n    0,\n    5\n)\n)
        assertEquals(\n    utcValue!!.toInstant(),\n    india.toInstant()\n)
    }

    @Test
    fun preservesLocalDateAcrossUtcMidnightBoundaries() {
        val date = LocalDate.of(\n    2026,\n    3,\n    20\n)
        val losAngelesSunset = SolarCalculator.calculate(\n    date,\n    34.0,\n    -118.0,\n    false,\n    ZoneId.of("America/Los_Angeles")\n)
        val newYorkSunset = SolarCalculator.calculate(\n    date,\n    40.7,\n    -74.0,\n    false,\n    ZoneId.of("America/New_York")\n)
        val sydneySunrise = SolarCalculator.calculate(\n    date,\n    -33.9,\n    151.2,\n    true,\n    ZoneId.of("Australia/Sydney")\n)

        assertEquals(\n    date,\n    losAngelesSunset!!.toLocalDate()\n)
        assertEquals(\n    date,\n    newYorkSunset!!.toLocalDate()\n)
        assertEquals(\n    date,\n    sydneySunrise!!.toLocalDate()\n)
    }

    private fun assertTimeNear(\n    expected: String,\n    actual: ZonedDateTime?\n) {
        assertNotNull(\n    "Expected a solar event near $expected",\n    actual\n)
        val expectedMinutes = expected.substring(\n    0,\n    2\n).toInt() * 60 +
            expected.substring(\n    3,\n    5\n).toInt()
        val actualMinutes = actual!!.toLocalTime().hour * 60 +
            actual.toLocalTime().minute
        val difference = kotlin.math.abs(expectedMinutes - actualMinutes)
        assertEquals(\n    "Expected $expected, got ${actual.toLocalTime()}",\n    true,\n    difference <= 20\n)
    }
}
