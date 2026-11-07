package com.example.sms.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsSendersTest {

    @Test
    fun theSameBankNumberInEveryDressIsOneSender() {
        val forms = listOf("+98500019000", "98500019000", "098500019000", "0098500019000", "500019000")
        for (a in forms) for (b in forms) assertTrue("$a vs $b", SmsSenders.same(a, b))
    }

    @Test
    fun shortCodesLoseOnlyTheirCountryAndTrunkPrefix() {
        assertTrue(SmsSenders.same("+98170", "170"))
        assertTrue(SmsSenders.same("0170", "98170"))
        assertEquals("170", SmsSenders.canonical("+98170"))
        assertFalse(SmsSenders.same("170", "1700"))
    }

    @Test
    fun persianDigitsAndSpacingAreIgnored() {
        assertTrue(SmsSenders.same("۹۸۳۰۰۰۹۴۱۷", "983 000 9417"))
    }

    @Test
    fun nameSendersCompareCaseInsensitively() {
        assertTrue(SmsSenders.same("b.pasargad", "B.Pasargad"))
        assertTrue(SmsSenders.same("Bank Melli", "bankmelli"))
        assertFalse(SmsSenders.same("Melli", "Mellat"))
    }

    @Test
    fun emptyIsNeverTheSameAsEmpty() {
        assertFalse(SmsSenders.same("", ""))
        assertFalse(SmsSenders.same(null, null))
    }

    @Test
    fun numericDetection() {
        assertTrue(SmsSenders.isNumeric("+98500019000"))
        assertTrue(SmsSenders.isNumeric("30009417"))
        assertFalse(SmsSenders.isNumeric("Melli"))
        assertFalse(SmsSenders.isNumeric(null))
    }
}
