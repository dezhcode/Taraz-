package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BankCard
import com.example.data.FinancialGoal
import com.example.data.Loan
import com.example.data.Transaction
import com.example.ui.theme.*
import com.example.utils.JalaliDate
import com.example.utils.MoneyFormat

/**
 * "Horizon" — the home screen.
 *
 * The balance sits on green with its own trajectory drawn behind it: a finance
 * screen that shows a number without its direction is a receipt, not a
 * dashboard. The month card overlaps the panel's lower edge and is the screen's
 * only real depth, instead of a shadow under every box.
 */
@Composable
fun HorizonHomeScreen(
    userProfile: UserProfile?,
    totalBalance: Long,
    monthlyIncome: Long,
    monthlyExpense: Long,
    todayExpense: Long,
    dailyBudget: Long,
    onSetDailyBudget: (Long) -> Unit,
    transactions: List<Transaction>,
    cards: List<BankCard>,
    loans: List<Loan>,
    activeGoal: FinancialGoal?,
    onSaveGoal: (title: String, targetAmount: Long, currentAmount: Long) -> Unit,
    onDeleteGoal: (goalId: Int) -> Unit,
    onCardClick: (BankCard) -> Unit,
    onNavigateToCards: () -> Unit,
    onNavigateToLoans: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onSeeAllTransactions: () -> Unit,
    onTransactionClick: (Transaction) -> Unit,
    modifier: Modifier = Modifier
) {
    var showBudgetDialog by remember { mutableStateOf(false) }
    var showGoalDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HorizonBackground)
            .verticalScroll(rememberScrollState())
    ) {
        HeroPanel(
            userName = userProfile?.name ?: "کاربر تراز",
            totalBalance = totalBalance,
            transactions = transactions,
            onNavigateToSettings = onNavigateToSettings
        )

        // Lifted onto the panel's edge — the one place the screen has depth.
        MonthFlowCard(
            monthlyIncome = monthlyIncome,
            monthlyExpense = monthlyExpense,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .offset(y = (-54).dp)
        )

        Column(modifier = Modifier.offset(y = (-38).dp)) {

            if (cards.isNotEmpty()) {
                AccountsRow(cards = cards, onCardClick = onCardClick)
                Spacer(modifier = Modifier.height(14.dp))
            }

            BudgetRingCard(
                todayExpense = todayExpense,
                dailyBudget = dailyBudget,
                onSetBudget = { showBudgetDialog = true }
            )

            Spacer(modifier = Modifier.height(12.dp))

            QuickTiles(
                loanCount = loans.count { !it.isSettled },
                cardCount = cards.size,
                onNavigateToLoans = onNavigateToLoans,
                onNavigateToCards = onNavigateToCards
            )

            Spacer(modifier = Modifier.height(16.dp))

            RecentTransactions(
                transactions = transactions,
                onTransactionClick = onTransactionClick,
                onSeeAll = onSeeAllTransactions
            )

            if (activeGoal != null) {
                Spacer(modifier = Modifier.height(16.dp))
                GoalRow(goal = activeGoal, onClick = { showGoalDialog = true })
            }

            Spacer(modifier = Modifier.height(110.dp))
        }
    }

    if (showGoalDialog) {
        GoalEditorDialog(
            activeGoal = activeGoal,
            onSave = { title, target, saved ->
                onSaveGoal(title, target, saved)
                showGoalDialog = false
            },
            onDelete = { id ->
                onDeleteGoal(id)
                showGoalDialog = false
            },
            onDismiss = { showGoalDialog = false }
        )
    }

    if (showBudgetDialog) {
        SetDailyBudgetDialog(
            currentBudget = dailyBudget,
            onDismiss = { showBudgetDialog = false },
            onConfirm = { amount ->
                onSetDailyBudget(amount)
                showBudgetDialog = false
            }
        )
    }
}

/** Latin amounts must not be reflowed by the RTL layout, or the sign jumps. */
@Composable
private fun Amount(
    text: String,
    fontSize: Int,
    weight: FontWeight,
    color: Color,
    letterSpacing: Int = 0
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = fontSize.sp,
                fontWeight = weight,
                color = color,
                letterSpacing = letterSpacing.sp,
                fontFeatureSettings = "tnum"   // tabular figures: the column stays still
            )
        )
    }
}

