package com.example.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * پیامک‌های بانکی که از آخرین بررسی به صندوق ورودی رسیده‌اند، از همان مسیر [SmsIngest] می‌گذرند.
 *
 * گیرندهٔ پیامک ([SmsReceiver]) فقط وقتی کار می‌کند که اندروید پیامک تازه را به تراز بدهد؛ روی بعضی
 * گوشی‌ها (مثلاً شیائومی بدون «اجرای خودکار» و مجوز «پیامک‌های اعلان») این اعلام هرگز نمی‌رسد. این
 * بررسی هنگام باز شدن برنامه و هر ۱۵ دقیقه در پس‌زمینه ([com.example.reminders.SmsCatchUpWorker])
 * همان پیامک‌ها را از صندوق ورودی برمی‌دارد. پیامک تکراری (که گیرنده قبلاً دیده) دوباره ثبت نمی‌شود.
 */
object SmsCatchUp {

    /**
     * بار اول (برای کسی که پیامک‌خوان را پیش از این نسخه روشن کرده بود) فقط پیامک‌های همین چند روز اخیر،
     * نه کل صندوق ورودی. برای بقیه مرز همان لحظهٔ روشن شدن پیامک‌خوان است ([SmsPrefs.enabledAt]).
     */
    private const val FIRST_RUN_WINDOW_MS = 3L * 24 * 3600 * 1000

    /** کمی هم‌پوشانی با بررسی قبل، برای پیامکی که دیرتر از زمان خودش در صندوق ثبت شده. */
    private const val OVERLAP_MS = 10L * 60 * 1000

    /**
     * پیامک‌های تازه را می‌خواند و تراکنش‌های تازه در انتظار را برمی‌گرداند. اگر [notify] باشد، برای هر
     * کدام نوتیفیکیشن «تراکنش جدید شناسایی شد» می‌آید (بررسی پس‌زمینه)؛ وقتی برنامه باز است شیت کافی است.
     */
    suspend fun run(context: Context, notify: Boolean): List<PendingTransaction> = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (!SmsPrefs.enabled(app)) return@withContext emptyList()
        val granted = ContextCompat.checkSelfPermission(app, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        if (!granted) return@withContext emptyList()

        val now = System.currentTimeMillis()
        val last = SmsPrefs.lastInboxScan(app)
        // هرگز پیش از روشن شدن پیامک‌خوان: آن پیامک‌ها در موجودی‌ای که کاربر وارد کرده هستند.
        val since = maxOf(if (last <= 0L) now - FIRST_RUN_WINDOW_MS else last - OVERLAP_MS, SmsPrefs.enabledAt(app))

        val messages = ArrayList<Triple<String, String, Long>>()
        try {
            app.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("address", "body", "date"),
                "date > ?", arrayOf(since.toString()),
                "date ASC"
            )?.use { c ->
                val a = c.getColumnIndex("address")
                val b = c.getColumnIndex("body")
                val d = c.getColumnIndex("date")
                if (a < 0 || b < 0 || d < 0) return@use
                while (c.moveToNext() && messages.size < 500) {
                    val address = c.getString(a)?.trim().orEmpty()
                    val body = c.getString(b).orEmpty()
                    if (address.isNotEmpty() && body.isNotBlank()) messages += Triple(address, body, c.getLong(d))
                }
            }
        } catch (e: Exception) {
            Log.e("SmsCatchUp", "Could not read the SMS inbox", e)
            return@withContext emptyList()
        }

        val db = AppDatabase.getDatabase(app, CoroutineScope(Dispatchers.IO))
        val found = ArrayList<PendingTransaction>()
        var newest = maxOf(last, 0L)
        // سرشماره‌هایی که پیش از این بررسی ثبت شده‌اند. موجودی حساب هنگام ثبت سرشماره با آخرین پیامک بانک
        // یکی می‌شود؛ پیامک‌های پیش از آن در همان موجودی هستند و ثبت دوباره‌شان موجودی را دو بار تغییر می‌داد.
        val registered = db.bankSenderDao().getAllSenders()
        for ((address, body, date) in messages) {
            if (date > newest) newest = date
            val sender = registered.firstOrNull { SmsIngest.sameSender(it.senderNumber, address) }
            if (sender != null && date < sender.lastUpdated) continue
            try {
                val outcome = SmsIngest.process(db, address, body, if (date > 0) date else now)
                if (outcome is SmsIngest.Outcome.Pending) {
                    found += outcome.tx
                    if (notify) SmsQuickAction.notify(app, outcome.tx, outcome.bankName)
                }
            } catch (e: Exception) {
                Log.e("SmsCatchUp", "Could not process an SMS", e)
            }
        }
        SmsPrefs.setLastInboxScan(app, if (newest > 0) newest else now)
        if (notify && found.isNotEmpty()) SmsQuickAction.notifySummary(app)
        found
    }
}
