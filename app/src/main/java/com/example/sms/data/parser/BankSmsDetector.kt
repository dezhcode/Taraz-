package com.example.sms.data.parser

/**
 * کدام پیامک بانکی است و از کدام بانک؟
 *
 * همان روش موتور اصلی سند: بانک از روی «سرشماره» شناخته می‌شود؛ شمارهٔ فرستنده (یا نام لاتین آن،
 * مثل BankMellat) با فهرست شماره‌های هر بانک در [BankPatternDb] مقایسه می‌شود. متن پیامک به‌تنهایی
 * بانک را تعیین نمی‌کند، چون تبلیغ «وام بانک ملت» از شمارهٔ فروشگاه هم «بانک ملت» دارد و «سامانه»
 * هم «سامان» را در خود دارد.
 *
 * فقط برای فرستنده‌ای که شماره‌اش شناخته نیست، و فقط وقتی پیامکش واقعاً شکل تراکنش دارد
 * ([isTransaction]: مبلغ، جهت، و مانده یا حساب یا تاریخ؛ بدون لینک)، بانک از متن حدس زده می‌شود:
 * نام صریح «بانک X» در متن، یا قالبی از دیتابیس که فقط مال یک بانک است.
 */
object BankSmsDetector {

    data class Detection(
        /** نام نمایشی بانک (مثل «بانک ملت»)؛ null اگر معلوم نباشد. */
        val bankName: String?,
        /** فرستنده از روی شماره یا نامش بانک شناخته شد. */
        val bySender: Boolean,
        /** خود پیامک شکل تراکنش بانکی دارد. */
        val isTransaction: Boolean
    ) {
        val isBank: Boolean get() = bySender || isTransaction
    }

    /** پیامک [body] از فرستندهٔ [address]. */
    fun detect(address: String, body: String): Detection {
        val sender = senderBank(address)
        val tx = isTransaction(body)
        val name = sender?.displayName ?: if (tx) contentBank(body) else null
        return Detection(name, sender != null, tx)
    }

    // ───────────────────────── سرشماره ─────────────────────────

    /** بانکِ این سرشماره: شمارهٔ ثبت‌شده در دیتابیس، یا نام لاتین دقیق بانک (BankMellat، Saman Bank، B.Sepah). */
    fun senderBank(address: String): BankPatternDb.Bank? {
        BankPatternDb.bankForSender(address)?.let { return it }
        val key = BankPatternDb.senderKey(address)
        if (key.isEmpty() || key.any { it.isDigit() }) return null
        for ((word, bank) in LATIN) {
            if (key == word || key == "bank$word" || key == "${word}bank" || key == "b$word" || key == "bank${word}iran") {
                return BankPatternDb.banks.firstOrNull { it.name == bank }
            }
        }
        return null
    }

    private val LATIN = listOf(
        "melli" to "ملی", "mellat" to "ملت", "saman" to "سامان", "tejarat" to "تجارت", "saderat" to "صادرات",
        "sepah" to "سپه", "pasargad" to "پاسارگاد", "parsian" to "پارسیان", "refah" to "رفاه کارگران",
        "keshavarzi" to "کشاورزی", "maskan" to "مسکن", "ayandeh" to "آینده", "sina" to "سینا", "shahr" to "شهر",
        "dey" to "دی", "day" to "دی", "resalat" to "رسالت", "karafarin" to "کارافرین", "en" to "اقتصاد نوین",
        "eghtesadnovin" to "اقتصاد نوین", "tourism" to "گردشگری", "gardeshgari" to "گردشگری", "post" to "پست بانک",
        "blu" to "بلو بانک", "sarmayeh" to "سرمایه", "khavarmiane" to "خاورمیانه", "tt" to "توسعه تعاون",
        "qmehriran" to "قرض الحسنه مهرایران", "mehriran" to "قرض الحسنه مهرایران", "iz" to "ایران زمین",
        "iranzamin" to "ایران زمین", "ansar" to "انصار", "ghavamin" to "قوامین", "mellal" to "ملل", "noor" to "نور"
    )

    // ───────────────────────── پیامک تراکنش ─────────────────────────

