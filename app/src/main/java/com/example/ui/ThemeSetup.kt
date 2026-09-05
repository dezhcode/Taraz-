package com.example.ui

import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.ui.theme.IranYekanFontFamily

// --- Fidar Luxury Design System Color Palette ---

val EmeraldPrimary = Color(0xFF1F9D73) // Emerald Green - Income, Growth, Primary Button (#1F9D73)
val EmeraldHover = Color(0xFF167656)   // Hover/Active Green
val NavySecondary = Color(0xFF3D3D3D)  // Dark Gray - Typography, High Contrast
val IceSlate = Color(0xFFF1F5F9)       // Ice Slate - Accent backgrounds, light separators
val WhiteSurface = Color(0xFFFFFFFF)   // Pure White - Cards, elevated panels
val BackgroundLight = Color(0xFFF8FAFC) // Premium, light slate/ice gray background to separate cards and text clearly
val SurfaceWhite = Color(0xFFFFFFFF)    // Pure White - Cards, sheets, and floating components


// --- Status Colors ---
val SuccessGreen = Color(0xFF1F9D73)
val AlertOrange = Color(0xFFF59E0B)
val ErrorRed = Color(0xFFC93756)       // Rose Red - Expenses, warnings (#C93756)
val SlateGray = Color(0xFF64748B)

// Brand Gradients
val EmeraldNavyGradient = listOf(Color(0xFF167656), Color(0xFF1E293B))
val NavyGoldGradient = listOf(Color(0xFF0F172A), Color(0xFF1E293B))
val SoftGreenGradient = listOf(Color(0xFF1F9D73).copy(alpha = 0.15f), Color(0xFF1F9D73).copy(alpha = 0.05f))

// --- Typography Setup ---
// Modern sans-serif paired with precise letterSpacing and lineHeights to make Persian text extremely elegant.
val FidarTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = IranYekanFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = IranYekanFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = IranYekanFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = IranYekanFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = IranYekanFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = IranYekanFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.25.sp
    ),
    labelLarge = TextStyle(
        fontFamily = IranYekanFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = IranYekanFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

val FidarColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    secondary = NavySecondary,
    onSecondary = Color.White,
    tertiary = AlertOrange,
    onTertiary = Color.White,
    background = BackgroundLight,
    onBackground = NavySecondary,
    surface = WhiteSurface,
    onSurface = NavySecondary,
    surfaceVariant = IceSlate,
    onSurfaceVariant = NavySecondary,
    error = ErrorRed,
    onError = Color.White
)
