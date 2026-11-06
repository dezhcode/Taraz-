package com.example.ui.layer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BankCard
import com.example.data.Category
import com.example.data.Transaction
import com.example.data.isTransfer
import com.example.data.tagList
import com.example.utils.JalaliDate

/** فیلتر پیشرفتهٔ تراکنش‌ها؛ هر بخش خالی یعنی «همه» */
data class TxFilter(
    val type: String = "all", // all | expense | income | transfer
    val categories: Set<String> = emptySet(),
    val accounts: Set<String> = emptySet(),
    val tag: String? = null,
    val minAmount: Long = 0,
    val maxAmount: Long = 0,
    val from: Long? = null,
    val to: Long? = null,
    val rangeLabel: String? = null
) {
    val active: Boolean
        get() = type != "all" || categories.isNotEmpty() || accounts.isNotEmpty() || tag != null ||
            minAmount > 0 || maxAmount > 0 || from != null || to != null

    fun matches(t: Transaction): Boolean {
        when (type) {
            "expense" -> if (!t.isExpense || t.isTransfer) return false
            "income" -> if (t.isExpense || t.isTransfer) return false
            "transfer" -> if (!t.isTransfer) return false
        }
        if (categories.isNotEmpty() && t.category !in categories) return false
        if (accounts.isNotEmpty() && t.bankName !in accounts) return false
        if (tag != null && tag !in t.tagList) return false
        if (minAmount > 0 && t.amount < minAmount) return false
        if (maxAmount > 0 && t.amount > maxAmount) return false
        if (from != null && t.date < from) return false
        if (to != null && t.date >= to) return false
        return true
    }

    /** «هزینه · خوراک، حمل‌ونقل · این ماه» برای نمایش بالای نتیجه */
    fun summary(): String = buildList {
        when (type) { "expense" -> add("هزینه"); "income" -> add("درآمد"); "transfer" -> add("انتقال") }
        if (categories.isNotEmpty()) add(categories.joinToString("، "))
        if (accounts.isNotEmpty()) add(accounts.joinToString("، "))
        tag?.let { add("# $it") }
        if (minAmount > 0 || maxAmount > 0) add(
            when {
                minAmount > 0 && maxAmount > 0 -> "${millions(minAmount)} تا ${millions(maxAmount)}"
                minAmount > 0 -> "از ${millions(minAmount)}"
                else -> "تا ${millions(maxAmount)}"
            }
        )
        rangeLabel?.let { add(it) }
    }.joinToString(" · ")
}

@Composable
private fun FilterSection(title: String, trailing: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = lt(14.sp, FontWeight.Bold), modifier = Modifier.weight(1f))
            if (trailing != null) Text(trailing, style = lt(12.sp, color = L.Muted))
        }
        content()
    }
}

@Composable
private fun DateBox(label: String, value: String?, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(L.Chip).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, style = lt(11.sp, color = L.Muted))
        Text(value ?: "انتخاب کنید", style = lt(14.sp, FontWeight.SemiBold, if (value == null) L.Faint else L.Ink), maxLines = 1)
    }
}

