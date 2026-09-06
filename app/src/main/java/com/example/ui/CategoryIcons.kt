package com.example.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Categories store an icon KEY, not a drawable — a name in the database
 * survives icon-set changes and can be written by the user without the app
 * knowing anything about vectors.
 */
object CategoryIcons {

    /** Offered when a user builds a category. Order is the order they see. */
    val keys: List<String> = listOf(
        "food", "transport", "bill", "shopping", "health", "home",
        "fun", "education", "gift", "saving", "wage", "loan",
        "clothing", "pet", "travel", "other"
    )

    fun vectorFor(key: String): ImageVector = when (key) {
        "food" -> Icons.Default.Restaurant
        "transport" -> Icons.Default.DirectionsCar
        "bill" -> Icons.Default.ReceiptLong
        "shopping" -> Icons.Default.ShoppingBag
        "health" -> Icons.Default.MedicalServices
        "home" -> Icons.Default.Home
        "fun" -> Icons.Default.SportsEsports
        "education" -> Icons.Default.School
        "gift" -> Icons.Default.CardGiftcard
        "saving" -> Icons.Default.Savings
        "wage" -> Icons.Default.Payments
        "loan" -> Icons.Default.AccountBalance
        "clothing" -> Icons.Default.Checkroom
        "pet" -> Icons.Default.Pets
        "travel" -> Icons.Default.Flight
        else -> Icons.Default.Category
    }

    /**
     * Colours a user can pick for a category. Every entry clears 3:1 against a
     * white surface, so a category chip is legible whichever one they choose.
     */
    val palette: List<String> = listOf(
        "#0B7A57", "#1D6FA3", "#7A5AA8", "#0E9490",
        "#D97706", "#B0532F", "#B03060", "#5C6B73"
    )

    fun colorOf(hex: String): Color = try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: IllegalArgumentException) {
        // A hand-edited or corrupted value must not crash the picker.
        Color(0xFF0B7A57)
    }
}
