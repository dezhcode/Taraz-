package com.example.sms.data.parser

import java.util.regex.Pattern

/**
 * دیتابیس الگوی پیامک بانک‌ها (۴۷ بانک، ۱٬۰۷۷ قالب تراکنش، ۳۵۴ شمارهٔ فرستنده).
 *
 * منبع: `resources/sms/bank_patterns.json`، همان ساختار `Name / Numbers / TransactionTypes[]`
 * که هر قالب در آن `Template` (متن پیامک با جای‌نگهدارهای `[[amount]]`، `[[accountNumber]]`،
 * `[[date]]`، `[[time]]`، `[[tType]]`، `[[var]]`)، regex هر فیلد و `Type` (۰ برداشت، ۱ واریز) دارد.
 *
 * فایل یک‌بار و تنبل خوانده می‌شود؛ [warmUp] را می‌شود در شروع برنامه روی رشتهٔ پس‌زمینه صدا زد.
 * نبودن یا خراب بودن فایل هرگز برنامه را نمی‌اندازد: دیتابیس خالی می‌ماند و پارسرهای دیگر کار می‌کنند.
 */
object BankPatternDb {

    private const val RESOURCE = "sms/bank_patterns.json"

    /** یک فیلد قالب: regex خام از دیتابیس. */
    class Field(val regex: String?)

    class Template(
        val bank: String,
        val raw: String,
        /** ۱ واریز، ۰ برداشت. */
        val type: Int,
        val typeName: String,
        val amount: Field,
        val account: Field,
        val date: Field,
        val time: Field
    ) {
        val isDeposit: Boolean get() = type == 1
    }

    class Bank(
        /** نام بانک در دیتابیس، مثل «ملی» یا «قرض الحسنه مهرایران». */
        val name: String,
        /** نامی که در تراز نشان داده می‌شود و با لوگوها می‌خواند، مثل «بانک ملی». */
        val displayName: String,
        val numbers: List<String>,
        val templates: List<Template>
    )

    @Volatile
    private var loaded: List<Bank>? = null
    @Volatile
    private var senderIndex: Map<String, List<Bank>> = emptyMap()

    val banks: List<Bank>
        get() = loaded ?: synchronized(this) {
            loaded ?: load().also {
                senderIndex = buildSenderIndex(it)
                loaded = it
            }
        }

    /** دیتابیس را از پیش می‌خواند تا نخستین پیامک منتظر نماند. */
    fun warmUp() {
        banks
    }

    /** برای آزمون: دیتابیس را از متن JSON می‌سازد. */
    internal fun loadFrom(json: String) = synchronized(this) {
        val list = parse(json)
        senderIndex = buildSenderIndex(list)
        loaded = list
    }

    // ───────────────────────── جست‌وجو ─────────────────────────

    /** بانکِ این شمارهٔ فرستنده، اگر در دیتابیس باشد. */
    fun bankForSender(address: String): Bank? = banksForSender(address).firstOrNull()

    /** همهٔ بانک‌هایی که این شماره را دارند (مثلاً پارسیان و «تاپ» شمارهٔ مشترک دارند). */
    fun banksForSender(address: String): List<Bank> {
        if (address.isBlank()) return emptyList()
        banks
        return senderIndex[senderKey(address)].orEmpty()
    }

    /**
     * بانکِ این نام («بانک ملی»، «ملی»، «بانک قرض الحسنه مهر ایران»…). اول برابری کامل هسته،
     * بعد شمول، و فقط وقتی یک بانک پیدا شود (تا «توسعه» به‌اشتباه یکی از چند بانک نشود).
     */
    fun bankForName(name: String): Bank? {
        val core = nameCore(name)
        if (core.length < 2) return null
        val all = banks
        all.firstOrNull { nameCore(it.name) == core || nameCore(it.displayName) == core }?.let { return it }
        val hits = all.filter {
            val c = nameCore(it.name)
            c.length >= 2 && (c.contains(core) || core.contains(c))
        }
        return hits.singleOrNull()
    }

    fun nameCore(name: String): String =
        SmsText.normalize(name).lowercase()
            .replace("بانک", "").replace("قرض الحسنه", "").replace("قرضالحسنه", "")
            .replace("موسسه اعتباری", "").replace("bank", "")
            .replace("آ", "ا")
            .filter { it.isLetterOrDigit() }

    /** «+98700717»، «98700717»، «0700717» و «700717» یک فرستنده‌اند؛ نام‌ها بدون فاصله و کوچک. */
    fun senderKey(raw: String): String {
        var d = SmsText.normalize(raw).lowercase().filter { it.isLetterOrDigit() }
        if (d.isEmpty()) return d
        if (d.all { it.isDigit() }) {
            if (d.startsWith("0098")) d = d.drop(4)
            else if (d.startsWith("98") && d.length > 7) d = d.drop(2)
            d = d.trimStart('0').ifEmpty { d }
        }
        return d
    }

