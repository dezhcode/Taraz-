package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.*
import com.example.data.Loan
import com.example.data.Transaction
import com.example.data.countsAsExpense
import com.example.data.countsAsIncome
import com.example.data.BankCard
import com.example.data.FinancialGoal
import com.example.data.FinancialHealthResult
import com.example.data.FinancialCoachInsight
import com.example.ui.components.IranianBankLogo
import com.example.ui.components.oceanicShimmer
import kotlin.math.absoluteValue
import java.util.Locale

// Number formatter utility
fun formatNumber(value: Long): String {
    return String.format(Locale.ENGLISH, "%,d", value)
}

@Composable
fun AnimatedBalanceText(
    targetBalance: Long,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier
) {
    var startAnimation by remember { mutableStateOf(false) }
    LaunchedEffect(targetBalance) {
        startAnimation = true
    }
    val animatedProgress by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "BalanceAnimation"
    )
    val currentBalance = (targetBalance * animatedProgress).toLong()
    Text(
        text = formatNumber(currentBalance),
        style = style,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    isLoading: Boolean = false,
    userProfile: UserProfile?,
    totalBalance: Long,
    monthlyIncome: Long,
    monthlyExpense: Long,
    todayExpense: Long = 0L,
    dailyBudget: Long = 0L,
    onSetDailyBudget: (Long) -> Unit = {},
    financialCoachInsight: FinancialCoachInsight = FinancialCoachInsight(),
    loans: List<Loan>,
    transactions: List<Transaction>,
    cards: List<BankCard>,
    activeGoal: FinancialGoal? = null,
    financialHealth: FinancialHealthResult = FinancialHealthResult(),
    onSaveGoal: (title: String, targetAmount: Long, currentAmount: Long) -> Unit = { _, _, _ -> },
    onDeleteGoal: (goalId: Int) -> Unit = {},
    onCardClick: (BankCard) -> Unit,
    onAddCardClick: () -> Unit,
    onAddTransactionClick: (isExpense: Boolean) -> Unit,
    onTransferClick: () -> Unit,
    onNavigateToAI: () -> Unit,
    onNavigateToLoans: () -> Unit,
    onNavigateToCards: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onSeeAllTransactions: () -> Unit = {},
    onTransactionClick: (Transaction) -> Unit
) {
    val scrollState = rememberScrollState()
    var selectedCardForSheet by remember { mutableStateOf<BankCard?>(null) }
    var showCardSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showGoalDialog by remember { mutableStateOf(false) }

    var isShimmerActive by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        isShimmerActive = false
    }
    val showSkeleton = isLoading || isShimmerActive

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .verticalScroll(scrollState)
            .padding(bottom = 16.dp)
    ) {
        // Calculate time-based greeting
        val currentHour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
        val timeGreeting = remember(currentHour) {
            when (currentHour) {
                in 5..11 -> "صبح بخیر"
                in 12..15 -> "ظهر بخیر"
                in 16..19 -> "عصر بخیر"
                else -> "شب بخیر"
            }
        }
        val userName = userProfile?.name?.takeIf { it.isNotBlank() } ?: "کاربر گرامی"
        val greetingText = "$timeGreeting $userName"

        // App Header Status Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 16.dp, bottom = 12.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = greetingText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = NavySecondary,
                    fontSize = 18.sp
                )
            )

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .shadow(elevation = 2.dp, shape = CircleShape, spotColor = Color.Black.copy(alpha = 0.04f), ambientColor = Color.Black.copy(alpha = 0.02f))
                    .clip(CircleShape)
                    .background(SurfaceWhite)
                    .clickable { onNavigateToSettings() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = NavySecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (showSkeleton) {
            FinancialOverviewCardsSkeleton(modifier = Modifier.fillMaxWidth())
        } else {
            // Dedicated Net Worth (ثروت خالص) Section in Evo Style
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "ثروت خالص",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = EbaySecondaryText,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnimatedBalanceText(
                        targetBalance = totalBalance,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = EbayDarkText,
                            fontSize = 30.sp,
                            letterSpacing = 0.sp
                        )
                    )
                    Text(
                        text = "تومان",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = EbaySecondaryText,
                            fontSize = 14.sp
                        ),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                val weeklyChangeText = remember(transactions, totalBalance) {
                    val now = System.currentTimeMillis()
                    val sevenDaysMs = 7 * 24 * 60 * 60 * 1000L
                    val thisWeekTx = transactions.filter { it.date >= now - sevenDaysMs }
                    val thisWeekIncome = thisWeekTx.filter { it.countsAsIncome }.sumOf { it.amount }
                    val thisWeekExpense = thisWeekTx.filter { it.countsAsExpense }.sumOf { it.amount }
                    val thisWeekNetFlow = thisWeekIncome - thisWeekExpense

                    val balanceSevenDaysAgo = totalBalance - thisWeekNetFlow

                    if (balanceSevenDaysAgo > 0) {
                        val change = (thisWeekNetFlow.toDouble() / balanceSevenDaysAgo.toDouble()) * 100
                        val sign = if (change >= 0) "+" else ""
                        "$sign${String.format(Locale.ENGLISH, "%.1f", change)}%"
                    } else if (thisWeekNetFlow > 0) {
                        "+100.0%"
                    } else if (thisWeekNetFlow < 0) {
                        "-100.0%"
                    } else {
                        "+0.0%"
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (weeklyChangeText.startsWith("-")) EbayRedLight else EbayGreenLight,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = "تغییرات این هفته: $weeklyChangeText",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (weeklyChangeText.startsWith("-")) EbayRed else EbayGreen,
                            fontSize = 11.5.sp
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Income and expense for the month, folded into the balance block:
                // they used to be three full-width cards competing with it.
                MonthlyFlowRow(
                    monthlyIncome = monthlyIncome,
                    monthlyExpense = monthlyExpense
                )
            }

        Spacer(modifier = Modifier.height(20.dp))

        // Redesigned Bank Cards Carousel (2 full cards + ~1/3 of next card visible)
        val pagerState = rememberPagerState(pageCount = { cards.size + 1 })
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                pageSize = PageSize.Fixed(145.dp),
                pageSpacing = 10.dp,
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) { page ->
                if (page < cards.size) {
                    val card = cards[page]
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(86.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceWhite)
                            .border(BorderStroke(1.dp, EbayBorderGray), RoundedCornerShape(16.dp))
                            .clickable {
                                onCardClick(card)
                            }
                            .padding(12.dp)
                            .testTag("dashboard_bank_card_item")
                    ) {
                        // Top Right: Colored Logo + Bank Name
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            IranianBankLogo(
                                bankName = card.bankName,
                                size = 20.dp,
                                hasCircleBg = true,
                                colorFilter = null
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = card.bankName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = EbayDarkText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Middle Center: Balance Amount
                        Text(
                            text = formatNumber(card.balance),
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = EbayDarkText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                letterSpacing = 0.5.sp
                            ),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(top = 12.dp)
                        )

                        // Bottom Left: Toman Currency Label
                        Text(
                            text = "تومان",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = EbaySecondaryText,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.align(Alignment.BottomEnd)
                        )
                    }
                } else {
                    // Trailing Card ("Add Card") in Evo Style
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(86.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(EbayBlueLight)
                            .border(BorderStroke(1.dp, EbayBluePrimary), RoundedCornerShape(16.dp))
                            .clickable { onAddCardClick() }
                            .testTag("dashboard_add_new_card_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = EbayBluePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "افزودن کارت",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = EbayBluePrimary,
                                    fontSize = 12.5.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Carousel Dots Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(cards.size + 1) { i ->
                    val isSelected = pagerState.currentPage == i
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (isSelected) 8.dp else 5.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) EbayBluePrimary else EbaySecondaryText.copy(alpha = 0.3f))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick access to the two screens that were previously unreachable:
        // BanksSubScreen and LoansSubScreen existed and worked, but nothing in
        // the UI ever called onNavigateToCards / onNavigateToLoans.
        QuickAccessSection(
            loanCount = loans.count { !it.isSettled },
            cardCount = cards.size,
            onNavigateToLoans = onNavigateToLoans,
            onNavigateToCards = onNavigateToCards
        )

        Spacer(modifier = Modifier.height(16.dp))

        // One "today" card instead of three advice cards (health score, AI coach,
        // budget) competing for the same attention.
        TodayCard(
            todayExpense = todayExpense,
            dailyBudget = dailyBudget,
            onSetDailyBudget = onSetDailyBudget,
            insight = financialCoachInsight,
            onNavigateToAI = onNavigateToAI
        )

        Spacer(modifier = Modifier.height(16.dp))

        // The thing people open a finance app for: what happened lately.
        RecentTransactionsSection(
            transactions = transactions,
            onTransactionClick = onTransactionClick,
            onSeeAll = onSeeAllTransactions
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Health score and goal are reflective numbers, looked at monthly rather
        // than daily, so they sit below the daily material instead of above it.
        FinancialOverviewSection(
            financialHealth = financialHealth,
            activeGoal = activeGoal,
            onNavigateToAI = onNavigateToAI,
            onGoalClick = { showGoalDialog = true }
        )
        }

        Spacer(modifier = Modifier.height(100.dp)) // Padding for bottom navigation bar
    }

    // Active Goal Creation / Edit Dialog
    if (showGoalDialog) {
        var tempName by remember(activeGoal) { mutableStateOf(activeGoal?.title ?: "") }
        var tempTarget by remember(activeGoal) { mutableStateOf(if (activeGoal != null && activeGoal.targetAmount > 0) activeGoal.targetAmount.toString() else "") }
        var tempSaved by remember(activeGoal) { mutableStateOf(if (activeGoal != null && activeGoal.currentAmount > 0) activeGoal.currentAmount.toString() else "") }

        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            title = {
                Text(
                    text = if (activeGoal == null) "ایجاد هدف مالی جدید" else "ویرایش هدف مالی",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = NavySecondary)
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        label = { Text("نام هدف (مثلا خرید لپ‌تاپ)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = tempTarget,
                        onValueChange = { tempTarget = it.filter { c -> c.isDigit() } },
                        label = { Text("مبلغ کل هدف (تومان)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = tempSaved,
                        onValueChange = { tempSaved = it.filter { c -> c.isDigit() } },
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
                            onSaveGoal(tempName.trim(), target, saved)
                        }
                        showGoalDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("ذخیره هدف")
                }
            },
            dismissButton = {
                if (activeGoal != null) {
                    TextButton(
                        onClick = {
                            onDeleteGoal(activeGoal.id)
                            showGoalDialog = false
                        }
                    ) {
                        Text("حذف هدف", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    TextButton(onClick = { showGoalDialog = false }) {
                        Text("انصراف")
                    }
                }
            }
        )
    }
}

// Helper for Persian Digits
fun toPersianDigits(input: String): String {
    val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
    return input.map { char ->
        if (char in '0'..'9') {
            persianDigits[char - '0']
        } else {
            char
        }
    }.joinToString("")
}

fun formatPersianNumber(value: Long): String {
    val formatted = String.format(Locale.ENGLISH, "%,d", value)
    return toPersianDigits(formatted)
}

@Composable
fun FinancialOverviewSection(
    financialHealth: FinancialHealthResult,
    activeGoal: FinancialGoal?,
    onNavigateToAI: () -> Unit,
    onGoalClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedScore by animateIntAsState(
        targetValue = financialHealth.score,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "HealthScoreAnimation"
    )

    val healthProgressFloat by animateFloatAsState(
        targetValue = animatedScore / 100f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "HealthProgressAnimation"
    )

    val (healthColor, healthStatusText) = when {
        financialHealth.status == "no_data" || animatedScore == 0 -> Pair(SlateGray, "اطلاعات مالی بیشتری ثبت کنید")
        animatedScore >= 80 -> Pair(Color(0xFF10B981), "عالی")
        animatedScore >= 60 -> Pair(Color(0xFFF59E0B), "خوب")
        else -> Pair(Color(0xFFEF4444), "نیاز به بهبود")
    }

    // Active Goal Calculations
    val hasActiveGoal = activeGoal != null && activeGoal.title.isNotBlank()
    val goalTarget = activeGoal?.targetAmount ?: 0L
    val goalSaved = activeGoal?.currentAmount ?: 0L

    val goalPercent = if (hasActiveGoal && goalTarget > 0) {
        ((goalSaved.toFloat() / goalTarget.toFloat()) * 100).toInt().coerceIn(0, 100)
    } else {
        0
    }

    val animatedGoalPercent by animateIntAsState(
        targetValue = goalPercent,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "GoalPercentAnimation"
    )

    val animatedGoalProgressFloat by animateFloatAsState(
        targetValue = if (hasActiveGoal && goalTarget > 0) (goalSaved.toFloat() / goalTarget.toFloat()).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "GoalProgressAnimation"
    )

    val remainingGoalAmount = if (hasActiveGoal) (goalTarget - goalSaved).coerceAtLeast(0) else 0L

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- CARD 1: Financial Health (سلامت مالی) ---
        Card(
            modifier = Modifier
                .weight(1f)
                .height(142.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable { onNavigateToAI() }
                .testTag("financial_health_card"),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, EbayBorderGray),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Row: Title + Red Heart Icon Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "سلامت مالی",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = EbayDarkText,
                            fontSize = 14.sp
                        )
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(EbayRedLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "سلامت مالی",
                            tint = EbayRed,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                // Score Display
                Text(
                    text = "${toPersianDigits(animatedScore.toString())} / ${toPersianDigits("100")}",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = EbayDarkText,
                        fontSize = 18.sp
                    )
                )

                // Progress Bar & Status Text
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { healthProgressFloat },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        color = healthColor,
                        trackColor = healthColor.copy(alpha = 0.15f)
                    )

                    Text(
                        text = healthStatusText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = healthColor,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // --- CARD 2: Active Goal (هدف فعال) ---
        Card(
            modifier = Modifier
                .weight(1f)
                .height(142.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable { onGoalClick() }
                .testTag("active_goal_card"),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, EbayBorderGray),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Row: Title + Target Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "هدف فعال",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = EbayDarkText,
                            fontSize = 14.sp
                        )
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(EbayBlueLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrackChanges,
                            contentDescription = "هدف فعال",
                            tint = EbayBluePrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                if (hasActiveGoal) {
                    // Goal Title + Percent Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = activeGoal?.title ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = EbayDarkText,
                                fontSize = 13.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${toPersianDigits(animatedGoalPercent.toString())}%",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = EbayBluePrimary,
                                fontSize = 14.sp
                            )
                        )
                    }

                    // Progress Bar & Remaining Amount
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { animatedGoalProgressFloat },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            color = EbayBluePrimary,
                            trackColor = EbayBlueLight
                        )

                        Text(
                            text = "${formatPersianNumber(remainingGoalAmount)} تومان باقی مانده",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium,
                                color = EbaySecondaryText,
                                fontSize = 10.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    // Empty State
                    ActiveGoalEmptyStateIllustration(
                        onGoalClick = onGoalClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun MonthlySummaryCardsSection(
    monthlyIncome: Long,
    monthlyExpense: Long,
    modifier: Modifier = Modifier
) {
    val monthlySavings = monthlyIncome - monthlyExpense

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Card 1: Monthly Income
        MonthlySummaryCard(
            modifier = Modifier.weight(1f),
            title = "درآمد ماه",
            value = monthlyIncome,
            icon = Icons.Default.TrendingUp,
            iconTint = Color(0xFF10B981),
            valueColor = if (monthlyIncome > 0) Color(0xFF10B981) else SlateGray,
            statusText = if (monthlyIncome > 0) "ثبت شده" else "بدون ورودی",
            statusColor = if (monthlyIncome > 0) Color(0xFF10B981) else SlateGray
        )

        // Card 2: Monthly Expense
        MonthlySummaryCard(
            modifier = Modifier.weight(1f),
            title = "هزینه ماه",
            value = monthlyExpense,
            icon = Icons.Default.TrendingDown,
            iconTint = Color(0xFFEF4444),
            valueColor = if (monthlyExpense > 0) Color(0xFFEF4444) else SlateGray,
            statusText = if (monthlyExpense > 0) "پرداخت شده" else "بدون خروجی",
            statusColor = if (monthlyExpense > 0) Color(0xFFEF4444) else SlateGray
        )

        // Card 3: Monthly Savings
        MonthlySummaryCard(
            modifier = Modifier.weight(1f),
            title = "پس‌انداز",
            value = monthlySavings,
            icon = Icons.Default.AccountBalanceWallet,
            iconTint = if (monthlySavings >= 0) Color(0xFF3B82F6) else Color(0xFFEF4444),
            valueColor = if (monthlySavings > 0) Color(0xFF3B82F6) else if (monthlySavings < 0) Color(0xFFEF4444) else SlateGray,
            statusText = if (monthlySavings > 0) "مثبت" else if (monthlySavings < 0) "کسری مالی" else "صفر",
            statusColor = if (monthlySavings > 0) Color(0xFF3B82F6) else if (monthlySavings < 0) Color(0xFFEF4444) else SlateGray
        )
    }
}

@Composable
fun MonthlySummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    value: Long,
    icon: ImageVector,
    iconTint: Color,
    valueColor: Color,
    statusText: String,
    statusColor: Color
) {
    Surface(
        modifier = modifier
            .height(118.dp)
            .clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, EbayBorderGray)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            // Header: Icon & Status Indicator Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(statusColor.copy(alpha = 0.1f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            fontSize = 8.5.sp
                        )
                    )
                }
            }

            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    color = EbaySecondaryText,
                    fontSize = 11.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Value & Toman
            Column {
                Text(
                    text = formatNumber(value),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = valueColor,
                        fontSize = 12.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "تومان",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Normal,
                        color = EbaySecondaryText,
                        fontSize = 8.5.sp
                    )
                )
            }
        }
    }
}

