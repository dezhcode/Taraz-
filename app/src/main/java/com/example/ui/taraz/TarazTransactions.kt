package com.example.ui.taraz

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Transaction
import com.example.data.countsAsExpense
import com.example.ui.FinanceViewModel
import com.example.utils.JalaliDate

private const val DAY_MS = 24L * 60 * 60 * 1000

/**
 * Transactions as days: pick a day in the week strip and its transactions show
 * as a timeline. Search looks across everything.
 */
@Composable
fun TarazTransactionsScreen(
    vm: FinanceViewModel,
    onTransactionClick: (Transaction) -> Unit
) {
    val transactions by vm.transactions.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val budget by vm.dailyBudget.collectAsStateWithLifecycle()

    val now = System.currentTimeMillis()
    val todayStart = JalaliDate.dayRange(now).first
    var weekOffset by remember { mutableIntStateOf(0) }        // 0 = this week, 1 = the week before …
    var selectedBack by remember { mutableIntStateOf(0) }      // days back from the week's last day
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val weekEnd = todayStart - weekOffset * 7 * DAY_MS
    val days = (6 downTo 0).map { back ->
        val start = weekEnd - back * DAY_MS
        start to transactions.filter { it.countsAsExpense && it.date >= start && it.date < start + DAY_MS }.sumOf { it.amount }
    }
    val weekTotal = days.sumOf { it.second }
    val maxSpent = days.maxOf { it.second }.coerceAtLeast(1L)
    val selectedStart = weekEnd - selectedBack * DAY_MS
    val dayTx = transactions.filter { it.date >= selectedStart && it.date < selectedStart + DAY_MS }.sortedByDescending { it.date }
    val daySpent = dayTx.filter { it.countsAsExpense }.sumOf { it.amount }

    val firstDay = JalaliDate.fromTimestamp(weekEnd - 6 * DAY_MS)
    val lastDay = JalaliDate.fromTimestamp(weekEnd)

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(Tz.Ground)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = BottomBarClearance)
    ) {
        item {
            TzTopBar(
                title = "تراکنش‌ها",
                onBack = null,
                subtitle = "${TzFormat.fa(firstDay.day)} تا ${lastDay.formatShort()} · خرج ${TzFormat.compact(weekTotal)}"
            ) {
                if (searching) {
                    SecondaryButton("بستن جستجو", onClick = { searching = false; query = "" }, fill = false, height = 40.dp, fontSize = 13.sp, icon = Icons.Filled.Close)
                } else {
                    SecondaryButton("جستجو", onClick = { searching = true }, fill = false, height = 40.dp, fontSize = 14.sp, icon = Icons.Filled.Search)
                }
            }
        }

        if (searching) {
            item {
                Box(
                    Modifier
                        .padding(start = 20.dp, end = 20.dp, top = 14.dp)
                        .fillMaxWidth()
                        .height(48.dp)
                        .tzField(focused = true)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) Text("جستجو در عنوان، دسته یا حساب", style = tzText(14.sp, color = Tz.Faint))
                    BasicTextField(query, { query = it }, singleLine = true, textStyle = tzText(14.sp), modifier = Modifier.fillMaxWidth())
                }
            }
            val q = query.trim()
            val results = if (q.isEmpty()) emptyList() else transactions.filter {
                it.title.contains(q) || it.category.contains(q) || it.bankName.contains(q)
            }.sortedByDescending { it.date }
            if (q.isNotEmpty() && results.isEmpty()) {
                item { EmptyNote("چیزی پیدا نشد", "عبارت دیگری را امتحان کنید.", Modifier.padding(20.dp)) }
            }
            results.groupBy { JalaliDate.dayRange(it.date).first }.forEach { (start, list) ->
                item(key = "s-$start") {
                    Text(
                        TzFormat.dayLabel(start, now),
                        style = tzText(13.sp, FontWeight.Bold),
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 6.dp)
                    )
                }
                list.forEach { tx ->
                    item(key = "sr-${tx.id}") {
                        TxCardRow(tx, categories, Modifier.padding(horizontal = 20.dp, vertical = 4.dp), onClick = { onTransactionClick(tx) })
                    }
                }
            }
            return@LazyColumn
        }

        item {
            TzCard(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp), padding = PaddingValues(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArrowButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "هفتهٔ قبل") {
                        weekOffset += 1
                        selectedBack = 0
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        days.forEachIndexed { index, (start, spent) ->
                            val back = 6 - index
                            val on = back == selectedBack
                            val over = budget > 0 && spent > budget
                            Column(
                                Modifier
                                    .weight(1f)
                                    .height(72.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (on) Tz.Cobalt else Color.Transparent)
                                    .tzClick { selectedBack = back }
                                    .semantics { contentDescription = TzFormat.weekday(start) + " " + JalaliDate.fromTimestamp(start).formatShort() }
                                    .padding(vertical = 7.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    if (start == todayStart) "امروز" else TzFormat.weekdayLetter(start),
                                    style = tzText(11.sp, color = if (on) Tz.CobaltOnDark else Tz.Muted)
                                )
                                Text(TzFormat.fa(JalaliDate.fromTimestamp(start).day), style = tzText(15.sp, FontWeight.Bold, if (on) Color.White else Tz.Ink))
                                val h = (22f * spent / maxSpent).coerceAtLeast(if (spent > 0) 5f else 3f)
                                Box(
                                    Modifier
                                        .width(6.dp)
                                        .height(h.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(
                                            when {
                                                on -> Color.White
                                                over -> Tz.AmberDot
                                                spent > 0 -> Tz.BarSoft
                                                else -> Tz.Hairline
                                            }
                                        )
                                )
                            }
                        }
                    }
                    ArrowButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "هفتهٔ بعد", enabled = weekOffset > 0) {
                        weekOffset -= 1
                        selectedBack = 0
                    }
                }
                if (budget > 0) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(Tz.AmberDot))
                        Spacer(Modifier.width(6.dp))
                        Text("بالای بودجهٔ روزانه", style = tzText(11.sp, color = Tz.Muted), modifier = Modifier.weight(1f))
                        Text("بودجهٔ روزانه: ${TzFormat.compact(budget)}", style = tzText(11.sp, color = Tz.Muted))
                    }
                }
            }
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (selectedStart == todayStart) "امروز، " + TzFormat.weekday(selectedStart) + " " + JalaliDate.fromTimestamp(selectedStart).formatShort()
                        else TzFormat.weekday(selectedStart) + " " + JalaliDate.fromTimestamp(selectedStart).formatShort(),
                        style = tzText(17.sp, FontWeight.Bold)
                    )
                    Text("خرج روز: ${TzFormat.money(daySpent)} تومان", style = tzText(13.sp, color = Tz.Muted))
                }
                if (budget > 0) {
                    if (daySpent > budget) {
                        TzTag(TzFormat.compact(daySpent - budget) + " بالای بودجه", Tz.AmberTint, Tz.Amber)
                    } else {
                        TzTag("زیر بودجه", Tz.GreenTint, Tz.GreenText)
                    }
                }
            }
        }

        if (dayTx.isEmpty()) {
            item {
                EmptyNote(
                    "در این روز تراکنشی ثبت نشده",
                    "روز دیگری را از نوار بالا انتخاب کنید یا با دکمهٔ + تراکنش تازه ثبت کنید.",
                    Modifier.padding(horizontal = 20.dp)
                )
            }
        }
        dayTx.forEachIndexed { index, tx ->
            item(key = "d-${tx.id}") {
                TimelineRow(label = TzFormat.time(tx.date), isFirst = index == 0, isLast = index == dayTx.lastIndex) {
                    TxCardRow(tx, categories, onClick = { onTransactionClick(tx) })
                }
            }
        }
    }
}

@Composable
private fun ArrowButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 32.dp, height = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(if (enabled) Modifier.tzClick(onClick) else Modifier)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (enabled) Tz.Ink else Tz.Border, modifier = Modifier.size(20.dp))
    }
}
