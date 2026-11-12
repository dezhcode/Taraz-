package com.example.data

import android.content.Context
import com.example.utils.JalaliDate
import com.example.utils.NumberToPersianWords
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * واحد پولی دفتر. همهٔ مبلغ‌های دیتابیس به «کوچک‌ترین واحد» همین پول ذخیره می‌شوند:
 * تومان به تومان، ریال به ریال (دقیق، همان عدد پیامک بانک)، و دلار به سنت (۱۲٫۵۰ دلار = ۱۲۵۰).
 */
enum class AppCurrency(val code: String, val label: String, val decimals: Int) {
    TOMAN("toman", "تومان", 0),
    RIAL("rial", "ریال", 0),
    USD("usd", "دلار", 2);

    /** پیامک‌های بانکی ایران ریالی‌اند؛ دفتر دلاری آن‌ها را نمی‌خواند. */
    val readsBankSms: Boolean get() = this != USD

    /** یک «واحد کامل» (یک تومان، یک ریال، یک دلار) به واحد ذخیره. */
    val unit: Long get() = if (decimals == 2) 100L else 1L

    companion object {
        fun of(code: String?): AppCurrency? = entries.firstOrNull { it.code == code }
    }
}

/**
 * قالب‌بندی، خواندن و تبدیل مبلغ‌ها با واحد پولی انتخاب‌شده. [current] پیش از هر نمایش با [load]
 * از تنظیمات خوانده می‌شود (هنگام باز شدن برنامه، گیرندهٔ پیامک، کارهای پس‌زمینه و ویجت).
 */
object Money {
    private const val FILE = "taraz_prefs"
    private const val KEY = "currency"

    @Volatile
    var current: AppCurrency = AppCurrency.TOMAN
        private set

    val label: String get() = current.label

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** واحد ذخیره‌شده را می‌خواند؛ کسی که پیش از این نسخه داده داشته، تومانی است. */
    fun load(context: Context): AppCurrency {
        current = AppCurrency.of(prefs(context).getString(KEY, null)) ?: AppCurrency.TOMAN
        return current
    }

    /** کاربر هنوز واحد پول را انتخاب نکرده (اولین اجرا). */
    fun isChosen(context: Context): Boolean = prefs(context).contains(KEY)

    /** فقط برچسب را ذخیره می‌کند؛ تبدیل مبلغ‌ها با [FinanceRepository.convertCurrency] است. */
    fun save(context: Context, currency: AppCurrency) {
        prefs(context).edit().putString(KEY, currency.code).apply()
        current = currency
    }

    /** فقط برای آزمون‌ها */
    internal fun setForTest(currency: AppCurrency) { current = currency }

    // ───────────────────────── نمایش ─────────────────────────

    /** ۱۲۳٬۴۵۶ (تومان/ریال) یا ۱۲٬۳۴۵٫۶۷ (دلار)، با رقم فارسی */
    fun format(value: Long, currency: AppCurrency = current): String = JalaliDate.toPersianDigits(latin(value, currency))

    /** همان [format] با رقم و جداکنندهٔ لاتین (1,234.56) */
    fun formatLatin(value: Long, currency: AppCurrency = current): String = latin(value, currency).replace('٬', ',').replace('٫', '.')

    private fun latin(value: Long, currency: AppCurrency): String {
        if (currency.decimals == 0) return String.format(Locale.ENGLISH, "%,d", value).replace(',', '٬')
        val a = abs(value)
        val whole = String.format(Locale.ENGLISH, "%,d", a / 100).replace(',', '٬')
        val cents = String.format(Locale.ENGLISH, "%02d", a % 100)
        return (if (value < 0) "-" else "") + whole + "٫" + cents
    }

    /** «۱۲۳٬۴۵۶ تومان» */
    fun withLabel(value: Long, currency: AppCurrency = current): String = format(value, currency) + " " + currency.label

