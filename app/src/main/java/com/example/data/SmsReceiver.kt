package com.example.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Background entry for bank SMS. Hands every message to [SmsIngest], which only
 * files it for review — balances change when the user approves, not here.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return

        // A long SMS arrives in several parts; join them per sender before reading.
        val messages = parts.filterNotNull()
            .groupBy { it.originatingAddress.orEmpty() }
            .filterKeys { it.isNotBlank() }
            .map { (address, list) ->
                Triple(address, list.joinToString("") { it.messageBody.orEmpty() }, list.first().timestampMillis)
            }

        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(appContext, CoroutineScope(Dispatchers.IO))
                for ((address, body, sentAt) in messages) {
                    val receivedAt = if (sentAt > 0) sentAt else System.currentTimeMillis()
                    when (val outcome = SmsIngest.process(db, address, body, receivedAt)) {
                        is SmsIngest.Outcome.Pending -> SmsQuickAction.notify(appContext, outcome.tx, outcome.bankName)
                        is SmsIngest.Outcome.Unknown -> NotificationHelper.showNotification(
                            appContext,
                            "پیامک تازه از ${outcome.bankName} خوانده نشد",
                            "قالب این پیامک را یک بار در تراز آموزش دهید تا بعد خودکار ثبت شود."
                        )
                        else -> Unit
                    }
                }
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Could not process incoming SMS", e)
            } finally {
                pending.finish()
            }
        }
    }
}
