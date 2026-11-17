package com.sunrisesunset.clock

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class LocationSetupViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LocationSetupViewModel::class.java)) {
            return LocationSetupViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
