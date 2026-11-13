package com.example.logic

import com.example.data.BankBin
import com.example.sms.data.parser.BankSmsDetector
import com.example.utils.JalaliDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** شمارهٔ کارت با ارقام فارسی، سررسید قسطِ پرداخت‌نشده، شمارش روزها و تشخیص مبلغِ بدون جداکننده. */
class DatesAndCardsTest {

    private val noon = 12 * 3_600_000L
    private fun day(y: Int, m: Int, d: Int) = JalaliDate.toTimestamp(y, m, d)

    // ───────────── شمارهٔ کارت ─────────────

    @Test
    fun cardNumbersTypedWithAPersianKeyboardAreUnderstood() {
        assertEquals("6104337412904218", BankBin.digitsOf("۶۱۰۴ ۳۳۷۴ ۱۲۹۰ ۴۲۱۸"))
        assertEquals("6104337412904218", BankBin.digitsOf("٦١٠٤-٣٣٧٤-١٢٩٠-٤٢١٨"))
        assertEquals("610433", BankBin.digitsOf("6104 33"))
        assertEquals("بانک ملت", BankBin.bankFor("۶۱۰۴۳۳۷۴۱۲۹۰۴۲۱۸"))
        assertEquals("بانک ملت", BankBin.bankFor("6104337412904218"))
        assertNull(BankBin.bankFor("۶۱۰۴"))
    }

    // ───────────── قسط بعدی ─────────────

    @Test
    fun theNextInstalmentSkipsMonthsAlreadyPaid() {
        val from = day(1403, 8, 10) + noon
        // هنوز پرداخت نشده: همین ماه
        assertEquals(day(1403, 8, 15), JalaliDate.nextUnpaidDueTimestamp(15, emptySet(), from))
        // قسط آبان زودتر از موعد پرداخت شده: قسط بعدی آذر است، نه دوباره آبان
        assertEquals(day(1403, 9, 15), JalaliDate.nextUnpaidDueTimestamp(15, setOf(140308), from))
        assertEquals(day(1403, 10, 15), JalaliDate.nextUnpaidDueTimestamp(15, setOf(140308, 140309), from))
        // روز سررسید این ماه گذشته: ماه بعد، و اگر آن هم پرداخت شده ماه بعدتر
        assertEquals(day(1403, 9, 5), JalaliDate.nextUnpaidDueTimestamp(5, emptySet(), from))
        assertEquals(day(1403, 10, 5), JalaliDate.nextUnpaidDueTimestamp(5, setOf(140309), from))
        // پایان سال
        assertEquals(day(1404, 1, 20), JalaliDate.nextUnpaidDueTimestamp(20, setOf(140312), day(1403, 12, 1) + noon))
    }

    @Test
    fun aDueDayThatTheMonthDoesNotHaveFallsOnItsLastDay() {
        // مهر ۳۰ روز دارد
        assertEquals(day(1403, 7, 30), JalaliDate.nextUnpaidDueTimestamp(31, emptySet(), day(1403, 7, 1) + noon))
    }

    // ───────────── روزها ─────────────

    @Test
    fun daysAreCountedByCalendarDay() {
        val today = day(1403, 8, 17)
        assertEquals(0, JalaliDate.daysUntil(today + 23 * 3_600_000L, today + 60_000L))
        assertEquals(1, JalaliDate.daysUntil(day(1403, 8, 18) + 60_000L, today + 23 * 3_600_000L))
        assertEquals(-1, JalaliDate.daysUntil(day(1403, 8, 16) + noon, today + noon))
        assertEquals(14, JalaliDate.daysUntil(day(1403, 9, 1), today))
    }

    @Test
    fun aDayEndsWhereTheNextOneStarts() {
        val (start, end) = JalaliDate.dayRange(day(1403, 8, 30) + noon)
        assertEquals(day(1403, 8, 30), start)
        assertEquals(day(1403, 9, 1), end)
        assertEquals(JalaliDate(1403, 9, 1), JalaliDate.fromTimestamp(end))
        // آخرین لحظهٔ روز هنوز همان روز است
        assertEquals(JalaliDate(1403, 8, 30), JalaliDate.fromTimestamp(end - 1))
    }

    // ───────────── مبلغ بدون جداکنندهٔ هزارگان ─────────────

    @Test
    fun plainAmountsCountOnlyWithALabelAUnitOrASign() {
        listOf(
            "مبلغ:500000",
            "برداشت 1250000 از حساب 12345",
            "واریز: ۲۵۰۰۰۰۰",
            "+500000\nمانده 1000000",
            "480000-\nمانده 9520000",
            "500000 ریال",
            "1,250,000"
        ).forEach { assertTrue(it, BankSmsDetector.hasMoney(it)) }

        listOf(
            "برای اطلاعات بیشتر با +989121234567 تماس بگیرید",
            "0817-13:45",
            "1403/08/17 13:45",
            "کد پیگیری 123456789",
            "حساب 1234-5678-9012",
            "۳۰۰م وام اعتباری ویژه خرید کالا"
        ).forEach { assertFalse(it, BankSmsDetector.hasMoney(it)) }
    }

    @Test
    fun aBankSmsWithoutSeparatorsIsStillATransaction() {
        assertTrue(BankSmsDetector.isTransaction("برداشت:500000\nمانده:1200000\n1403/08/17"))
        assertTrue(BankSmsDetector.isTransaction("+500000\nمانده 1000000\n08/17-13:45"))
        assertFalse(BankSmsDetector.isTransaction("شماره پیگیری شما 500000 است"))
    }
}