@Composable
private fun HeroPanel(
    userName: String,
    totalBalance: Long,
    transactions: List<Transaction>,
    onNavigateToSettings: () -> Unit
) {
    val trend = remember(transactions, totalBalance) {
        netWorthTrend(transactions, totalBalance)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(HorizonGreen)
    ) {
        if (trend.size >= 2) {
            TrendGraph(
                points = trend,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp)
                    .align(Alignment.BottomCenter)
            )
        }

        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 74.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = greetingForNow(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = HorizonOnGreen, fontSize = 11.5.sp
                        )
                    )
                    Text(
                        text = userName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.5.sp
                        )
                    )
                }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .clickable { onNavigateToSettings() }
                        .semantics { contentDescription = "تنظیمات" }
                        .testTag("horizon_settings"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = Color(0xFFDDF2EA),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            Text(
                text = "ثروت خالص",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = HorizonOnGreen, fontSize = 11.5.sp, letterSpacing = 0.3.sp
                )
            )
            Spacer(modifier = Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Amount(
                    text = MoneyFormat.amount(totalBalance),
                    fontSize = 36, weight = FontWeight.Bold, color = Color.White, letterSpacing = -1
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = "تومان",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = HorizonOnGreen, fontSize = 13.sp
                    ),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}

/**
 * Cumulative net worth over the recent past, derived from the transactions
 * already on the device — the app keeps no balance history, so the line is
 * computed backwards from today's balance rather than invented.
 */
private fun netWorthTrend(
    transactions: List<Transaction>,
    currentBalance: Long,
    weeks: Int = 12
): List<Long> {
    if (transactions.isEmpty()) return emptyList()

    val weekMs = 7L * 24 * 60 * 60 * 1000
    val now = System.currentTimeMillis()
    val series = ArrayList<Long>(weeks + 1)
    var balance = currentBalance
    series.add(balance)

    for (i in 0 until weeks) {
        val end = now - i * weekMs
        val start = end - weekMs
        val net = transactions
            .filter { it.date in start until end }
            .sumOf { if (it.isExpense) -it.amount else it.amount }
        balance -= net
        series.add(balance)
    }
    return series.reversed()
}

@Composable
private fun TrendGraph(points: List<Long>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val min = points.min()
        val max = points.max()
        val span = (max - min).coerceAtLeast(1L).toFloat()
        val stepX = size.width / (points.size - 1).coerceAtLeast(1)

        fun yFor(value: Long): Float {
            val ratio = (value - min) / span
            return size.height * (1f - ratio * 0.72f) - size.height * 0.08f
        }

        val line = Path().apply {
            moveTo(0f, yFor(points.first()))
            points.forEachIndexed { index, value ->
                if (index > 0) lineTo(index * stepX, yFor(value))
            }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }

        drawPath(path = area, color = HorizonGreenBright, alpha = 0.42f)
        drawPath(path = line, color = HorizonGreenGlow, style = Stroke(width = 2.dp.toPx()), alpha = 0.85f)
    }
}

@Composable
private fun MonthFlowCard(
    monthlyIncome: Long,
    monthlyExpense: Long,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = HorizonSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            FlowItem(
                label = "دریافتی ماه",
                amount = monthlyIncome,
                icon = Icons.Default.ArrowUpward,
                tint = HorizonGreen,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(38.dp)
                    .background(HorizonDivider)
            )
            FlowItem(
                label = "هزینهٔ ماه",
                amount = monthlyExpense,
                icon = Icons.Default.ArrowDownward,
                tint = HorizonClay,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            )
        }
    }
}

@Composable
private fun FlowItem(
    label: String,
    amount: Long,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = HorizonInkMuted, fontSize = 11.sp
                )
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Amount(MoneyFormat.amount(amount), 16, FontWeight.Bold, HorizonInk)
    }
}

