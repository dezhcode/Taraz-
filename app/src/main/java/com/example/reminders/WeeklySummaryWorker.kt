package com.example.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.WeeklyDigest
import com.example.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first

/** Friday evening: a short look back at the week and what is due next week. */
class WeeklySummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val prefs = applicationContext.getSharedPreferences("fidar_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("alerts_enabled", true) && prefs.getBoolean(KEY_ENABLED, true)) {
            val db = AppDatabase.getDatabase(applicationContext, CoroutineScope(Dispatchers.IO))
            val digest = WeeklyDigest.build(
                db.transactionDao().getAllTransactions().first(),
                db.loanDao().getAllLoans().first(),
                db.chequeDao().getAllNow(),
                db.billDao().getAllNow()
            )
            // Nothing recorded and nothing coming up: stay quiet.
            if (digest.count > 0 || digest.upcoming.isNotEmpty()) {
                val week = java.util.Calendar.getInstance().get(java.util.Calendar.WEEK_OF_YEAR)
                val key = "weekly_sent_${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)}_$week"
                if (!prefs.getBoolean(key, false)) {
                    NotificationHelper.showNotification(
                        context = applicationContext,
                        title = digest.title,
                        message = digest.lines().joinToString("\n"),
                        notificationId = 7700,
                        action = ACTION_OPEN
                    )
                    prefs.edit().putBoolean(key, true).apply()
                }
            }
        }
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        const val KEY_ENABLED = "weekly_summary_enabled"
        const val ACTION_OPEN = "taraz.dezhcode.app.WEEKLY_SUMMARY"
    }
}
