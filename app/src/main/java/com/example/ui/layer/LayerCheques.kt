package com.example.ui.layer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BankCard
import com.example.data.Category
import com.example.data.Cheque
import com.example.data.ChequeStatus
import com.example.data.isCash
import com.example.utils.JalaliDate

private fun daysUntil(ts: Long): Int = ((startOfDay(ts) - startOfDay(System.currentTimeMillis())) / DAY_MS).toInt()

/** «۳ روز دیگر»، «امروز»، «۲ روز گذشته» */
internal fun dueLabel(ts: Long): String {
    val d = daysUntil(ts)
    return when {
        d == 0 -> "امروز"
        d == 1 -> "فردا"
        d > 1 -> "${fa(d)} روز دیگر"
        else -> "${fa(-d)} روز گذشته"
    }
}

private fun statusLabel(c: Cheque): String = when (c.status) {
    ChequeStatus.CLEARED -> "پاس شد"
    ChequeStatus.BOUNCED -> "برگشت خورد"
    else -> dueLabel(c.dueDate)
}

/** بخش «چک‌ها» در صفحهٔ قرض و قسط: چک‌هایی که داده‌ام و گرفته‌ام */
@Composable
internal fun ColumnScope.ChequesSection(cheques: List<Cheque>, onAdd: () -> Unit, onOpen: (Cheque) -> Unit) {
    val pending = cheques.filter { it.status == ChequeStatus.PENDING }
    val outSum = pending.filter { it.issued }.sumOf { it.amount }
    val inSum = pending.filter { !it.issued }.sumOf { it.amount }

    Row(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp).fillMaxWidth().height(44.dp).clip(ButtonShape)
            .background(L.Primary).clickable(onClick = onAdd),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text("ثبت چک", style = lt(14.sp, FontWeight.Bold, Color.White))
    }

    if (cheques.isEmpty()) {
        Column(
            Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("هنوز چکی ثبت نکرده‌اید", style = lt(15.sp, FontWeight.Bold))
            Text(
                "چک‌های پرداختی و دریافتی را با تاریخ سررسید ثبت کنید؛ تراز سه روز و یک روز پیش از سررسید یادآوری می‌کند و پس از وصول، مبلغ را در حساب ثبت می‌کند.",
                style = lt(13.sp, color = L.Muted, lineHeight = 23.sp)
            )
        }
        return
    }

    if (pending.isNotEmpty()) {
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f).cardSurface(16.dp).padding(12.dp)) {
                Text("چک‌های پرداختی", style = lt(12.sp, color = L.Muted))
                FitText(toman(outSum), lt(16.sp, FontWeight.Bold, L.RedText))
            }
            Column(Modifier.weight(1f).cardSurface(16.dp).padding(12.dp)) {
                Text("چک‌های دریافتی", style = lt(12.sp, color = L.Muted))
                FitText(toman(inSum), lt(16.sp, FontWeight.Bold, L.Green))
            }
        }
    }

    val ordered = pending.sortedBy { it.dueDate } + cheques.filter { it.status != ChequeStatus.PENDING }.sortedByDescending { it.dueDate }
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ordered.forEach { c ->
            val soon = c.status == ChequeStatus.PENDING && daysUntil(c.dueDate) <= 3
            Row(
                Modifier.fillMaxWidth().cardSurface(18.dp).clickable { onOpen(c) }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(40.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .background(if (c.issued) L.RedSoft else L.GreenSoft),
                    contentAlignment = Alignment.Center
                ) { Text(if (c.issued) "↑" else "↓", style = lt(18.sp, FontWeight.Bold, if (c.issued) L.RedText else L.GreenText)) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text((if (c.issued) "به " else "از ") + c.person, style = lt(15.sp, FontWeight.SemiBold), maxLines = 1)
                    Text(
                        "${JalaliDate.fromTimestamp(c.dueDate).formatShort()} · ${c.bankName}",
                        style = lt(12.sp, color = L.Muted), maxLines = 1
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(money(c.amount), style = lt(15.sp, FontWeight.Bold, if (c.status == ChequeStatus.PENDING) L.Ink else L.Muted))
                    Text(
                        statusLabel(c),
                        style = lt(11.sp, FontWeight.SemiBold, when {
                            c.status == ChequeStatus.BOUNCED -> L.RedText
                            c.status == ChequeStatus.CLEARED -> L.GreenText
                            soon -> L.AmberInk
                            else -> L.Muted
                        })
                    )
                }
            }
        }
    }
}

