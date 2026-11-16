package com.sunrisesunset.clock

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun locationSetupScreen(
    viewModel: LocationSetupViewModel,
    onRequestLocationPermission: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(viewModel) { viewModel.loadSavedLocation() }

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
                    onClick = onRequestLocationPermission,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading
                ) {
                    Text(
                        if (state.hasSavedLocation) {
                            "Refresh approximate location"
                        } else {
                            "Use approximate location"
                        }
                    )
                }
            }
        }
    }
}
