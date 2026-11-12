package com.example.sms.data.parser

import com.example.sms.domain.model.ParsedTransactionModel
import com.example.utils.JalaliDate

/**
 * پارسرهای اختصاصی بانک‌ها (۲۵ پارسر + هلپرهای مشترک)، بازنویسی Kotlin همان منطق:
 *
 * - گروه استاندارد (دی، ملت، سپه، شهر، تجارت، رفاه، رسالت، اقتصاد نوین، کشاورزی، کارآفرین،
 *   گردشگری، سامان، پارسیان، صادرات، سینا، بلو): نوع ← [commonType]، مبلغ ← [commonAmount]،
 *   تاریخ و ساعت ← [commonDateV2].
 * - ملی و مسکن: مبلغ علامت‌دار و تاریخ «MMDD-HH:MM».
 * - پاسارگاد: پیامک «سرزمین هوشمند پاد / ویپاد» به [pod] می‌رود.
 * - آینده: هر فیلد مستقل، و اگر مبلغ نبود مبلغ علامت‌دار جای نوع و مبلغ را می‌گیرد.
 * - مهر ایران، ملل، توسعه تعاون، خاورمیانه: قالب تاریخ اختصاصی خودشان.
 * - سامان: اگر متن «بلوبانک» داشت، پارسر بلو.
 *
 * تفاوت‌های عمدی با نسخهٔ اصلی، برای درستی در تراز:
 * - متن پیش از پارس یکسان می‌شود ([SmsText.normalize])؛ پس ارقام فارسی و «٬» برای همهٔ بانک‌ها کار می‌کند.
 * - عددی که بلافاصله پس از «مانده/موجودی» آمده هرگز مبلغ تراکنش گرفته نمی‌شود.
 * - تاریخ نامعتبر (ماه بیش از ۱۲ و…) پذیرفته نمی‌شود و به‌جایش زمان رسیدن پیامک می‌نشیند.
 *
 * خطای هر پارسر (همان throw های نسخهٔ اصلی) به null تبدیل می‌شود تا پارسر بعدی زنجیره امتحان شود.
 */
class BankRuleParser(private val now: () -> Long = System::currentTimeMillis) : SmsParser {

    private class Fail(msg: String) : Exception(msg)

    private data class JDate(val y: Int, val m: Int, val d: Int)

    private data class Result(val income: Boolean, val amount: Long, val date: JDate?, val time: String)

    override fun parse(body: String, bankName: String): ParsedTransactionModel? {
        val sms = SmsText.normalize(body).trim()
        if (sms.isEmpty() || isNonTransactionMessage(sms)) return null
        val key = bankKey(bankName) ?: bankKey(com.example.sms.util.BankUtils.detectBankName("", sms)) ?: return null
        val r = try {
            dispatch(key, sms)
        } catch (e: Fail) {
            null
        } catch (e: RuntimeException) {
            null
        } ?: return null
        if (r.amount <= 0) return null
        return ParsedTransactionModel(
            transactionType = if (r.income) "deposit" else "withdrawal",
            amount = r.amount,
            date = timestamp(r.date, r.time),
            cardNumber = SmsText.cardLast4(sms),
            balance = SmsText.balance(sms),
            bankName = bankName,
            reference = "",
            merchant = ""
        )
    }

    // ───────────────────────── نقشهٔ بانک ← پارسر ─────────────────────────

    private fun dispatch(key: String, sms: String): Result? = when (key) {
        // سامان با متن «بلوبانک» به پارسر بلو می‌رود؛ بلو فقط ارقام را یکسان می‌کرد که اینجا برای همه انجام شده.
        "saman", "blubank", "day", "mellat", "sepah", "shahr", "tejarat", "refah", "resalat", "enbank",
        "keshavarzi", "karafarin", "gardeshgari", "parsian", "saderat", "sina" -> standard(sms)
        "melli" -> melli(sms)
        "maskan" -> melli(sms)
        "pasargad" -> pasargad(sms)
        "ayandeh" -> ayandeh(sms)
        "mehriranian" -> mehrIranian(sms)
        "mellal" -> mellal(sms)
        "tosetavon" -> toseTavon(sms)
        "khavarmiane" -> khavarmiane(sms)
        else -> null
    }