@Composable
private fun AccountsRow(cards: List<BankCard>, onCardClick: (BankCard) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(cards) { card ->
            Column(
                modifier = Modifier
                    .width(150.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(HorizonSurface)
                    .border(1.dp, HorizonBorder, RoundedCornerShape(16.dp))
                    .clickable { onCardClick(card) }
                    .padding(13.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = card.bankName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold, fontSize = 12.sp
                        ),
                        maxLines = 1
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(HorizonGreen)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Amount(MoneyFormat.amount(card.balance), 19, FontWeight.Bold, HorizonInk)
                Text(
                    text = "تومان",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = HorizonInkFaint, fontSize = 10.5.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun BudgetRingCard(
    todayExpense: Long,
    dailyBudget: Long,
    onSetBudget: () -> Unit
) {
    val ratio = if (dailyBudget > 0) (todayExpense.toFloat() / dailyBudget.toFloat()) else 0f
    val progress by animateFloatAsState(
        targetValue = ratio.coerceIn(0f, 1f),
        animationSpec = tween(700),
        label = "budget_ring"
    )
    val over = dailyBudget > 0 && todayExpense > dailyBudget
    val ringColor = if (over) HorizonClay else HorizonGreen
    val percent = (ratio * 100).toInt().coerceIn(0, 999)

    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HorizonSurface)
            .border(1.dp, HorizonBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 15.dp)
            .testTag("horizon_budget"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(56.dp)) {
                val stroke = 6.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = HorizonGreenTint,
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(width = stroke)
                )
                if (progress > 0f) {
                    drawArc(
                        color = ringColor,
                        startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                        topLeft = Offset(inset, inset), size = arcSize,
                        style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                }
            }
            Amount("$percent%", 12, FontWeight.Bold, HorizonInk)
        }

        Spacer(modifier = Modifier.width(15.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "بودجهٔ امروز",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold, fontSize = 14.sp, color = HorizonInk
                    )
                )
                Text(
                    text = if (dailyBudget > 0) "تنظیم" else "تعیین بودجه",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = HorizonGreen, fontWeight = FontWeight.Bold, fontSize = 11.5.sp
                    ),
                    modifier = Modifier
                        .clickable { onSetBudget() }
                        .testTag("horizon_set_budget")
                )
            }
            Spacer(modifier = Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Amount(MoneyFormat.amount(todayExpense), 12, FontWeight.Normal, HorizonInkMuted)
                Text(
                    text = if (dailyBudget > 0) " از " else " تومان خرج امروز",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = HorizonInkMuted, fontSize = 12.sp
                    )
                )
                if (dailyBudget > 0) {
                    Amount(MoneyFormat.amount(dailyBudget), 12, FontWeight.Normal, HorizonInkMuted)
                    Text(
                        text = " تومان",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = HorizonInkMuted, fontSize = 12.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickTiles(
    loanCount: Int,
    cardCount: Int,
    onNavigateToLoans: () -> Unit,
    onNavigateToCards: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QuickTile(
            title = "اقساط و وام‌ها",
            subtitle = if (loanCount > 0) "$loanCount وام فعال" else "ثبت وام جدید",
            icon = Icons.Default.AccountBalance,
            onClick = onNavigateToLoans,
            testTag = "horizon_loans",
            modifier = Modifier.weight(1f)
        )
        QuickTile(
            title = "کارت‌های بانکی",
            subtitle = if (cardCount > 0) "$cardCount کارت" else "افزودن کارت",
            icon = Icons.Default.CreditCard,
            onClick = onNavigateToCards,
            testTag = "horizon_cards",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(HorizonGreenTint)
            .clickable { onClick() }
            .semantics { contentDescription = "$title، $subtitle" }
            .testTag(testTag)
            .padding(horizontal = 13.dp, vertical = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = HorizonGreen,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(9.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = HorizonInk
            ),
            maxLines = 1
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color(0xFF5E7C6E), fontSize = 10.5.sp
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun RecentTransactions(
    transactions: List<Transaction>,
    onTransactionClick: (Transaction) -> Unit,
    onSeeAll: () -> Unit
) {
    val recent = remember(transactions) {
        transactions.sortedByDescending { it.date }.take(5)
    }

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "تراکنش‌های اخیر",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = HorizonInk
                )
            )
            if (recent.isNotEmpty()) {
                Text(
                    text = "مشاهده همه",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = HorizonGreen, fontWeight = FontWeight.Bold, fontSize = 11.5.sp
                    ),
                    modifier = Modifier
                        .clickable { onSeeAll() }
                        .testTag("horizon_see_all")
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (recent.isEmpty()) {
            Text(
                text = "هنوز تراکنشی ثبت نشده. با دکمهٔ + اولین تراکنش را اضافه کنید.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = HorizonInkMuted, fontSize = 12.5.sp
                ),
                modifier = Modifier.padding(vertical = 14.dp)
            )
        } else {
            recent.forEach { tx ->
                TransactionRow(tx = tx, onClick = { onTransactionClick(tx) })
            }
        }
    }
}

@Composable
private fun TransactionRow(tx: Transaction, onClick: () -> Unit) {
    val tint = if (tx.isExpense) HorizonClay else HorizonGreen
    val well = if (tx.isExpense) HorizonClayTint else HorizonGreenTint

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(well),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (tx.isExpense) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(modifier = Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tx.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold, fontSize = 13.sp, color = HorizonInk
                ),
                maxLines = 1
            )
            Text(
                text = JalaliDate.fromTimestamp(tx.date).formatShort(),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = HorizonInkFaint, fontSize = 10.5.sp
                )
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Amount(MoneyFormat.signed(tx.amount, tx.isExpense), 13, FontWeight.Bold, tint)
    }
}

/**
 * The mockup has no goal section, but the goal editor is only reachable from
 * the old dashboard — this compact row keeps the feature alive on the new home.
 */
@Composable
private fun GoalRow(goal: FinancialGoal, onClick: () -> Unit) {
    val ratio = if (goal.targetAmount > 0)
        (goal.currentAmount.toFloat() / goal.targetAmount.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HorizonSurface)
            .border(1.dp, HorizonBorder, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(15.dp)
            .testTag("horizon_goal")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = goal.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold, fontSize = 13.sp, color = HorizonInk
                ),
                maxLines = 1
            )
            Amount("${(ratio * 100).toInt()}%", 12, FontWeight.Bold, HorizonGreen)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(HorizonGreenTint)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(HorizonGreen)
            )
        }
    }
}

