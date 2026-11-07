package com.example.sms.data.repository

import android.content.Context
import com.example.data.AppDatabase
import com.example.data.BankCard
import com.example.data.BankSender
import com.example.sms.data.engine.BankSmsEngineProvider
import com.example.sms.data.scanner.InboxMessage
import com.example.sms.data.scanner.SmsInboxReader
import com.example.sms.engine.BankSmsEngine
import com.example.sms.engine.SmsBank
import com.example.sms.engine.SmsSenders
import com.example.sms.util.BankUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A sender found in the inbox, with what the engine makes of its messages. */
data class InboxSender(
    /** As the inbox shows it; this is what gets stored and matched. */
    val address: String,
    val messageCount: Int,
    val lastBody: String,
    val lastDate: Long,
    /** The chosen bank lists this number among the ones it is known to use. */
    val knownForBank: Boolean,
    /** At least one of its messages talks about money. */
    val looksLikeBank: Boolean,
    /** Of the [sampled] newest messages, how many the chosen bank's rules read as transactions. */
    val recognized: Int,
    val sampled: Int
)

data class PreviewTransaction(val isIncome: Boolean, val amountToman: Long, val date: Long)

data class SenderPreview(
    val sampled: Int,
    val recognized: Int,
    /** Newest first. */
    val transactions: List<PreviewTransaction>
)

data class ActivationResult(
    /** Transactions added for review from the inbox history (0 when no history was asked for). */
    val imported: Int,
    /** Inbox messages from this sender that were looked at for the import. */
    val scanned: Int
)

/**
 * Everything the «turn the SMS reader on for this card» flow does with data:
 * listing the senders in the inbox, trying a bank's rules on a sender's
 * messages before committing, and saving the link between card, bank and sender.
 */
class SmsActivationRepository(
    context: Context,
    private val db: AppDatabase,
    private val smsRepository: SmsRepositoryImpl
) {
    private val appContext = context.applicationContext
    private val reader = SmsInboxReader(appContext)
    private val engine: BankSmsEngine get() = BankSmsEngineProvider.get(appContext)

    private var inbox: List<InboxMessage> = emptyList()

    fun hasPermission(): Boolean = reader.hasPermission()

    /**
     * Reads the inbox and returns its senders, most useful first: numbers the
     * bank is known to use, then senders whose messages the bank's rules can
     * read, then anything else that talks about money.
     */
    suspend fun loadSenders(bank: SmsBank): List<InboxSender> = withContext(Dispatchers.Default) {
        inbox = reader.readRecent()
        val known = engine.database.byName(bank.patternName)

        val groups = LinkedHashMap<String, MutableList<InboxMessage>>()
        for (m in inbox) groups.getOrPut(SmsSenders.canonical(m.address)) { mutableListOf() }.add(m)

        groups.values.map { messages ->
            val newest = messages.first()
            val looksLikeBank = messages.take(12).any { BankUtils.isBankSms(it.body) }
            val knownForBank = known?.usesSender(newest.address) == true
            val sample = if (looksLikeBank || knownForBank) messages.take(SAMPLE_SIZE) else emptyList()
            val recognized = sample.count { engine.parse(it.body, newest.address, bank.patternName) != null }
            InboxSender(
                address = newest.address,
                messageCount = messages.size,
                lastBody = newest.body,
                lastDate = newest.date,
                knownForBank = knownForBank,
                looksLikeBank = looksLikeBank,
                recognized = recognized,
                sampled = sample.size
            )
        }.sortedWith(
            compareByDescending<InboxSender> { it.knownForBank }
                .thenByDescending { it.recognized > 0 }
                .thenByDescending { it.recognized }
                .thenByDescending { it.looksLikeBank }
                .thenByDescending { it.lastDate }
        )
    }

    /** What the bank's rules make of this sender's latest messages. */
    suspend fun preview(address: String, bank: SmsBank): SenderPreview = withContext(Dispatchers.Default) {
        val messages = inbox.filter { SmsSenders.same(it.address, address) }.take(PREVIEW_SAMPLE)
        val found = messages.mapNotNull { m ->
            engine.parse(m.body, address, bank.patternName)?.let {
                PreviewTransaction(it.isIncome, it.amountToman, it.timestamp(m.date))
            }
        }
        SenderPreview(sampled = messages.size, recognized = found.size, transactions = found)
    }

    /** The card whose reader currently listens to [address], other than [card] itself. */
    suspend fun cardLinkedTo(address: String, card: BankCard): String? = withContext(Dispatchers.IO) {
        db.bankSenderDao().getAllSenders()
            .firstOrNull { SmsSenders.same(it.senderNumber, address) && it.cardName.isNotBlank() && it.cardName != card.bankName }
            ?.cardName
    }

    /** Saves the card number typed in the activation flow. The account's name stays its key. */
    suspend fun saveCardNumber(card: BankCard, digits: String) = withContext(Dispatchers.IO) {
        val current = db.bankCardDao().getCardByBankName(card.bankName) ?: return@withContext
        if (current.cardNumber != digits) db.bankCardDao().insertCard(current.copy(cardNumber = digits))
    }

    /**
     * Links [address] to [card] under [bank]'s rules. A number feeds one card and
     * a card has one reader, so earlier links of either are replaced. With
     * [importDays] > 0 the inbox history of that many days is read as well, and
     * what it contains is queued for the user to approve.
     */
    suspend fun activate(card: BankCard, bank: SmsBank, address: String, importDays: Int): ActivationResult =
        withContext(Dispatchers.IO) {
            val senders = db.bankSenderDao()
            senders.getAllSenders()
                .filter { SmsSenders.same(it.senderNumber, address) || it.cardName == card.bankName }
                .forEach {
                    senders.deleteSender(it.id)
                    db.bankSmsParserDao().deleteAllParsersForSender(it.senderNumber)
                }
            senders.insertSender(
                BankSender(
                    bankName = bank.displayName,
                    senderNumber = address,
                    isEnabled = true,
                    learningStatus = "آموزش دیده",
                    parserVersion = 1,
                    engineBank = bank.patternName,
                    cardName = card.bankName
                )
            )

            if (importDays <= 0) return@withContext ActivationResult(0, 0)

            val since = System.currentTimeMillis() - importDays * DAY_MS
            val history = inbox
                .filter { it.date >= since && SmsSenders.same(it.address, address) }
                .sortedBy { it.date }
            var imported = 0
            for (m in history) {
                if (smsRepository.processIncomingSms(address, m.body, m.date) != null) imported++
            }
            ActivationResult(imported = imported, scanned = history.size)
        }

    /** Switches the reader off for a card. Transactions already queued or recorded stay. */
    suspend fun deactivate(cardName: String) = withContext(Dispatchers.IO) {
        db.bankSenderDao().getSendersForCard(cardName).forEach {
            db.bankSenderDao().deleteSender(it.id)
            db.bankSmsParserDao().deleteAllParsersForSender(it.senderNumber)
        }
    }

    private companion object {
        const val SAMPLE_SIZE = 5
        const val PREVIEW_SAMPLE = 20
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
