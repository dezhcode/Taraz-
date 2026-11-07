package com.example.sms.engine

import com.example.utils.JalaliDate

/** What the pattern engine extracted from a message. */
data class PatternOutcome(
    val bankName: String,
    val isIncome: Boolean,
    /** As printed in the message. */
    val amount: Long,
    /** Jalali `YYYY-MM-DD`; null when the message has no usable date. */
    val date: String?,
    /** `HH:MM`; null when the message has no usable time. */
    val time: String?,
    val accountNumber: String?,
    /** Number of the five checks (type, amount, date, time, account) that passed. */
    val score: Int,
    /** True when it came from the all-five-checks pass, false from the relaxed pass. */
    val strict: Boolean
)

/**
 * Kotlin port of the Java engine's `SMSTextProcessor`.
 *
 * Each bank has many layouts; every layout carries one regex per field. A layout
 * is tried by running its regexes over the whole message and counting how many of
 * the five fields (type, amount, date, time, account number) are found:
 *  - strict pass (`compareWithPatterns`): all five found;
 *  - relaxed pass (`compareWithPatterns2`): at least the amount and the account
 *    number are found, a missing date/time stands in as "now".
 * The direction of the money is decided separately by [messageIsIncome].
 */
class PatternEngine(
    private val jalaliYear: () -> Int = { JalaliDate.today().year }
) {

    private val regexCache = HashMap<String, Regex?>()

    /** Strict pass first, then the relaxed one. */
    fun parse(banks: List<PatternBank>, rawMessage: String): PatternOutcome? =
        parseStrict(banks, rawMessage) ?: parseRelaxed(banks, rawMessage)

    fun parseStrict(banks: List<PatternBank>, rawMessage: String): PatternOutcome? {
        val message = SmsText.cleanForParsing(rawMessage)
        for (bank in banks) {
            for (template in bank.templates) {
                val c = Candidate(message)
                var score = 0
                if (checkDate(template, c)) score++
                if (checkAccount(template, c)) score++
                if (checkAmount(template, c)) score++
                if (checkTime(template, c)) score++
                if (checkType(template, message)) score++
                if (score > 4 && !isPasswordSms(message) && c.account != null) {
                    return c.toOutcome(bank.name, message, score, strict = true)
                }
            }
        }
        return null
    }

    /**
     * [requireAccount] = false is the last resort: the amount and the clear
     * wording are enough even when the layout's account-number regex does not
     * match (a bank that changed how it prints accounts).
     */
    fun parseRelaxed(banks: List<PatternBank>, rawMessage: String, requireAccount: Boolean = true): PatternOutcome? {
        val message = SmsText.cleanForParsing(rawMessage)
        val normalized = SmsText.normalizeLetters(message)
        for (bank in banks) {
            val needsAmountWord = bank.name.contains("ملت")
            if (needsAmountWord && !normalized.contains("مبلغ")) continue
            // Blu Bank and the Pod wallet send no account number at all.
            val hasNoAccount = bank.name.contains("بلو") || bank.name.contains("سرزمین هوشمند پاد")
            for (template in bank.templates) {
                val c = Candidate(message)
                var score = 0
                checkDate(template, c)
                score++ // a missing date still counts: "today" stands in
                if (hasNoAccount) { c.account = ""; score++ }
                else if (checkAccount(template, c)) score++
                else if (!requireAccount) { c.account = ""; score++ }
                if (checkAmount(template, c)) score++
                checkTime(template, c)
                score++ // likewise for the time
                if (checkType(template, normalized)) score++
                if (score >= 3 && !isPasswordSms(message) && c.account != null && c.amount != null) {
                    return c.toOutcome(bank.name, message, score, strict = false)
                }
            }
        }
        return null
    }

    // ---- field checks (each fills the candidate and says whether it scored) ----

    private class Candidate(val message: String) {
        var date: String? = null
        var time: String? = null
        var account: String? = null
        var amount: Long? = null

        fun toOutcome(bank: String, message: String, score: Int, strict: Boolean) = PatternOutcome(
            bankName = bank,
            isIncome = messageIsIncome(message),
            amount = amount ?: 0L,
            date = date,
            time = time,
            accountNumber = account?.ifEmpty { null },
            score = score,
            strict = strict
        )
    }

    private fun checkDate(t: PatternTemplate, c: Candidate): Boolean {
        val regex = compile(t.date) ?: return false
        c.date = retrieveDate(regex, c.message)
        return c.date != null
    }

    private fun checkAccount(t: PatternTemplate, c: Candidate): Boolean {
        val regex = compile(t.account) ?: return false
        val raw = regex.find(c.message)?.value ?: return false
        c.account = formatAccountNumber(raw)
        return true
    }

    private fun checkAmount(t: PatternTemplate, c: Candidate): Boolean {
        val regex = compile(t.amount) ?: return false
        val m = AmountScan.firstTransactionMatch(c.message, regex) ?: return false
        val value = AmountScan.toAmount(m.value) ?: return false
        if (value <= 0) return false
        c.amount = value
        return true
    }

    private fun checkTime(t: PatternTemplate, c: Candidate): Boolean {
        val regex = compile(t.time) ?: return false
        val raw = regex.find(c.message)?.value ?: return false
        c.time = formatTime(raw)
        return c.time != null
    }

    /**
     * With a type regex it must be found in the message; without one the type
     * name decides: a deposit layout wants «وار» or «+», a withdrawal «برداشت» or «-».
     */
    private fun checkType(t: PatternTemplate, message: String): Boolean {
        val identity = t.typeIdentity
        if (identity != null) {
            val regex = compile(identity)
            if (regex != null && regex.containsMatchIn(message)) return true
            // Identity words are sometimes spelled with Arabic letters; try the normalised pair too.
            val normalizedRegex = compile(SmsText.normalizeLetters(identity))
            return normalizedRegex != null && normalizedRegex.containsMatchIn(SmsText.normalizeLetters(message))
        }
        return when (t.typeName?.let { SmsText.nameKey(it) }) {
            "واریز" -> message.contains("وار") || message.contains("+")
            "برداشت" -> message.contains("برداشت") || message.contains("-")
            else -> false
        }
    }

    /**
     * `retrievePatternFromMessage`: the first match of the date regex that is a
     * plausible Jalali date. Six-, four- and five-character matches are first
     * checked for a sane month/day (a stray «0412» in a phone number is not a date).
     */
    private fun retrieveDate(regex: Regex, message: String): String? {
        for (m in regex.findAll(message)) {
            val c = m.value
            val plausible = when {
                c.length == 6 -> {
                    val month = c.substring(2, 4).toIntOrNull()
                    val day = c.substring(4, 6).toIntOrNull()
                    month != null && day != null && validDate(month, day) && validYear(c.substring(0, 2))
                }
                c.length == 4 -> {
                    val month = c.substring(0, 2).toIntOrNull()
                    val day = c.substring(2, 4).toIntOrNull()
                    month != null && day != null && validDate(month, day)
                }
                c.length == 5 && c.contains('/') -> {
                    val month = c.substring(0, 2).toIntOrNull()
                    val day = c.substring(3, 5).toIntOrNull()
                    month != null && day != null && validDate(month, day)
                }
                else -> true
            }
            if (!plausible) continue
            JalaliParts.parse(c, jalaliYear())?.let { return it }
        }
        return null
    }

    private fun validDate(month: Int, day: Int): Boolean = when (month) {
        in 1..6 -> day in 1..31
        in 7..12 -> day in 1..30
        else -> false
    }

    /** Two-digit years 00–04 are rejected: they are what stray digit runs look like, not 1300–1304. */
    private fun validYear(yy: String): Boolean = yy !in setOf("00", "01", "02", "03", "04")

    private fun compile(pattern: String?): Regex? {
        if (pattern == null) return null
        synchronized(regexCache) {
            if (regexCache.containsKey(pattern)) return regexCache[pattern]
            // Some exports double-escape backslashes; fold them back.
            val normalized = pattern.replace("\\\\", "\\")
            val compiled = try { Regex(normalized) } catch (e: Exception) { null }
            regexCache[pattern] = compiled
            return compiled
        }
    }

    companion object {

        /** `setMessageType`: true = money arrived. Words first, then the sign; expense by default. */
        fun messageIsIncome(rawMessage: String): Boolean {
            val m = SmsText.normalizeLetters(rawMessage)
            val lower = m.lowercase()
            if (m.contains("دریافت") || m.contains("واریز") || m.contains("به حساب شما نشست") || lower.contains("variz")) return true
            if (m.contains("برداشت") || m.contains("خرید") || m.contains("کارمزد") ||
                lower.contains("bardasht") || m.contains("از حساب شما پریید") || m.contains("از حساب شما پرید")
            ) return false
            if (m.contains("+")) return true
            return false
        }

        /**
         * `isPasswordSms`: texts that are not transactions — passwords, login codes,
         * warnings. A fee message is a transaction unless it is the notice about
         * how long a transaction stays valid.
         */
        fun isPasswordSms(rawMessage: String): Boolean {
            val c = SmsText.normalizeLetters(rawMessage)
            if (c.contains("کارمزد")) return c.contains("مدت زمان اعتبار تراکنش")
            return c.contains("رمز") || c.contains("کد ورود") || c.contains("ورود") ||
                c.contains("به سامانه همراه بانک ملت خوش آمدید.") ||
                c.contains("بانک ملی (هشدار)") || c.contains("اقساط قرار داد") ||
                c.contains("هشدار") || c.contains("تایید مجوز برداشت") ||
                c.contains("شناسه تایید برداشت")
        }

        /** `FormatAcountNumber`: drops the characters banks use to mask or group an account. */
        fun formatAccountNumber(s: String): String =
            s.replace("*", "").replace("-", "").replace(".", "").replace(",", "").replace("/", "")

        /** `DateTimeParser.formatTime`: HH:MM, or null when it is not a clock time. */
        fun formatTime(raw: String): String? {
            val parts = raw.trim().split(':')
            if (parts.size < 2) return null
            val h = parts[0].trim().toIntOrNull() ?: return null
            val m = parts[1].trim().toIntOrNull() ?: return null
            if (h !in 0..23 || m !in 0..59) return null
            // Not String.format: under a Persian locale %d writes Persian digits.
            return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
        }
    }
}

