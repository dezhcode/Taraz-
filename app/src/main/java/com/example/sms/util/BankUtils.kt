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

    /**
     * Detects Iranian bank name from 16-digit card number using its 6-digit BIN prefix.
     */
    fun detectBankFromCardNumber(cardNumber: String): String? {
        val cleanNumber = convertNumeralsAndClean(cardNumber).filter { it.isDigit() }
        if (cleanNumber.length < 6) return null
        val binStr = cleanNumber.substring(0, 6)
        val bin = binStr.toIntOrNull() ?: return null

        return when (bin) {
            627381 -> "بانک انصار"
            636214 -> "بانک آینده"
            502938 -> "بانک دی"
            627412 -> "بانک اقتصاد نوین"
            628157 -> "موسسه اعتباری توسعه"
            505416 -> "بانک گردشگری"
            639599 -> "بانک قوامین"
            627488, 502910 -> "بانک کارآفرین"
            603770, 639217 -> "بانک کشاورزی"
            628023 -> "بانک مسکن"
            639370 -> "بانک مهر اقتصاد"
            606373 -> "بانک قرض الحسنه مهر ایرانیان"
            603799 -> "بانک ملی"
            610433, 991975 -> "بانک ملت"
            111111 -> "همه کارتخوانها"
            622106 -> "بانک پارسیان"
            502229, 639347 -> "بانک پاسارگاد"
            627760 -> "پست بانک ایران"
            589463 -> "بانک رفاه"
            627961 -> "بانک صنعت و معدن"
            603769 -> "بانک صادرات"
            621986 -> "بانک سامان"
            639607 -> "بانک سرمایه"
            589210 -> "بانک سپه"
            504706, 502806 -> "بانک شهر"
            639346 -> "بانک سینا"
            627353, 585983 -> "بانک تجارت"
            636949 -> "بانک حکمت"
            627648 -> "بانک توسعه صادرات"
            502908 -> "بانک توسعه تعاون"
            504172 -> "بانک رسالت"
            505785 -> "بلو کارت"
            else -> null
        }
    }
}
