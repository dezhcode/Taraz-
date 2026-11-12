package com.example.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * «ثبت کن» / «مال من نیست» on a bank SMS notification, without opening the app.
 * Approving does what the review sheet does with its suggestions untouched: the
 * account the SMS names, a category guessed from the title, the SMS's own date.
 */
class SmsQuickAction : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        if (id < 0) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(appContext, CoroutineScope(Dispatchers.IO))
                when (intent.action) {
                    ACTION_APPROVE -> approve(db, id)
                    ACTION_DISMISS -> SmsApproval.reject(db, id)
                }
                NotificationManagerCompat.from(appContext).cancel(notificationId(id))
                refreshSummary(appContext, db)
            } catch (e: Exception) {
                android.util.Log.e("SmsQuickAction", "Could not handle SMS action", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_APPROVE = "taraz.dezhcode.app.SMS_APPROVE"
        const val ACTION_DISMISS = "taraz.dezhcode.app.SMS_DISMISS"
        /** باز کردن تراز روی شیت تراکنش‌های تازه (با شناسهٔ تراکنش در [NotificationHelper.EXTRA_TARGET_ID]). */
        const val ACTION_REVIEW = "taraz.dezhcode.app.SMS_REVIEW"
        private const val EXTRA_ID = "pending_id"
        private const val GROUP = "taraz_sms_pending"
        private const val SUMMARY_ID = 29_999

        fun notificationId(pendingId: Int) = 30_000 + pendingId

        private fun intent(c: Context, action: String, id: Int): PendingIntent =
            PendingIntent.getBroadcast(
                c, (if (action == ACTION_APPROVE) 1 else 2) * 1_000_000 + id,
                Intent(c, SmsQuickAction::class.java).setAction(action).putExtra(EXTRA_ID, id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        /**
         * «تراکنش جدید شناسایی شد»: مبلغ، جهت، بانک و کارت. زدن روی خودش تراز را روی شیت همین تراکنش
         * باز می‌کند؛ «ثبت سریع» بدون باز کردن برنامه ثبتش می‌کند.
         */
        fun notify(c: Context, tx: PendingTransaction, bankName: String) {
            val card = tx.card.filter { it.isDigit() }.takeLast(4)
            val line = "${NotificationHelper.formatAmount(tx.amount)} تومان " + (if (tx.isExpense) "برداشت" else "واریز") +
                " · " + bankName + (if (card.length == 4) " · کارت " + com.example.utils.JalaliDate.toPersianDigits(card) else "")
            NotificationHelper.showNotification(
                c,
                "تراکنش جدید شناسایی شد",
                "$line\nبرای ثبت در تراز بزنید.",
                notificationId = notificationId(tx.id),
                action = ACTION_REVIEW,
                targetId = tx.id,
                group = GROUP,
                buttons = listOf(
                    "ثبت سریع" to intent(c, ACTION_APPROVE, tx.id),
                    "مال من نیست" to intent(c, ACTION_DISMISS, tx.id)
                )
            )
        }

        /** خلاصهٔ گروه وقتی دو تراکنش یا بیشتر منتظرند: «۳ تراکنش جدید برای ثبت». */
        suspend fun notifySummary(c: Context) {
            refreshSummary(c, AppDatabase.getDatabase(c, CoroutineScope(Dispatchers.IO)))
        }

        private suspend fun refreshSummary(c: Context, db: AppDatabase) {
            val count = db.pendingTransactionDao().getPendingTransactions().size
            if (count < 2) {
                NotificationManagerCompat.from(c).cancel(SUMMARY_ID)
                return
            }
            NotificationHelper.showNotification(
                c,
                "${com.example.utils.JalaliDate.toPersianDigits(count)} تراکنش جدید برای ثبت",
                "برای دیدن و ثبت در تراز بزنید.",
                notificationId = SUMMARY_ID,
                action = ACTION_REVIEW,
                group = GROUP,
                groupSummary = true
            )
        }

        /** وقتی تراکنشی در خود برنامه ثبت یا رد شد، نوتیفیکیشنش هم برود. */
        fun cancel(c: Context, pendingIds: Collection<Int>) {
            val nm = NotificationManagerCompat.from(c)
            pendingIds.forEach { nm.cancel(notificationId(it)) }
            nm.cancel(SUMMARY_ID)
        }

        /** Same result as approving in the sheet without changing anything; see [SmsApproval]. */
        suspend fun approve(db: AppDatabase, id: Int) {
            SmsApproval.approve(db, id)
        }
    }
}
