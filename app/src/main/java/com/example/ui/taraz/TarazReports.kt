package com.example.ui.taraz

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.FinancialGoal
import com.example.data.countsAsExpense
import com.example.data.countsAsIncome
import com.example.ui.FinanceViewModel
import com.example.utils.JalaliDate

private data class MonthSaving(val label: String, val saving: Long)

@Composable
fun TarazReportsScreen(
    vm: FinanceViewModel,
    onBack: () -> Unit,
    onAskAssistant: () -> Unit
) {
    val transactions by vm.transactions.collectAsStateWithLifecycle()
    val income by vm.monthlyIncome.collectAsStateWithLifecycle()
    val expense by vm.monthlyExpense.collectAsStateWithLifecycle()
    val goal by vm.activeGoal.collectAsStateWithLifecycle()
    var editGoal by remember { mutableStateOf(false) }

    val today = JalaliDate.today()
    val saving = income - expense

    // Last six Jalali months, oldest first.
    val months = (5 downTo 0).map { back ->
        var y = today.year
        var m = today.month - back
        while (m < 1) { m += 12; y -= 1 }
        val key = y * 100 + m
        val inMonth = transactions.filter { JalaliDate.fromTimestamp(it.date).monthKey() == key }
        MonthSaving(
            JalaliDate.MONTH_NAMES[m - 1],
            inMonth.filter { it.countsAsIncome }.sumOf { it.amount } - inMonth.filter { it.countsAsExpense }.sumOf { it.amount }
        )
    }
    val currentIsBest = months.isNotEmpty() && months.last().saving > 0 && months.last().saving >= months.maxOf { it.saving }

    val monthKey = today.monthKey()
    val byCategory = transactions
        .filter { it.countsAsExpense && JalaliDate.fromTimestamp(it.date).monthKey() == monthKey }
        .groupBy { it.category }
        .mapValues { e -> e.value.sumOf { it.amount } }
        .entries.sortedByDescending { it.value }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(Tz.Ground)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            TzTopBar("گزارش ${today.monthName}", onBack = onBack, subtitle = "۱ تا ${TzFormat.fa(today.day)} ${today.monthName} ${TzFormat.fa(today.year)}")
        }
        item {
            TzCard(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp), padding = PaddingValues(18.dp)) {
                Text(if (saving >= 0) "پس‌انداز تا امروز" else "بیشتر از درآمد خرج شده", style = tzText(13.sp, color = Tz.Muted))
                Row(verticalAlignment = Alignment.Bottom) {
                    val parts = TzFormat.compact(saving).split(" ")
                    Text(parts.first(), style = tzText(54.sp, FontWeight.ExtraLight, if (saving < 0) Tz.Amber else Tz.Ink, letterSpacing = (-2).sp, lineHeight = 56.sp))
                    Spacer(Modifier.width(8.dp))
                    Text((parts.getOrNull(1) ?: "") + " تومان", style = tzText(14.sp, FontWeight.SemiBold), modifier = Modifier.padding(bottom = 8.dp))
                    Spacer(Modifier.width(8.dp))
                    if (income > 0 && saving > 0) {
                        TzTag("${TzFormat.fa(saving * 100 / income)}٪ درآمد", Tz.GreenTint, Tz.GreenText, Modifier.padding(bottom = 8.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Hairline()
                Spacer(Modifier.height(10.dp))
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("دریافتی", style = tzText(13.sp, color = Tz.Muted))
                        Text("+" + TzFormat.compact(income), style = tzText(14.sp, FontWeight.SemiBold, Tz.Green))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("هزینه", style = tzText(13.sp, color = Tz.Muted))
                        Text("−" + TzFormat.compact(expense), style = tzText(14.sp, FontWeight.SemiBold))
                    }
                }
            }
        }
        item {
            TzCard(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
                Text(
                    if (currentIsBest) "${today.monthName}، پرپس‌اندازترین ماه شش ماه اخیر" else "پس‌انداز شش ماه اخیر",
                    style = tzText(14.sp, FontWeight.Bold)
                )
                Text("دریافتی منهای هزینهٔ هر ماه", style = tzText(12.sp, color = Tz.Muted))
                Spacer(Modifier.height(12.dp))
                SavingsBars(months)
            }
        }
        item {
            TzCard(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("هزینه‌ها کجا رفت؟", style = tzText(14.sp, FontWeight.Bold), modifier = Modifier.weight(1f))
                    Text("از ${TzFormat.compact(expense)}", style = tzText(12.sp, color = Tz.Muted))
                }
                Spacer(Modifier.height(10.dp))
                if (byCategory.isEmpty()) {
                    Text("این ماه هنوز هزینه‌ای ثبت نشده.", style = tzText(13.sp, color = Tz.Muted))
                }
                val max = byCategory.firstOrNull()?.value?.coerceAtLeast(1L) ?: 1L
                byCategory.take(4).forEachIndexed { i, (name, value) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = tzText(13.sp, if (i == 0) FontWeight.Bold else FontWeight.Normal), modifier = Modifier.width(72.dp), maxLines = 1)
                        Box(Modifier.weight(1f)) {
                            TzProgress(value.toFloat() / max, height = 8.dp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            TzFormat.short(value) + "  " + TzFormat.fa(if (expense > 0) value * 100 / expense else 0) + "٪",
                            style = tzText(12.sp, FontWeight.SemiBold, align = TextAlign.End),
                            modifier = Modifier.width(80.dp)
                        )
                    }
                }
                if (byCategory.size > 4) {
                    val rest = byCategory.drop(4).sumOf { it.value }
                    Text("${TzFormat.fa(byCategory.size - 4)} دستهٔ دیگر: ${TzFormat.compact(rest)}", style = tzText(12.sp, color = Tz.Muted))
                }
            }
        }
        item {
            GoalCard(goal, onEdit = { editGoal = true })
        }
        item {
            SecondaryButton(
                "از دستیار بپرسید: این ماه کجا صرفه‌جویی کنم؟",
                onClick = onAskAssistant,
                icon = Icons.Filled.AutoAwesome,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
                fontSize = 13.sp
            )
        }
    }

    if (editGoal) {
        GoalDialog(
            goal = goal,
            onDismiss = { editGoal = false },
            onSave = { title, target, saved ->
                vm.insertGoal(title, target, saved)
                editGoal = false
            },
            onDelete = goal?.let { g -> { vm.deleteGoal(g.id); editGoal = false } }
        )
    }
}

