package com.example.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.attachmentList
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
            val database = AppDatabase.getDatabase(
                applicationContext,
                CoroutineScope(Dispatchers.IO)
            )
            // Monthly recurring items are recorded here too, so they arrive even
            // if the app is not opened on their day.
            runCatching { com.example.data.FinanceRepository(database).runDueRecurring() }
            // Weekly or monthly encrypted backup into the folder the user chose.
            runCatching { com.example.data.AutoBackup.runIfDue(applicationContext) }
            // Photos of deleted transactions, after a few days' grace.
            runCatching {
                val used = database.transactionDao().getAllTransactions().first().flatMap { it.attachmentList }.toSet()
                com.example.data.Attachments.cleanOrphans(applicationContext, used)
            }

            if (!prefs.getBoolean("alerts_enabled", true)) return Result.success()
            val loans = database.loanDao().getAllLoans().first()

            for (loan in loans) {
                if (!loan.reminderEnabled || loan.isSettled) continue

                val payments = database.loanPaymentDao().forLoan(loan.id)
                val paidKeys = payments.map { it.monthKey }.toSet()

                // Overdue: this month's day has passed and it is still unpaid. Only for
                // loans paid through Taraz, whose history tells us what is paid.
                val today = JalaliDate.today()
                val thisKey = today.monthKey()
                val thisDue = JalaliDate.dueTimestampFor(thisKey, loan.dueDay)
                val lateDays = -JalaliDate.daysUntil(thisDue)
                if (payments.isNotEmpty() && thisKey !in paidKeys && lateDays in OVERDUE_ON_DAYS) {
                    val lateKey = "loan_${loan.id}_${thisKey}_late_$lateDays"
                    if (!prefs.getBoolean(lateKey, false)) {
                        NotificationHelper.showNotification(
                            context = applicationContext,
                            title = "قسط معوق ${loan.loanName}",
                            message = "${NotificationHelper.formatAmount(loan.installmentAmount)} تومان — سررسید ${JalaliDate.fromTimestamp(thisDue).formatLong()} گذشته است.",
                            notificationId = NOTIFICATION_ID_BASE + loan.id
                        )
                        prefs.edit().putBoolean(lateKey, true).apply()
                    }
                }

                val dueAt = JalaliDate.nextDueTimestamp(loan.dueDay)
                val daysLeft = JalaliDate.daysUntil(dueAt)
                if (daysLeft !in REMIND_ON_DAYS) continue
                // Already paid for that month (on time or ahead): nothing to remind.
                if (JalaliDate.fromTimestamp(dueAt).monthKey() in paidKeys) continue

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

            remindPersonDues(database, prefs)
            runCatching { remindCheques(database, prefs) }
            runCatching { remindBills(database, prefs) }
            runCatching { remindCreditCards(database, prefs) }

            Result.success()
        } catch (e: Exception) {
            // A transient database or notification failure should be retried,
            // not silently dropped: a missed instalment is the whole point.
            Result.retry()
        }
    }

    /** روز موعد (و یک روز قبلش) قرض‌هایی که با اشخاص هنوز صاف نشده‌اند */
    private suspend fun remindPersonDues(database: AppDatabase, prefs: android.content.SharedPreferences) {
        val all = database.debtDao().getAll().first()
        val balances = all.filter { it.personId != null }.groupBy { it.personId!! }.mapValues { (_, l) -> l.sumOf { it.signedAmount } }
        for (d in all) {
            val due = d.dueDate ?: continue
            if (d.settled || d.isRepayment) continue
            val bal = d.personId?.let { balances[it] } ?: 0L
            // حساب صاف شده، یا جهتش عوض شده: یادآوری لازم نیست
            if (bal == 0L || (bal > 0) != d.owedToMe) continue
            val days = JalaliDate.daysUntil(due)
            if (days !in listOf(0, 1)) continue
            val key = "debt_${d.id}_due_$days"
            if (prefs.getBoolean(key, false)) continue
            val amount = NotificationHelper.formatAmount(kotlin.math.abs(bal))
            val whenText = if (days == 0) "امروز" else "فردا"
            NotificationHelper.showNotification(
                context = applicationContext,
                title = if (d.owedToMe) "$whenText موعد دریافت از ${d.person}" else "$whenText موعد پرداخت به ${d.person}",
                message = if (d.owedToMe) "مبلغ $amount تومان از ${d.person} بستانکار هستید." else "مبلغ $amount تومان به ${d.person} بدهکار هستید.",
                notificationId = 9000 + d.id
            )
            prefs.edit().putBoolean(key, true).apply()
        }
    }

    private fun notifyOnce(prefs: android.content.SharedPreferences, key: String, title: String, message: String, id: Int) {
        if (prefs.getBoolean(key, false)) return
        NotificationHelper.showNotification(context = applicationContext, title = title, message = message, notificationId = id)
        prefs.edit().putBoolean(key, true).apply()
    }

    private fun whenText(days: Int) = when (days) { 0 -> "امروز"; 1 -> "فردا"; else -> "$days روز دیگر" }

    /** سه روز و یک روز قبل و روز سررسید چک‌های در انتظار */
    private suspend fun remindCheques(database: AppDatabase, prefs: android.content.SharedPreferences) {
        for (c in database.chequeDao().pending()) {
            val days = JalaliDate.daysUntil(c.dueDate)
            if (days !in REMIND_ON_DAYS) continue
            val amount = NotificationHelper.formatAmount(c.amount)
            notifyOnce(
                prefs, "cheque_${c.id}_${c.dueDate}_$days",
                if (c.issued) "${whenText(days)} سررسید پرداخت چک به ${c.person}" else "${whenText(days)} سررسید دریافت چک از ${c.person}",
                if (c.issued) "موجودی حساب ${c.bankName} باید دست‌کم $amount تومان باشد." else "مبلغ $amount تومان؛ چک را برای وصول به بانک ارائه کنید.",
                10_000 + c.id
            )
        }
    }

    /** سه روز قبل و روز موعد هر قبض */
    private suspend fun remindBills(database: AppDatabase, prefs: android.content.SharedPreferences) {
        for (b in database.billDao().getAllNow()) {
            if (!b.enabled) continue
            val days = JalaliDate.daysUntil(b.dueDate)
            if (days !in listOf(0, 3)) continue
            notifyOnce(
                prefs, "bill_${b.id}_${b.dueDate}_$days",
                "${whenText(days)} موعد قبض ${b.title.ifBlank { b.kind }}",
                (if (b.amount > 0) "${NotificationHelper.formatAmount(b.amount)} تومان" else "مبلغ در قبض درج شده است") +
                    if (b.billId.isNotBlank()) " · شناسه ${b.billId}" else "",
                11_000 + b.id
            )
        }
    }

    /** سه روز و یک روز قبل از روز بازپرداخت کارت اعتباری، اگر بدهی دارد */
    private suspend fun remindCreditCards(database: AppDatabase, prefs: android.content.SharedPreferences) {
        for (c in database.bankCardDao().getAll()) {
            if (c.accountType != com.example.data.AccountType.CREDIT || c.dueDay <= 0 || c.balance >= 0) continue
            val due = JalaliDate.nextDueTimestamp(c.dueDay)
            val days = JalaliDate.daysUntil(due)
            if (days !in REMIND_ON_DAYS) continue
            val d = JalaliDate.fromTimestamp(due)
            notifyOnce(
                prefs, "credit_${c.id}_${d.monthKey()}_$days",
                "${whenText(days)} بازپرداخت ${c.bankName}",
                "${NotificationHelper.formatAmount(-c.balance)} تومان از اعتبار استفاده شده است.",
                12_000 + c.id
            )
        }
    }

    companion object {
        /** Warn three days ahead, the day before, and on the day itself. */
        private val REMIND_ON_DAYS = listOf(0, 1, 3)
        /** Days after a missed due day to say it is late. */
        private val OVERDUE_ON_DAYS = listOf(1, 3)
        private const val NOTIFICATION_ID_BASE = 8000
    }
}
