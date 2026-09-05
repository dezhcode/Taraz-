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
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
            val appContext = context.applicationContext
            val db = AppDatabase.getDatabase(appContext, CoroutineScope(Dispatchers.IO))
            val repository = SmsRepositoryImpl(
                appContext,
                db.bankSenderDao(),
                db.pendingTransactionDao(),
                db.unknownSmsDao()
            )

            CoroutineScope(Dispatchers.IO).launch {
                for (message in messages) {
                    val senderNum = message.originatingAddress ?: continue
                    val body = message.messageBody ?: continue
                    
                    Log.d("SmsReceiver", "Received SMS from $senderNum: $body")
                    
                    val parsed = repository.processIncomingSms(senderNum, body)
                    if (parsed != null) {
                        if (parsed.balance > 0) {
                            db.bankCardDao().updateBalance(parsed.bankName, parsed.balance)
                        }
                        NotificationHelper.showNotification(
                            appContext,
                            "تراکنش جدید استخراج شد",
                            "یک تراکنش بانکی جدید شناسایی و آماده تأیید است."
                        )
                    } else {
                        val senders = db.bankSenderDao().getAllSenders()
                        val isRegisteredSender = senders.any { 
                            senderNum.contains(it.senderNumber) || it.senderNumber.contains(senderNum) 
                        }
                        if (isRegisteredSender) {
                            NotificationHelper.showNotification(
                                appContext,
                                "ساختار پیامک تغییر کرده است",
                                "ساختار جدید پیامک بانکی شناسایی شد."
                            )
                        }
                    }
                }
            }
        }
    }
}
