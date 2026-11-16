package com.sunrisesunset.clock

data class LocationSetupUiState(
    val message: String = "Allow approximate location to calculate sunrise and sunset for your area.",
    val isLoading: Boolean = false,
    val hasSavedLocation: Boolean = false
)
