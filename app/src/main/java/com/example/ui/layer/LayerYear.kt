package com.example.ui.layer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Debt
import com.example.data.LoanPayment
import com.example.data.Transaction
import com.example.data.countsAsExpense
import com.example.data.countsAsIncome
import com.example.utils.JalaliDate

private data class YearStats(
    val year: Int,
    val income: Long,
    val expense: Long,
    val monthly: List<Pair<Long, Long>>, // (income, expense) for each month 1..12
    val topCats: List<Pair<String, Long>>,
    val biggest: Transaction?,
    val count: Int,
    val busiestWeekday: String?,
    val loanPaid: Long,
    val lent: Long,
    val gotBack: Long
)

private fun yearStats(year: Int, txs: List<Transaction>, payments: List<LoanPayment>, debts: List<Debt>): YearStats {
    val start = JalaliDate.toTimestamp(year, 1, 1)
    val end = JalaliDate.toTimestamp(year + 1, 1, 1)
    val inYear = txs.filter { it.date in start until end }
    val exp = inYear.filter { it.countsAsExpense }
    val inc = inYear.filter { it.countsAsIncome }
    val monthly = (1..12).map { m ->
        val s = JalaliDate.toTimestamp(year, m, 1)
        val e = if (m == 12) end else JalaliDate.toTimestamp(year, m + 1, 1)
        inc.filter { it.date in s until e }.sumOf { it.amount } to exp.filter { it.date in s until e }.sumOf { it.amount }
    }
    val weekday = exp.groupBy { weekdayName(it.date) }.mapValues { (_, l) -> l.sumOf { it.amount } }.maxByOrNull { it.value }?.key
    return YearStats(
        year = year,
        income = inc.sumOf { it.amount },
        expense = exp.sumOf { it.amount },
        monthly = monthly,
        topCats = exp.groupBy { it.category.ifBlank { "سایر" } }.mapValues { (_, l) -> l.sumOf { it.amount } }
            .toList().sortedByDescending { it.second }.take(5),
        biggest = exp.maxByOrNull { it.amount },
        count = inYear.size,
        busiestWeekday = weekday,
        loanPaid = payments.filter { it.date in start until end }.sumOf { it.amount },
        lent = debts.filter { it.createdAt in start until end && it.owedToMe && !it.isRepayment }.sumOf { it.amount },
        gotBack = debts.filter { it.createdAt in start until end && !it.owedToMe && it.isRepayment }.sumOf { it.amount }
    )
}

