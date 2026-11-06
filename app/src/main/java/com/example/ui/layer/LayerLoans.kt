package com.example.ui.layer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BankCard
import com.example.data.Loan
import com.example.data.isPurchase
import com.example.utils.JalaliDate

internal fun remainingInstallments(loan: Loan): Int {
    if (loan.installmentAmount <= 0) return 0
    val left = (loan.totalAmount - loan.paidAmount).coerceAtLeast(0)
    return ((left + loan.installmentAmount - 1) / loan.installmentAmount).toInt()
}

internal fun totalInstallments(loan: Loan): Int =
    if (loan.installmentAmount <= 0) 0
    else ((loan.totalAmount + loan.installmentAmount - 1) / loan.installmentAmount).toInt()

@Composable
internal fun ColumnScope.LoansSection(
    loans: List<Loan>,
    onAddLoan: () -> Unit,
    onPay: (Loan) -> Unit,
    onOpenLoan: (Loan) -> Unit
) {

    val now = System.currentTimeMillis()
    val active = loans.filter { !it.isSettled }
    val withDue = active.map { it to JalaliDate.nextDueTimestamp(it.dueDay, now) }.sortedBy { it.second }
    val today = JalaliDate.today()

    var monthShift by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<Int?>(null) }

        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("وام‌ها و خریدهای قسطی", style = lt(17.sp, FontWeight.Bold))
                Text(
                    if (active.isEmpty()) "قسط فعالی ندارید"
                    else "${fa(active.size)} مورد فعال · قسط ${today.monthName} ${money(active.sumOf { it.installmentAmount })} تومان",
                    style = lt(12.sp, color = L.Muted)
                )
            }
            Row(
                Modifier.height(38.dp).clip(ButtonShape).background(L.PrimarySoft).clickable(onClick = onAddLoan).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, null, tint = L.PrimaryText, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("وام جدید", style = lt(13.sp, FontWeight.Bold, L.PrimaryText))
            }
        }

        // قسط بعدی
        withDue.firstOrNull()?.let { (loan, due) ->
            val days = JalaliDate.daysUntil(due, now)
            Column(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp).fillMaxWidth()
                    .shadow(6.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x140E1116), spotColor = Color(0x140E1116))
                    .clip(RoundedCornerShape(22.dp)).background(L.Surface).padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("قسط بعدی · ${daysLabel(days)}", style = lt(12.sp, FontWeight.Bold, L.AmberText))
                        Text((if (loan.isPurchase) "خرید قسطی · " else "") + "${loan.loanName} · ${loan.bankName}", style = lt(15.sp, FontWeight.SemiBold))
                        Text("سررسید ${weekdayName(due)} ${JalaliDate.fromTimestamp(due).formatShort()}", style = lt(12.sp, color = L.Muted))
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(money(loan.installmentAmount), style = lt(24.sp, letterSpacing = (-0.8).sp))
                        Spacer(Modifier.width(4.dp))
                        Text("تومان", style = lt(12.sp), modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
                PrimaryButton("پرداخت این قسط", Modifier.fillMaxWidth().height(50.dp)) { onPay(loan) }
            }
        }

        // تقویم سررسیدها
        val baseMonthIndex = today.year * 12 + (today.month - 1) + monthShift
        val year = baseMonthIndex / 12
        val month = baseMonthIndex % 12 + 1
        val dim = JalaliDate.daysInMonth(year, month)
        val firstCol = saturdayFirstIndex(JalaliDate.toTimestamp(year, month, 1))
        val dueByDay: Map<Int, List<Loan>> = active.groupBy { it.dueDay.coerceIn(1, dim) }
        val firstDueLoanId = withDue.firstOrNull()?.first?.id

        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp).fillMaxWidth().cardSurface(22.dp).padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 14.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("تقویم سررسیدها", style = lt(15.sp, FontWeight.Bold), modifier = Modifier.weight(1f))
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { monthShift -= 1; picked = null }.semantics { contentDescription = "ماه قبل" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = L.Muted)
                }
                Text("${JalaliDate.MONTH_NAMES[month - 1]} ${fa(year)}", style = lt(13.sp, FontWeight.SemiBold))
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { monthShift += 1; picked = null }.semantics { contentDescription = "ماه بعد" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = L.Muted)
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEach {
                    Text(it, style = lt(11.sp, color = L.Muted), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            val cells = firstCol + dim
            val rows = (cells + 6) / 7
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (cIdx in 0 until 7) {
                        val day = r * 7 + cIdx - firstCol + 1
                        Box(Modifier.weight(1f).height(36.dp), contentAlignment = Alignment.Center) {
                            if (day in 1..dim) {
                                val isToday = monthShift == 0 && day == today.day
                                val past = monthShift < 0 || (monthShift == 0 && day < today.day)
                                val dues = dueByDay[day]
                                val isFirst = dues?.any { it.id == firstDueLoanId } == true && monthShift == 0
                                val bg = when { isToday -> L.Primary; isFirst -> L.AmberText; else -> Color.Transparent }
                                val fg = when { isToday || isFirst -> Color.White; past -> L.Faint; else -> L.Ink }
                                Box(
                                    Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(bg)
                                        .then(
                                            if (dues != null && !isFirst) Modifier.border(1.5.dp, if (picked == day) L.Primary else L.Ink, CircleShape)
                                            else if (picked == day) Modifier.border(1.5.dp, L.Ink, CircleShape) else Modifier
                                        )
                                        .then(if (dues != null) Modifier.clickable { picked = day } else Modifier)
                                        .semantics { contentDescription = "${fa(day)} ${JalaliDate.MONTH_NAMES[month - 1]}" + if (dues != null) "، سررسید قسط" else "" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(fa(day), style = lt(13.sp, if (dues != null || isToday) FontWeight.Bold else FontWeight.Normal, fg))
                                }
                            }
                        }
                    }
                }
            }
            if (active.isNotEmpty()) {
                Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    withDue.take(3).forEachIndexed { i, (loan, _) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(10.dp).clip(CircleShape)
                                    .then(if (i == 0) Modifier.background(L.AmberText) else Modifier.border(1.5.dp, L.Ink, CircleShape))
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("${loan.loanName} ${fa(loan.dueDay)}", style = lt(11.sp, color = L.Muted), maxLines = 1)
                        }
                    }
                }
            }
            val pickedLoans = picked?.let { dueByDay[it] }
            Box(Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp).fillMaxWidth().clip(ButtonShape).background(L.Bg).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    if (pickedLoans != null) pickedLoans.joinToString("\n") {
                        "${fa(picked!!)} ${JalaliDate.MONTH_NAMES[month - 1]}: قسط ${it.loanName} · ${money(it.installmentAmount)} تومان · ${it.bankName}"
                    } else if (active.isEmpty()) "وام فعالی ثبت نشده است."
                    else "روی روزهای رنگی بزنید تا جزئیات قسط آن روز را ببینید.",
                    style = lt(12.sp, if (pickedLoans != null) FontWeight.SemiBold else FontWeight.Normal, if (pickedLoans != null) L.Ink else L.Muted, lineHeight = 20.sp)
                )
            }
        }

        // همهٔ وام‌ها
        if (loans.isNotEmpty()) {
            Text("همهٔ وام‌ها", style = lt(17.sp, FontWeight.Bold), modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp))
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                loans.sortedBy { it.isSettled }.forEach { loan ->
                    val progress = if (loan.totalAmount > 0) (loan.paidAmount.toFloat() / loan.totalAmount).coerceIn(0f, 1f) else 0f
                    Column(Modifier.fillMaxWidth().cardSurface().clickable { onOpenLoan(loan) }.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(loan.loanName, style = lt(15.sp, FontWeight.SemiBold))
                                Text(
                                    if (loan.isSettled) "${loan.bankName} · تسویه شده"
                                    else "${loan.bankName} · ${money(loan.installmentAmount)} در ماه · روز ${fa(loan.dueDay)}",
                                    style = lt(12.sp, color = L.Muted)
                                )
                            }
                            if (!loan.isSettled) SoftButton("پرداخت", height = 36.dp) { onPay(loan) }
                        }
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(PillShape).background(L.Line)) {
                            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(PillShape).background(if (loan.isSettled) L.Green else L.Primary))
                        }
                        Row {
                            val total = totalInstallments(loan)
                            Text(
                                if (total > 0) "${fa(total - remainingInstallments(loan))} از ${fa(total)} قسط" else "",
                                style = lt(12.sp, color = L.Muted), modifier = Modifier.weight(1f)
                            )
                            Text("مانده ${money((loan.totalAmount - loan.paidAmount).coerceAtLeast(0))}", style = lt(12.sp, color = L.Muted))
                        }
                    }
                }
            }
        }
}

