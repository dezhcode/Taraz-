package com.example.ui.layer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FinancialGoal
import com.example.data.Transaction
import com.example.data.countsAsExpense
import com.example.data.countsAsIncome
import com.example.utils.JalaliDate
import java.util.Locale

private data class MonthStat(val start: Long, val income: Long, val expense: Long) {
    val saved: Long get() = income - expense
}

private fun monthStat(transactions: List<Transaction>, anyDayInMonth: Long): MonthStat {
    val (start, end) = JalaliDate.monthRange(anyDayInMonth)
    val inMonth = transactions.filter { it.date in start until end }
    return MonthStat(
        start = start,
        income = inMonth.filter { it.countsAsIncome }.sumOf { it.amount },
        expense = inMonth.filter { it.countsAsExpense }.sumOf { it.amount }
    )
}

private fun oneDecimal(millions: Double): String =
    fa(String.format(Locale.ENGLISH, "%.1f", millions).replace('.', '٫'))

/**
 * گزارش ماهانه — طرح نهایی «لایه»: پس‌انداز تا امروز، نمودار شش‌ماهه،
 * هزینه‌ها به تفکیک دسته، پیشرفت هدف و پیوند به دستیار.
 */
@Composable
fun LayerReportsScreen(
    transactions: List<Transaction>,
    goal: FinancialGoal?,
    onOpenGoals: () -> Unit,
    onAskAssistant: (prompt: String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenYear: () -> Unit = {}
) {
    var offset by remember { mutableIntStateOf(0) } // ۰ = ماه جاری، منفی = ماه‌های قبل
    val now = System.currentTimeMillis()
    val monthTs = shiftJalaliMonth(now, offset)
    val date = JalaliDate.fromTimestamp(monthTs)
    val cur = remember(transactions, monthTs) { monthStat(transactions, monthTs) }
    val isCurrentMonth = offset == 0

    // شش ماه منتهی به ماه انتخاب‌شده
    val series = remember(transactions, monthTs) {
        (5 downTo 0).map { monthStat(transactions, shiftJalaliMonth(monthTs, -it)) }
    }
    val maxSaved = series.maxOf { it.saved }.coerceAtLeast(1L)
    val best = series.maxByOrNull { it.saved }

    val (mStart, mEnd) = JalaliDate.monthRange(monthTs)
    val byCategory = remember(transactions, monthTs) {
        transactions.filter { it.countsAsExpense && it.date in mStart until mEnd }
            .groupBy { it.category.ifBlank { "سایر" } }
            .mapValues { it.value.sumOf { t -> t.amount } }
            .toList().sortedByDescending { it.second }
    }
    // همان دسته‌ها در ماه قبل، برای «نسبت به ماه قبل»
    val prevTs = shiftJalaliMonth(monthTs, -1)
    val prev = remember(transactions, prevTs) { monthStat(transactions, prevTs) }
    val prevByCategory = remember(transactions, prevTs) {
        val (ps, pe) = JalaliDate.monthRange(prevTs)
        transactions.filter { it.countsAsExpense && it.date in ps until pe }
            .groupBy { it.category.ifBlank { "سایر" } }
            .mapValues { it.value.sumOf { t -> t.amount } }
    }
    val top = byCategory.take(4)
    val rest = byCategory.drop(4).sumOf { it.second }

    val rangeText = if (isCurrentMonth) "۱ تا ${fa(date.let { JalaliDate.today().day })} ${date.monthName} ${fa(date.year)}"
    else "${date.formatMonthYear()}، تمام ماه"

    Column(
        modifier.fillMaxSize().background(L.Bg)
            .verticalScroll(rememberScrollState()).statusBarsPadding().padding(bottom = NavClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LayerHeader(
            title = "گزارش ${date.monthName}",
            subtitle = rangeText,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MonthStepper(Icons.AutoMirrored.Filled.KeyboardArrowRight, "ماه قبل", true) { offset-- }
                    MonthStepper(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "ماه بعد", !isCurrentMonth) { offset++ }
                }
            }
        )

        // پس‌انداز
        LayerCard {
            Text(if (isCurrentMonth) "پس‌انداز تا امروز" else "پس‌انداز این ماه", style = lt(13.sp, color = L.Muted))
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    (if (cur.saved < 0) "−" else "") + oneDecimal(kotlin.math.abs(cur.saved) / 1_000_000.0),
                    style = lt(52.sp, FontWeight.Light, if (cur.saved < 0) L.RedText else L.Ink, letterSpacing = (-2).sp)
                )
                Text("میلیون تومان", style = lt(14.sp, FontWeight.SemiBold), modifier = Modifier.padding(bottom = 8.dp))
                if (cur.income > 0 && cur.saved > 0) {
                    val pct = (cur.saved * 100 / cur.income).toInt()
                    Box(
                        Modifier.padding(bottom = 8.dp).height(26.dp).clip(PillShape).background(L.GreenSoft).padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("${fa(pct)}٪ درآمد", style = lt(12.sp, FontWeight.Bold, L.GreenText)) }
                }
            }
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(1.dp).background(L.Line))
            Row(Modifier.padding(top = 10.dp).fillMaxWidth()) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("دریافتی", style = lt(13.sp, color = L.Muted))
                    Text("+${oneDecimal(cur.income / 1_000_000.0)} میلیون", style = lt(13.sp, FontWeight.SemiBold, L.Green))
                }
                Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("هزینه", style = lt(13.sp, color = L.Muted))
                    Text("−${oneDecimal(cur.expense / 1_000_000.0)} میلیون", style = lt(13.sp, FontWeight.SemiBold))
                }
            }
        }

        // نمودار شش‌ماهه
        LayerCard(padding = 16.dp) {
            Text(
                if (best != null && best.start == series.last().start && best.saved > 0) "${date.monthName}، پرپس‌اندازترین ماه شش ماه اخیر"
                else "پس‌انداز شش ماه اخیر",
                style = lt(14.sp, FontWeight.Bold)
            )
            Text("پس‌انداز هر ماه، میلیون تومان", style = lt(12.sp, color = L.Muted), modifier = Modifier.padding(top = 2.dp))
            Row(
                Modifier.padding(top = 14.dp).fillMaxWidth().height(150.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // آخرین ماه سمت راست (RTL: اولین عنصر راست‌ترین است) ← ترتیب را برعکس می‌گذاریم
                series.reversed().forEachIndexed { i, s ->
                    val isSel = i == 0
                    val h = (kotlin.math.max(s.saved, 0L).toFloat() / maxSaved * 100f).coerceAtLeast(4f)
                    val label = JalaliDate.fromTimestamp(s.start).monthName
                    Column(
                        Modifier.weight(1f).height(150.dp).semantics {
                            contentDescription = "$label: ${oneDecimal(s.saved / 1_000_000.0)} میلیون"
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            oneDecimal(s.saved / 1_000_000.0),
                            style = lt(11.sp, if (isSel) FontWeight.Bold else FontWeight.Normal, if (isSel) L.Ink else L.Muted)
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier.width(20.dp).height(h.dp)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(if (isSel) L.Primary else Color6)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(label, style = lt(11.sp, if (isSel) FontWeight.Bold else FontWeight.Normal, if (isSel) L.Ink else L.Muted), maxLines = 1)
                    }
                }
            }
        }

        // سال من
        Row(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp))
                .background(L.Surface).clickable(onClick = onOpenYear).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("سال من · مرور ${fa(JalaliDate.today().year)}", style = lt(14.sp, FontWeight.Bold), modifier = Modifier.weight(1f))
            Text("ببینید", style = lt(13.sp, FontWeight.SemiBold, L.PrimaryText))
        }

        // دسته‌ها
        LayerCard(padding = 16.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text("هزینه‌ها کجا رفت؟", style = lt(14.sp, FontWeight.Bold))
                Text("از ${oneDecimal(cur.expense / 1_000_000.0)} میلیون", style = lt(12.sp, color = L.Muted))
            }
            if (top.isEmpty()) {
                Text("هنوز هزینه‌ای در این ماه ثبت نشده.", style = lt(13.sp, color = L.Muted), modifier = Modifier.padding(top = 12.dp))
            } else {
                val maxCat = top.first().second.coerceAtLeast(1L)
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    top.forEachIndexed { i, (name, sum) ->
                        val pct = if (cur.expense > 0) (sum * 100 / cur.expense).toInt() else 0
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(name, style = lt(13.sp, if (i == 0) FontWeight.Bold else FontWeight.Normal), modifier = Modifier.width(66.dp), maxLines = 1)
                            LProgress(sum.toFloat() / maxCat, L.Primary, modifier = Modifier.weight(1f))
                            Row(Modifier.width(76.dp), horizontalArrangement = Arrangement.End) {
                                Text(millions(sum), style = lt(13.sp, FontWeight.Bold))
                                Text(" ${fa(pct)}٪", style = lt(13.sp, color = L.Muted))
                            }
                        }
                        val before = prevByCategory[name] ?: 0L
                        if (before > 0) {
                            val change = ((sum - before) * 100 / before).toInt()
                            if (change != 0) Text(
                                if (change > 0) "${fa(change)}٪ بیشتر از ${JalaliDate.fromTimestamp(prevTs).monthName}"
                                else "${fa(-change)}٪ کمتر از ${JalaliDate.fromTimestamp(prevTs).monthName}",
                                style = lt(11.sp, color = if (change > 0) L.RedText else L.GreenText),
                                modifier = Modifier.padding(start = 76.dp)
                            )
                        }
                    }
                }
                if (rest > 0) {
                    Text(
                        "سایر دسته‌ها: ${oneDecimal(rest / 1_000_000.0)} میلیون (${fa((rest * 100 / cur.expense.coerceAtLeast(1)).toInt())}٪)",
                        style = lt(12.sp, color = L.Muted), modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }

        // هدف
        LayerCard(padding = 16.dp) {
            if (goal == null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("هنوز هدفی نگذاشته‌اید", style = lt(14.sp, FontWeight.Bold))
                        Text("یک هدف پس‌انداز بگذارید تا پیشرفتش را اینجا ببینید.", style = lt(12.sp, color = L.Muted))
                    }
                    SoftButton("هدف تازه", 36.dp, onOpenGoals)
                }
            } else {
                val frac = if (goal.targetAmount > 0) goal.currentAmount.toFloat() / goal.targetAmount else 0f
                val perMonth = series.map { it.saved }.filter { it > 0 }.average().takeIf { !it.isNaN() } ?: 0.0
                val remain = (goal.targetAmount - goal.currentAmount).coerceAtLeast(0)
                val monthsLeft = if (perMonth > 0) kotlin.math.ceil(remain / perMonth).toInt() else null
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("هدف: ${goal.title}", style = lt(14.sp, FontWeight.Bold))
                        Text(
                            "${oneDecimal(goal.currentAmount / 1_000_000.0)} از ${oneDecimal(goal.targetAmount / 1_000_000.0)} میلیون" +
                                (if (monthsLeft != null && remain > 0) " · با این روند حدود ${fa(monthsLeft)} ماه دیگر" else ""),
                            style = lt(12.sp, color = L.Muted)
                        )
                    }
                    SoftButton("ویرایش هدف", 36.dp, onOpenGoals)
                }
                LProgress(frac, L.Green, modifier = Modifier.padding(top = 10.dp).semantics {
                    contentDescription = "${fa((frac * 100).toInt())} درصد هدف پس‌انداز شده"
                })
            }
        }

        // پیوند به دستیار
        Row(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(48.dp)
                .clip(RoundedCornerShape(14.dp)).background(L.PrimarySoft).clickable {
                    // خلاصهٔ واقعی همین ماه برای دستیار؛ پاسخ در چت ذخیره می‌شود
                    val cats = byCategory.take(6).joinToString("، ") { (n, v) ->
                        val b = prevByCategory[n] ?: 0L
                        "$n ${v} تومان" + if (b > 0) " (ماه قبل $b)" else ""
                    }
                    onAskAssistant(
                        "گزارش ${date.formatMonthYear()} من را کوتاه و کاربردی تحلیل کن.\n" +
                            "دریافتی: ${cur.income} تومان، هزینه: ${cur.expense} تومان، پس‌انداز: ${cur.saved} تومان.\n" +
                            "ماه قبل: دریافتی ${prev.income}، هزینه ${prev.expense}.\n" +
                            "هزینه به تفکیک دسته: ${cats.ifBlank { "ثبت نشده" }}.\n" +
                            "بگو کجا بیشتر از معمول خرج کرده‌ام و برای ماه بعد سه پیشنهاد مشخص بده."
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.AutoAwesome, null, tint = L.PrimaryText, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("تحلیل ${date.monthName} با دستیار", style = lt(14.sp, FontWeight.Bold, L.PrimaryText))
        }
    }
}

private val Color6 get() = L.BarSoft

@Composable
private fun MonthStepper(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
            .background(if (enabled) L.PrimarySoft else L.Chip)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (enabled) L.PrimaryText else L.Placeholder, modifier = Modifier.size(22.dp))
    }
}
