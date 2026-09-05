package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BankSenderDao {
    @Query("SELECT * FROM bank_senders ORDER BY lastUpdated DESC")
    fun getAllSendersFlow(): Flow<List<BankSender>>

    @Query("SELECT * FROM bank_senders")
    suspend fun getAllSenders(): List<BankSender>

    @Query("SELECT * FROM bank_senders WHERE senderNumber = :senderNumber LIMIT 1")
    suspend fun getSenderByNumber(senderNumber: String): BankSender?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSender(sender: BankSender)

    @Update
    suspend fun updateSender(sender: BankSender)

    @Query("DELETE FROM bank_senders WHERE id = :id")
    suspend fun deleteSender(id: Int)

    @Query("DELETE FROM bank_senders WHERE senderNumber = :senderNumber")
    suspend fun deleteSenderByNumber(senderNumber: String)
}

@Dao
interface BankSmsParserDao {
    @Query("SELECT * FROM bank_sms_parsers WHERE senderNumber = :senderNumber ORDER BY version DESC")
    fun getParsersBySenderFlow(senderNumber: String): Flow<List<BankSmsParser>>

    @Query("SELECT * FROM bank_sms_parsers WHERE senderNumber = :senderNumber ORDER BY version DESC LIMIT 1")
    suspend fun getLatestParserForSender(senderNumber: String): BankSmsParser?

    @Query("SELECT * FROM bank_sms_parsers WHERE senderNumber = :senderNumber AND version = :version LIMIT 1")
    suspend fun getParserByVersion(senderNumber: String, version: Int): BankSmsParser?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParser(parser: BankSmsParser)

    @Query("DELETE FROM bank_sms_parsers WHERE senderNumber = :senderNumber AND version = :version")
    suspend fun deleteParserVersion(senderNumber: String, version: Int)

    @Query("DELETE FROM bank_sms_parsers WHERE senderNumber = :senderNumber")
    suspend fun deleteAllParsersForSender(senderNumber: String)
}

@Dao
interface PendingTransactionDao {
    @Query("SELECT * FROM pending_transactions WHERE status = 'در انتظار بررسی' ORDER BY date DESC")
    fun getPendingTransactionsFlow(): Flow<List<PendingTransaction>>

    @Query("SELECT * FROM pending_transactions WHERE status = 'در انتظار بررسی' ORDER BY date DESC")
    suspend fun getPendingTransactions(): List<PendingTransaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingTransaction(tx: PendingTransaction)

    @Update
    suspend fun updatePendingTransaction(tx: PendingTransaction)

    @Query("DELETE FROM pending_transactions WHERE id = :id")
    suspend fun deletePendingTransaction(id: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM pending_transactions WHERE smsBodyHash = :hash LIMIT 1)")
    suspend fun isDuplicate(hash: String): Boolean
}

@Dao
interface UnknownSmsDao {
    @Query("SELECT * FROM unknown_sms WHERE isIgnored = 0 ORDER BY timestamp DESC")
    fun getUnknownSmsFlow(): Flow<List<UnknownSms>>

    @Query("SELECT * FROM unknown_sms WHERE isIgnored = 0 ORDER BY timestamp DESC")
    suspend fun getUnknownSmsList(): List<UnknownSms>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnknownSms(sms: UnknownSms)

    @Query("UPDATE unknown_sms SET isIgnored = 1 WHERE id = :id")
    suspend fun ignoreUnknownSms(id: Int)

    @Query("DELETE FROM unknown_sms WHERE id = :id")
    suspend fun deleteUnknownSms(id: Int)
}
