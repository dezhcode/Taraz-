package com.example.sms.engine

import com.example.utils.JalaliDate

/** The unit an amount is printed in; the app itself works in toman. */
enum class AmountUnit { RIAL, TOMAN }

/** Which of the engines produced the result. */
enum class ParsedVia { PATTERN, SCRIPT, PATTERN_RELAXED }

/** A bank transaction read from one SMS. */
data class EngineTransaction(
    /** Catalog display name when known, e.g. «بانک ملی». */
    val bankName: String,
    val isIncome: Boolean,
    /** Exactly as printed in the message. */
    val amount: Long,
    val unit: AmountUnit,
    /** Jalali `YYYY-MM-DD`, or null when the message has no date. */
    val date: String?,
    /** `HH:MM`, or null when the message has no time. */
    val time: String?,
    val via: ParsedVia
) {
    /** The amount in toman, which is what the app stores. */
    val amountToman: Long get() = if (unit == AmountUnit.RIAL) amount / 10 else amount

    /**
     * When the transaction happened. A message without a date counts as "now"; a
     * date without a time is placed at midday. Bank messages never come from the
     * future, so a date that would be (a year-less «12/25» read in January) is
     * moved back one Jalali year.
     */
    fun timestamp(now: Long = System.currentTimeMillis()): Long {
        val d = date ?: return now
        val parts = d.split('-')
        if (parts.size != 3) return now
        val y = parts[0].toIntOrNull() ?: return now
        val m = parts[1].toIntOrNull() ?: return now
        val day = parts[2].toIntOrNull() ?: return now
        if (m !in 1..12 || day !in 1..31) return now
        val hm = time?.split(':')
        val hour = hm?.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 12
        val minute = hm?.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val offset = (hour * 60L + minute) * 60_000L
        val clampedDay = day.coerceAtMost(JalaliDate.daysInMonth(y, m))
        var ts = JalaliDate.toTimestamp(y, m, clampedDay) + offset
        if (ts > now + FUTURE_SLACK_MS) {
            val previous = JalaliDate.toTimestamp(y - 1, m, day.coerceAtMost(JalaliDate.daysInMonth(y - 1, m))) + offset
            if (previous <= now + FUTURE_SLACK_MS) ts = previous
        }
        return ts
    }

    private companion object {
        const val FUTURE_SLACK_MS = 36L * 60 * 60 * 1000
    }
}

/**
 * The single entry point for turning a bank SMS into a transaction.
 *
 * Both engines of the original apps are run, most trustworthy first:
 *  1. the pattern engine's strict pass (type, amount, date, time and account all found);
 *  2. the bank's hand-written script parser;
 *  3. the pattern engine's relaxed pass.
 *
 * Which bank's rules apply comes from [bankName] — chosen by the user when the
 * reader was switched on for a card — and, failing that, from the sender number.
 * A message that is a one-time code, or that says nothing about money moving, is
 * not a transaction no matter which engine would accept it.
 */
