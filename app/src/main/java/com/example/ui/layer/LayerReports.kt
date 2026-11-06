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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.example.data.tagList
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
    onOpenYear: () -> Unit = {},
    onShare: (monthTs: Long) -> Unit = {},
    onOpenWeekly: () -> Unit = {}
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

    ScreenTour("reports", Tours.reports)
    Column(
        modifier.fillMaxSize().background(L.Bg)
            .verticalScroll(rememberScrollState()).statusBarsPadding().padding(bottom = NavClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LayerHeader(
            title = "گزارش ${date.monthName}",
            subtitle = rangeText,
            trailing = {
                Row(Modifier.tourTarget("rep.month"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MonthStepper(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "ماه قبل", true) { offset-- }
                    MonthStepper(Icons.AutoMirrored.Filled.KeyboardArrowRight, "ماه بعد", !isCurrentMonth) { offset++ }
                }
            }
        )

        // پس‌انداز
        LayerCard(modifier = Modifier.tourTarget("rep.saved")) {
            Text(if (isCurrentMonth) "پس‌انداز تا امروز" else "پس‌انداز این ماه", style = lt(13.sp, color = L.Muted))
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FitText(
                    (if (cur.saved < 0) "−" else "") + money(kotlin.math.abs(cur.saved)),
                    lt(44.sp, FontWeight.Light, if (cur.saved < 0) L.RedText else L.Ink, letterSpacing = (-1.5).sp),
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text("تومان", style = lt(14.sp, FontWeight.SemiBold), modifier = Modifier.padding(bottom = 8.dp))
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
                    FitText("+${toman(cur.income)}", lt(13.sp, FontWeight.SemiBold, L.Green))
                }
                Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("هزینه", style = lt(13.sp, color = L.Muted))
                    FitText("−${toman(cur.expense)}", lt(13.sp, FontWeight.SemiBold))
                }
            }
        }

        // نمودار شش‌ماهه
        LayerCard(padding = 16.dp) {
            Text(
                if (best != null && best.start == series.last().start && best.saved > 0) "${date.monthName}، بیشترین پس‌انداز در شش ماه اخیر"
                else "پس‌انداز شش ماه اخیر",
                style = lt(14.sp, FontWeight.Bold)
            )
            Text("پس‌انداز هر ماه، به تومان", style = lt(12.sp, color = L.Muted), modifier = Modifier.padding(top = 2.dp))
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
                            contentDescription = "$label: ${toman(s.saved)}"
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        FitText(
                            (if (s.saved < 0) "−" else "") + money(kotlin.math.abs(s.saved)),
                            lt(10.sp, if (isSel) FontWeight.Bold else FontWeight.Normal, if (isSel) L.Ink else L.Muted),
                            minSize = 6.sp
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

        // خلاصهٔ هفته و فرستادن گزارش
        Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(L.Surface)
                    .clickable(onClick = onOpenWeekly).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
            ) { Text("خلاصهٔ هفته", style = lt(14.sp, FontWeight.Bold)) }
            Row(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(L.Surface)
                    .clickable { onShare(monthTs) }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
            ) { Text("ارسال گزارش ماه", style = lt(14.sp, FontWeight.Bold, L.PrimaryText)) }
        }

        // سال من
        Row(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp))
                .background(L.Surface).clickable(onClick = onOpenYear).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("گزارش سالانهٔ ${fa(JalaliDate.today().year)}", style = lt(14.sp, FontWeight.Bold), modifier = Modifier.weight(1f))
            Text("مشاهده", style = lt(13.sp, FontWeight.SemiBold, L.PrimaryText))
        }

        // جریان پول: درآمد ماه کجا رفت
        if (cur.income > 0 || cur.expense > 0) MoneyFlowCard(cur.income, cur.expense, byCategory)

        // برچسب‌ها: جمع هزینهٔ هر برچسب از اول
        val tagTotals = remember(transactions) {
            transactions.filter { it.countsAsExpense && it.tags.isNotBlank() }
                .flatMap { t -> t.tagList.map { it to t } }
                .groupBy({ it.first }, { it.second })
                .map { (tag, list) -> Triple(tag, list.sumOf { it.amount }, list.sortedByDescending { it.date }) }
                .sortedByDescending { it.second }
        }
        if (tagTotals.isNotEmpty()) {
            var openTag by remember { mutableStateOf<String?>(null) }
            LayerCard(padding = 16.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text("برچسب‌ها", style = lt(14.sp, FontWeight.Bold))
                    Text("جمع هزینه از ابتدا", style = lt(12.sp, color = L.Muted))
                }
                tagTotals.take(8).forEach { (tag, total, list) ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(12.dp))
                            .clickable { openTag = if (openTag == tag) null else tag }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("# $tag", style = lt(14.sp, FontWeight.SemiBold, L.PrimaryText), modifier = Modifier.weight(1f))
                        Text("${fa(list.size)} مورد · ${money(total)} تومان", style = lt(13.sp, color = L.Muted))
                    }
                    if (openTag == tag) {
                        list.take(6).forEach { t ->
                            Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp)) {
                                Text("${JalaliDate.fromTimestamp(t.date).formatShort()} · ${t.title}", style = lt(12.sp), maxLines = 1, modifier = Modifier.weight(1f))
                                Text(money(t.amount), style = lt(12.sp, FontWeight.SemiBold, L.RedText))
                            }
                        }
                        if (list.size > 6) Text("برای مشاهدهٔ همه، تراکنش‌ها را با این برچسب فیلتر کنید.", style = lt(11.sp, color = L.Muted), modifier = Modifier.padding(start = 12.dp, top = 4.dp))
                    }
                }
            }
        }

        // دسته‌ها
        LayerCard(padding = 16.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text("هزینه‌ها به تفکیک دسته", style = lt(14.sp, FontWeight.Bold))
                Text("از ${toman(cur.expense)}", style = lt(12.sp, color = L.Muted))
            }
            if (top.isEmpty()) {
                Text("هنوز هزینه‌ای در این ماه ثبت نشده است.", style = lt(13.sp, color = L.Muted), modifier = Modifier.padding(top = 12.dp))
            } else {
                val maxCat = top.first().second.coerceAtLeast(1L)
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    top.forEachIndexed { i, (name, sum) ->
                        val pct = if (cur.expense > 0) (sum * 100 / cur.expense).toInt() else 0
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(name, style = lt(13.sp, if (i == 0) FontWeight.Bold else FontWeight.Normal), modifier = Modifier.weight(1f), maxLines = 1)
                                Text(toman(sum), style = lt(13.sp, FontWeight.Bold))
                                Text(" · ${fa(pct)}٪", style = lt(13.sp, color = L.Muted))
                            }
                            LProgress(sum.toFloat() / maxCat, L.Primary)
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
                        "سایر دسته‌ها: ${toman(rest)} (${fa((rest * 100 / cur.expense.coerceAtLeast(1)).toInt())}٪)",
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
                        Text("هدفی تعیین نشده است", style = lt(14.sp, FontWeight.Bold))
                        Text("یک هدف پس‌انداز تعیین کنید تا پیشرفت آن در این بخش نمایش داده شود.", style = lt(12.sp, color = L.Muted))
                    }
                    SoftButton("هدف جدید", 36.dp, onOpenGoals)
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
                            "${money(goal.currentAmount)} از ${toman(goal.targetAmount)}" +
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

        // پیوند به دستیار (وقتی دستیار روشن است)
        if (com.example.Features.AI) Row(
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

/**
 * جریان پول ماه: درآمد بالا، و زیرش همان مقدار بین دسته‌های خرج و پس‌انداز تقسیم می‌شود.
 * اگر خرج از درآمد بیشتر باشد، قسمت اضافه «از جیب قبلی» نشان داده می‌شود.
 */
@Composable
private fun MoneyFlowCard(income: Long, expense: Long, byCategory: List<Pair<String, Long>>) {
    val palette = listOf(L.Primary, L.Amber, Color(0xFF7A5AA8), Color(0xFF0E9490), L.Chevron)
    val top = byCategory.take(4)
    val rest = byCategory.drop(4).sumOf { it.second }
    val parts = buildList {
        top.forEachIndexed { i, (n, v) -> add(Triple(n, v, palette[i])) }
        if (rest > 0) add(Triple("سایر دسته‌ها", rest, palette[4]))
        if (income > expense) add(Triple("پس‌انداز", income - expense, L.Green))
    }
    val whole = maxOf(income, expense).coerceAtLeast(1)
    LayerCard(padding = 16.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("جریان وجوه", style = lt(14.sp, FontWeight.Bold), modifier = Modifier.weight(1f))
            Text(
                if (income >= expense) "${fa((((income - expense) * 100) / income.coerceAtLeast(1)).toInt())}٪ درآمد پس‌انداز شد"
                else "${toman(expense - income)} بیشتر از درآمد",
                style = lt(12.sp, FontWeight.SemiBold, if (income >= expense) L.GreenText else L.RedText)
            )
        }
        Spacer(Modifier.height(12.dp))
        // درآمد
        Text("درآمد ${toman(income)}", style = lt(12.sp, color = L.Muted))
        Box(
            Modifier.padding(top = 4.dp).fillMaxWidth((income.toFloat() / whole).coerceIn(0.02f, 1f)).height(14.dp)
                .clip(RoundedCornerShape(7.dp)).background(L.Green.copy(alpha = 0.35f))
        )
        Spacer(Modifier.height(10.dp))
        // به کجا رفت
        Text("محل مصرف", style = lt(12.sp, color = L.Muted))
        Row(Modifier.padding(top = 4.dp).fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            parts.forEach { (_, v, c) -> Box(Modifier.weight(v.toFloat().coerceAtLeast(1f)).height(14.dp).background(c)) }
            val used = parts.sumOf { it.second }
            if (used < whole) Spacer(Modifier.weight((whole - used).toFloat()))
        }
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            parts.forEach { (n, v, c) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c))
                    Text(n, style = lt(13.sp), modifier = Modifier.padding(start = 8.dp).weight(1f), maxLines = 1)
                    Text(millions(v), style = lt(13.sp, FontWeight.SemiBold))
                    Text(
                        "${fa(((v * 100) / whole).toInt())}٪", style = lt(12.sp, color = L.Muted),
                        modifier = Modifier.padding(start = 8.dp).width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
            }
        }
    }
}
