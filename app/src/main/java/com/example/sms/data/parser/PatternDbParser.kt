package com.example.sms.data.parser

import com.example.sms.domain.model.ParsedTransactionModel
import com.example.utils.JalaliDate
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * پارسر مبتنی بر دیتابیس الگوی بانک‌ها ([BankPatternDb]).
 *
 * دو مرحله دارد، هر دو فقط روی قالب‌های همان بانک (از روی شمارهٔ فرستنده یا نام بانک):
 *
 * ۱. [Mode.TEMPLATE] — کل قالب بانک به یک regex تبدیل می‌شود (متن ثابت قالب باید عیناً در پیامک
 *    باشد و جای‌نگهدارها گروه‌های نام‌دار می‌شوند). دقیق‌ترین مسیر است، پس در زنجیره اول می‌آید.
 *
 * ۲. [Mode.SCORE] — همان امتیازدهی موتور اصلی: regex هر فیلد (مبلغ، شماره حساب، تاریخ، ساعت، نوع)
 *    جداگانه روی کل متن؛ هر فیلد موفق یک امتیاز. پذیرش با ۵ امتیاز، یا دست‌کم ۳ امتیاز به شرط
 *    داشتن مبلغ و شماره حساب. این مسیر پشتیبان است و در زنجیره پس از موتورهای دیگر می‌آید.
 *
 * مبلغ و مانده همان‌طور که در پیامک آمده‌اند برمی‌گردند (ریال/تومان با
 * [com.example.data.SmsIngest.toToman] تبدیل می‌شود).
 */
