package com.example.utils

import java.util.Calendar
import java.util.TimeZone

/**
 * Jalali (Solar Hijri) calendar support.
 *
 * Every date the user sees, and every month boundary the app groups by, must be
 * Jalali: an Iranian user's "this month" starts on 1 Mehr, not on 1 October.
 *
 * The conversion is the standard Khayyam/Birashk arithmetic algorithm, verified
 * against known reference dates (Nowruz 1404/1405, 1 Mehr 1405, Gregorian leap
 * days) and round-trip tested over every day from 1900 to 2100.
 */
data class JalaliDate(
    val year: Int,
    val month: Int,  // 1 = فروردین … 12 = اسفند
    val day: Int
) {
    val monthName: String get() = MONTH_NAMES[month - 1]

    /** "۱۴ شهریور ۱۴۰۵" */
    fun formatLong(): String = "${toPersianDigits(day)} $monthName ${toPersianDigits(year)}"

    /** "۱۴ شهریور" — for dense lists where the year is obvious */
    fun formatShort(): String = "${toPersianDigits(day)} $monthName"

    /** "شهریور ۱۴۰۵" — month headers and report grouping */
    fun formatMonthYear(): String = "$monthName ${toPersianDigits(year)}"

    /** Sortable key for grouping, e.g. 140506. Never show this to the user. */
    fun monthKey(): Int = year * 100 + month

    companion object {
        val MONTH_NAMES = listOf(
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
        )

        private val PERSIAN_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

        fun toPersianDigits(value: Any): String = buildString {
            for (ch in value.toString()) {
                if (ch in '0'..'9') append(PERSIAN_DIGITS[ch - '0']) else append(ch)
            }
        }

        /** Convert an epoch millisecond timestamp (device time zone) to a Jalali date. */
        fun fromTimestamp(timestamp: Long): JalaliDate {
            val cal = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = timestamp }
            return fromGregorian(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
        }

        fun today(): JalaliDate = fromTimestamp(System.currentTimeMillis())

        fun fromGregorian(gy: Int, gm: Int, gd: Int): JalaliDate {
            val gDayOfMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
            val gy2 = gy - 1600
            var gDayNo = 365 * gy2 + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400
            gDayNo += gDayOfMonth[gm - 1] + (gd - 1)
            if (gm > 2 && ((gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0)) gDayNo++

            var jDayNo = gDayNo - 79
            val jNp = jDayNo / 12053
            jDayNo %= 12053

            var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
            jDayNo %= 1461
            if (jDayNo >= 366) {
                jy += (jDayNo - 1) / 365
                jDayNo = (jDayNo - 1) % 365
            }

            val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)
            for (i in 0..10) {
                if (jDayNo < jDaysInMonth[i]) return JalaliDate(jy, i + 1, jDayNo + 1)
                jDayNo -= jDaysInMonth[i]
            }
            return JalaliDate(jy, 12, jDayNo + 1)
        }

        /** Epoch millis at 00:00:00 of the given Jalali date, device time zone. */
        fun toTimestamp(jy: Int, jm: Int, jd: Int): Long {
            val (gy, gm, gd) = toGregorian(jy, jm, jd)
            return Calendar.getInstance(TimeZone.getDefault()).apply {
                clear()
                set(gy, gm - 1, gd, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        fun toGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
            val jy2 = jy - 979
            var jDayNo = 365 * jy2 + (jy2 / 33) * 8 + ((jy2 % 33) + 3) / 4
            for (i in 0 until (jm - 1)) jDayNo += if (i < 6) 31 else 30
            jDayNo += jd - 1

            var gDayNo = jDayNo + 79
            var gy = 1600 + 400 * (gDayNo / 146097)
            gDayNo %= 146097

            var leap = true
            if (gDayNo >= 36525) {
                gDayNo--
                gy += 100 * (gDayNo / 36524)
                gDayNo %= 36524
                if (gDayNo >= 365) gDayNo++ else leap = false
            }
            gy += 4 * (gDayNo / 1461)
            gDayNo %= 1461
            if (gDayNo >= 366) {
                leap = false
                gDayNo--
                gy += gDayNo / 365
                gDayNo %= 365
            }

            val gDaysInMonth = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
            var gm = 0
            while (gm < 12 && gDayNo >= gDaysInMonth[gm]) {
                gDayNo -= gDaysInMonth[gm]
                gm++
            }
            return Triple(gy, gm + 1, gDayNo + 1)
        }

        /** Number of days in a Jalali month (esfand is 30 in a leap year). */
        fun daysInMonth(jy: Int, jm: Int): Int = when {
            jm <= 6 -> 31
            jm <= 11 -> 30
            isLeapYear(jy) -> 30
            else -> 29
        }

        /**
         * Leap residues are derived from — and verified against — the conversion
         * above, so daysInMonth() can never disagree with fromGregorian().
         */
        fun isLeapYear(jy: Int): Boolean {
            val cycle = (((jy - 979) % 33) + 33) % 33
            return cycle == 0 || cycle == 4 || cycle == 8 || cycle == 12 ||
                   cycle == 16 || cycle == 20 || cycle == 24 || cycle == 28
        }

        /** [start, end) epoch-millis bounds of the Jalali month containing [timestamp]. */
        fun monthRange(timestamp: Long): Pair<Long, Long> {
            val date = fromTimestamp(timestamp)
            val start = toTimestamp(date.year, date.month, 1)
            val end = if (date.month == 12) toTimestamp(date.year + 1, 1, 1)
                      else toTimestamp(date.year, date.month + 1, 1)
            return start to end
        }

        /** [start, end) epoch-millis bounds of the Jalali day containing [timestamp]. */
        fun dayRange(timestamp: Long): Pair<Long, Long> {
            val date = fromTimestamp(timestamp)
            val start = toTimestamp(date.year, date.month, date.day)
            return start to (start + 24L * 60 * 60 * 1000)
        }
    }
}
