package com.example.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.sms.data.repository.SmsRepositoryImpl
import com.example.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives every incoming text and hands the ones from a registered bank sender
 * to the SMS engine.
 *
 * Two details decide whether this works on a real phone:
 *  - A text longer than one segment (Persian text splits at 70 characters) arrives
 *    as several parts in one broadcast. They must be joined before parsing, or a
 *    long bank message is read as two fragments, neither of which is a transaction.
 *  - The work runs after [onReceive] returns, so the broadcast is kept alive with
 *    [goAsync]; without it the process can be killed before the transaction is saved.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (parts.isNullOrEmpty()) return

        val texts = LinkedHashMap<String, StringBuilder>()
        var receivedAt = 0L
        for (part in parts) {
            val address = part.originatingAddress ?: continue
            texts.getOrPut(address) { StringBuilder() }.append(part.messageBody.orEmpty())
            if (receivedAt == 0L) receivedAt = part.timestampMillis
        }
        if (texts.isEmpty()) return
        if (receivedAt <= 0L) receivedAt = System.currentTimeMillis()

        val appContext = context.applicationContext
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val db = AppDatabase.getDatabase(appContext, scope)
                val repository = SmsRepositoryImpl(
                    appContext,
                    db.bankSenderDao(),
                    db.pendingTransactionDao(),
                    db.unknownSmsDao()
                )
                val alertsEnabled = appContext
                    .getSharedPreferences("fidar_prefs", Context.MODE_PRIVATE)
                    .getBoolean("alerts_enabled", true)

                for ((address, body) in texts) {
                    try {
                        val parsed = repository.processIncomingSms(address, body.toString(), receivedAt)
                        if (parsed == null) continue

                        // The legacy parsers also read a balance; the engine does not.
                        if (parsed.balance > 0) {
                            db.bankCardDao().updateBalance(parsed.bankName, parsed.balance)
                        }
                        if (alertsEnabled) {
                            val direction = if (parsed.transactionType == "deposit") "واریز" else "برداشت"
                            NotificationHelper.showNotification(
                                appContext,
                                "تراکنش جدید از ${parsed.bankName}",
                                "$direction ${NotificationHelper.formatAmount(parsed.amount)} تومان شناسایی شد. برای تأیید و ثبت، تراز را باز کنید."
                            )
                        }
                    } catch (e: Exception) {
                        // One bad message must not stop the others in the same broadcast.
                        Log.e("SmsReceiver", "Could not process a message from $address", e)
                    }
                }
            } finally {
                pending?.finish()
            }
        }
    }
}
