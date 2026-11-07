package com.example.sms.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hand-written messages in the shapes the script parsers were written for. Device year is fixed at 1405. */
class ScriptEngineTest {

    private val script = ScriptEngine { 1405 }

    private fun parse(key: String, sms: String) = script.parse(key, sms)

    @Test
    fun melliWithdrawalHasTrailingMinusAndMonthDayTime() {
        val r = parse("melli", "بانك ملي ايران\nبرداشت:5,000,000-\nحساب:70008\nمانده:84,184,943\n0411-20:35")!!
        assertFalse(r.isIncome)
        assertEquals(5_000_000L, r.amount)
        assertEquals("1405-04-11", r.date)
        assertEquals("20:35", r.time)
    }

    @Test
    fun melliPurchaseWithFullDateAndSeconds() {
        val r = parse("melli", "بانك ملي ايران\nكارت: 3142\nخريد: 1,100,000\nمانده: 189,222,743\nتاريخ: 1405/04/10\nساعت: 18:07:05")!!
        assertFalse(r.isIncome)
        assertEquals(1_100_000L, r.amount)
        assertEquals("1405-04-10", r.date)
        assertEquals("18:07", r.time)
    }

    @Test
    fun melliInstalmentIsReadFromTheSignAlone() {
        val r = parse("melli", "بانك ملي ايران\nقسط:93,615,172-\nحساب:70008\nمانده:865,433\n0311-21:19")!!
        assertFalse(r.isIncome)
        assertEquals(93_615_172L, r.amount)
        assertEquals("1405-03-11", r.date)
    }

    @Test
    fun melliDepositIsIncome() {
        val r = parse("melli", "بانك ملي ايران\nواریز:2,000,000+\nحساب:70008\nمانده:3,000,000\n0412-09:05")!!
        assertTrue(r.isIncome)
        assertEquals(2_000_000L, r.amount)
        assertEquals("09:05", r.time)
    }

    @Test
    fun maskanIsMelliAfterDirectionMarksAreRemoved() {
        val r = parse("maskan", "‏بانک مسکن\n‎برداشت:750,000-\nحساب:1234\nمانده:9,000,000\n0502-10:15")!!
        assertFalse(r.isIncome)
        assertEquals(750_000L, r.amount)
        assertEquals("1405-05-02", r.date)
    }

    @Test
    fun resalatSignedAmountWithUnderscoreDateTime() {
        val r = parse("resalat", "10.13328262.1\n-1,060,000\n04/11_12:36\nمانده: 430,097,482")!!
        assertFalse(r.isIncome)
        assertEquals(1_060_000L, r.amount)
        assertEquals("1405-04-11", r.date)
        assertEquals("12:36", r.time)

        val deposit = parse("resalat", "10.13328262.1\n+5,500,000\n04/12_10:15\nمانده: 435,597,482")!!
        assertTrue(deposit.isIncome)
        assertEquals(5_500_000L, deposit.amount)
    }

    @Test
    fun standardBanksReadWordsAmountAndFullDate() {
        for (key in listOf("day", "mellat", "sepah", "shahr", "tejarat", "refah", "enbank", "keshavarzi", "karafarin", "gardeshgari", "parsian", "sina", "saman", "saderat")) {
            val r = parse(key, "بانک\nبرداشت از حساب 1234\nمبلغ: 2,500,000 ریال\n1405/04/11 14:30\nمانده: 10,000,000")
            assertNotNull(key, r)
            assertFalse(key, r!!.isIncome)
            assertEquals(key, 2_500_000L, r.amount)
            assertEquals(key, "1405-04-11", r.date)
            assertEquals(key, "14:30", r.time)
        }
    }

    @Test
    fun incomeWordsIncludingInterestAndRefund() {
        assertTrue(parse("mellat", "واریز 1,000,000 ریال 1405/04/11 10:00")!!.isIncome)
        assertTrue(parse("mellat", "سود سپرده 12,345 ریال 1405/04/11 10:00")!!.isIncome)
        assertTrue(parse("mellat", "برگشت پول 80,000 ریال 1405/04/11 10:00")!!.isIncome)
        assertTrue(parse("mellat", "مبلغ 500,000 ریال به حساب شما نشست 1405/04/11 10:00")!!.isIncome)
        assertFalse(parse("mellat", "مبلغ 500,000 ریال از حساب شما پرید 1405/04/11 10:00")!!.isIncome)
    }

    @Test
    fun yearlessFormatsTakeTheCurrentYear() {
        // F1  HH:MM-MMDD     F2  MM/DD-HH:MM     F4  HH:MM-MM/DD     F8  MMDD - HH:MM
        assertEquals("1405-04-11", parse("tejarat", "خرید 1,000,000 مانده 5,000,000 20:35-0411")!!.date)
        assertEquals("1405-04-11", parse("tejarat", "خرید 1,000,000 مانده 5,000,000 04/11-20:35")!!.date)
        assertEquals("1405-04-11", parse("tejarat", "خرید 1,000,000 مانده 5,000,000 20:35-04/11")!!.date)
        assertEquals("1405-04-11", parse("tejarat", "خرید 1,000,000 مانده 5,000,000 0411 - 20:35")!!.date)
    }

