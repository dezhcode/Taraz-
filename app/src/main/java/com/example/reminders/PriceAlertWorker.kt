package com.example.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.PriceAlert
import com.example.data.PriceAlerts
import com.example.data.PriceService
import com.example.data.alertPriceText
import com.example.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Every half hour while an alert is on: reads the prices those alerts watch and notifies the ones
 * that crossed. Each alert fires once and switches itself off. Stops itself when no alert is on.
 */
class PriceAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        com.example.data.Money.load(applicationContext)
        val db = AppDatabase.getDatabase(applicationContext, CoroutineScope(Dispatchers.IO))
        val alerts = db.priceAlertDao().getAllNow().filter { it.enabled }
        if (alerts.isEmpty()) {
            ReminderScheduler.schedulePriceAlerts(applicationContext, false)
        } else {
            val codes = alerts.map { it.code }.toSet() + setOf("usd", "usdt")
            val (quotes, _) = PriceService.refresh(applicationContext, codes, needed = emptySet())
            fire(applicationContext, PriceAlerts.hits(alerts, quotes), quotes["usd"]?.price) { db.priceAlertDao().insert(it) }
        }
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        /** Notifies each hit and switches its alert off (through [save]). */
        suspend fun fire(context: Context, hits: List<PriceAlerts.Hit>, usdToman: Long?, save: suspend (PriceAlert) -> Unit) {
            val now = System.currentTimeMillis()
            for (h in hits) {
                val a = h.alert
                val limit = if (h.up) a.above else a.below
                NotificationHelper.showNotification(
                    context = context,
                    title = "هشدار قیمت: ${a.name}",
                    message = "${a.name} به ${alertPriceText(h.price, a.inUsd, usdToman)} رسید؛ " +
                        (if (h.up) "بالاتر از " else "پایین‌تر از ") + alertPriceText(limit, a.inUsd, usdToman) + ".",
                    notificationId = 8800 + a.id
                )
                save(a.copy(enabled = false, lastFiredAt = now))
            }
        }
    }
}