    /** زنجیرهٔ استاندارد: commonTypeParser ← commonAmountParser ← commonDateParserV2. */
    private fun standard(sms: String): Result {
        val type = commonType(sms)
        val amount = commonAmount(sms)
        val (date, time) = commonDateV2(sms)
        return Result(type, amount, date, time)
    }

    /** PodMsgParser: فقط از مسیر پاسارگاد. تاریخ ندارد. */
    private fun pod(sms: String): Result {
        val income = when {
            Regex("(خرید)|(پرداخت)|(برداشت)|(کارمزد)").containsMatchIn(sms) -> false
            sms.contains("تخصیص داده شد") -> true
            else -> throw Fail("type")
        }
        return Result(income, commonAmount(sms), null, "")
    }

    private fun pasargad(sms: String): Result =
        if (sms.contains("سرزمین هوشمند پاد") || sms.contains("ویپاد")) pod(sms) else standard(sms)

    /** MelliParser (و MaskanParser روی متن بی‌LRM/RLM که normalize آن را پاک کرده). */
    private fun melli(sms: String): Result {
        val type = commonType(sms)
        val amount = signedAmount(sms)?.second ?: throw Fail("amount")
        val short = Regex("\\d{4}-\\d{2}:\\d{2}").find(sms)
        val date: JDate?
        val time: String
        if (short != null) {
            val left = short.value.substringBefore('-')
            date = valid(JDate(currentYear(), left.substring(0, 2).toInt(), left.substring(2, 4).toInt()))
            time = commonTime(sms)
        } else {
            val v2 = commonDateV2(sms)
            date = v2.first
            time = commonTime(sms)
        }
        return Result(type, amount, date, time)
    }

    /** AyandehParser: هر فیلد جدا؛ نبودِ مبلغ با مبلغ علامت‌دار جبران می‌شود. */
    private fun ayandeh(sms: String): Result {
        var type: Boolean? = try { commonType(sms) } catch (e: Fail) { null }
        val v2 = try { commonDateV2(sms) } catch (e: Fail) { null to "" }
        var amount: Long? = try { commonAmount(sms) } catch (e: Fail) { null }
        if (amount == null) {
            val (raw, value) = signedAmount(sms) ?: throw Fail("amount")
            type = raw.contains('+')
            amount = value
        }
        return Result(type ?: false, amount, v2.first, v2.second)
    }

    /** MehrIranianParser: تاریخ V1، نوع فقط از علامت «+». */
    private fun mehrIranian(sms: String): Result {
        val date = commonDateV1(sms) ?: throw Fail("date")
        val (raw, amount) = signedAmount(sms) ?: throw Fail("amount")
        return Result(raw.contains('+'), amount, date, commonTime(sms))
    }

    /** MellalParser: نوع از علامت، تاریخ «YYMMDD-HH:MM» یا V2. */
    private fun mellal(sms: String): Result {
        val (raw, amount) = signedAmount(sms) ?: throw Fail("amount")
        val income = when {
            raw.contains('+') -> true
            raw.contains('-') -> false
            else -> commonType(sms)
        }
        val m = Regex("\\d{6,8}-\\d{2}:\\d{2}").find(sms)
        val date = if (m != null) {
            var left = m.value.substringBefore('-')
            if (left.length == 6) left = "14$left"
            if (left.length == 8) valid(JDate(left.substring(0, 4).toInt(), left.substring(4, 6).toInt(), left.substring(6).toInt())) else null
        } else {
            commonDateV2(sms).first
        }
        return Result(income, amount, date, commonTime(sms))
    }

