package com.example.logic

import com.example.sms.data.parser.BankPatternDb
import com.example.sms.data.parser.BankRuleParser
import com.example.sms.data.parser.CompositeParser
import com.example.sms.data.parser.PatternDbParser
import com.example.sms.data.parser.SmsText
import com.example.utils.JalaliDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern

/**
 * پارسرهای بانکی: دیتابیس الگو (۴۷ بانک) و ۲۵ پارسر اختصاصی. متن‌ها نمونهٔ ساختگی‌اند؛ اعداد فرضی‌اند.
 */
class BankSmsParsersTest {

    /** ۱۷ آبان ۱۴۰۳، ظهر: سال جاری پارسرها برای تاریخ‌های کوتاه. */
    private val fixedNow = JalaliDate.toTimestamp(1403, 8, 17) + 12 * 3600_000L
    private val rules = BankRuleParser { fixedNow }
    private val template = PatternDbParser(PatternDbParser.Mode.TEMPLATE) { fixedNow }
    private val score = PatternDbParser(PatternDbParser.Mode.SCORE) { fixedNow }

    // ───────────────────────── دیتابیس الگو ─────────────────────────

    @Test
    fun patternDatabaseLoadsAllBanks() {
        val banks = BankPatternDb.banks
        assertEquals(47, banks.size)
        assertEquals(1077, banks.sumOf { it.templates.size })
    }

    @Test
    fun senderNumbersFindTheirBank() {
        assertEquals("ملی", BankPatternDb.bankForSender("+98700717")?.name)
        assertEquals("ملی", BankPatternDb.bankForSender("700717")?.name)
        assertEquals("ملی", BankPatternDb.bankForSender("Bank Melli")?.name)
        assertEquals("پاسارگاد", BankPatternDb.bankForSender("98500019000")?.name)
        assertEquals("پاسارگاد", BankPatternDb.bankForSender("+98500019000")?.name)
        assertEquals("ملت", BankPatternDb.bankForSender("BankMellat")?.name)
        assertEquals("بانک ملی", BankPatternDb.bankForSender("+98700717")?.displayName)
        assertNull(BankPatternDb.bankForSender("+989121234567"))
        assertNull(BankPatternDb.bankForSender(""))
    }

    @Test
    fun bankNamesFindTheirRecord() {
        assertEquals("ملی", BankPatternDb.bankForName("بانک ملی")?.name)
        assertEquals("رفاه کارگران", BankPatternDb.bankForName("بانک رفاه")?.name)
        assertEquals("قرض الحسنه مهرایران", BankPatternDb.bankForName("بانک قرض الحسنه مهر ایران")?.name)
        assertEquals("کارافرین", BankPatternDb.bankForName("بانک کارآفرین")?.name)
        assertEquals("دی", BankPatternDb.bankForName("بانک دی")?.name)
        assertNull(BankPatternDb.bankForName("بانک نامشخص (1234)"))
    }

    /**
     * از هر قالب دیتابیس یک پیامک ساختگی ساخته و خوانده می‌شود. مبلغ باید تقریباً همیشه درست باشد؛
     * جهت تراکنش در قالب‌هایی که برداشت و واریزشان متن یکسان دارند ذاتاً مبهم است.
     */
    @Test
    fun everyTemplateReadsItsOwnSample() {
        var total = 0
        var amountOk = 0
        var typeOk = 0
        val misses = ArrayList<String>()
        for (bank in BankPatternDb.banks) for (t in bank.templates) {
            if (!t.raw.contains("[[amount]]")) continue
            val body = sample(t) ?: continue
            total++
            val r = template.parse(body, "", bank.numbers.first())
                ?: score.parse(body, "", bank.numbers.first())
            if (r != null && r.amount == 1_250_000L) amountOk++ else if (misses.size < 40) misses += "${bank.name}: $body"
            if (r != null && (r.transactionType == "deposit") == t.isDeposit) typeOk++
        }
        assertTrue("too few samples: $total", total > 1000)
        assertTrue("amount read in $amountOk of $total\n" + misses.joinToString("\n---\n"), amountOk >= total * 0.99)
        assertTrue("type right in $typeOk of $total", typeOk >= total * 0.98)
    }

