package com.example.ui.layer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BankCard
import com.example.data.Bill
import com.example.data.BillKind
import com.example.utils.JalaliDate

private fun billDays(b: Bill): Int = ((startOfDay(b.dueDate) - startOfDay(System.currentTimeMillis())) / DAY_MS).toInt()

/** قبض‌هایی که تا سه روز دیگر یا زودتر موعدشان است */
fun billsDueSoon(bills: List<Bill>): Int = bills.count { it.enabled && billDays(it) <= 3 }

private fun billEmoji(kind: String): String = when (kind) {
    BillKind.POWER -> "⚡"
    BillKind.WATER -> "💧"
    BillKind.GAS -> "🔥"
    BillKind.PHONE -> "☎"
    BillKind.MOBILE -> "📱"
    BillKind.INTERNET -> "🌐"
    else -> "🧾"
}

/** قبوض: برق، آب، گاز… با موعد پرداخت و «پرداخت کردم» */
@Composable
fun LayerBillsScreen(
    bills: List<Bill>,
    cards: List<BankCard>,
    onBack: () -> Unit,
    onSave: (Bill) -> Unit,
    onDelete: (Bill) -> Unit,
    onPay: (Bill, Long, String) -> Unit
) {
    var editing by remember { mutableStateOf<Bill?>(null) }
    var adding by remember { mutableStateOf(false) }
    var paying by remember { mutableStateOf<Bill?>(null) }
    val sorted = bills.sortedBy { it.dueDate }
    val dueSoon = sorted.filter { billDays(it) <= 7 }

    LayerScreen(
        title = "قبوض",
        subtitle = "برق، آب، گاز، تلفن و اینترنت با یادآوری موعد",
        onBack = onBack,
        bottomBar = { BigButton("افزودن قبض") { adding = true } }
    ) {
        if (bills.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("هنوز قبضی اضافه نکرده‌اید", style = lt(15.sp, FontWeight.Bold))
                Text(
                    "شناسهٔ قبض و تاریخ موعد را یک بار وارد کنید؛ سه روز قبل از موعد یادآوری می‌آید و با «پرداخت کردم» هزینه در دستهٔ قبوض ثبت و موعد دورهٔ بعد خودکار تنظیم می‌شود.",
                    style = lt(13.sp, color = L.Muted, lineHeight = 23.sp)
                )
            }
        } else {
            if (dueSoon.isNotEmpty()) {
                val known = dueSoon.sumOf { it.amount }
                Callout(
                    "${fa(dueSoon.size)} قبض تا یک هفتهٔ دیگر",
                    if (known > 0) "حدود ${money(known)} تومان" else null,
                    L.AmberSoft, L.AmberInk,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sorted.forEach { b ->
                    val d = billDays(b)
                    Column(
                        Modifier.fillMaxWidth().cardSurface(18.dp).clickable { editing = b }.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(L.Chip),
                                contentAlignment = Alignment.Center
                            ) { Text(billEmoji(b.kind), style = lt(18.sp)) }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(b.title.ifBlank { b.kind }, style = lt(15.sp, FontWeight.SemiBold), maxLines = 1)
                                Text(
                                    "موعد ${JalaliDate.fromTimestamp(b.dueDate).formatShort()} · ${dueLabel(b.dueDate)}",
                                    style = lt(12.sp, color = if (d < 0) L.RedText else if (d <= 3) L.AmberInk else L.Muted)
                                )
                            }
                            Text(if (b.amount > 0) money(b.amount) else "—", style = lt(15.sp, FontWeight.Bold))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (b.billId.isNotBlank()) "شناسه ${fa(b.billId)}" else "هر ${fa(b.periodMonths)} ماه",
                                style = lt(12.sp, color = L.Muted), modifier = Modifier.weight(1f), maxLines = 1
                            )
                            SoftButton("پرداخت کردم", 34.dp) { paying = b }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (adding || editing != null) {
        BillSheet(
            bill = editing,
            onDismiss = { adding = false; editing = null },
            onSave = onSave,
            onDelete = onDelete
        )
    }
    paying?.let { b ->
        PayBillSheet(b, cards, onDismiss = { paying = null }, onPay = { amount, account -> onPay(b, amount, account) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BillSheet(bill: Bill?, onDismiss: () -> Unit, onSave: (Bill) -> Unit, onDelete: (Bill) -> Unit) {
    var kind by remember { mutableStateOf(bill?.kind ?: BillKind.POWER) }
    var title by remember { mutableStateOf(bill?.title ?: "") }
    var billId by remember { mutableStateOf(bill?.billId ?: "") }
    var payId by remember { mutableStateOf(bill?.payId ?: "") }
    var amountText by remember { mutableStateOf(bill?.amount?.takeIf { it > 0 }?.let { money(it) } ?: "") }
    var due by remember { mutableLongStateOf(bill?.dueDate ?: (startOfDay(System.currentTimeMillis()) + 14 * DAY_MS + 10 * 3_600_000L)) }
    var period by remember { mutableIntStateOf(bill?.periodMonths ?: 2) }
    var pickDate by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    LayerSheet(
        title = if (bill == null) "قبض تازه" else bill.title.ifBlank { bill.kind },
        onDismiss = onDismiss,
        topGap = 50.dp,
        bottomBar = {
            BigButton(if (bill == null) "افزودن" else "ذخیره") {
                onSave(
                    (bill ?: Bill(kind = kind, title = "", dueDate = due)).copy(
                        kind = kind, title = title.ifBlank { kind }, billId = billId, payId = payId,
                        amount = parseAmount(amountText), dueDate = due, periodMonths = period
                    )
                )
                onDismiss()
            }
            if (bill != null) {
                if (askDelete) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.weight(1f).height(44.dp).clip(ButtonShape).background(L.RedText).clickable { onDelete(bill); onDismiss() },
                            contentAlignment = Alignment.Center
                        ) { Text("بله، حذف شود", style = lt(14.sp, FontWeight.Bold, Color.White)) }
                        Box(
                            Modifier.height(44.dp).clip(ButtonShape).background(L.Surface).clickable { askDelete = false }.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("انصراف", style = lt(14.sp, FontWeight.SemiBold)) }
                    }
                } else {
                    Box(Modifier.fillMaxWidth().height(40.dp).clickable { askDelete = true }, contentAlignment = Alignment.Center) {
                        Text("حذف قبض", style = lt(14.sp, FontWeight.SemiBold, L.RedText))
                    }
                }
            }
        }
    ) {
        Text("نوع", style = lt(13.sp, FontWeight.Bold))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BillKind.all.forEach { k -> LChip("${billEmoji(k)} $k", k == kind) { kind = k; if (k == BillKind.MOBILE || k == BillKind.INTERNET) period = 1 } }
        }
        LField(title, { title = it }, "نام (اختیاری)", placeholder = "مثلاً برق خانه")
        LField(billId, { v -> billId = v.filter { it.isDigit() || it in "۰۱۲۳۴۵۶۷۸۹" }.take(18) }, "شناسهٔ قبض",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        LField(payId, { v -> payId = v.filter { it.isDigit() || it in "۰۱۲۳۴۵۶۷۸۹" }.take(18) }, "شناسهٔ پرداخت (اختیاری)",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        if (billId.isNotBlank()) {
            SoftButton("کپی شناسه‌ها برای پرداخت", 36.dp) {
                clipboard.setText(AnnotatedString(listOf(billId, payId).filter { it.isNotBlank() }.joinToString(" ")))
            }
        }
        LField(amountText, { amountText = groupedInput(it) }, "مبلغ این دوره (اختیاری)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        LRow("موعد پرداخت", "${JalaliDate.fromTimestamp(due).formatShort()} · ${dueLabel(due)}", divider = false, onClick = { pickDate = true })
        Text("هر چند ماه یک بار", style = lt(13.sp, FontWeight.Bold))
        LSegmented(listOf("ماهانه", "دوماهه", "سه‌ماهه"), (period - 1).coerceIn(0, 2), { period = it + 1 })
        Spacer(Modifier.height(4.dp))
    }
    if (pickDate) {
        JalaliDatePickerSheet(initial = due, onDismiss = { pickDate = false }, onPick = { due = it }, allowFuture = true, title = "موعد پرداخت", showTime = false)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PayBillSheet(bill: Bill, cards: List<BankCard>, onDismiss: () -> Unit, onPay: (Long, String) -> Unit) {
    var amountText by remember { mutableStateOf(bill.amount.takeIf { it > 0 }?.let { money(it) } ?: "") }
    var account by remember { mutableStateOf(bill.bankName.takeIf { n -> cards.any { it.bankName == n } } ?: cards.firstOrNull()?.bankName ?: "") }
    val amount = parseAmount(amountText)
    LayerSheet(
        title = "پرداخت ${bill.title.ifBlank { bill.kind }}",
        onDismiss = onDismiss,
        topGap = 120.dp,
        bottomBar = {
            BigButton("ثبت پرداخت", enabled = amount > 0 && account.isNotBlank()) { onPay(amount, account); onDismiss() }
        }
    ) {
        LField(amountText, { amountText = groupedInput(it) }, "مبلغ پرداخت‌شده (تومان)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Text("از حساب", style = lt(13.sp, FontWeight.Bold))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            cards.forEach { c -> LChip(c.bankName, c.bankName == account) { account = c.bankName } }
        }
        Text(
            "هزینه در دستهٔ «قبوض» ثبت می‌شود و موعد بعدی ${fa(bill.periodMonths)} ماه جلو می‌رود.",
            style = lt(12.sp, color = L.Muted, lineHeight = 20.sp)
        )
    }
}
