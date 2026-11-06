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
     * Resolves the bank name from sender number or SMS content.
     */
    fun detectBankName(sender: String, body: String): String {
        val senderLower = sender.lowercase().trim()
        val bodyClean = convertNumeralsAndClean(body)

        return when {
            // Check sender string
            senderLower.contains("melli") || senderLower.contains("bmi") || senderLower.contains("ملی") -> "بانک ملی"
            senderLower.contains("mellat") || senderLower.contains("melat") || senderLower.contains("ملت") -> "بانک ملت"
            senderLower.contains("tejarat") || senderLower.contains("تجارت") -> "بانک تجارت"
            senderLower.contains("saman") || senderLower.contains("سامان") -> "بانک سامان"
            senderLower.contains("refah") || senderLower.contains("رفاه") -> "بانک رفاه"
            senderLower.contains("ayandeh") || senderLower.contains("آینده") -> "بانک آینده"
            senderLower.contains("parsian") || senderLower.contains("پارسیان") -> "بانک پارسیان"
            senderLower.contains("sepah") || senderLower.contains("سپه") -> "بانک سپه"
            senderLower.contains("saderat") || senderLower.contains("bsi") || senderLower.contains("صادرات") -> "بانک صادرات"
            senderLower.contains("pasargad") || senderLower.contains("bpi") || senderLower.contains("پاسارگاد") -> "بانک پاسارگاد"
            senderLower.contains("maskan") || senderLower.contains("مسکن") -> "بانک مسکن"
            senderLower.contains("keshavarzi") || senderLower.contains("کشاورزی") -> "بانک کشاورزی"
            senderLower.contains("shahr") || senderLower.contains("شهر") -> "بانک شهر"
            senderLower.contains("sina") || senderLower.contains("سینا") -> "بانک سینا"
            senderLower.contains("mehr") || senderLower.contains("مهر") -> "بانک مهر ایران"
            senderLower.contains("resalat") || senderLower.contains("رسالت") -> "بانک رسالت"

            // Fallback to checking body text
            bodyClean.contains("بانک ملی") -> "بانک ملی"
            bodyClean.contains("بانک ملت") -> "بانک ملت"
            bodyClean.contains("تجارت") -> "بانک تجارت"
            bodyClean.contains("سامان") -> "بانک سامان"
            bodyClean.contains("رفاه کارگران") -> "بانک رفاه"
            bodyClean.contains("آینده") -> "بانک آینده"
            bodyClean.contains("پارسیان") -> "بانک پارسیان"
            bodyClean.contains("سپه") -> "بانک سپه"
            bodyClean.contains("صادرات") -> "بانک صادرات"
            bodyClean.contains("پاسارگاد") -> "بانک پاسارگاد"
            bodyClean.contains("مسکن") -> "بانک مسکن"
            bodyClean.contains("کشاورزی") -> "بانک کشاورزی"
            bodyClean.contains("بانک شهر") -> "بانک شهر"
            bodyClean.contains("سینا") -> "بانک سینا"
            bodyClean.contains("مهر ایران") -> "بانک مهر ایران"
            bodyClean.contains("قرض الحسنه رسالت") -> "بانک رسالت"

            else -> "بانک نامشخص ($sender)"
        }
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
