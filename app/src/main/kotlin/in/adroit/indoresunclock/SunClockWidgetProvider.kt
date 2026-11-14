package `in`.adroit.indoresunclock

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.*

class SunClockWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateWidget(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: android.content.Intent) {
        super.onReceive(context, intent)
        if (intent.action == android.content.Intent.ACTION_DATE_CHANGED ||
            intent.action == android.content.Intent.ACTION_TIME_CHANGED ||
            intent.action == android.content.Intent.ACTION_TIMEZONE_CHANGED) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, SunClockWidgetProvider::class.java)
            )
            onUpdate(context, manager, ids)
        }
    }

    companion object {
        private const val LAT = 22.7196
        private const val LON = 75.8577
        private val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

        fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.sun_clock_widget)
            val today = LocalDate.now(ZONE)
            val sunrise = solarTime(today, true)
            val sunset = solarTime(today, false)
            val timeFormat = DateTimeFormatter.ofPattern("hh:mm a", java.util.Locale.ENGLISH)
            val dateFormat = DateTimeFormatter.ofPattern("EEEE, d MMM", java.util.Locale.ENGLISH)

            views.setTextViewText(R.id.sunrise_time, sunrise.format(timeFormat))
            views.setTextViewText(R.id.sunset_time, sunset.format(timeFormat))
            views.setTextViewText(R.id.date_text, today.format(dateFormat) + " • Indore")
            manager.updateAppWidget(id, views)
        }

        // NOAA-style sunrise/sunset approximation; no internet or location permission needed.
        private fun solarTime(date: LocalDate, isSunrise: Boolean): java.time.LocalTime {
            val day = date.dayOfYear
            val longitudeHour = LON / 15.0
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
                sinDeclination * sin(Math.toRadians(LAT))) /
                (cosDeclination * cos(Math.toRadians(LAT)))

            if (cosHourAngle > 1.0 || cosHourAngle < -1.0) {
                return if (isSunrise) java.time.LocalTime.of(6, 0) else java.time.LocalTime.of(18, 0)
            }

            val hourAngle = if (isSunrise) 360.0 - Math.toDegrees(acos(cosHourAngle))
                            else Math.toDegrees(acos(cosHourAngle))
            val localMeanTime = hourAngle / 15.0 + rightAscension - 0.06571 * t - 6.622
            var utcHour = (localMeanTime - longitudeHour) % 24.0
            if (utcHour < 0) utcHour += 24.0
            val istHour = (utcHour + 5.5) % 24.0
            val totalMinutes = (istHour * 60).roundToInt()
            return java.time.LocalTime.of((totalMinutes / 60) % 24, totalMinutes % 60)
        }
    }
}
