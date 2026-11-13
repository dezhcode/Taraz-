package com.example.ui.layer

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Search
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

/** «یک نگاه»: چقدر خرج کرده‌ام، چقدر بدهکارم و چقدر طلبکارم */
data class HomeGlance(
    val spent: Long = 0,
    val income: Long = 0,
    val owe: Long = 0,
    val oweNote: String = "",
    val owed: Long = 0,
    val owedNote: String = "",
    val balance: Long = 0
)

/** یک کار در «پیش رو»: قسط، قبض، چک، طلب یا بازپرداخت کارت اعتباری */
data class UpcomingItem(
    val kind: String,
    val title: String,
    val amount: Long,
    val at: Long,
    val action: String,
    /** رنگ برچسب نوع: ۰ آبی، ۱ کهربایی، ۲ قرمز، ۳ سبز */
    val tone: Int,
    val primary: Boolean = false,
    val onAction: () -> Unit
)

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
    onOpenCategories: () -> Unit = {},
    layout: List<Pair<String, Boolean>> = HomeTools.defaultLayout,
    tools: List<String> = HomeTools.defaultTools,
    glance: HomeGlance = HomeGlance(),
    upcoming: List<UpcomingItem> = emptyList(),
    onTool: (String) -> Unit = {},
    onOpenAllTools: () -> Unit = {},
    onOpenLayout: () -> Unit = {},
    onOpenObligations: () -> Unit = {},
    onOpenSearch: () -> Unit = {}
) {
    val now = System.currentTimeMillis()
    val todayStart = startOfDay(now)
    ScreenTour("home", Tours.home)

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
            HeaderButton("جستجو", onOpenSearch, Modifier.tourTarget("home.search")) {
                Icon(Icons.Default.Search, null, tint = L.Ink, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(8.dp))
            HeaderButton("تنظیمات", onOpenSettings, Modifier.tourTarget("home.settings")) {
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

        // ── کیف پول لایه‌ای (همیشه بالای صفحه)
        WalletStack(
            cards = cards,
            totalBalance = totalBalance,
            monthlyIncome = monthlyIncome,
            monthlyExpense = monthlyExpense,
            owed = glance.owed,
            owe = glance.owe,
            onOpenAccounts = onOpenAccounts,
            onOpenObligations = onOpenObligations,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp).tourTarget("home.card")
        )

        // ── بخش‌ها به چیدمان کاربر
        layout.filter { it.second }.forEach { (id, _) ->
            when (id) {
                "glance" -> GlanceCard(glance, onOpenReports, onOpenObligations)
                "upcoming" -> UpcomingSection(upcoming, now)
                "sms" -> if (pendingSms.isNotEmpty()) SmsBanner(pendingSms, onOpenSmsInbox)
                "today" -> TodayCard(
                    transactions = transactions,
                    todayStart = todayStart,
                    todayExpense = todayExpense,
                    dailyBudget = dailyBudget,
                    onOpenDay = onOpenDay,
                    onOpenReports = onOpenReports,
                    onSetBudget = onOpenBudget,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)
                )
                "tools" -> ToolsSection(tools, onTool, onOpenAllTools, onOpenLayout)
                "plan" -> PlanSection(activeGoal, categories, transactions, onOpenGoals, onOpenBudget, onOpenReports)
                "recent" -> RecentSection(transactions, cards, categories, onTransactionClick, onSeeAllTransactions)
            }
        }

        // چیدمان همیشه در دسترس است، حتی اگر «ابزارهای من» خاموش باشد
        Row(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp).fillMaxWidth().height(46.dp)
                .clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpenLayout),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Tune, null, tint = L.PrimaryText, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("چیدمان خانه", style = lt(14.sp, FontWeight.SemiBold, L.PrimaryText))
        }
    }
}

@Composable
private fun SectionTitle(title: String, trailing: String? = null, onTrailing: (() -> Unit)? = null, note: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = lt(17.sp, FontWeight.Bold), modifier = Modifier.weight(1f))
        if (note != null) Text(note, style = lt(12.sp, color = L.Muted))
        if (trailing != null && onTrailing != null) {
            Box(
                Modifier.height(36.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onTrailing).padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) { Text(trailing, style = lt(13.sp, FontWeight.SemiBold, L.PrimaryText)) }
        }
    }
}

