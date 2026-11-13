package com.example.logic

import com.example.data.BankCard
import com.example.data.BankSender
import com.example.data.Category
import com.example.data.PendingTransaction
import com.example.data.SmsApproval
import com.example.data.SmsIngest
import org.junit.Assert.assertEquals
import org.junit.Test

/** دستهٔ پیشنهادی تراکنش پیامکی و پیدا کردن حساب آن. */
class SmsApprovalLogicTest {

    private val categories = listOf(
        Category(name = "غذا", sortOrder = 0),
        Category(name = "خرید", sortOrder = 2),
        Category(name = "قسط", sortOrder = 6),
        Category(name = "سایر", sortOrder = 7),
        Category(name = "حقوق", isIncome = true, sortOrder = 8),
        Category(name = "سایر درآمدها", isIncome = true, sortOrder = 9)
    )

    private fun sms(body: String, isExpense: Boolean, card: String = "") = PendingTransaction(
        senderNumber = "700717", amount = 480_000, date = 0, isExpense = isExpense, card = card,
        title = "تراکنش پیامکی بانک ملت", smsBody = body, smsBodyHash = "h"
    )

    @Test
    fun theSuggestedCategoryAlwaysExists() {
        // «درآمد» دسته نیست؛ درآمدِ بی‌نشانه «سایر درآمدها» می‌شود
        assertEquals("سایر درآمدها", SmsApproval.guessCategory(sms("+2,000,000\nمانده 5,000,000", false), categories))
        assertEquals("سایر", SmsApproval.guessCategory(sms("برداشت 480,000", true), categories))
    }

    @Test
    fun aCategoryNamedInTheSmsIsPreferred() {
        assertEquals("خرید", SmsApproval.guessCategory(sms("خرید 480,000 ریال", true), categories))
        assertEquals("حقوق", SmsApproval.guessCategory(sms("واریز حقوق 25,000,000", false), categories))
        // دستهٔ هزینه برای درآمد پیشنهاد نمی‌شود
        assertEquals("سایر درآمدها", SmsApproval.guessCategory(sms("برگشت خرید 480,000+", false), categories))
    }

    @Test
    fun withoutTheUsualCategoriesAnotherOneOfTheSameKindIsUsed() {
        val own = listOf(Category(name = "خانه"), Category(name = "کار آزاد", isIncome = true))
        assertEquals("خانه", SmsApproval.guessCategory(sms("برداشت 480,000", true), own))
        assertEquals("کار آزاد", SmsApproval.guessCategory(sms("واریز 480,000", false), own))
        assertEquals("سایر", SmsApproval.guessCategory(sms("برداشت 480,000", true), emptyList()))
        assertEquals("سایر درآمدها", SmsApproval.guessCategory(sms("واریز 480,000", false), emptyList()))
    }

    @Test
    fun aCardSavedWithPersianDigitsIsStillFound() {
        val cards = listOf(
            BankCard(bankName = "ملت حقوق", cardNumber = "۶۱۰۴۳۳۷۴۱۲۹۰۴۲۱۸", balance = 0),
            BankCard(bankName = "بانک ملت", cardNumber = "6104331111111111", balance = 0)
        )
        val senders = listOf(BankSender(bankName = "بانک ملت", senderNumber = "700717"))
        assertEquals("ملت حقوق", SmsIngest.accountFor(sms("برداشت", true, card = "4218"), cards, senders))
    }
}
