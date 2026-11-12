package com.example.sms.data.parser

import com.example.sms.domain.model.ParsedTransactionModel
import com.example.sms.util.BankUtils
import com.example.utils.JalaliDate

/**
 * موتور خواندن پیامک بانکی، اختصاصی تراز و بدون نیاز به آموزش.
 *
 * به‌جای یک قالب برای هر بانک، «ساختار» پیامک را می‌خواند: برچسب‌ها (برداشت:، واریز:، مانده:)،
 * علامت مبلغ (+ و -)، و جمله‌های رایج («از حساب شما برداشت شد»). هر عدد فقط یک نقش می‌گیرد:
 * مبلغ، مانده، کارت، تاریخ، ساعت یا شمارهٔ پیگیری؛ پس مانده هرگز به‌جای مبلغ نمی‌نشیند.
 * اگر جهت تراکنش (برداشت یا واریز) روشن نباشد، حدس نمی‌زند و null برمی‌گرداند.
 *
 * مبلغ و مانده همان‌طور که در پیامک آمده برگردانده می‌شوند (ریال یا تومان)؛ تبدیل واحد با
 * [com.example.data.SmsIngest.toBookUnit] است.
 */
class EngineParser : SmsParser {

    override fun parse(body: String, bankName: String): ParsedTransactionModel? {
        if (SmsFilter.isNonTransaction(body)) return null
        val text = clean(body)
        if (text.isBlank()) return null

        val spans = ArrayList<Span>()
        val date = findDate(text, spans)
        val time = findTime(text, spans)
        val card = findCard(text, spans)
        val reference = findReference(text, spans)
        markLongNumbers(text, spans)

        val balance = findBalance(text, spans)
        val found = findAmount(text, spans) ?: return null
        if (found.amount <= 0) return null

        return ParsedTransactionModel(
            transactionType = if (found.isDeposit) "deposit" else "withdrawal",
            amount = found.amount,
            date = toTimestamp(date, time),
            cardNumber = card,
            balance = balance,
            bankName = bankName,
            reference = reference,
            merchant = findMerchant(text)
        )
    }

    // ───────────────────────── پاک‌سازی ─────────────────────────

    private class Span(val start: Int, val end: Int)

    private fun inSpan(spans: List<Span>, start: Int, end: Int) = spans.any { start < it.end && end > it.start }

    private fun clean(body: String): String =
        BankUtils.convertNumeralsAndClean(body)
            .replace('٬', ',').replace('٫', ',')           // ٬ ٫
            .replace('،', ',')
            .replace(Regex("[‌‎‏‪-‮⁦-⁩]"), " ")
            .replace('\r', '\n')
            .replace(Regex("[ \t]+"), " ")
            .trim()

    // ───────────────────────── تاریخ و ساعت ─────────────────────────

    private class DateParts(val y: Int, val m: Int, val d: Int)
    private class TimeParts(val h: Int, val min: Int, val s: Int)

    private val DATE = Regex("(?<![\\d/-])(\\d{4}|\\d{2})[/-](\\d{1,2})[/-](\\d{1,2})(?![\\d/-])")
    private val TIME = Regex("(?<![\\d:])(\\d{1,2}):(\\d{2})(?::(\\d{2}))?(?![\\d:])")

    private fun findDate(text: String, spans: MutableList<Span>): DateParts? {
        for (m in DATE.findAll(text)) {
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            val c = m.groupValues[3].toInt()
            // سال دو رقمی را از ۱۴۰۰ حساب می‌کنیم؛ ماه و روز باید معتبر باشند.
            val y = if (m.groupValues[1].length == 2) 1400 + a else a
            if (y !in 1300..1499 || b !in 1..12 || c !in 1..31) continue
            if (b > 6 && c > 30) continue
            spans += Span(m.range.first, m.range.last + 1)
            return DateParts(y, b, c)
        }
        // شکل‌های دو رقمی که ساختار تاریخ دارند ولی سال معتبر ندارند هم عدد مبلغ حساب نمی‌شوند.
        for (m in DATE.findAll(text)) spans += Span(m.range.first, m.range.last + 1)
        return null
    }

    private fun findTime(text: String, spans: MutableList<Span>): TimeParts? {
        for (m in TIME.findAll(text)) {
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            val s = m.groupValues[3].ifEmpty { "0" }.toInt()
            spans += Span(m.range.first, m.range.last + 1)
            if (h in 0..23 && min in 0..59 && s in 0..59) return TimeParts(h, min, s)
        }
        return null
    }