    /** ToseTavonParser: «YY/MM/DD_HH:MM» یا «MM/DD_HH:MM». */
    private fun toseTavon(sms: String): Result {
        val type = commonType(sms)
        val amount = commonAmount(sms)
        val full = Regex("\\d{2}/\\d{2}/\\d{2}[_-]\\d{2}:\\d{2}").find(sms)
        val date = if (full != null) {
            val (yy, mm, dd) = full.value.split('_', '-')[0].split('/')
            valid(JDate(1400 + yy.toInt(), mm.toInt(), dd.toInt()))
        } else {
            val short = Regex("\\d{2}/\\d{2}[_-]\\d{2}:\\d{2}").find(sms) ?: throw Fail("date")
            val (mm, dd) = short.value.split('_', '-')[0].split('/')
            valid(JDate(currentYear(), mm.toInt(), dd.toInt()))
        }
        return Result(type, amount, date, commonTime(sms))
    }

    /** KhavarmianeParser: ساعت از V2؛ تاریخ از «14YY/MM/DD» یا سطری پنج‌نویسه‌ای مثل «08/17». */
    private fun khavarmiane(sms: String): Result {
        val type = commonType(sms)
        val amount = commonAmount(sms)
        val time = commonDateV2(sms).second
        val full = Regex("14\\d{2}/\\d{2}/\\d{2}").find(sms)
        val date = if (full != null) {
            val (y, m, d) = full.value.split('/')
            valid(JDate(y.toInt(), m.toInt(), d.toInt()))
        } else {
            sms.split('\n').map { it.trim() }.firstOrNull { it.contains('/') && it.length == 5 }
                ?.let { Regex("(\\d{2})/(\\d{2})").find(it) }
                ?.let { valid(JDate(currentYear(), it.groupValues[1].toInt(), it.groupValues[2].toInt())) }
        }
        return Result(type, amount, date, time)
    }

    // ───────────────────────── هلپرهای مشترک ─────────────────────────

    private val SIGNED = Regex("([-+])?(\\d{1,3}(?:,\\d{3})+)([-+])?")
    private val GROUPED = Regex("\\d{1,3}(?:,\\d{3})+")

    /** commonTypeParser: true = واریز (income)، false = برداشت (expense). */
    private fun commonType(sms: String): Boolean {
        if (sms.contains("برگشت پول")) return true
        if (sms.contains("حساب شما نشست")) return true
        if (sms.contains("حساب شما پرید")) return false
        if (Regex("(واریز)|(سود)").containsMatchIn(sms)) return true
        if (Regex("(برداشت)|(خرید)").containsMatchIn(sms)) return false
        val m = SIGNED.find(sms) ?: throw Fail("type")
        return m.value.contains('+')
    }

    /** آیا این عدد درست پس از «مانده/موجودی» آمده است؟ */
    private fun isBalance(sms: String, start: Int): Boolean {
        val before = sms.substring(maxOf(0, start - 16), start)
        return Regex("(مانده|موجودی)[^\\d\\n]{0,10}$").containsMatchIn(before)
    }

    /** commonAmountParser: نخستین عدد با جداکنندهٔ هزارگان. */
    private fun commonAmount(sms: String): Long {
        val m = GROUPED.findAll(sms).firstOrNull { !isBalance(sms, it.range.first) } ?: throw Fail("amount")
        return SmsText.money(m.value) ?: throw Fail("amount")
    }

    /** `(-|+)?(\d{1,3}(,\d{3})+)(-|+)?` ← (متن علامت‌دار، مقدار). */
    private fun signedAmount(sms: String): Pair<String, Long>? {
        val m = SIGNED.findAll(sms).firstOrNull { !isBalance(sms, it.groups[2]!!.range.first) } ?: return null
        val v = SmsText.money(m.groupValues[2]) ?: return null
        return m.value to v
    }

    private fun currentYear(): Int = JalaliDate.fromTimestamp(now()).year

    private fun valid(d: JDate): JDate? =
        if (d.y in 1300..1499 && d.m in 1..12 && d.d in 1..31 && !(d.m > 6 && d.d > 30)) d else null

