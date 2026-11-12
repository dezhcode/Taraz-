package com.example.reminders

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    private const val DAILY_LOAN_CHECK = "taraz_daily_loan_check"
    private const val REMINDER_HOUR = 10   // a sensible hour to be told about money

    /**
     * Registers the daily instalment check. Safe to call on every app start:
     * KEEP means an already-scheduled check is not restarted, so the schedule
     * does not drift later each time the user opens the app.
     */
    fun scheduleDailyLoanCheck(context: Context) {
        val request = PeriodicWorkRequestBuilder<LoanReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(millisUntilNextRun(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DAILY_LOAN_CHECK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Every Friday at 20:00 (drift of a few minutes is fine for a summary). */
    fun scheduleWeeklySummary(context: Context) {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY)
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.WEEK_OF_YEAR, 1)
        }
        val request = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(next.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("taraz_weekly_summary", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * بررسی پیامک‌های بانکی صندوق ورودی هر ۱۵ دقیقه (کمترین فاصلهٔ WorkManager). KEEP: هر بار باز شدن
     * برنامه زمان‌بندی را از نو شروع نمی‌کند. خود کار وقتی پیامک‌خوان خاموش است هیچ نمی‌خواند.
     */
    fun scheduleSmsCatchUp(context: Context) {
        val request = PeriodicWorkRequestBuilder<SmsCatchUpWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("taraz_sms_catch_up", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelDailyLoanCheck(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DAILY_LOAN_CHECK)
    }

    /** Run the check right now — used after adding or editing a loan. */
    fun runLoanCheckNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<LoanReminderWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "${DAILY_LOAN_CHECK}_now",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /**
     * A one-off reminder (the AI assistant offers these).
     * Replaces the previous viewModelScope.delay approach, which was lost the
     * moment the app was closed.
     */
    fun scheduleOneOffReminder(context: Context, title: String, delaySeconds: Long) {
        val request = OneTimeWorkRequestBuilder<OneOffReminderWorker>()
            .setInitialDelay(delaySeconds.coerceAtLeast(1), TimeUnit.SECONDS)
            .setInputData(Data.Builder().putString(OneOffReminderWorker.KEY_TITLE, title).build())
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    private fun millisUntilNextRun(): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, REMINDER_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now) || equals(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }
}
