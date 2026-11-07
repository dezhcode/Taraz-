package com.example.sms.engine

/**
 * A bank the SMS reader can be switched on for.
 *
 * The two engines name banks differently — the pattern database says «ملی», the
 * script engine says `melli`, the card-number prefix table says «بانک ملی» — and
 * the logo lookup in the UI wants yet another spelling. This is the one place
 * that ties them together.
 */
data class SmsBank(
    /** Name in the pattern database (letters normalised), e.g. «ملی». */
    val patternName: String,
    /** What the user sees and what is stored on the card/sender, e.g. «بانک ملی». */
    val displayName: String,
    /** Key of the hand-written script parser, or null when only patterns exist. */
    val scriptKey: String? = null,
    /** Other spellings (card-prefix table, older code) that mean this bank. */
    val aliases: List<String> = emptyList()
)

object BankCatalog {

    val banks: List<SmsBank> = listOf(
        SmsBank("ملی", "بانک ملی", "melli"),
        SmsBank("ملت", "بانک ملت", "mellat"),
        SmsBank("صادرات", "بانک صادرات", "saderat"),
        SmsBank("تجارت", "بانک تجارت", "tejarat"),
        SmsBank("سپه", "بانک سپه", "sepah"),
        SmsBank("کشاورزی", "بانک کشاورزی", "keshavarzi"),
        SmsBank("مسکن", "بانک مسکن", "maskan"),
        SmsBank("رفاه کارگران", "بانک رفاه کارگران", "refah", listOf("بانک رفاه")),
        SmsBank("پست بانک", "پست بانک", aliases = listOf("پست بانک ایران")),
        SmsBank("سامان", "بانک سامان", "saman"),
        SmsBank("پارسیان", "بانک پارسیان", "parsian"),
        SmsBank("پاسارگاد", "بانک پاسارگاد", "pasargad"),
        SmsBank("اقتصاد نوین", "بانک اقتصاد نوین", "enbank"),
        SmsBank("کارافرین", "بانک کارآفرین", "karafarin", listOf("بانک کارافرین")),
        SmsBank("سینا", "بانک سینا", "sina"),
        SmsBank("سرمایه", "بانک سرمایه"),
        SmsBank("شهر", "بانک شهر", "shahr"),
        SmsBank("دی", "بانک دی", "day"),
        SmsBank("ایران زمین", "بانک ایران زمین", aliases = listOf("بانک ایران‌زمین")),
        SmsBank("آینده", "بانک آینده", "ayandeh"),
        SmsBank("گردشگری", "بانک گردشگری", "gardeshgari"),
        SmsBank("حکمت ایرانیان", "بانک حکمت ایرانیان", aliases = listOf("بانک حکمت")),
        SmsBank("قوامین", "بانک قوامین"),
        SmsBank("انصار", "بانک انصار"),
        SmsBank("مهراقتصاد", "بانک مهر اقتصاد"),
        SmsBank("قرض الحسنه مهرایران", "بانک قرض الحسنه مهر ایران", "mehriranian",
            listOf("بانک مهر ایران", "بانک مهر ایرانیان", "بانک قرض الحسنه مهر ایرانیان", "قرض الحسنه مهر ایران")),
        SmsBank("ملل", "موسسه اعتباری ملل", "mellal", listOf("بانک ملل")),
        SmsBank("نور", "موسسه اعتباری نور", aliases = listOf("بانک نور")),
        SmsBank("کوثر", "موسسه اعتباری کوثر"),
        SmsBank("توسعه تعاون", "بانک توسعه تعاون", "tosetavon"),
        SmsBank("توسعه صادرات", "بانک توسعه صادرات"),
        SmsBank("صنعت و معدن", "بانک صنعت و معدن"),
        SmsBank("موسسه اعتباری توسعه", "موسسه اعتباری توسعه"),
        SmsBank("خاورمیانه", "بانک خاورمیانه", "khavarmiane"),
        SmsBank("کارآفرینی امید", "کارآفرینی امید"),
        SmsBank("رسالت", "بانک رسالت", "resalat", listOf("بانک قرض الحسنه رسالت", "قرض الحسنه رسالت")),
        SmsBank("آرمان", "بانک آرمان"),
        SmsBank("ثامن", "بانک ثامن"),
        SmsBank("فردا بانک", "فردا بانک"),
        SmsBank("تعاونی اعتبار", "تعاونی اعتبار"),
        SmsBank("راسخون", "موسسه راسخون"),
        SmsBank("بانک مرکزی ج.ا.ا", "بانک مرکزی"),
        SmsBank("صندوق قرض الحسنه", "صندوق قرض الحسنه"),
        SmsBank("بلو بانک", "بلو بانک", "blubank", listOf("بلو کارت", "بلوبانک")),
        SmsBank("سرزمین هوشمند پاد", "سرزمین هوشمند پاد", aliases = listOf("پاد", "ویپاد")),
        SmsBank("تجارت الکترونیک پارسیان(تاپ)", "تاپ (تجارت الکترونیک پارسیان)", aliases = listOf("تاپ"))
    )

    private val byKey: Map<String, SmsBank> = buildMap {
        for (bank in banks) {
            put(SmsText.nameKey(bank.patternName), bank)
            put(SmsText.nameKey(bank.displayName), bank)
            for (alias in bank.aliases) put(SmsText.nameKey(alias), bank)
        }
    }

    /** Finds a bank by any of its names: database name, display name or alias. */
    fun find(name: String?): SmsBank? {
        if (name.isNullOrBlank()) return null
        return byKey[SmsText.nameKey(name)]
    }

    /** The bank behind a card number's prefix, e.g. 603799… → «بانک ملی». */
    fun forCardBankName(cardBankName: String?): SmsBank? = find(cardBankName)
}