    /** commonDateParser (V1): `\d{2,4}(/|.)\d{1,2}(/|.)\d{1,2}`؛ سال دو رقمی: بیش از ۸۰ ← ۱۳، وگرنه ۱۴. */
    private fun commonDateV1(sms: String): JDate? {
        val m = Regex("(\\d{2,4})([/.])(\\d{1,2})\\2(\\d{1,2})(?!\\d)").find(sms) ?: return null
        val yRaw = m.groupValues[1]
        val y = when (yRaw.length) {
            2 -> (if (yRaw.toInt() > 80) 1300 else 1400) + yRaw.toInt()
            4 -> yRaw.toInt()
            else -> return null
        }
        return valid(JDate(y, m.groupValues[3].toInt(), m.groupValues[4].toInt()))
    }

    /** commonDateParserV2: اول V1 (اگر سال ۱۴xx بود)، بعد هشت قالب کوتاه به ترتیب. */
    private fun commonDateV2(sms: String): Pair<JDate?, String> {
        val time = commonTime(sms)
        val v1 = commonDateV1(sms)
        if (v1 != null && v1.y >= 1400) return v1 to time
        val year = currentYear()

        Regex("(?:\\d{2}:)?\\d{2}:\\d{2}-(\\d{2})(\\d{2})").find(sms)?.let {                       // F1  HH:MM-MMDD
            valid(JDate(year, it.groupValues[1].toInt(), it.groupValues[2].toInt()))?.let { d -> return d to time }
        }
        Regex("(\\d{2})/(\\d{2})-\\d{2}:\\d{2}").find(sms)?.let {                                    // F2  MM/DD-HH:MM
            valid(JDate(year, it.groupValues[1].toInt(), it.groupValues[2].toInt()))?.let { d -> return d to time }
        }
        Regex("(\\d{2})/(\\d{2})/(\\d{2})_\\d{2}:\\d{2}").find(sms)?.let {                           // F3  YY/MM/DD_HH:MM
            valid(JDate(1400 + it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt()))?.let { d -> return d to time }
        }
        Regex("\\d{2}:\\d{2}(?::\\d{2})?-(\\d{2})/(\\d{2})").find(sms)?.let {                        // F4  HH:MM-MM/DD
            valid(JDate(year, it.groupValues[1].toInt(), it.groupValues[2].toInt()))?.let { d -> return d to time }
        }
        Regex("(\\d{2})(\\d{2})(\\d{2})-\\d{2}:\\d{2}").find(sms)?.let {                             // F5  YYMMDD-HH:MM
            valid(JDate(1400 + it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt()))?.let { d -> return d to time }
        }
        Regex("(?<![\\d/])(\\d{1,2})/(\\d{1,2})(?![\\d/])").find(sms)?.let {                         // F6  MM/DD
            valid(JDate(year, it.groupValues[1].toInt(), it.groupValues[2].toInt()))?.let { d -> return d to time }
        }
        Regex("(\\d{4})/(\\d{2})/(\\d{2})-\\d{2}:\\d{2}").find(sms)?.let {                           // F7  YYYY/MM/DD-HH:MM
            valid(JDate(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt()))?.let { d -> return d to time }
        }
        Regex("(\\d{2})(\\d{2}) - \\d{2}:\\d{2}").find(sms)?.let {                                   // F8  MMDD - HH:MM
            valid(JDate(year, it.groupValues[1].toInt(), it.groupValues[2].toInt()))?.let { d -> return d to time }
        }
        if (v1 == null) throw Fail("date")
        return v1 to time
    }

    /** commonTimeParser: «HH:MM:SS» اولویت دارد؛ خروجی «HH:MM»؛ نبود ساعت خطا نیست. */
    private fun commonTime(sms: String): String {
        Regex("\\d{2}:\\d{2}:\\d{2}").find(sms)?.let { return normalizeTime(it.value.take(5)) }
        Regex("\\d{1,2}:\\d{2}").find(sms)?.let { return normalizeTime(it.value) }
        return ""
    }

    private fun normalizeTime(t: String): String = t.split(':').joinToString(":") { it.padStart(2, '0') }