@Composable
fun DailyBudgetCardSection(
    todayExpense: Long,
    dailyBudget: Long,
    onSetDailyBudget: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showBudgetDialog by remember { mutableStateOf(false) }

    val percentage = if (dailyBudget > 0) {
        ((todayExpense.toDouble() / dailyBudget.toDouble()) * 100).toInt().coerceAtLeast(0)
    } else 0

    val progressRatio = if (dailyBudget > 0) {
        (todayExpense.toFloat() / dailyBudget.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progressRatio,
        animationSpec = tween(durationMillis = 800),
        label = "budgetProgress"
    )

    val (statusText, statusColor) = when {
        dailyBudget == 0L -> Pair("بودجه‌ای برای امروز تعیین نشده", EbaySecondaryText)
        todayExpense > dailyBudget -> Pair("امروز از بودجه عبور کردی", EbayRed)
        percentage >= 80 -> Pair("نزدیک شدن به سقف بودجه", Color(0xFFF59E0B))
        else -> Pair("در محدوده بودجه هستی", EbayGreen)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { showBudgetDialog = true },
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, EbayBorderGray)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Title & Action Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(EbayBlueLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "بودجه امروز",
                            tint = EbayBluePrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "بودجه امروز",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = EbayDarkText,
                            fontSize = 15.sp
                        )
                    )
                }

                Surface(
                    onClick = { showBudgetDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    color = EbayBlueLight
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "تنظیم بودجه",
                            tint = EbayBluePrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "تنظیم بودجه",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = EbayBluePrimary,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            // Amounts Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "امروز خرج کردی",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = EbaySecondaryText,
                            fontSize = 11.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${formatNumber(todayExpense)} تومان",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (todayExpense > dailyBudget && dailyBudget > 0) EbayRed else EbayDarkText,
                            fontSize = 18.sp
                        )
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "بودجه مجاز",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = EbaySecondaryText,
                            fontSize = 11.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (dailyBudget > 0) "${formatNumber(dailyBudget)} تومان" else "تعیین نشده",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = EbaySecondaryText,
                            fontSize = 14.sp
                        )
                    )
                }
            }

            // Progress Bar & Percentage Label
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "میزان مصرف بودجه",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = EbaySecondaryText,
                            fontSize = 10.5.sp
                        )
                    )
                    Text(
                        text = if (dailyBudget > 0) "${percentage}%" else "0%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            fontSize = 11.sp
                        )
                    )
                }

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = statusColor,
                    trackColor = statusColor.copy(alpha = 0.15f)
                )
            }

            // Bottom Status Indicator Pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(statusColor.copy(alpha = 0.1f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        fontSize = 11.5.sp
                    )
                )
            }
        }
    }

    if (showBudgetDialog) {
        SetDailyBudgetDialog(
            currentBudget = dailyBudget,
            onDismiss = { showBudgetDialog = false },
            onConfirm = { newBudget ->
                onSetDailyBudget(newBudget)
                showBudgetDialog = false
            }
        )
    }
}

