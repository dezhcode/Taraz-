package com.example.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.utils.JalaliDate
import com.example.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first

/**
 * Checks every loan once a day and raises a notification for instalments that
 * are due today or in the next few days.
 *
 * This runs through WorkManager rather than a coroutine delay, so it survives
 * the app being closed and — unlike AlarmManager — is automatically restored
 * after a reboot without a BOOT_COMPLETED receiver of our own.
 */
class LoanReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val prefs = applicationContext.getSharedPreferences("fidar_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("alerts_enabled", true)) return Result.success()

            val database = AppDatabase.getDatabase(
                applicationContext,
                CoroutineScope(Dispatchers.IO)
            )
            val loans = database.loanDao().getAllLoans().first()

            for (loan in loans) {
                if (!loan.reminderEnabled || loan.isSettled) continue

                val dueAt = JalaliDate.nextDueTimestamp(loan.dueDay)
                val daysLeft = JalaliDate.daysUntil(dueAt)
                if (daysLeft !in REMIND_ON_DAYS) continue

                // One notification per loan per day, so a reschedule or a retry
                // cannot produce duplicates.
                val dueDate = JalaliDate.fromTimestamp(dueAt)
                val stampKey = "loan_${loan.id}_${dueDate.year}_${dueDate.month}_${daysLeft}"
                if (prefs.getBoolean(stampKey, false)) continue

                val amount = NotificationHelper.formatAmount(loan.installmentAmount)
                val title = when (daysLeft) {
                    0 -> "امروز سررسید قسط ${loan.loanName}"
                    1 -> "فردا سررسید قسط ${loan.loanName}"
                    else -> "$daysLeft روز تا سررسید قسط ${loan.loanName}"
                }
                val message = "$amount تومان — ${loan.bankName} — ${dueDate.formatLong()}"

                NotificationHelper.showNotification(
                    context = applicationContext,
                    title = title,
                    message = message,
                    notificationId = NOTIFICATION_ID_BASE + loan.id
                )
                prefs.edit().putBoolean(stampKey, true).apply()
            }

            Result.success()
        } catch (e: Exception) {
            // A transient database or notification failure should be retried,
            // not silently dropped: a missed instalment is the whole point.
            Result.retry()
        }
    }

    companion object {
        /** Warn three days ahead, the day before, and on the day itself. */
        private val REMIND_ON_DAYS = listOf(0, 1, 3)
        private const val NOTIFICATION_ID_BASE = 8000
    }
}