@Composable
internal fun GlanceCard(g: HomeGlance, onOpenReports: () -> Unit, onOpenObligations: () -> Unit) {
    // مبلغ‌ها کامل نوشته می‌شوند؛ برای همین هر مورد یک ردیف کامل دارد نه یک ستون باریک
    Column(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp).fillMaxWidth().tourTarget("home.glance").cardSurface(22.dp)
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        GlanceRow(
            "هزینه‌های ${JalaliDate.today().monthName}", if (g.income > 0) "درآمد این ماه ${toman(g.income)}" else "درآمدی ثبت نشده",
            g.spent, L.RedText, onOpenReports
        )
        Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(1.dp).background(L.Line))
        GlanceRow("بدهکار", g.oweNote.ifBlank { "بدهکاری ندارید" }, g.owe, if (g.owe > 0) L.Ink else L.Muted, onOpenObligations)
        Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(1.dp).background(L.Line))
        GlanceRow("بستانکار", g.owedNote.ifBlank { "بستانکاری ندارید" }, g.owed, if (g.owed > 0) L.Green else L.Muted, onOpenObligations)
        val net = g.balance + g.owed - g.owe
        Row(
            Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(L.Chip).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("خالص پس از تسویه", style = lt(13.sp, FontWeight.SemiBold))
                Text("موجودی + بستانکار − بدهکار", style = lt(11.sp, color = L.Muted))
            }
            Spacer(Modifier.width(8.dp))
            FitText(toman(net), lt(15.sp, FontWeight.Bold, if (net < 0) L.RedText else L.Ink))
        }
    }
}

@Composable
private fun GlanceRow(label: String, note: String, amount: Long, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = lt(14.sp, FontWeight.SemiBold), maxLines = 1)
            Text(note, style = lt(11.sp, color = L.Muted), maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            FitText(money(amount), lt(17.sp, FontWeight.Bold, color))
            Text(" ${com.example.data.Money.label}", style = lt(11.sp, color = L.Muted), modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

@Composable
internal fun UpcomingSection(items: List<UpcomingItem>, now: Long) {
    SectionTitle(
        "سررسیدهای پیش رو",
        note = if (items.isEmpty()) null else "۷ روز آینده · ${fa(items.size)} مورد · ${toman(items.sumOf { it.amount })}"
    )
    if (items.isEmpty()) {
        Text(
            "در هفتهٔ آینده قسط، قبض یا چکی سررسید نمی‌شود.",
            style = lt(13.sp, color = L.Muted), modifier = Modifier.padding(horizontal = 24.dp)
        )
        return
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.forEach { u ->
            val days = JalaliDate.daysUntil(u.at, now)
            val (soft, ink) = when (u.tone) {
                1 -> L.AmberSoft to L.AmberInk
                2 -> L.RedSoft to L.RedText
                3 -> L.GreenSoft to L.GreenText
                else -> L.PrimarySoft to L.PrimaryText
            }
            Column(
                Modifier.width(200.dp).cardSurface(20.dp).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.height(24.dp).clip(PillShape).background(soft).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                        Text(u.kind, style = lt(12.sp, FontWeight.Bold, ink))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        dueLabel(u.at), style = lt(12.sp, FontWeight.SemiBold, if (days <= 1) L.AmberText else L.Muted)
                    )
                }
                Text(u.title, style = lt(14.sp, FontWeight.SemiBold), maxLines = 1)
                Text(if (u.amount > 0) "${money(u.amount)} ${com.example.data.Money.label}" else "مبلغ نامشخص", style = lt(17.sp))
                Box(
                    Modifier.fillMaxWidth().height(40.dp).clip(ButtonShape)
                        .background(if (u.primary) L.Primary else L.PrimarySoft).clickable(onClick = u.onAction),
                    contentAlignment = Alignment.Center
                ) { Text(u.action, style = lt(13.sp, FontWeight.Bold, if (u.primary) Color.White else L.PrimaryText)) }
            }
        }
    }
}

