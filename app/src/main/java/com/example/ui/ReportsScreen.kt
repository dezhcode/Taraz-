package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Transaction
import com.example.data.countsAsExpense
import com.example.services.SmsTransactionCategorizerService
import com.example.ui.theme.*

@Composable
fun ReportsScreen(
    transactions: List<Transaction>,
    monthlyIncome: Long,
    monthlyExpense: Long
) {
    val scrollState = rememberScrollState()

    // Group expenses by category
    val expenses = transactions.filter { it.countsAsExpense }
    val totalExpenseSum = expenses.sumOf { it.amount }

    val categoryStats = expenses
        .groupBy { it.category }
        .mapValues { entry -> entry.value.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundLight)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "گزارش‌ها و تحلیل مالی",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = EbayDarkText,
                        fontSize = 20.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "سهم مخارج و تحلیل هوشمند رفتارهای مالی بر اساس eBay Evo",
                    style = MaterialTheme.typography.bodySmall.copy(color = EbaySecondaryText, fontSize = 12.sp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundLight)
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // 1. Balance Summary Card (eBay Evo Style)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, EbayBorderGray),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(EbayGreenLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = null,
                                    tint = EbayGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("دریافتی کل ماه", style = MaterialTheme.typography.bodySmall.copy(color = EbaySecondaryText))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${formatNumber(monthlyIncome)} تومان",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = EbayDarkText,
                                fontSize = 16.sp
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(40.dp)
                            .background(EbayBorderGray)
                            .align(Alignment.CenterVertically)
                    )

                    Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(EbayRedLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TrendingDown,
                                    contentDescription = null,
                                    tint = EbayRed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("مخارج کل ماه", style = MaterialTheme.typography.bodySmall.copy(color = EbaySecondaryText))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${formatNumber(monthlyExpense)} تومان",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = EbayDarkText,
                                fontSize = 16.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Custom Donut Pie Chart in eBay Evo Palette
            Text(
                text = "توزیع مخارج به تفکیک دسته",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = EbayDarkText,
                    fontSize = 16.sp
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, EbayBorderGray),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (totalExpenseSum == 0L) {
                        Text(
                            text = "جهت نمایش نمودار توزیع دسته‌ها، حداقل یک هزینه ثبت کنید.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = EbaySecondaryText),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 32.dp)
                        )
                    } else {
                        // eBay Evo Vibrant Color Palette
                        val colors = listOf(EbayBluePrimary, EbayGreen, EbayYellow, EbayRed, Color(0xFF8B5CF6), Color(0xFF06B6D4))

                        var animateTrigger by remember { mutableStateOf(false) }
                        val animationProgress by animateFloatAsState(
                            targetValue = if (animateTrigger) 1f else 0f,
                            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
                        )
                        LaunchedEffect(Unit) {
                            animateTrigger = true
                        }

                        Box(
                            modifier = Modifier
                                .size(150.dp)
                                .testTag("donut_chart_canvas_holder"),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                var currentStartAngle = -90f
                                categoryStats.forEachIndexed { index, stat ->
                                    val targetSweep = (stat.second.toFloat() / totalExpenseSum.toFloat()) * 360f
                                    val sweep = targetSweep * animationProgress
                                    val color = colors[index % colors.size]

                                    drawArc(
                                        color = color,
                                        startAngle = currentStartAngle,
                                        sweepAngle = sweep,
                                        useCenter = false,
                                        style = Stroke(width = 22.dp.toPx()),
                                        size = Size(size.width - 22.dp.toPx(), size.height - 22.dp.toPx()),
                                        topLeft = Offset(11.dp.toPx(), 11.dp.toPx())
                                    )
                                    currentStartAngle += sweep
                                }
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "مجموع مخارج",
                                    style = MaterialTheme.typography.labelSmall.copy(color = EbaySecondaryText, fontSize = 10.sp)
                                )
                                Text(
                                    text = "${formatNumber(totalExpenseSum)}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = EbayDarkText,
                                        fontSize = 14.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Category Legend Grid
                        categoryStats.forEachIndexed { index, stat ->
                            val color = colors[index % colors.size]
                            val percent = (stat.second * 100 / totalExpenseSum).toInt()

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stat.first,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = EbayDarkText)
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${formatNumber(stat.second)} تومان",
                                        style = MaterialTheme.typography.bodySmall.copy(color = EbaySecondaryText)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(color.copy(alpha = 0.12f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "$percent%",
                                            style = MaterialTheme.typography.labelSmall.copy(color = color, fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Monthly Reports Breakdown
            Text(
                text = "روند و تفکیک ماهانه",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = EbayDarkText,
                    fontSize = 16.sp
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            val monthlyReports = SmsTransactionCategorizerService.generateMonthlyReports(transactions)

            if (monthlyReports.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, EbayBorderGray)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "هنوز تراکنشی برای تفکیک ماهانه ثبت نشده است.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = EbaySecondaryText)
                        )
                    }
                }
            } else {
                monthlyReports.reversed().forEach { report ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, EbayBorderGray)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Month Header & Savings Rate
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(EbayBluePrimary)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = report.monthYear,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = EbayDarkText)
                                    )
                                }

                                val savingsPercent = if (report.totalIncome > 0) {
                                    ((report.totalIncome - report.totalExpense) * 100 / report.totalIncome).toInt()
                                } else 0

                                if (savingsPercent > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(EbayGreenLight)
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "پس‌انداز: $savingsPercent٪",
                                            style = MaterialTheme.typography.labelSmall.copy(color = EbayGreen, fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Income & Expense summary
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("کل دریافتی", style = MaterialTheme.typography.bodySmall.copy(color = EbaySecondaryText))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${formatNumber(report.totalIncome)} تومان",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = EbayGreen)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("کل مخارج", style = MaterialTheme.typography.bodySmall.copy(color = EbaySecondaryText))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${formatNumber(report.totalExpense)} تومان",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = EbayRed)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. AI Smart Optimization Tips Advice Card (eBay Evo Blue Card)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = EbayBlueLight),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, EbayBluePrimary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(EbayBluePrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = EbayBluePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "تحلیل سلامت بودجه تراز",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = EbayDarkText)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val ratio = if (monthlyIncome > 0) (monthlyExpense.toFloat() / monthlyIncome.toFloat()) else 0f
                    val budgetTips = when {
                        ratio == 0f -> "هنوز فعالیت مالی منظمی ثبت نشده است. تراکنش‌های خود را ثبت کنید تا تحلیل هوشمند ارائه شود."
                        ratio > 0.8f -> "زنگ خطر! مخارج این ماه شما ${(ratio * 100).toInt()}% درآمد شما بوده است. پیشنهاد می‌شود هزینه‌های غیرضروری را متوقف کنید."
                        ratio in 0.5f..0.8f -> "مواظب باشید! مخارج شما در سطح متوسط است. کنترل دسته‌های پرخرج توصیه می‌شود."
                        else -> "سلامت مالی عالی است! مخارج تنها ${(ratio * 100).toInt()}% درآمد است. می‌توانید پس‌انداز را افزایش دهید."
                    }

                    Text(
                        text = budgetTips,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = EbayDarkText,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

