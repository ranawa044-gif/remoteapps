package com.remoteapps.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF3B82F6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1D4ED8),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF10B981),
    onSecondary = Color.White,
    background = Color(0xFF0B0F19),
    surface = Color(0xFF151C2C),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC),
    error = Color(0xFFEF4444),
    onError = Color.White
)

@Composable
fun RemoteAppsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
