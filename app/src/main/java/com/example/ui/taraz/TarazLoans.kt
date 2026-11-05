package com.example.ui.taraz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BankCard
import com.example.data.Loan
import com.example.ui.FinanceViewModel
import com.example.utils.JalaliDate
import java.util.Calendar

/** Instalments still to pay, rounded up: 36M left at 5M a month is 8 payments. */
fun Loan.remainingInstallments(): Int =
    if (installmentAmount <= 0) 0 else ((totalAmount - paidAmount).coerceAtLeast(0) + installmentAmount - 1).div(installmentAmount).toInt()

fun Loan.totalInstallments(): Int =
    if (installmentAmount <= 0) 0 else ((totalAmount + installmentAmount - 1) / installmentAmount).toInt()

fun Loan.paidInstallments(): Int = (totalInstallments() - remainingInstallments()).coerceAtLeast(0)

@Composable
fun TarazLoansScreen(
    vm: FinanceViewModel,
    onPay: (Loan) -> Unit
) {
    val loans by vm.loans.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var monthOffset by remember { mutableIntStateOf(0) }
    var pickedDay by remember { mutableStateOf<Int?>(null) }

    val now = System.currentTimeMillis()
    val active = loans.filter { !it.isSettled }
    val next = active.map { it to JalaliDate.nextDueTimestamp(it.dueDay, now) }.minByOrNull { it.second }
    val monthlyTotal = active.sumOf { it.installmentAmount }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(Tz.Ground)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = BottomBarClearance)
    ) {
        item {
            TzTopBar(
                title = "اقساط",
                onBack = null,
                subtitle = if (active.isEmpty()) "وام فعالی ندارید" else "${TzFormat.fa(active.size)} وام فعال · قسط ماهانه ${TzFormat.compact(monthlyTotal)}"
            ) {
                SecondaryButton("وام جدید", onClick = { showAdd = true }, fill = false, height = 40.dp, fontSize = 14.sp, icon = Icons.Filled.Add)
            }
        }

        if (loans.isEmpty()) {
            item {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    EmptyNote("هنوز وامی ثبت نکرده‌اید", "مشخصات وام را وارد کنید تا سررسید هر قسط را یادآوری کنیم و پیشرفت بازپرداخت را ببینید.")
                    PrimaryButton("ثبت اولین وام", onClick = { showAdd = true }, icon = Icons.Filled.Add)
                }
            }
        }

        if (next != null) {
            item {
                val (loan, due) = next
                val days = JalaliDate.daysUntil(due, now)
                TzCard(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp), padding = PaddingValues(18.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "قسط بعدی · " + when (days) {
                                    0 -> "امروز"
                                    1 -> "فردا"
                                    else -> TzFormat.fa(days) + " روز دیگر"
                                },
                                style = tzText(12.sp, FontWeight.Bold, if (days <= 3) Tz.Amber else Tz.Muted)
                            )
                            Text("${loan.loanName} · ${loan.bankName}", style = tzText(15.sp, FontWeight.SemiBold))
                            Text("سررسید ${TzFormat.weekday(due)} ${JalaliDate.fromTimestamp(due).formatShort()}", style = tzText(12.sp, color = Tz.Muted))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(TzFormat.money(loan.installmentAmount), style = tzText(26.sp, FontWeight.Light, letterSpacing = (-0.5).sp))
                            Text("تومان", style = tzText(12.sp, color = Tz.Muted))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("پرداخت این قسط", onClick = { onPay(loan) }, height = 50.dp)
                }
            }
        }

        if (active.isNotEmpty()) {
            item {
                DueCalendar(
                    loans = active,
                    monthOffset = monthOffset,
                    onMonthChange = {
                        monthOffset = it
                        pickedDay = null
                    },
                    pickedDay = pickedDay,
                    onPick = { pickedDay = it },
                    now = now
                )
            }
        }

        if (loans.isNotEmpty()) {
            item {
                Spacer(Modifier.height(20.dp))
                SectionTitle("همهٔ وام‌ها", subtitle = "روی هر وام بزنید تا قسطش را پرداخت کنید")
                Spacer(Modifier.height(8.dp))
            }
            item {
                TzCard(Modifier.padding(horizontal = 20.dp), padding = PaddingValues(horizontal = 16.dp)) {
                    loans.sortedBy { it.isSettled }.forEachIndexed { index, loan ->
                        if (index > 0) Hairline()
                        LoanRow(loan, onClick = { if (!loan.isSettled) onPay(loan) })
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddLoanSheet(
            onDismiss = { showAdd = false },
            onSave = { bank, name, total, paid, inst, day ->
                vm.addLoan(bank, name, total, paid, inst, day)
                showAdd = false
            }
        )
    }
}

@Composable
private fun LoanRow(loan: Loan, onClick: () -> Unit) {
    val fraction = if (loan.totalAmount > 0) loan.paidAmount.toFloat() / loan.totalAmount else 0f
    Column(
        Modifier
            .fillMaxWidth()
            .tzClick(onClick)
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(loan.loanName, style = tzText(15.sp, FontWeight.SemiBold))
                Text(
                    "${loan.bankName} · ${TzFormat.money(loan.installmentAmount)} تومان، ${TzFormat.fa(loan.dueDay)} هر ماه",
                    style = tzText(12.sp, color = Tz.Muted)
                )
            }
            if (loan.isSettled) TzTag("تسویه شده", Tz.GreenTint, Tz.GreenText)
            else if (!loan.reminderEnabled) TzTag("یادآور خاموش", Tz.Field, Tz.Muted)
        }
        TzProgress(fraction, height = 6.dp, color = if (loan.isSettled) Tz.Green else Tz.Cobalt)
        Text(
            "${TzFormat.fa(loan.paidInstallments())} از ${TzFormat.fa(loan.totalInstallments())} قسط پرداخت شده · " +
                if (loan.isSettled) "تسویه کامل" else "${TzFormat.compact(loan.totalAmount - loan.paidAmount)} مانده",
            style = tzText(12.sp, color = Tz.Muted)
        )
    }
}

