package com.sunrisesunset.clock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class SolarCalculatorEdgeCaseTest {
    @Test
    fun dateLineSunsetAtWestLongitudeBoundaryStaysOnRequestedLocalDate() {
        val date = LocalDate.of(
            2026,
            3,
            20
        )
        val result = SolarCalculator.calculate(
            date,
            0.0,
            -180.0,
            false,
            ZoneOffset.ofHours(-12)
        )

        assertEquals(
            date,
            result?.toLocalDate()
        )
        assertEquals(
            ZoneOffset.ofHours(-12),
            result?.offset
        )
    }

    @Test
    fun dateLineSunriseAtEastLongitudeBoundaryStaysOnRequestedLocalDate() {
        val date = LocalDate.of(
            2026,
            3,
            20
        )
        val result = SolarCalculator.calculate(
            date,
            0.0,
            180.0,
            true,
            ZoneOffset.ofHours(12)
        )

        assertEquals(
            date,
            result?.toLocalDate()
        )
        assertEquals(
            ZoneOffset.ofHours(12),
            result?.offset
        )
    }

    @Test
    fun sunrisePrecedesSunsetAtRepresentativeLocations() {
        val date = LocalDate.of(2026, 3, 20)
        val locations = listOf(
            Triple(0.0, 0.0, ZoneOffset.UTC),
            Triple(34.0, -118.0, ZoneId.of("America/Los_Angeles")),
            Triple(40.7, -74.0, ZoneId.of("America/New_York")),
            Triple(-33.9, 151.2, ZoneId.of("Australia/Sydney"))
        )

        locations.forEach { (latitude, longitude, zone) ->
            assertSolarEventsAreOrdered(date, latitude, longitude, zone)
        }
    }

    private fun assertSolarEventsAreOrdered(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zone: ZoneId
    ) {
        val sunrise = SolarCalculator.calculate(date, latitude, longitude, true, zone)
        val sunset = SolarCalculator.calculate(date, latitude, longitude, false, zone)

        assertTrue("Missing sunrise at $latitude, $longitude", sunrise != null)
        assertTrue("Missing sunset at $latitude, $longitude", sunset != null)
        assertEquals(date, sunrise!!.toLocalDate())
        assertEquals(date, sunset!!.toLocalDate())
        assertTrue(
            "Sunrise must precede sunset at $latitude, $longitude",
            sunrise.toInstant().isBefore(sunset.toInstant())
        )
    }

    @Test
    fun movingEastByFifteenDegreesMovesSolarEventsEarlierInUtc() {
        val date = LocalDate.of(
            2026,
            3,
            20
        )
        val greenwich = SolarCalculator.calculate(
            date,
            0.0,
            0.0,
            true,
            ZoneOffset.UTC
        )
        val fifteenEast = SolarCalculator.calculate(
            date,
            0.0,
            15.0,
            true,
            ZoneOffset.UTC
        )

        val difference = Duration.between(
            fifteenEast!!.toInstant(),
            greenwich!!.toInstant()
        )
            .toMinutes()
        assertTrue(
            "Expected about one hour, got $difference minutes",
            difference in 45..75
        )
    }

    @Test
    fun northernSummerDayIsLongerAtHigherLatitude() {
        val date = LocalDate.of(
            2026,
            6,
            21
        )
        val equatorDay = dayLengthMinutes(
            date,
            0.0,
            0.0,
            ZoneOffset.UTC
        )
        val northernDay = dayLengthMinutes(
            date,
            50.0,
            0.0,
            ZoneOffset.UTC
        )

        assertTrue(
            "Higher northern latitude should have a longer summer day",
            northernDay > equatorDay
        )
    }

    @Test
    fun southernSummerDayIsLongerThanSouthernWinterDay() {
        val summer = LocalDate.of(
            2026,
            12,
            21
        )
        val winter = LocalDate.of(
            2026,
            6,
            21
        )

        val summerLength = dayLengthMinutes(
            summer,
            -33.9,
            151.2,
            ZoneId.of("Australia/Sydney")
        )
        val winterLength = dayLengthMinutes(
            winter,
            -33.9,
            151.2,
            ZoneId.of("Australia/Sydney")
        )

        assertTrue(
            "Southern summer should have a longer day",
            summerLength > winterLength
        )
    }

    @Test
    fun daylightSavingSpringTransitionUsesPostTransitionOffset() {
        val date = LocalDate.of(
            2026,
            3,
            8
        )
        val zone = ZoneId.of("America/New_York")
        val sunrise = SolarCalculator.calculate(
            date,
            40.7128,
            -74.0060,
            true,
            zone
        )
        val sunset = SolarCalculator.calculate(
            date,
            40.7128,
            -74.0060,
            false,
            zone
        )

        assertEquals(
            date,
            sunrise?.toLocalDate()
        )
        assertEquals(
            date,
            sunset?.toLocalDate()
        )
        assertEquals(
            ZoneOffset.ofHours(-4),
            sunrise?.offset
        )
        assertEquals(
            ZoneOffset.ofHours(-4),
            sunset?.offset
        )
    }

    @Test
    fun daylightSavingAutumnTransitionUsesPostTransitionOffsetForBothEvents() {
        val date = LocalDate.of(
            2026,
            11,
            1
        )
        val zone = ZoneId.of("America/New_York")
        val sunrise = SolarCalculator.calculate(
            date,
            40.7128,
            -74.0060,
            true,
            zone
        )
        val sunset = SolarCalculator.calculate(
            date,
            40.7128,
            -74.0060,
            false,
            zone
        )

        // The autumn transition occurs at 02:00; both solar events are later.
        assertEquals(
            ZoneOffset.ofHours(-5),
            sunrise?.offset
        )
        assertEquals(
            ZoneOffset.ofHours(-5),
            sunset?.offset
        )
        assertEquals(
            date,
            sunrise?.toLocalDate()
        )
        assertEquals(
            date,
            sunset?.toLocalDate()
        )
    }

    @Test
    fun leapDayProducesBothSolarEvents() {
        val date = LocalDate.of(
            2024,
            2,
            29
        )
        val zone = ZoneId.of("Asia/Kolkata")

        val sunrise = SolarCalculator.calculate(
            date,
            22.7196,
            75.8577,
            true,
            zone
        )
        val sunset = SolarCalculator.calculate(
            date,
            22.7196,
            75.8577,
            false,
            zone
        )

        assertEquals(
            date,
            sunrise?.toLocalDate()
        )
        assertEquals(
            date,
            sunset?.toLocalDate()
        )
        assertTrue(sunrise!!.toInstant().isBefore(sunset!!.toInstant()))
    }

    @Test
    fun returnedZonedDateTimeUsesRequestedZone() {
        val date = LocalDate.of(
            2026,
            9,
            1
        )
        val zone = ZoneId.of("Asia/Kolkata")

        val result = SolarCalculator.calculate(
            date,
            22.7196,
            75.8577,
            true,
            zone
        )

        assertEquals(
            zone,
            result?.zone
        )
    }

    @Test
    fun repeatedCalculationReturnsIdenticalResult() {
        val date = LocalDate.of(
            2026,
            7,
            14
        )
        val zone = ZoneId.of("Asia/Kolkata")

        val first = SolarCalculator.calculate(
            date,
            22.7196,
            75.8577,
            true,
            zone
        )
        val second = SolarCalculator.calculate(
            date,
            22.7196,
            75.8577,
            true,
            zone
        )

        assertEquals(
            first,
            second
        )
    }

    @Test
    fun invalidCoordinatesRejectNegativeInfinityAndOutOfRangeValues() {
        val date = LocalDate.of(
            2026,
            3,
            20
        )

        assertEquals(
            null,
            SolarCalculator.calculate(
                date,
                Double.NEGATIVE_INFINITY,
                0.0,
                true,
                ZoneOffset.UTC
            )
        )
        assertEquals(
            null,
            SolarCalculator.calculate(
                date,
                0.0,
                Double.NEGATIVE_INFINITY,
                false,
                ZoneOffset.UTC
            )
        )
        assertEquals(
            null,
            SolarCalculator.calculate(
                date,
                0.0,
                180.000001,
                true,
                ZoneOffset.UTC
            )
        )
        assertEquals(
            null,
            SolarCalculator.calculate(
                date,
                -90.000001,
                0.0,
                false,
                ZoneOffset.UTC
            )
        )
    }

    private fun dayLengthMinutes(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zone: ZoneId
    ): Long {
        val sunrise = SolarCalculator.calculate(
            date,
            latitude,
            longitude,
            true,
            zone
        )
        val sunset = SolarCalculator.calculate(
            date,
            latitude,
            longitude,
            false,
            zone
        )

        assertTrue(
            "Expected sunrise for $date at $latitude, $longitude",
            sunrise != null
        )
        assertTrue(
            "Expected sunset for $date at $latitude, $longitude",
            sunset != null
        )
        return Duration.between(
            sunrise!!.toInstant(),
            sunset!!.toInstant()
        ).toMinutes()
    }

    @Test
    fun exactGeographicPolesNeverProduceNonFiniteSolarTimes() {
        val dates = listOf(
            LocalDate.of(2026, 3, 20),
            LocalDate.of(2026, 6, 21),
            LocalDate.of(2026, 12, 21)
        )
        for (latitude in listOf(-90.0, 90.0)) {
            for (date in dates) {
                for (sunrise in listOf(true, false)) {
                    val result = SolarCalculator.calculate(
                        date,
                        latitude,
                        0.0,
                        sunrise,
                        ZoneOffset.UTC
                    )
                    assertEquals(
                        "The geographic pole has no standard sunrise/sunset crossing on $date",
                        null,
                        result
                    )
                }
            }
        }
    }

    @Test
    fun coordinateBoundariesAreInclusiveAndAdjacentValuesAreRejected() {
        assertTrue(SolarCalculator.hasValidCoordinates(-90.0, -180.0))
        assertTrue(SolarCalculator.hasValidCoordinates(90.0, 180.0))
        assertTrue(!SolarCalculator.hasValidCoordinates(-90.0000001, 0.0))
        assertTrue(!SolarCalculator.hasValidCoordinates(90.0000001, 0.0))
        assertTrue(!SolarCalculator.hasValidCoordinates(0.0, -180.0000001))
        assertTrue(!SolarCalculator.hasValidCoordinates(0.0, 180.0000001))
        assertTrue(!SolarCalculator.hasValidCoordinates(Double.NaN, 0.0))
        assertTrue(!SolarCalculator.hasValidCoordinates(0.0, Double.POSITIVE_INFINITY))
        assertTrue(!SolarCalculator.hasValidCoordinates(0.0, Double.NEGATIVE_INFINITY))
    }

    @Test
    fun solarEventsRemainOrderedAcrossYearBoundaryAndDateLineZones() {
        val cases = listOf(
            Triple(LocalDate.of(2026, 12, 31), 66.0, 179.9),
            Triple(LocalDate.of(2027, 1, 1), -66.0, -179.9),
            Triple(LocalDate.of(2026, 12, 31), 0.0, 180.0),
            Triple(LocalDate.of(2027, 1, 1), 0.0, -180.0)
        )
        for ((date, latitude, longitude) in cases) {
            val zone = if (longitude >= 0) {
                ZoneOffset.ofHours(12)
            } else {
                ZoneOffset.ofHours(-12)
            }
            val sunrise = SolarCalculator.calculate(date, latitude, longitude, true, zone)
            val sunset = SolarCalculator.calculate(date, latitude, longitude, false, zone)
            if (sunrise != null && sunset != null) {
                assertEquals(date, sunrise.toLocalDate())
                assertEquals(date, sunset.toLocalDate())
                assertTrue(sunrise.toInstant().isBefore(sunset.toInstant()))
            }
        }
    }

    @Test
    fun changingDisplayZonePreservesTheUnderlyingSolarInstant() {
        val date = LocalDate.of(2026, 9, 22)
        val utc = SolarCalculator.calculate(
            date,
            22.7196,
            75.8577,
            true,
            ZoneOffset.UTC
        )
        val kolkata = SolarCalculator.calculate(
            date,
            22.7196,
            75.8577,
            true,
            ZoneId.of("Asia/Kolkata")
        )
        val losAngeles = SolarCalculator.calculate(
            date,
            22.7196,
            75.8577,
            true,
            ZoneId.of("America/Los_Angeles")
        )
        assertEquals(utc?.toInstant(), kolkata?.toInstant())
        assertEquals(utc?.toInstant(), losAngeles?.toInstant())
    }

    @Test
    fun polarTransitionReturnsEitherAValidFiniteEventOrNoEvent() {
        val date = LocalDate.of(2026, 6, 21)
        for (latitude in listOf(65.0, 66.0, 66.5, 67.0, 67.5, 68.0, 69.0)) {
            for (sunrise in listOf(true, false)) {
                val result = SolarCalculator.calculate(
                    date,
                    latitude,
                    0.0,
                    sunrise,
                    ZoneOffset.UTC
                )
                if (result != null) {
                    assertTrue(result.toInstant().epochSecond > 0)
                    assertEquals(date, result.toLocalDate())
                }
            }
        }
    }
}