@Composable
private fun SmsBanner(pending: List<PendingTransaction>, onOpen: () -> Unit) {
    Row(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(L.PrimarySoft).clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.Sms, null, tint = L.PrimaryText, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text("${fa(pending.size)} پیامک بانکی منتظر تأیید", style = lt(14.sp, FontWeight.Bold, L.PrimaryText))
            Text(
                pending.take(2).joinToString(" · ") { (if (it.isExpense) "برداشت " else "واریز ") + money(it.amount) },
                style = lt(12.sp, color = L.Info), maxLines = 1
            )
        }
        Box(Modifier.height(36.dp).clip(ButtonShape).background(L.Primary).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text("بررسی", style = lt(13.sp, FontWeight.Bold, Color.White))
        }
    }
}

@Composable
private fun ToolsSection(tools: List<String>, onTool: (String) -> Unit, onOpenAll: () -> Unit, onOpenLayout: () -> Unit) {
    SectionTitle("ابزارهای من", trailing = "چیدمان", onTrailing = onOpenLayout)
    val items: List<HomeTool?> = tools.mapNotNull { HomeTools.find(it) } + listOf(null) // null = «همهٔ ابزارها»
    Column(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().cardSurface(22.dp).padding(horizontal = 6.dp, vertical = 8.dp)
    ) {
        items.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { t ->
                    Column(
                        Modifier.weight(1f).heightIn(min = 86.dp).clip(RoundedCornerShape(14.dp))
                            .clickable { if (t == null) onOpenAll() else onTool(t.id) }
                            .padding(vertical = 6.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        if (t == null) {
                            Box(
                                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).border(1.5.dp, L.PrimaryDisabled, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Default.MoreHoriz, null, tint = L.PrimaryText, modifier = Modifier.size(22.dp)) }
                            Text("همهٔ ابزارها", style = lt(12.sp, FontWeight.SemiBold, L.PrimaryText), textAlign = TextAlign.Center)
                        } else {
                            IconWell(t.icon, size = 46.dp, bg = L.PrimarySoft, fg = L.PrimaryText, iconSize = 22.dp, radius = 14.dp)
                            Text(t.label, style = lt(12.sp, lineHeight = 17.sp), textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PlanSection(
    goal: com.example.data.FinancialGoal?,
    categories: List<Category>,
    transactions: List<Transaction>,
    onOpenGoals: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenReports: () -> Unit
) {
    val alerts = remember(categories, transactions) { budgetAlerts(categories, transactions) }
    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier.weight(1f).cardSurface(20.dp).clickable(onClick = onOpenGoals).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (goal != null && goal.targetAmount > 0) {
                val frac = (goal.currentAmount.toFloat() / goal.targetAmount).coerceIn(0f, 1f)
                Text("هدف: ${goal.title}", style = lt(12.sp, color = L.Muted), maxLines = 1)
                Text("${fa((frac * 100).toInt())}٪", style = lt(18.sp, FontWeight.Bold))
                LProgress(frac, L.Green, height = 6.dp)
                FitText("${money(goal.currentAmount)} از ${toman(goal.targetAmount)}", lt(11.sp, color = L.Muted), minSize = 8.sp)
            } else {
                Text("هدف پس‌انداز", style = lt(13.sp, FontWeight.Bold))
                Text("مثلاً سفر یا صندوق اضطراری.", style = lt(12.sp, color = L.Muted, lineHeight = 20.sp))
            }
        }
        Column(
            Modifier.weight(1f).cardSurface(20.dp).clickable(onClick = if (alerts.isEmpty()) onOpenBudget else onOpenReports).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val top = alerts.firstOrNull()
            if (top != null) {
                val (cat, used, pct) = top
                Text("بودجهٔ ${cat.name}", style = lt(12.sp, color = L.Muted), maxLines = 1)
                Text("${fa(pct)}٪", style = lt(18.sp, FontWeight.Bold, if (pct >= 100) L.RedText else L.AmberText))
                LProgress(pct / 100f, if (pct >= 100) L.Red else L.Amber, height = 6.dp)
                FitText("${money(used)} از ${toman(cat.monthlyBudget)}", lt(11.sp, color = L.Muted), minSize = 8.sp)
            } else {
                val withBudget = categories.count { it.monthlyBudget > 0 }
                Text("بودجه", style = lt(13.sp, FontWeight.Bold))
                Text(
                    if (withBudget > 0) "همهٔ ${fa(withBudget)} دسته زیر سقف هستند." else "برای دسته‌ها سقف ماهانه تعیین کنید.",
                    style = lt(12.sp, color = L.Muted, lineHeight = 20.sp)
                )
            }
        }
    }
}

@Composable
private fun RecentSection(
    transactions: List<Transaction>,
    cards: List<BankCard>,
    categories: List<Category>,
    onTransactionClick: (Transaction) -> Unit,
    onSeeAll: () -> Unit
) {
    SectionTitle("آخرین تراکنش‌ها", trailing = "همه", onTrailing = onSeeAll)
    val recent = remember(transactions) { transactions.sortedByDescending { it.date }.take(4) }
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (recent.isEmpty()) {
            Text(if (com.example.Features.SMS) "هنوز تراکنشی ثبت نشده. با دکمهٔ + یا از پیامک بانک ثبت کنید." else "هنوز تراکنشی ثبت نشده است. برای ثبت، دکمهٔ + را بزنید.", style = lt(13.sp, color = L.Muted, lineHeight = 22.sp))
        }
        recent.forEach { tx -> TransactionCard(tx, cards, categories) { onTransactionClick(tx) } }
    }
}

// ─────────────────── کیف پول ───────────────────

@Composable
internal fun WalletStack(
    cards: List<BankCard>,
    totalBalance: Long,
    monthlyIncome: Long,
    monthlyExpense: Long,
    owed: Long,
    owe: Long,
    onOpenAccounts: () -> Unit,
    onOpenObligations: () -> Unit,
    modifier: Modifier = Modifier
) {
    val month = JalaliDate.today().monthName
    // طرح «ب»: موجودی کامل + نوار «چند درصد دریافتی این ماه خرج شد»؛ کارت‌های رنگی پشتش می‌مانند
    Box(modifier.fillMaxWidth()) {
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
                .shadow(18.dp, RoundedCornerShape(24.dp), ambientColor = Color(0x472250F4), spotColor = Color(0x472250F4))
                .clip(RoundedCornerShape(24.dp))
                .background(L.Primary)
                .clickable(onClickLabel = "مشاهدهٔ حساب‌ها", onClick = onOpenAccounts)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("موجودی همهٔ حساب‌ها", style = lt(13.sp, color = L.OnPrimarySoft), modifier = Modifier.weight(1f))
                Text("${fa(cards.size)} حساب", style = lt(13.sp, FontWeight.SemiBold, Color.White))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.Bottom) {
                FitText(
                    money(totalBalance),
                    lt(34.sp, FontWeight.Normal, Color.White, letterSpacing = (-1).sp),
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(6.dp))
                Text("${com.example.data.Money.label}", style = lt(14.sp, color = Color.White), modifier = Modifier.padding(bottom = 7.dp))
            }
            // بستانکار و بدهکار جدا، و جمع کل؛ موجودی اصلی بالا دست‌نخورده می‌ماند
            Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WalletBox("بستانکار", "\u200E+" + money(owed), Modifier.weight(1f), onOpenObligations)
                WalletBox("بدهکار", "\u200E−" + money(owe), Modifier.weight(1f), onOpenObligations)
            }
            val net = totalBalance + owed - owe
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("جمع کل", style = lt(13.sp, FontWeight.SemiBold, Color.White))
                    Text("موجودی + بستانکار − بدهکار", style = lt(11.sp, color = L.OnPrimarySoft))
                }
                Spacer(Modifier.width(8.dp))
                FitText(
                    (if (net < 0) "\u200E−" else "") + money(kotlin.math.abs(net)) + " ${com.example.data.Money.label}",
                    lt(16.sp, FontWeight.Bold, if (net < 0) Color(0xFFFFC56B) else Color.White)
                )
            }
            Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.22f)))
            val pct = if (monthlyIncome > 0) ((monthlyExpense * 100) / monthlyIncome).toInt() else null
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(month, style = lt(12.sp, color = L.OnPrimarySoft), modifier = Modifier.weight(1f))
                Text(
                    when {
                        pct == null && monthlyExpense > 0 -> "دریافتی ثبت نشده است"
                        pct == null -> "هنوز هزینه‌ای ثبت نشده است"
                        pct > 100 -> "${fa(pct)}٪ دریافتی؛ هزینه از دریافتی بیشتر است"
                        else -> "${fa(pct)}٪ دریافتی هزینه شده"
                    },
                    style = lt(12.sp, FontWeight.SemiBold, Color.White)
                )
            }
            val frac = when {
                monthlyIncome > 0 -> (monthlyExpense.toFloat() / monthlyIncome).coerceIn(0f, 1f)
                monthlyExpense > 0 -> 1f
                else -> 0f
            }
            Box(
                Modifier.padding(top = 8.dp).fillMaxWidth().height(8.dp).clip(PillShape).background(Color.White.copy(alpha = 0.22f))
                    .semantics { contentDescription = "${pct?.let { fa(it) } ?: "۰"} درصد دریافتی $month هزینه شده" }
            ) {
                Box(
                    Modifier.fillMaxWidth(frac).fillMaxHeight().clip(PillShape)
                        .background(if (pct != null && pct > 100) Color(0xFFFFC56B) else Color.White)
                )
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f)) {
                    Text("دریافتی ", style = lt(12.sp, color = L.OnPrimarySoft))
                    FitText(money(monthlyIncome), lt(12.sp, FontWeight.Bold, Color.White), minSize = 9.sp)
                }
                Text("هزینه ", style = lt(12.sp, color = L.OnPrimarySoft))
                FitText(money(monthlyExpense), lt(12.sp, FontWeight.Bold, Color.White), minSize = 9.sp)
            }
        }
    }
}

