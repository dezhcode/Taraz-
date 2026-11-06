package com.example.ui.layer

import androidx.compose.foundation.layout.Arrangement
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
    val allTags = remember(transactions) { transactions.flatMap { it.tagList }.distinct().sorted() }
    val built = f.copy(minAmount = parseAmount(minText), maxAmount = parseAmount(maxText))
    val hits = remember(built, transactions) { transactions.filter { built.matches(it) } }
    val now = System.currentTimeMillis()

    fun range(label: String, from: Long?, to: Long?) { f = f.copy(from = from, to = to, rangeLabel = label) }

    LayerSheet(
        title = "فیلتر تراکنش‌ها",
        onDismiss = onDismiss,
        topGap = 40.dp,
        bottomBar = {
            Callout(
                "${fa(hits.size)} تراکنش · ${money(hits.filter { !it.isTransfer && it.isExpense }.sumOf { it.amount })} هزینه",
                if (hits.any { !it.isExpense && !it.isTransfer }) "${money(hits.filter { !it.isExpense && !it.isTransfer }.sumOf { it.amount })} درآمد" else null,
                L.PrimarySoft, L.PrimaryText
            )
            BigButton("نمایش ${fa(hits.size)} تراکنش") { onApply(built); onDismiss() }
        }
    ) {
        if (built.active) SoftButton("پاک کردن همهٔ فیلترها", height = 36.dp) { f = TxFilter(); minText = ""; maxText = "" }
        Text("نوع", style = lt(13.sp, FontWeight.Bold))
        LSegmented(listOf("همه", "هزینه", "درآمد", "انتقال"), listOf("all", "expense", "income", "transfer").indexOf(f.type), {
            f = f.copy(type = listOf("all", "expense", "income", "transfer")[it])
        })
        Text("بازهٔ تاریخ", style = lt(13.sp, FontWeight.Bold))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val (ms, me) = JalaliDate.monthRange(now)
            val prev = JalaliDate.monthRange(shiftJalaliMonth(now, -1))
            LChip("همه", f.from == null && f.to == null) { f = f.copy(from = null, to = null, rangeLabel = null) }
            LChip("این ماه", f.rangeLabel == "این ماه") { range("این ماه", ms, me) }
            LChip("ماه قبل", f.rangeLabel == "ماه قبل") { range("ماه قبل", prev.first, prev.second) }
            LChip("۳ ماه اخیر", f.rangeLabel == "۳ ماه اخیر") { range("۳ ماه اخیر", JalaliDate.monthRange(shiftJalaliMonth(now, -2)).first, me) }
            LChip("امسال", f.rangeLabel == "امسال") {
                val y = JalaliDate.today().year
                range("امسال", JalaliDate.toTimestamp(y, 1, 1), JalaliDate.toTimestamp(y + 1, 1, 1))
            }
        }
        Text("دسته", style = lt(13.sp, FontWeight.Bold))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.sortedBy { it.sortOrder }.forEach { c ->
                val on = c.name in f.categories
                LChip(c.name, on) { f = f.copy(categories = if (on) f.categories - c.name else f.categories + c.name) }
            }
        }
        if (cards.isNotEmpty()) {
            Text("حساب", style = lt(13.sp, FontWeight.Bold))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cards.forEach { c ->
                    val on = c.bankName in f.accounts
                    LChip(c.bankName, on) { f = f.copy(accounts = if (on) f.accounts - c.bankName else f.accounts + c.bankName) }
                }
            }
        }
        if (allTags.isNotEmpty()) {
            Text("برچسب", style = lt(13.sp, FontWeight.Bold))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                allTags.forEach { t -> LChip("# $t", f.tag == t) { f = f.copy(tag = if (f.tag == t) null else t) } }
            }
        }
        Text("بازهٔ مبلغ (تومان)", style = lt(13.sp, FontWeight.Bold))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LField(minText, { minText = groupedInput(it) }, "از", Modifier.weight(1f), placeholder = "۰", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            LField(maxText, { maxText = groupedInput(it) }, "تا", Modifier.weight(1f), placeholder = "بی‌سقف", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        Spacer(Modifier.height(4.dp))
    }
}
