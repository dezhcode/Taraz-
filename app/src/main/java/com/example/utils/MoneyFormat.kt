package com.example.utils

import java.text.NumberFormat
import java.util.Locale

/**
 * One place that decides how money looks, so the app never shows Latin digits
 * in one card and Persian digits in the next.
 *
 * Latin digits with comma grouping — 3,000,876 — everywhere amounts appear.
 * Persian digits stay for dates and counts, which JalaliDate already handles.
 */
object MoneyFormat {

    private val grouping: NumberFormat = NumberFormat.getNumberInstance(Locale.US)

    /** 3000876 -> "3,000,876" */
    fun amount(value: Long): String = grouping.format(value)

    /** A signed amount for transaction rows: "+30,000,000" / "−113,000". */
    fun signed(value: Long, isExpense: Boolean): String {
        val sign = if (isExpense) "−" else "+"   // real minus sign, not a hyphen
        return sign + grouping.format(kotlin.math.abs(value))
    }

    /** "3,000,876 تومان" */
    fun withUnit(value: Long): String = "${amount(value)} تومان"
}