@Composable
private fun WalletBox(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.14f)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, style = lt(11.sp, color = L.OnPrimarySoft))
        FitText(value, lt(15.sp, FontWeight.Bold, Color.White), minSize = 10.sp)
    }
}

// ─────────────────── روزشمار امروز ───────────────────

@Composable
internal fun TodayCard(
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
                        .semantics { contentDescription = "${weekdayName(start)}، هزینه ${money(spend)} ${com.example.data.Money.label}" }
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
            Text("هزینه‌های امروز", style = lt(13.sp, color = L.Muted))
            Row(verticalAlignment = Alignment.Bottom) {
                FitText(
                    money(todayExpense),
                    lt(52.sp, FontWeight.Normal, letterSpacing = (-1.5).sp, lineHeight = 56.sp),
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                Text("${com.example.data.Money.label}", style = lt(15.sp, FontWeight.SemiBold), modifier = Modifier.padding(bottom = 8.dp))
            }
            Text(
                if (dailyBudget > 0) "سقف هزینهٔ روزانه: ${toman(dailyBudget)}" else "سقف روزانه تعیین نشده است",
                style = lt(12.sp, color = L.Muted)
            )
            if (dailyBudget > 0) {
                val pct = (todayExpense.toFloat() / dailyBudget).coerceIn(0f, 1f)
                Box(
                    Modifier.fillMaxWidth().height(8.dp).clip(PillShape).background(L.Line)
                        .semantics { contentDescription = "${fa((pct * 100).toInt())} درصد بودجهٔ امروز مصرف شده" }
                ) {
                    Box(Modifier.fillMaxWidth(pct).fillMaxHeight().clip(PillShape).background(if (todayExpense > dailyBudget) L.Amber else L.Primary))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val left = dailyBudget - todayExpense
                Text(
                    when {
                        dailyBudget <= 0 -> "با تعیین سقف روزانه، تراز نزدیک شدن به سقف را اطلاع می‌دهد."
                        left >= 0 -> "${toman(left)} تا سقف امروز باقی مانده است."
                        else -> "هزینه‌های امروز ${toman(-left)} از سقف بیشتر شده است."
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
                    append(if (tx.isTransfer) "انتقال · در هزینه‌ها منظور نمی‌شود" else "${tx.category} · ${tx.bankName}")
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
internal fun HeaderButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Box(
        modifier
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
