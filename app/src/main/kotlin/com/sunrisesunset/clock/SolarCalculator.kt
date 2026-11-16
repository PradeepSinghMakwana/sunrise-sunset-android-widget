package com.sunrisesunset.clock

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.*

/**
 * NOAA-style sunrise/sunset approximation.
 *
 * Returns null when the sun does not cross the standard 90.833-degree
 * sunrise/sunset zenith on the requested date.
 */
internal object SolarCalculator {
    fun calculate(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        isSunrise: Boolean,
        zone: ZoneId
    ): ZonedDateTime? {
        if (!latitude.isFinite() || latitude !in -90.0..90.0) return null
        if (!longitude.isFinite() || longitude !in -180.0..180.0) return null

        val day = date.dayOfYear
        val longitudeHour = longitude / 15.0
        val approximateTime = day + (((if (isSunrise) 6.0 else 18.0) - longitudeHour) / 24.0)
        val meanAnomaly = 0.9856 * approximateTime - 3.289

        val trueLongitude = (
            meanAnomaly +
                1.916 * sin(Math.toRadians(meanAnomaly)) +
                0.020 * sin(2.0 * Math.toRadians(meanAnomaly)) +
                282.634
            ).normalizeDegrees()

        var rightAscension = Math.toDegrees(
            atan(0.91764 * tan(Math.toRadians(trueLongitude)))
        ).normalizeDegrees()
        val longitudeQuadrant = floor(trueLongitude / 90.0) * 90.0
        val rightAscensionQuadrant = floor(rightAscension / 90.0) * 90.0
        rightAscension = (rightAscension + longitudeQuadrant - rightAscensionQuadrant) / 15.0

        val sinDeclination = 0.39782 * sin(Math.toRadians(trueLongitude))
        val cosDeclination = cos(asin(sinDeclination))
        val latitudeRadians = Math.toRadians(latitude)
        val cosHourAngle = (
            cos(Math.toRadians(90.833)) -
                sinDeclination * sin(latitudeRadians)
            ) / (cosDeclination * cos(latitudeRadians))

        if (cosHourAngle !in -1.0..1.0) return null

        val hourAngle = if (isSunrise) {
            360.0 - Math.toDegrees(acos(cosHourAngle))
        } else {
            Math.toDegrees(acos(cosHourAngle))
        }

        val localMeanTime =
            hourAngle / 15.0 + rightAscension - 0.06571 * approximateTime - 6.622
        val rawUtcHour = localMeanTime - longitudeHour
        val utcHour = ((rawUtcHour % 24.0) + 24.0) % 24.0

        // The requested date is the observer's local date. The NOAA time
        // fraction identifies whether the corresponding UTC instant belongs
        // to the previous, same, or next UTC date.
        val utcDayOffset = floor(approximateTime).toLong() - day
        val utcDate = date.plusDays(utcDayOffset)
        val utcMinutes = (utcHour * 60.0).roundToLong()
        return utcDate.atStartOfDay(ZoneOffset.UTC)
            .plusMinutes(utcMinutes)
            .withZoneSameInstant(zone)
    }

    private fun Double.normalizeDegrees(): Double = (this % 360.0 + 360.0) % 360.0
}
