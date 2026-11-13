package com.example.sms.util

object BankUtils {
    /**
     * Common Persian banking keywords used for initial detection.
     */
    val BANKING_KEYWORDS = listOf(
        "برداشت",
        "واریز",
        "انتقال",
        "مانده",
        "موجودی",
        "ریال",
        "تومان",
        "کارت",
        "حساب",
        "تراکنش",
        "پرداخت",
        "خرید",
        "POS",
        "ATM",
        "IBAN"
    )

    /**
     * Determines whether an SMS text is likely to be a bank message.
     */
    fun isBankSms(body: String): Boolean {
        val cleanBody = convertNumeralsAndClean(body)
        return BANKING_KEYWORDS.any { keyword ->
            cleanBody.contains(keyword, ignoreCase = true)
        }
    }

    /**
     * نام بانکِ یک سرشماره. همان روش موتور اصلی: شمارهٔ فرستنده با شماره‌های هر بانک در دیتابیس الگو
     * (یا نام لاتین دقیق بانک، مثل BankMellat). متن پیامک فقط وقتی به کار می‌آید که خودِ پیامک تراکنش
     * باشد و نام بانک را صریح ببرد؛ تبلیغ «وام بانک ملت» یا «سامانه» بانک را تعیین نمی‌کند.
     */
    fun detectBankName(sender: String, body: String): String {
        com.example.sms.data.parser.BankSmsDetector.senderBank(sender)?.let { return it.displayName }
        if (com.example.sms.data.parser.BankSmsDetector.isTransaction(body)) {
            com.example.sms.data.parser.BankSmsDetector.bankNamedIn(body)?.let { return it }
        }
        return "بانک نامشخص ($sender)"
    }

    /**
     * Converts Arabic/Persian digits to English digits and cleans space issues.
     */
    fun convertNumeralsAndClean(text: String): String {
        var result = text
        val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        val englishDigits = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')

        for (i in 0..9) {
            result = result.replace(persianDigits[i], englishDigits[i])
            result = result.replace(arabicDigits[i], englishDigits[i])
        }
        
        // Map common Persian characters
        result = result.replace('ي', 'ی').replace('ك', 'ک')
        
        return result
    }

    /** Bank of a card number from its first six digits; one table for the whole app (see [com.example.data.BankBin]). */
    fun detectBankFromCardNumber(cardNumber: String): String? =
        com.example.data.BankBin.bankFor(convertNumeralsAndClean(cardNumber))
}