/**
 * فیلتر تراکنش‌ها: نوع، بازهٔ تاریخ (آماده یا دلخواه)، دسته، حساب، برچسب و مبلغ.
 * پایین شیت همیشه تعداد نتیجه و دکمهٔ نمایش دیده می‌شود.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TxFilterSheet(
    current: TxFilter,
    transactions: List<Transaction>,
    cards: List<BankCard>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onApply: (TxFilter) -> Unit
) {
    var f by remember { mutableStateOf(current) }
    var minText by remember { mutableStateOf(if (current.minAmount > 0) money(current.minAmount) else "") }
    var maxText by remember { mutableStateOf(if (current.maxAmount > 0) money(current.maxAmount) else "") }
    var picking by remember { mutableStateOf<String?>(null) } // "from" | "to"
    val allTags = remember(transactions) { transactions.flatMap { it.tagList }.distinct().sorted() }
    val built = f.copy(minAmount = parseAmount(minText), maxAmount = parseAmount(maxText))
    val hits = remember(built, transactions) { transactions.filter { built.matches(it) } }
    val spent = hits.filter { !it.isTransfer && it.isExpense }.sumOf { it.amount }
    val earned = hits.filter { !it.isTransfer && !it.isExpense }.sumOf { it.amount }
    val now = System.currentTimeMillis()
    val custom = f.rangeLabel?.startsWith("از ") == true || picking != null

    fun range(label: String, from: Long?, to: Long?) { f = f.copy(from = from, to = to, rangeLabel = label) }
    fun customLabel(from: Long?, to: Long?): String =
        "از ${from?.let { JalaliDate.fromTimestamp(it).formatShort() } ?: "…"} تا ${to?.let { JalaliDate.fromTimestamp(it - 1).formatShort() } ?: "امروز"}"

    LayerSheet(
        title = "فیلتر تراکنش‌ها",
        onDismiss = onDismiss,
        closeLabel = "بستن",
        topGap = 24.dp,
        bottomBar = {
            Box(Modifier.fillMaxWidth().height(1.dp).background(L.Line))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${fa(hits.size)} تراکنش", style = lt(15.sp, FontWeight.Bold))
                    Text(
                        listOfNotNull(
                            if (spent > 0) "هزینه ${money(spent)}" else null,
                            if (earned > 0) "درآمد ${money(earned)}" else null
                        ).joinToString(" · ").ifEmpty { "تومان" },
                        style = lt(12.sp, color = L.Muted), maxLines = 1
                    )
                }
                if (built.active) {
                    Box(
                        Modifier.height(40.dp).clip(RoundedCornerShape(12.dp))
                            .clickable { f = TxFilter(); minText = ""; maxText = "" }.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("پاک کردن همه", style = lt(13.sp, FontWeight.SemiBold, L.RedText)) }
                }
            }
            BigButton(
                if (hits.isEmpty()) "تراکنشی با این فیلتر نیست" else "نمایش ${fa(hits.size)} تراکنش",
                enabled = hits.isNotEmpty()
            ) { onApply(built); onDismiss() }
        }
    ) {
        FilterSection("نوع") {
            LSegmented(listOf("همه", "هزینه", "درآمد", "انتقال"), listOf("all", "expense", "income", "transfer").indexOf(f.type), {
                f = f.copy(type = listOf("all", "expense", "income", "transfer")[it])
            })
        }

        FilterSection("بازهٔ تاریخ") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val (ms, me) = JalaliDate.monthRange(now)
                val prev = JalaliDate.monthRange(shiftJalaliMonth(now, -1))
                LChip("همه", f.from == null && f.to == null && !custom) { f = f.copy(from = null, to = null, rangeLabel = null) }
                LChip("این ماه", f.rangeLabel == "این ماه") { range("این ماه", ms, me) }
                LChip("ماه قبل", f.rangeLabel == "ماه قبل") { range("ماه قبل", prev.first, prev.second) }
                LChip("۳ ماه اخیر", f.rangeLabel == "۳ ماه اخیر") { range("۳ ماه اخیر", JalaliDate.monthRange(shiftJalaliMonth(now, -2)).first, me) }
                LChip("امسال", f.rangeLabel == "امسال") {
                    val y = JalaliDate.today().year
                    range("امسال", JalaliDate.toTimestamp(y, 1, 1), JalaliDate.toTimestamp(y + 1, 1, 1))
                }
                LChip("دلخواه", custom) {
                    if (!custom) f = f.copy(from = null, to = null, rangeLabel = customLabel(null, null))
                }
            }
            if (custom) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateBox("از تاریخ", f.from?.let { JalaliDate.fromTimestamp(it).formatShort() }, Modifier.weight(1f)) { picking = "from" }
                    DateBox("تا تاریخ", f.to?.let { JalaliDate.fromTimestamp(it - 1).formatShort() }, Modifier.weight(1f)) { picking = "to" }
                }
            }
        }

        FilterSection("دسته", if (f.categories.isNotEmpty()) "${fa(f.categories.size)} انتخاب" else null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.sortedBy { it.sortOrder }.forEach { c ->
                    val on = c.name in f.categories
                    LChip(c.name, on) { f = f.copy(categories = if (on) f.categories - c.name else f.categories + c.name) }
                }
            }
        }

        if (cards.isNotEmpty()) {
            FilterSection("حساب") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cards.forEach { c ->
                        val on = c.bankName in f.accounts
                        LChip(c.bankName, on) { f = f.copy(accounts = if (on) f.accounts - c.bankName else f.accounts + c.bankName) }
                    }
                }
            }
        }

        if (allTags.isNotEmpty()) {
            FilterSection("برچسب") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    allTags.forEach { t -> LChip("# $t", f.tag == t) { f = f.copy(tag = if (f.tag == t) null else t) } }
                }
            }
        }

        FilterSection("مبلغ (تومان)") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LField(minText, { minText = groupedInput(it) }, "کمترین", Modifier.weight(1f), placeholder = "۰", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                LField(maxText, { maxText = groupedInput(it) }, "بیشترین", Modifier.weight(1f), placeholder = "بی‌سقف", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    picking?.let { which ->
        JalaliDatePickerSheet(
            initial = (if (which == "from") f.from else f.to?.minus(1)) ?: now,
            onDismiss = { picking = null },
            onPick = { ts ->
                val day = startOfDay(ts)
                f = if (which == "from") {
                    val to = f.to?.takeIf { it > day }
                    f.copy(from = day, to = to, rangeLabel = customLabel(day, to))
                } else {
                    val to = day + DAY_MS
                    val from = f.from?.takeIf { it < to }
                    f.copy(from = from, to = to, rangeLabel = customLabel(from, to))
                }
            },
            title = if (which == "from") "از تاریخ" else "تا تاریخ",
            showTime = false
        )
    }
}
