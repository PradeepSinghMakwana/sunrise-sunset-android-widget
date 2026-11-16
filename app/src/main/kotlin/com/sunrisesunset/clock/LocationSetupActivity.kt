package com.sunrisesunset.clock

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel

class LocationSetupActivity : ComponentActivity() {
    private var setupViewModel: LocationSetupViewModel? = null

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            setupViewModel?.findLocation()
        } else {
            setupViewModel?.permissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val factory = LocationSetupViewModelFactory(application)
        setContent {
            val model: LocationSetupViewModel = viewModel(factory = factory)
            setupViewModel = model
            LocationSetupScreen(model) {
                if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    model.findLocation()
                } else {
                    locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }
        }
    }
}
