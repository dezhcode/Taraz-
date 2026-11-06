package com.example.ui.layer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BankCard
import com.example.data.Category
import com.example.data.Loan
import com.example.data.PendingTransaction
import com.example.data.Transaction
import com.example.data.tagList
import com.example.data.countsAsExpense
import com.example.data.isTransfer
import com.example.utils.JalaliDate

@Composable
fun LayerHomeScreen(
    userName: String,
    cards: List<BankCard>,
    totalBalance: Long,
    monthlyIncome: Long,
    monthlyExpense: Long,
    todayExpense: Long,
    dailyBudget: Long,
    transactions: List<Transaction>,
    loans: List<Loan>,
    pendingSms: List<PendingTransaction>,
    categories: List<Category>,
    onOpenBudget: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onOpenAccounts: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSmsInbox: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenDay: (dayStart: Long) -> Unit,
    onSeeAllTransactions: () -> Unit,
    onPayLoan: (Loan) -> Unit,
    onApproveSms: (PendingTransaction) -> Unit,
    onDismissSms: (PendingTransaction) -> Unit,
    onReviewSms: (PendingTransaction) -> Unit,
    onTransactionClick: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
    activeGoal: com.example.data.FinancialGoal? = null,
    onOpenGoals: () -> Unit = {},
    onOpenCategories: () -> Unit = {}
) {
    val now = System.currentTimeMillis()
    val todayStart = startOfDay(now)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(L.Bg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(bottom = NavClearance)
    ) {
        // ── سرصفحه
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("سلام ${userName.ifBlank { "دوست من" }}", style = lt(22.sp, FontWeight.Bold))
                Text("${weekdayName(now)} ${JalaliDate.today().formatShort()}", style = lt(13.sp, color = L.Muted))
            }
            HeaderButton("تنظیمات", onOpenSettings) {
                Icon(Icons.Default.Tune, null, tint = L.Ink, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(8.dp))
            HeaderButton(
                if (pendingSms.isEmpty()) "اعلان‌ها" else "اعلان‌ها، ${fa(pendingSms.size)} پیامک منتظر تأیید",
                onOpenNotifications
            ) {
                Icon(Icons.Default.Notifications, null, tint = L.Ink, modifier = Modifier.size(22.dp))
                if (pendingSms.isNotEmpty()) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-11).dp, y = 10.dp)
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(L.Red)
                            .border(2.dp, L.Surface, CircleShape)
                    )
                }
            }
        }

        // ── کیف پول لایه‌ای
        WalletStack(
            cards = cards,
            totalBalance = totalBalance,
            monthlyIncome = monthlyIncome,
            monthlyExpense = monthlyExpense,
            onOpenAccounts = onOpenAccounts,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)
        )

        // ── روزشمار: نوار هفته + خرج امروز
        TodayCard(
            transactions = transactions,
            todayStart = todayStart,
            todayExpense = todayExpense,
            dailyBudget = dailyBudget,
            onOpenDay = onOpenDay,
            onOpenReports = onOpenReports,
            onSetBudget = onOpenBudget,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)
        )

        // ── هشدار بودجهٔ دسته‌ها (۸۰٪ به بالا)
        val alerts = remember(categories, transactions) { budgetAlerts(categories, transactions) }
        if (alerts.isNotEmpty()) {
            val (cat, used, pct) = alerts.first()
            val over = pct >= 100
            Callout(
                title = if (over) "بودجهٔ «${cat.name}» تمام شد" else "${fa(pct)}٪ بودجهٔ «${cat.name}» خرج شده",
                body = "${millions(used)} از ${millions(cat.monthlyBudget)} این ماه" +
                    (if (alerts.size > 1) " · ${fa(alerts.size - 1)} دستهٔ دیگر هم نزدیک سقف است" else ""),
                bg = if (over) L.RedSoft else L.AmberSoft,
                titleColor = if (over) L.RedText else L.AmberInk,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp).clickable(onClick = onOpenReports)
            )
        }

        // ── اهداف پس‌انداز و دسته‌بندی‌ها
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                Modifier.weight(1f).cardSurface(18.dp).clickable(onClick = onOpenGoals).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("اهداف پس‌انداز", style = lt(13.sp, FontWeight.Bold), modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = L.Chevron, modifier = Modifier.size(16.dp))
                }
                if (activeGoal != null && activeGoal.targetAmount > 0) {
                    val frac = (activeGoal.currentAmount.toFloat() / activeGoal.targetAmount).coerceIn(0f, 1f)
                    Text(activeGoal.title, style = lt(12.sp, color = L.Muted), maxLines = 1)
                    LProgress(frac, L.Primary, height = 6.dp)
                    Text("${fa((frac * 100).toInt())}٪ · ${millions(activeGoal.currentAmount)} از ${millions(activeGoal.targetAmount)}", style = lt(11.sp, color = L.Muted), maxLines = 1)
                } else {
                    Text("هدفی بگذارید؛ مثلاً سفر یا صندوق اضطراری.", style = lt(12.sp, color = L.Muted, lineHeight = 20.sp))
                }
            }
            Column(
                Modifier.weight(1f).cardSurface(18.dp).clickable(onClick = onOpenCategories).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("دسته‌بندی‌ها", style = lt(13.sp, FontWeight.Bold), modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = L.Chevron, modifier = Modifier.size(16.dp))
                }
                val withBudget = categories.count { it.monthlyBudget > 0 }
                Text("${fa(categories.size)} دسته", style = lt(12.sp, color = L.Muted))
                Text(
                    if (withBudget > 0) "${fa(withBudget)} دسته با بودجهٔ ماهانه" else "برای هر دسته بودجهٔ ماهانه بگذارید",
                    style = lt(11.sp, color = L.Muted, lineHeight = 19.sp)
                )
            }
        }

        // ── خط زمان
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("خط زمان", style = lt(17.sp, FontWeight.Bold))
            Text("کارهای پیش رو و تراکنش‌های امروز", style = lt(12.sp, color = L.Muted))
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            val upcoming = loans
                .filter { !it.isSettled }
                .map { it to JalaliDate.nextDueTimestamp(it.dueDay, now) }
                .filter { JalaliDate.daysUntil(it.second, now) in 0..7 }
                .sortedBy { it.second }

            upcoming.forEach { (loan, due) ->
                val days = JalaliDate.daysUntil(due, now)
                TimelineRow(
                    label = "${weekdayName(due)}\n${JalaliDate.fromTimestamp(due).formatShort()}",
                    node = { HollowNode(L.Amber) },
                    railTop = 30.dp
                ) {
                    Row(
                        Modifier.fillMaxWidth().cardSurface().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("قسط ${loan.loanName}", style = lt(14.sp, FontWeight.SemiBold))
                            Row {
                                Text("${money(loan.installmentAmount)} تومان · ", style = lt(12.sp, color = L.Muted))
                                Text(daysLabel(days), style = lt(12.sp, FontWeight.SemiBold, L.AmberText))
                            }
                        }
                        SoftButton("پرداخت") { onPayLoan(loan) }
                    }
                }
            }

            // اکنون
            Row(Modifier.fillMaxWidth().height(30.dp).padding(bottom = 0.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("اکنون · ${timeOfDay(now)}", style = lt(12.sp, FontWeight.Bold, L.PrimaryText))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f).height(2.dp).clip(RoundedCornerShape(1.dp)).background(L.Primary))
            }
            Spacer(Modifier.height(8.dp))

            val todayTx = transactions.filter { it.date >= todayStart }.sortedByDescending { it.date }
            val entries: List<Pair<Long, Any>> =
                (todayTx.map { it.date to (it as Any) } + pendingSms.map { it.date to (it as Any) })
                    .sortedByDescending { it.first }
                    .take(8)

            if (entries.isEmpty()) {
                Text(
                    "امروز هنوز تراکنشی ثبت نشده. با دکمهٔ + یا از پیامک بانک ثبت کنید.",
                    style = lt(13.sp, color = L.Muted, lineHeight = 22.sp),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            entries.forEachIndexed { i, (ts, item) ->
                val last = i == entries.lastIndex
                val label = if (ts >= todayStart) timeOfDay(ts) else JalaliDate.fromTimestamp(ts).formatShort()
                when (item) {
                    is PendingTransaction -> TimelineRow(
                        label = label,
                        node = { HollowNode(L.Amber, filled = false, size = 14.dp) },
                        lastRow = last
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(ItemShape)
                                .background(L.Surface)
                                .border(1.5.dp, L.AmberRing, ItemShape)
                                .clickable { onReviewSms(item) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconWell(Icons.Default.Sms)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.title.ifBlank { "تراکنش بانکی" }, style = lt(14.sp, FontWeight.Medium))
                                    Text("از پیامک ${item.card} · هنوز ثبت نشده", style = lt(12.sp, FontWeight.SemiBold, L.AmberText))
                                }
                                Text(signedMoney(item.amount, item.isExpense), style = lt(14.sp, FontWeight.SemiBold, if (item.isExpense) L.RedText else L.Green))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PrimaryButton("ثبت کن", Modifier.weight(1f).height(40.dp)) { onApproveSms(item) }
                                SoftButton("مال من نیست") { onDismissSms(item) }
                            }
                        }
                    }
                    is Transaction -> TimelineRow(
                        label = label,
                        node = { SolidNode(txColor(item, L.Primary)) },
                        lastRow = last
                    ) {
                        TransactionCard(item, cards, categories) { onTransactionClick(item) }
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onSeeAllTransactions),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("همهٔ تراکنش‌ها", style = lt(14.sp, FontWeight.SemiBold, L.PrimaryText))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = L.PrimaryText, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─────────────────── کیف پول ───────────────────

@Composable
private fun WalletStack(
    cards: List<BankCard>,
    totalBalance: Long,
    monthlyIncome: Long,
    monthlyExpense: Long,
    onOpenAccounts: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxWidth().height(180.dp)) {
        Box(
            Modifier.padding(horizontal = 28.dp).fillMaxWidth().height(60.dp)
                .clip(RoundedCornerShape(22.dp)).background(if (cards.size > 2) cardColor(2) else L.CardColors[2])
        )
        Box(
            Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp).fillMaxWidth().height(60.dp)
                .clip(RoundedCornerShape(22.dp)).background(if (cards.size > 1) cardColor(1) else L.CardColors[1])
        )
        Column(
            Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .height(164.dp)
                .shadow(18.dp, RoundedCornerShape(24.dp), ambientColor = Color(0x472250F4), spotColor = Color(0x472250F4))
                .clip(RoundedCornerShape(24.dp))
                .background(L.Primary)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("موجودی کل · ${fa(cards.size)} حساب", style = lt(13.sp, color = L.OnPrimarySoft))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(money(totalBalance), style = lt(32.sp, FontWeight.Normal, Color.White, letterSpacing = (-1).sp))
                    Spacer(Modifier.width(6.dp))
                    Text("تومان", style = lt(14.sp, color = Color.White), modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(
                    "${JalaliDate.today().monthName}: +${millions(monthlyIncome)} دریافتی · −${millions(monthlyExpense)} هزینه",
                    style = lt(12.sp, color = L.OnPrimarySoft)
                )
            }
            Row(
                Modifier
                    .height(36.dp)
                    .clip(ButtonShape)
                    .background(Color.White.copy(alpha = 0.18f))
                    .clickable(onClick = onOpenAccounts)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("مشاهدهٔ حساب‌ها", style = lt(13.sp, FontWeight.SemiBold, Color.White))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ─────────────────── روزشمار امروز ───────────────────

@Composable
private fun TodayCard(
    transactions: List<Transaction>,
    todayStart: Long,
    todayExpense: Long,
    dailyBudget: Long,
    onOpenDay: (Long) -> Unit,
    onOpenReports: () -> Unit,
    onSetBudget: () -> Unit,
    modifier: Modifier = Modifier
) {
    val days = (6 downTo 0).map { back ->
        val start = todayStart - back * DAY_MS
        start to transactions.filter { it.countsAsExpense && it.date >= start && it.date < start + DAY_MS }.sumOf { it.amount }
    }
    val maxSpend = days.maxOf { it.second }.coerceAtLeast(1L)

    Column(modifier.fillMaxWidth().cardSurface(24.dp).padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            days.forEach { (start, spend) ->
                val isToday = start == todayStart
                val over = dailyBudget > 0 && spend > dailyBudget
                val barH = (spend.toFloat() / maxSpend * 22f).coerceAtLeast(6f).dp
                Column(
                    Modifier
                        .weight(1f)
                        .height(70.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isToday) L.Primary else Color.Transparent)
                        .clickable { onOpenDay(start) }
                        .semantics { contentDescription = "${weekdayName(start)}، خرج ${money(spend)} تومان" }
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(if (isToday) "امروز" else weekdayShort(start), style = lt(11.sp, color = if (isToday) L.OnPrimarySoft else L.Muted))
                    Text(fa(JalaliDate.fromTimestamp(start).day), style = lt(14.sp, if (isToday) FontWeight.Bold else FontWeight.SemiBold, if (isToday) Color.White else L.Ink))
                    Box(
                        Modifier.width(6.dp).height(barH).clip(RoundedCornerShape(3.dp))
                            .background(if (isToday) Color.White else if (over) L.Amber else L.BarIdle)
                    )
                }
            }
        }
        Box(Modifier.padding(horizontal = 2.dp, vertical = 14.dp).fillMaxWidth().height(1.dp).background(L.Line))
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("خرج امروز", style = lt(13.sp, color = L.Muted))
            val inThousands = todayExpense >= 1000
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (inThousands) money(todayExpense / 1000) else money(todayExpense),
                    style = lt(60.sp, FontWeight.Normal, letterSpacing = (-2).sp, lineHeight = 60.sp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.padding(bottom = 6.dp)) {
                    Text(if (inThousands) "هزار تومان" else "تومان", style = lt(15.sp, FontWeight.SemiBold))
                    Text(
                        if (dailyBudget > 0) "از بودجهٔ ${money(dailyBudget / 1000)} هزار" else "سقف روزانه ندارید",
                        style = lt(12.sp, color = L.Muted)
                    )
                }
            }
            if (dailyBudget > 0) {
                val pct = (todayExpense.toFloat() / dailyBudget).coerceIn(0f, 1f)
                Box(
                    Modifier.fillMaxWidth().height(8.dp).clip(PillShape).background(L.Line)
                        .semantics { contentDescription = "${fa((pct * 100).toInt())} درصد بودجهٔ امروز خرج شده" }
                ) {
                    Box(Modifier.fillMaxWidth(pct).fillMaxHeight().clip(PillShape).background(if (todayExpense > dailyBudget) L.Amber else L.Primary))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val left = dailyBudget - todayExpense
                Text(
                    when {
                        dailyBudget <= 0 -> "با تعیین سقف روزانه، تراز به شما خبر می‌دهد کی نزدیک سقف هستید."
                        left >= 0 -> "هنوز ${money(left / 1000)} هزار تومان تا سقف امروز جا دارید."
                        else -> "${money(-left / 1000)} هزار تومان بیشتر از سقف امروز خرج شده."
                    },
                    style = lt(12.sp, color = if (left < 0 && dailyBudget > 0) L.AmberText else L.Muted, lineHeight = 20.sp),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                if (dailyBudget <= 0) SoftButton("تعیین سقف", height = 34.dp, onClick = onSetBudget)
                else SoftButton("گزارش ماه", height = 34.dp, onClick = onOpenReports)
            }
        }
    }
}

// ─────────────────── اجزای مشترک خط زمان ───────────────────

fun daysLabel(days: Int): String = when (days) {
    0 -> "امروز"
    1 -> "فردا"
    else -> "${fa(days)} روز دیگر"
}

@Composable
internal fun TimelineRow(
    label: String,
    node: @Composable () -> Unit,
    lastRow: Boolean = false,
    railTop: Dp = 0.dp,
    labelWidth: Dp = 46.dp,
    content: @Composable () -> Unit
) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Text(
            label,
            style = lt(12.sp, color = L.Muted, lineHeight = 17.sp),
            modifier = Modifier.width(labelWidth).padding(top = 16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.width(18.dp).fillMaxHeight()) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = railTop)
                    .width(2.dp)
                    .then(if (lastRow) Modifier.height(22.dp) else Modifier.fillMaxHeight())
                    .background(L.Rail)
            )
            Box(Modifier.align(Alignment.TopCenter).padding(top = 20.dp)) { node() }
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).padding(bottom = 10.dp)) { content() }
    }
}

