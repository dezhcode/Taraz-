package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The Horizon palette.
 *
 * Green carries "your money": the balance panel, incoming amounts, the primary
 * action. Outgoing amounts are terracotta rather than red — red against green
 * is the classic red-green colour-blindness failure (about 8% of men), and in
 * an app whose whole job is separating money in from money out, that is the
 * one distinction that must never rest on hue alone. Amount signs (+ / −) and
 * icon direction carry the same meaning, so colour is never the only cue.
 */

// Brand
val HorizonGreen = Color(0xFF2250F4)        // primary — panel, actions, income
val HorizonGreenBright = Color(0xFF0E9C6E)  // the area under the trend line
val HorizonGreenGlow = Color(0xFF6FE3B4)    // the trend line itself
val HorizonGreenTint = Color(0xFFEEF2FF)    // icon wells, quick tiles
val HorizonOnGreen = Color(0xFF9BD8C0)      // secondary text on the green panel

// Expense
val HorizonClay = Color(0xFFB0532F)
val HorizonClayTint = Color(0xFFFBEDE7)

// Neutrals — greyed toward green so the whole screen reads as one temperature
val HorizonBackground = Color(0xFFF5F6F8)
val HorizonSurface = Color(0xFFFFFFFF)
val HorizonBorder = Color(0xFFE1E4EA)
val HorizonDivider = Color(0xFFECEEF2)
val HorizonInk = Color(0xFF0E1116)          // primary text
val HorizonInkMuted = Color(0xFF5D6573)     // secondary text — passes 4.5:1 on white
val HorizonInkFaint = Color(0xFF6E7684)     // timestamps, units
