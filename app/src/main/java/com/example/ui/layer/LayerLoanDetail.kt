package com.example.ui.layer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BankCard
import com.example.data.Loan
import com.example.data.Transaction
import com.example.utils.JalaliDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** تاریخ سررسید بعد از [monthsAhead] ماه از [from]؛ روز بیش از طول ماه به آخر ماه می‌رود */
private fun dueAfter(dueDay: Int, from: Long, monthsAhead: Int): Long {
    val base = shiftJalaliMonth(from, monthsAhead)
    val d = JalaliDate.fromTimestamp(base)
    return JalaliDate.toTimestamp(d.year, d.month, dueDay.coerceAtMost(JalaliDate.daysInMonth(d.year, d.month)))
}

/**
 * جزئیات وام — طرح نهایی: باقی‌مانده، نوار قسط‌ها، سه آمار، یادآوری،
 * پرداخت‌های قبلی و دکمهٔ پرداخت.
 */
@Composable
fun LayerLoanDetailScreen(
    loan: Loan,
    transactions: List<Transaction>,
    payments: List<com.example.data.LoanPayment>,
    onBack: () -> Unit,
    onPay: () -> Unit,
    onToggleReminder: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val total = totalInstallments(loan)
    val remaining = remainingInstallments(loan)
    val paidCount = (total - remaining).coerceAtLeast(0)
    val left = (loan.totalAmount - loan.paidAmount).coerceAtLeast(0)
    val now = System.currentTimeMillis()
    val nextDue = JalaliDate.nextUnpaidDueTimestamp(
        loan.dueDay, payments.filter { it.loanId == loan.id }.map { it.monthKey }.toSet(), now
    )
    val finalDue = if (remaining > 0) dueAfter(loan.dueDay, nextDue, remaining - 1) else null
    var askDelete by remember { mutableStateOf(false) }

    val history = remember(transactions, loan) {
        transactions.filter { it.isExpense && it.category == "قسط" && it.title.contains(loan.loanName) }
            .sortedByDescending { it.date }.take(6)
    }

    LayerScreen(
        title = loan.loanName,
        subtitle = "${loan.bankName} · روز ${fa(loan.dueDay)} هر ماه",
        onBack = onBack,
        bottomBar = {
            if (!loan.isSettled) BigButton("پرداخت قسط ${fa(paidCount + 1)}", onClick = onPay)
            if (askDelete) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(L.RedSoft).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("این وام از تراز حذف شود؟ پرداخت‌هایی که ثبت کرده‌اید در تراکنش‌ها باقی می‌مانند.", style = lt(13.sp, color = L.RedInk, lineHeight = 22.sp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(L.RedText).clickable(onClick = onDelete), contentAlignment = Alignment.Center) {
                            Text("بله، حذف شود", style = lt(14.sp, FontWeight.Bold, Color.White))
                        }
                        Box(Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(L.Surface).clickable { askDelete = false }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                            Text("انصراف", style = lt(14.sp, FontWeight.SemiBold))
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).clickable { askDelete = true }, contentAlignment = Alignment.Center) {
                    Text("حذف این وام", style = lt(14.sp, FontWeight.SemiBold, L.RedText))
                }
            }
        }
    ) {
        LayerCard(radius = 24.dp, padding = 18.dp) {
            Text(if (loan.isSettled) "این وام تسویه شده است" else "باقی‌مانده", style = lt(13.sp, color = L.Muted))
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(money(left), style = lt(34.sp, FontWeight.Light, letterSpacing = (-1).sp))
                Text("تومان", style = lt(14.sp), modifier = Modifier.padding(bottom = 6.dp))
            }
            // نوار قسط‌ها
            val units = min(max(total, 1), 24)
            val doneUnits = if (total > 0) (paidCount.toFloat() / total * units).roundToInt().coerceIn(0, units) else 0
            Row(
                Modifier.padding(top = 14.dp).fillMaxWidth().semantics { contentDescription = "${fa(paidCount)} قسط از ${fa(total)} قسط پرداخت شده" },
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                for (i in 0 until units) {
                    val c = when {
                        i < doneUnits -> L.Primary
                        i == doneUnits && !loan.isSettled -> L.AmberText
                        else -> L.Rail
                    }
                    Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(3.dp)).background(c))
                }
            }
            Row(Modifier.padding(top = 10.dp).fillMaxWidth()) {
                Text("${fa(paidCount)} از ${fa(total)} قسط پرداخت شده", style = lt(12.sp, color = L.Muted), modifier = Modifier.weight(1f))
                if (finalDue != null) {
                    val d = JalaliDate.fromTimestamp(finalDue)
                    Text("تسویه: ${d.formatMonthYear()}", style = lt(12.sp, color = L.Muted))
                }
            }
        }

        Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("مبلغ کل", millions(loan.totalAmount), Modifier.weight(1f))
            StatTile("قسط ماهانه", millions(loan.installmentAmount), Modifier.weight(1f))
            StatTile(
                "قسط بعدی",
                if (loan.isSettled) "—" else JalaliDate.fromTimestamp(nextDue).formatShort(),
                Modifier.weight(1f), valueColor = if (loan.isSettled) L.Ink else L.AmberText
            )
        }

        LayerCard(radius = 18.dp, padding = 16.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("یادآوری قسط", style = lt(14.sp, FontWeight.SemiBold))
                    Text("یک روز قبل از سررسید", style = lt(12.sp, color = L.Muted))
                }
                LSwitch(loan.reminderEnabled, "یادآوری قسط", onChange = onToggleReminder)
            }
        }

        SectionTitle("سابقهٔ اقساط")
        LayerCard(radius = 18.dp, padding = 0.dp) {
            val mine = payments.filter { it.loanId == loan.id }.sortedByDescending { it.monthKey * 10_000_000_000L + it.date / 1000 }
            if (mine.isEmpty() && history.isEmpty()) {
                Text("هنوز پرداختی از طریق تراز ثبت نشده است.", style = lt(13.sp, color = L.Muted), modifier = Modifier.padding(16.dp))
            } else if (mine.isEmpty()) {
                // Payments from before the history table existed: show the transactions.
                history.forEachIndexed { i, tx ->
                    PaymentRow("قسط ${fa(max(paidCount - i, 1))} · ${JalaliDate.fromTimestamp(tx.date).formatShort()}", "از ${tx.bankName}", tx.amount, late = false)
                    if (i < history.size - 1) Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(L.Line))
                }
            } else {
                mine.forEachIndexed { i, p ->
                    val due = JalaliDate.dueTimestampFor(p.monthKey, loan.dueDay)
                    val late = JalaliDate.daysUntil(p.date, due) > 0
                    val paidOn = JalaliDate.fromTimestamp(p.date).formatShort()
                    val monthName = JalaliDate.MONTH_NAMES[p.jalaliMonth - 1]
                    PaymentRow(
                        "قسط $monthName ${fa(p.jalaliYear)}",
                        if (late) "پرداخت با تأخیر · $paidOn" else "پرداخت شده · $paidOn",
                        p.amount,
                        late
                    )
                    if (i < mine.size - 1) Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(L.Line))
                }
            }
        }
    }
}

