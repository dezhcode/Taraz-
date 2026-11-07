package com.example.logic

import com.example.sms.data.parser.SmsFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * پیامک‌هایی که نباید تراکنش شوند. متن‌ها نمونهٔ ساختگی از «شکل» پیامک‌های رایج‌اند، نه
 * رونوشت پیامک یک بانک مشخص.
 */
class SmsFilterTest {

    @Test
    fun oneTimePasswordsAreNeverTransactions() {
        listOf(
            "رمز پویا: 482913\nمبلغ: 2,500,000 ریال\nفروشگاه الف",
            "رمز دوم یکبار مصرف شما 123456 است. مبلغ ۲٬۵۰۰٬۰۰۰ ریال",
            "کد تأیید: ۴۸۲۹۱۳ . لطفاً آن را به کسی نگویید",
            "کد ورود شما 5521 است",
            "Your OTP is 482913. Amount 2,500,000 IRR",
            "رمز يكبار مصرف: 482913" // ی و ک عربی
        ).forEach { assertTrue(it, SmsFilter.isOneTimeCode(it)) }
    }

    @Test
    fun zeroWidthSpaceAndExtraSpacesDoNotHideACode() {
        assertTrue(SmsFilter.isOneTimeCode("رمز یک‌بار   مصرف شما 1234"))
        assertTrue(SmsFilter.isOneTimeCode("کد  تایید\n 1234"))
    }

    @Test
    fun failedAttemptsAreIgnored() {
        assertTrue(SmsFilter.isFailedAttempt("تراکنش ناموفق: برداشت 500,000 ریال"))
        assertTrue(SmsFilter.isFailedAttempt("خرید انجام نشد. موجودی کافی نیست"))
    }

    @Test
    fun realLookingTransactionsStayUntouched() {
        listOf(
            "برداشت: 1,500,000\nمانده: 12,000,000\nکارت: 6037****1234",
            "واریز 25,000,000 ریال به حساب شما\nمانده: ۳۷٬۰۰۰٬۰۰۰",
            "خرید 480,000 ریال\nفروشگاه الف\nمانده: 9,520,000",
            "انتقال کارت به کارت: ۳۰۰٬۰۰۰ ریال"
        ).forEach { assertFalse(it, SmsFilter.isNonTransaction(it)) }
    }

    @Test
    fun normalizeMakesDigitsAndLettersComparable() {
        assertEquals("رمز دوم 1234", SmsFilter.normalize("رمز  دوم ۱۲۳۴"))
        assertEquals("کد تایید", SmsFilter.normalize("كد تأیید"))
    }
}
