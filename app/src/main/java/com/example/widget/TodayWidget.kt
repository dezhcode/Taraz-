package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.countsAsExpense
import com.example.utils.JalaliDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Actions from the app-icon shortcuts and the widget's buttons. */
object QuickAction {
    const val ADD_EXPENSE = "taraz.dezhcode.app.ADD_EXPENSE"
    const val ADD_INCOME = "taraz.dezhcode.app.ADD_INCOME"
    const val VOICE = "taraz.dezhcode.app.VOICE_ENTRY"
}

/**
 * Home-screen widget: today's spending (against the daily budget, if one is
 * set) and the nearest unpaid instalment. Tapping it opens Taraz.
 */
class TodayWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                render(context.applicationContext, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Redraws every placed widget; call after the data changes. */
        fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, TodayWidget::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch { runCatching { render(app, manager, ids) } }
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val db = AppDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO))
            val now = System.currentTimeMillis()
            val (start, end) = JalaliDate.dayRange(now)
            val today = db.transactionDao().getAllTransactions().first()
                .filter { it.countsAsExpense && it.date >= start && it.date < end }
                .sumOf { it.amount }
            val budget = context.getSharedPreferences("fidar_prefs", Context.MODE_PRIVATE).getLong("daily_budget", 0L)

            val next = db.loanDao().getAllLoans().first()
                .filter { !it.isSettled }
                .map { it to JalaliDate.nextDueTimestamp(it.dueDay, now) }
                .minByOrNull { it.second }

            val views = RemoteViews(context.packageName, R.layout.widget_today)
            views.setTextViewText(R.id.widget_today, "${fa(today)} تومان")
            views.setTextViewText(
                R.id.widget_budget,
                if (budget > 0) {
                    val left = budget - today
                    if (left >= 0) "${fa(left)} تا سقف بودجهٔ امروز" else "${fa(-left)} بیشتر از بودجهٔ امروز"
                } else ""
            )
            views.setTextViewText(
                R.id.widget_loan,
                next?.let { (loan, due) ->
                    val days = JalaliDate.daysUntil(due, now)
                    val whenText = when (days) {
                        0 -> "امروز"
                        1 -> "فردا"
                        else -> "${JalaliDate.toPersianDigits(days)} روز دیگر"
                    }
                    "قسط ${loan.loanName}: ${fa(minOf(loan.installmentAmount, loan.totalAmount - loan.paidAmount))} تومان، $whenText"
                } ?: "قسط پیش رویی وجود ندارد"
            )
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            // دکمه‌های ثبت سریع: مستقیم صفحهٔ ثبت باز می‌شود
            listOf(
                R.id.widget_add_expense to QuickAction.ADD_EXPENSE,
                R.id.widget_add_income to QuickAction.ADD_INCOME,
                R.id.widget_voice to QuickAction.VOICE
            ).filter { it.second != QuickAction.VOICE || com.example.Features.VOICE }.forEachIndexed { i, (viewId, action) ->
                val pi = PendingIntent.getActivity(
                    context, 10 + i,
                    Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(viewId, pi)
            }
            // ثبت با صدا فعلاً خاموش است
            if (!com.example.Features.VOICE) views.setViewVisibility(R.id.widget_voice, android.view.View.GONE)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun fa(v: Long): String =
            JalaliDate.toPersianDigits(String.format(java.util.Locale.ENGLISH, "%,d", v).replace(',', '٬'))
    }
}
