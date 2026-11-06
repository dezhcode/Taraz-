package com.example.logic

import com.example.data.Backup
import com.example.data.BankCard
import com.example.data.BankSender
import com.example.data.PendingTransaction
import com.example.data.SmsIngest
import com.example.utils.JalaliDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Logic that needs no Android: dates of instalments, SMS reading, backup encryption. */
class PureLogicTest {

    // ─── Instalment months ───

    @Test
    fun nextMonthKeyRollsOverEsfand() {
        assertEquals(140601, JalaliDate.nextMonthKey(140512))
        assertEquals(140508, JalaliDate.nextMonthKey(140507))
    }

    @Test
    fun aPaymentSettlesTheCurrentMonthFirst() {
        val at = JalaliDate.toTimestamp(1405, 9, 20)
        assertEquals(140509, JalaliDate.settledMonthKey(15, at, emptySet()))
    }

    @Test
    fun aSecondPaymentGoesToTheNextUnpaidMonth() {
        val at = JalaliDate.toTimestamp(1405, 9, 2)
        assertEquals(140510, JalaliDate.settledMonthKey(15, at, setOf(140509)))
        assertEquals(140511, JalaliDate.settledMonthKey(15, at, setOf(140509, 140510)))
    }

    // ─── SMS ───

    @Test
    fun senderNumbersAreComparedWithoutTheCountryCode() {
        assertEquals("700717", SmsIngest.normalizeAddress("+98700717"))
        assertEquals("700717", SmsIngest.normalizeAddress("98700717"))
        assertEquals("700717", SmsIngest.normalizeAddress("0098700717"))
        assertEquals("1000670", SmsIngest.normalizeAddress("1000670"))
        assertTrue(SmsIngest.sameSender("+98700717", "700717"))
        assertFalse(SmsIngest.sameSender("700717", "700718"))
    }

    @Test
    fun rialsBecomeTomanUnlessTheSmsSaysToman() {
        assertEquals(1_250_000L, SmsIngest.toToman(12_500_000L, "برداشت 12,500,000 ریال"))
        assertEquals(12_500_000L, SmsIngest.toToman(12_500_000L, "مبلغ 12,500,000 تومان"))
        assertEquals(1_250_000L, SmsIngest.toToman(12_500_000L, "-12,500,000"))
    }

    @Test
    fun persianDigitsAreRead() {
        assertEquals("1405", SmsIngest.latinDigits("۱۴۰۵"))
    }

    private fun pending(sender: String, card: String) = PendingTransaction(
        senderNumber = sender, amount = 100, date = 0, isExpense = true, card = card,
        title = "", smsBody = "", smsBodyHash = "h"
    )

    @Test
    fun anSmsGoesToTheCardWithTheSameLastFourDigits() {
        val cards = listOf(
            BankCard(bankName = "ملت حقوق", cardNumber = "6104337412904218", balance = 0),
            BankCard(bankName = "بانک ملت", cardNumber = "6104331111111111", balance = 0)
        )
        val senders = listOf(BankSender(bankName = "بانک ملت", senderNumber = "700717"))
        assertEquals("ملت حقوق", SmsIngest.accountFor(pending("+98700717", "4218"), cards, senders))
    }

    @Test
    fun withoutCardDigitsTheSmsGoesToTheAccountOfItsBank() {
        val cards = listOf(BankCard(bankName = "بانک ملت", balance = 0))
        val senders = listOf(BankSender(bankName = "بانک ملت", senderNumber = "700717"))
        assertEquals("بانک ملت", SmsIngest.accountFor(pending("700717", ""), cards, senders))
    }

    @Test
    fun anUnknownCardNeverBecomesAnAccountNamedAfterItsDigits() {
        val senders = listOf(BankSender(bankName = "بانک سامان", senderNumber = "9821"))
        assertEquals("بانک سامان", SmsIngest.accountFor(pending("9821", "3142"), emptyList(), senders))
    }

    // ─── Backup ───

    @Test
    fun aBackupOpensWithItsPasswordOnly() {
        val data = Backup.encrypt("{\"transactions\":[],\"cards\":[]}", "secret-1")
        assertEquals("{\"transactions\":[],\"cards\":[]}", Backup.decrypt(data, "secret-1"))
        try {
            Backup.decrypt(data, "wrong")
            fail("a wrong password must not open the backup")
        } catch (e: Backup.WrongPasswordException) { /* expected */ }
    }

    @Test
    fun aRandomFileIsNotTakenForABackup() {
        try {
            Backup.decrypt("hello world, not a backup".toByteArray(), "x")
            fail("a random file must be refused")
        } catch (e: Backup.NotABackupException) { /* expected */ }
    }

    @Test
    fun weeklyDigestComparesWithTheWeekBeforeAndSkipsTransfers() {
        val now = 1_800_000_000_000L
        val day = 86_400_000L
        fun tx(amount: Long, daysAgo: Int, type: String = com.example.data.TransactionType.EXPENSE, cat: String = "خوراک") =
            com.example.data.Transaction(title = "t", amount = amount, category = cat, date = now - daysAgo * day,
                isExpense = type != com.example.data.TransactionType.INCOME, bankName = "ملت", type = type)
        val d = com.example.data.WeeklyDigest.build(
            listOf(
                tx(300_000, 1), tx(200_000, 2, cat = "حمل‌ونقل"), tx(5_000_000, 3, com.example.data.TransactionType.TRANSFER),
                tx(250_000, 9)
            ),
            emptyList(), emptyList(), emptyList(), now
        )
        org.junit.Assert.assertEquals(500_000L, d.spent)
        org.junit.Assert.assertEquals(250_000L, d.prevSpent)
        org.junit.Assert.assertEquals(100, d.changePercent)
        org.junit.Assert.assertEquals("خوراک", d.topCategory)
    }
}