@Composable
private fun PaymentRow(title: String, sub: String, amount: Long, late: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(if (late) L.AmberSoft else L.GreenSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Check, null, tint = if (late) L.AmberInk else L.Green, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = lt(14.sp, FontWeight.Medium))
            Text(sub, style = lt(12.sp, color = if (late) L.AmberInk else L.Muted))
        }
        Text(money(amount), style = lt(14.sp, FontWeight.SemiBold))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier, valueColor: Color = L.Ink) {
    Column(modifier.cardSurface(16.dp).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = lt(11.sp, color = L.Muted))
        FitText(value, lt(15.sp, FontWeight.Bold, valueColor))
    }
}

/**
 * وام جدید — صفحهٔ کامل طرح نهایی با خلاصهٔ زنده «چند قسط مانده، کی تمام می‌شود».
 */
@Composable
fun LayerAddLoanScreen(
    cards: List<BankCard>,
    onBack: () -> Unit,
    onConfirm: (bankName: String, loanName: String, total: Long, paid: Long, installment: Long, dueDay: Int, reminder: Boolean, kind: String) -> Unit
) {
    var purchase by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var bank by remember { mutableStateOf(cards.firstOrNull { !it.accountType.equals("CASH") }?.bankName.orEmpty()) }
    var totalText by remember { mutableStateOf("") }
    var instText by remember { mutableStateOf("") }
    var paidText by remember { mutableStateOf("") }
    var day by remember { mutableIntStateOf(JalaliDate.today().day.coerceIn(1, 31)) }
    var reminder by remember { mutableStateOf(true) }

    val total = parseAmount(totalText)
    val inst = parseAmount(instText)
    val paid = parseAmount(paidText)
    val valid = name.isNotBlank() && bank.isNotBlank() && total > 0 && inst > 0 && paid <= total
    val remainCount = if (inst > 0 && total > paid) ((total - paid + inst - 1) / inst).toInt() else 0

    LayerScreen(
        title = if (purchase) "خرید اقساطی" else "وام جدید",
        subtitle = if (purchase) "گوشی، لوازم خانگی یا هر خریدی که به‌صورت اقساطی پرداخت می‌شود" else "مشخصات قرارداد وام را وارد کنید",
        onBack = onBack,
        bottomBar = {
            BigButton(if (purchase) "ثبت خرید اقساطی" else "ثبت وام", enabled = valid) {
                onConfirm(bank.trim(), name.trim(), total, paid, inst, day, reminder,
                    if (purchase) com.example.data.LoanKind.PURCHASE else com.example.data.LoanKind.LOAN)
            }
        }
    ) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LSegmented(listOf("وام بانکی", "خرید اقساطی"), if (purchase) 1 else 0, { purchase = it == 1 })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LField(name, { name = it }, if (purchase) "شرح خرید" else "نام وام", Modifier.weight(1f), placeholder = if (purchase) "مثلاً یخچال" else "مثلاً وام خودرو")
                LField(bank, { bank = it }, if (purchase) "فروشنده" else "بانک", Modifier.weight(1f), placeholder = if (purchase) "مثلاً دیجی‌کالا" else "مثلاً ملت")
            }
            if (cards.isNotEmpty() && !purchase) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    cards.filter { it.accountType != "CASH" }.take(4).forEach { c ->
                        LChip(c.bankName, bank == c.bankName) { bank = c.bankName }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LField(totalText, { totalText = groupedInput(it) }, if (purchase) "مبلغ کل اقساط" else "مبلغ کل وام", Modifier.weight(1f), placeholder = "۰",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                LField(instText, { instText = groupedInput(it) }, "مبلغ هر قسط", Modifier.weight(1f), placeholder = "۰",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            LField(
                paidText, { paidText = groupedInput(it) }, "مبلغ پرداخت‌شده تاکنون (تومان)", placeholder = "۰",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                hint = if (paid > total && total > 0) "مبلغ پرداخت‌شده از کل وام بیشتر است." else null
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("روز سررسید در هر ماه", style = lt(13.sp, FontWeight.SemiBold))
                Row(
                    Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp)).background(L.Surface)
                        .border1().padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StepperButton(Icons.Default.Add, "یک روز بعد") { day = if (day >= 31) 1 else day + 1 }
                    Text("روز ${fa(day)} ماه", style = lt(17.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    StepperButton(Icons.Default.Remove, "یک روز قبل") { day = if (day <= 1) 31 else day - 1 }
                }
            }

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(L.Surface).border1().padding(horizontal = 14.dp).height(60.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("یادآوری یک روز پیش از سررسید", style = lt(14.sp, FontWeight.SemiBold))
                    Text("ساعت ۱۰ صبح", style = lt(12.sp, color = L.Muted))
                }
                LSwitch(reminder, "یادآوری یک روز پیش از سررسید") { reminder = it }
            }

            if (remainCount > 0) {
                val next = JalaliDate.nextDueTimestamp(day)
                val last = dueAfter(day, next, remainCount - 1)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(L.PrimarySoft).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("خلاصه", style = lt(12.sp, FontWeight.Bold, L.PrimaryText))
                    Text(
                        "${fa(remainCount)} قسط ${money(inst)} تومانی باقی مانده است. قسط بعدی ${JalaliDate.fromTimestamp(next).formatShort()} و آخرین قسط ${JalaliDate.fromTimestamp(last).formatMonthYear()}.",
                        style = lt(13.sp, color = L.Info, lineHeight = 24.sp)
                    )
                }
            }
        }
    }
}

private fun Modifier.border1(): Modifier =
    this.then(Modifier.border(1.dp, L.Rail, RoundedCornerShape(14.dp)))

@Composable
private fun StepperButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(L.PrimarySoft).clickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = L.PrimaryText, modifier = Modifier.size(20.dp)) }
}
