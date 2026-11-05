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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Loan
import com.example.data.PendingTransaction
import com.example.data.Transaction
import com.example.data.countsAsExpense
import com.example.data.isCash
import com.example.ui.FinanceViewModel
import com.example.utils.JalaliDate

private const val DAY = 24L * 60 * 60 * 1000

/**
 * Home: the wallet (total across accounts), today's spending against the daily
 * budget with the last seven days beside it, and a timeline of what is coming
 * up and what happened today.
 */
@Composable
fun TarazHomeScreen(
    vm: FinanceViewModel,
    onOpenAccounts: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenSettings: () -> Unit,
    onSeeAllTransactions: () -> Unit,
    onPayLoan: (Loan) -> Unit,
    onReviewPending: (PendingTransaction) -> Unit,
    onTransactionClick: (Transaction) -> Unit
) {
    val profile by vm.userProfile.collectAsStateWithLifecycle()
    val total by vm.totalBalance.collectAsStateWithLifecycle()
    val cards by vm.cards.collectAsStateWithLifecycle()
    val income by vm.monthlyIncome.collectAsStateWithLifecycle()
    val expense by vm.monthlyExpense.collectAsStateWithLifecycle()
    val todaySpent by vm.todayExpense.collectAsStateWithLifecycle()
    val budget by vm.dailyBudget.collectAsStateWithLifecycle()
    val transactions by vm.transactions.collectAsStateWithLifecycle()
    val loans by vm.loans.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val pending by vm.pendingTransactions.collectAsStateWithLifecycle()

    var showBudgetDialog by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val todayRange = JalaliDate.dayRange(now)
    val todayTx = transactions.filter { it.date >= todayRange.first && it.date < todayRange.second }
        .sortedByDescending { it.date }
    val upcoming = loans.filter { !it.isSettled }
        .map { it to JalaliDate.nextDueTimestamp(it.dueDay, now) }
        .filter { JalaliDate.daysUntil(it.second, now) in 0..7 }
        .sortedBy { it.second }
    val waiting = pending.take(3)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Tz.Ground)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = BottomBarClearance)
    ) {
        item {
            HomeHeader(
                name = profile?.name?.takeIf { it.isNotBlank() } ?: "کاربر تراز",
                date = TzFormat.weekday(now) + " " + JalaliDate.today().formatShort(),
                onOpenSettings = onOpenSettings
            )
        }
        item {
            WalletStack(
                total = total,
                accountCount = cards.size,
                backColors = cards.take(2).map { accountColor(it.bankName, it.isCash) },
                income = income,
                expense = expense,
                onOpenAccounts = onOpenAccounts
            )
        }
        item {
            TodayCard(
                transactions = transactions,
                todaySpent = todaySpent,
                budget = budget,
                now = now,
                onOpenReports = onOpenReports,
                onEditBudget = { showBudgetDialog = true }
            )
        }
        item {
            Spacer(Modifier.height(24.dp))
            SectionTitle("خط زمان", subtitle = "کارهای پیش رو و تراکنش‌های امروز")
            Spacer(Modifier.height(10.dp))
        }
        upcoming.forEachIndexed { index, (loan, due) ->
            item(key = "loan-${loan.id}") {
                TimelineRow(
                    label = if (JalaliDate.daysUntil(due, now) <= 1) TzFormat.dayLabel(due, now)
                        else TzFormat.weekday(due) + "\n" + JalaliDate.fromTimestamp(due).formatShort(),
                    isFirst = index == 0,
                    node = { TimelineRing() }
                ) {
                    UpcomingLoanCard(loan, JalaliDate.daysUntil(due, now)) { onPayLoan(loan) }
                }
            }
        }
        item {
            NowMarker(TzFormat.time(now))
        }
        waiting.forEach { p ->
            item(key = "pending-${p.id}") {
                TimelineRow(label = TzFormat.time(p.date), node = { TimelineRing(color = Tz.AmberDot, fill = Color.White) }) {
                    PendingSmsCard(
                        tx = p,
                        onAccept = { onReviewPending(p) },
                        onReject = { vm.rejectPendingTransaction(p.id) }
                    )
                }
            }
        }
        if (todayTx.isEmpty() && waiting.isEmpty()) {
            item {
                EmptyNote(
                    "امروز هنوز تراکنشی ثبت نشده",
                    "با دکمهٔ آبی + پایین صفحه، اولین خرج یا درآمد امروز را ثبت کنید.",
                    Modifier.padding(horizontal = 20.dp)
                )
            }
        }
        todayTx.forEachIndexed { index, tx ->
            item(key = "tx-${tx.id}") {
                TimelineRow(label = TzFormat.time(tx.date), isLast = index == todayTx.lastIndex) {
                    TxCardRow(tx, categories, onClick = { onTransactionClick(tx) })
                }
            }
        }
        item {
            Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                TextAction("همهٔ تراکنش‌ها", onClick = onSeeAllTransactions)
            }
        }
    }

    if (showBudgetDialog) {
        AmountDialog(
            title = "بودجهٔ روزانه",
            initial = budget,
            help = "سقف خرج هر روز را بنویسید. روی خانه می‌بینید هر روز چقدر از آن مانده است.",
            onDismiss = { showBudgetDialog = false },
            onConfirm = {
                vm.setDailyBudget(it)
                showBudgetDialog = false
            }
        )
    }
}