@Composable
private fun DueCalendar(
    loans: List<Loan>,
    monthOffset: Int,
    onMonthChange: (Int) -> Unit,
    pickedDay: Int?,
    onPick: (Int) -> Unit,
    now: Long
) {
    val today = JalaliDate.fromTimestamp(now)
    var y = today.year
    var m = today.month + monthOffset
    while (m > 12) { m -= 12; y += 1 }
    while (m < 1) { m += 12; y -= 1 }
    val daysInMonth = JalaliDate.daysInMonth(y, m)
    val firstDow = Calendar.getInstance().apply { timeInMillis = JalaliDate.toTimestamp(y, m, 1) }.get(Calendar.DAY_OF_WEEK)
    val firstCol = firstDow % 7                     // Saturday = 0 … Friday = 6
    val dues: Map<Int, List<Loan>> = loans.groupBy { it.dueDay.coerceIn(1, daysInMonth) }
    val isThisMonth = monthOffset == 0

    TzCard(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp), padding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("تقویم سررسیدها", style = tzText(15.sp, FontWeight.Bold), modifier = Modifier.weight(1f).padding(start = 4.dp))
            Box(
                Modifier.size(40.dp).clip(CircleShape).tzClick { onMonthChange(monthOffset - 1) }.semantics { contentDescription = "ماه قبل" },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = Tz.Muted) }
            Text("${JalaliDate.MONTH_NAMES[m - 1]} ${TzFormat.fa(y)}", style = tzText(13.sp, FontWeight.SemiBold))
            Box(
                Modifier.size(40.dp).clip(CircleShape).tzClick { onMonthChange(monthOffset + 1) }.semantics { contentDescription = "ماه بعد" },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Tz.Muted) }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEach {
                Text(it, style = tzText(11.sp, color = Tz.Muted, align = TextAlign.Center), modifier = Modifier.weight(1f))
            }
        }
        val cells = firstCol + daysInMonth
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val day = r * 7 + c - firstCol + 1
                    Box(Modifier.weight(1f).height(38.dp), contentAlignment = Alignment.Center) {
                        if (day in 1..daysInMonth) {
                            val due = dues[day]
                            val isToday = isThisMonth && day == today.day
                            val past = isThisMonth && day < today.day
                            val soon = due != null && isThisMonth && day >= today.day && day - today.day <= 3
                            val bg = when {
                                isToday -> Tz.Cobalt
                                soon -> Tz.Amber
                                else -> Color.Transparent
                            }
                            val fg = when {
                                isToday || soon -> Color.White
                                past -> Tz.Faint
                                else -> Tz.Ink
                            }
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(bg)
                                    .then(
                                        if (due != null && !soon && !isToday) Modifier.border(1.5.dp, Tz.Ink, CircleShape)
                                        else if (pickedDay == day) Modifier.border(2.dp, Tz.Ink, CircleShape) else Modifier
                                    )
                                    .then(if (due != null) Modifier.tzClick { onPick(day) } else Modifier),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(TzFormat.fa(day), style = tzText(13.sp, if (due != null || isToday) FontWeight.Bold else FontWeight.Normal, fg))
                            }
                        }
                    }
                }
            }
        }
        val picked = pickedDay?.let { dues[it] }
        Box(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Tz.Ground)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                if (picked == null) "روی روزهای علامت‌دار بزنید تا قسط آن روز را ببینید."
                else "${TzFormat.fa(pickedDay)} ${JalaliDate.MONTH_NAMES[m - 1]}: " + picked.joinToString("، ") {
                    "${it.loanName} · ${TzFormat.money(it.installmentAmount)} تومان"
                },
                style = tzText(12.sp, if (picked == null) FontWeight.Normal else FontWeight.SemiBold, if (picked == null) Tz.Muted else Tz.Ink, lineHeight = 20.sp)
            )
        }
    }
}