    private fun buildSenderIndex(list: List<Bank>): Map<String, List<Bank>> {
        val map = HashMap<String, MutableList<Bank>>()
        for (bank in list) for (n in bank.numbers) {
            val key = senderKey(n)
            if (key.length < 2) continue
            val banksOfKey = map.getOrPut(key) { ArrayList(1) }
            if (bank !in banksOfKey) banksOfKey += bank
        }
        return map
    }

    // ───────────────────────── نام نمایشی ─────────────────────────

    private val DISPLAY = mapOf(
        "رسالت" to "بانک قرض الحسنه رسالت",
        "قرض الحسنه مهرایران" to "بانک قرض الحسنه مهر ایران",
        "مهراقتصاد" to "بانک مهر اقتصاد",
        "پست بانک" to "پست بانک",
        "کارافرین" to "بانک کارآفرین",
        "رفاه کارگران" to "بانک رفاه کارگران",
        "حکمت ايرانيان" to "بانک حکمت ایرانیان",
        "کوثر" to "موسسه اعتباری کوثر",
        "موسسه اعتباری توسعه" to "موسسه اعتباری توسعه",
        "بلو بانک" to "بلو بانک",
        "سرزمین هوشمند پاد" to "ویپاد",
        "ایران کارت" to "ایران کارت",
        "تجارت الکترونیک پارسیان(تاپ)" to "تاپ (پارسیان)",
        "تعاوني اعتبار" to "تعاونی اعتبار",
        "فردا بانک" to "فردا بانک",
        "کارآفريني اميد" to "صندوق کارآفرینی امید",
        "بانک مرکزي ج.ا.ا" to "بانک مرکزی",
        "صندوق قرض الحسنه" to "صندوق قرض الحسنه",
        "آرمان" to "موسسه اعتباری آرمان",
        "ثامن" to "موسسه اعتباری ثامن"
    )

    private fun displayName(name: String): String =
        DISPLAY[name] ?: SmsText.normalize("بانک $name").trim()

    // ───────────────────────── خواندن JSON ─────────────────────────

    private fun load(): List<Bank> = try {
        val stream = BankPatternDb::class.java.classLoader?.getResourceAsStream(RESOURCE)
            ?: Thread.currentThread().contextClassLoader?.getResourceAsStream(RESOURCE)
        stream?.use { parse(it.readBytes().toString(Charsets.UTF_8)) } ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    @Suppress("UNCHECKED_CAST")
    private fun parse(json: String): List<Bank> {
        val root = MiniJson(json).value() as? List<Any?> ?: return emptyList()
        return root.mapNotNull { item ->
            val o = item as? Map<String, Any?> ?: return@mapNotNull null
            val name = (o["Name"] as? String)?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            val numbers = (o["Numbers"] as? List<Any?>).orEmpty().mapNotNull { (it as? String)?.trim() }
            val templates = (o["TransactionTypes"] as? List<Any?>).orEmpty().mapNotNull { t ->
                val tt = t as? Map<String, Any?> ?: return@mapNotNull null
                fun field(key: String) = Field(((tt[key] as? Map<String, Any?>)?.get("Regex") as? String))
                Template(
                    bank = name,
                    raw = (tt["Template"] as? String).orEmpty(),
                    type = (tt["Type"] as? Number)?.toInt() ?: 0,
                    typeName = (tt["TypeName"] as? String).orEmpty(),
                    amount = field("Amount"),
                    account = field("AccountNumber"),
                    date = field("Date"),
                    time = field("Time")
                )
            }
            Bank(name, displayName(name), numbers, templates)
        }
    }

    /** regex یک فیلد اگر سالم باشد (چند regex دیتابیس غلط تایپی دارند، مثل «\d4}»)، وگرنه null. */
    fun safeRegex(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val r = raw.replace("\\\\", "\\")
        return try {
            Pattern.compile(r); r
        } catch (e: Exception) {
            null
        }
    }

    /** خوانندهٔ کوچک JSON، تا دیتابیس بدون وابستگی (و در آزمون‌های JVM) خوانده شود. */
    private class MiniJson(private val s: String) {
        private var i = 0

        fun value(): Any? {
            ws()
            if (i >= s.length) return null
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> { i += 4; true }
                'f' -> { i += 5; false }
                'n' -> { i += 4; null }
                else -> if (c == '-' || c.isDigit()) num() else throw IllegalStateException("bad json at $i")
            }
        }

        private fun ws() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++
            ws()
            if (s[i] == '}') { i++; return m }
            while (true) {
                ws()
                val k = str()
                ws(); i++ // :
                m[k] = value()
                ws()
                if (s[i++] == '}') return m
            }
        }

        private fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++
            ws()
            if (s[i] == ']') { i++; return l }
            while (true) {
                l += value()
                ws()
                if (s[i++] == ']') return l
            }
        }

        private fun str(): String {
            val sb = StringBuilder()
            i++ // "
            while (true) {
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        when (val e = s[i++]) {
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000c')
                            'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Number {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            val t = s.substring(start, i)
            return t.toLongOrNull() ?: t.toDouble()
        }
    }
}
