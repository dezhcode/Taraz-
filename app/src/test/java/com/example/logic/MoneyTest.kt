package com.example.logic

import com.example.data.AppCurrency
import com.example.data.Money
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** واحد پول: نمایش، ورود، مبلغ پیامک و تبدیل بین تومان، ریال و دلار (سنت). */
class MoneyTest {

    @After
    fun reset() = Money.setForTest(AppCurrency.TOMAN)

    @Test
    fun formatsEachCurrency() {
        assertEquals("۱٬۲۳۴٬۵۶۷", Money.format(1_234_567, AppCurrency.TOMAN))
        assertEquals("۱٬۲۳۴٬۵۶۷", Money.format(1_234_567, AppCurrency.RIAL))
        assertEquals("۱٬۲۳۴٫۵۶", Money.format(123_456, AppCurrency.USD))
        assertEquals("۰٫۰۵", Money.format(5, AppCurrency.USD))
        assertEquals("-۱۲٫۵۰", Money.format(-1_250, AppCurrency.USD))
        assertEquals("1,234.56", Money.formatLatin(123_456, AppCurrency.USD))
        assertEquals("۱۲٫۵۰ دلار", Money.withLabel(1_250, AppCurrency.USD))
        assertEquals("۵۰۰ ریال", Money.withLabel(500, AppCurrency.RIAL))
    }

    @Test
    fun readsTypedAmounts() {
        assertEquals(12_500L, Money.parse("۱۲٬۵۰۰", AppCurrency.TOMAN))
        // تومان و ریال اعشار ندارند: ممیز نادیده گرفته می‌شود
        assertEquals(125L, Money.parse("12.5", AppCurrency.TOMAN))
        assertEquals(1_250L, Money.parse("12.5", AppCurrency.USD))
        assertEquals(1_250L, Money.parse("۱۲٫۵", AppCurrency.USD))
        assertEquals(123_456L, Money.parse("1,234.56", AppCurrency.USD))
        assertEquals(1_200L, Money.parse("12.", AppCurrency.USD))
        assertEquals(5L, Money.parse("0.05", AppCurrency.USD))
        // بیش از دو رقم اعشار کنار می‌رود
        assertEquals(1_299L, Money.parse("12.999", AppCurrency.USD))
        assertEquals(0L, Money.parse("abc", AppCurrency.USD))
    }

    @Test
    fun keepsTheDecimalPointWhileTyping() {
        assertEquals("۱۲٫", Money.inputText("12.", AppCurrency.USD))
        assertEquals("۱٬۲۳۴٫۵", Money.inputText("1234.5", AppCurrency.USD))
        assertEquals("۱٬۲۳۴٫۵۶", Money.inputText("۱٬۲۳۴٫۵۶۷", AppCurrency.USD))
        assertEquals("۰٫", Money.inputText(".", AppCurrency.USD))
        assertEquals("۱۲٬۵۰۰", Money.inputText("12500", AppCurrency.TOMAN))
        assertEquals("", Money.inputText("", AppCurrency.USD))
        assertEquals("12.5", Money.toInput(1_250, AppCurrency.USD))
        assertEquals("12", Money.toInput(1_200, AppCurrency.USD))
        assertEquals("12.05", Money.toInput(1_205, AppCurrency.USD))
        assertEquals("125000", Money.toInput(125_000, AppCurrency.RIAL))
    }

    @Test
    fun bankSmsAmountsInRialAreKeptExactly() {
        val body = "برداشت 1,234,567 ریال مانده 9,876,543"
        assertEquals(1_234_567L, Money.fromSms(1_234_567, body, AppCurrency.RIAL))
        assertEquals(123_456L, Money.fromSms(1_234_567, body, AppCurrency.TOMAN))
        assertNull(Money.fromSms(1_234_567, body, AppCurrency.USD))
        // پیامکی که صریحاً تومان می‌گوید
        val toman = "واریز 50,000 تومان"
        assertEquals(500_000L, Money.fromSms(50_000, toman, AppCurrency.RIAL))
        assertEquals(50_000L, Money.fromSms(50_000, toman, AppCurrency.TOMAN))
    }

    @Test
    fun convertsBetweenCurrencies() {
        assertEquals(1_250L, Money.convert(125, AppCurrency.TOMAN, AppCurrency.RIAL, 0))
        assertEquals(126L, Money.convert(1_255, AppCurrency.RIAL, AppCurrency.TOMAN, 0))
        assertEquals(125L, Money.convert(1_250, AppCurrency.RIAL, AppCurrency.TOMAN, 0))
        // ۹۰۰٬۰۰۰ تومان با دلار ۹۰٬۰۰۰ تومانی = ۱۰ دلار = ۱۰۰۰ سنت
        assertEquals(1_000L, Money.convert(900_000, AppCurrency.TOMAN, AppCurrency.USD, 90_000))
        assertEquals(1_000L, Money.convert(9_000_000, AppCurrency.RIAL, AppCurrency.USD, 90_000))
        assertEquals(900_000L, Money.convert(1_000, AppCurrency.USD, AppCurrency.TOMAN, 90_000))
        assertEquals(10.0, Money.factor(AppCurrency.TOMAN, AppCurrency.RIAL, 0), 1e-12)
        assertEquals(100.0 / 90_000, Money.factor(AppCurrency.TOMAN, AppCurrency.USD, 90_000), 1e-12)
        assertEquals(1_000_000L, Money.fromToman(100_000, null, AppCurrency.RIAL))
        assertEquals(150L, Money.fromToman(135_000, 90_000, AppCurrency.USD))
        assertNull(Money.fromToman(135_000, null, AppCurrency.USD))
        assertEquals(135_000L, Money.toToman(150, 90_000, AppCurrency.USD))
    }

    @Test
    fun saysAmountsInWords() {
        assertEquals("دوازده دلار و پنجاه سنت", Money.words(1_250, AppCurrency.USD))
        assertEquals("پنجاه سنت", Money.words(50, AppCurrency.USD))
        assertEquals("ده دلار", Money.words(1_000, AppCurrency.USD))
    }
}