class PatternDbParser(
    private val mode: Mode = Mode.TEMPLATE,
    private val now: () -> Long = System::currentTimeMillis
) : SmsParser {

    enum class Mode { TEMPLATE, SCORE }

    override fun parse(body: String, bankName: String): ParsedTransactionModel? = parse(body, bankName, "")

    fun parse(body: String, bankName: String, sender: String): ParsedTransactionModel? {
        if (SmsFilter.isNonTransaction(body) || isPasswordSms(body)) return null
        // همهٔ بانک‌های این شماره (چند بانک شمارهٔ مشترک دارند) و بانکِ نامی که کاربر داده.
        val candidates = (BankPatternDb.banksForSender(sender) + listOfNotNull(BankPatternDb.bankForName(bankName)))
            .distinct().filter { it.templates.isNotEmpty() }
        if (candidates.isEmpty()) return null
        val text = SmsText.normalize(body).trim()
        return try {
            when (mode) {
                Mode.TEMPLATE -> byTemplate(text, candidates, bankName)
                Mode.SCORE -> candidates.firstNotNullOfOrNull { byScore(text, it, bankName) }
            }
        } catch (e: Exception) {
            null
        }
    }

    // ───────────────────────── مرحلهٔ ۱: قالب کامل ─────────────────────────

    private class Compiled(
        val template: BankPatternDb.Template,
        val pattern: Pattern?,
        /** تکه‌های ثابت قالب (بدون فاصله) برای پیش‌آزمون سریع، به ترتیب. */
        val literals: List<String>,
        val literalLength: Int,
        val hasType: Boolean
    )

    private class Hit(val c: Compiled, val amount: Long, val amountStart: Int, val amountEnd: Int,
                      val account: String, val date: String, val time: String, val typeText: String)

    private fun byTemplate(text: String, banks: List<BankPatternDb.Bank>, bankName: String): ParsedTransactionModel? {
        val squeezed = text.filterNot { it.isWhitespace() }
        val hits = banks.flatMap(::compiled).mapNotNull { hit(it, text, squeezed) }
        if (hits.isEmpty()) return null
        val best = hits.maxWith(compareBy<Hit> { it.c.literalLength }.thenBy { if (it.c.hasType) 1 else 0 })
        val bank = banks.first { it.name == best.c.template.bank }
        // جهت فقط از قالب‌های همان بانک، نه قالب‌های بانک هم‌شماره.
        val sameBank = hits.filter { it.c.template.bank == bank.name }

        val isDeposit = signAt(text, best.amountStart, best.amountEnd)
            ?: wordDirection(best.typeText)
            ?: sameBank.map { it.c.template.isDeposit }.distinct().singleOrNull()
            ?: messageType(text)
            ?: best.c.template.isDeposit

        return ParsedTransactionModel(
            transactionType = if (isDeposit) "deposit" else "withdrawal",
            amount = best.amount,
            date = timestamp(best.date, best.time),
            cardNumber = best.account.filter { it.isDigit() }.takeLast(4).ifEmpty { SmsText.cardLast4(text) },
            balance = SmsText.balance(text),
            bankName = bankName.ifBlank { bank.displayName },
            reference = "",
            merchant = ""
        )
    }

    /** این قالب کل پیامک را می‌پوشاند؟ (پیش‌آزمون سریع تکه‌های ثابت، بعد regex کامل) */
    private fun hit(c: Compiled, text: String, squeezed: String): Hit? {
        val p = c.pattern ?: return null
        if (c.literalLength < MIN_LITERAL) return null
        if (!literalsInOrder(squeezed, c.literals)) return null
        val m = p.matcher(text)
        if (!m.find()) return null
        val amountRaw = m.group("amount") ?: return null
        val amount = SmsText.money(amountRaw) ?: return null
        if (amount <= 0) return null
        return Hit(
            c, amount, m.start("amount"), m.end("amount"),
            account = groupOrEmpty(m, "account"),
            date = groupOrEmpty(m, "date"),
            time = groupOrEmpty(m, "time"),
            typeText = groupOrEmpty(m, "ttype")
        )
    }

    /**
     * بانک‌هایی که دست‌کم یکی از قالب‌هایشان این پیامک را کامل می‌پوشاند، بدون دانستن فرستنده.
     * قالب‌های خیلی کلی (کمتر از [minLiteral] نویسهٔ ثابت) حساب نمی‌شوند.
     */
    fun matchingBanks(body: String, minLiteral: Int = 8): List<BankPatternDb.Bank> {
        val text = SmsText.normalize(body).trim()
        if (text.isEmpty()) return emptyList()
        val squeezed = text.filterNot { it.isWhitespace() }
        return BankPatternDb.banks.filter { bank ->
            compiled(bank).any { it.literalLength >= minLiteral && hit(it, text, squeezed) != null }
        }
    }

    private fun groupOrEmpty(m: java.util.regex.Matcher, name: String): String =
        try { m.group(name).orEmpty() } catch (e: IllegalArgumentException) { "" }

    private fun literalsInOrder(squeezed: String, literals: List<String>): Boolean {
        var from = 0
        for (l in literals) {
            val at = squeezed.indexOf(l, from)
            if (at < 0) return false
            from = at + l.length
        }
        return true
    }

    private fun compiled(bank: BankPatternDb.Bank): List<Compiled> =
        cache.getOrPut(bank.name) { bank.templates.map(::compile) }

    private fun compile(t: BankPatternDb.Template): Compiled {
        val raw = SmsText.normalize(t.raw).trim()
        val sb = StringBuilder()
        val literals = ArrayList<String>()
        var literalLength = 0
        val used = HashSet<String>()
        var hasType = false
        var last = 0
        for (m in PLACEHOLDER.findAll(raw)) {
            literal(raw.substring(last, m.range.first), sb, literals).also { literalLength += it }
            val name = m.groupValues[1]
            val group = when (name) {
                "amount" -> "amount"
                "accountNumber" -> "account"
                "date" -> "date"
                "time" -> "time"
                "tType" -> "ttype"
                else -> null
            }
            if (group == null || !used.add(group)) {
                sb.append(if (name == "tType") "[^\\n]*?" else "[\\s\\S]*?")
            } else {
                if (group == "ttype") hasType = true
                // مبلغ و حساب از مرز عدد شروع و تمام می‌شوند؛ نه تکه‌ای از عددی بلندتر.
                val bounded = group == "amount" || group == "account"
                if (bounded) sb.append("(?<!\\d)(?<!\\d,)")
                sb.append("(?<").append(group).append('>').append(fieldRegex(group, t)).append(')')
                if (bounded) sb.append("(?!\\d)")
            }
            last = m.range.last + 1
        }
        literal(raw.substring(last), sb, literals).also { literalLength += it }
        val pattern = if ("amount" in used) {
            try { Pattern.compile(sb.toString()) } catch (e: Exception) { null }
        } else null
        return Compiled(t, pattern, literals, literalLength, hasType)
    }

    /** متن ثابت قالب: هر نویسه عیناً، و میان نویسه‌ها فاصلهٔ اختیاری (بانک‌ها فاصله را یکسان نمی‌گذارند). */
    private fun literal(part: String, sb: StringBuilder, literals: MutableList<String>): Int {
        val chars = part.filterNot { it.isWhitespace() }
        if (chars.isEmpty()) {
            if (part.isNotEmpty()) sb.append("\\s*")
            return 0
        }
        literals += chars
        sb.append("\\s*")
        chars.forEachIndexed { i, ch ->
            if (i > 0) sb.append("\\s*")
            sb.append(Pattern.quote(ch.toString()))
        }
        sb.append("\\s*")
        return chars.length
    }

    private fun fieldRegex(group: String, t: BankPatternDb.Template): String = when (group) {
        "amount" -> "\\d[\\d,]*"
        "time" -> "\\d{1,2}:\\d{1,2}(?::\\d{1,2})?"
        "ttype" -> "[^\\n]*?"
        "account" -> BankPatternDb.safeRegex(t.account.regex)
            ?.takeIf { it != ".*" && !it.contains("\\n") && !it.startsWith("\\s") }
            ?.let { "(?:$it)" }
            ?: "[\\d*][\\d*.\\-/,]*"
        // «\d{4}-» یعنی تاریخ با خط تیره تمام می‌شود؛ اگر قالب خودش «-» را بعد از تاریخ آورده، خط تیره اختیاری است.
        "date" -> t.date.regex?.trim()?.removePrefix("\\n")?.removePrefix("/")
            ?.let { r -> if (r.endsWith("-")) BankPatternDb.safeRegex(r.removeSuffix("-"))?.let { "(?:$it)-?" } else BankPatternDb.safeRegex(r)?.let { "(?:$it)" } }
            ?.takeIf { !it.contains(".*") && !it.contains("|") }
            ?: "\\d{1,4}(?:[/.,\\-]\\d{1,2}){0,2}"
        else -> "[\\s\\S]*?"
    }

    // ───────────────────────── مرحلهٔ ۲: امتیازدهی فیلدی ─────────────────────────

    private fun byScore(text: String, bank: BankPatternDb.Bank, bankName: String): ParsedTransactionModel? {
        for (t in bank.templates) {
            var score = 0
            val type = messageType(text)
            if (t.isDeposit && (text.contains("وار") || text.contains("+"))) score++
            else if (!t.isDeposit && (text.contains("برداشت") || text.contains("-"))) score++

            // مبلغ باید شکل پول داشته باشد (جداکنندهٔ هزارگان یا واحد پول)، نه تکه‌ای از شماره حساب.
            val amount = firstMatch(t.amount.regex, text) { looksLikeMoney(text, it) }
                ?.let { SmsText.money(it) }?.takeIf { it > 0 }
            if (amount != null) score++
            val dateRaw = firstMatch(t.date.regex?.removeSuffix("-"), text) { parseDate(it) != null }
            if (dateRaw != null) score++
            val timeRaw = firstMatch(t.time.regex, text) { parseTime(it) != null }
            if (timeRaw != null) score++
            val account = firstMatch(t.account.regex, text)
                ?.filter { it.isDigit() || it == '*' }?.takeIf { it.any(Char::isDigit) }
            if (account != null) score++

            val accepted = score >= 5 || (score >= 3 && amount != null && account != null)
            if (!accepted || amount == null) continue
            val isDeposit = type ?: t.isDeposit
            return ParsedTransactionModel(
                transactionType = if (isDeposit) "deposit" else "withdrawal",
                amount = amount,
                date = timestamp(dateRaw.orEmpty(), timeRaw.orEmpty()),
                cardNumber = account.orEmpty().filter { it.isDigit() }.takeLast(4),
                balance = SmsText.balance(text),
                bankName = bankName.ifBlank { bank.displayName },
                reference = "",
                merchant = ""
            )
        }
        return null
    }

    private fun firstMatch(regex: String?, text: String, valid: (String) -> Boolean = { true }): String? =
        firstMatchAt(regex, text) { _, g -> valid(g) }?.second

    private fun firstMatchAt(regex: String?, text: String, valid: (Int, String) -> Boolean): Pair<Int, String>? {
        val r = BankPatternDb.safeRegex(regex) ?: return null
        if (r == ".*") return null
        val m = Pattern.compile(r).matcher(text)
        while (m.find()) {
            val g = m.group()
            if (g.isNotBlank() && valid(m.start(), g)) return m.start() to g
        }
        return null
    }

    private fun looksLikeMoney(text: String, g: String): Boolean {
        if (MONEY.matches(g.trim())) return true
        val at = text.indexOf(g)
        return at >= 0 && UNIT_AFTER.containsMatchIn(text.substring(at + g.length))
    }

    // ───────────────────────── جهت تراکنش ─────────────────────────

    /** علامت چسبیده به مبلغ: «+۲۵٬۰۰۰» یا «۴۸۰٬۰۰۰-». */
    private fun signAt(text: String, start: Int, end: Int): Boolean? {
        val before = text.getOrNull(start - 1)
        val after = text.getOrNull(end)
        return when {
            before == '+' || after == '+' -> true
            after == '-' -> false
            before == '-' && text.getOrNull(start - 2)?.isDigit() != true -> false
            else -> null
        }
    }

    private fun wordDirection(t: String): Boolean? {
        if (t.isBlank()) return null
        val dep = DEPOSIT_WORDS.any { t.contains(it, ignoreCase = true) }
        val wd = WITHDRAW_WORDS.any { t.contains(it, ignoreCase = true) }
        return if (dep == wd) null else dep
    }

    /** setMessageType موتور اصلی: واریز/دریافت ← ۱، برداشت/خرید/کارمزد ← ۰، وگرنه علامت. */
    private fun messageType(m: String): Boolean? {
        val l = m.lowercase()
        if (m.contains("دریافت") || m.contains("واریز") || m.contains("به حساب شما نشست") || l.contains("variz")) return true
        if (m.contains("برداشت") || m.contains("خرید") || m.contains("کارمزد") || l.contains("bardasht") ||
            m.contains("از حساب شما پرید") || m.contains("از حساب شما پریید")
        ) return false
        if (m.contains("+")) return true
        if (m.contains("-")) return false
        return null
    }

    // ───────────────────────── تاریخ و ساعت ─────────────────────────

    private class D(val y: Int, val m: Int, val d: Int)

    private fun currentYear() = JalaliDate.fromTimestamp(now()).year

    private fun expandYear(y: Int, digits: Int): Int = when {
        digits >= 4 -> y
        y > 80 -> 1300 + y
        else -> 1400 + y
    }

    private fun parseDate(raw: String): D? {
        val parts = Regex("\\d+").findAll(raw).map { it.value }.toList()
        val cur = currentYear()
        val d: D? = when (parts.size) {
            3 -> {
                val (a, b, c) = parts
                when {
                    a.length == 4 -> D(a.toInt(), b.toInt(), c.toInt())
                    c.length == 4 -> D(c.toInt(), b.toInt(), a.toInt())
                    else -> D(expandYear(a.toInt(), a.length), b.toInt(), c.toInt())
                }
            }
            2 -> {
                val (a, b) = parts
                if (a.length == 4 || b.length == 4) null else D(cur, a.toInt(), b.toInt())
            }
            1 -> {
                val p = parts[0]
                when (p.length) {
                    4 -> D(cur, p.substring(0, 2).toInt(), p.substring(2, 4).toInt())
                    6 -> D(expandYear(p.substring(0, 2).toInt(), 2), p.substring(2, 4).toInt(), p.substring(4, 6).toInt())
                    8 -> D(p.substring(0, 4).toInt(), p.substring(4, 6).toInt(), p.substring(6, 8).toInt())
                    else -> null
                }
            }
            else -> null
        }
        if (d == null) return null
        if (d.y !in 1300..1499 || d.m !in 1..12 || d.d !in 1..31 || (d.m > 6 && d.d > 30)) return null
        return d
    }

    private fun parseTime(raw: String): Long? {
        val p = raw.split(':').mapNotNull { it.trim().toIntOrNull() }
        if (p.size < 2) return null
        val (h, m) = p
        val s = p.getOrElse(2) { 0 }
        if (h !in 0..23 || m !in 0..59 || s !in 0..59) return null
        return (h * 3600L + m * 60L + s) * 1000L
    }

    private fun timestamp(date: String, time: String): Long {
        val d = parseDate(date) ?: return now()
        return try {
            JalaliDate.toTimestamp(d.y, d.m, d.d) + (parseTime(time) ?: 0L)
        } catch (e: Exception) {
            now()
        }
    }

    companion object {
        /** قالب‌های کامپایل‌شده، مشترک میان همهٔ نمونه‌ها. */
        private val cache = ConcurrentHashMap<String, List<Compiled>>()

        private val PLACEHOLDER = Regex("\\[\\[(\\w+)\\]\\]")
        private val MONEY = Regex("\\d{1,3}(?:,\\d{3})+")
        private val UNIT_AFTER = Regex("^\\s*(?:ریال|تومان)")

        /** قالبی که کمتر از این تعداد نویسهٔ ثابت دارد آن‌قدر کلی است که هر پیامکی را می‌گیرد. */
        private const val MIN_LITERAL = 4

        private val DEPOSIT_WORDS = listOf("واریز", "دریافت", "سود", "برگشت", "عودت", "نشست", "variz", "deposit")
        private val WITHDRAW_WORDS = listOf("برداشت", "خرید", "پرداخت", "کسر", "قسط", "کارمزد", "bardasht", "withdraw", "purchase")

        /**
         * isPasswordSms موتور اصلی: پیامک‌های رمز، هشدار، خوش‌آمد و تایید برداشت. پیامک کارمزد فقط
         * وقتی رد می‌شود که «مدت زمان اعتبار» داشته باشد (کارمزد معمولی تراکنش واقعی است).
         */
        fun isPasswordSms(body: String): Boolean {
            val c = SmsText.normalize(body)
            if (c.contains("کارمزد")) return c.contains("مدت زمان اعتبار")
            return c.contains("رمز") || c.contains("کد ورود") || c.contains("ورود به") ||
                c.contains("خوش آمدید") || c.contains("بانک ملی (هشدار)") || c.contains("اقساط قرار داد") ||
                c.contains("تایید مجوز برداشت") || c.contains("شناسه تایید برداشت")
        }
    }
}
