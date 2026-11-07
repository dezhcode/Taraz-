package com.example.sms.engine

import com.example.utils.JalaliDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineFacadeTest {

    private val engine = BankSmsEngine(TemplateSamples.loadDatabase()) { 1405 }

    private val melliSms = "بانك ملي ايران\nبرداشت:5,000,000-\nحساب:70008\nمانده:84,184,943\n0411-20:35"

    @Test
    fun melliMessageThroughTheChosenBank() {
        val r = engine.parse(melliSms, sender = "+9830009417", bankName = "بانک ملی")!!
        assertEquals("بانک ملی", r.bankName)
        assertFalse(r.isIncome)
        assertEquals(5_000_000L, r.amount)
        assertEquals(AmountUnit.RIAL, r.unit)
        assertEquals(500_000L, r.amountToman)
        assertEquals("1405-04-11", r.date)
        assertEquals("20:35", r.time)
    }

    @Test
    fun theBankIsFoundFromTheSenderWhenNoneIsChosen() {
        val r = engine.parse(melliSms, sender = "+9830009417")
        assertNotNull(r)
        assertEquals("بانک ملی", r!!.bankName)
        assertEquals(5_000_000L, r.amount)
    }

    @Test
    fun anUnknownSenderWithNoChosenBankIsNotGuessed() {
        assertNull(engine.parse(melliSms, sender = "+98123456789"))
    }

    @Test
    fun oneTimeCodesAreNeverTransactions() {
        assertNull(engine.parse("کد تایید: 123456\nخرید 1,000,000 ریال", "+9830009417", "ملی"))
        assertNull(engine.parse("رمز یکبار مصرف 5566 برای پرداخت 2,500,000 ریال", "+9830009417", "ملی"))
        assertNull(engine.parse("Your OTP is 1234 for 1,000,000 IRR", "+9830009417", "ملی"))
    }

    @Test
    fun anAdvertWithAnAmountButNoMoneyMovementIsNotATransaction() {
        assertNull(engine.parse("با ثبت نام در طرح جدید تا 5,000,000 ریال جایزه ببرید", "+9830009417", "ملی"))
    }

    @Test
    fun aBalanceInquiryReplyIsNotATransaction() {
        assertNull(engine.parse("بانک ملی\nموجودی حساب شما: 5,000,000 ریال\n1405/04/11 10:00", "+9830009417", "ملی"))
    }

    @Test
    fun tomanMessagesAreNotDividedAgain() {
        val r = engine.parse("برداشت 250,000 تومان از حساب 1234 در 1405/04/11 10:00", "+9830009417", "ملی")!!
        assertEquals(AmountUnit.TOMAN, r.unit)
        assertEquals(250_000L, r.amountToman)
    }

    @Test
    fun rialIsTheDefaultAndWinsWhenBothUnitsAreNamed() {
        assertEquals(AmountUnit.RIAL, BankSmsEngine.detectUnit("برداشت 1,000,000"))
        assertEquals(AmountUnit.RIAL, BankSmsEngine.detectUnit("1,000,000 ریال معادل 100,000 تومان"))
        assertEquals(AmountUnit.TOMAN, BankSmsEngine.detectUnit("100,000 تومان"))
    }

    @Test
    fun bluBankTextSentFromSamansNumberIsRead() {
        val r = engine.parse("بلوبانک\nمبلغ 100,000 ریال به حساب شما نشست\n1405/04/11 11:45", "+9820000", "سامان")
        assertNotNull(r)
        assertTrue(r!!.isIncome)
        assertEquals(100_000L, r.amount)
    }

    @Test
    fun aDateFromAnImplausibleYearIsDropped() {
        val r = engine.parse("خرید 1,000,000 ریال 98/04/11_20:35 مانده 5,000,000", "+9830009417", "تجارت")
        assertNotNull(r)
        assertNull(r!!.date)
    }

    @Test
    fun timestampPlacesTheTransactionOnItsDay() {
        val now = JalaliDate.toTimestamp(1405, 4, 12) + 9 * 3_600_000L
        val tx = EngineTransaction("بانک ملی", false, 100, AmountUnit.RIAL, "1405-04-11", "20:35", ParsedVia.PATTERN)
        assertEquals(JalaliDate.toTimestamp(1405, 4, 11) + (20 * 60 + 35) * 60_000L, tx.timestamp(now))
    }

    @Test
    fun aDateWithoutATimeIsPlacedAtMidday() {
        val now = JalaliDate.toTimestamp(1405, 4, 12)
        val tx = EngineTransaction("x", false, 100, AmountUnit.RIAL, "1405-04-11", null, ParsedVia.SCRIPT)
        assertEquals(JalaliDate.toTimestamp(1405, 4, 11) + 12 * 3_600_000L, tx.timestamp(now))
    }

    @Test
    fun noDateMeansNow() {
        val tx = EngineTransaction("x", false, 100, AmountUnit.RIAL, null, null, ParsedVia.SCRIPT)
        assertEquals(12345L, tx.timestamp(12345L))
    }

    @Test
    fun aYearlessDateThatWouldBeInTheFutureMovesBackAYear() {
        // Read in early Farvardin 1406, a message dated 12/25 can only be Esfand 1405.
        val now = JalaliDate.toTimestamp(1406, 1, 3) + 10 * 3_600_000L
        val tx = EngineTransaction("x", false, 100, AmountUnit.RIAL, "1406-12-25", "09:00", ParsedVia.SCRIPT)
        assertEquals(JalaliDate.toTimestamp(1405, 12, 25) + 9 * 3_600_000L, tx.timestamp(now))
    }

    @Test
    fun resultsAreAsciiEvenOnAPersianLocalePhone() {
        val saved = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale("fa", "IR"))
            val r = engine.parse(melliSms, sender = "+9830009417", bankName = "بانک ملی")!!
            assertEquals("1405-04-11", r.date)
            assertEquals("20:35", r.time)
            val viaPattern = engine.parse(
                "بانک ملت\nبرداشت مبلغ 500,000 ریال از حساب 12345\n1405/04/11 08:05", "+981000920000", "ملت"
            )!!
            assertEquals("1405-04-11", viaPattern.date)
            assertEquals("08:05", viaPattern.time)
        } finally {
            java.util.Locale.setDefault(saved)
        }
    }

    @Test
    fun theCatalogResolvesCardPrefixNames() {
        for ((cardName, expected) in listOf(
            "بانک ملی" to "ملی",
            "بانک رفاه کارگران" to "رفاه کارگران",
            "بانک قرض الحسنه رسالت" to "رسالت",
            "بانک قرض الحسنه مهر ایران" to "قرض الحسنه مهرایران",
            "بانک مهر اقتصاد" to "مهراقتصاد",
            "بانک ملل" to "ملل",
            "پست بانک" to "پست بانک",
            "بانک کارآفرین" to "کارافرین",
            "بانک ایران زمین" to "ایران زمین",
            "بانک حکمت ایرانیان" to "حکمت ایرانیان"
        )) {
            assertEquals(cardName, expected, BankCatalog.forCardBankName(cardName)?.patternName)
        }
        assertNull(BankCatalog.forCardBankName("بانک ناشناخته"))
    }
}
