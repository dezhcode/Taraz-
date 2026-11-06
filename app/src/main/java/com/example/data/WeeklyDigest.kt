package com.example.data

import com.example.utils.JalaliDate
import java.util.Locale

/**
 * The Friday-evening look back at the week: what was spent against the week
 * before, where it went, and what falls due in the coming seven days.
 * Plain arithmetic on the user's own data; nothing leaves the phone.
 */
object WeeklyDigest {
    private const val DAY = 24L * 60 * 60 * 1000

    data class Upcoming(val title: String, val amount: Long, val at: Long)

    data class Digest(
        val spent: Long,
        val prevSpent: Long,
        val earned: Long,
        val topCategory: String?,
        val topCategoryAmount: Long,
        val biggest: Transaction?,
        val upcoming: List<Upcoming>,
        val count: Int
    ) {
        /** Change against the week before, in whole percent; null when there is nothing to compare. */
        val changePercent: Int?
            get() = if (prevSpent > 0) (((spent - prevSpent) * 100.0) / prevSpent).toInt() else null

        val title: String
            get() = "خلاصهٔ هفته: ${amount(spent)} تومان هزینه"

        /** The lines of the notification and of the in-app card, in order. */
        fun lines(): List<String> = buildList {
            if (count == 0) {
                add("این هفته تراکنشی ثبت نشده است.")
            } else {
                val ch = changePercent
                add(
                    "هزینهٔ این هفته ${amount(spent)} تومان بود" + when {
                        ch == null -> "."
                        ch > 0 -> "؛ ${fa(ch)}٪ بیشتر از هفتهٔ قبل."
                        ch < 0 -> "؛ ${fa(-ch)}٪ کمتر از هفتهٔ قبل."
                        else -> "؛ هم‌اندازهٔ هفتهٔ قبل."
                    }
                )
                if (earned > 0) add("${amount(earned)} تومان درآمد داشتید.")
                topCategory?.let { add("بیشترین هزینه: $it با ${amount(topCategoryAmount)} تومان.") }
                biggest?.let { add("بزرگ‌ترین خرید: ${it.title} (${amount(it.amount)} تومان).") }
            }
            if (upcoming.isNotEmpty()) {
                add(
                    "هفتهٔ آینده: " + upcoming.take(4).joinToString("، ") { "${it.title} ${amount(it.amount)}" } +
                        if (upcoming.size > 4) " و ${fa(upcoming.size - 4)} مورد دیگر" else ""
                )
            }
        }
    }

    fun build(
        transactions: List<Transaction>,
        loans: List<Loan>,
        cheques: List<Cheque>,
        bills: List<Bill>,
        now: Long = System.currentTimeMillis()
    ): Digest {
        val weekStart = now - 7 * DAY
        val prevStart = now - 14 * DAY
        val week = transactions.filter { it.date in weekStart..now }
        val spentList = week.filter { it.countsAsExpense }
        val prevSpent = transactions.filter { it.countsAsExpense && it.date in prevStart until weekStart }.sumOf { it.amount }
        val byCat = spentList.groupBy { it.category.ifBlank { "سایر" } }.mapValues { (_, l) -> l.sumOf { it.amount } }
        val top = byCat.maxByOrNull { it.value }

        val horizon = now + 7 * DAY
        val upcoming = buildList {
            loans.filter { !it.isSettled }.forEach { l ->
                val due = JalaliDate.nextDueTimestamp(l.dueDay, now)
                if (due <= horizon) add(Upcoming("قسط ${l.loanName}", l.installmentAmount, due))
            }
            cheques.filter { it.status == ChequeStatus.PENDING && it.issued && it.dueDate in (now - DAY)..horizon }
                .forEach { add(Upcoming("پرداخت چک به ${it.person}", it.amount, it.dueDate)) }
            bills.filter { it.enabled && it.dueDate <= horizon }
                .forEach { add(Upcoming("قبض ${it.title.ifBlank { it.kind }}", it.amount, it.dueDate)) }
        }.sortedBy { it.at }

        return Digest(
            spent = spentList.sumOf { it.amount },
            prevSpent = prevSpent,
            earned = week.filter { it.countsAsIncome }.sumOf { it.amount },
            topCategory = top?.key,
            topCategoryAmount = top?.value ?: 0,
            biggest = spentList.maxByOrNull { it.amount },
            upcoming = upcoming,
            count = week.size
        )
    }

    private fun fa(v: Any) = JalaliDate.toPersianDigits(v)

    fun amount(v: Long): String = fa(String.format(Locale.ENGLISH, "%,d", v).replace(',', '٬'))
}
