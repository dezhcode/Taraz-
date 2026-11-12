package com.example.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity

object NotificationHelper {

    private const val CHANNEL_ID = "taraz_notifications"

    /** شناسهٔ همراه باز شدن برنامه از نوتیفیکیشن (مثلاً تراکنش پیامکی که باید نشان داده شود). */
    const val EXTRA_TARGET_ID = "taraz_target_id"
    private const val CHANNEL_NAME = "اعلان‌های هوشمند تراز"
    private const val CHANNEL_DESC = "یادآوری اقساط و هشدارهای مالی تراز"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /** Persian-digit thousands separators, e.g. ۲٬۵۰۰٬۰۰۰ */
    fun formatAmount(amount: Long): String {
        val grouped = java.text.NumberFormat.getNumberInstance(java.util.Locale("fa", "IR")).format(amount)
        return grouped
    }

    /**
     * [notificationId] lets each loan own a stable slot, so three loans produce
     * three notifications instead of overwriting one another.
     */
    fun showNotification(
        context: Context,
        title: String,
        message: String,
        notificationId: Int? = null,
        action: String? = null,
        buttons: List<Pair<String, PendingIntent>> = emptyList(),
        /** شناسه‌ای که با باز شدن برنامه همراه [action] می‌رسد (مثلاً تراکنش پیامکی)؛ ۰ یعنی هیچ. */
        targetId: Int = 0,
        /** نوتیفیکیشن‌های هم‌گروه (مثل تراکنش‌های پیامکی) زیر یک خلاصه جمع می‌شوند. */
        group: String? = null,
        groupSummary: Boolean = false
    ) {
        // Ensure channel is created
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (action != null) this.action = action
            if (targetId != 0) putExtra(EXTRA_TARGET_ID, targetId)
        }
        // هر نوتیفیکیشن شناسه‌دار PendingIntent خودش را دارد؛ وگرنه همه یکی می‌شدند و آخرین شناسه برنده بود.
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            if (targetId != 0) targetId else 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.example.R.drawable.ic_stat_taraz)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(1000, 1000, 1000))
        buttons.forEach { (label, pi) -> builder.addAction(0, label, pi) }
        if (group != null) builder.setGroup(group).setGroupSummary(groupSummary)

        try {
            with(NotificationManagerCompat.from(context)) {
                // Post notification with a unique ID
                notify(notificationId ?: System.currentTimeMillis().toInt(), builder.build())
            }
        } catch (e: SecurityException) {
            // Handled when user didn't grant POST_NOTIFICATIONS runtime permission yet
            e.printStackTrace()
        }
    }
}
