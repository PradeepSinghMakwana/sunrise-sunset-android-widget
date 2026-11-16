package com.sunrisesunset.clock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

class SunClockWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_DATE_CHANGED ||
            intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SunClockWidgetProvider::class.java))
            onUpdate(context, manager, ids)
        }
    }

    companion object {
        fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.sun_clock_widget)
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val prefs = context.getSharedPreferences(LocationPreferences.PREFS, Context.MODE_PRIVATE)
            val lat = prefs.getString(LocationPreferences.LATITUDE, null)?.toDoubleOrNull()
            val lon = prefs.getString(LocationPreferences.LONGITUDE, null)?.toDoubleOrNull()
            val timeFormat = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())
            val dateFormat = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault())

            if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                val sunrise = solarTime(today, lat, lon, true, zone)
                val sunset = solarTime(today, lat, lon, false, zone)
                views.setTextViewText(R.id.sunrise_time, sunrise?.format(timeFormat) ?: "—")
                views.setTextViewText(R.id.sunset_time, sunset?.format(timeFormat) ?: "—")
                views.setTextViewText(R.id.date_text, today.format(dateFormat))
            } else {
                views.setTextViewText(R.id.sunrise_time, "—")
                views.setTextViewText(R.id.sunset_time, "—")
                views.setTextViewText(R.id.date_text, "Open app to set approximate location")
            }

            val openApp = PendingIntent.getActivity(
                context, 0, Intent(context, LocationSetupActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            manager.updateAppWidget(id, views)
        }

        // NOAA-style approximation. Returns null where the sun does not rise or set.
        internal fun solarTime(
            date: LocalDate, latitude: Double, longitude: Double,
            isSunrise: Boolean, zone: ZoneId
        ): java.time.LocalTime? {
            val day = date.dayOfYear
            val longitudeHour = longitude / 15.0
            val t = day + (((if (isSunrise) 6.0 else 18.0) - longitudeHour) / 24.0)
            val meanAnomaly = 0.9856 * t - 3.289
            var trueLongitude = meanAnomaly +
                1.916 * sin(Math.toRadians(meanAnomaly)) +
                0.020 * sin(2 * Math.toRadians(meanAnomaly)) + 282.634
            trueLongitude = (trueLongitude % 360 + 360) % 360
            var rightAscension = Math.toDegrees(atan(0.91764 * tan(Math.toRadians(trueLongitude))))
            rightAscension = (rightAscension % 360 + 360) % 360
            val lQuadrant = floor(trueLongitude / 90) * 90
            val raQuadrant = floor(rightAscension / 90) * 90
            rightAscension = (rightAscension + lQuadrant - raQuadrant) / 15.0
            val sinDeclination = 0.39782 * sin(Math.toRadians(trueLongitude))
            val cosDeclination = cos(asin(sinDeclination))
            val cosHourAngle = (cos(Math.toRadians(90.833)) -
                sinDeclination * sin(Math.toRadians(latitude))) /
                (cosDeclination * cos(Math.toRadians(latitude)))
            if (cosHourAngle > 1.0 || cosHourAngle < -1.0) return null
            val hourAngle = if (isSunrise) 360.0 - Math.toDegrees(acos(cosHourAngle))
                else Math.toDegrees(acos(cosHourAngle))
            val localMeanTime = hourAngle / 15.0 + rightAscension - 0.06571 * t - 6.622
            var utcHour = (localMeanTime - longitudeHour) % 24.0
            if (utcHour < 0) utcHour += 24.0
            val utcMinutes = (utcHour * 60.0).roundToLong()
            val utcInstant = date.atStartOfDay(ZoneOffset.UTC).toInstant()
                .plusSeconds(utcMinutes * 60L)
            return Instant.ofEpochSecond(utcInstant.epochSecond).atZone(zone).toLocalTime()
        }
    }
}