@Composable
fun SetDailyBudgetDialog(
    currentBudget: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var budgetText by remember { mutableStateOf(if (currentBudget > 0) currentBudget.toString() else "") }
    val parsedBudget = budgetText.toLongOrNull() ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "تنظیم بودجه روزانه",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = NavySecondary
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "مبلغ بودجه روزانه مورد نظر خود را به تومان وارد کنید:",
                    style = MaterialTheme.typography.bodySmall.copy(color = SlateGray)
                )

                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() }) {
                            budgetText = input
                        }
                    },
                    label = { Text("بودجه روزانه (تومان)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = Color.Black.copy(alpha = 0.12f)
                    )
                )

                if (parsedBudget > 0) {
                    Text(
                        text = "معادل: ${formatNumber(parsedBudget)} تومان",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = EmeraldPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                // Quick suggestions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(500000L, 800000L, 1000000L, 2000000L).forEach { suggestion ->
                        Surface(
                            onClick = { budgetText = suggestion.toString() },
                            shape = RoundedCornerShape(8.dp),
                            color = EmeraldPrimary.copy(alpha = 0.1f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "${suggestion / 1000} هزار",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldPrimary,
                                    textAlign = TextAlign.Center
                                ),
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(parsedBudget) },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("ثبت بودجه", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف", color = SlateGray)
            }
        }
    )
}

