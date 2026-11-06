package com.example.data

import com.example.sms.data.parser.CompositeParser
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

    /** Bank SMS give rials unless they say toman; Taraz keeps toman everywhere. */
    fun toToman(raw: Long, body: String): Long =
        if (body.contains("تومان") && !body.contains("ریال")) raw else raw / 10

    private data class Parsed(val amount: Long, val balance: Long, val card: String, val isExpense: Boolean)

    /** Reads [body] from [address] and files it as pending, unknown, or nothing. */
    suspend fun process(db: AppDatabase, address: String, body: String, receivedAt: Long): Outcome {
        val sender = findSender(db.bankSenderDao(), address)?.takeIf { it.isEnabled } ?: return Outcome.NotBank
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
            val p = CompositeParser().parse(body, sender.bankName)
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
            amount = toToman(found.amount, body),
            // When the SMS arrived, not when it was read: the parsers' own dates were "now".
            date = receivedAt,
            isExpense = found.isExpense,
            card = found.card,
            title = "تراکنش پیامکی ${sender.bankName}",
            smsBody = body,
            smsBodyHash = hash,
            balance = toToman(found.balance, body)
        )
        db.pendingTransactionDao().insertPendingTransaction(tx)
        return Outcome.Pending(tx, sender.bankName)
    }

    /**
     * The account an SMS belongs to: the card whose last four digits it names,
     * else the account named after its bank, else that bank's name (a new
     * account is made on approval). Never the bare card digits, which used to
     * turn into an account called «۳۱۴۲».
     */
    fun accountFor(p: PendingTransaction, cards: List<BankCard>, senders: List<BankSender>): String {
        val last4 = p.card.filter { it.isDigit() }.takeLast(4)
        if (last4.length == 4) {
            cards.firstOrNull { !it.isCash && it.cardNumber.endsWith(last4) }?.let { return it.bankName }
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
