package com.example.sms.data.scanner

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.sms.domain.model.BankSenderModel
import com.example.sms.util.BankUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmsScanner(private val context: Context) {

    /**
     * Scans the SMS inbox, identifies and groups messages that appear to be bank transactions,
     * and returns a list of candidate BankSenderModels sorted by message count.
     */
    suspend fun scanInboxForBankSenders(): List<BankSenderModel> = withContext(Dispatchers.IO) {
        val candidateMap = mutableMapOf<String, Int>() // senderNumber -> bankMessageCount
        val senderSampleBody = mutableMapOf<String, String>() // senderNumber -> latestSmsBody
        
        try {
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_SMS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                Log.w("SmsScanner", "READ_SMS permission is not granted. Aborting scan.")
                return@withContext emptyList()
            }

            val inboxUri = Uri.parse("content://sms/inbox")
            val projection = arrayOf("address", "body", "date")
            
            val cursor = context.contentResolver.query(
                inboxUri,
                projection,
                null,
                null,
                "date DESC"
            )

            cursor?.use { c ->
                val addressIdx = c.getColumnIndex("address")
                val bodyIdx = c.getColumnIndex("body")

                while (c.moveToNext()) {
                    if (addressIdx >= 0 && bodyIdx >= 0) {
                        val sender = c.getString(addressIdx)?.trim() ?: continue
                        val body = c.getString(bodyIdx) ?: continue

                        // Check if the body contains banking keywords
                        if (BankUtils.isBankSms(body)) {
                            val currentCount = candidateMap[sender] ?: 0
                            candidateMap[sender] = currentCount + 1
                            
                            // Keep a sample body to help resolve bank names
                            if (!senderSampleBody.containsKey(sender)) {
                                senderSampleBody[sender] = body
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SmsScanner", "Error scanning SMS inbox", e)
        }

        // Convert candidates into domain model objects
        val senders = candidateMap.map { (sender, count) ->
            val sampleBody = senderSampleBody[sender] ?: ""
            val bankName = BankUtils.detectBankName(sender, sampleBody)

            BankSenderModel(
                bankName = bankName,
                senderNumber = sender,
                isEnabled = true,
                messageCount = count,
                lastUpdated = System.currentTimeMillis()
            )
        }

        // Sort by message count descending so the most active banks show first
        senders.sortedByDescending { it.messageCount }
    }
}