@Composable
fun AiFinancialCoachCardSection(
    insight: FinancialCoachInsight,
    onNavigateToAI: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (insight.message.isBlank()) {
        AiCoachEmptyStateIllustration(
            onNavigateToAI = onNavigateToAI,
            modifier = modifier
        )
        return
    }

    val (typeColor, typeBg, typeTitle, typeIcon) = when (insight.type.lowercase()) {
        "danger" -> QuadrupleCardStyle(
            Color(0xFFEF4444),
            Color(0xFFEF4444).copy(alpha = 0.08f),
            "هشدار مهم مالی",
            Icons.Default.Warning
        )
        "warning" -> QuadrupleCardStyle(
            Color(0xFFF59E0B),
            Color(0xFFF59E0B).copy(alpha = 0.08f),
            "پیشنهاد هوشمند تراز",
            Icons.Default.Lightbulb
        )
        "success" -> QuadrupleCardStyle(
            Color(0xFF10B981),
            Color(0xFF10B981).copy(alpha = 0.08f),
            "عملکرد عالی",
            Icons.Default.CheckCircle
        )
        "suggestion" -> QuadrupleCardStyle(
            Color(0xFF3B82F6),
            Color(0xFF3B82F6).copy(alpha = 0.08f),
            "بینش مربی مالی",
            Icons.Default.AutoAwesome
        )
        else -> QuadrupleCardStyle(
            SlateGray,
            Color.Black.copy(alpha = 0.04f),
            "دستیار هوشمند مالی",
            Icons.Default.Psychology
        )
    }

    AnimatedContent(
        targetState = insight,
        transitionSpec = {
            (fadeIn(animationSpec = tween(400)) + slideInVertically(animationSpec = tween(400)) { height -> height / 3 }) togetherWith
                    fadeOut(animationSpec = tween(300))
        },
        label = "aiInsightTransition"
    ) { currentInsight ->
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable { onNavigateToAI() },
            shape = RoundedCornerShape(16.dp),
            color = SurfaceWhite,
            border = BorderStroke(1.dp, typeColor.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(typeBg)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(typeColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = typeIcon,
                                contentDescription = "AI Coach",
                                tint = typeColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = typeTitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = EbayDarkText,
                                fontSize = 14.5.sp
                            )
                        )
                    }

                    // Priority tag badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = typeColor.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = typeColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "تحلیل AI",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = typeColor,
                                    fontSize = 10.5.sp
                                )
                            )
                        }
                    }
                }

                // Message body
                Text(
                    text = currentInsight.message,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = EbayDarkText,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp
                    )
                )

                // Optional Action Button
                currentInsight.action?.let { actionText ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            onClick = { onNavigateToAI() },
                            shape = RoundedCornerShape(16.dp),
                            color = typeColor
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = actionText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 11.5.sp
                                    )
                                )
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class QuadrupleCardStyle(
    val color: Color,
    val bg: Color,
    val title: String,
    val icon: ImageVector
)

