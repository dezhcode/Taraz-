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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BankCard
import com.example.data.Category
import com.example.data.Transaction
import com.example.data.countsAsExpense
import com.example.data.isTransfer
import com.example.utils.JalaliDate

@Composable
fun LayerTransactionsScreen(
    transactions: List<Transaction>,
    cards: List<BankCard>,
    categories: List<Category>,
    dailyBudget: Long,
    selectedDay: Long,
    onSelectDay: (Long) -> Unit,
    onTransactionClick: (Transaction) -> Unit,
    modifier: Modifier = Modifier
) {
    val todayStart = startOfDay(System.currentTimeMillis())
    val selected = startOfDay(selectedDay)
    // هفته‌ای که روز انتخاب‌شده در آن است (پنجرهٔ ۷ روزه که به امروز یا قبل‌تر ختم می‌شود)
    var weekOffset by remember(selected) {
        mutableIntStateOf(((todayStart - selected) / DAY_MS / 7).toInt().coerceAtLeast(0))
    }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val windowEnd = todayStart - weekOffset * 7 * DAY_MS
    val days = (6 downTo 0).map { windowEnd - it * DAY_MS }
    fun spendOf(start: Long) = transactions.filter { it.countsAsExpense && it.date >= start && it.date < start + DAY_MS }.sumOf { it.amount }
    val weekSpend = days.sumOf { spendOf(it) }
    val maxSpend = days.maxOf { spendOf(it) }.coerceAtLeast(1L)

    Column(
        modifier
            .fillMaxSize()
            .background(L.Bg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(bottom = NavClearance)
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("تراکنش‌ها", style = lt(26.sp, FontWeight.Bold))
                val first = JalaliDate.fromTimestamp(days.first())
                val last = JalaliDate.fromTimestamp(days.last())
                Text(
                    "هفتهٔ ${fa(first.day)} تا ${last.formatShort()} · خرج ${millions(weekSpend).removeSuffix("م")} میلیون",
                    style = lt(13.sp, color = L.Muted)
                )
            }
            Row(
                Modifier.height(40.dp).clip(ButtonShape).background(L.PrimarySoft)
                    .clickable { searching = !searching; if (!searching) query = "" }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(if (searching) Icons.Default.Close else Icons.Default.Search, null, tint = L.PrimaryText, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (searching) "بستن" else "جستجو", style = lt(14.sp, FontWeight.Bold, L.PrimaryText))
            }
        }

        if (searching) {
            Box(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp).fillMaxWidth().height(50.dp)
                    .cardSurface(16.dp).padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) Text("عنوان، دسته یا حساب…", style = lt(14.sp, color = L.Muted))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = lt(14.sp),
                    cursorBrush = SolidColor(L.Primary),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "جستجو در تراکنش‌ها" }
                )
            }
            val results = if (query.isBlank()) emptyList() else transactions.filter {
                it.title.contains(query, true) || it.category.contains(query, true) || it.bankName.contains(query, true) ||
                    it.amount.toString().contains(query.filter { c -> c.isDigit() }.ifEmpty { "\u0000" })
            }.sortedByDescending { it.date }.take(50)
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (query.isNotBlank()) {
                    Text("${fa(results.size)} نتیجه", style = lt(13.sp, color = L.Muted))
                }
                results.forEach { tx ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${weekdayName(tx.date)} ${JalaliDate.fromTimestamp(tx.date).formatShort()} · ${timeOfDay(tx.date)}", style = lt(11.sp, color = L.Muted))
                        TransactionCard(tx, cards, categories) { onTransactionClick(tx) }
                    }
                }
            }
            return@Column
        }

        // ── انتخاب روز
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp).fillMaxWidth().cardSurface(22.dp).padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                days.forEach { start ->
                    val on = start == selected
                    val spend = spendOf(start)
                    val over = dailyBudget > 0 && spend > dailyBudget
                    val h = (spend.toFloat() / maxSpend * 22f).coerceAtLeast(5f).dp
                    Column(
                        Modifier
                            .weight(1f)
                            .height(74.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (on) L.Primary else Color.Transparent)
                            .clickable { onSelectDay(start) }
                            .semantics {
                                this.selected = on
                                contentDescription = "${weekdayName(start)} ${JalaliDate.fromTimestamp(start).formatShort()}"
                            }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (start == todayStart) "امروز" else weekdayShort(start), style = lt(11.sp, color = if (on) L.OnPrimarySoft else L.Muted))
                        Text(fa(JalaliDate.fromTimestamp(start).day), style = lt(15.sp, FontWeight.Bold, if (on) Color.White else L.Ink))
                        Box(Modifier.width(6.dp).height(h).clip(RoundedCornerShape(3.dp)).background(if (on) Color.White else if (over) L.Amber else L.BarIdle))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).clickable { weekOffset += 1 }
                        .semantics { contentDescription = "هفتهٔ قبل" },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = L.Muted) }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(L.Amber))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (dailyBudget > 0) "بالای بودجهٔ روزانه (${money(dailyBudget / 1000)} هزار)" else "بالای بودجهٔ روزانه",
                        style = lt(11.sp, color = L.Muted)
                    )
                }
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = weekOffset > 0) { weekOffset -= 1 }
                        .semantics { contentDescription = "هفتهٔ بعد" },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = if (weekOffset > 0) L.Muted else L.Rail) }
            }
        }

        // ── سرصفحهٔ روز
        val daySpend = spendOf(selected)
        val over = dailyBudget > 0 && daySpend > dailyBudget
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val title = if (selected == todayStart) "امروز، ${weekdayName(selected)} ${JalaliDate.fromTimestamp(selected).formatShort()}"
                else "${weekdayName(selected)} ${JalaliDate.fromTimestamp(selected).formatShort()}"
                Text(title, style = lt(17.sp, FontWeight.Bold))
                Text("خرج روز: ${money(daySpend)} تومان", style = lt(13.sp, color = L.Muted))
            }
            if (dailyBudget > 0) {
                Box(
                    Modifier.height(28.dp).clip(PillShape).background(if (over) L.AmberSoft else L.GreenSoft).padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (over) "${money((daySpend - dailyBudget) / 1000)} هزار بالای بودجه" else "زیر بودجه",
                        style = lt(12.sp, FontWeight.Bold, if (over) L.AmberText else L.GreenText)
                    )
                }
            }
        }

        // ── خط زمان روز
        val dayTx = transactions.filter { it.date >= selected && it.date < selected + DAY_MS }.sortedByDescending { it.date }
        Column(Modifier.padding(horizontal = 20.dp)) {
            if (dayTx.isEmpty()) {
                Text("در این روز تراکنشی ثبت نشده.", style = lt(13.sp, color = L.Muted), modifier = Modifier.padding(vertical = 12.dp))
            }
            dayTx.forEachIndexed { i, tx ->
                TimelineRow(
                    label = timeOfDay(tx.date),
                    node = { SolidNode(if (tx.isTransfer) L.Primary else L.Ink) },
                    lastRow = i == dayTx.lastIndex,
                    labelWidth = 42.dp
                ) {
                    TransactionCard(tx, cards, categories) { onTransactionClick(tx) }
                }
            }
        }
    }
}
