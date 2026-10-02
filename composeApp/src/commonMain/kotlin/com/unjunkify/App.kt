package com.unjunkify

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.unjunkify.ui.CleanScreen
import com.unjunkify.ui.CleanViewModel
import com.unjunkify.ui.ExemptScreen
import com.unjunkify.ui.HealthScreen
import com.unjunkify.ui.HealthViewModel
import com.unjunkify.ui.theme.GlassGradientEndDark
import com.unjunkify.ui.theme.GlassGradientEndLight
import com.unjunkify.ui.theme.GlassGradientMidDark
import com.unjunkify.ui.theme.GlassGradientMidLight
import com.unjunkify.ui.theme.GlassGradientStartDark
import com.unjunkify.ui.theme.GlassGradientStartLight
import com.unjunkify.ui.theme.UnjunkifyTheme
import com.unjunkify.ui.theme.ThemeMode

private enum class HealthTab { Clean, Exempt, Health }

@Composable
fun UnjunkifyApp(
    cleanViewModel: CleanViewModel,
    healthViewModel: HealthViewModel,
    onRequestMediaPermission: () -> Unit = {},
    onRequestUsageAccess: () -> Unit = {},
) {
    var themeMode by remember { mutableStateOf(ThemeMode.System) }
    var currentTab by remember { mutableStateOf(HealthTab.Clean) }

    UnjunkifyTheme(themeMode = themeMode) {
        val isDark = when (themeMode) {
            ThemeMode.System -> isSystemInDarkTheme()
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }

        val gradientColors = if (isDark) {
            listOf(GlassGradientStartDark, GlassGradientMidDark, GlassGradientEndDark)
        } else {
            listOf(GlassGradientStartLight, GlassGradientMidLight, GlassGradientEndLight)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = gradientColors,
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY,
                    )
                )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
            ) {
                Scaffold(
                    containerColor = Color.Transparent,
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.60f),
                            tonalElevation = 0.dp,
                        ) {
                            NavigationBarItem(
                                selected = currentTab == HealthTab.Clean,
                                onClick = { currentTab = HealthTab.Clean },
                                icon = { Icon(Icons.Default.CleaningServices, contentDescription = "Clean") },
                                label = { Text("Clean") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                ),
                            )
                            NavigationBarItem(
                                selected = currentTab == HealthTab.Exempt,
                                onClick = { currentTab = HealthTab.Exempt },
                                icon = { Icon(Icons.Default.Shield, contentDescription = "Exempt") },
                                label = { Text("Exempt") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                ),
                            )
                            NavigationBarItem(
                                selected = currentTab == HealthTab.Health,
                                onClick = { currentTab = HealthTab.Health },
                                icon = { Icon(Icons.Default.Favorite, contentDescription = "Health") },
                                label = { Text("Health") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                ),
                            )
                        }
                    }
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {
                        when (currentTab) {
                            HealthTab.Clean -> CleanScreen(
                                viewModel = cleanViewModel,
                                onRequestMediaPermission = onRequestMediaPermission,
                            )
                            HealthTab.Exempt -> ExemptScreen(viewModel = cleanViewModel)
                            HealthTab.Health -> HealthScreen(
                                viewModel = healthViewModel,
                                onRequestUsageAccess = onRequestUsageAccess,
                            )
                        }
                    }
                }
            }
        }
    }
}