    @Test
    fun twoDigitYearsPivotAtEighty() {
        assertEquals("1405-04-11", parse("tejarat", "خرید 1,000,000 05/04/11 20:35")!!.date)
        // 98 is 1398; the original chain does not accept it (it would read it as 1498),
        // and the facade drops such an implausible year (see EngineFacadeTest).
        val old = parse("tejarat", "خرید 1,000,000 98/04/11_20:35")
        assertEquals("1498-04-11", old!!.date)
    }

    @Test
    fun podMessagesViaPasargadHaveNoDate() {
        val r = parse("pasargad", "*سرزمین هوشمند پاد*\nبرداشت\nمبلغ : 1,250,000ریال\nموجودی : 84,184,943ریال")!!
        assertFalse(r.isIncome)
        assertEquals(1_250_000L, r.amount)
        assertNull(r.date)
        assertNull(r.time)

        val allocated = parse("pasargad", "*ویپاد*\nاعتبار 300,000 ریال تخصیص داده شد")!!
        assertTrue(allocated.isIncome)
    }

    @Test
    fun pasargadOrdinaryMessagesUseTheStandardChain() {
        val r = parse("pasargad", "بانك پاسارگاد\nبرداشت از: 123.456.789.1\nمبلغ: 1,250,000 ريال\n20:35_05/04/11\nموجودي: 84,184,943 ريال")!!
        assertEquals(1_250_000L, r.amount)
        assertEquals("1405-04-11", r.date)
    }

    @Test
    fun samanTextAboutBluBankIsReadAsBlu() {
        val r = parse("saman", "بلوبانک\nمبلغ 100,000 ریال به حساب شما نشست\n1405/04/11 11:45")!!
        assertTrue(r.isIncome)
        assertEquals(100_000L, r.amount)
    }

    @Test
    fun bluBankAcceptsPersianDigits() {
        val r = parse("blubank", "واریز ۱,۲۵۰,۰۰۰ ریال\n۱۴۰۵/۰۴/۱۱ ۱۱:۴۵")
        // «۱,۲۵۰,۰۰۰» becomes 1,250,000 once digits are normalised.
        assertEquals(1_250_000L, r!!.amount)
        assertEquals("1405-04-11", r.date)
    }

    @Test
    fun ayandehSurvivesAMissingDate() {
        val r = parse("ayandeh", "بانک آینده\nواریز\n1,500,000\nمانده: 7,000,000")!!
        assertTrue(r.isIncome)
        assertEquals(1_500_000L, r.amount)
        assertNull(r.date)
    }

    @Test
    fun mellalReadsSixDigitDateWithSign() {
        val r = parse("mellal", "ملل\n2,000,000-\n040411-20:35\nمانده 9,000,000")!!
        assertFalse(r.isIncome)
        assertEquals("1404-04-11", r.date)
        assertEquals("20:35", r.time)
    }

    @Test
    fun toseTavonTwoDateFormats() {
        assertEquals("1405-04-11", parse("tosetavon", "برداشت 700,000 05/04/11_20:35")!!.date)
        assertEquals("1405-04-11", parse("tosetavon", "برداشت 700,000 04/11-20:35")!!.date)
    }

    @Test
    fun khavarmianeRebuildsTheDate() {
        assertEquals("1405-04-11", parse("khavarmiane", "برداشت\n900,000\n1405/04/11\n10:20")!!.date)
        assertEquals("1405-08-17", parse("khavarmiane", "برداشت\n900,000\n08/17\n10:20")!!.date)
    }

    @Test
    fun mehrIranianSignDecidesAndWordsBackItUp() {
        assertTrue(parse("mehriranian", "+3,000,000 1405/04/11 10:00")!!.isIncome)
        assertFalse(parse("mehriranian", "-3,000,000 1405/04/11 10:00")!!.isIncome)
        assertTrue(parse("mehriranian", "واریز سود 3,000,000 1405/04/11 10:00")!!.isIncome)
    }

    @Test
    fun oneTimeCodesAndWelcomeTextsAreNotTransactions() {
        assertNull(parse("mellat", "رمز پویا: 123456 مبلغ 1,000,000 ریال"))
        assertNull(parse("mellat", "کد تایید 123456 برای خرید 1,000,000 ریال"))
        assertNull(parse("mellat", "به همراه بانک خوش آمدید"))
    }

    @Test
    fun aFeeMessageIsKeptEvenThoughKarmozdContainsRamz() {
        val r = parse("mellat", "کارمزد انتقال 6,000 ریال 1405/04/11 10:00")
        assertNotNull(r)
        assertFalse(r!!.isIncome)
    }

    @Test
    fun balanceFigureIsNeverTheAmount() {
        val r = parse("mellat", "مانده: 12,000,000\nبرداشت: 500,000\n1405/04/11 10:00")!!
        assertEquals(500_000L, r.amount)
        assertNull("a balance-inquiry reply is not a transaction", parse("mellat", "موجودی: 5,000,000 ریال 1405/04/11 10:00"))
    }

    @Test
    fun noDateAtAllFailsStandardParsers() {
        assertNull(parse("mellat", "برداشت 500,000 ریال"))
    }

    @Test
    fun unknownKeyGivesNothing() {
        assertNull(parse("nosuchbank", "برداشت 500,000 ریال 1405/04/11"))
    }
}
