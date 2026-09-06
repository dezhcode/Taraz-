package com.example.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Shows 6037991112345678 as 6037 9911 1234 5678 while the field keeps storing
 * bare digits.
 *
 * As with the amount field, the offset mapping is what makes it usable: a space
 * sits before digit i whenever i is a positive multiple of four, and the caret
 * has to step over those or editing in the middle of the number puts characters
 * in the wrong place.
 */
object CardNumberTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        if (digits.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val grouped = buildString {
            digits.forEachIndexed { index, ch ->
                if (index > 0 && index % 4 == 0) append(' ')
                append(ch)
            }
        }

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val safe = offset.coerceIn(0, digits.length)
                val spaces = if (safe == 0) 0 else (safe - 1) / 4
                return (safe + spaces).coerceIn(0, grouped.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val safe = offset.coerceIn(0, grouped.length)
                val spaces = grouped.take(safe).count { it == ' ' }
                return (safe - spaces).coerceIn(0, digits.length)
            }
        }
        return TransformedText(AnnotatedString(grouped), mapping)
    }
}
