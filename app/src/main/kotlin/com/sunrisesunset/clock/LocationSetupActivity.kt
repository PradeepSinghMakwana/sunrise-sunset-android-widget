package com.sunrisesunset.clock

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class LocationSetupActivity : Activity(), LocationListener {
    private lateinit var status: TextView
    private lateinit var locationManager: LocationManager

    companion object {
        private const val REQUEST_LOCATION = 100
        const val PREFS = "sunrise_location"
        const val LATITUDE = "latitude"
        const val LONGITUDE = "longitude"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }

        val heading = TextView(this).apply {
            text = "Sunrise Sunset Clock"
            textSize = 25f
        }
        status = TextView(this).apply {
            textSize = 16f
            setPadding(0, 24, 0, 24)
        }
        val button = Button(this).apply {
            text = "Use approximate location"
            setOnClickListener { beginLocationSetup() }
        }

        layout.addView(heading)
        layout.addView(status)
        layout.addView(button)
        setContentView(layout)

        if (hasLocationPermission()) {
            findLocation()
        } else {
            status.text = "Allow approximate location to calculate sunrise and sunset for your area. Precise location is not requested."
        }
    }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun beginLocationSetup() {
        if (!hasLocationPermission()) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                REQUEST_LOCATION
            )
        } else {
            findLocation()
        }
    }

    private fun findLocation() {
        if (!hasLocationPermission()) return

        status.text = "Finding your approximate location…"

        try {
            val lastKnown = listOf(
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            ).firstNotNullOfOrNull { provider ->
                try {
                    if (locationManager.isProviderEnabled(provider)) {
                        locationManager.getLastKnownLocation(provider)
                    } else null
                } catch (_: Exception) {
                    null
                }
            }

            if (lastKnown != null) {
                saveLocation(lastKnown)
                return
            }

            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    0L,
                    0f,
                    this
                )
                status.text = "Waiting for a location fix. Keep this screen open briefly."
            } else {
                status.text = "Network location is unavailable. Enable location services and try again."
            }
        } catch (_: SecurityException) {
            status.text = "Location permission is needed. Please try again."
        } catch (e: Exception) {
            status.text = "Could not get location: ${e.localizedMessage ?: "try again"}"
        }
    }

    override fun onLocationChanged(location: Location) {
        saveLocation(location)
        try {
            locationManager.removeUpdates(this)
        } catch (_: Exception) {
        }
    }

    private fun saveLocation(location: Location) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(LATITUDE, location.latitude.toString())
            .putString(LONGITUDE, location.longitude.toString())
            .apply()

        status.text = "Approximate location saved. Your widget is ready."
        refreshWidgets()
    }

    private fun refreshWidgets() {
        val manager = AppWidgetManager.getInstance(this)
        val component = ComponentName(this, SunClockWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        ids.forEach { SunClockWidgetProvider.updateWidget(this, manager, it) }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                findLocation()
            } else {
                status.text = "Approximate location was not allowed. You can enable it in Android app permissions."
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            locationManager.removeUpdates(this)
        } catch (_: Exception) {
        }
    }
}