@Composable
fun ActiveGoalEmptyStateIllustration(
    onGoalClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "هدفی تعریف نشده است",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = NavySecondary,
                    fontSize = 12.sp
                )
            )
            Text(
                text = "برای پس‌انداز منظم هدف جدیدی بسازید",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = SlateGray,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Surface(
                onClick = onGoalClick,
                shape = RoundedCornerShape(8.dp),
                color = EmeraldPrimary,
                shadowElevation = 0.dp
            ) {
                Text(
                    text = "+ ایجاد هدف جدید",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 10.5.sp
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Minimalist Vector Target Canvas Illustration
        Box(
            modifier = Modifier.size(52.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerOffset = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2 - 3.dp.toPx()

                drawCircle(
                    color = EmeraldPrimary.copy(alpha = 0.08f),
                    radius = radius,
                    center = centerOffset
                )

                drawCircle(
                    color = EmeraldPrimary.copy(alpha = 0.40f),
                    radius = radius,
                    center = centerOffset,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )
                )

                drawCircle(
                    color = EmeraldPrimary.copy(alpha = 0.18f),
                    radius = radius * 0.55f,
                    center = centerOffset
                )
            }

            Icon(
                imageVector = Icons.Default.Flag,
                contentDescription = null,
                tint = EmeraldPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun AiCoachEmptyStateIllustration(
    onNavigateToAI: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = Color.Black.copy(alpha = 0.04f)
            )
            .clickable { onNavigateToAI() },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(EmeraldPrimary.copy(alpha = 0.04f))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(EmeraldPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = "دستیار هوشمند مالی تراز",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = NavySecondary,
                            fontSize = 13.5.sp
                        )
                    )
                }

                Text(
                    text = "مربی مالی در حال بررسی الگوی درآمد و مخارج شماست. با ثبت تراکنش‌های جدید، هوشمندترین بینش‌ها اینجا نمایش داده می‌شوند.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = SlateGray,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp
                    )
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Minimal Vector Graphic for AI Coach
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerOffset = Offset(size.width / 2, size.height / 2)
                    val radius = size.minDimension / 2 - 2.dp.toPx()

                    drawCircle(
                        color = Color(0xFF3B82F6).copy(alpha = 0.08f),
                        radius = radius,
                        center = centerOffset
                    )

                    drawCircle(
                        color = Color(0xFF3B82F6).copy(alpha = 0.25f),
                        radius = radius,
                        center = centerOffset,
                        style = Stroke(
                            width = 1.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                    )

                    drawCircle(
                        color = EmeraldPrimary.copy(alpha = 0.15f),
                        radius = radius * 0.6f,
                        center = centerOffset
                    )
                }

                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
fun Modifier.skeletonShimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslation"
    )

    val shimmerColors = listOf(
        Color(0xFFE2E8F0).copy(alpha = 0.5f),
        Color(0xFFF1F5F9).copy(alpha = 0.95f),
        Color(0xFFE2E8F0).copy(alpha = 0.5f)
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 300f, translateAnim - 300f),
        end = Offset(translateAnim, translateAnim)
    )

    return this.background(brush)
}

