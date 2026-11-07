package com.example.sms.engine

import com.example.utils.JalaliDate

/** What one of the per-bank script parsers extracts from a message. */
data class ScriptResult(
    val isIncome: Boolean,
    /** As printed in the message (rial for almost every bank). */
    val amount: Long,
    /** Jalali `YYYY-MM-DD`; null when the message carries no usable date. */
    val date: String?,
    /** `HH:MM`; null when the message has no time. */
    val time: String?
)

internal class ScriptParseException(message: String) : Exception(message)

/**
 * Kotlin port of the 24 hand-written bank parsers of the JavaScript engine and
 * their shared helpers (`commonTypeParser`, `commonAmountParser`,
 * `commonDateParser(V2)`, `commonTimeParser`, `isNonTransactionMessage`).
 *
 * Every parser has the same contract: return the extracted fields or throw.
 * [parse] turns a throw into null, like the original `parseSms` catch block.
 * Messages that carry no year (`04/11`, `0411-20:35`) get the Jalali year of the
 * device at parse time, exactly as the original does.
 */
class ScriptEngine(
    private val jalaliYear: () -> Int = { JalaliDate.today().year }
) {

    private val parsers: Map<String, (String) -> ScriptResult> = mapOf(
        "saman" to this::standard,
        "blubank" to this::standard,
        "ayandeh" to this::ayandeh,
        "parsian" to this::standard,
        "day" to this::standard,
        "mellat" to this::standard,
        "melli" to this::melli,
        "sepah" to this::standard,
        "tejarat" to this::standard,
        "saderat" to this::standard,
        "keshavarzi" to this::standard,
        "enbank" to this::standard,
        "pasargad" to this::pasargad,
        "shahr" to this::standard,
        "refah" to this::standard,
        "mehriranian" to this::mehrIranian,
        "resalat" to this::standard,
        "sina" to this::standard,
        "mellal" to this::mellal,
        "tosetavon" to this::toseTavon,
        "karafarin" to this::standard,
        "khavarmiane" to this::khavarmiane,
        "gardeshgari" to this::standard,
        "maskan" to this::maskan
    )

    val supportedKeys: Set<String> get() = parsers.keys

    fun supports(bankKey: String?): Boolean = bankKey != null && parsers.containsKey(bankKey)

    /** Null when the bank has no script parser, the message is not a transaction, or parsing fails. */
    fun parse(bankKey: String, rawSms: String): ScriptResult? {
        val parser = parsers[bankKey] ?: return null
        val sms = SmsText.normalizeLetters(SmsText.cleanForParsing(rawSms))
        if (isNonTransactionMessage(sms)) return null
        return try {
            // Blu Bank sends from Saman's number; its text is what tells them apart.
            if (bankKey == "saman" && sms.contains("بلوبانک")) parsers.getValue("blubank")(sms)
            else parser(sms)
        } catch (e: ScriptParseException) {
            null
        } catch (e: RuntimeException) {
            null
        }
    }

    // ---- the parsers ------------------------------------------------------

    /** DayParser, MellatParser, SepahParser, ShahrParser, TejaratParser, ... — all the same chain. */
    private fun standard(sms: String): ScriptResult {
        val type = commonType(sms)
        val amount = commonAmount(sms)
        val (date, time) = commonDateV2(sms)
        return ScriptResult(type, amount, date, time.ifEmpty { null })
    }

    private fun pasargad(sms: String): ScriptResult {
        if (sms.contains("سرزمین هوشمند پاد") || sms.contains("ویپاد")) return pod(sms)
        return standard(sms)
    }

    /** PodMsgParser: no date and no time in its output. */
    private fun pod(sms: String): ScriptResult {
        val income = when {
            POD_EXPENSE.containsMatchIn(sms) -> false
            sms.contains("تخصیص داده شد") -> true
            else -> throw ScriptParseException("type")
        }
        return ScriptResult(income, commonAmount(sms), null, null)
    }

    private fun melli(sms: String): ScriptResult {
        val income = commonType(sms)
        val (amount, _) = signedAmount(sms)
        val withMonthDay = MELLI_DATE.find(sms)
        val date = if (withMonthDay != null) {
            val left = withMonthDay.value.split('-')[0]
            "${jalaliYear()}-${slice(left, 0, 2)}-${slice(left, 2, 4)}"
        } else {
            commonDateV2(sms).first
        }
        val time = commonTime(sms)
        return ScriptResult(income, amount, date, time.ifEmpty { null })
    }

    private fun maskan(sms: String): ScriptResult = melli(sms.replace("‎", "").replace("‏", ""))

    private fun ayandeh(sms: String): ScriptResult {
        var income: Boolean? = try { commonType(sms) } catch (e: ScriptParseException) { null }
        var date: String? = null
        var time: String? = null
        try {
            val r = commonDateV2(sms)
            date = r.first
            time = r.second
        } catch (e: ScriptParseException) {
            // The date is optional for Ayandeh; the amount is not.
        }
        var amount: Long? = try { commonAmount(sms) } catch (e: ScriptParseException) { null }
        if (amount == null) {
            val (value, matched) = signedAmount(sms)
            income = matched.contains('+')
            amount = value
        }
        return ScriptResult(income ?: false, amount, date?.ifEmpty { null }, time?.ifEmpty { null })
    }

    private fun mehrIranian(sms: String): ScriptResult {
        val date = commonDateV1(sms)
        val (amount, matched) = signedAmount(sms)
        // The original reads only the sign; with no sign at all the wording («واریز سود») decides.
        val income = when {
            matched.contains('+') -> true
            matched.contains('-') -> false
            else -> try { commonType(sms) } catch (e: ScriptParseException) { false }
        }
        return ScriptResult(income, amount, date, commonTime(sms).ifEmpty { null })
    }

    private fun mellal(sms: String): ScriptResult {
        val (amount, matched) = signedAmount(sms)
        val income = when {
            matched.contains('+') -> true
            matched.contains('-') -> false
            else -> commonType(sms)
        }
        val compact = MELLAL_DATE.find(sms)
        val date = if (compact != null) {
            var left = compact.value.split('-')[0]
            if (left.length == 6) left = "14$left"
            "${slice(left, 0, 4)}-${slice(left, 4, 6)}-${left.substring(minOf(6, left.length))}"
        } else {
            commonDateV2(sms).first.ifEmpty { throw ScriptParseException("date") }
        }
        return ScriptResult(income, amount, date, commonTime(sms).ifEmpty { null })
    }

    private fun toseTavon(sms: String): ScriptResult {
        val income = commonType(sms)
        val amount = commonAmount(sms)
        val full = TOSE_FULL.find(sms)
        val date: String
        if (full != null) {
            val sep = if (full.value.contains('-')) '-' else '_'
            val parts = full.value.split(sep)[0].split('/')
            date = "14${parts.getOrElse(0) { "" }}-${parts.getOrElse(1) { "" }}-${parts.getOrElse(2) { "" }}"
        } else {
            val short = TOSE_SHORT.find(sms) ?: throw ScriptParseException("date")
            val sep = if (short.value.contains('-')) '-' else '_'
            val parts = short.value.split(sep)[0].split('/')
            date = "${jalaliYear()}-${parts.getOrElse(0) { "" }}-${parts.getOrElse(1) { "" }}"
        }
        return ScriptResult(income, amount, date, commonTime(sms).ifEmpty { null })
    }

    private fun khavarmiane(sms: String): ScriptResult {
        val income = commonType(sms)
        val amount = commonAmount(sms)
        val (_, time) = commonDateV2(sms) // throws when there is no date at all, like the original
        val full = KHAVAR_FULL.find(sms)
        val date: String = if (full != null) {
            val parts = full.value.split('/')
            "${parts[0]}-${parts[1]}-${parts[2]}"
        } else {
            val line = sms.split('\n').map { it.trim() }.firstOrNull { it.contains('/') && it.length == 5 }
            val md = line?.let { KHAVAR_SHORT.find(it) }
            if (md != null) {
                val parts = md.value.split('/')
                "${jalaliYear()}-${parts[0]}-${parts[1]}"
            } else ""
        }
        return ScriptResult(income, amount, date.ifEmpty { null }, time.ifEmpty { null })
    }

    // ---- shared helpers ---------------------------------------------------

    /** `commonTypeParser`; true = income. */
    internal fun commonType(sms: String): Boolean {
        if (sms.contains("برگشت پول")) return true
        if (sms.contains("حساب شما نشست")) return true
        if (sms.contains("حساب شما پرید")) return false
        if (INCOME_WORDS.containsMatchIn(sms)) return true
        if (EXPENSE_WORDS.containsMatchIn(sms)) return false
        val m = AmountScan.firstTransactionMatch(sms, SIGNED_AMOUNT) ?: throw ScriptParseException("type")
        return m.value.contains('+')
    }

    /** `commonAmountParser`: first thousands-separated number that is not a balance. */
    internal fun commonAmount(sms: String): Long {
        val m = AmountScan.firstTransactionMatch(sms, GROUPED_AMOUNT) ?: throw ScriptParseException("amount")
        return AmountScan.toAmount(m.value) ?: throw ScriptParseException("amount")
    }

    /** The `(-|\+)?(\d{1,3}(,\d{3})+)(-|\+)?` match the Melli family reads its sign from. */
    private fun signedAmount(sms: String): Pair<Long, String> {
        val m = AmountScan.firstTransactionMatch(sms, SIGNED_AMOUNT) ?: throw ScriptParseException("amount")
        val value = AmountScan.toAmount(m.value) ?: throw ScriptParseException("amount")
        return value to m.value
    }

    /** `commonDateParser` (V1): `YYYY-MM-DD`, the year completed from 2 digits when needed. */
    internal fun commonDateV1(sms: String): String {
        val m = V1_DATE.find(sms) ?: throw ScriptParseException("date")
        val sep = if (m.value.contains('/')) '/' else '.'
        val parts = m.value.split(sep)
        val y = parts.getOrElse(0) { "" }
        val prefix = if (y.length == 2) (if ((y.toIntOrNull() ?: 0) > 80) "13" else "14") else ""
        return prefix + listOf(y, parts.getOrElse(1) { "" }, parts.getOrElse(2) { "" })
            .joinToString("-") { ensureTwoDigits(it) }
    }

    /**
     * `commonDateParserV2`: the V1 date when it is a 14xx year, otherwise the
     * first of eight formats banks use (some of them carry no year at all).
     * Returns (date, time); time is "" when the message has none.
     */
    internal fun commonDateV2(sms: String): Pair<String, String> {
        val time = commonTime(sms)
        val v1 = try { commonDateV1(sms) } catch (e: ScriptParseException) { null }
        if (v1 != null && v1.startsWith("14")) return v1 to time

        val year = jalaliYear()
        var date: String? = null

        // F1  20:35-0411
        F1.find(sms)?.let { m ->
            val right = m.value.split('-')[1]
            date = "$year-${slice(right, 0, 2)}-${slice(right, 2, 4)}"
        }
        // F2  04/11-20:35[:10]
        if (date == null) F2.find(sms)?.let { m ->
            val p = m.value.split('-')[0].split('/')
            date = "$year-${ensureTwoDigits(p[0])}-${ensureTwoDigits(p.getOrElse(1) { "" })}"
        }
        // F3  04/11/05_20:35
        if (date == null) F3.find(sms)?.let { m ->
            val p = m.value.split('_')[0].split('/')
            date = "14${p[0]}-${p[1]}-${p[2]}"
        }
        // F4  20:35[:10]-04/11
        if (date == null) F4.find(sms)?.let { m ->
            val p = m.value.split('-')[1].split('/')
            date = "$year-${ensureTwoDigits(p[0])}-${ensureTwoDigits(p.getOrElse(1) { "" })}"
        }
        // F5  040511-20:35
        if (date == null) F5.find(sms)?.let { m ->
            val left = m.value.split('-')[0]
            date = "14${slice(left, 0, 2)}-${slice(left, 2, 4)}-${slice(left, 4, 6)}"
        }
        // F6  4/11
        if (date == null) F6.find(sms)?.let { m ->
            val p = m.value.split('/')
            date = "$year-${ensureTwoDigits(p[0])}-${ensureTwoDigits(p.getOrElse(1) { "" })}"
        }
        // F7  1405/04/11-20:35
        if (date == null) F7.find(sms)?.let { m ->
            val p = m.value.split('-')[0].split('/')
            date = "${p[0]}-${p[1]}-${p[2]}"
        }
        // F8  0411 - 20:35
        if (date == null) F8.find(sms)?.let { m ->
            val s = m.value
            date = "$year-${slice(s, 0, 2)}-${slice(s, 2, 4)}"
        }

        val result = date ?: throw ScriptParseException("date")
        return result to time
    }

    /** `commonTimeParser`: HH:MM (seconds dropped); "" when there is no time. */
    internal fun commonTime(sms: String): String {
        val withSeconds = TIME_HMS.find(sms)
        if (withSeconds != null) return normalizeTime(withSeconds.value.substring(0, 5))
        val short = TIME_HM.find(sms)
        if (short != null) return normalizeTime(short.value)
        return ""
    }

    internal fun normalizeTime(t: String): String = t.split(':').joinToString(":") { it.padStart(2, '0') }

    internal fun ensureTwoDigits(x: String?): String {
        if (x.isNullOrEmpty()) return ""
        return if (x.length != 1) x else "0$x"
    }

    /** `isNonTransactionMessage`: one-time codes, welcome texts, reminders. */
    internal fun isNonTransactionMessage(sms: String): Boolean {
        for (k in NON_TRANSACTION_KEYWORDS) if (sms.contains(k)) return true
        // An OTP message says «رمز»; a fee message says «کارمزد», which contains it.
        if (sms.contains("رمز") && !sms.contains("کارمزد")) return true
        return false
    }

    private fun slice(s: String, from: Int, to: Int): String =
        s.substring(minOf(from, s.length), minOf(to, s.length))

    private companion object {
        val GROUPED_AMOUNT = Regex("""\d{1,3}(,\d{3})+""")
        val SIGNED_AMOUNT = Regex("""(-|\+)?(\d{1,3}(,\d{3})+)(-|\+)?""")
        val INCOME_WORDS = Regex("""واریز|سود""")
        val EXPENSE_WORDS = Regex("""برداشت|خرید""")
        val POD_EXPENSE = Regex("""خرید|پرداخت|برداشت|کارمزد""")

        val V1_DATE = Regex("""\d{2,4}[/.]\d{1,2}[/.]\d{1,2}""")
        val F1 = Regex("""(\d{2}:)?\d{2}:\d{2}-\d{4}""")
        val F2 = Regex("""\d{2}/\d{2}-\d{2}:\d{2}(:\d{2})?""")
        val F3 = Regex("""\d{2}/\d{2}/\d{2}_\d{2}:\d{2}""")
        val F4 = Regex("""\d{2}:\d{2}(:\d{2})?-\d{2}/\d{2}""")
        val F5 = Regex("""\d{6}-\d{2}:\d{2}""")
        val F6 = Regex("""\d{1,2}/\d{1,2}""")
        val F7 = Regex("""\d{4}/\d{2}/\d{2}-\d{2}:\d{2}""")
        val F8 = Regex("""\d{4} - \d{2}:\d{2}""")

        val TIME_HMS = Regex("""\d{2}:\d{2}:\d{2}""")
        val TIME_HM = Regex("""\d{1,2}:\d{2}""")

        val MELLI_DATE = Regex("""\d{4}-\d{2}:\d{2}""")
        val MELLAL_DATE = Regex("""\d{6,8}-\d{2}:\d{2}""")
        val TOSE_FULL = Regex("""\d{2}/\d{2}/\d{2}[_-]\d{2}:\d{2}""")
        val TOSE_SHORT = Regex("""\d{2}/\d{2}[_-]\d{2}:\d{2}""")
        val KHAVAR_FULL = Regex("""14\d{2}/\d{2}/\d{2}""")
        val KHAVAR_SHORT = Regex("""\d{2}/\d{2}""")

        val NON_TRANSACTION_KEYWORDS = listOf(
            "پويا", "پویا", "وارد", "ورود", "code", "CODE", "Code",
            "یادآوری بازپرداخت", "خوش آمديد", "خوش آمدید", "اعتبار تا",
            "رمز پویا:", "کد ", "کد تایید"
        )
    }
}