@Composable
private fun HomeHeader(name: String, date: String, onOpenSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("سلام $name", style = tzText(22.sp, FontWeight.Bold))
            Text(date, style = tzText(13.sp, color = Tz.Muted))
        }
        Box(
            Modifier
                .size(48.dp)
                .shadow(1.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Tz.Surface)
                .tzClick(onOpenSettings)
                .semantics { contentDescription = "تنظیمات" },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Tune, contentDescription = null, tint = Tz.Ink, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun WalletStack(
    total: Long,
    accountCount: Int,
    backColors: List<Color>,
    income: Long,
    expense: Long,
    onOpenAccounts: () -> Unit
) {
    val back1 = backColors.getOrNull(1) ?: Color(0xFF0F3B66)
    val back0 = backColors.getOrNull(0) ?: Color(0xFFB4232F)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp)
            .height(184.dp)
    ) {
        Box(
            Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(back1)
        )
        Box(
            Modifier
                .padding(top = 8.dp, start = 14.dp, end = 14.dp)
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(back0)
        )
        Column(
            Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .height(168.dp)
                .shadow(14.dp, RoundedCornerShape(24.dp), ambientColor = Tz.Cobalt, spotColor = Tz.Cobalt)
                .clip(RoundedCornerShape(24.dp))
                .background(Tz.Cobalt)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("موجودی کل · ${TzFormat.fa(accountCount)} حساب", style = tzText(13.sp, color = Tz.CobaltOnDark))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(TzFormat.money(total), style = tzText(34.sp, FontWeight.Light, Color.White, letterSpacing = (-0.5).sp))
                    Spacer(Modifier.width(6.dp))
                    Text("تومان", style = tzText(14.sp, color = Color.White), modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(
                    "این ماه: +${TzFormat.compact(income)} دریافتی · −${TzFormat.compact(expense)} هزینه",
                    style = tzText(12.sp, color = Tz.CobaltOnDark)
                )
            }
            Row(
                Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.18f))
                    .tzClick(onOpenAccounts)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("مشاهدهٔ حساب‌ها", style = tzText(13.sp, FontWeight.SemiBold, Color.White))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun TodayCard(
    transactions: List<Transaction>,
    todaySpent: Long,
    budget: Long,
    now: Long,
    onOpenReports: () -> Unit,
    onEditBudget: () -> Unit
) {
    val todayStart = JalaliDate.dayRange(now).first
    val days = (6 downTo 0).map { back ->
        val start = todayStart - back * DAY
        val spent = transactions.filter { it.countsAsExpense && it.date >= start && it.date < start + DAY }.sumOf { it.amount }
        Triple(start, spent, back == 0)
    }
    val maxSpent = days.maxOf { it.second }.coerceAtLeast(1L)

    TzCard(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp), padding = PaddingValues(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            days.forEach { (start, spent, isToday) ->
                val over = budget > 0 && spent > budget
                Column(
                    Modifier
                        .weight(1f)
                        .height(70.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isToday) Tz.Cobalt else Color.Transparent)
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (isToday) "امروز" else TzFormat.weekdayLetter(start),
                        style = tzText(11.sp, color = if (isToday) Tz.CobaltOnDark else Tz.Muted)
                    )
                    Text(
                        TzFormat.fa(JalaliDate.fromTimestamp(start).day),
                        style = tzText(14.sp, FontWeight.SemiBold, if (isToday) Color.White else Tz.Ink)
                    )
                    val h = (22f * spent / maxSpent).coerceAtLeast(if (spent > 0) 5f else 3f)
                    Box(
                        Modifier
                            .width(6.dp)
                            .height(h.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when {
                                    isToday -> Color.White
                                    over -> Tz.AmberDot
                                    spent > 0 -> Tz.BarSoft
                                    else -> Tz.Hairline
                                }
                            )
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Hairline()
        Spacer(Modifier.height(14.dp))

        Text("خرج امروز", style = tzText(13.sp, color = Tz.Muted))
        val (big, unit) = if (todaySpent >= 1_000_000L) {
            TzFormat.compact(todaySpent).substringBefore(" ") to "میلیون تومان"
        } else {
            TzFormat.fa(todaySpent / 1000) to "هزار تومان"
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(big, style = tzText(60.sp, FontWeight.ExtraLight, letterSpacing = (-2).sp, lineHeight = 60.sp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.padding(bottom = 6.dp)) {
                Text(unit, style = tzText(15.sp, FontWeight.SemiBold))
                Text(
                    if (budget > 0) "از بودجهٔ ${TzFormat.compact(budget)}" else "بودجهٔ روزانه تعیین نشده",
                    style = tzText(12.sp, color = Tz.Muted)
                )
            }
        }
        if (budget > 0) {
            Spacer(Modifier.height(8.dp))
            TzProgress(todaySpent.toFloat() / budget, color = if (todaySpent > budget) Tz.AmberDot else Tz.Cobalt)
            Spacer(Modifier.height(6.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val left = budget - todaySpent
            Text(
                when {
                    budget <= 0 -> "با تعیین سقف روزانه، هر روز می‌بینید چقدر جا دارید."
                    left >= 0 -> "هنوز ${TzFormat.compact(left)} تومان تا سقف امروز جا دارید."
                    else -> "${TzFormat.compact(-left)} تومان بیشتر از سقف امروز خرج شده."
                },
                style = tzText(12.sp, color = if (left < 0 && budget > 0) Tz.Amber else Tz.Muted, lineHeight = 19.sp),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            if (budget <= 0) {
                SecondaryButton("تعیین بودجه", onClick = onEditBudget, fill = false, height = 36.dp, fontSize = 12.sp)
            } else {
                SecondaryButton("گزارش ماه", onClick = onOpenReports, fill = false, height = 36.dp, fontSize = 12.sp)
            }
        }
        if (budget > 0) {
            TextAction("تغییر بودجهٔ روزانه", onClick = onEditBudget, fontSize = 12.sp)
        } else {
            TextAction("گزارش ماه", onClick = onOpenReports, fontSize = 12.sp)
        }
    }
}

@Composable
private fun UpcomingLoanCard(loan: Loan, daysLeft: Int, onPay: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Tz.RowShape)
            .background(Tz.Surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("قسط ${loan.loanName}", style = tzText(14.sp, FontWeight.SemiBold), maxLines = 1)
            Row {
                Text(TzFormat.money(loan.installmentAmount) + " تومان · ", style = tzText(12.sp, color = Tz.Muted))
                Text(
                    when (daysLeft) {
                        0 -> "امروز"
                        1 -> "فردا"
                        else -> TzFormat.fa(daysLeft) + " روز دیگر"
                    },
                    style = tzText(12.sp, FontWeight.SemiBold, Tz.Amber)
                )
            }
        }
        SecondaryButton("پرداخت", onClick = onPay, fill = false, height = 40.dp, fontSize = 14.sp)
    }
}

@Composable
private fun NowMarker(time: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("اکنون · $time", style = tzText(12.sp, FontWeight.Bold, Tz.CobaltText))
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .weight(1f)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Tz.Cobalt)
        )
    }
}

@Composable
private fun PendingSmsCard(tx: PendingTransaction, onAccept: () -> Unit, onReject: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Tz.RowShape)
            .background(Tz.Surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tx.title.ifBlank { "تراکنش بانکی" }, style = tzText(14.sp, FontWeight.Medium), maxLines = 1)
                Text("از پیامک ${tx.card} · هنوز ثبت نشده", style = tzText(12.sp, FontWeight.SemiBold, Tz.Amber), maxLines = 1)
            }
            Text(TzFormat.signed(tx.amount, tx.isExpense), style = tzText(14.sp, FontWeight.SemiBold, if (tx.isExpense) Tz.Ink else Tz.Green))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("بررسی و ثبت", onClick = onAccept, modifier = Modifier.weight(1f), height = 40.dp)
            SecondaryButton("مال من نیست", onClick = onReject, fill = false, height = 40.dp, fontSize = 14.sp)
        }
    }
}