    private val MONEY = Regex(
        "(?<![\\d,])\\d{1,3}(?:,\\d{3})+(?![\\d,])|(?<![\\d.])\\d{1,3}(?:\\.\\d{3}){2,}(?![\\d.])|(?<![\\d,])\\d{4,}\\s*(?:ریال|تومان)"
    )
    private val DIRECTION = Regex("برداشت|واریز|خرید|انتقال|پرداخت|کسر|دریافت|برگشت|عودت|سود|قسط|حواله|شارژ|variz|bardasht")
    private val SIGNED = Regex("[+-]\\s?\\d{1,3}(?:,\\d{3})+|\\d{1,3}(?:,\\d{3})+\\s?[+-](?!\\d)")
    private val BALANCE = Regex("(?:مانده|موجودی)[^\\n\\d]{0,20}[+-]?\\d")
    private val ACCOUNT = listOf(
        Regex("\\d{4,6}[ -]?[*xX#•]{2,}"),
        Regex("[*xX#•]{2,}[ -]?\\d{3,}"),
        Regex("(?<![\\d/])\\d{2,}[.\\-]\\d{2,}[.\\-]\\d{2,}(?![\\d/])"),
        Regex("(?:حساب|کارت|سپرده|از|به)\\s*[:：]?\\s*\\d{4,}")
    )
    private val DATE_TIME = Regex("\\d{2,4}/\\d{1,2}(?:/\\d{1,2})?|(?<!\\d)\\d{1,2}:\\d{2}(?!\\d)|\\d{4}-\\d{2}:\\d{2}")
    private val LINK = Regex("https?://|www\\.|[A-Za-z0-9-]+\\.(?:ir|com|co|me|link|app|org|net)(?:/|\\b)", RegexOption.IGNORE_CASE)

    /**
     * شکل پیامک تراکنش بانکی: مبلغ پولی (با جداکنندهٔ هزارگان یا ریال/تومان)، جهت (واژهٔ برداشت/واریز/…
     * یا علامت + و -)، و دست‌کم یکی از مانده، شماره حساب/کارت، تاریخ/ساعت. پیامک لینک‌دار (تبلیغ)،
     * رمز، کد و تراکنش ناموفق تراکنش نیستند.
     */
    fun isTransaction(body: String): Boolean {
        if (SmsFilter.isNonTransaction(body) || PatternDbParser.isPasswordSms(body)) return false
        val t = SmsText.normalize(body)
        if (LINK.containsMatchIn(t)) return false
        if (!MONEY.containsMatchIn(t)) return false
        if (!DIRECTION.containsMatchIn(t) && !SIGNED.containsMatchIn(t)) return false
        return BALANCE.containsMatchIn(t) || ACCOUNT.any { it.containsMatchIn(t) } || DATE_TIME.containsMatchIn(t)
    }

    /** مبلغی به شکل پول دارد (۱,۲۵۰,۰۰۰ یا ۱.۲۵۰.۰۰۰ یا «۵۰۰۰۰ ریال»)؛ «۳۰۰م وام» ندارد. */
    fun hasMoney(body: String): Boolean = MONEY.containsMatchIn(SmsText.normalize(body))

    // ───────────────────────── بانک از متن ─────────────────────────

    /**
     * بانکِ یک پیامک تراکنش از روی متنش: اول نام صریح («بانک ملت»، «پست بانک»)، بعد قالب دیتابیس
     * اگر فقط با قالب‌های یک بانک جور باشد. نام بانک باید کلمهٔ کامل باشد («سامانه» سامان نیست).
     */
    fun contentBank(body: String): String? {
        bankNamedIn(body)?.let { return it }
        val banks = PatternDbParser().matchingBanks(body)
        return banks.singleOrNull()?.displayName
    }

    /** نام صریح بانک در متن، با «بانک/بانك» پیش یا پس از آن؛ null اگر نباشد یا چند بانک نام برده شود. */
    fun bankNamedIn(body: String): String? {
        val t = SmsText.normalize(body).replace('آ', 'ا')
        val found = LinkedHashSet<String>()
        for (bank in BankPatternDb.banks) {
            for (name in names(bank)) {
                val n = Regex.escape(name).replace(" ", "\\s*")
                val re = Regex("(?:بانک|موسسه اعتباری|قرض الحسنه)\\s*$n(?![\\p{L}])|(?<![\\p{L}])$n\\s*بانک")
                if (re.containsMatchIn(t)) { found += bank.displayName; break }
            }
        }
        return found.singleOrNull()
    }

    private fun names(bank: BankPatternDb.Bank): List<String> {
        val core = SmsText.normalize(bank.name).replace('آ', 'ا').replace("بانک", "").trim()
        val display = SmsText.normalize(bank.displayName).replace('آ', 'ا')
            .replace("بانک", "").replace("قرض الحسنه", "").replace("موسسه اعتباری", "").trim()
        return listOf(core, display, core.replace(" ", ""), display.replace(" ", ""))
            .filter { it.length >= 2 }.distinct()
    }
}