    @Test
    fun pasargadTemplateGivesAccountTypeAndBalance() {
        val body = "بانك پاسارگاد\nحساب شما\nبرداشت از: 123.456.789.1\nمبلغ: 1,250,000 ريال\n13:45_03/08/17\nموجودي: 9,000,000 ريال"
        val r = template.parse(body, "", "98500019000")!!
        assertEquals("withdrawal", r.transactionType)
        assertEquals(1_250_000L, r.amount)
        assertEquals(9_000_000L, r.balance)
        assertEquals("7891", r.cardNumber)
        assertEquals(JalaliDate.toTimestamp(1403, 8, 17) + (13 * 3600L + 45 * 60L) * 1000L, r.date)
    }

    @Test
    fun signNextToTheAmountDecidesBetweenLookAlikeTemplates() {
        val out = "بانک ملی\nانتقال:1,250,000-\nحساب:12345\nمانده:9,000,000\n0817-13:45"
        val inn = "بانک ملی\nانتقال:1,250,000+\nحساب:12345\nمانده:9,000,000\n0817-13:45"
        assertEquals("withdrawal", template.parse(out, "بانک ملی", "")!!.transactionType)
        assertEquals("deposit", template.parse(inn, "بانک ملی", "")!!.transactionType)
        assertEquals(1_250_000L, template.parse(inn, "بانک ملی", "")!!.amount)
    }

    @Test
    fun unknownBankIsLeftToTheOtherParsers() {
        assertNull(template.parse("برداشت 1,000,000 ریال", "بانک نامشخص", "+989121234567"))
    }

    // ───────────────────────── پارسرهای اختصاصی ─────────────────────────

    @Test
    fun melliSignedAmountAndShortDate() {
        val r = rules.parse("بانک ملی ایران\nانتقال:1,250,000-\nحساب:12345\nمانده:9,000,000\n0817-13:45", "بانک ملی")!!
        assertEquals("withdrawal", r.transactionType)
        assertEquals(1_250_000L, r.amount)
        assertEquals(9_000_000L, r.balance)
        assertEquals(JalaliDate.toTimestamp(1403, 8, 17) + (13 * 3600L + 45 * 60L) * 1000L, r.date)
    }

    @Test
    fun maskanIsMelliWithoutDirectionMarks() {
        val r = rules.parse("بانک مسکن\n‏واریز:2,000,000+‏\nمانده:5,000,000\n0817-09:10", "بانک مسکن")!!
        assertEquals("deposit", r.transactionType)
        assertEquals(2_000_000L, r.amount)
    }

    @Test
    fun standardChainForMellat() {
        val r = rules.parse("بانک ملت\nخرید 480,000 ریال\nکارت 6104***1234\n1403/08/15 21:05\nمانده 2,000,000", "بانک ملت")!!
        assertEquals("withdrawal", r.transactionType)
        assertEquals(480_000L, r.amount)
        assertEquals(2_000_000L, r.balance)
        assertEquals("1234", r.cardNumber)
        assertEquals(JalaliDate.toTimestamp(1403, 8, 15) + (21 * 3600L + 5 * 60L) * 1000L, r.date)
    }

    @Test
    fun persianDigitsWorkForEveryBank() {
        val r = rules.parse("بانک تجارت\nواریز ۲٬۵۰۰٬۰۰۰ ریال\n۱۴۰۳/۰۸/۱۰ ۱۰:۰۰", "بانک تجارت")!!
        assertEquals("deposit", r.transactionType)
        assertEquals(2_500_000L, r.amount)
    }

    @Test
    fun balanceBeforeAmountIsNotTheAmount() {
        val r = rules.parse("بانک سپه\nمانده: 9,000,000\nبرداشت 150,000\n08/17-11:00", "بانک سپه")!!
        assertEquals(150_000L, r.amount)
        assertEquals(9_000_000L, r.balance)
    }

    @Test
    fun podMessagesGoThroughPasargad() {
        val buy = rules.parse("سرزمین هوشمند پاد\nخرید 150,000 ریال از فروشگاه", "بانک پاسارگاد")!!
        assertEquals("withdrawal", buy.transactionType)
        assertEquals(150_000L, buy.amount)
        val credit = rules.parse("ویپاد\nمبلغ 3,000,000 ریال به کیف پول شما تخصیص داده شد", "ویپاد")!!
        assertEquals("deposit", credit.transactionType)
    }