@Composable
private fun SavingsBars(months: List<MonthSaving>) {
    val maxAbs = months.maxOf { kotlin.math.abs(it.saving) }.coerceAtLeast(1L)
    val description = months.joinToString("، ") { "${it.label} ${TzFormat.compact(it.saving)}" }
    Column(Modifier.semantics { contentDescription = "پس‌انداز ماهانه: $description" }) {
        Row(Modifier.fillMaxWidth().height(18.dp)) {
            months.forEachIndexed { i, m ->
                Text(
                    if (i == months.lastIndex || m.saving < 0) TzFormat.short(m.saving).let { if (m.saving < 0) "−$it" else it } else "",
                    style = tzText(11.sp, FontWeight.Bold, if (m.saving < 0) Tz.Amber else Tz.Ink, align = TextAlign.Center),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val slot = size.width / months.size
            val barW = 20.dp.toPx().coerceAtMost(slot * 0.6f)
            val base = size.height - 2.dp.toPx()
            drawLine(Tz.Border, Offset(0f, base), Offset(size.width, base), strokeWidth = 1.dp.toPx())
            // Months run right to left, like the text around them.
            months.forEachIndexed { i, m ->
                val cx = size.width - slot * (i + 0.5f)
                val h = (base - 6.dp.toPx()) * (kotlin.math.abs(m.saving).toFloat() / maxAbs)
                val color = when {
                    m.saving < 0 -> Tz.AmberDot
                    i == months.lastIndex -> Tz.Cobalt
                    else -> Tz.BarGrey
                }
                drawBar(cx - barW / 2, base - h.coerceAtLeast(2.dp.toPx()), barW, h.coerceAtLeast(2.dp.toPx()), color)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            months.forEachIndexed { i, m ->
                val current = i == months.lastIndex
                Text(
                    m.label,
                    style = tzText(11.sp, if (current) FontWeight.Bold else FontWeight.Normal, if (current) Tz.Ink else Tz.Muted, align = TextAlign.Center),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun DrawScope.drawBar(x: Float, y: Float, w: Float, h: Float, color: androidx.compose.ui.graphics.Color) {
    val r = 4.dp.toPx().coerceAtMost(h / 2)
    drawRoundRect(color, topLeft = Offset(x, y), size = Size(w, h), cornerRadius = CornerRadius(r, r))
    if (h > r) drawRect(color, topLeft = Offset(x, y + h - r), size = Size(w, r))
}

@Composable
private fun GoalCard(goal: FinancialGoal?, onEdit: () -> Unit) {
    TzCard(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
        if (goal == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconWell(Icons.Filled.Flag, Tz.CobaltTint, Tz.Cobalt)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("هدف پس‌انداز ندارید", style = tzText(14.sp, FontWeight.Bold))
                    Text("مثلاً خرید لپ‌تاپ؛ پیشرفتش را همین‌جا می‌بینید.", style = tzText(12.sp, color = Tz.Muted))
                }
                SecondaryButton("تعیین هدف", onClick = onEdit, fill = false, height = 36.dp, fontSize = 13.sp)
            }
        } else {
            val fraction = if (goal.targetAmount > 0) goal.currentAmount.toFloat() / goal.targetAmount else 0f
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("هدف: ${goal.title}", style = tzText(14.sp, FontWeight.Bold))
                    Text("${TzFormat.compact(goal.currentAmount)} از ${TzFormat.compact(goal.targetAmount)}", style = tzText(12.sp, color = Tz.Muted))
                }
                SecondaryButton("ویرایش هدف", onClick = onEdit, fill = false, height = 36.dp, fontSize = 13.sp)
            }
            Spacer(Modifier.height(10.dp))
            TzProgress(fraction, color = Tz.Green)
        }
    }
}

@Composable
private fun GoalDialog(
    goal: FinancialGoal?,
    onDismiss: () -> Unit,
    onSave: (String, Long, Long) -> Unit,
    onDelete: (() -> Unit)?
) {
    var title by remember { mutableStateOf(goal?.title ?: "") }
    var target by remember { mutableStateOf(goal?.targetAmount?.toString() ?: "") }
    var saved by remember { mutableStateOf(goal?.currentAmount?.toString() ?: "") }
    val t = target.toLongOrNull() ?: 0L
    val s = saved.toLongOrNull() ?: 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tz.Surface,
        title = { Text(if (goal == null) "هدف پس‌انداز" else "ویرایش هدف", style = tzText(18.sp, FontWeight.Bold)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TzTextField("نام هدف", title, { title = it.take(40) }, "مثلاً خرید لپ‌تاپ")
                TzTextField("مبلغ هدف", target, { target = TzFormat.digitsToLatin(it).take(13) }, "تومان", numeric = true)
                TzTextField("تا امروز جمع شده", saved, { saved = TzFormat.digitsToLatin(it).take(13) }, "تومان", numeric = true)
            }
        },
        confirmButton = {
            TextAction("ذخیره", onClick = { if (title.isNotBlank() && t > 0) onSave(title.trim(), t, s) })
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextAction("حذف هدف", onClick = onDelete, color = Tz.Red)
                TextAction("انصراف", onClick = onDismiss, color = Tz.Muted)
            }
        }
    )
}