/** ثبت یا ویرایش چک؛ برای چک ثبت‌شده دکمه‌های «پاس شد / برگشت خورد» هم هست */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChequeSheet(
    cheque: Cheque?,
    cards: List<BankCard>,
    categories: List<Category>,
    personNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (Cheque) -> Unit,
    onStatus: (Cheque, String, String) -> Unit,
    onDelete: (Cheque) -> Unit
) {
    var issued by remember { mutableStateOf(cheque?.issued ?: true) }
    var amountText by remember { mutableStateOf(cheque?.amount?.let { money(it) } ?: "") }
    var person by remember { mutableStateOf(cheque?.person ?: "") }
    var due by remember { mutableLongStateOf(cheque?.dueDate ?: (startOfDay(System.currentTimeMillis()) + 30 * DAY_MS + 10 * 3_600_000L)) }
    var account by remember { mutableStateOf(cheque?.bankName ?: cards.firstOrNull { !it.isCash }?.bankName ?: cards.firstOrNull()?.bankName ?: "") }
    var sayadi by remember { mutableStateOf(cheque?.sayadi ?: "") }
    var category by remember { mutableStateOf(cheque?.category ?: "سایر") }
    var note by remember { mutableStateOf(cheque?.note ?: "") }
    var pickDate by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    val amount = parseAmount(amountText)
    val canSave = amount > 0 && person.isNotBlank() && account.isNotBlank()
    val built = (cheque ?: Cheque(issued = issued, amount = 0, dueDate = due, person = "", bankName = "")).copy(
        issued = issued, amount = amount, dueDate = due, person = person.trim(), bankName = account,
        sayadi = sayadi, category = category, note = note.trim()
    )

    LayerSheet(
        title = if (cheque == null) "ثبت چک" else "${if (cheque.issued) "پرداخت چک به" else "دریافت چک از"} ${cheque.person}",
        onDismiss = onDismiss,
        topGap = 40.dp,
        bottomBar = {
            BigButton(if (cheque == null) "ثبت چک" else "ذخیره", enabled = canSave) { onSave(built); onDismiss() }
            if (cheque != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (cheque.status != ChequeStatus.CLEARED) {
                        Box(
                            Modifier.weight(1f).height(44.dp).clip(ButtonShape).background(L.GreenSoft)
                                .clickable(enabled = canSave) { onStatus(built, ChequeStatus.CLEARED, account); onDismiss() },
                            contentAlignment = Alignment.Center
                        ) { Text("پاس شد", style = lt(14.sp, FontWeight.Bold, L.GreenText)) }
                    }
                    if (cheque.status != ChequeStatus.BOUNCED) {
                        Box(
                            Modifier.weight(1f).height(44.dp).clip(ButtonShape).background(L.RedSoft)
                                .clickable { onStatus(built, ChequeStatus.BOUNCED, account); onDismiss() },
                            contentAlignment = Alignment.Center
                        ) { Text("برگشت خورد", style = lt(14.sp, FontWeight.Bold, L.RedText)) }
                    }
                    if (cheque.status != ChequeStatus.PENDING) {
                        Box(
                            Modifier.weight(1f).height(44.dp).clip(ButtonShape).background(L.Chip)
                                .clickable { onStatus(built, ChequeStatus.PENDING, account); onDismiss() },
                            contentAlignment = Alignment.Center
                        ) { Text("در انتظار", style = lt(14.sp, FontWeight.Bold)) }
                    }
                }
                if (askDelete) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.weight(1f).height(44.dp).clip(ButtonShape).background(L.RedText).clickable { onDelete(cheque); onDismiss() },
                            contentAlignment = Alignment.Center
                        ) { Text("بله، حذف شود", style = lt(14.sp, FontWeight.Bold, Color.White)) }
                        Box(
                            Modifier.height(44.dp).clip(ButtonShape).background(L.Surface).clickable { askDelete = false }.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("انصراف", style = lt(14.sp, FontWeight.SemiBold)) }
                    }
                } else {
                    Box(Modifier.fillMaxWidth().height(40.dp).clickable { askDelete = true }, contentAlignment = Alignment.Center) {
                        Text("حذف چک", style = lt(14.sp, FontWeight.SemiBold, L.RedText))
                    }
                }
            }
        }
    ) {
        if (cheque != null && cheque.status == ChequeStatus.CLEARED) {
            Callout("این چک وصول شده است", "${money(cheque.amount)} تومان روی حساب ${cheque.bankName} ثبت شده است. «در انتظار» آن تراکنش را برمی‌دارد.", L.GreenSoft, L.GreenText)
        }
        LSegmented(listOf("پرداخت چک", "دریافت چک"), if (issued) 0 else 1, {
            issued = it == 0
            // دستهٔ هزینه برای چک دریافتی معنا ندارد و برعکس
            val fits = categories.filter { c -> c.isIncome == !issued }.map { c -> c.name }
            if (category !in fits) category = fits.firstOrNull() ?: category
        })
        LField(amountText, { amountText = groupedInput(it) }, "مبلغ (تومان)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        LField(person, { person = it }, if (issued) "در وجه" else "از طرف", placeholder = "نام شخص یا فروشگاه")
        val sugg = personNames.filter { it != person && (person.isBlank() || it.contains(person.trim())) }.take(6)
        if (sugg.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sugg.forEach { n -> LChip(n, false) { person = n } }
            }
        }
        LRow("تاریخ سررسید", "${weekdayName(due)} ${JalaliDate.fromTimestamp(due).formatShort()} · ${dueLabel(due)}", divider = false, onClick = { pickDate = true })
        Text(if (issued) "از حساب" else "واریز به حساب", style = lt(13.sp, FontWeight.Bold))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            cards.forEach { c -> LChip(c.bankName, c.bankName == account) { account = c.bankName } }
        }
        Text("دسته (برای وقتی پاس شد)", style = lt(13.sp, FontWeight.Bold))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.filter { it.isIncome == !issued }.sortedBy { it.sortOrder }.forEach { c -> LChip(c.name, c.name == category) { category = c.name } }
        }
        LField(sayadi, { v -> sayadi = v.filter { it.isDigit() || it in "۰۱۲۳۴۵۶۷۸۹" }.take(16) }, "شمارهٔ صیادی (اختیاری)", placeholder = "۱۶ رقم",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        LField(note, { note = it }, "یادداشت (اختیاری)")
        Spacer(Modifier.height(4.dp))
    }
    if (pickDate) {
        JalaliDatePickerSheet(initial = due, onDismiss = { pickDate = false }, onPick = { due = it }, allowFuture = true, title = "تاریخ سررسید", showTime = false)
    }
}
