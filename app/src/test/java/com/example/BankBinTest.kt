package com.example

import com.example.data.BankBin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BankBinTest {

    @Test
    fun aKnownPrefixIdentifiesItsBank() {
        assertEquals("بانک ملی", BankBin.bankFor("6037991112345678"))
        assertEquals("بانک ملت", BankBin.bankFor("6104337612345678"))
        assertEquals("بانک صادرات", BankBin.bankFor("6037691112345678"))
        assertEquals("بانک سامان", BankBin.bankFor("6219861012345678"))
    }

    @Test
    fun sixDigitsAreEnough() {
        // The logo should appear as soon as the issuer is known, not at digit 16.
        assertEquals("بانک ملی", BankBin.bankFor("603799"))
    }

    @Test
    fun fewerThanSixDigitsIdentifiesNothing() {
        assertNull(BankBin.bankFor("60379"))
        assertNull(BankBin.bankFor(""))
    }

    @Test
    fun anUnknownPrefixReturnsNullRatherThanAGuess() {
        assertNull(BankBin.bankFor("1234567890123456"))
    }

    @Test
    fun spacesFromTheFormattedFieldAreIgnored() {
        assertEquals("بانک ملی", BankBin.bankFor("6037 9911 1234 5678"))
    }
}