@Composable
fun FinancialOverviewCardsSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Net Worth Header Skeleton
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .skeletonShimmer()
            )
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .skeletonShimmer()
            )
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .skeletonShimmer()
            )
        }

        // Bank Cards Carousel Skeleton
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(145.dp)
                    .height(86.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .skeletonShimmer()
            )
            Box(
                modifier = Modifier
                    .width(145.dp)
                    .height(86.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .skeletonShimmer()
            )
        }

        // Health & Goal Cards Skeleton
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(142.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .skeletonShimmer()
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(142.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .skeletonShimmer()
            )
        }

        // Monthly Summary Cards Skeleton
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(105.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .skeletonShimmer()
                )
            }
        }

        // Daily Budget Card Skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(20.dp))
                .skeletonShimmer()
        )

        // AI Insight Card Skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(20.dp))
                .skeletonShimmer()
        )
    }
}

@Composable
fun QuickAccessSection(
    loanCount: Int,
    cardCount: Int,
    onNavigateToLoans: () -> Unit,
    onNavigateToCards: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickAccessCard(
            title = "اقساط و وامها",
            subtitle = if (loanCount > 0)
                "${toPersianDigits(loanCount.toString())} وام فعال"
            else "ثبت وام جدید",
            icon = Icons.Default.AccountBalance,
            accent = EbayBluePrimary,
            onClick = onNavigateToLoans,
            testTag = "quick_access_loans",
            modifier = Modifier.weight(1f)
        )
        QuickAccessCard(
            title = "کارتهای بانکی",
            subtitle = if (cardCount > 0)
                "${toPersianDigits(cardCount.toString())} کارت ثبتشده"
            else "افزودن کارت",
            icon = Icons.Default.CreditCard,
            accent = EmeraldPrimary,
            onClick = onNavigateToCards,
            testTag = "quick_access_cards",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickAccessCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(96.dp)
            .clickable { onClick() }
            .semantics { contentDescription = "$title، $subtitle" }
            .testTag(testTag),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(19.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = EbaySecondaryText,
                        fontSize = 11.sp
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Income and expense for the month, as two quiet figures under the balance.
 * Previously three full-width cards, which made the screen read as if the
 * monthly totals mattered as much as the balance itself.
 */
@Composable
fun MonthlyFlowRow(
    monthlyIncome: Long,
    monthlyExpense: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MonthlyFlowItem(
            label = "دریافتی ماه",
            amount = monthlyIncome,
            icon = Icons.Default.ArrowDownward,
            tint = EbayGreen
        )
        Box(
            modifier = Modifier
                .height(28.dp)
                .width(1.dp)
                .background(EbayBorderGray)
        )
        MonthlyFlowItem(
            label = "هزینه ماه",
            amount = monthlyExpense,
            icon = Icons.Default.ArrowUpward,
            tint = EbayRed
        )
    }
}

@Composable
private fun MonthlyFlowItem(
    label: String,
    amount: Long,
    icon: ImageVector,
    tint: Color
) {
    Column(horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = EbaySecondaryText,
                    fontSize = 11.sp
                )
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = formatPersianNumber(amount),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        )
    }
}

