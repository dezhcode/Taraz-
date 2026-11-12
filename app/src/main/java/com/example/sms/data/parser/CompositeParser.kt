package com.example.sms.data.parser

import com.example.sms.domain.model.ParsedTransactionModel

/**
 * ترتیب خواندن پیامک:
 *
 * ۱. [PatternDbParser] قالب کامل — قالب‌های ثبت‌شدهٔ همان بانک (۴۷ بانک، ۱٬۰۷۷ قالب)؛ دقیق‌ترین مسیر.
 * ۲. [BankSpecificParser] — قالب‌های دست‌نوشتهٔ ملی و رسالت.
 * ۳. [EngineParser] — موتور ساختاری تراز (برچسب‌ها، علامت‌ها، جمله‌ها).
 * ۴. [BankRuleParser] — ۲۵ پارسر اختصاصی بانک‌ها با هلپرهای مشترک.
 * ۵. [PatternDbParser] امتیازدهی — regex تک‌تک فیلدهای قالب‌های بانک، با آستانهٔ امتیاز.
 * ۶. خواننده‌های قدیمی‌تر برای شکل‌هایی که هیچ‌کدام نمی‌شناسند.
 *
 * رمز یک‌بارمصرف و تراکنش ناموفق هرگز عبور نمی‌کنند.
 */
class CompositeParser : SmsParser {

    private val template = PatternDbParser(PatternDbParser.Mode.TEMPLATE)
    private val score = PatternDbParser(PatternDbParser.Mode.SCORE)

    private val parsers: List<SmsParser> = listOf(
        BankSpecificParser(),
        EngineParser(),
        BankRuleParser()
    )

    private val legacy: List<SmsParser> = listOf(
        RegexParser(),
        KeywordParser(),
        CopilotParser()
    )

    override fun parse(body: String, bankName: String): ParsedTransactionModel? = parse(body, bankName, "")

    /** [sender] شمارهٔ فرستنده است؛ با آن بانک از دیتابیس الگو حتی بدون نام درست پیدا می‌شود. */
    fun parse(body: String, bankName: String, sender: String): ParsedTransactionModel? {
        if (SmsFilter.isNonTransaction(body)) return null
        // نام بانک برای پارسرهای اختصاصی: اگر کاربر نامی نداده، نام بانک فرستنده از دیتابیس.
        val name = bankName.ifBlank { BankPatternDb.bankForSender(sender)?.displayName.orEmpty() }

        template.parse(body, name, sender)?.takeIf { it.amount > 0 }?.let { return it }
        for (parser in parsers) {
            val result = parser.parse(body, name)
            if (result != null && result.amount > 0) return result
        }
        score.parse(body, name, sender)?.takeIf { it.amount > 0 }?.let { return it }
        for (parser in legacy) {
            val result = parser.parse(body, name)
            if (result != null && result.amount > 0) return result
        }
        return null
    }
}