class BankSmsEngine(
    val database: PatternDatabase,
    private val jalaliYear: () -> Int = { JalaliDate.today().year }
) {
    private val script = ScriptEngine(jalaliYear)
    private val pattern = PatternEngine(jalaliYear)

    /** Banks whose rules apply to [sender]'s messages — what the UI may suggest. */
    fun banksForSender(sender: String?, message: String = ""): List<SmsBank> =
        database.forSender(sender, message).mapNotNull { BankCatalog.find(it.name) }

    fun parse(message: String, sender: String? = null, bankName: String? = null): EngineTransaction? {
        val clean = SmsText.cleanForParsing(message)
        if (looksLikeOneTimeCode(clean) || !hasTransactionSignal(clean)) return null

        val chosen = database.byName(BankCatalog.find(bankName)?.patternName ?: bankName)
        val banks = (listOfNotNull(chosen) + database.forSender(sender, clean)).distinct()
        if (banks.isEmpty()) return null

        pattern.parseStrict(banks, clean)?.let { return it.toTransaction(clean, ParsedVia.PATTERN) }

        val scriptKeys = banks.mapNotNull { BankCatalog.find(it.name)?.scriptKey }.distinct()
        for (key in scriptKeys) {
            val r = script.parse(key, clean) ?: continue
            if (r.amount <= 0) continue
            val display = banks.firstNotNullOfOrNull { b ->
                BankCatalog.find(b.name)?.takeIf { it.scriptKey == key }
            }?.displayName ?: key
            return EngineTransaction(
                bankName = display,
                isIncome = r.isIncome,
                amount = r.amount,
                unit = detectUnit(clean),
                date = r.date?.takeIf { isPlausibleDate(it) },
                time = r.time?.takeIf { it.isNotEmpty() },
                via = ParsedVia.SCRIPT
            )
        }

        pattern.parseRelaxed(banks, clean)?.let { return it.toTransaction(clean, ParsedVia.PATTERN_RELAXED) }
        pattern.parseRelaxed(banks, clean, requireAccount = false)
            ?.let { return it.toTransaction(clean, ParsedVia.PATTERN_RELAXED) }
        return null
    }

    /** `YYYY-MM-DD` with a real month/day and a year near today (a stray «98/04/11» is not 1498). */
    private fun isPlausibleDate(date: String): Boolean {
        if (!date.matches(ISO_JALALI)) return false
        val y = date.substring(0, 4).toInt()
        val m = date.substring(5, 7).toInt()
        val d = date.substring(8, 10).toInt()
        return y in 1380..(jalaliYear() + 1) && m in 1..12 && d in 1..31
    }

    private fun PatternOutcome.toTransaction(message: String, via: ParsedVia) = EngineTransaction(
        bankName = BankCatalog.find(bankName)?.displayName ?: bankName,
        isIncome = isIncome,
        amount = amount,
        unit = detectUnit(message),
        date = date,
        time = time,
        via = via
    )

    companion object {
        private val ISO_JALALI = Regex("""\d{4}-\d{2}-\d{2}""")

        private val ONE_TIME_CODE = Regex(
            """(کد|رمز)\s*(تایید|تأیید|یک\s?بار|فعال|ورود|پویا|دوم|اول)|\b(otp|cvv2?)\b|\bpin\b""",
            RegexOption.IGNORE_CASE
        )

        private val SIGNAL_WORDS = listOf(
            "واریز", "برداشت", "خرید", "پرداخت", "انتقال", "کارمزد", "برگشت", "سود", "کسر",
            "حقوق", "قسط", "دریافت", "تراکنش", "نشست", "پرید", "مانده", "موجودی", "تخصیص",
            "variz", "bardasht"
        )
        private val SIGNED_AMOUNT = Regex("""[+-]\s?\d{1,3}(,\d{3})+|\d{1,3}(,\d{3})+\s?[+-]""")

        /** A one-time-code text mentions the amount it protects but moves no money. */
        fun looksLikeOneTimeCode(message: String): Boolean =
            ONE_TIME_CODE.containsMatchIn(SmsText.normalizeLetters(message))

        /**
         * Something in the text says money moved: a verb («واریز»), a balance
         * line, or a signed amount («-1,060,000»). Without any, an amount is
         * just a number in an advertisement.
         */
        fun hasTransactionSignal(message: String): Boolean {
            val m = SmsText.normalizeLetters(message).lowercase()
            return SIGNAL_WORDS.any { m.contains(it) } || SIGNED_AMOUNT.containsMatchIn(m)
        }

        /** Banks print rial unless the text says «تومان» and never «ریال». */
        fun detectUnit(message: String): AmountUnit {
            val m = SmsText.normalizeLetters(message).lowercase()
            val saysToman = m.contains("تومان") || m.contains("toman")
            val saysRial = m.contains("ریال") || m.contains("rls") || m.contains("irr") || m.contains("rial")
            return if (saysToman && !saysRial) AmountUnit.TOMAN else AmountUnit.RIAL
        }
    }
}
