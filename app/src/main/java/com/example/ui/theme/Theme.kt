package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = SurfaceWhite,
    secondary = NavySecondary,
    onSecondary = SurfaceWhite,
    tertiary = AlertOrange,
    onTertiary = SurfaceWhite,
    background = BackgroundLight,
    onBackground = NavySecondary,
    surface = SurfaceWhite,
    onSurface = NavySecondary,
    surfaceVariant = IceSlate,
    onSurfaceVariant = NavySecondary,
    error = ErrorRed,
    onError = SurfaceWhite
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Only Light Theme for Fintech App
    val colorScheme = LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
