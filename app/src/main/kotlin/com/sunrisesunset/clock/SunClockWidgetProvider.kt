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
            val views = RemoteViews(context.packageName, R.layout.sun_clock_widget)
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val prefs = context.getSharedPreferences(
                LocationSetupActivity.PREFS,
                Context.MODE_PRIVATE
            )
            val latitude = prefs.getString(
                LocationSetupActivity.LATITUDE,
                null
            )?.toDoubleOrNull()
            val longitude = prefs.getString(
                LocationSetupActivity.LONGITUDE,
                null
            )?.toDoubleOrNull()

            val timeFormat = DateTimeFormatter.ofPattern(TIME_PATTERN, Locale.getDefault())
            val dateFormat = DateTimeFormatter.ofPattern(DATE_PATTERN, Locale.getDefault())

            if (
                latitude != null &&
                longitude != null &&
                SolarCalculator.hasValidCoordinates(latitude, longitude)
            ) {
                val sunrise = SolarCalculator.calculate(
                    today,
                    latitude,
                    longitude,
                    isSunrise = true,
                    zone = zone
                )
                val sunset = SolarCalculator.calculate(
                    today,
                    latitude,
                    longitude,
                    isSunrise = false,
                    zone = zone
                )
                views.setTextViewText(
                    R.id.sunrise_time,
                    sunrise?.format(timeFormat) ?: NO_EVENT_TEXT
                )
                views.setTextViewText(
                    R.id.sunset_time,
                    sunset?.format(timeFormat) ?: NO_EVENT_TEXT
                )
                views.setTextViewText(R.id.date_text, today.format(dateFormat))
            } else {
                views.setTextViewText(R.id.sunrise_time, NO_EVENT_TEXT)
                views.setTextViewText(R.id.sunset_time, NO_EVENT_TEXT)
                views.setTextViewText(R.id.date_text, LOCATION_REQUIRED_TEXT)
            }

            val openApp = PendingIntent.getActivity(
                context,
                OPEN_APP_REQUEST_CODE,
                Intent(context, LocationSetupActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            manager.updateAppWidget(id, views)
        }
    }
}
