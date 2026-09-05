package com.example

import com.example.utils.JalaliDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The instalment date is what the whole reminder feature keys off, so the
 * awkward cases are pinned: month rollover, and days that do not exist.
 */
class LoanDueDateTest {

    private fun millis(jy: Int, jm: Int, jd: Int) = JalaliDate.toTimestamp(jy, jm, jd)

    @Test
    fun dueDayLaterThisMonthStaysInThisMonth() {
        val from = millis(1405, 6, 10)
        val due = JalaliDate.fromTimestamp(JalaliDate.nextDueTimestamp(20, from))
        assertEquals(JalaliDate(1405, 6, 20), due)
    }

    @Test
    fun dueDayAlreadyPassedRollsToNextMonth() {
        val from = millis(1405, 6, 25)
        val due = JalaliDate.fromTimestamp(JalaliDate.nextDueTimestamp(5, from))
        assertEquals(JalaliDate(1405, 7, 5), due)
    }

    @Test
    fun dueDayTodayIsToday() {
        val from = millis(1405, 6, 14)
        val due = JalaliDate.fromTimestamp(JalaliDate.nextDueTimestamp(14, from))
        assertEquals(JalaliDate(1405, 6, 14), due)
        assertEquals(0, JalaliDate.daysUntil(JalaliDate.nextDueTimestamp(14, from), from))
    }

    @Test
    fun dayThirtyOneClampsToThirtyInAThirtyDayMonth() {
        // Mehr has 30 days: an instalment "on the 31st" must land on 30 Mehr,
        // not slip into Aban.
        val from = millis(1405, 7, 1)
        val due = JalaliDate.fromTimestamp(JalaliDate.nextDueTimestamp(31, from))
        assertEquals(JalaliDate(1405, 7, 30), due)
    }

    @Test
    fun dayThirtyClampsToTwentyNineInOrdinaryEsfand() {
        val from = millis(1404, 12, 1)   // 1404 is not a leap year
        val due = JalaliDate.fromTimestamp(JalaliDate.nextDueTimestamp(30, from))
        assertEquals(JalaliDate(1404, 12, 29), due)
    }

    @Test
    fun rolloverFromEsfandLandsInFarvardinOfTheNextYear() {
        val from = millis(1404, 12, 20)
        val due = JalaliDate.fromTimestamp(JalaliDate.nextDueTimestamp(5, from))
        assertEquals(JalaliDate(1405, 1, 5), due)
    }

    @Test
    fun daysUntilCountsWholeDays() {
        val from = millis(1405, 6, 10)
        val due = JalaliDate.nextDueTimestamp(13, from)
        assertEquals(3, JalaliDate.daysUntil(due, from))
        assertTrue(due > from)
    }
}
