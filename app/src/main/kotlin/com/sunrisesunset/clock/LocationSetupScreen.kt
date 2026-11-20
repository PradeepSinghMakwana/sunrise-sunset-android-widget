package com.sunrisesunset.clock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val SunriseLightColors = lightColorScheme(
    primary = Color(0xFF9A4B16),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCC5),
    onPrimaryContainer = Color(0xFF351000),
    secondary = Color(0xFF755846),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCC5),
    onSecondaryContainer = Color(0xFF2A170A),
    tertiary = Color(0xFF5D5C8E),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF241A16),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF241A16),
    surfaceVariant = Color(0xFFF3E1D8),
    onSurfaceVariant = Color(0xFF51443D),
    outline = Color(0xFF85746B)
)

private val SunriseDarkColors = darkColorScheme(
    primary = Color(0xFFFFB68B),
    onPrimary = Color(0xFF572000),
    primaryContainer = Color(0xFF793400),
    onPrimaryContainer = Color(0xFFFFDCC5),
    secondary = Color(0xFFE6BEA7),
    onSecondary = Color(0xFF422B1D),
    secondaryContainer = Color(0xFF5B4030),
    onSecondaryContainer = Color(0xFFFFDCC5),
    tertiary = Color(0xFFC4C2FA),
    onTertiary = Color(0xFF2D2D5D),
    background = Color(0xFF1B120E),
    onBackground = Color(0xFFF2DFD6),
    surface = Color(0xFF1B120E),
    onSurface = Color(0xFFF2DFD6),
    surfaceVariant = Color(0xFF51443D),
    onSurfaceVariant = Color(0xFFD7C2B8),
    outline = Color(0xFF9F8D83)
)

@Composable
private fun SunriseSunsetTheme(content: @Composable () -> Unit) {
    val colors = if (androidx.compose.foundation.isSystemInDarkTheme()) {
        SunriseDarkColors
    } else {
        SunriseLightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun locationSetupScreen(
    viewModel: LocationSetupViewModel,
    onRequestLocationPermission: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(viewModel) { viewModel.loadSavedLocation() }

    SunriseSunsetTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("☀", style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Column {
                        Text(
                            "SUNRISE  /  SUNSET",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Your day, in its own rhythm",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Make every day\nfeel in sync.",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "Local sunrise and sunset times, right on your home screen.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "YOUR SOLAR DAY",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Text("☀  ☾", style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("SUNRISE", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("Morning", style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.SemiBold)
                            }
                            Column(horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("SUNSET", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("Evening", style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                                    CircleShape
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.58f)
                                    .height(7.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        }
                        Text(
                            "Times are calculated for your saved approximate location.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = MaterialTheme.shapes.large,
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("Set your location", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold)
                        Text(
                            "Allow approximate location to calculate accurate local solar times. " +
                                "Precise location is never requested.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.isLoading) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp)
                                Text("Finding your area…",
                                    style = MaterialTheme.typography.labelLarge)
                            }
                        } else {
                            Text(
                                state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = onRequestLocationPermission,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isLoading,
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(
                                if (state.hasSavedLocation) {
                                    "Refresh approximate location"
                                } else {
                                    "Use approximate location"
                                },
                                modifier = Modifier.padding(vertical = 5.dp)
                            )
                        }
                        if (state.hasSavedLocation) {
                            OutlinedButton(
                                onClick = onRequestLocationPermission,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !state.isLoading,
                                shape = MaterialTheme.shapes.large
                            ) {
                                Text("Update location")
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "PRIVATE BY DESIGN  •  APPROXIMATE LOCATION ONLY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
