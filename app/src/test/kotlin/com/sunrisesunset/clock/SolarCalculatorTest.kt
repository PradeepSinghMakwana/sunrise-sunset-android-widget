package com.sunrisesunset.clock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class SolarCalculatorTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun equinoxAtEquatorIsCloseToSixAndEighteenUtc() {
        val date = LocalDate.of(
            2026,
            3,
            20
        )

        assertTimeNear(
            "06:04",
            SolarCalculator.calculate(
                date,
                0.0,
                0.0,
                true,
                utc
            )
        )
        assertTimeNear(
            "18:11",
            SolarCalculator.calculate(
                date,
                0.0,
                0.0,
                false,
                utc
            )
        )
    }

    @Test
    fun juneSolsticeShowsExpectedSeasonInBothHemispheres() {
        val date = LocalDate.of(2026, 6, 21)
        val northernSunrise = SolarCalculator.calculate(
            date,
            40.7,
            -74.0,
            true,
            utc
        )
        val southernSunrise = SolarCalculator.calculate(
            date,
            -33.9,
            151.2,
            true,
            utc
        )

        assertTimeNear("09:25", northernSunrise)
        assertTimeNear("21:00", southernSunrise)
        assertNotNull(
            SolarCalculator.calculate(
                date,
                40.7,
                -74.0,
                false,
                utc
            )
        )
        assertNotNull(
            SolarCalculator.calculate(
                date,
                -33.9,
                151.2,
                false,
                utc
            )
        )
    }

    @Test
    fun decemberSolsticeShowsExpectedSeasonInBothHemispheres() {
        val date = LocalDate.of(
            2026,
            12,
            21
        )

        assertTimeNear(
            "12:16",
            SolarCalculator.calculate(
                date,
                40.7,
                -74.0,
                true,
                utc
            )
        )
        assertTimeNear(
            "18:41",
            SolarCalculator.calculate(
                date,
                -33.9,
                151.2,
                true,
                utc
            )
        )
    }

    @Test
    fun polarDayAndPolarNightReturnNoEvent() {
        assertNull(
            SolarCalculator.calculate(
                LocalDate.of(2026, 6, 21),
                89.0,
                0.0,
                true,
                utc
            )
        )
        assertNull(
            SolarCalculator.calculate(
                LocalDate.of(2026, 12, 21),
                89.0,
                0.0,
                true,
                utc
            )
        )
        assertNull(
            SolarCalculator.calculate(
                LocalDate.of(2026, 12, 21),
                -89.0,
                0.0,
                false,
                utc
            )
        )
        assertNull(
            SolarCalculator.calculate(
                LocalDate.of(2026, 6, 21),
                -89.0,
                0.0,
                false,
                utc
            )
        )
    }

    @Test
    fun rejectsInvalidAndNonFiniteCoordinates() {
        val date = LocalDate.of(2026, 3, 20)

        assertNull(
            SolarCalculator.calculate(date, 90.1, 0.0, true, utc)
        )
        assertNull(
            SolarCalculator.calculate(date, -90.1, 0.0, true, utc)
        )
        assertNull(
            SolarCalculator.calculate(date, 0.0, 180.1, true, utc)
        )
        assertNull(
            SolarCalculator.calculate(date, 0.0, -180.1, true, utc)
        )
        assertNull(
            SolarCalculator.calculate(date, Double.NaN, 0.0, true, utc)
        )
        assertNull(
            SolarCalculator.calculate(
                date,
                0.0,
                Double.POSITIVE_INFINITY,
                true,
                utc
            )
        )
    }

    @Test
    fun convertsUtcInstantIntoRequestedTimezone() {
        val date = LocalDate.of(
            2026,
            3,
            20
        )
        val india = SolarCalculator.calculate(
            date,
            0.0,
            0.0,
            true,
            ZoneId.of("Asia/Kolkata")
        )
        val utcValue = SolarCalculator.calculate(
            date,
            0.0,
            0.0,
            true,
            utc
        )

        assertEquals(
            "11:34",
            india!!.toLocalTime().toString().substring(
                0,
                5
            )
        )
        assertEquals(
            utcValue!!.toInstant(),
            india.toInstant()
        )
    }

    @Test
    fun preservesLocalDateAcrossUtcMidnightBoundaries() {
        val date = LocalDate.of(
            2026,
            3,
            20
        )
        val losAngelesSunset = SolarCalculator.calculate(
            date,
            34.0,
            -118.0,
            false,
            ZoneId.of("America/Los_Angeles")
        )
        val newYorkSunset = SolarCalculator.calculate(
            date,
            40.7,
            -74.0,
            false,
            ZoneId.of("America/New_York")
        )
        val sydneySunrise = SolarCalculator.calculate(
            date,
            -33.9,
            151.2,
            true,
            ZoneId.of("Australia/Sydney")
        )

        assertEquals(
            date,
            losAngelesSunset!!.toLocalDate()
        )
        assertEquals(
            date,
            newYorkSunset!!.toLocalDate()
        )
        assertEquals(
            date,
            sydneySunrise!!.toLocalDate()
        )
    }

    private fun assertTimeNear(
        expected: String,
        actual: ZonedDateTime?
    ) {
        assertNotNull(
            "Expected a solar event near $expected",
            actual
        )
        val expectedMinutes = expected.substring(
            0,
            2
        ).toInt() * 60 +
            expected.substring(
                3,
                5
            ).toInt()
        val actualMinutes = actual!!.toLocalTime().hour * 60 +
            actual.toLocalTime().minute
        val difference = kotlin.math.abs(expectedMinutes - actualMinutes)
        assertEquals(
            "Expected $expected, got ${actual.toLocalTime()}",
            true,
            difference <= 20
        )
    }
}
