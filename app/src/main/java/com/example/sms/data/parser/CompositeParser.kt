package com.example.sms.data.parser

import com.example.sms.domain.model.ParsedTransactionModel

/**
 * ترتیب خواندن پیامک: قالب‌های ویژهٔ بانک‌هایی که شکل پیامکشان ثابت است، بعد موتور ساختاری تراز
 * ([EngineParser])، و در پایان خواننده‌های قدیمی‌تر برای شکل‌هایی که موتور نمی‌شناسد.
 * رمز یک‌بارمصرف و تراکنش ناموفق هرگز عبور نمی‌کنند.
 */
class CompositeParser : SmsParser {

    private val parsers = listOf(
        BankSpecificParser(),
        EngineParser(),
        RegexParser(),
        KeywordParser(),
        CopilotParser()
    )

    override fun parse(body: String, bankName: String): ParsedTransactionModel? {
        if (SmsFilter.isNonTransaction(body)) return null
        for (parser in parsers) {
            val result = parser.parse(body, bankName)
            if (result != null && result.amount > 0) return result
        }
        return null
    }
}
