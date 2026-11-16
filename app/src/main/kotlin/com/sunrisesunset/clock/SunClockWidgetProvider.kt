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
        if (intent.action == Intent.ACTION_DATE_CHANGED ||
            intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, SunClockWidgetProvider::class.java)
            )
            onUpdate(context, manager, ids)
        }
    }

    companion object {
        fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.sun_clock_widget)
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val prefs = context.getSharedPreferences(
                LocationSetupActivity.PREFS, Context.MODE_PRIVATE
            )
            val lat = prefs.getString(LocationSetupActivity.LATITUDE, null)?.toDoubleOrNull()
            val lon = prefs.getString(LocationSetupActivity.LONGITUDE, null)?.toDoubleOrNull()

            val timeFormat = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())
            val dateFormat = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault())

            if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                val sunrise = SolarCalculator.calculate(today, lat, lon, isSunrise = true, zone = zone)
                val sunset = SolarCalculator.calculate(today, lat, lon, isSunrise = false, zone = zone)
                views.setTextViewText(R.id.sunrise_time, sunrise?.format(timeFormat) ?: "—")
                views.setTextViewText(R.id.sunset_time, sunset?.format(timeFormat) ?: "—")
                views.setTextViewText(R.id.date_text, today.format(dateFormat))
            } else {
                views.setTextViewText(R.id.sunrise_time, "—")
                views.setTextViewText(R.id.sunset_time, "—")
                views.setTextViewText(R.id.date_text, "Open app to set approximate location")
            }

            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, LocationSetupActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            manager.updateAppWidget(id, views)
        }

    }
}
