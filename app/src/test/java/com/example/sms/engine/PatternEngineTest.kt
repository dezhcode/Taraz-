package com.example.sms.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternEngineTest {

    private val db = TemplateSamples.loadDatabase()
    private val engine = PatternEngine { 1405 }

    @Test
    fun jalaliPartsHandlesEveryShapeInTheDatabase() {
        val y = 1405
        assertEquals("1405-04-11", JalaliParts.parse("1405/04/11", y))
        assertEquals("1405-04-01", JalaliParts.parse("1405/4/1", y))
        assertEquals("1405-04-11", JalaliParts.parse("05/04/11", y))
        assertEquals("1405-04-11", JalaliParts.parse("04/11", y))
        assertEquals("1405-04-11", JalaliParts.parse("4/11", y))
        assertEquals("1405-04-11", JalaliParts.parse("0411", y))
        assertEquals("1405-04-11", JalaliParts.parse("0411-", y))
        assertEquals("1405-04-11", JalaliParts.parse("050411", y))
        assertEquals("1405-04-11", JalaliParts.parse("14050411", y))
        assertEquals("1405-04-11", JalaliParts.parse("05,04,11", y))
        assertEquals("1405-04-11", JalaliParts.parse("1405.04.11", y))
        assertEquals("1405-04-11", JalaliParts.parse("11 تیر 1405", y))
        assertEquals("1398-12-29", JalaliParts.parse("98/12/29", y))
    }

    @Test
    fun jalaliPartsRejectsWhatIsNotADate() {
        assertNull(JalaliParts.parse("", 1405))
        assertNull(JalaliParts.parse("1234", 1405))        // month 12, day 34
        assertNull(JalaliParts.parse("1405/13/01", 1405))
        assertNull(JalaliParts.parse("1405/07/31", 1405))  // Mehr has 30 days
        assertNull(JalaliParts.parse("2025/11/02", 1405))  // Gregorian
        assertNull(JalaliParts.parse("11 Aug 2025", 1405))
        assertNull(JalaliParts.parse("12345", 1405))
    }

    @Test
    fun directionComesFromWordsThenSign() {
        assertTrue(PatternEngine.messageIsIncome("واریز 1,000"))
        assertTrue(PatternEngine.messageIsIncome("واريز 1,000"))
        assertTrue(PatternEngine.messageIsIncome("مبلغ به حساب شما نشست"))
        assertFalse(PatternEngine.messageIsIncome("برداشت 1,000"))
        assertFalse(PatternEngine.messageIsIncome("خريد 1,000"))
        assertTrue(PatternEngine.messageIsIncome("+1,000"))
        assertFalse(PatternEngine.messageIsIncome("-1,000"))
        assertFalse("expense is the default", PatternEngine.messageIsIncome("1,000"))
    }

    @Test
    fun passwordTextsAreRejectedButFeesAreKept() {
        assertTrue(PatternEngine.isPasswordSms("رمز دوم شما: 12345"))
        assertTrue(PatternEngine.isPasswordSms("کد ورود: 1234"))
        assertTrue(PatternEngine.isPasswordSms("هشدار: تراکنش مشکوک"))
        assertFalse(PatternEngine.isPasswordSms("کارمزد 6,000 ریال"))
        assertTrue(PatternEngine.isPasswordSms("کارمزد 6,000 ریال، مدت زمان اعتبار تراکنش 2 دقیقه"))
        assertFalse(PatternEngine.isPasswordSms("برداشت 1,000"))
    }

    @Test
    fun timeIsValidated() {
        assertEquals("20:35", PatternEngine.formatTime("20:35"))
        assertEquals("08:05", PatternEngine.formatTime("8:5"))
        assertEquals("20:35", PatternEngine.formatTime("20:35:10"))
        assertNull(PatternEngine.formatTime("25:00"))
        assertNull(PatternEngine.formatTime("12:75"))
        assertNull(PatternEngine.formatTime("1235"))
    }

    @Test
    fun accountMaskingCharactersAreDropped() {
        assertEquals("1234567", PatternEngine.formatAccountNumber("12*34-56.7"))
    }

    @Test
    fun strictPassNeedsAllFiveFieldsAndRelaxedPassNeedsOnlyAmountAndAccount() {
        val melli = listOf(db.byName("ملی")!!)
        val full = "بانك ملي ايران\nبرداشت:5,000,000-\nحساب:70008\nمانده:84,184,943\n0411-20:35"
        val strict = engine.parseStrict(melli, full)
        assertNotNull(strict)
        assertTrue(strict!!.strict)
        assertEquals(5_000_000L, strict.amount)
        assertEquals("1405-04-11", strict.date)
        assertEquals("20:35", strict.time)

        val noTime = "بانك ملي ايران\nبرداشت:5,000,000-\nحساب:70008"
        assertNull(engine.parseStrict(melli, noTime))
        val relaxed = engine.parseRelaxed(melli, noTime)
        assertNotNull(relaxed)
        assertFalse(relaxed!!.strict)
        assertNull(relaxed.date)
        assertNull(relaxed.time)
    }

    @Test
    fun mellatNeedsTheWordAmount() {
        val mellat = listOf(db.byName("ملت")!!)
        assertNull(engine.parseRelaxed(mellat, "برداشت 500,000 ریال از حساب 12345"))
        assertNotNull(engine.parseRelaxed(mellat, "برداشت مبلغ 500,000 ریال از حساب 12345"))
    }

    @Test
    fun banksAreFoundByTheirSenderNumbers() {
        assertEquals(listOf("پاسارگاد"), db.forSender("+98500019000", "x").map { it.name })
        assertEquals(listOf("پاسارگاد"), db.forSender("b.pasargad", "x").map { it.name })
        assertEquals(listOf("آینده"), db.forSender("AyandehBank", "x").map { it.name })
        assertTrue(db.forSender("+98999999", "x").isEmpty())
    }

    @Test
    fun samanIsDroppedWhenTheTextDoesNotNameIt() {
        val bySaman = db.forSender("+9820000", "برداشت 1,000 ریال")
        assertTrue(bySaman.none { it.name.contains("سامان") })
        val withName = db.forSender("+9820000", "بانک سامان برداشت 1,000 ریال")
        assertTrue(withName.any { it.name.contains("سامان") })
    }

    @Test
    fun bluBankIsRecognisedByNameOrGateway() {
        assertTrue(db.forSender("whatever", "بلوبانک واریز").any { it.name.contains("بلو") })
        assertTrue(db.forSender("989999987641", "واریز").any { it.name.contains("بلو") })
    }

    @Test
    fun malformedDatabaseIsEmptyNotACrash() {
        assertTrue(PatternDatabase.parse("not json").banks.isEmpty())
        assertTrue(PatternDatabase.parse("{}").banks.isEmpty())
        assertTrue(PatternDatabase.parse("").banks.isEmpty())
    }
}