@Composable
internal fun SolidNode(color: Color) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
}

@Composable
internal fun HollowNode(ring: Color, filled: Boolean = false, size: Dp = 10.dp) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(if (filled) ring else L.Surface)
            .border(BorderStroke(2.5.dp, ring), CircleShape)
    )
}

/** رنگ هر تراکنش: ورودی سبز، خروجی قرمز، انتقال [transferColor] */
internal fun txColor(tx: Transaction, transferColor: Color): Color = when {
    tx.isTransfer -> transferColor
    tx.isExpense -> L.RedText
    else -> L.Green
}

@Composable
internal fun TransactionCard(
    tx: Transaction,
    cards: List<BankCard>,
    categories: List<Category>,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().cardSurface().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (tx.isTransfer) IconWell(categoryIcon("انتقال", categories), bg = L.PrimarySoft, fg = L.Primary)
        else IconWell(categoryIcon(tx.category, categories))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(tx.title, style = lt(14.sp, FontWeight.Medium), maxLines = 1)
            Text(
                buildString {
                    append(if (tx.isTransfer) "انتقال · در جمع هزینه نیست" else "${tx.category} · ${tx.bankName}")
                    tx.tagList.firstOrNull()?.let { append(" · #$it") }
                    if (tx.attachments.isNotBlank()) append(" · 📎")
                },
                style = lt(12.sp, color = L.Muted),
                maxLines = 1
            )
        }
        Text(
            if (tx.isTransfer) money(tx.amount) else signedMoney(tx.amount, tx.isExpense),
            style = lt(14.sp, FontWeight.SemiBold, txColor(tx, L.Muted))
        )
    }
}

@Composable
internal fun HeaderButton(label: String, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .shadow(1.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x0F0E1116), spotColor = Color(0x0F0E1116))
            .clip(RoundedCornerShape(16.dp))
            .background(L.Surface)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
internal fun SoftButton(text: String, height: Dp = 40.dp, onClick: () -> Unit) {
    Box(
        Modifier.height(height).clip(ButtonShape).background(L.PrimarySoft).clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = lt(if (height < 38.dp) 12.sp else 14.sp, FontWeight.Bold, L.PrimaryText))
    }
}

@Composable
internal fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.clip(ButtonShape).background(if (enabled) L.Primary else L.PrimaryDisabled)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = lt(14.sp, FontWeight.Bold, Color.White))
    }
}

/** سطح سفید کارت با سایهٔ خیلی نرم */
internal fun Modifier.cardSurface(radius: Dp = 18.dp): Modifier =
    this
        .shadow(1.dp, RoundedCornerShape(radius), ambientColor = L.CardShadow, spotColor = L.CardShadow)
        .clip(RoundedCornerShape(radius))
        .background(L.Surface)