/** «سال من»: مرور یک سال شمسی با عدد و نمودار */
@Composable
fun LayerYearScreen(
    transactions: List<Transaction>,
    payments: List<LoanPayment>,
    debts: List<Debt>,
    onBack: () -> Unit
) {
    val thisYear = JalaliDate.today().year
    var year by remember { mutableIntStateOf(thisYear) }
    val firstYear = transactions.minOfOrNull { JalaliDate.fromTimestamp(it.date).year } ?: thisYear
    val st = remember(year, transactions, payments, debts) { yearStats(year, transactions, payments, debts) }
    val saved = st.income - st.expense

    LayerScreen(
        title = "سال من",
        subtitle = "مرور سال ${fa(year)}" + if (year == thisYear) " تا امروز" else "",
        onBack = onBack
    ) {
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (firstYear..thisYear).toList().takeLast(4).forEach { y -> LChip(fa(y), y == year) { year = y } }
        }

        // جلد
        Column(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(L.Primary, Color(0xFF1B2E8F))))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("عملکرد سال ${fa(year)}", style = lt(14.sp, FontWeight.SemiBold, Color(0xD9FFFFFF)))
            Text(
                if (saved >= 0) "${millions(saved)} پس‌انداز" else "هزینه ${millions(-saved)} ${com.example.data.Money.label} بیش از درآمد بود",
                style = lt(30.sp, FontWeight.Bold, Color.White)
            )
            Text(
                "${millions(st.income)} درآمد · ${millions(st.expense)} هزینه · ${fa(st.count)} تراکنش",
                style = lt(13.sp, color = Color(0xD9FFFFFF))
            )
            if (st.income > 0 && saved > 0) {
                Text("${fa((saved * 100 / st.income).toInt())}٪ از درآمد پس‌انداز شد.", style = lt(13.sp, FontWeight.SemiBold, Color.White))
            }
        }

        if (st.count == 0) {
            Text("برای این سال تراکنشی ثبت نشده است.", style = lt(13.sp, color = L.Muted), modifier = Modifier.padding(horizontal = 24.dp))
            return@LayerScreen
        }

        // نمودار ماه‌ها
        LayerCard(padding = 16.dp) {
            Text("هزینهٔ هر ماه", style = lt(14.sp, FontWeight.Bold))
            val maxV = st.monthly.maxOf { maxOf(it.first, it.second) }.coerceAtLeast(1)
            val bestIdx = st.monthly.indices.maxByOrNull { st.monthly[it].first - st.monthly[it].second }
            Row(Modifier.padding(top = 12.dp).fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                st.monthly.forEachIndexed { i, (inc, exp) ->
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                            Box(Modifier.width(5.dp).fillMaxHeight((inc.toFloat() / maxV).coerceIn(0.02f, 1f)).clip(RoundedCornerShape(2.dp)).background(L.Green))
                            Box(Modifier.width(5.dp).fillMaxHeight((exp.toFloat() / maxV).coerceIn(0.02f, 1f)).clip(RoundedCornerShape(2.dp)).background(if (i == bestIdx) L.Primary else L.BarSoft))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(JalaliDate.MONTH_NAMES[i].take(3), style = lt(9.sp, color = L.Muted), textAlign = TextAlign.Center, maxLines = 1)
                    }
                }
            }
            if (bestIdx != null) {
                val best = st.monthly[bestIdx]
                Text(
                    "بهترین ماه: ${JalaliDate.MONTH_NAMES[bestIdx]} با ${millions(best.first - best.second)} پس‌انداز",
                    style = lt(12.sp, color = L.Muted), modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // دسته‌ها
        if (st.topCats.isNotEmpty()) {
            LayerCard(padding = 16.dp) {
                Text("بیشترین هزینه‌ها", style = lt(14.sp, FontWeight.Bold))
                val top = st.topCats.first().second.coerceAtLeast(1)
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    st.topCats.forEach { (name, sum) ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(name, style = lt(13.sp), modifier = Modifier.weight(1f), maxLines = 1)
                                Text(toman(sum), style = lt(13.sp, FontWeight.Bold))
                            }
                            LProgress(sum.toFloat() / top, L.Primary)
                        }
                    }
                }
            }
        }

        // نکته‌ها
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            st.biggest?.let { b -> Fact("بزرگ‌ترین هزینه", "${b.title} · ${money(b.amount)} ${com.example.data.Money.label}", JalaliDate.fromTimestamp(b.date).formatLong()) }
            st.busiestWeekday?.let { Fact("پرهزینه‌ترین روز هفته", it, "روزی با بیشترین هزینه") }
            val yearStart = JalaliDate.toTimestamp(year, 1, 1)
            val days = (
                if (year == thisYear) JalaliDate.daysUntil(System.currentTimeMillis(), yearStart) + 1
                else JalaliDate.daysUntil(JalaliDate.toTimestamp(year + 1, 1, 1), yearStart) // ۳۶۵ یا ۳۶۶ در سال کبیسه
                ).coerceAtLeast(1)
            Fact("میانگین هزینهٔ روزانه", "${money(st.expense / days)} ${com.example.data.Money.label}", "در ${fa(days)} روز")
            if (st.loanPaid > 0) Fact("قسط پرداخت‌شده", "${money(st.loanPaid)} ${com.example.data.Money.label}", "از وام‌های بانکی")
            if (st.lent > 0 || st.gotBack > 0) Fact("حساب اشخاص", "پرداخت ${money(st.lent)} ${com.example.data.Money.label}", "دریافت ${money(st.gotBack)} ${com.example.data.Money.label}")
        }
    }
}

@Composable
private fun Fact(label: String, value: String, sub: String) {
    Column(Modifier.fillMaxWidth().cardSurface(18.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = lt(12.sp, color = L.Muted))
        Text(value, style = lt(15.sp, FontWeight.Bold))
        Text(sub, style = lt(11.sp, color = L.Muted))
    }
}
