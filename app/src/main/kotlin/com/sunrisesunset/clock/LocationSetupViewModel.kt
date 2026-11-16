package com.sunrisesunset.clock

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class LocationSetupViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        const val LOCATION_TIMEOUT_MILLIS = 30_000L
    }

    private val repository = LocationRepository(application)
    private val _uiState = MutableStateFlow(LocationSetupUiState())
    val uiState: StateFlow<LocationSetupUiState> = _uiState.asStateFlow()

    fun loadSavedLocation() {
        viewModelScope.launch {
            if (repository.hasSavedLocation()) {
                _uiState.value = LocationSetupUiState(
                    message = "Approximate location is saved. Your widget can show local sunrise and sunset.",
                    hasSavedLocation = true
                )
            }
        }
    }

    fun permissionDenied() {
        _uiState.value = _uiState.value.copy(
            message = "Approximate location was not allowed. You can enable it in Android app permissions."
        )
    }

    fun findLocation() {
        if (_uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(
            message = "Finding your approximate location…",
            isLoading = true
        )
        viewModelScope.launch {
            try {
                val location: Location? = try {
                    withTimeout(LOCATION_TIMEOUT_MILLIS) { repository.findLocation() }
                } catch (_: TimeoutCancellationException) {
                    null
                }
                if (location == null) {
                    _uiState.value = LocationSetupUiState(
                        message = "No location fix was received. Turn on Location services and try again."
                    )
                } else {
                    repository.save(location)
                    withContext(Dispatchers.IO) { refreshWidgets() }
                    _uiState.value = LocationSetupUiState(
                        message = "Approximate location saved. Your widget is ready.",
                        hasSavedLocation = true
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: SecurityException) {
                _uiState.value = LocationSetupUiState(
                    message = "Location permission is needed. Please try again."
                )
            } catch (_: Exception) {
                _uiState.value = LocationSetupUiState(
                    message = "Could not get your location. Please try again."
                )
            } finally {
                if (_uiState.value.isLoading) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
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
