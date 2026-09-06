package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Loan
import com.example.data.Transaction
import com.example.ui.EmeraldPrimary
import com.example.ui.NavySecondary
import com.example.ui.SlateGray
import com.example.ui.Tab
import java.text.NumberFormat
import java.util.Locale

data class AiFabState(
    val message: String,
    val prompt: String,
    val badge: String? = null,
    val isAlert: Boolean = false
)

@Composable
fun AiFloatingButton(
    currentTab: Tab,
    transactions: List<Transaction>,
    loans: List<Loan>,
    totalBalance: Long,
    monthlyIncome: Long,
    monthlyExpense: Long,
    onClick: (prefilledPrompt: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    // Dynamic rule evaluation for Fidar AI State Machine
    val aiState = remember(currentTab, transactions, loans, totalBalance, monthlyIncome, monthlyExpense) {
        // Rule 1: High Expenses warning (Waiting for help / Smart)
        if (monthlyExpense > 0 && monthlyIncome > 0 && monthlyExpense > monthlyIncome) {
            AiFabState(
                message = "هزینه‌هات از درآمد این ماه بیشتر شده! همفکری کنیم؟",
                prompt = "هزینه‌های ماه جاری من از درآمدم بیشتر شده. لطفاً تراکنش‌هایم را بررسی کن و یک راهکار فوری و عملی برای کاهش هزینه‌ها و مهار بودجه به من بده.",
                badge = "alert",
                isAlert = true
            )
        }
        // Rule 2: Big Salary / Income deposit in the last 2 days (Real-time trigger)
        else {
            val lastTwoDays = System.currentTimeMillis() - (2 * 24 * 60 * 60 * 1000)
            val recentSalary = transactions.firstOrNull { 
                it.date >= lastTwoDays && (it.category == "حقوق" || (!it.isExpense && it.amount >= 1000000))
            }
            if (recentSalary != null) {
                val formattedAmount = formatToman(recentSalary.amount)
                AiFabState(
                    message = "واریزی جدید $formattedAmount تومانی داری! بیا براش برنامه‌ریزی کنیم.",
                    prompt = "من به تازگی یک واریزی به مبلغ ${recentSalary.amount} تومان تحت عنوان '${recentSalary.title}' دریافت کردم. چطور می‌توانم این درآمد را به بهترین شکل بودجه‌بندی، پس‌انداز و سرمایه‌گذاری کنم؟",
                    badge = "income"
                )
            }
            // Rule 3: Low Balance alert (Real-time trigger)
            else if (totalBalance in 1..499999) {
                AiFabState(
                    message = "موجودی کیف پولت کمتر از ۵۰۰ هزار تومانه! مدیریت کنیم؟",
                    prompt = "موجودی کل حساب‌های من به شدت کاهش یافته و زیر ۵۰۰ هزار تومان است. چه توصیه‌ها و استراتژی‌های مدیریت هزینه‌ای در این شرایط برای من داری؟",
                    badge = "alert",
                    isAlert = true
                )
            }
            // Rule 4: Tab-specific rules (Smart)
            else {
                when (currentTab) {
                    Tab.REPORTS -> {
                        AiFabState(
                            message = "می‌خوای این نمودار مخارج رو برات تحلیل کنم؟",
                            prompt = "من در حال حاضر در صفحه گزارش‌ها هستم. لطفاً گزارش‌ها و نمودار سهم دسته‌بندی‌های مختلف هزینه‌های من را به صورت موشکافانه تحلیل کن و بگو چطور می‌توانم بهینه‌تر خرج کنم.",
                            badge = "reports"
                        )
                    }
                    Tab.TRANSACTIONS -> {
                        // Check for duplicate transactions (same title, amount, and close dates)
                        val duplicatesExist = checkDuplicateTransactions(transactions)
                        if (duplicatesExist) {
                            AiFabState(
                                message = "احتمالاً تراکنش تکراری ثبت کردی! بررسی کنیم؟",
                                prompt = "من متوجه چند تراکنش تکراری با عنوان و مبالغ مشابه در لیست تراکنش‌هایم شدم. لطفاً تراکنش‌های اخیر مرا بررسی کن و الگوها یا خطاهای احتمالی ثبت را به من بگو.",
                                badge = "duplicates"
                            )
                        } else {
                            AiFabState(
                                message = "تحلیل هوشمند و الگوهای هزینه‌کرد تراکنش‌ها",
                                prompt = "تراکنش‌های اخیر من را به صورت کامل تحلیل کن، الگوهای تکراری یا مخارج غیرضروری و مشکوک را شناسایی کن و پیشنهاد بده.",
                                badge = "transactions"
                            )
                        }
                    }
                    Tab.LOANS -> {
                        AiFabState(
                            message = "بیا برنامهٔ پرداخت اقساطت را مرور کنیم!",
                            prompt = "با توجه به وام‌ها و اقساط ثبت‌شده‌ام، برنامهٔ پرداخت را بررسی کن و بگو کدام قسط را زودتر تسویه کنم به‌صرفه‌تر است.",
                            badge = "loans"
                        )
                    }
                    else -> {
                        // General encouraging/smart status (Encouragement)
                        AiFabState(
                            message = "مدیریت مالی ترازت عالی پیش میره! سوالی داری؟",
                            prompt = "وضعیت کلی تراکنش‌ها، تعادل مالی و بودجه‌بندی من را بررسی کن و یک ارزیابی کلی همراه با تشویق و توصیه به من بده.",
                            badge = "info"
                        )
                    }
                }
            }
        }
    }

    var isBubbleDismissed by remember { mutableStateOf(false) }
    var lastEvaluatedMessage by remember { mutableStateOf("") }

    // If the evaluated message changes because of page/data update, show the bubble again!
    if (aiState.message != lastEvaluatedMessage) {
        lastEvaluatedMessage = aiState.message
        // Only show bubble automatically if it is a high-priority alert (isAlert == true).
        // For general informational tips, the AI remains quiet until clicked.
        isBubbleDismissed = !aiState.isAlert
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Slide-in Text Bubble (Left of the FAB button)
        AnimatedVisibility(
            visible = !isBubbleDismissed,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it / 2 }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it / 2 })
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .widthIn(max = 240.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onClick(aiState.prompt) }
                    )
                    .border(
                        width = 1.dp,
                        color = if (aiState.isAlert) Color(0x33EF4444) else Color(0x1F2D7D5F),
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = aiState.message,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = NavySecondary,
                            lineHeight = 18.sp
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    
                    Spacer(modifier = Modifier.width(6.dp))
                    
                    IconButton(
                        onClick = { isBubbleDismissed = true },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close advice",
                            tint = SlateGray,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // AI Circular Floating Button with pulsing style
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(elevation = 6.dp, shape = CircleShape)
                .background(color = EmeraldPrimary, shape = CircleShape)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = { onClick(null) }
                )
                .testTag("ai_assistant_fab"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "دستیار هوشمند",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )

            // Dynamic red ring or notification badge on top of FAB if it is an alert or has pending help
            if (aiState.badge != null) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = if (aiState.isAlert) Color(0xFFEF4444) else Color(0xFFF59E0B),
                            shape = CircleShape
                        )
                        .border(1.5.dp, Color.White, CircleShape)
                        .align(Alignment.TopEnd)
                )
            }
        }
    }
}

private fun formatToman(amount: Long): String {
    return try {
        NumberFormat.getNumberInstance(Locale.US).format(amount)
    } catch (e: Exception) {
        amount.toString()
    }
}

private fun checkDuplicateTransactions(transactions: List<Transaction>): Boolean {
    if (transactions.size < 2) return false
    val sorted = transactions.sortedByDescending { it.date }
    for (i in 0 until sorted.size - 1) {
        val current = sorted[i]
        val next = sorted[i + 1]
        if (current.title == next.title &&
            current.amount == next.amount &&
            current.isExpense == next.isExpense &&
            current.bankName == next.bankName &&
            Math.abs(current.date - next.date) < 600000
        ) {
            return true
        }
    }
    return false
}