    @Test
    fun ayandehFallsBackToTheSignedAmount() {
        val r = rules.parse("آینده\nحساب 0201\n+3,400,000\n1403/08/16\n08:30:12", "بانک آینده")!!
        assertEquals("deposit", r.transactionType)
        assertEquals(3_400_000L, r.amount)
    }

    @Test
    fun mehrIranianUsesTheFullDateAndSign() {
        val r = rules.parse("قرض الحسنه مهر ایران\n+2,000,000\n1403/08/11\n12:30", "بانک قرض الحسنه مهر ایران")!!
        assertEquals("deposit", r.transactionType)
        assertEquals(JalaliDate.toTimestamp(1403, 8, 11) + (12 * 3600L + 30 * 60L) * 1000L, r.date)
        assertNull(rules.parse("قرض الحسنه مهر ایران\n+2,000,000\n12:30", "بانک قرض الحسنه مهر ایران"))
    }

    @Test
    fun mellalCompactDate() {
        val r = rules.parse("موسسه ملل\n-500,000\n030812-14:20", "بانک ملل")!!
        assertEquals("withdrawal", r.transactionType)
        assertEquals(JalaliDate.toTimestamp(1403, 8, 12) + (14 * 3600L + 20 * 60L) * 1000L, r.date)
    }

    @Test
    fun toseTavonDates() {
        val full = rules.parse("بانک توسعه تعاون\nبرداشت 300,000\n03/08/09_13:45", "بانک توسعه تعاون")!!
        assertEquals(JalaliDate.toTimestamp(1403, 8, 9) + (13 * 3600L + 45 * 60L) * 1000L, full.date)
        val short = rules.parse("بانک توسعه تعاون\nبرداشت 300,000\n08/09-13:45", "بانک توسعه تعاون")!!
        assertEquals(JalaliDate.toTimestamp(1403, 8, 9) + (13 * 3600L + 45 * 60L) * 1000L, short.date)
    }

    @Test
    fun khavarmianeRebuildsTheDate() {
        val r = rules.parse("بانک خاورمیانه\nواریز 700,000\n08/14\n10:20", "بانک خاورمیانه")!!
        assertEquals("deposit", r.transactionType)
        assertEquals(JalaliDate.toTimestamp(1403, 8, 14) + (10 * 3600L + 20 * 60L) * 1000L, r.date)
    }

    @Test
    fun banksWithoutARuleParserAreSkipped() {
        assertNull(rules.parse("برداشت 300,000", "بانک نامشخص"))
    }

    @Test
    fun bankKeys() {
        assertEquals("melli", BankRuleParser.bankKey("بانک ملی"))
        assertEquals("mellal", BankRuleParser.bankKey("بانک ملل"))
        assertEquals("day", BankRuleParser.bankKey("بانک دی"))
        assertEquals("saderat", BankRuleParser.bankKey("بانک صادرات"))
        assertNull(BankRuleParser.bankKey("بانک توسعه صادرات"))
        assertEquals("karafarin", BankRuleParser.bankKey("بانک کارآفرین"))
        assertEquals("pasargad", BankRuleParser.bankKey("ویپاد"))
        assertEquals("blubank", BankRuleParser.bankKey("بلو بانک"))
        assertEquals("mehriranian", BankRuleParser.bankKey("بانک قرض الحسنه مهر ایران"))
    }

    // ───────────────────────── پیامک غیرتراکنشی ─────────────────────────

    @Test
    fun nonTransactionMessages() {
        listOf(
            "رمز پویا: 482913\nمبلغ: 2,500,000 ریال",
            "به همراه بانک خوش آمدید",
            "یادآوری بازپرداخت قسط 1,000,000 ریال",
            "کد 5521 را وارد کنید",
            "ورود به اینترنت بانک 13:45"
        ).forEach { assertTrue(it, BankRuleParser.isNonTransactionMessage(it)) }
        listOf(
            "کارمزد 5,000 ریال برداشت شد",
            "برداشت 500,000 ریال کد پیگیری 123456"
        ).forEach { assertTrue(it, !BankRuleParser.isNonTransactionMessage(it)) }
        assertTrue(PatternDbParser.isPasswordSms("رمز دوم شما 1234"))
        assertTrue(!PatternDbParser.isPasswordSms("کارمزد 5,000 ریال"))
        assertTrue(PatternDbParser.isPasswordSms("کارمزد 5,000 ریال. مدت زمان اعتبار تراکنش 2 دقیقه"))
    }