/**
 * The single "today" card: budget progress, plus the coach's line inside it
 * when there is something worth saying. Replaces two separate cards that both
 * told the user how their day is going.
 */
@Composable
fun TodayCard(
    todayExpense: Long,
    dailyBudget: Long,
    onSetDailyBudget: (Long) -> Unit,
    insight: FinancialCoachInsight,
    onNavigateToAI: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showBudgetDialog by remember { mutableStateOf(false) }

    val ratio = if (dailyBudget > 0) (todayExpense.toFloat() / dailyBudget.toFloat()) else 0f
    val progress by animateFloatAsState(
        targetValue = ratio.coerceIn(0f, 1f),
        animationSpec = tween(700),
        label = "budget_progress"
    )
    val over = dailyBudget > 0 && todayExpense > dailyBudget
    val near = dailyBudget > 0 && !over && ratio >= 0.8f
    val accent = when {
        over -> EbayRed
        near -> AlertOrange
        else -> EmeraldPrimary
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("today_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "امروز",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                )
                Text(
                    text = if (dailyBudget > 0) "تنظیم بودجه" else "تعیین بودجه روزانه",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = EbayBluePrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier
                        .clickable { showBudgetDialog = true }
                        .testTag("today_set_budget")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatPersianNumber(todayExpense),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (dailyBudget > 0)
                        "از ${formatPersianNumber(dailyBudget)} تومان"
                    else "تومان خرج امروز",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = EbaySecondaryText,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }

            if (dailyBudget > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = accent,
                    trackColor = accent.copy(alpha = 0.12f)
                )
            }

            // The coach speaks inside this card rather than in one of its own.
            if (insight.message.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.07f))
                        .clickable { onNavigateToAI() }
                        .padding(12.dp)
                        .testTag("today_coach_line"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = insight.message,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        ),
                        maxLines = 2,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
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

/**
 * Last few transactions, with a link into the full list.
 * The data was already being passed to this screen and only used to compute a
 * weekly delta; the user could not see a single transaction without leaving home.
 */
@Composable
fun RecentTransactionsSection(
    transactions: List<Transaction>,
    onTransactionClick: (Transaction) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val recent = remember(transactions) {
        transactions.sortedByDescending { it.date }.take(5)
    }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "تراکنش‌های اخیر",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            )
            if (recent.isNotEmpty()) {
                Text(
                    text = "مشاهده همه",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = EbayBluePrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier
                        .clickable { onSeeAll() }
                        .testTag("recent_see_all")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (recent.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = "هنوز تراکنشی ثبت نشده. با دکمه + اولین تراکنش را اضافه کنید.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = EbaySecondaryText,
                        fontSize = 12.5.sp
                    ),
                    modifier = Modifier.padding(18.dp)
                )
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    recent.forEachIndexed { index, tx ->
                        RecentTransactionRow(
                            transaction = tx,
                            onClick = { onTransactionClick(tx) }
                        )
                        if (index < recent.lastIndex) {
                            HorizontalDivider(
                                color = EbayBorderGray.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentTransactionRow(
    transaction: Transaction,
    onClick: () -> Unit
) {
    val tint = if (transaction.isExpense) EbayRed else EbayGreen
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (transaction.isExpense) Icons.Default.ArrowUpward
                              else Icons.Default.ArrowDownward,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(17.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = com.example.utils.JalaliDate.fromTimestamp(transaction.date).formatShort() +
                        " • " + transaction.category,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = EbaySecondaryText,
                    fontSize = 11.sp
                ),
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = (if (transaction.isExpense) "- " else "+ ") + formatPersianNumber(transaction.amount),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = tint,
                fontSize = 13.sp
            )
        )
    }
}



