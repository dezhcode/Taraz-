package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// طرح نهایی «تراز» (لایه) — همان توکن‌های بوم طراحی
val EbayBluePrimary: Color get() = if (com.example.ui.layer.LayerTheme.dark) Color(0xFF3D66FF) else Color(0xFF2250F4)   // Core eBay Evo Blue (#0064D2)
val EbayBlueHover: Color get() = if (com.example.ui.layer.LayerTheme.dark) Color(0xFF9AB0FF) else Color(0xFF1D44D8)
val EbayBlueLight: Color get() = if (com.example.ui.layer.LayerTheme.dark) Color(0xFF1C2546) else Color(0xFFEEF2FF)

val EbayRed: Color get() = if (com.example.ui.layer.LayerTheme.dark) Color(0xFFF06368) else Color(0xFFE5484D)           // eBay Red (#E53238)
val EbayRedLight: Color get() = if (com.example.ui.layer.LayerTheme.dark) Color(0xFF3A1A1C) else Color(0xFFFFF1F1)

val EbayYellow: Color get() = if (com.example.ui.layer.LayerTheme.dark) Color(0xFFF5A524) else Color(0xFFF59E0B)        // eBay Yellow (#F5AF02)
val EbayYellowLight: Color get() = if (com.example.ui.layer.LayerTheme.dark) Color(0xFF33270F) else Color(0xFFFEF3E2)

val EbayGreen: Color get() = if (com.example.ui.layer.LayerTheme.dark) Color(0xFF2BB283) else Color(0xFF0E8A5F)         // eBay Green (#058527)
val EbayGreenLight: Color get() = if (com.example.ui.layer.LayerTheme.dark) Color(0xFF10291F) else Color(0xFFE3F5EC)

// Neutral & Typography
val EbayDarkText: Color get() = if (com.example.ui.layer.LayerTheme.dark) Color(0xFFECEFF4) else Color(0xFF0E1116)       // Evo High-Contrast Dark Text
val EbaySecondaryText: Color get() = if (com.example.ui.layer.LayerTheme.dark) Color(0xFF9AA3B2) else Color(0xFF5D6573)  // Evo Secondary Gray Text
val EbayBorderGray: Color get() = if (com.example.ui.layer.LayerTheme.dark) Color(0xFF252B36) else Color(0xFFECEEF2)     // Evo Card Border
val EbayChipBackground: Color get() = if (com.example.ui.layer.LayerTheme.dark) Color(0xFF1F2430) else Color(0xFFF1F3F6) // Evo Chip / Pill Light Gray

// Compatibility Aliases mapped to eBay Evo System
val EmeraldPrimary: Color get() = EbayBluePrimary
val EmeraldHover: Color get() = EbayBlueHover
val EmeraldLight: Color get() = EbayBlueLight

val NavySecondary: Color get() = EbayDarkText
val SlateGray: Color get() = EbaySecondaryText
val IceSlate: Color get() = EbayChipBackground

// Background & Surfaces
val BackgroundLight: Color get() = if (com.example.ui.layer.LayerTheme.dark) Color(0xFF0B0E13) else Color(0xFFDDE1E8)
val SurfaceWhite: Color get() = if (com.example.ui.layer.LayerTheme.dark) Color(0xFF161A22) else Color(0xFFFFFFFF)

// Status Colors
val ErrorRed: Color get() = EbayRed
val ErrorLight: Color get() = EbayRedLight
val SuccessGreen: Color get() = EbayGreen
val AlertOrange: Color get() = EbayYellow


