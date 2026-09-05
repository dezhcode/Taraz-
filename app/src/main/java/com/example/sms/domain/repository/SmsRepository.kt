package com.example.sms.domain.repository

import com.example.sms.domain.model.BankSenderModel
import com.example.sms.domain.model.ParsedTransactionModel
import kotlinx.coroutines.flow.Flow

interface SmsRepository {
    /**
     * Scans the Android SMS inbox, runs keyword detection, deduplicates senders,
     * and returns a list of candidate bank senders with their message count.
     */
    suspend fun scanDeviceSms(): List<BankSenderModel>

    /**
     * Returns a Flow of currently saved bank senders from Room database.
     */
    fun getSelectedSendersFlow(): Flow<List<BankSenderModel>>

    /**
     * Saves a list of bank senders into the database.
     */
    suspend fun saveSelectedSenders(senders: List<BankSenderModel>)

    /**
     * Toggles the enabled state of a registered bank sender.
     */
    suspend fun toggleSender(senderNumber: String, isEnabled: Boolean)

    /**
     * Deletes a bank sender from the monitored list.
     */
    suspend fun deleteSenderByNumber(senderNumber: String)

    /**
     * Parses an incoming SMS message from a registered sender and persists it
     * as a transaction if parsing is successful.
     */
    suspend fun processIncomingSms(sender: String, body: String): ParsedTransactionModel?
    suspend fun setInitialBalanceFromLatestSms(senderNumber: String, bankName: String)
}
