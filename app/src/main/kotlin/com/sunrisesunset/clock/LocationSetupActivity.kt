package com.sunrisesunset.clock

import android.Manifest
import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object LocationPreferences {
    const val PREFS = "sunrise_location"
    const val LATITUDE = "latitude"
    const val LONGITUDE = "longitude"
}

data class LocationSetupUiState(
    val message: String = "Allow approximate location to calculate sunrise and sunset for your area.",
    val isLoading: Boolean = false,
    val hasSavedLocation: Boolean = false
)

class LocationRepository(private val context: Context) {
    suspend fun findLastKnownLocation(): Location? = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .firstNotNullOfOrNull { provider ->
                try {
                    if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider) else null
                } catch (_: SecurityException) {
                    null
                } catch (_: RuntimeException) {
                    null
                }
            }
    }

    suspend fun save(location: Location) = withContext(Dispatchers.IO) {
        context.getSharedPreferences(LocationPreferences.PREFS, Context.MODE_PRIVATE).edit()
            .putString(LocationPreferences.LATITUDE, location.latitude.toString())
            .putString(LocationPreferences.LONGITUDE, location.longitude.toString())
            .apply()
    }
}

class LocationSetupViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LocationRepository(application)
    private val _uiState = MutableStateFlow(LocationSetupUiState())
    val uiState: StateFlow<LocationSetupUiState> = _uiState.asStateFlow()

    fun loadSavedLocation() {
        val prefs = getApplication<Application>().getSharedPreferences(
            LocationPreferences.PREFS, Context.MODE_PRIVATE
        )
        val hasLocation = prefs.contains(LocationPreferences.LATITUDE) &&
            prefs.contains(LocationPreferences.LONGITUDE)
        if (hasLocation) {
            _uiState.value = LocationSetupUiState(
                message = "Approximate location is saved. Your widget can show local sunrise and sunset.",
                hasSavedLocation = true
            )
        }
    }

    fun findLocation() {
        if (_uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(
            message = "Finding your approximate location…",
            isLoading = true
        )
        viewModelScope.launch {
            try {
                val location = repository.findLastKnownLocation()
                if (location == null) {
                    _uiState.value = LocationSetupUiState(
                        message = "No recent location is available. Turn on Location services and try again."
                    )
                } else {
                    repository.save(location)
                    refreshWidgets()
                    _uiState.value = LocationSetupUiState(
                        message = "Approximate location saved. Your widget is ready.",
                        hasSavedLocation = true
                    )
                }
            } catch (_: SecurityException) {
                _uiState.value = LocationSetupUiState(
                    message = "Location permission is needed. Please try again."
                )
            } catch (_: Exception) {
                _uiState.value = LocationSetupUiState(
                    message = "Could not get your location. Please try again."
                )
            }
        }
    }

    private fun refreshWidgets() {
        val context = getApplication<Application>()
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, SunClockWidgetProvider::class.java)
        )
        ids.forEach { SunClockWidgetProvider.updateWidget(context, manager, it) }
    }
}

class LocationSetupViewModelFactory(private val application: Application) :
    ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LocationSetupViewModel::class.java)) {
            return LocationSetupViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class LocationSetupActivity : ComponentActivity() {
    private var setupViewModel: LocationSetupViewModel? = null
    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) setupViewModel?.findLocation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val factory = LocationSetupViewModelFactory(application)
        setContent {
            val model: LocationSetupViewModel = viewModel(factory = factory)
            setupViewModel = model
            val state by model.uiState.collectAsState()
            LaunchedEffect(Unit) { model.loadSavedLocation() }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Text("Sunrise Sunset Clock", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Use your approximate location to calculate local sunrise and sunset. Precise location is not requested.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(state.message, style = MaterialTheme.typography.bodyMedium)
                        if (state.isLoading) CircularProgressIndicator()
                        Button(
                            onClick = {
                                if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                                    PackageManager.PERMISSION_GRANTED) {
                                    model.findLocation()
                                } else {
                                    locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isLoading
                        ) {
                            Text(if (state.hasSavedLocation) "Refresh approximate location" else "Use approximate location")
                        }
                    }
                }
            }
        }
    }
}
