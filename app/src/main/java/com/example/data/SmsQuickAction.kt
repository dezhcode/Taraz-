package com.example.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
                    ACTION_DISMISS -> db.pendingTransactionDao().setStatus(id, PendingStatus.REJECTED)
                }
                NotificationManagerCompat.from(appContext).cancel(notificationId(id))
            } catch (e: Exception) {
                android.util.Log.e("SmsQuickAction", "Could not handle SMS action", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_APPROVE = "ir.taraz.finance.SMS_APPROVE"
        const val ACTION_DISMISS = "ir.taraz.finance.SMS_DISMISS"
        private const val EXTRA_ID = "pending_id"

        fun notificationId(pendingId: Int) = 30_000 + pendingId

        private fun intent(c: Context, action: String, id: Int): PendingIntent =
            PendingIntent.getBroadcast(
                c, (if (action == ACTION_APPROVE) 1 else 2) * 1_000_000 + id,
                Intent(c, SmsQuickAction::class.java).setAction(action).putExtra(EXTRA_ID, id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        /** The notification for a new bank SMS, with its two buttons. */
        fun notify(c: Context, tx: PendingTransaction, bankName: String) {
            NotificationHelper.showNotification(
                c,
                "تراکنش تازه از $bankName",
                "${NotificationHelper.formatAmount(tx.amount)} تومان " + (if (tx.isExpense) "برداشت" else "واریز") +
                    " — «ثبت کن» یا برای بررسی بزنید.",
                notificationId = notificationId(tx.id),
                buttons = listOf(
                    "ثبت کن" to intent(c, ACTION_APPROVE, tx.id),
                    "مال من نیست" to intent(c, ACTION_DISMISS, tx.id)
                )
            )
        }

        /** Same result as approving in the review sheet without changing anything. */
        suspend fun approve(db: AppDatabase, id: Int) {
            val dao = db.pendingTransactionDao()
            val p = dao.getById(id) ?: return
            if (p.status != PendingStatus.PENDING) return
            val repo = FinanceRepository(db)
            val name = SmsIngest.accountFor(p, repo.allCardsNow(), db.bankSenderDao().getAllSenders())
            if (repo.getCardByName(name) == null) {
                val delta = if (p.isExpense) -p.amount else p.amount
                repo.insertCard(
                    BankCard(bankName = name, cardNumber = "", balance = if (p.balance > 0) p.balance - delta else 0L, accountType = AccountType.BANK)
                )
            }
            val categories = db.categoryDao().getAllCategories().first()
            val category = categories.firstOrNull { c -> c.isIncome == !p.isExpense && p.title.contains(c.name) }?.name
                ?: if (p.isExpense) "سایر" else "درآمد"
            repo.insertTransaction(
                Transaction(
                    title = p.title.ifBlank { "تراکنش بانکی" }, amount = p.amount, category = category, date = p.date,
                    isExpense = p.isExpense, bankName = name,
                    type = if (p.isExpense) TransactionType.EXPENSE else TransactionType.INCOME
                )
            )
            dao.setStatus(id, PendingStatus.APPROVED)
        }
    }
}