    // ───────────────────────── زنجیرهٔ کامل ─────────────────────────

    @Test
    fun compositeFindsTheBankFromTheSenderAlone() {
        val body = "بانك پاسارگاد\nحساب شما\nواریز به: 123.456.789.1\nمبلغ: 2,000,000 ريال\n03/08/17_13:45\nموجودي: 11,000,000 ريال"
        val r = CompositeParser().parse(body, "", "+98500019000")
        assertNotNull(r)
        assertEquals("deposit", r!!.transactionType)
        assertEquals(2_000_000L, r.amount)
        assertEquals(11_000_000L, r.balance)
    }

    @Test
    fun textNormalization() {
        assertEquals("مبلغ 1,250,000 ریال", SmsText.normalize("مبلغ ۱٬۲۵۰٬۰۰۰ ريال"))
        assertEquals("حساب:12345", SmsText.normalize("حساب:‏12345‬"))
    }

    // ───────────────────────── ساخت پیامک نمونه از قالب ─────────────────────────

    private fun sample(t: BankPatternDb.Template): String? {
        val sb = StringBuilder()
        var last = 0
        for (m in Regex("\\[\\[(\\w+)]]").findAll(t.raw)) {
            sb.append(t.raw, last, m.range.first)
            sb.append(
                when (m.groupValues[1]) {
                    "amount" -> "1,250,000"
                    "accountNumber" -> accountSample(t.account.regex)
                    "date" -> pick(t.date.regex, listOf("1403/08/17", "03/08/17", "08/17", "8/17", "0817-", "0817", "030817-", "030817", "03,08,17", "1403.08.17", "1403-")) ?: "1403/08/17"
                    "time" -> pick(t.time.regex, listOf("13:45", "13:45:10")) ?: "13:45"
                    "tType" -> t.typeName
                    else -> ""
                }
            )
            last = m.range.last + 1
        }
        sb.append(t.raw.substring(last))
        return sb.toString()
    }

    private fun pick(regex: String?, candidates: List<String>): String? {
        val r = BankPatternDb.safeRegex(regex) ?: return null
        val p = Pattern.compile(r)
        return candidates.firstOrNull { p.matcher(it).matches() }
    }

    /** نمونه‌ای که regex حساب را کامل می‌پوشاند: «\d{4}» ← ۴ رقم، «\*» ← ستاره، باقی عیناً. */
    private fun accountSample(regex: String?): String {
        val r = BankPatternDb.safeRegex(regex) ?: return "12345678"
        val out = StringBuilder()
        var i = 0
        var digit = 1
        fun digits(n: Int) = repeat(n) { out.append(('0' + (digit++ % 10))) }
        while (i < r.length) {
            val c = r[i]
            if (c == '\\' && i + 1 < r.length) {
                val e = r[i + 1]
                i += 2
                val q = Regex("^\\{(\\d+)(?:,(\\d*))?}").find(r.substring(i))
                val count = q?.groupValues?.get(1)?.toInt() ?: 1
                if (q != null) i += q.value.length
                val plus = r.getOrNull(i) == '+' || r.getOrNull(i) == '*'
                if (plus) i++
                when (e) {
                    'd' -> digits(if (plus) 4 else count)
                    'D' -> repeat(count) { out.append('x') }
                    's' -> out.append(' ')
                    'n' -> out.append(' ')
                    else -> repeat(if (plus) 2 else count) { out.append(e) }
                }
            } else if (c == '[') {
                val end = r.indexOf(']', i)
                out.append(r[i + 1])
                i = end + 1
                if (r.getOrNull(i) == '{') i = r.indexOf('}', i) + 1
            } else if (c == '.' ) {
                out.append('.'); i++
            } else if (c in "+*?") {
                i++
            } else {
                out.append(c); i++
            }
        }
        val s = out.toString()
        return if (Pattern.compile(r).matcher(s).find()) s else "12345678"
    }
}
