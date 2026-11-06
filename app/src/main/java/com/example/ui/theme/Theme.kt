package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.example.ui.layer.L
import com.example.ui.layer.LayerTheme

/**
 * تم برنامه: رنگ‌های Material از توکن‌های طرح نهایی ساخته می‌شوند؛
 * حالت روشن/تیره را [LayerTheme.dark] تعیین می‌کند (انتخاب کاربر، نه تنظیم گوشی).
 */
@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    val dark = LayerTheme.dark
    val colorScheme = if (dark) {
        darkColorScheme(
            primary = L.Primary,
            onPrimary = androidx.compose.ui.graphics.Color.White,
            secondary = L.PrimaryText,
            onSecondary = L.Bg,
            tertiary = L.Amber,
            background = L.Bg,
            onBackground = L.Ink,
            surface = L.Surface,
            onSurface = L.Ink,
            surfaceVariant = L.Chip,
            onSurfaceVariant = L.Muted,
            surfaceContainer = L.Surface,
            surfaceContainerHigh = L.Surface,
            surfaceContainerHighest = L.Chip,
            surfaceContainerLow = L.Surface,
            outline = L.Rail,
            outlineVariant = L.Line,
            error = L.Red,
            onError = androidx.compose.ui.graphics.Color.White
        )
    } else {
        lightColorScheme(
            primary = L.Primary,
            onPrimary = androidx.compose.ui.graphics.Color.White,
            secondary = L.Ink,
            onSecondary = androidx.compose.ui.graphics.Color.White,
            tertiary = L.Amber,
            onTertiary = androidx.compose.ui.graphics.Color.White,
            background = L.Bg,
            onBackground = L.Ink,
            surface = L.Surface,
            onSurface = L.Ink,
            surfaceVariant = L.Chip,
            onSurfaceVariant = L.Ink,
            surfaceContainer = L.Surface,
            surfaceContainerHigh = L.Surface,
            surfaceContainerHighest = L.Chip,
            surfaceContainerLow = L.Surface,
            outline = L.Rail,
            outlineVariant = L.Line,
            error = L.Red,
            onError = androidx.compose.ui.graphics.Color.White
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
