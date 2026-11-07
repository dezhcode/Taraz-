package com.example.logic

import com.example.sms.data.parser.EngineParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * موتور ساختاری پیامک. متن‌ها نمونهٔ ساختگی از «شکل»های رایج پیامک بانکی‌اند (برچسب‌دار، علامت‌دار،
 * جمله‌ای)، نه رونوشت پیامک بانک مشخص. قالب ویژهٔ هر بانک با نمونهٔ واقعی همان بانک اضافه می‌شود.
 */
class EngineParserTest {
    private val engine = EngineParser()
    private fun parse(body: String) = engine.parse(body, "بانک آزمون")

    @Test
    fun labeledLinesGiveAmountBalanceAndCard() {
        val r = parse("برداشت: 1,500,000\nمانده: 12,000,000\nکارت: ****1234")!!
        assertEquals("withdrawal", r.transactionType)
        assertEquals(1_500_000L, r.amount)
        assertEquals(12_000_000L, r.balance)
        assertEquals("1234", r.cardNumber)
    }

    @Test
    fun persianDigitsAndSeparatorsAreUnderstood() {
        val r = parse("واریز ۲۵٬۰۰۰٬۰۰۰ ریال به حساب شما\nمانده: ۳۷٬۰۰۰٬۰۰۰")!!
        assertEquals("deposit", r.transactionType)
        assertEquals(25_000_000L, r.amount)
        assertEquals(37_000_000L, r.balance)
    }

    @Test
    fun purchaseWithMerchantLine() {
        val r = parse("خرید 480,000 ریال\nپذیرنده: کافه نمونه\nمانده: 9,520,000")!!
        assertEquals("withdrawal", r.transactionType)
        assertEquals(480_000L, r.amount)
        assertEquals(9_520_000L, r.balance)
        assertEquals("کافه نمونه", r.merchant)
    }

    @Test
    fun signedAmountsDecideTheDirection() {
        val plus = parse("+25,000,000\nمانده: 37,000,000")!!
        assertEquals("deposit", plus.transactionType)
        assertEquals(25_000_000L, plus.amount)

        val minus = parse("-480,000\nمانده: 9,520,000")!!
        assertEquals("withdrawal", minus.transactionType)
        assertEquals(480_000L, minus.amount)

        val trailing = parse("480,000-\nمانده: 9,520,000")!!
        assertEquals("withdrawal", trailing.transactionType)
        assertEquals(480_000L, trailing.amount)
    }

    @Test
    fun sentenceStyle() {
        val r = parse("مبلغ 480,000 ریال از حساب شما برداشت شد. موجودی: 9,520,000 ریال")!!
        assertEquals("withdrawal", r.transactionType)
        assertEquals(480_000L, r.amount)
        assertEquals(9_520_000L, r.balance)
    }

    @Test
    fun balanceNeverBecomesTheAmount() {
        val r = parse("مانده پس از برداشت: 9,520,000\nبرداشت: 480,000")!!
        assertEquals(480_000L, r.amount)
        assertEquals(9_520_000L, r.balance)
    }

    @Test
    fun feeLineDoesNotHideTheRealAmount() {
        val r = parse("کارمزد: 5,000\nبرداشت: 1,000,000\nمانده: 4,000,000")!!
        assertEquals(1_000_000L, r.amount)
    }

    @Test
    fun cardFragmentsAndLongNumbersAreNotAmounts() {
        val a = parse("برداشت از حساب 1234 مبلغ 480,000")!!
        assertEquals(480_000L, a.amount)

        val b = parse("6037-9971-2345-6789 برداشت 480,000")!!
        assertEquals(480_000L, b.amount)
        assertEquals("6789", b.cardNumber)

        val c = parse("برداشت 480,000 ریال شماره پیگیری: 123456789012")!!
        assertEquals(480_000L, c.amount)
        assertEquals("123456789012", c.reference)
    }

    @Test
    fun dateAndTimeAreNotAmounts() {
        val r = parse("خرید 480,000 ریال 1405/08/18 10:42")!!
        assertEquals(480_000L, r.amount)
        val dashed = parse("خرید 480,000 ریال 1405-08-18")!!
        assertEquals(480_000L, dashed.amount)
    }

    @Test
    fun latinLabelsWork() {
        val r = parse("Withdraw 480,000 IRR")!!
        assertEquals("withdrawal", r.transactionType)
        assertEquals(480_000L, r.amount)
    }

    @Test
    fun whatIsNotATransactionIsNotGuessed() {
        // رمز یک‌بارمصرف، فقط مانده، و پیامی که جهتش روشن نیست
        assertNull(parse("رمز پویا: 482913\nمبلغ: 2,500,000 ریال"))
        assertNull(parse("مانده حساب شما 5,000,000 ریال است"))
        assertNull(parse("انتقال 300,000 ریال"))
        assertNull(parse(""))
    }

    @Test
    fun valuesStayInTheUnitOfTheSms() {
        // تبدیل ریال به تومان کار SmsIngest است؛ موتور عدد را همان‌طور که هست می‌دهد.
        val r = parse("برداشت: 1,500,000 ریال")!!
        assertNotNull(r)
        assertEquals(1_500_000L, r.amount)
    }
}
