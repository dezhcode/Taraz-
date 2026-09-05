package com.example.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.utils.NotificationHelper

/** Delivers a single user- or assistant-created reminder. */
class OneOffReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("fidar_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("alerts_enabled", true)) return Result.success()

        val title = inputData.getString(KEY_TITLE) ?: return Result.success()
        NotificationHelper.showNotification(
            context = applicationContext,
            title = "یادآوری تراز",
            message = title
        )
        return Result.success()
    }

    companion object {
        const val KEY_TITLE = "reminder_title"
    }
}
