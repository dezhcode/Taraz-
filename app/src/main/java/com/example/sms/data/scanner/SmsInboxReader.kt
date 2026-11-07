package com.example.sms.data.scanner

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One received text as stored in the phone's inbox. */
data class InboxMessage(
    /** The sender exactly as the inbox shows it: `+9830009417`, `Bank Melli`, … */
    val address: String,
    val body: String,
    /** Epoch millis the phone received it. */
    val date: Long
)

/**
 * Reads the SMS inbox, newest first.
 *
 * The cap keeps the read bounded on phones with years of messages; bank senders
 * that matter are the ones that wrote recently. The limit is applied while
 * walking the cursor — `LIMIT` inside the sort order is rejected by the SMS
 * provider on newer Android versions.
 */
class SmsInboxReader(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun readRecent(limit: Int = DEFAULT_LIMIT): List<InboxMessage> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val out = ArrayList<InboxMessage>()
        try {
            context.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("address", "body", "date"),
                null,
                null,
                "date DESC"
            )?.use { c ->
                val addressIdx = c.getColumnIndex("address")
                val bodyIdx = c.getColumnIndex("body")
                val dateIdx = c.getColumnIndex("date")
                if (addressIdx < 0 || bodyIdx < 0 || dateIdx < 0) return@use
                while (c.moveToNext() && out.size < limit) {
                    val address = c.getString(addressIdx)?.trim().orEmpty()
                    val body = c.getString(bodyIdx).orEmpty()
                    if (address.isEmpty() || body.isEmpty()) continue
                    out.add(InboxMessage(address, body, c.getLong(dateIdx)))
                }
            }
        } catch (e: Exception) {
            Log.e("SmsInboxReader", "Could not read the SMS inbox", e)
        }
        out
    }

    companion object {
        const val DEFAULT_LIMIT = 4000
    }
}
