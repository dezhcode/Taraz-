package com.example.sms.data.repository

import android.content.Context
import android.util.Log
import com.example.data.BankSender
import com.example.data.BankSenderDao
import com.example.data.PendingTransaction
import com.example.data.PendingTransactionDao
import com.example.data.UnknownSms
import com.example.data.UnknownSmsDao
import com.example.sms.data.engine.BankSmsEngineProvider
import com.example.sms.data.parser.CompositeParser
import com.example.sms.data.scanner.SmsScanner
import com.example.sms.engine.BankCatalog
import com.example.sms.engine.BankSmsEngine
import com.example.sms.engine.SmsSenders
import com.example.sms.domain.model.BankSenderModel
import com.example.sms.domain.model.ParsedTransactionModel
import com.example.sms.domain.repository.SmsRepository
import com.example.sms.util.BankUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Locale

class SmsRepositoryImpl(
    private val context: Context,
    private val bankSenderDao: BankSenderDao,
    private val pendingTransactionDao: PendingTransactionDao,
    private val unknownSmsDao: UnknownSmsDao
) : SmsRepository {

    private val scanner = SmsScanner(context)
    private val parser = CompositeParser()
    private val engine: BankSmsEngine get() = BankSmsEngineProvider.get(context)

    override suspend fun scanDeviceSms(): List<BankSenderModel> = withContext(Dispatchers.IO) {
        try {
            // 1. Scan real device SMS messages and count transactions
            val scannedSenders = scanner.scanInboxForBankSenders()

            // 2. Fetch already registered bank senders from Room
            val registeredSenders = bankSenderDao.getAllSenders().associateBy { it.senderNumber }

            // 3. Merge lists: if already registered, preserve its database ID and enabled state
            scannedSenders.map { scanned ->
                val registered = registeredSenders[scanned.senderNumber]
                if (registered != null) {
                    scanned.copy(
                        id = registered.id,
                        isEnabled = registered.isEnabled
                    )
                } else {
                    scanned
                }
            }
        } catch (e: Exception) {
            Log.e("SmsRepositoryImpl", "Error during scanDeviceSms execution", e)
            emptyList()
        }
    }

    override fun getSelectedSendersFlow(): Flow<List<BankSenderModel>> {
        return bankSenderDao.getAllSendersFlow().map { list ->
            list.map { mapToDomain(it) }
        }
    }

    override suspend fun saveSelectedSenders(senders: List<BankSenderModel>) = withContext(Dispatchers.IO) {
        // Insert-or-replace, but never wipe what the activation flow stored on an
        // existing row (the bank whose rules read it, and the card it feeds).
        senders.forEach { senderModel ->
            val existing = bankSenderDao.getSenderByNumber(senderModel.senderNumber)
            val entity = existing?.copy(
                isEnabled = senderModel.isEnabled,
                lastUpdated = senderModel.lastUpdated
            ) ?: mapToEntity(senderModel)
            bankSenderDao.insertSender(entity)
        }
    }

    override suspend fun toggleSender(senderNumber: String, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        val existing = bankSenderDao.getSenderByNumber(senderNumber)
        if (existing != null) {
            bankSenderDao.insertSender(existing.copy(isEnabled = isEnabled, lastUpdated = System.currentTimeMillis()))
        }
    }

    override suspend fun deleteSenderByNumber(senderNumber: String) = withContext(Dispatchers.IO) {
        bankSenderDao.deleteSenderByNumber(senderNumber)
    }

    override suspend fun processIncomingSms(
        sender: String,
        body: String,
        receivedAt: Long
    ): ParsedTransactionModel? = withContext(Dispatchers.IO) {
        // 1. Is this a sender the user registered, and is it switched on?
        //    The inbox spells the same number several ways (+98…, 0…, bare), so compare canonically.
        val bankSender = bankSenderDao.getAllSenders()
            .firstOrNull { it.isEnabled && SmsSenders.same(it.senderNumber, sender) }
        if (bankSender == null) {
            Log.i("SmsRepositoryImpl", "Sender $sender is not registered or disabled. Skipping incoming SMS.")
            return@withContext null
        }

        // 2. Duplicate detection. The body alone is not enough: two identical
        //    «کارمزد» texts a day apart are two transactions.
        val hash = md5("${SmsSenders.canonical(sender)}|${receivedAt / 60_000L}|$body")
        if (pendingTransactionDao.isDuplicate(hash)) {
            Log.i("SmsRepositoryImpl", "Duplicate SMS message detected. Skipping.")
            return@withContext null
        }

        val card = bankSender.cardName.ifBlank { bankSender.bankName }

        // 3a. The bank's own message layouts, when the user told us which bank this is.
        val engineBank = bankSender.engineBank.ifBlank { BankCatalog.find(bankSender.bankName)?.patternName.orEmpty() }
        if (engineBank.isNotBlank()) {
            val tx = engine.parse(body, sender, engineBank)
            if (tx != null && tx.amountToman > 0) {
                pendingTransactionDao.insertPendingTransaction(
                    PendingTransaction(
                        senderNumber = bankSender.senderNumber,
                        amount = tx.amountToman,
                        date = tx.timestamp(receivedAt),
                        isExpense = !tx.isIncome,
                        card = card,
                        title = "تراکنش پیامکی ${bankSender.bankName}",
                        smsBody = body,
                        smsBodyHash = hash,
                        status = "در انتظار بررسی"
                    )
                )
                return@withContext ParsedTransactionModel(
                    transactionType = if (tx.isIncome) "deposit" else "withdrawal",
                    amount = tx.amountToman,
                    date = tx.timestamp(receivedAt),
                    cardNumber = "",
                    balance = 0L,
                    bankName = bankSender.bankName,
                    reference = "",
                    merchant = ""
                )
            }
        }

        // 3b. Senders the user set up with the engine are read by the engine only: a
        //     generic parser would turn an advert's "5,000,000" into a transaction.
        //     Only texts that look like bank messages are kept for the user to review.
        if (bankSender.engineBank.isNotBlank()) {
            if (BankUtils.isBankSms(body) && !BankSmsEngine.looksLikeOneTimeCode(body)) {
                unknownSmsDao.insertUnknownSms(
                    UnknownSms(senderNumber = bankSender.senderNumber, smsBody = body, timestamp = receivedAt)
                )
            }
            return@withContext null
        }

        // 3c. Senders from the older scan screen keep the generic parsers.
        val parsed = parser.parse(body, bankSender.bankName)
        if (parsed != null && parsed.amount > 0) {
            val finalAmount = parsed.amount / 10L
            val finalBalance = parsed.balance / 10L
            val finalParsed = parsed.copy(amount = finalAmount, balance = finalBalance)
            val isExpense = finalParsed.transactionType != "deposit"
            val pendingTx = PendingTransaction(
                senderNumber = bankSender.senderNumber,
                amount = finalAmount,
                date = finalParsed.date,
                isExpense = isExpense,
                card = finalParsed.cardNumber,
                title = "تراکنش پیامکی ${bankSender.bankName}",
                smsBody = body,
                smsBodyHash = hash,
                status = "در انتظار بررسی"
            )
            pendingTransactionDao.insertPendingTransaction(pendingTx)
            finalParsed
        } else {
            // If parsing failed, save it in unknown_sms so the user can review/train
            unknownSmsDao.insertUnknownSms(
                UnknownSms(
                    senderNumber = bankSender.senderNumber,
                    smsBody = body,
                    timestamp = System.currentTimeMillis()
                )
            )
            null
        }
    }

    private fun mapToDomain(entity: BankSender): BankSenderModel {
        return BankSenderModel(
            id = entity.id,
            bankName = entity.bankName,
            senderNumber = entity.senderNumber,
            isEnabled = entity.isEnabled,
            messageCount = 0, // database doesn't store count, scanner handles count
            lastUpdated = entity.lastUpdated
        )
    }

    private fun mapToEntity(domain: BankSenderModel): BankSender {
        return BankSender(
            id = domain.id,
            bankName = domain.bankName,
            senderNumber = domain.senderNumber,
            isEnabled = domain.isEnabled,
            learningStatus = "آموزش دیده",
            parserVersion = 1,
            lastUpdated = domain.lastUpdated
        )
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val byteArray = md.digest(input.toByteArray())
        return byteArray.joinToString("") { "%02x".format(Locale.US, it) }
    }
    override suspend fun setInitialBalanceFromLatestSms(senderNumber: String, bankName: String) { withContext(Dispatchers.IO) {
        try {
            val inboxUri = android.net.Uri.parse("content://sms/inbox")
            val projection = arrayOf("body")
            val cursor = context.contentResolver.query(
                inboxUri,
                projection,
                "address = ?",
                arrayOf(senderNumber),
                "date DESC LIMIT 1"
            )
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val bodyIdx = c.getColumnIndex("body")
                    if (bodyIdx >= 0) {
                        val body = c.getString(bodyIdx)
                        val parsed = parser.parse(body, bankName)
                        if (parsed != null && parsed.balance > 0) {
                            val db = com.example.data.AppDatabase.getDatabase(context, kotlinx.coroutines.CoroutineScope(Dispatchers.IO))
                            db.bankCardDao().updateBalance(bankName, parsed.balance / 10L)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SmsRepositoryImpl", "Error setting initial balance from latest SMS", e)
        }
    }
}
}
