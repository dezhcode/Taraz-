package com.example.data

import com.example.sms.data.parser.BankSmsDetector
import com.example.sms.data.parser.CompositeParser
import com.example.sms.data.parser.SmsFilter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.Locale

/**
 * The one path every bank SMS takes, whether it arrives in the background
 * ([SmsReceiver]) or is replayed from the app after a format is learned.
 *
 * It only proposes: the SMS becomes a pending transaction and nothing touches
 * a balance until the user approves it. (The receiver used to write the SMS
 * «مانده» into the account straight away, and approving then moved the
 * balance a second time.)
 */
object SmsIngest {

    sealed class Outcome {
        /** Not from an enabled bank sender. */
        object NotBank : Outcome()
        /** Already seen (pending, approved or rejected). */
        object Duplicate : Outcome()
        /** One-time code or a failed attempt: never a transaction, never stored. */
        object Ignored : Outcome()
        data class Pending(val tx: PendingTransaction, val bankName: String) : Outcome()
        /** From a bank, but no format could read it; kept for learning. */
        data class Unknown(val bankName: String) : Outcome()
    }

    private val INCOME_TYPES = setOf("deposit", "salary", "income")

    /**
     * Sender numbers arrive as "+98700717", "98700717" or "700717" depending on
     * the operator; reduce them to one form before comparing.
     */
    fun normalizeAddress(raw: String): String {
        var d = latinDigits(raw).lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
        if (d.startsWith("0098")) d = d.drop(4)
        else if (d.startsWith("98") && d.length > 7) d = d.drop(2)
        return d.trimStart('0').ifEmpty { d }
    }

    fun sameSender(a: String, b: String): Boolean {
        val x = normalizeAddress(a)
        val y = normalizeAddress(b)
        return x.isNotEmpty() && x == y
    }

    suspend fun findSender(dao: BankSenderDao, address: String): BankSender? =
        dao.getAllSenders().firstOrNull { sameSender(it.senderNumber, address) }

    fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(Locale.US, it) }
    }

    fun latinDigits(s: String): String = buildString(s.length) {
        for (ch in s) {
            val p = "۰۱۲۳۴۵۶۷۸۹".indexOf(ch)
            val a = "٠١٢٣٤٥٦٧٨٩".indexOf(ch)
            append(
                when {
                    p >= 0 -> '0' + p
                    a >= 0 -> '0' + a
                    else -> ch
                }
            )
        }
    }

    private fun number(s: String?): Long = latinDigits(s.orEmpty()).filter { it.isDigit() }.toLongOrNull() ?: 0L

    /**
     * مبلغ پیامک به واحد دفتر ([Money]): پیامک بانک ریالی است مگر «تومان» بگوید. در دفتر ریالی همان
     * عدد پیامک بی‌کم‌وکاست ثبت می‌شود، در دفتر تومانی ÷۱۰. دفتر دلاری پیامک نمی‌خواند (۰).
     */
    fun toBookUnit(raw: Long, body: String): Long = Money.fromSms(raw, body) ?: 0L

    private data class Parsed(val amount: Long, val balance: Long, val card: String, val isExpense: Boolean)

    /** What Taraz understands from one SMS (toman), for the preview in the sender setup. */
    data class Preview(val amount: Long, val balance: Long, val card: String, val isExpense: Boolean)

    /**
     * Reads [body] with the built-in readers only (no learned format, no database), so the
     * setup screen can show «تراز این پیامک را این‌طور فهمید» before a sender is saved.
     * Null for one-time codes, failed attempts and anything the readers cannot parse.
     */
    fun preview(body: String, bankName: String, sender: String = ""): Preview? {
        // فقط پیامکی که شکل تراکنش دارد؛ تبلیغ «۳۰۰م وام» نباید «درآمد ۳۰ تومان» نشان داده شود.
        if (SmsFilter.isNonTransaction(body) || !BankSmsDetector.isTransaction(body)) return null
        val p = CompositeParser().parse(body, bankName, sender)?.takeIf { it.amount > 0 } ?: return null
        return Preview(
            amount = toBookUnit(p.amount, body),
            balance = toBookUnit(p.balance, body),
            card = latinDigits(p.cardNumber).filter { it.isDigit() }.takeLast(4),
            isExpense = p.transactionType !in INCOME_TYPES
        )
    }

    /**
     * گیرندهٔ پیامک، بررسی صندوق ورودی هنگام باز شدن برنامه و بررسی پس‌زمینه ممکن است یک پیامک را
     * هم‌زمان ببینند؛ یکی‌یکی پردازش می‌شوند تا بررسی تکراری‌بودن معتبر بماند.
     */
    private val lock = Mutex()

    /** Reads [body] from [address] and files it as pending, unknown, or nothing. */
    suspend fun process(db: AppDatabase, address: String, body: String, receivedAt: Long): Outcome =
        lock.withLock { processLocked(db, address, body, receivedAt) }

    private suspend fun processLocked(db: AppDatabase, address: String, body: String, receivedAt: Long): Outcome {
        val registered = findSender(db.bankSenderDao(), address)
        // فرستندهٔ ثبت‌نشده‌ای که شماره‌اش در دیتابیس بانک‌ها هست هم پیامک بانکی است (نه فرستنده‌ای که کاربر خاموشش کرده).
        val known = if (registered == null) BankSmsDetector.senderBank(address) else null
        if (registered == null && known == null) return Outcome.NotBank
        if (registered != null && !registered.isEnabled) return Outcome.NotBank
        // رمز یک‌بارمصرف، تراکنش ناموفق و پیامک بی‌مبلغ (تبلیغ، اطلاع‌رسانی): نه ثبت می‌شوند، نه در فهرست ناشناخته‌ها می‌مانند.
        if (SmsFilter.isNonTransaction(body) || !BankSmsDetector.hasMoney(body)) return Outcome.Ignored
        val sender = registered ?: BankSender(
            bankName = known!!.displayName,
            senderNumber = address,
            isEnabled = true,
            learningStatus = "آموزش دیده"
        )
        val hash = md5(body)
        if (db.pendingTransactionDao().isDuplicate(hash)) return Outcome.Duplicate

        var parsed: Parsed? = null
        val learned = db.bankSmsParserDao().getLatestParserForSender(sender.senderNumber)
        if (learned != null) {
            val m = SmsParserRepository.parseLocalSms(body, learned)
            val amount = number(m?.get("amount"))
            if (m != null && amount > 0) {
                parsed = Parsed(
                    amount = amount,
                    balance = number(m["balance"]),
                    card = latinDigits(m["card"].orEmpty()).filter { it.isDigit() }.takeLast(4),
                    isExpense = (m["type"] ?: "purchase") !in INCOME_TYPES
                )
            }
        }
        if (parsed == null) {
            val p = CompositeParser().parse(body, sender.bankName, address)
            if (p != null && p.amount > 0) {
                parsed = Parsed(
                    amount = p.amount,
                    balance = p.balance,
                    card = latinDigits(p.cardNumber).filter { it.isDigit() }.takeLast(4),
                    isExpense = p.transactionType !in INCOME_TYPES
                )
            }
        }

        val found = parsed
        // فرستندهٔ تازه‌شناخته فقط با نخستین تراکنش خوانا ثبت می‌شود، نه با پیامک تبلیغاتی.
        if (registered == null) {
            if (found == null) return Outcome.NotBank
            db.bankSenderDao().insertSender(sender)
        }
        if (found == null) {
            val already = db.unknownSmsDao().getUnknownSmsList().any { it.smsBody == body }
            if (!already) {
                db.unknownSmsDao().insertUnknownSms(
                    UnknownSms(senderNumber = sender.senderNumber, smsBody = body, timestamp = receivedAt)
                )
            }
            return Outcome.Unknown(sender.bankName)
        }

        val tx = PendingTransaction(
            senderNumber = sender.senderNumber,
            amount = toBookUnit(found.amount, body),
            // When the SMS arrived, not when it was read: the parsers' own dates were "now".
            date = receivedAt,
            isExpense = found.isExpense,
            card = found.card,
            title = "تراکنش پیامکی ${sender.bankName}",
            smsBody = body,
            smsBodyHash = hash,
            balance = toBookUnit(found.balance, body)
        )
        val id = db.pendingTransactionDao().insertPendingTransaction(tx).toInt()
        return Outcome.Pending(tx.copy(id = id), sender.bankName)
    }

    /**
     * The account an SMS belongs to: the card whose last four digits it names,
     * else the account named after its bank, else that bank's name (a new
     * account is made on approval). Never the bare card digits, which used to
     * turn into an account called «۳۱۴۲».
     */
    fun accountFor(p: PendingTransaction, cards: List<BankCard>, senders: List<BankSender>): String {
        val last4 = BankBin.digitsOf(p.card).takeLast(4)
        if (last4.length == 4) {
            // شمارهٔ کارتی که با ارقام فارسی ذخیره شده هم باید پیدا شود
            cards.firstOrNull { !it.isCash && BankBin.digitsOf(it.cardNumber).endsWith(last4) }?.let { return it.bankName }
        }
        val bank = senders.firstOrNull { sameSender(it.senderNumber, p.senderNumber) }?.bankName?.trim()
            ?.takeIf { it.isNotEmpty() } ?: "حساب بانکی"
        cards.firstOrNull { it.bankName == bank }?.let { return it.bankName }
        val core = bank.removePrefix("بانک").trim()
        if (core.length >= 2) {
            val matches = cards.filter { !it.isCash && (it.bankName.contains(core) || core.contains(it.bankName.removePrefix("بانک").trim())) }
            if (matches.size == 1) return matches.first().bankName
        }
        return bank
    }
}