/**
 * شیت تأیید پرداخت قسط: حساب مبدأ، پیش‌نمایش «بعد از تأیید» و حالت موفق.
 */
@Composable
fun LayerPaySheet(
    loan: Loan,
    cards: List<BankCard>,
    onConfirm: (loan: Loan, fromBank: String) -> Unit,
    onDismiss: () -> Unit,
    onGoHome: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    var done by remember { mutableStateOf(false) }
    var from by remember { mutableStateOf(cards.firstOrNull { it.bankName == loan.bankName }?.bankName ?: cards.firstOrNull()?.bankName ?: loan.bankName) }
    var pickOpen by remember { mutableStateOf(false) }
    val fromCard = cards.firstOrNull { it.bankName == from }
    val remainingBefore = remainingInstallments(loan)
    // آخرین قسط ممکن است کمتر از قسط عادی باشد؛ همان باقی‌مانده پرداخت می‌شود
    val payAmount = minOf(loan.installmentAmount, (loan.totalAmount - loan.paidAmount).coerceAtLeast(0))
    val total = totalInstallments(loan)
    val nextDue = JalaliDate.nextDueTimestamp(loan.dueDay, System.currentTimeMillis() + DAY_MS * 2)

    Box(Modifier.fillMaxSize().background(Color(0x990E1116)).pointerInput(Unit) { detectTapGestures { onDismiss() } }) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(L.Surface)
                .pointerInput(Unit) { detectTapGestures { } }
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp)
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(5.dp).clip(PillShape).background(L.Handle))
            if (!done) {
                Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("تأیید پرداخت قسط", style = lt(20.sp, FontWeight.Bold))
                    Text("جزئیات را بررسی کنید و بعد تأیید بزنید.", style = lt(13.sp, color = L.Muted))
                }
                Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("قسط ${loan.loanName} · ${loan.bankName}", style = lt(12.sp, color = L.Muted))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(money(payAmount), style = lt(42.sp, letterSpacing = (-1.5).sp))
                        Spacer(Modifier.width(6.dp))
                        Text("تومان", style = lt(15.sp), modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
                Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(1.dp).background(L.Line))
                Box {
                    Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("از حساب", style = lt(14.sp, color = L.Muted), modifier = Modifier.weight(1f))
                        AccountSwatch(from, cards, 24.dp, 16.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(fromCard?.let { shortAccountLabel(it) } ?: from, style = lt(14.sp, FontWeight.SemiBold))
                        Spacer(Modifier.width(10.dp))
                        if (cards.size > 1) SoftButton("تغییر", height = 32.dp) { pickOpen = true }
                    }
                    DropdownMenu(expanded = pickOpen, onDismissRequest = { pickOpen = false }, containerColor = L.Surface) {
                        cards.forEach { c ->
                            DropdownMenuItem(text = { Text("${shortAccountLabel(c)} · ${millions(c.balance)}", style = lt(14.sp)) }, onClick = { from = c.bankName; pickOpen = false })
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(L.Line))
                Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("زمان ثبت", style = lt(14.sp, color = L.Muted), modifier = Modifier.weight(1f))
                    Text("امروز، ${timeOfDay(System.currentTimeMillis())}", style = lt(14.sp, FontWeight.SemiBold))
                }

                Column(Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(L.Bg).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("بعد از تأیید", style = lt(12.sp, FontWeight.Bold, L.Muted))
                    if (fromCard != null) BeforeAfter("موجودی ${fromCard.bankName}", money(fromCard.balance), money(fromCard.balance - payAmount))
                    BeforeAfter("اقساط باقی‌مانده", "${fa(remainingBefore)} قسط", "${fa((remainingBefore - 1).coerceAtLeast(0))} قسط")
                    Row { Text("خط زمان", style = lt(13.sp), modifier = Modifier.weight(1f)); Text("در امروز ${timeOfDay(System.currentTimeMillis())} نشان داده می‌شود", style = lt(13.sp)) }
                }
                Row(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(L.PrimarySoft).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Icon(Icons.Default.Info, null, tint = L.PrimaryText, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("تراز فقط این پرداخت را در حساب‌های شما ثبت می‌کند؛ پولی از کارت بانکی برداشت نمی‌شود.", style = lt(12.sp, color = L.Info, lineHeight = 21.sp))
                }
                Spacer(Modifier.weight(1f))
                PrimaryButton("تأیید و ثبت پرداخت", Modifier.fillMaxWidth().height(54.dp)) {
                    onConfirm(loan, from)
                    done = true
                }
                Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Text("انصراف", style = lt(15.sp, FontWeight.SemiBold, L.PrimaryText))
                }
            } else {
                Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.size(76.dp).clip(CircleShape).background(L.GreenSoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, null, tint = L.Green, modifier = Modifier.size(38.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("پرداخت ثبت شد", style = lt(22.sp, FontWeight.Bold))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        (if (total > 0) "قسط ${fa(total - remainingBefore + 1)} از ${fa(total)} ${loan.loanName}\n" else "") +
                            "${money(payAmount)} تومان از ${fromCard?.let { shortAccountLabel(it) } ?: from}",
                        style = lt(14.sp, color = L.Muted, lineHeight = 25.sp), textAlign = TextAlign.Center
                    )
                    if (remainingBefore > 1) {
                        Spacer(Modifier.height(4.dp))
                        Text("قسط بعدی: ${JalaliDate.fromTimestamp(nextDue).formatShort()}", style = lt(13.sp, color = L.Muted))
                    }
                }
                PrimaryButton("دیدن در خط زمان امروز", Modifier.fillMaxWidth().height(54.dp), onClick = onGoHome)
                Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(50.dp).clip(RoundedCornerShape(16.dp)).background(L.PrimarySoft).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Text("بازگشت به اقساط", style = lt(15.sp, FontWeight.Bold, L.PrimaryText))
                }
            }
        }
    }
}

@Composable
private fun BeforeAfter(label: String, before: String, after: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = lt(13.sp), modifier = Modifier.weight(1f))
        Text(before, style = lt(13.sp, color = L.Muted))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "به", tint = L.Ink, modifier = Modifier.padding(horizontal = 6.dp).size(16.dp))
        Text(after, style = lt(13.sp, FontWeight.Bold))
    }
}
