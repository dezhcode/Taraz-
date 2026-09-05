package com.example

import com.example.utils.JalaliDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class JalaliDateTest {

    @Test
    fun testReferenceDates() {
        assertEquals(JalaliDate(1404, 12, 29), JalaliDate.fromGregorian(2026, 3, 20))
        assertEquals(JalaliDate(1405, 1, 1), JalaliDate.fromGregorian(2026, 3, 21))
        assertEquals(JalaliDate(1404, 1, 1), JalaliDate.fromGregorian(2025, 3, 21))
        assertEquals(JalaliDate(1405, 7, 1), JalaliDate.fromGregorian(2026, 9, 23))
        assertEquals(JalaliDate(1404, 10, 11), JalaliDate.fromGregorian(2026, 1, 1))
        assertEquals(JalaliDate(1402, 12, 10), JalaliDate.fromGregorian(2024, 2, 29))
    }

    @Test
    fun testRoundTripConversion() {
        for (year in 1390..1420) {
            for (month in 1..12) {
                val daysInM = JalaliDate.daysInMonth(year, month)
                val testDays = intArrayOf(1, daysInM)
                for (day in testDays) {
                    val (gy, gm, gd) = JalaliDate.toGregorian(year, month, day)
                    val roundTrip = JalaliDate.fromGregorian(gy, gm, gd)
                    assertEquals("Mismatch for $year/$month/$day", JalaliDate(year, month, day), roundTrip)
                }
            }
        }
    }

    @Test
    fun testEsfandDays() {
        assertEquals("Esfand 1403 must have 30 days (leap year)", 30, JalaliDate.daysInMonth(1403, 12))
        assertTrue("Year 1403 is a leap year", JalaliDate.isLeapYear(1403))

        assertEquals("Esfand 1404 must have 29 days", 29, JalaliDate.daysInMonth(1404, 12))
        assertEquals("Year 1404 is not a leap year", false, JalaliDate.isLeapYear(1404))
    }

    @Test
    fun testMonthRangeForSeptember5th2026() {
        val cal = Calendar.getInstance(TimeZone.getDefault()).apply {
            clear()
            set(2026, Calendar.SEPTEMBER, 5, 12, 0, 0)
        }
        val (start, end) = JalaliDate.monthRange(cal.timeInMillis)

        val startDate = JalaliDate.fromTimestamp(start)
        val endDate = JalaliDate.fromTimestamp(end)

        assertEquals("Range must start on 1 Shahrivar 1405", JalaliDate(1405, 6, 1), startDate)
        assertEquals("Range must end on 1 Mehr 1405", JalaliDate(1405, 7, 1), endDate)
    }

    @Test
    fun testSeptember27th2026IsMehr() {
        val jDate = JalaliDate.fromGregorian(2026, 9, 27)
        assertEquals("Year must be 1405", 1405, jDate.year)
        assertEquals("Month must be 7 (Mehr)", 7, jDate.month)
        assertEquals("Month name must be مهر", "مهر", jDate.monthName)
    }
}
