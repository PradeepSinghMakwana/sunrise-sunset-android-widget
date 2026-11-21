package com.sunrisesunset.clock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class SunClockWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateWidget(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (
            intent.action == Intent.ACTION_DATE_CHANGED ||
            intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, SunClockWidgetProvider::class.java)
            )
            onUpdate(context, manager, ids)
        }
    }

    companion object {
        private const val OPEN_APP_REQUEST_CODE = 0
        private const val TIME_PATTERN = "hh:mm a"
        private const val DATE_PATTERN = "EEEE, d MMM"
        private const val NO_EVENT_TEXT = "—"
        private const val LOCATION_REQUIRED_TEXT = "Open app to set approximate location"

        fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            manager.updateAppWidget(id, buildRemoteViews(context))
        }

        internal fun buildRemoteViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.sun_clock_widget)
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val coordinates = readCoordinates(context)

            if (coordinates == null) {
                showLocationRequired(views)
            } else {
                showSolarEvents(views, today, coordinates, zone)
            }

            attachOpenAppAction(context, views)
            return views
        }

        private fun readCoordinates(context: Context): Pair<Double, Double>? {
            val prefs = context.getSharedPreferences(
                LocationPreferences.PREFS,
                Context.MODE_PRIVATE
            )
            val latitude = prefs.getString(
                LocationPreferences.LATITUDE,
                null
            )?.toDoubleOrNull()
            val longitude = prefs.getString(
                LocationPreferences.LONGITUDE,
                null
            )?.toDoubleOrNull()

            return if (
                latitude != null &&
                longitude != null &&
                SolarCalculator.hasValidCoordinates(latitude, longitude)
            ) {
                latitude to longitude
            } else {
                null
            }
        }

        private fun showSolarEvents(
            views: RemoteViews,
            date: LocalDate,
            coordinates: Pair<Double, Double>,
            zone: ZoneId
        ) {
            val timeFormat = DateTimeFormatter.ofPattern(TIME_PATTERN, Locale.getDefault())
            val dateFormat = DateTimeFormatter.ofPattern(DATE_PATTERN, Locale.getDefault())
            val (latitude, longitude) = coordinates
            val sunrise = SolarCalculator.calculate(date, latitude, longitude, true, zone)
            val sunset = SolarCalculator.calculate(date, latitude, longitude, false, zone)

            views.setTextViewText(
                R.id.sunrise_time,
                sunrise?.format(timeFormat) ?: NO_EVENT_TEXT
            )
            views.setTextViewText(
                R.id.sunset_time,
                sunset?.format(timeFormat) ?: NO_EVENT_TEXT
            )
            views.setTextViewText(R.id.date_text, date.format(dateFormat))
        }

        private fun showLocationRequired(views: RemoteViews) {
            views.setTextViewText(R.id.sunrise_time, NO_EVENT_TEXT)
            views.setTextViewText(R.id.sunset_time, NO_EVENT_TEXT)
            views.setTextViewText(R.id.date_text, LOCATION_REQUIRED_TEXT)
        }

        private fun attachOpenAppAction(context: Context, views: RemoteViews) {
            val openApp = PendingIntent.getActivity(
                context,
                OPEN_APP_REQUEST_CODE,
                Intent(context, LocationSetupActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
        }
    }
}
