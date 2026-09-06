package com.example

import com.example.data.AccountType
import com.example.data.BankCard
import com.example.data.isCash
import com.example.data.maskedNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cash used to be impossible to record: the add form demanded sixteen digits.
 * These pin the shape that made it possible.
 */
class AccountTypeTest {

    @Test
    fun cashNeedsNoCardNumber() {
        val cash = BankCard(bankName = "پول نقد", balance = 850_000, accountType = AccountType.CASH)
        assertTrue(cash.isCash)
        assertEquals("", cash.cardNumber)
        assertEquals("", cash.maskedNumber)
    }

    @Test
    fun existingRowsStayBankAccounts() {
        // Anything written before this migration had no accountType column.
        val card = BankCard(bankName = "بانک ملی", cardNumber = "6037991112345678", balance = 1)
        assertEquals(AccountType.BANK, card.accountType)
        assertFalse(card.isCash)
    }

    @Test
    fun onlyTheLastFourDigitsAreShown() {
        val card = BankCard(
            bankName = "بانک رسالت", cardNumber = "6037991112345678", balance = 0
        )
        assertEquals("•••• 5678", card.maskedNumber)
    }

    @Test
    fun aPartialCardNumberShowsNothingRatherThanGarbage() {
        val card = BankCard(bankName = "کارت", cardNumber = "60", balance = 0)
        assertEquals("", card.maskedNumber)
    }

    @Test
    fun anInitialBalanceIsKept() {
        // Net worth is wrong from day one if the opening balance is dropped.
        val cash = BankCard(bankName = "پول نقد", balance = 2_500_000, accountType = AccountType.CASH)
        assertEquals(2_500_000L, cash.balance)
    }
}
