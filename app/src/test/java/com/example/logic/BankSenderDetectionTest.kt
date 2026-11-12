package com.example.logic

import com.example.sms.data.parser.BankSmsDetector
import com.example.sms.util.BankUtils
import com.example.ui.layer.CursorKeeper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تشخیص پیامک و سرشمارهٔ بانکی، و مکان‌نمای کادرهای عددی. پیامک‌های تبلیغ و تأمین اجتماعی همان
 * شکلی‌اند که در فهرست انتخاب سرشماره به‌اشتباه «بانک ملت» و «بانک سامان» نام گرفته بودند.
 */
class BankSenderDetectionTest {

    private val loanAd = "۳۰۰م وام اعتباری بانک ملت در ازکی‌وام\nویژه خرید کالا\nدریافت وام👇\nazvm.co/826t"
    private val socialSecurity = "*سازمان تامین اجتماعی*\nبیمه‌شده گرامي بنيامين مؤذن زاده به کدملي 3381088300 ، " +
        "سابقه مهر 1405 شما، با کارکرد 30 روز، دستمزد 227344510 ريال و شغل بارشمار، در کارگاه شرکت نيکنام حد بندر شعبه شهيد"
    private val melli = "بانک ملی ایران\nانتقال:1,250,000-\nحساب:12345\nمانده:9,000,000\n0817-13:45"
    private val mellat = "بانک ملت\nخرید 480,000 ریال\nکارت 6104***1234\n1403/08/15 21:05\nمانده 2,000,000"

    // ───────────── سرشماره ─────────────

    @Test
    fun senderNumbersAndLatinNamesIdentifyTheBank() {
        assertEquals("بانک ملی", BankSmsDetector.senderBank("+98700717")?.displayName)
        assertEquals("بانک ملت", BankSmsDetector.senderBank("BankMellat")?.displayName)
        assertEquals("بانک ملت", BankSmsDetector.senderBank("Mellat Bank")?.displayName)
        assertEquals("بانک سامان", BankSmsDetector.senderBank("SamanBank")?.displayName)
        assertEquals("بانک پاسارگاد", BankSmsDetector.senderBank("B.Pasargad")?.displayName)
    }

    @Test
    fun shopsAndServicesAreNotBanks() {
        listOf("AZKIVAM", "MCI shop", "OFOQKOUROSH", "Shahrvand", "+9850003000348", "7575", "+98100020002010", "9892000001420")
            .forEach { assertNull(it, BankSmsDetector.senderBank(it)) }
    }

    // ───────────── پیامک تراکنش ─────────────

    @Test
    fun adsAndNoticesAreNotTransactions() {
        assertFalse(BankSmsDetector.isTransaction(loanAd))
        assertFalse(BankSmsDetector.isTransaction(socialSecurity))
        assertFalse(BankSmsDetector.isTransaction("تا سقف 50,000,000 ریال وام بگیرید: www.example.ir"))
        assertFalse(BankSmsDetector.isTransaction("رمز پویا: 482913\nمبلغ: 2,500,000 ریال\n13:45"))
        assertFalse(BankSmsDetector.hasMoney(loanAd))
    }

    @Test
    fun realTransactionsAre() {
        assertTrue(BankSmsDetector.isTransaction(melli))
        assertTrue(BankSmsDetector.isTransaction(mellat))
        assertTrue(BankSmsDetector.isTransaction("واریز ۲۵٬۰۰۰٬۰۰۰ ریال به حساب شما\nمانده: ۳۷٬۰۰۰٬۰۰۰"))
        assertTrue(BankSmsDetector.isTransaction("+3,400,000\n1403/08/16\n08:30:12"))
    }

    // ───────────── بانک از متن ─────────────

    @Test
    fun bankNameMustBeAWholeWord() {
        assertNull(BankSmsDetector.bankNamedIn("ورود به سامانه تامین اجتماعی"))
        assertEquals("بانک ملت", BankSmsDetector.bankNamedIn(mellat))
        assertEquals("پست بانک", BankSmsDetector.bankNamedIn("پست بانک ایران\nبرداشت 100,000"))
        assertNull(BankSmsDetector.bankNamedIn("بانک دیجیتال"))
    }

    @Test
    fun detectionUsesTheSenderFirstAndTextOnlyForTransactions() {
        val ad = BankSmsDetector.detect("AZKIVAM", loanAd)
        assertNull(ad.bankName)
        assertFalse(ad.isBank)

        val sso = BankSmsDetector.detect("9892000001420", socialSecurity)
        assertNull(sso.bankName)
        assertFalse(sso.isBank)

        val unknownNumberTx = BankSmsDetector.detect("+989999000111", mellat)
        assertEquals("بانک ملت", unknownNumberTx.bankName)
        assertTrue(unknownNumberTx.isTransaction)

        // شمارهٔ بانک، حتی وقتی پیامکش تبلیغ است
        val bankAd = BankSmsDetector.detect("+98700717", loanAd)
        assertEquals("بانک ملی", bankAd.bankName)
        assertTrue(bankAd.bySender)
    }

    @Test
    fun legacyBankGuessNoLongerReadsAds() {
        assertEquals("بانک نامشخص (AZKIVAM)", BankUtils.detectBankName("AZKIVAM", loanAd))
        assertEquals("بانک ملت", BankUtils.detectBankName("BankMellat", loanAd))
    }

    // ───────────── مکان‌نما ─────────────

    /** مثل groupedInput: رقم‌ها فارسی و با «٬» سه‌تا سه‌تا. */
    private fun grouped(text: String): String {
        val digits = text.map { c -> "۰۱۲۳۴۵۶۷۸۹".indexOf(c).let { if (it >= 0) '0' + it else c } }
            .filter { it.isDigit() }.joinToString("").trimStart('0')
        if (digits.isEmpty()) return ""
        return "%,d".format(java.util.Locale.ENGLISH, digits.toLong()).replace(',', '٬')
            .map { if (it.isDigit()) "۰۱۲۳۴۵۶۷۸۹"[it - '0'] else it }.joinToString("")
    }

    /** تایپ رقم به رقم در جای مکان‌نما، با قالب‌بندی بعد از هر رقم. */
    private fun type(start: String, startCursor: Int, keys: String, format: (String) -> String): Pair<String, Int> {
        var text = start
        var cursor = startCursor
        for (k in keys) {
            val edited = text.substring(0, cursor) + k + text.substring(cursor)
            val formatted = format(edited)
            cursor = CursorKeeper.cursorAfterFormat(edited, cursor + 1, formatted)
            text = formatted
        }
        return text to cursor
    }

    @Test
    fun typingAnAmountKeepsTheDigitsInOrder() {
        val (text, cursor) = type("", 0, "5588888", ::grouped)
        assertEquals("۵٬۵۸۸٬۸۸۸", text)
        assertEquals(text.length, cursor)
    }

    @Test
    fun typingInTheMiddleStaysInTheMiddle() {
        // «۱٬۰۰۰» با مکان‌نما بعد از «۱»؛ تایپ «۲» می‌شود ۱۲٬۰۰۰ و مکان‌نما بعد از «۲»
        val (text, cursor) = type("۱٬۰۰۰", 1, "2", ::grouped)
        assertEquals("۱۲٬۰۰۰", text)
        assertEquals(2, cursor)
    }

    @Test
    fun cardNumbersGroupedWithSpaces() {
        val card: (String) -> String = { it.filter(Char::isDigit).take(16).chunked(4).joinToString(" ") }
        val (text, cursor) = type("", 0, "6104337412904218", card)
        assertEquals("6104 3374 1290 4218", text)
        assertEquals(text.length, cursor)
    }
}