    /** مبلغ به حروف: «دوازده دلار و پنجاه سنت»، «یک میلیون تومان» */
    fun words(value: Long, currency: AppCurrency = current): String {
        val v = abs(value)
        if (currency.decimals == 0) return NumberToPersianWords.convert(v) + " " + currency.label
        val whole = v / 100
        val cents = v % 100
        return buildString {
            if (whole > 0 || cents == 0L) append(NumberToPersianWords.convert(whole)).append(" دلار")
            if (cents > 0) {
                if (whole > 0) append(" و ")
                append(NumberToPersianWords.convert(cents)).append(" سنت")
            }
        }
    }

    // ───────────────────────── ورود ─────────────────────────

    private const val DECIMAL_MARKS = ".٫/"

    private fun digit(ch: Char): Char? {
        val p = "۰۱۲۳۴۵۶۷۸۹".indexOf(ch)
        val a = "٠١٢٣٤٥٦٧٨٩".indexOf(ch)
        return when {
            ch in '0'..'9' -> ch
            p >= 0 -> '0' + p
            a >= 0 -> '0' + a
            else -> null
        }
    }

    /** بخش صحیح و اعشاری متن ورودی (فقط رقم‌ها؛ جداکنندهٔ هزارگان نادیده)؛ اعشار null یعنی ممیز ندارد. */
    private fun split(text: String, currency: AppCurrency): Pair<String, String?> {
        val whole = StringBuilder()
        var frac: StringBuilder? = null
        for (ch in text) {
            if (currency.decimals > 0 && ch in DECIMAL_MARKS) {
                if (frac == null) frac = StringBuilder()
                continue
            }
            val d = digit(ch) ?: continue
            if (frac != null) { if (frac.length < currency.decimals) frac.append(d) } else whole.append(d)
        }
        return whole.toString().trimStart('0') to frac?.toString()
    }

    /** مبلغ از متن ورودی (ارقام فارسی/عربی هم)، به واحد ذخیره؛ ورودی نامعتبر = ۰ */
    fun parse(text: String, currency: AppCurrency = current): Long {
        val (whole, frac) = split(text, currency)
        val w = whole.take(15).toLongOrNull() ?: 0L
        if (currency.decimals == 0) return w
        val f = (frac.orEmpty() + "00").take(2).toLong()
        return w * 100 + f
    }

    /**
     * متن کادر مبلغ بعد از هر تایپ: ۱۲٬۵۰۰٬۰۰۰، یا برای دلار «۱۲٬۳۴۵٫۶» تا ممیز و رقم اعشاری که
     * کاربر هنوز در حال تایپ آن است از بین نرود.
     */
    fun inputText(text: String, currency: AppCurrency = current): String {
        val (whole, frac) = split(text, currency)
        if (whole.isEmpty() && frac == null && text.none { digit(it) != null }) return ""
        val w = whole.take(15).toLongOrNull() ?: 0L
        val grouped = String.format(Locale.ENGLISH, "%,d", w).replace(',', '٬')
        return JalaliDate.toPersianDigits(if (frac == null) grouped else "$grouped٫$frac")
    }

    /** مبلغ ذخیره‌شده به شکل متن ساده برای ویرایش («12.5» یا «125000») */
    fun toInput(value: Long, currency: AppCurrency = current): String =
        if (currency.decimals == 0) value.toString()
        else (value / 100).toString() + if (value % 100 != 0L) "." + String.format(Locale.ENGLISH, "%02d", value % 100).trimEnd('0') else ""

    // ───────────────────────── تبدیل ─────────────────────────

    /**
     * مبلغ پیامک بانک به واحد دفتر. پیامک‌ها ریالی‌اند مگر صریحاً «تومان» بگویند. در دفتر ریالی همان
     * عدد پیامک بی‌کم‌وکاست ثبت می‌شود. دفتر دلاری پیامک نمی‌خواند (null).
     */
    fun fromSms(raw: Long, body: String, currency: AppCurrency = current): Long? {
        val toman = body.contains("تومان") && !body.contains("ریال")
        return when (currency) {
            AppCurrency.RIAL -> if (toman) raw * 10 else raw
            AppCurrency.TOMAN -> if (toman) raw else raw / 10
            AppCurrency.USD -> null
        }
    }