private fun greetingForNow(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "صبح بخیر"
        in 12..16 -> "ظهر بخیر"
        in 17..20 -> "عصر بخیر"
        else -> "شب بخیر"
    }
}

/**
 * The goal editor, lifted out of DashboardScreen so the feature survives the
 * move to this home screen. Same fields, same callbacks.
 */
@Composable
private fun GoalEditorDialog(
    activeGoal: FinancialGoal?,
    onSave: (title: String, target: Long, saved: Long) -> Unit,
    onDelete: (goalId: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var tempName by remember(activeGoal) { mutableStateOf(activeGoal?.title ?: "") }
    var tempTarget by remember(activeGoal) {
        mutableStateOf(activeGoal?.targetAmount?.takeIf { it > 0 }?.toString() ?: "")
    }
    var tempSaved by remember(activeGoal) {
        mutableStateOf(activeGoal?.currentAmount?.takeIf { it > 0 }?.toString() ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (activeGoal == null) "ثبت هدف مالی" else "ویرایش هدف مالی") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("نام هدف (مثلا خرید لپ‌تاپ)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = tempTarget,
                    onValueChange = { value -> tempTarget = value.filter { it.isDigit() } },
                    label = { Text("مبلغ کل هدف (تومان)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = tempSaved,
                    onValueChange = { value -> tempSaved = value.filter { it.isDigit() } },
                    label = { Text("مبلغ پس‌انداز شده (تومان)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = tempTarget.toLongOrNull() ?: 0L
                    val saved = tempSaved.toLongOrNull() ?: 0L
                    if (tempName.isNotBlank() && target > 0) {
                        onSave(tempName.trim(), target, saved)
                    } else {
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = HorizonGreen)
            ) {
                Text("ذخیره هدف")
            }
        },
        dismissButton = {
            if (activeGoal != null) {
                TextButton(onClick = { onDelete(activeGoal.id) }) {
                    Text("حذف هدف", color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = onDismiss) { Text("انصراف") }
            }
        }
    )
}