// ------------------------------------------------------------------ pay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayInstallmentSheet(
    loan: Loan,
    cards: List<BankCard>,
    onDismiss: () -> Unit,
    onConfirm: (accountName: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var account by remember {
        mutableStateOf(cards.firstOrNull { it.bankName == loan.bankName }?.bankName ?: cards.firstOrNull()?.bankName)
    }
    var done by remember { mutableStateOf(false) }
    val card = cards.firstOrNull { it.bankName == account }
    val remaining = loan.remainingInstallments()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Tz.Surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
        ) {
            if (!done) {
                Text("تأیید پرداخت قسط", style = tzText(20.sp, FontWeight.Bold))
                Text("جزئیات را بررسی کنید و بعد تأیید بزنید.", style = tzText(13.sp, color = Tz.Muted))
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("قسط ${loan.loanName} · ${loan.bankName}", style = tzText(12.sp, color = Tz.Muted))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(TzFormat.money(loan.installmentAmount), style = tzText(44.sp, FontWeight.ExtraLight, letterSpacing = (-1.5).sp))
                        Spacer(Modifier.width(6.dp))
                        Text("تومان", style = tzText(15.sp), modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
                Hairline()
                if (cards.isNotEmpty()) {
                    AccountPicker(cards, account, "از حساب", { account = it }, {}, Modifier.fillMaxWidth().padding(vertical = 4.dp), filled = false)
                } else {
                    Text("حسابی ندارید؛ پرداخت فقط در وام ثبت می‌شود.", style = tzText(13.sp, color = Tz.Muted), modifier = Modifier.padding(vertical = 14.dp))
                }
                Hairline()
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                    Text("زمان ثبت", style = tzText(14.sp, color = Tz.Muted), modifier = Modifier.weight(1f))
                    Text("امروز، " + TzFormat.time(System.currentTimeMillis()), style = tzText(14.sp, FontWeight.SemiBold))
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Tz.Ground)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("بعد از تأیید", style = tzText(12.sp, FontWeight.Bold, Tz.Muted))
                    if (card != null) {
                        BeforeAfter("موجودی ${card.bankName}", TzFormat.money(card.balance), TzFormat.money(card.balance - loan.installmentAmount))
                    }
                    BeforeAfter("اقساط باقی‌مانده", "${TzFormat.fa(remaining)} قسط", "${TzFormat.fa((remaining - 1).coerceAtLeast(0))} قسط")
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Tz.CobaltTint)
                        .padding(12.dp)
                ) {
                    Icon(Icons.Filled.Info, null, tint = Tz.CobaltText, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "تراز فقط این پرداخت را در حساب‌های شما ثبت می‌کند؛ پولی از کارت بانکی برداشت نمی‌شود.",
                        style = tzText(12.sp, color = Color(0xFF1E2B5C), lineHeight = 21.sp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton("تأیید و ثبت پرداخت", onClick = {
                    onConfirm(account ?: loan.bankName)
                    done = true
                })
                TextAction("انصراف", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            } else {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(Modifier.size(76.dp).clip(CircleShape).background(Tz.GreenTint), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Check, null, tint = Tz.Green, modifier = Modifier.size(38.dp))
                    }
                    Text("پرداخت ثبت شد", style = tzText(22.sp, FontWeight.Bold))
                    Text(
                        "قسط ${TzFormat.fa(loan.paidInstallments() + 1)} از ${TzFormat.fa(loan.totalInstallments())} ${loan.loanName}\n" +
                            "${TzFormat.money(loan.installmentAmount)} تومان از ${account ?: loan.bankName}",
                        style = tzText(14.sp, color = Tz.Muted, align = TextAlign.Center, lineHeight = 24.sp)
                    )
                }
                PrimaryButton("بستن", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun BeforeAfter(label: String, before: String, after: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = tzText(13.sp), modifier = Modifier.weight(1f))
        Text(before, style = tzText(13.sp, color = Tz.Muted))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "به", tint = Tz.Muted, modifier = Modifier.size(18.dp))
        Text(after, style = tzText(13.sp, FontWeight.Bold))
    }
}

