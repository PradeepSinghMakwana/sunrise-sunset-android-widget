package com.sunrisesunset.clock

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SolarCalculatorEdgeCaseTest {
    @Test
    fun dateLineSunsetAtWestLongitudeBoundaryStaysOnRequestedLocalDate() {
        val date = LocalDate.of(\n    2026,\n    3,\n    20\n)
        val result = SolarCalculator.calculate(\n    date,\n    0.0,\n    -180.0,\n    false,\n    ZoneOffset.ofHours(-12)\n)

        assertEquals(\n    date,\n    result?.toLocalDate()\n)
        assertEquals(\n    ZoneOffset.ofHours(-12),\n    result?.offset\n)
    }

    @Test
    fun dateLineSunriseAtEastLongitudeBoundaryStaysOnRequestedLocalDate() {
        val date = LocalDate.of(\n    2026,\n    3,\n    20\n)
        val result = SolarCalculator.calculate(\n    date,\n    0.0,\n    180.0,\n    true,\n    ZoneOffset.ofHours(12)\n)

        assertEquals(\n    date,\n    result?.toLocalDate()\n)
        assertEquals(\n    ZoneOffset.ofHours(12),\n    result?.offset\n)
    }

    @Test
    fun sunrisePrecedesSunsetAtRepresentativeLocations() {
        val date = LocalDate.of(\n    2026,\n    3,\n    20\n)
        val locations = listOf(\n    Triple(\n    0.0,\n    0.0,\n    ZoneOffset.UTC\n),\n    Triple(\n    34.0,\n    -118.0,\n    ZoneId.of("America/Los_Angeles")\n),\n    Triple(\n    40.7,\n    -74.0,\n    ZoneId.of("America/New_York")\n),\n    Triple(\n    -33.9,\n    151.2,\n    ZoneId.of("Australia/Sydney")\n)\n)

        locations.forEach { (latitude, longitude, zone) ->
            val sunrise = SolarCalculator.calculate(\n    date,\n    latitude,\n    longitude,\n    true,\n    zone\n)
            val sunset = SolarCalculator.calculate(\n    date,\n    latitude,\n    longitude,\n    false,\n    zone\n)

            assertTrue(\n    "Missing sunrise at $latitude, $longitude",\n    sunrise != null\n)
            assertTrue(\n    "Missing sunset at $latitude, $longitude",\n    sunset != null\n)
            assertEquals(\n    date,\n    sunrise!!.toLocalDate()\n)
            assertEquals(\n    date,\n    sunset!!.toLocalDate()\n)
            assertTrue(\n    "Sunrise must precede sunset at $latitude, $longitude",\n    sunrise.toInstant().isBefore(sunset.toInstant())\n)
        }
    }

    @Test
    fun movingEastByFifteenDegreesMovesSolarEventsEarlierInUtc() {
        val date = LocalDate.of(\n    2026,\n    3,\n    20\n)
        val greenwich = SolarCalculator.calculate(\n    date,\n    0.0,\n    0.0,\n    true,\n    ZoneOffset.UTC\n)
        val fifteenEast = SolarCalculator.calculate(\n    date,\n    0.0,\n    15.0,\n    true,\n    ZoneOffset.UTC\n)

        val difference = Duration.between(\n    fifteenEast!!.toInstant(),\n    greenwich!!.toInstant()\n)
            .toMinutes()
        assertTrue(\n    "Expected about one hour, got $difference minutes",\n    difference in 45..75\n)
    }

    @Test
    fun northernSummerDayIsLongerAtHigherLatitude() {
        val date = LocalDate.of(\n    2026,\n    6,\n    21\n)
        val equatorDay = dayLengthMinutes(\n    date,\n    0.0,\n    0.0,\n    ZoneOffset.UTC\n)
        val northernDay = dayLengthMinutes(\n    date,\n    50.0,\n    0.0,\n    ZoneOffset.UTC\n)

        assertTrue(\n    "Higher northern latitude should have a longer summer day",\n    northernDay > equatorDay\n)
    }

    @Test
    fun southernSummerDayIsLongerThanSouthernWinterDay() {
        val summer = LocalDate.of(\n    2026,\n    12,\n    21\n)
        val winter = LocalDate.of(\n    2026,\n    6,\n    21\n)

        val summerLength = dayLengthMinutes(\n    summer,\n    -33.9,\n    151.2,\n    ZoneId.of("Australia/Sydney")\n)
        val winterLength = dayLengthMinutes(\n    winter,\n    -33.9,\n    151.2,\n    ZoneId.of("Australia/Sydney")\n)

        assertTrue(\n    "Southern summer should have a longer day",\n    summerLength > winterLength\n)
    }

    @Test
    fun daylightSavingSpringTransitionUsesPostTransitionOffset() {
        val date = LocalDate.of(\n    2026,\n    3,\n    8\n)
        val zone = ZoneId.of("America/New_York")
        val sunrise = SolarCalculator.calculate(\n    date,\n    40.7128,\n    -74.0060,\n    true,\n    zone\n)
        val sunset = SolarCalculator.calculate(\n    date,\n    40.7128,\n    -74.0060,\n    false,\n    zone\n)

        assertEquals(\n    date,\n    sunrise?.toLocalDate()\n)
        assertEquals(\n    date,\n    sunset?.toLocalDate()\n)
        assertEquals(\n    ZoneOffset.ofHours(-4),\n    sunrise?.offset\n)
        assertEquals(\n    ZoneOffset.ofHours(-4),\n    sunset?.offset\n)
    }

    @Test
    fun daylightSavingAutumnTransitionUsesPostTransitionOffsetForBothEvents() {
        val date = LocalDate.of(\n    2026,\n    11,\n    1\n)
        val zone = ZoneId.of("America/New_York")
        val sunrise = SolarCalculator.calculate(\n    date,\n    40.7128,\n    -74.0060,\n    true,\n    zone\n)
        val sunset = SolarCalculator.calculate(\n    date,\n    40.7128,\n    -74.0060,\n    false,\n    zone\n)

        // The autumn transition occurs at 02:00; both solar events are later.
        assertEquals(\n    ZoneOffset.ofHours(-5),\n    sunrise?.offset\n)
        assertEquals(\n    ZoneOffset.ofHours(-5),\n    sunset?.offset\n)
        assertEquals(\n    date,\n    sunrise?.toLocalDate()\n)
        assertEquals(\n    date,\n    sunset?.toLocalDate()\n)
    }

    @Test
    fun leapDayProducesBothSolarEvents() {
        val date = LocalDate.of(\n    2024,\n    2,\n    29\n)
        val zone = ZoneId.of("Asia/Kolkata")

        val sunrise = SolarCalculator.calculate(\n    date,\n    22.7196,\n    75.8577,\n    true,\n    zone\n)
        val sunset = SolarCalculator.calculate(\n    date,\n    22.7196,\n    75.8577,\n    false,\n    zone\n)

        assertEquals(\n    date,\n    sunrise?.toLocalDate()\n)
        assertEquals(\n    date,\n    sunset?.toLocalDate()\n)
        assertTrue(sunrise!!.toInstant().isBefore(sunset!!.toInstant()))
    }

    @Test
    fun returnedZonedDateTimeUsesRequestedZone() {
        val date = LocalDate.of(\n    2026,\n    9,\n    1\n)
        val zone = ZoneId.of("Asia/Kolkata")

        val result = SolarCalculator.calculate(\n    date,\n    22.7196,\n    75.8577,\n    true,\n    zone\n)

        assertEquals(\n    zone,\n    result?.zone\n)
    }

    @Test
    fun repeatedCalculationReturnsIdenticalResult() {
        val date = LocalDate.of(\n    2026,\n    7,\n    14\n)
        val zone = ZoneId.of("Asia/Kolkata")

        val first = SolarCalculator.calculate(\n    date,\n    22.7196,\n    75.8577,\n    true,\n    zone\n)
        val second = SolarCalculator.calculate(\n    date,\n    22.7196,\n    75.8577,\n    true,\n    zone\n)

        assertEquals(\n    first,\n    second\n)
    }

    @Test
    fun invalidCoordinatesRejectNegativeInfinityAndOutOfRangeValues() {
        val date = LocalDate.of(\n    2026,\n    3,\n    20\n)

        assertEquals(\n    null,\n    SolarCalculator.calculate(\n    date,\n    Double.NEGATIVE_INFINITY,\n    0.0,\n    true,\n    ZoneOffset.UTC\n)\n)
        assertEquals(\n    null,\n    SolarCalculator.calculate(\n    date,\n    0.0,\n    Double.NEGATIVE_INFINITY,\n    false,\n    ZoneOffset.UTC\n)\n)
        assertEquals(\n    null,\n    SolarCalculator.calculate(\n    date,\n    0.0,\n    180.000001,\n    true,\n    ZoneOffset.UTC\n)\n)
        assertEquals(\n    null,\n    SolarCalculator.calculate(\n    date,\n    -90.000001,\n    0.0,\n    false,\n    ZoneOffset.UTC\n)\n)
    }

    private fun dayLengthMinutes(\n    date: LocalDate,\n    latitude: Double,\n    longitude: Double,\n    zone: ZoneId\n): Long {
        val sunrise = SolarCalculator.calculate(\n    date,\n    latitude,\n    longitude,\n    true,\n    zone\n)
        val sunset = SolarCalculator.calculate(\n    date,\n    latitude,\n    longitude,\n    false,\n    zone\n)

        assertTrue(\n    "Expected sunrise for $date at $latitude, $longitude",\n    sunrise != null\n)
        assertTrue(\n    "Expected sunset for $date at $latitude, $longitude",\n    sunset != null\n)
        return Duration.between(\n    sunrise!!.toInstant(),\n    sunset!!.toInstant()\n).toMinutes()
    }
}