/**
 * Turns the date shapes in the pattern database into `YYYY-MM-DD`:
 * `1405/04/11`, `05/04/11`, `04/11`, `0411`, `050411`, `14050411`, `05,04,11`,
 * and `11 تیر 1405`. Shapes without a year take the current Jalali year.
 */
internal object JalaliParts {

    fun parse(raw: String, currentYear: Int): String? {
        val s = SmsText.convertEn(raw).trim()
        if (s.isEmpty()) return null

        val y: Int
        val m: Int
        val d: Int

        if (s.any { it.isLetter() }) {
            // «11 تیر 1405»
            val monthIndex = JalaliDate.MONTH_NAMES.indexOfFirst { s.contains(it) }
            if (monthIndex < 0) return null
            val numbers = Regex("""\d+""").findAll(s).map { it.value }.toList()
            if (numbers.size < 2) return null
            val day = numbers.first()
            val year = numbers.last()
            if (day.length > 2 || year.length != 4) return null
            d = day.toInt(); m = monthIndex + 1; y = year.toInt()
        } else {
            val tokens = Regex("""\d+""").findAll(s).map { it.value }.toList()
            when (tokens.size) {
                3 -> {
                    val (a, b, c) = tokens
                    when {
                        a.length == 4 -> { y = a.toInt(); m = b.toIntOrNull() ?: return null; d = c.toIntOrNull() ?: return null }
                        c.length == 4 -> { y = c.toInt(); m = b.toIntOrNull() ?: return null; d = a.toIntOrNull() ?: return null }
                        a.length <= 2 && c.length <= 2 -> {
                            y = twoDigitYear(a.toInt()); m = b.toIntOrNull() ?: return null; d = c.toIntOrNull() ?: return null
                        }
                        else -> return null
                    }
                }
                2 -> {
                    y = currentYear
                    m = tokens[0].toIntOrNull() ?: return null
                    d = tokens[1].toIntOrNull() ?: return null
                    if (tokens[0].length > 2 || tokens[1].length > 2) return null
                }
                1 -> {
                    val t = tokens[0]
                    when (t.length) {
                        4 -> { y = currentYear; m = t.substring(0, 2).toInt(); d = t.substring(2, 4).toInt() }
                        6 -> { y = twoDigitYear(t.substring(0, 2).toInt()); m = t.substring(2, 4).toInt(); d = t.substring(4, 6).toInt() }
                        8 -> { y = t.substring(0, 4).toInt(); m = t.substring(4, 6).toInt(); d = t.substring(6, 8).toInt() }
                        else -> return null
                    }
                }
                else -> return null
            }
        }

        if (y !in 1370..1499) return null
        if (m !in 1..12) return null
        val maxDay = if (m <= 6) 31 else 30
        if (d !in 1..maxDay) return null
        return "${y.toString().padStart(4, '0')}-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
    }

    /** Same pivot as the script engine: above 80 is 13xx, otherwise 14xx. */
    private fun twoDigitYear(yy: Int): Int = if (yy > 80) 1300 + yy else 1400 + yy
}