    /** قیمت تومانی (قیمت‌های روز) به واحد دفتر؛ برای دلار به نرخ [usdToman] نیاز است. */
    fun fromToman(toman: Long, usdToman: Long?, currency: AppCurrency = current): Long? = when (currency) {
        AppCurrency.TOMAN -> toman
        AppCurrency.RIAL -> toman * 10
        AppCurrency.USD -> usdToman?.takeIf { it > 0 }?.let { (toman.toDouble() * 100 / it).roundToLong() }
    }

    /** مبلغ دفتر به تومان؛ برای دلار با نرخ [usdToman]. */
    fun toToman(value: Long, usdToman: Long?, currency: AppCurrency = current): Long? = when (currency) {
        AppCurrency.TOMAN -> value
        AppCurrency.RIAL -> value / 10
        AppCurrency.USD -> usdToman?.takeIf { it > 0 }?.let { (value.toDouble() / 100 * it).roundToLong() }
    }

    /**
     * تبدیل یک مبلغ از [from] به [to]. تومان↔ریال دقیق است (ریال به تومان به نزدیک‌ترین تومان گرد
     * می‌شود)؛ با دلار نرخ [usdToman] (تومان برای هر دلار) لازم است.
     */
    fun convert(value: Long, from: AppCurrency, to: AppCurrency, usdToman: Long): Long {
        if (from == to) return value
        // اول به ریال (دقیق‌ترین واحد ایرانی)، بعد به مقصد
        val rial: Double = when (from) {
            AppCurrency.RIAL -> value.toDouble()
            AppCurrency.TOMAN -> value.toDouble() * 10
            AppCurrency.USD -> value.toDouble() / 100 * usdToman * 10
        }
        return when (to) {
            AppCurrency.RIAL -> rial.roundToLong()
            AppCurrency.TOMAN -> (rial / 10).roundToLong()
            AppCurrency.USD -> (rial / 10 / usdToman * 100).roundToLong()
        }
    }

    /** ارزش یک واحد ذخیرهٔ [c] به ریال (تومان ۱۰، ریال ۱، یک سنت = نرخ دلار به تومان ÷ ۱۰) */
    private fun rialPerUnit(c: AppCurrency, usdToman: Long): Double = when (c) {
        AppCurrency.TOMAN -> 10.0
        AppCurrency.RIAL -> 1.0
        AppCurrency.USD -> usdToman.toDouble() * 10 / 100
    }

    /** ضریبی که هر مبلغ [from] را به [to] می‌برد (برای تبدیل یک‌جای دیتابیس). */
    fun factor(from: AppCurrency, to: AppCurrency, usdToman: Long): Double =
        rialPerUnit(from, usdToman) / rialPerUnit(to, usdToman)

    /** چند مبلغ پیشنهادی گرد برای بودجهٔ روزانه، با برچسب کوتاه */
    fun dailyBudgetPresets(currency: AppCurrency = current): List<Pair<Long, String>> = when (currency) {
        AppCurrency.TOMAN -> listOf(500_000L to "۵۰۰ هزار", 1_000_000L to "۱ میلیون", 1_200_000L to "۱٫۲ میلیون", 2_000_000L to "۲ میلیون")
        AppCurrency.RIAL -> listOf(5_000_000L to "۵ میلیون", 10_000_000L to "۱۰ میلیون", 12_000_000L to "۱۲ میلیون", 20_000_000L to "۲۰ میلیون")
        AppCurrency.USD -> listOf(1_000L to "۱۰ دلار", 2_000L to "۲۰ دلار", 3_000L to "۳۰ دلار", 5_000L to "۵۰ دلار")
    }
}