    private fun timestamp(date: JDate?, time: String): Long {
        if (date == null) return now()
        val p = time.split(':').mapNotNull { it.toIntOrNull() }
        val ms = if (p.size >= 2 && p[0] in 0..23 && p[1] in 0..59) (p[0] * 3600L + p[1] * 60L) * 1000L else 0L
        return try {
            JalaliDate.toTimestamp(date.y, date.m, date.d) + ms
        } catch (e: Exception) {
            now()
        }
    }

    companion object {

        /**
         * isNonTransactionMessage: رمز، کد، خوش‌آمد، یادآوری قسط و… هرگز تراکنش نیستند. پیامکی که «رمز»
         * دارد ولی کارمزدی است رد نمی‌شود. «کد پیگیری/رهگیری/مرجع» کد ورود نیست و پیش از بررسی حذف می‌شود.
         */
        fun isNonTransactionMessage(raw: String): Boolean {
            val sms = SmsText.normalize(raw)
                .replace(Regex("کد\\s*(?:پیگیری|رهگیری|مرجع|ملی|شعبه|پستی)"), "")
            val keywords = listOf(
                "پویا", "ورود", "code", "CODE", "Code", "یادآوری بازپرداخت", "خوش آمدید",
                "اعتبار تا", "رمز پویا:", "کد ", "کد تایید"
            )
            if (keywords.any { sms.contains(it) }) return true
            return sms.contains("رمز") && !sms.contains("کارمزد")
        }

        /**
         * کلید بانک برای نقشهٔ پارسرها، از نام بانک («بانک ملی»، «ملی»، «Bank Melli»، …).
         * «پاد/ویپاد» به پاسارگاد می‌رود که خودش پیامک پاد را جدا می‌کند.
         */
        fun bankKey(bankName: String): String? {
            val core = BankPatternDb.nameCore(bankName)
            if (core.isEmpty()) return null
            return when {
                core.contains("بلو") || core.contains("blu") -> "blubank"
                core.contains("تاپ") || core.contains("تجارتالکترونیک") -> null
                core.contains("توسعهتعاون") || core.contains("tosetavon") || core.contains("ttbank") -> "tosetavon"
                core.contains("توسعهصادرات") -> null
                core.contains("کارافرینی") -> null
                core.contains("سامان") || core.contains("saman") -> "saman"
                core.contains("اینده") || core.contains("ayandeh") -> "ayandeh"
                core.contains("پارسیان") || core.contains("parsian") -> "parsian"
                core == "دی" || core == "dey" || core == "day" -> "day"
                core.contains("ملت") || core.contains("mellat") -> "mellat"
                core == "ملی" || core.contains("melli") -> "melli"
                core.contains("سپه") || core.contains("sepah") -> "sepah"
                core.contains("تجارت") || core.contains("tejarat") -> "tejarat"
                core.contains("صادرات") || core.contains("saderat") -> "saderat"
                core.contains("کشاورزی") || core.contains("keshavarzi") -> "keshavarzi"
                core.contains("اقتصادنوین") || core.contains("enbank") -> "enbank"
                core.contains("پاسارگاد") || core.contains("pasargad") || core.contains("ویپاد") || core.contains("پاد") -> "pasargad"
                core == "شهر" || core.contains("shahr") -> "shahr"
                core.contains("رفاه") || core.contains("refah") -> "refah"
                core.contains("مهرایران") || core.contains("qmehriran") -> "mehriranian"
                core.contains("رسالت") || core.contains("resalat") -> "resalat"
                core.contains("سینا") || core.contains("sina") -> "sina"
                core == "ملل" || core.contains("mellal") -> "mellal"
                core.contains("کارافرین") || core.contains("karafarin") -> "karafarin"
                core.contains("خاورمیانه") || core.contains("khavarmiane") -> "khavarmiane"
                core.contains("گردشگری") || core.contains("tourism") -> "gardeshgari"
                core.contains("مسکن") || core.contains("maskan") -> "maskan"
                else -> null
            }
        }
    }
}
