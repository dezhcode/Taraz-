package com.example.sms.engine

/**
 * Text clean-up shared by both SMS engines.
 *
 * Bank messages arrive with Persian or Arabic-Indic digits, Arabic yeh/kaf, and
 * invisible bidi marks (the pattern database even has a U+202A inside one
 * template). Every regex in the two engines is written for plain ASCII digits
 * and Persian letters, so the text is normalised once, up front.
 */
object SmsText {

    /** Persian (U+06F0..) and Arabic-Indic (U+0660..) digits to ASCII. */
    fun convertEn(s: String): String {
        var needs = false
        for (ch in s) {
            if ((ch in '۰'..'۹') || (ch in '٠'..'٩')) { needs = true; break }
        }
        if (!needs) return s
        val sb = StringBuilder(s.length)
        for (ch in s) {
            sb.append(
                when (ch) {
                    in '۰'..'۹' -> '0' + (ch - '۰')
                    in '٠'..'٩' -> '0' + (ch - '٠')
                    else -> ch
                }
            )
        }
        return sb.toString()
    }

    /** Arabic yeh/kaf to the Persian letters, so «واريز» and «واریز» compare equal. */
    fun normalizeLetters(s: String): String =
        s.replace('ي', 'ی') // ي -> ی
            .replace('ى', 'ی') // ى -> ی
            .replace('ك', 'ک') // ك -> ک

    /** LRM, RLM, embedding/override/isolate marks: invisible, but they break regex adjacency. */
    fun stripBidi(s: String): String {
        var needs = false
        for (ch in s) {
            if (isBidi(ch)) { needs = true; break }
        }
        if (!needs) return s
        val sb = StringBuilder(s.length)
        for (ch in s) if (!isBidi(ch)) sb.append(ch)
        return sb.toString()
    }

    private fun isBidi(ch: Char): Boolean =
        ch == '‎' || ch == '‏' || ch == '؜' ||
            ch in '‪'..'‮' || ch in '⁦'..'⁩'

    /** What both engines receive: ASCII digits, no bidi marks, CRLF folded to LF. */
    fun cleanForParsing(raw: String): String =
        convertEn(stripBidi(raw)).replace("\r\n", "\n").replace('\r', '\n')

    /** A comparison key for bank names: no spaces/ZWNJ, Persian letters. */
    fun nameKey(s: String): String =
        normalizeLetters(s).filterNot { it.isWhitespace() || it == '‌' }
}
