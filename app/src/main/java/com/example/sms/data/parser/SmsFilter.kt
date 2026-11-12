package com.example.sms.data.parser

import com.example.sms.util.BankUtils

/**
 * پیامک‌هایی که هرگز تراکنش نیستند: رمز یک‌بارمصرف / رمز پویا و تراکنش ناموفق.
 *
 * رمز پویای بانکی معمولاً «مبلغ … ریال» را هم در خود دارد؛ اگر فیلتر نشود، به‌جای
 * تراکنش واقعی یک «تراکنش» ساختگی می‌سازد و بدتر از آن، خود رمز در فهرست پیامک‌های
 * ناشناخته ذخیره می‌شود. این بررسی پیش از هر تحلیل و هر ذخیره‌ای انجام می‌شود.
 */
object SmsFilter {

    private val ONE_TIME_CODE = listOf(
        "رمز یکبار", "رمز یک بار", "رمز دوم", "رمز پویا", "رمز ورود", "رمز عبور",
        "رمز اینترنتی", "رمز پیامکی", "رمز تراکنش",
        "کد تایید", "کد تاییدیه", "کد فعالسازی", "کد فعال سازی", "کد ورود",
        "کد امنیتی", "کد یکبار", "کد یک بار", "کد احراز",
        "otp", "password", "verification code", "dynamic pin", "cvv2",
        // از موتورهای پیامک بانکی: شناسه/مجوز تایید برداشت و رمزهای دارای مهلت اعتبار
        "مدت زمان اعتبار", "شناسه تایید برداشت", "تایید مجوز برداشت"
    )

    /** پیامک‌های اطلاع‌رسانی بانک که تراکنش نیستند: خوش‌آمد و یادآوری قسط. */
    private val NOTICE = listOf(
        "خوش آمدید", "یادآوری بازپرداخت", "اقساط قرار داد"
    )

    private val FAILED = listOf(
        "ناموفق", "نا موفق", "انجام نشد", "موجودی کافی نیست", "عدم موجودی", "موجودی ناکافی"
    )

    /** متن را برای مقایسه یک‌شکل می‌کند: رقم لاتین، ی/ک فارسی، بدون نیم‌فاصله و فاصلهٔ اضافه، حروف کوچک. */
    fun normalize(body: String): String =
        BankUtils.convertNumeralsAndClean(body)
            .replace('‌', ' ')
            .replace('أ', 'ا').replace('ؤ', 'و').replace('ة', 'ه')
            .replace(Regex("\\s+"), " ")
            .lowercase()

    fun isOneTimeCode(body: String): Boolean {
        val t = normalize(body)
        if (ONE_TIME_CODE.any { t.contains(it) }) return true
        // «رمز» به‌تنهایی هم یعنی پیامک رمز؛ جز وقتی که فقط بخشی از «کارمزد» باشد.
        return t.replace("کارمزد", "").contains("رمز")
    }

    fun isNotice(body: String): Boolean {
        val t = normalize(body)
        return NOTICE.any { t.contains(it) }
    }

    fun isFailedAttempt(body: String): Boolean {
        val t = normalize(body)
        return FAILED.any { t.contains(it) }
    }

    fun isNonTransaction(body: String): Boolean = isOneTimeCode(body) || isFailedAttempt(body) || isNotice(body)
}
