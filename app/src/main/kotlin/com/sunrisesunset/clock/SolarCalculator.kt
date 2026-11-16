package com.sunrisesunset.clock

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/**
 * NOAA-style sunrise/sunset approximation.
 *
 * Returns null when the sun does not cross the standard sunrise/sunset
 * zenith on the requested date.
 */
internal object SolarCalculator {
    private const val MIN_LATITUDE = -90.0
    private const val MAX_LATITUDE = 90.0
    private const val MIN_LONGITUDE = -180.0
    private const val MAX_LONGITUDE = 180.0
    private const val DEGREES_PER_CIRCLE = 360.0
    private const val DEGREES_PER_QUADRANT = 90.0
    private const val DEGREES_PER_HOUR = 15.0
    private const val HOURS_PER_DAY = 24.0
    private const val MINUTES_PER_HOUR = 60.0
    private const val SUNRISE_HOUR = 6.0
    private const val SUNSET_HOUR = 18.0
    private const val SUN_ZENITH = 90.833
    private const val MEAN_ANOMALY_RATE = 0.9856
    private const val MEAN_ANOMALY_OFFSET = 3.289
    private const val LONGITUDE_SINE_COEFFICIENT = 1.916
    private const val LONGITUDE_DOUBLE_SINE_COEFFICIENT = 0.020
    private const val LONGITUDE_OFFSET = 282.634
    private const val RIGHT_ASCENSION_COEFFICIENT = 0.91764
    private const val DECLINATION_COEFFICIENT = 0.39782
    private const val LOCAL_TIME_COEFFICIENT = 0.06571
    private const val LOCAL_TIME_OFFSET = 6.622

    fun calculate(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        isSunrise: Boolean,
        zone: ZoneId
    ): ZonedDateTime? {
        if (!hasValidCoordinates(latitude, longitude)) return null

        val day = date.dayOfYear
        val longitudeHour = longitude / DEGREES_PER_HOUR
        val eventHour = if (isSunrise) SUNRISE_HOUR else SUNSET_HOUR
        val approximateTime = day + ((eventHour - longitudeHour) / HOURS_PER_DAY)
        val meanAnomaly = MEAN_ANOMALY_RATE * approximateTime - MEAN_ANOMALY_OFFSET

        val trueLongitude = (
            meanAnomaly +
                LONGITUDE_SINE_COEFFICIENT * sin(Math.toRadians(meanAnomaly)) +
                LONGITUDE_DOUBLE_SINE_COEFFICIENT * sin(2.0 * Math.toRadians(meanAnomaly)) +
                LONGITUDE_OFFSET
            ).normalizeDegrees()

        var rightAscension = Math.toDegrees(
            atan(RIGHT_ASCENSION_COEFFICIENT * tan(Math.toRadians(trueLongitude)))
        ).normalizeDegrees()
        val longitudeQuadrant = floor(trueLongitude / DEGREES_PER_QUADRANT) *
            DEGREES_PER_QUADRANT
        val rightAscensionQuadrant = floor(rightAscension / DEGREES_PER_QUADRANT) *
            DEGREES_PER_QUADRANT
        rightAscension = (
            rightAscension + longitudeQuadrant - rightAscensionQuadrant
            ) / DEGREES_PER_HOUR

        val sinDeclination = DECLINATION_COEFFICIENT * sin(Math.toRadians(trueLongitude))
        val cosDeclination = cos(asin(sinDeclination))
        val latitudeRadians = Math.toRadians(latitude)
        val cosHourAngle = (
            cos(Math.toRadians(SUN_ZENITH)) -
                sinDeclination * sin(latitudeRadians)
            ) / (cosDeclination * cos(latitudeRadians))

        return if (cosHourAngle in -1.0..1.0) {
            val hourAngle = if (isSunrise) {
                DEGREES_PER_CIRCLE - Math.toDegrees(acos(cosHourAngle))
            } else {
                Math.toDegrees(acos(cosHourAngle))
            }

            val localMeanTime = hourAngle / DEGREES_PER_HOUR +
                rightAscension - LOCAL_TIME_COEFFICIENT * approximateTime - LOCAL_TIME_OFFSET
            val rawUtcHour = localMeanTime - longitudeHour
            val utcHour = ((rawUtcHour % HOURS_PER_DAY) + HOURS_PER_DAY) % HOURS_PER_DAY

            // The requested date is the observer's local date. The NOAA time
            // fraction identifies whether the corresponding UTC instant belongs
            // to the previous, same, or next UTC date.
            val utcDayOffset = floor(approximateTime).toLong() - day
            val utcDate = date.plusDays(utcDayOffset)
            val utcMinutes = (utcHour * MINUTES_PER_HOUR).roundToLong()
            utcDate.atStartOfDay(ZoneOffset.UTC)
                .plusMinutes(utcMinutes)
                .withZoneSameInstant(zone)
        } else {
            null
        }
    }

    fun hasValidCoordinates(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() &&
            latitude in MIN_LATITUDE..MAX_LATITUDE &&
            longitude.isFinite() &&
            longitude in MIN_LONGITUDE..MAX_LONGITUDE

    private fun Double.normalizeDegrees(): Double =
        (this % DEGREES_PER_CIRCLE + DEGREES_PER_CIRCLE) % DEGREES_PER_CIRCLE
}
