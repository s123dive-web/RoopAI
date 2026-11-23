package com.suresh.roopai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.suresh.roopai.data.settings.ThemeMode

private val Dark = darkColorScheme(
    primary = Color(0xFFE7A6FF),
    onPrimary = Color(0xFF45105E),
    primaryContainer = Color(0xFF5E2A77),
    onPrimaryContainer = Color(0xFFF7D9FF),
    secondary = Color(0xFFFFB0CB),
    onSecondary = Color(0xFF541D34),
    secondaryContainer = Color(0xFF70334A),
    onSecondaryContainer = Color(0xFFFFD9E3),
    tertiary = Color(0xFFFFD27A),
    onTertiary = Color(0xFF402D00),
    background = Color(0xFF121016),
    onBackground = Color(0xFFE9E0EA),
    surface = Color(0xFF17141B),
    onSurface = Color(0xFFE9E0EA),
    surfaceVariant = Color(0xFF2B2630),
    onSurfaceVariant = Color(0xFFCFC3D2),
    surfaceContainer = Color(0xFF1E1A23),
    surfaceContainerHigh = Color(0xFF29242F),
    outline = Color(0xFF988E9C),
)

private val Light = lightColorScheme(
    primary = Color(0xFF7A3E96),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF7D9FF),
    onPrimaryContainer = Color(0xFF30004A),
    secondary = Color(0xFF9B4063),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E3),
    onSecondaryContainer = Color(0xFF3E001E),
    tertiary = Color(0xFF7A5900),
    background = Color(0xFFFFF7FC),
    onBackground = Color(0xFF1F1A21),
    surface = Color(0xFFFFF7FC),
    onSurface = Color(0xFF1F1A21),
    surfaceVariant = Color(0xFFECDFEE),
    onSurfaceVariant = Color(0xFF4D4451),
    surfaceContainer = Color(0xFFF6EBF7),
    surfaceContainerHigh = Color(0xFFF0E5F1),
    outline = Color(0xFF7E7482),
)

@Composable
fun RoopTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}

/** The editor always uses the dark palette so photo colours are judged on a neutral backdrop. */
@Composable
fun EditorTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Dark, content = content)
