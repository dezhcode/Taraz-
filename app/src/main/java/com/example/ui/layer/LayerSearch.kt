package com.example.ui.layer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Bill
import com.example.data.BankCard
import com.example.data.Category
import com.example.data.Cheque
import com.example.data.ChequeStatus
import com.example.data.Person
import com.example.data.Transaction
import com.example.data.tagList
import com.example.utils.JalaliDate

/** رقم‌های فارسی و عربی را لاتین می‌کند تا «۲۵۰» و «250» هر دو پیدا شوند */
private fun latin(s: String): String = buildString(s.length) {
    s.forEach { ch ->
        val p = "۰۱۲۳۴۵۶۷۸۹".indexOf(ch)
        val a = "٠١٢٣٤٥٦٧٨٩".indexOf(ch)
        append(if (p >= 0) '0' + p else if (a >= 0) '0' + a else ch)
    }
}

@Composable
private fun SearchGroup(title: String, count: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.padding(top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = lt(13.sp, FontWeight.Bold, L.Muted), modifier = Modifier.weight(1f))
            Text(fa(count), style = lt(12.sp, color = L.Muted))
        }
        content()
    }
}

@Composable
private fun SearchRow(title: String, sub: String, trailing: String? = null, trailingColor: androidx.compose.ui.graphics.Color = L.Ink, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = lt(14.sp, FontWeight.SemiBold), maxLines = 1)
            Text(sub, style = lt(12.sp, color = L.Muted), maxLines = 1)
        }
        if (trailing != null) Text(trailing, style = lt(14.sp, FontWeight.SemiBold, trailingColor))
    }
}

/**
 * جستجوی کلی از خانه: یک کادر برای ابزارها، تراکنش‌ها (عنوان، دسته، برچسب، حساب، مبلغ)،
 * اشخاص، قبض‌ها و چک‌ها.
 */
@Composable
fun HomeSearchSheet(
    transactions: List<Transaction>,
    cards: List<BankCard>,
    categories: List<Category>,
    persons: List<Person>,
    balances: Map<Int, Long>,
    bills: List<Bill>,
    cheques: List<Cheque>,
    onTool: (String) -> Unit,
    onTransaction: (Transaction) -> Unit,
    onPerson: (Person) -> Unit,
    onBills: () -> Unit,
    onCheque: (Cheque) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val q = latin(query.trim())
    val digits = q.filter { it.isDigit() }

    val tools = if (q.isEmpty()) emptyList() else HomeTools.all.filter { it.label.contains(q) || it.sub.contains(q) }
    val txs = remember(q, transactions) {
        if (q.length < 2 && digits.isEmpty()) emptyList()
        else transactions.filter { t ->
            t.title.contains(q) || t.category.contains(q) || t.bankName.contains(q) ||
                t.tagList.any { it.contains(q.removePrefix("#")) } ||
                (digits.length >= 3 && t.amount.toString().contains(digits))
        }.sortedByDescending { it.date }
    }
    val people = if (q.isEmpty()) emptyList() else persons.filter { it.name.contains(q) }
    val foundBills = if (q.isEmpty()) emptyList() else bills.filter { it.title.contains(q) || it.kind.contains(q) || (digits.length >= 4 && it.billId.contains(digits)) }
    val foundCheques = if (q.isEmpty()) emptyList() else cheques.filter { it.person.contains(q) || (digits.length >= 3 && it.amount.toString().contains(digits)) }
    val total = tools.size + txs.size + people.size + foundBills.size + foundCheques.size

    LayerSheet(title = "جستجو", onDismiss = onDismiss, closeLabel = "بستن", topGap = 24.dp) {
        LField(query, { query = it }, "عبارت جستجو", placeholder = "اسنپ، قبض، علی، ۲۵۰٬۰۰۰ یا #سفر")
        when {
            q.isEmpty() -> Text(
                "عنوان یا مبلغ تراکنش، نام شخص، قبض یا چک، یا نام یک ابزار مانند «چک» یا «بودجه» را وارد کنید.",
                style = lt(13.sp, color = L.Muted, lineHeight = 22.sp)
            )
            total == 0 -> Text("موردی یافت نشد.", style = lt(13.sp, color = L.Muted))
        }
        if (tools.isNotEmpty()) SearchGroup("ابزارها", tools.size) {
            tools.take(6).forEach { t -> SearchRow(t.label, t.sub) { onDismiss(); onTool(t.id) } }
        }
        if (people.isNotEmpty()) SearchGroup("اشخاص", people.size) {
            people.take(6).forEach { p ->
                val bal = balances[p.id] ?: 0L
                SearchRow(
                    p.name, p.relation,
                    trailing = when { bal > 0 -> "بستانکار ${money(bal)}"; bal < 0 -> "بدهکار ${money(-bal)}"; else -> "تسویه" },
                    trailingColor = if (bal > 0) L.Green else if (bal < 0) L.RedText else L.Muted
                ) { onDismiss(); onPerson(p) }
            }
        }
        if (foundBills.isNotEmpty()) SearchGroup("قبوض", foundBills.size) {
            foundBills.take(5).forEach { b ->
                SearchRow(b.title.ifBlank { b.kind }, "موعد ${JalaliDate.fromTimestamp(b.dueDate).formatShort()}", if (b.amount > 0) money(b.amount) else null) { onDismiss(); onBills() }
            }
        }
        if (foundCheques.isNotEmpty()) SearchGroup("چک‌ها", foundCheques.size) {
            foundCheques.take(5).forEach { c ->
                SearchRow(
                    (if (c.issued) "به " else "از ") + c.person,
                    "${JalaliDate.fromTimestamp(c.dueDate).formatShort()} · " + when (c.status) {
                        ChequeStatus.CLEARED -> "وصول شد"; ChequeStatus.BOUNCED -> "برگشتی"; else -> "در انتظار"
                    },
                    money(c.amount)
                ) { onDismiss(); onCheque(c) }
            }
        }
        if (txs.isNotEmpty()) SearchGroup("تراکنش‌ها", txs.size) {
            txs.take(30).forEach { t ->
                SearchRow(
                    t.title, "${JalaliDate.fromTimestamp(t.date).formatShort()} · ${t.category} · ${t.bankName}",
                    if (t.isTransfer()) money(t.amount) else signedMoney(t.amount, t.isExpense),
                    txColor(t, L.Muted)
                ) { onDismiss(); onTransaction(t) }
            }
            if (txs.size > 30) Text("${fa(txs.size - 30)} تراکنش دیگر؛ برای مشاهدهٔ همه از فیلتر تراکنش‌ها استفاده کنید.", style = lt(12.sp, color = L.Muted))
        }
        Spacer(Modifier.height(4.dp))
    }
}

private fun Transaction.isTransfer(): Boolean = type == com.example.data.TransactionType.TRANSFER