    private fun toTimestamp(date: DateParts?, time: TimeParts?): Long {
        if (date == null) return System.currentTimeMillis()
        return try {
            val base = JalaliDate.toTimestamp(date.y, date.m, date.d)
            base + (time?.let { (it.h * 3600L + it.min * 60L + it.s) * 1000L } ?: 0L)
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    // ───────────────────────── کارت و حساب ─────────────────────────

    private val MASKED_CARD = listOf(
        Regex("(?<!\\d)(\\d{4})[ -]?[*xX#•]{2,}[ -]?[*xX#•]*[ -]?(\\d{4})(?!\\d)"),   // 6037-****-****-1234 / 6037**1234
        Regex("[*xX#•]{2,}[ -]?(\\d{4})(?!\\d)"),                                       // ****1234
        Regex("(?<!\\d)(\\d{4})[*xX#•]{2,}(?![\\d*])")                                   // 1234****
    )
    private val CARD_LABEL = Regex("(?:کارت|شماره کارت|ش\\.? ?ک|card)\\s*[:：]?\\s*(\\d{4,16})(?!\\d)")
    private val FULL_CARD = Regex("(?<!\\d)(\\d{16})(?!\\d)")
    private val DASHED_CARD = Regex("(?<!\\d)\\d{4}[ -]\\d{4}[ -]\\d{4}[ -](\\d{4})(?!\\d)")
    private val ACCOUNT_LABEL = Regex("(?:حساب|شماره حساب|ش\\.? ?ح|acc)\\s*[:：]?\\s*([\\d.\\-]{6,24})")

    private fun findCard(text: String, spans: MutableList<Span>): String {
        for ((i, re) in MASKED_CARD.withIndex()) {
            val m = re.find(text) ?: continue
            spans += Span(m.range.first, m.range.last + 1)
            return m.groupValues[if (i == 0) 2 else 1].takeLast(4)
        }
        CARD_LABEL.find(text)?.let {
            spans += Span(it.groups[1]!!.range.first, it.groups[1]!!.range.last + 1)
            return it.groupValues[1].takeLast(4)
        }
        FULL_CARD.find(text)?.let {
            spans += Span(it.range.first, it.range.last + 1)
            return it.groupValues[1].takeLast(4)
        }
        DASHED_CARD.find(text)?.let {
            spans += Span(it.range.first, it.range.last + 1)
            return it.groupValues[1]
        }
        ACCOUNT_LABEL.find(text)?.let {
            spans += Span(it.groups[1]!!.range.first, it.groups[1]!!.range.last + 1)
            return it.groupValues[1].filter(Char::isDigit).takeLast(4)
        }
        return ""
    }

    // ───────────────────────── پیگیری و پذیرنده ─────────────────────────

    private val REFERENCE = Regex("(?<![A-Za-z])(?:شماره\\s*)?(?:پیگیری|مرجع|ارجاع|رهگیری|[Rr]ef)\\s*[:：.]?\\s*([0-9A-Za-z]{4,})")
    private val MERCHANT = Regex("(?:پذیرنده|فروشگاه|محل|شرح)\\s*[:：]\\s*([^\\n\\d]{2,40})")

    private fun findReference(text: String, spans: MutableList<Span>): String {
        val m = REFERENCE.find(text) ?: return ""
        spans += Span(m.groups[1]!!.range.first, m.groups[1]!!.range.last + 1)
        return m.groupValues[1]
    }

    private fun findMerchant(text: String): String = MERCHANT.find(text)?.groupValues?.get(1)?.trim().orEmpty()

    /** شمارهٔ حساب، شبا و کدهای بلند، هرگز مبلغ نیستند. */
    private fun markLongNumbers(text: String, spans: MutableList<Span>) {
        for (m in Regex("(?<![\\d,])\\d{10,}(?![\\d,])").findAll(text)) spans += Span(m.range.first, m.range.last + 1)
        for (m in Regex("(?i)IR\\s?\\d[\\d ]{20,}").findAll(text)) spans += Span(m.range.first, m.range.last + 1)
    }

    // ───────────────────────── اعداد ─────────────────────────

    private class Num(val value: Long, val start: Int, val end: Int, val sign: Char?)

    private val NUMBER = Regex("([+\\-])?\\s?(\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d{1,3}(?:\\.\\d{3}){2,}|\\d+(?:\\.\\d+)?)\\s?([+\\-])?")

    private fun numbers(text: String, spans: List<Span>): List<Num> {
        val out = ArrayList<Num>()
        for (m in NUMBER.findAll(text)) {
            val g = m.groups[2]!!
            if (inSpan(spans, g.range.first, g.range.last + 1)) continue
            val raw = g.value
            val intPart = when {
                raw.count { it == '.' } >= 2 -> raw.replace(".", "")
                raw.contains(',') -> raw.substringBefore('.').replace(",", "")
                else -> raw.substringBefore('.')
            }
            val value = intPart.toLongOrNull() ?: continue
            // علامت فقط وقتی چسبیده باشد: «+۲۵٬۰۰۰» یا «۲۵٬۰۰۰-»؛ نه خط تیرهٔ میان دو کلمه.
            val before = m.groups[1]?.value?.firstOrNull()
            val after = m.groups[3]?.value?.firstOrNull()
            val glued = if (before != null &&
                text.getOrNull(m.range.first + 1)?.isWhitespace() != true &&
                text.getOrNull(m.range.first - 1)?.isDigit() != true
            ) before else null
            val trailing = if (after != null &&
                text.getOrNull(m.range.last - 1)?.isWhitespace() != true &&
                text.getOrNull(m.range.last + 1)?.isDigit() != true
            ) after else null
            out += Num(value, g.range.first, g.range.last + 1, glued ?: trailing)
        }
        return out
    }

    // ───────────────────────── مانده ─────────────────────────

    private val BALANCE_LABEL = Regex("(?:مانده(?: حساب| قابل برداشت| کل| فعلی| جدید)?|موجودی(?: حساب| قابل برداشت| فعلی| جدید)?|(?<![A-Za-z])bal(?:ance)?)\\s*[:：]?", RegexOption.IGNORE_CASE)

    private val UNIT_ONLY = setOf("", "ریال", "تومان", "rls", "irr", "irt", "rial", "toman")

    /** «افزایش موجودی ۴۸۰٬۰۰۰» یا «کسر از موجودی»: اینجا «موجودی» مانده نیست، بخشی از جمله است. */
    private fun isBalanceWord(text: String, label: MatchResult): Boolean {
        val before = text.substring(maxOf(0, label.range.first - 10), label.range.first).trim()
        return !(before.endsWith("افزایش") || before.endsWith("کاهش") || before.endsWith("از") ||
            before.endsWith("به") || before.endsWith("در"))
    }

    private fun findBalance(text: String, spans: MutableList<Span>): Long {
        val nums = numbers(text, spans).filter { isMoney(it, text) }
        val labels = BALANCE_LABEL.findAll(text).filter { isBalanceWord(text, it) }.toList()
        // «مانده: ۱۲٬۰۰۰» یا «موجودی شما ۱۲٬۰۰۰ ریال»: نخستین عدد پولی بعد از برچسب
        for (lm in labels) {
            val n = nums.firstOrNull { it.start >= lm.range.last + 1 && it.start - (lm.range.last + 1) <= 24 }
            if (n != null) { spans += Span(n.start, n.end); return n.value }
        }
        // «۱۲٬۰۰۰ ریال مانده»: عدد پولی که فقط واحد پول از برچسب جدایش می‌کند
        for (lm in labels) {
            val n = nums.lastOrNull {
                it.end <= lm.range.first && text.substring(it.end, lm.range.first).trim().lowercase() in UNIT_ONLY
            }
            if (n != null) { spans += Span(n.start, n.end); return n.value }
        }
        return 0L
    }

    /** عددی که شکل پولی دارد: دست‌کم سه رقم، یا جداکنندهٔ هزارگان. */
    private fun isMoney(n: Num, text: String): Boolean {
        val raw = text.substring(n.start, n.end)
        return raw.contains(',') || raw.replace(".", "").length >= 3
    }

    // ───────────────────────── مبلغ و جهت ─────────────────────────

    private class Found(val amount: Long, val isDeposit: Boolean)

    private val WITHDRAW_LABELS = listOf("برداشت", "خرید", "پرداخت", "کسر", "قسط", "انتقال از", "withdraw", "purchase", "debit")
    private val DEPOSIT_LABELS = listOf("واریز", "دریافت", "اعتبار", "حقوق", "سود", "برگشت", "عودت", "deposit", "credit")
    private val FEE_LABELS = listOf("کارمزد")
    private val AMOUNT_LABEL = Regex("(?:مبلغ|amount)\\s*[:：]?", RegexOption.IGNORE_CASE)
    private val UNIT_AFTER = Regex("^\\s*(?:ریال|تومان|rls|irr|irt)", RegexOption.IGNORE_CASE)

    /** از میان چند عدد، پولی‌ترین: جداکنندهٔ هزارگان، بعد واحد پول، بعد پنج رقم و بیشتر. */
    private fun bestNumber(candidates: List<Num>, text: String): Num =
        candidates.firstOrNull { text.substring(it.start, it.end).contains(',') }
            ?: candidates.firstOrNull { UNIT_AFTER.containsMatchIn(text.substring(it.end)) }
            ?: candidates.firstOrNull { it.end - it.start >= 5 }
            ?: candidates.first()

    private fun findAmount(text: String, spans: List<Span>): Found? {
        val nums = numbers(text, spans).filter { isMoney(it, text) }
        if (nums.isEmpty()) return null

        // ۱) برچسب مبلغ در همان سطر: «برداشت: ۴۸۰٬۰۰۰» یا «واریز ۲۵٬۰۰۰٬۰۰۰ ریال»
        for (labels in listOf(WITHDRAW_LABELS to DEPOSIT_LABELS, FEE_LABELS to emptyList<String>())) {
            for (line in lineRanges(text)) {
                val lineText = text.substring(line.first, line.last)
                val lineNums = nums.filter { it.start >= line.first && it.end <= line.last }
                if (lineNums.isEmpty()) continue
                val w = firstLabel(lineText, labels.first)
                val d = firstLabel(lineText, labels.second)
                if ((w >= 0) == (d >= 0)) continue
                // «مانده پس از برداشت: …» سطر مانده است، نه مبلغ
                val bal = BALANCE_LABEL.find(lineText)
                if (bal != null && bal.range.first < maxOf(w, d)) continue
                return Found(bestNumber(lineNums, text).value, isDeposit = d >= 0)
            }
        }

        // ۲) علامت مبلغ: «+۲۵٬۰۰۰» واریز، «-۴۸۰٬۰۰۰» یا «۴۸۰٬۰۰۰-» برداشت
        nums.firstOrNull { it.sign != null }?.let { return Found(it.value, isDeposit = it.sign == '+') }

        // ۳) جمله‌های رایج: مبلغ بعد از «مبلغ» یا کنار ریال/تومان، جهت از معنای جمله
        val direction = sentenceDirection(text) ?: return null
        val labeled = AMOUNT_LABEL.find(text)?.let { lm -> nums.firstOrNull { it.start >= lm.range.last + 1 } }
        val withUnit = nums.firstOrNull { UNIT_AFTER.containsMatchIn(text.substring(it.end)) }
        val pick = labeled ?: withUnit ?: bestNumber(nums, text)
        return Found(pick.value, isDeposit = direction)
    }

    private fun lineRanges(text: String): List<IntRange> {
        val out = ArrayList<IntRange>()
        var start = 0
        for (i in 0..text.length) {
            if (i == text.length || text[i] == '\n') {
                if (i > start) out += start..i
                start = i + 1
            }
        }
        return out
    }

    private fun firstLabel(line: String, labels: List<String>): Int {
        val l = line.lowercase()
        return labels.map { l.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: -1
    }

    /** true = واریز، false = برداشت، null = روشن نیست. */
    private fun sentenceDirection(text: String): Boolean? {
        val t = text.lowercase()
        val toYou = Regex("(?:به|در)\\s*(?:حساب|کارت)\\s*(?:شما|[^\\n]{0,24}?شما)")
        val fromYou = Regex("از\\s*(?:حساب|کارت)\\s*(?:شما)?")
        if (toYou.containsMatchIn(t) && Regex("واریز|منتقل|اضافه|افزایش|دریافت|اعتبار").containsMatchIn(t)) return true
        if (Regex("برداشت|کسر|خرید|پرداخت|قسط|کارمزد|withdraw|purchase|debit").containsMatchIn(t)) return false
        if (Regex("واریز|دریافت|اعتبار|حقوق|سود|برگشت|عودت|deposit|credit").containsMatchIn(t)) return true
        if (fromYou.containsMatchIn(t) && t.contains("انتقال")) return false
        return null
    }
}
