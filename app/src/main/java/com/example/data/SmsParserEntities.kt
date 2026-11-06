package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bank_senders")
data class BankSender(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bankName: String,
    val senderNumber: String, // e.g., "100060", "Melli"
    val isEnabled: Boolean = true,
    val learningStatus: String = "نیاز به آموزش", // "نیاز به آموزش", "آموزش دیده", "در حال آموزش"
    val parserVersion: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "bank_sms_parsers", primaryKeys = ["senderNumber", "version"])
data class BankSmsParser(
    val senderNumber: String,
    val bankName: String,
    val version: Int,
    val templatesJson: String, // JSON containing templates with regex and fields mapping
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val checksum: String = ""
)

@Entity(tableName = "pending_transactions")
data class PendingTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val senderNumber: String,
    val amount: Long,
    val date: Long,
    val isExpense: Boolean,
    val card: String,
    val title: String,
    val smsBody: String,
    val smsBodyHash: String,
    val status: String = PendingStatus.PENDING,
    /** «مانده» read from the SMS, in toman; 0 when the SMS has none. */
    val balance: Long = 0
)

/**
 * Approved and rejected SMS stay in the table with their status, so the same
 * message (same hash) is never offered twice — deleting them used to let a
 * rescan bring an already-recorded SMS back.
 */
object PendingStatus {
    const val PENDING = "در انتظار بررسی"
    const val APPROVED = "تایید شده"
    const val REJECTED = "رد شده"
}

@Entity(tableName = "unknown_sms")
data class UnknownSms(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val senderNumber: String,
    val smsBody: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isIgnored: Boolean = false
)
