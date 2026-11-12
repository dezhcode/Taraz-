package com.example.sms.data.parser

/**
 * یکسان‌سازی متن پیامک پیش از هر regex: ارقام فارسی/عربی به لاتین، «ي/ك» عربی به «ی/ک» فارسی،
 * جداکنندهٔ هزارگان فارسی به کاما، حذف نویسه‌های نامرئی جهت متن (LRM/RLM/…) و یک‌شکل کردن سطرها.
 *
 * هم پیامک و هم قالب‌های بانک‌ها از همین مسیر می‌گذرند تا با هم مقایسه‌پذیر باشند.
 */
object SmsText {

    private val BIDI = Regex("[‎‏‪-‮⁦-⁩﻿ـ]")

    fun normalize(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            sb.append(
                when (ch) {
                    in '۰'..'۹' -> '0' + (ch - '۰')
                    in '٠'..'٩' -> '0' + (ch - '٠')
                    'ي', 'ى' -> 'ی'
                    'ك' -> 'ک'
                    'ة' -> 'ه'
                    'ۀ' -> 'ه'
                    '٬', '٫', '،' -> ','
                    '‌' -> ' '
                    '\r' -> '\n'
                    else -> ch
                }
            )
        }
        return BIDI.replace(sb.toString(), "")
            .replace(Regex("\n[ \t]*\n+"), "\n")
            .replace(Regex("[ \t]+"), " ")
    }

    /** «۱۲,۵۰۰,۰۰۰» → 12500000؛ null اگر رقمی نباشد. */
    fun money(raw: String?): Long? {
        val digits = raw.orEmpty().filter { it in '0'..'9' }
        if (digits.isEmpty() || digits.length > 15) return null
        return digits.toLongOrNull()
    }

    // ───────────── مانده و کارت، مشترک پارسرهای بانکی ─────────────

    private val BALANCE = Regex(
        "(?:مانده|موجودی|مانده حساب|مانده قابل برداشت|موجودی حساب)\\s*(?:حساب|قابل برداشت|فعلی|جدید)?\\s*[:：]?\\s*(?:ریال|تومان)?\\s*([+-]?\\s?\\d{1,3}(?:,\\d{3})+|[+-]?\\d+)"
    )

    /** نخستین عدد پس از «مانده/موجودی»؛ صفر اگر نباشد. */
    fun balance(text: String): Long {
        for (m in BALANCE.findAll(text)) {
            val before = text.substring(maxOf(0, m.range.first - 8), m.range.first)
            // «افزایش موجودی» یا «کسر از موجودی» برچسب مانده نیست.
            if (before.trimEnd().endsWith("افزایش") || before.trimEnd().endsWith("از")) continue
            money(m.groupValues[1])?.let { return it }
        }
        return 0L
    }

    private val CARD_PATTERNS = listOf(
        Regex("(?<!\\d)\\d{4,6}[ -]?[*xX#•]{2,}[ -]?[*xX#•]*[ -]?(\\d{3,4})(?!\\d)"),
        Regex("[*xX#•]{2,}[ -]?(\\d{3,4})(?!\\d)"),
        Regex("(?:کارت|حساب|سپرده)\\s*[:：]?\\s*([\\d][\\d.\\-/]{3,30}\\d)")
    )

    /** چهار رقم آخر کارت یا حساب، برای پیدا کردن حساب در تراز؛ رشتهٔ خالی اگر نباشد. */
    fun cardLast4(text: String): String {
        for (re in CARD_PATTERNS) {
            val m = re.find(text) ?: continue
            val digits = m.groupValues[1].filter { it.isDigit() }
            if (digits.length >= 3) return digits.takeLast(4)
        }
        return ""
    }
}