// ------------------------------------------------------------------ add loan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLoanSheet(
    onDismiss: () -> Unit,
    onSave: (bank: String, name: String, total: Long, paid: Long, installment: Long, dueDay: Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var bank by remember { mutableStateOf("") }
    var total by remember { mutableStateOf("") }
    var installment by remember { mutableStateOf("") }
    var paid by remember { mutableStateOf("") }
    var day by remember { mutableIntStateOf(JalaliDate.today().day) }

    val totalV = total.toLongOrNull() ?: 0L
    val instV = installment.toLongOrNull() ?: 0L
    val paidV = paid.toLongOrNull() ?: 0L
    val problem = when {
        name.isBlank() -> "نام وام را بنویسید"
        bank.isBlank() -> "نام بانک را بنویسید"
        totalV <= 0 -> "مبلغ کل وام را وارد کنید"
        instV <= 0 -> "مبلغ هر قسط را وارد کنید"
        paidV > totalV -> "پرداخت‌شده از کل وام بیشتر است"
        else -> null
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Tz.Surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("وام جدید", style = tzText(20.sp, FontWeight.Bold))
            Text("مشخصات قرارداد وام را وارد کنید.", style = tzText(13.sp, color = Tz.Muted))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TzTextField("نام وام", name, { name = it }, "مثلاً وام خودرو", Modifier.weight(1f))
                TzTextField("بانک", bank, { bank = it }, "مثلاً ملت", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TzTextField("مبلغ کل وام", total, { total = TzFormat.digitsToLatin(it).take(13) }, "تومان", Modifier.weight(1f), numeric = true)
                TzTextField("مبلغ هر قسط", installment, { installment = TzFormat.digitsToLatin(it).take(12) }, "تومان", Modifier.weight(1f), numeric = true)
            }
            TzTextField("تا امروز پرداخت شده (اختیاری)", paid, { paid = TzFormat.digitsToLatin(it).take(13) }, "تومان", numeric = true)

            Text("روز سررسید در هر ماه", style = tzText(13.sp, FontWeight.SemiBold))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .tzField()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepButton(Icons.Filled.Add, "یک روز بعد") { day = if (day >= 31) 1 else day + 1 }
                Text("${TzFormat.fa(day)} هر ماه", style = tzText(17.sp, FontWeight.SemiBold, align = TextAlign.Center), modifier = Modifier.weight(1f))
                StepButton(Icons.Filled.Remove, "یک روز قبل") { day = if (day <= 1) 31 else day - 1 }
            }
            if (problem == null) {
                val left = Loan(bankName = bank, loanName = name, totalAmount = totalV, paidAmount = paidV, installmentAmount = instV, dueDate = "", dueDay = day)
                    .remainingInstallments()
                val next = JalaliDate.nextDueTimestamp(day)
                Text(
                    "${TzFormat.fa(left)} قسط ${TzFormat.compact(instV)}ی مانده است. قسط بعدی ${TzFormat.weekday(next)} ${JalaliDate.fromTimestamp(next).formatShort()} است و یک روز قبلش یادآوری می‌کنیم.",
                    style = tzText(13.sp, color = Color(0xFF1E2B5C), lineHeight = 22.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Tz.CobaltTint)
                        .padding(12.dp)
                )
            }
            PrimaryButton(problem ?: "ثبت وام", enabled = problem == null, onClick = {
                onSave(bank.trim(), name.trim(), totalV, paidV, instV, day)
            })
        }
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Tz.CobaltTint)
            .tzClick(onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Tz.CobaltText) }
}

/** Labelled text field in the design's style. */
@Composable
fun TzTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    numeric: Boolean = false
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = tzText(13.sp, FontWeight.SemiBold))
        Box(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .tzField()
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) Text(placeholder, style = tzText(14.sp, color = Tz.Faint))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = tzText(15.sp),
                keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (numeric && value.isNotEmpty()) {
            val v = value.toLongOrNull()
            if (v != null && v > 0) Text(TzFormat.money(v) + " تومان", style = tzText(11.sp, color = Tz.Muted))
        }
    }
}
