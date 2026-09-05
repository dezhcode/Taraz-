package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.BankCard
import com.example.data.Loan
import com.example.ui.components.oceanicShimmer
import com.example.ui.components.IranianBankLogo
import com.example.ui.theme.*

// --- 1. BANKS SUB-SCREEN ---

@Composable
fun BanksSubScreen(
    cards: List<BankCard>,
    onBack: () -> Unit,
    onAddCard: (bankName: String, cardNumber: String, balance: Long, cardHolderName: String) -> Unit
) {
    var showAddCardDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundLight)
                    .statusBarsPadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("banks_back_button")
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت", tint = EbayDarkText)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "حساب‌ها و کارت‌های بانکی",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = EbayDarkText, fontSize = 20.sp)
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddCardDialog = true },
                containerColor = EbayBluePrimary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.testTag("add_card_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "افزودن کارت", modifier = Modifier.size(24.dp))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundLight)
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "کارت‌های فعال در سیستم",
                style = MaterialTheme.typography.bodyMedium.copy(color = EbaySecondaryText, fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(vertical = 12.dp)
            )

            if (cards.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("هیچ کارتی تعریف نشده است. با استفاده از دکمه + اولین کارت خود را تعریف کنید.")
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(cards) { card ->
                        BankCardItem(card = card)
                    }
                }
            }
        }

        if (showAddCardDialog) {
            AddCardDialog(
                onDismiss = { showAddCardDialog = false },
                onConfirm = { name, number, bal, holder ->
                    onAddCard(name, number, bal, holder)
                    showAddCardDialog = false
                }
            )
        }
    }
}

@Composable
fun BankCardItem(card: BankCard) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .shadow(6.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .oceanicShimmer(card.bankName, card.id.toString())
                .padding(20.dp)
        ) {
            // Large background tilted logo watermark
            IranianBankLogo(
                bankName = card.bankName,
                size = 140.dp,
                hasCircleBg = false,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 20.dp, y = 20.dp)
                    .graphicsLayer {
                        rotationZ = -20f
                        this.alpha = 0.12f
                    }
            )

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Bank Name
                Text(
                    text = card.bankName,
                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                )

                // Masked Card Number
                Text(
                    text = card.cardNumber,
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        fontSize = 18.sp
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )

                // Balances details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "موجودی کل حساب",
                            style = MaterialTheme.typography.labelMedium.copy(color = Color.White.copy(alpha = 0.6f))
                        )
                        Text(
                            text = "${formatNumber(card.balance)} تومان",
                            style = MaterialTheme.typography.titleLarge.copy(color = Color.White, fontWeight = FontWeight.Black)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "صاحب کارت",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.5f))
                        )
                        Text(
                            text = card.cardHolderName,
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddCardDialog(
    onDismiss: () -> Unit,
    onConfirm: (bankName: String, cardNumber: String, balance: Long, cardHolderName: String) -> Unit
) {
    var bankName by remember { mutableStateOf("") }
    var cardNumber by remember { mutableStateOf("") }
    var initialBalance by remember { mutableStateOf("") }
    var cardHolderName by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("add_card_dialog_surface"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "افزودن کارت بانکی",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = NavySecondary)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = SlateGray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text("نام بانک (مثال: بانک ملی)") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_add_card_bank_name")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = cardHolderName,
                    onValueChange = { cardHolderName = it },
                    label = { Text("نام صاحب کارت (مثال: علیرضا محمدی)") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_add_card_holder_name")
                )

                Spacer(modifier = Modifier.height(12.dp))

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = cardNumber,
                        onValueChange = { input ->
                            if (input.length <= 16 && input.all { it.isDigit() }) {
                                cardNumber = input
                                if (input.length >= 6) {
                                    val detectedBank = com.example.sms.util.BankUtils.detectBankFromCardNumber(input)
                                    if (detectedBank != null) {
                                        bankName = detectedBank
                                    }
                                }
                            }
                        },
                        label = { Text("شماره کارت (16 رقم)") },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_add_card_number"),
                        leadingIcon = {
                            if (bankName.isNotEmpty()) {
                                Box(modifier = Modifier.padding(start = 8.dp)) {
                                    IranianBankLogo(bankName = bankName, size = 24.dp)
                                }
                            } else {
                                Icon(imageVector = Icons.Default.CreditCard, contentDescription = null, tint = SlateGray)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = initialBalance,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() }) {
                                initialBalance = input
                            }
                        },
                        label = { Text("موجودی اولیه (تومان)") },
                        placeholder = { Text("مثال: 5,000,000") },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_add_card_balance")
                    )
                }

                if (initialBalance.isNotEmpty()) {
                    val formatted = initialBalance.toLongOrNull()?.let { formatNumber(it) } ?: initialBalance
                    Text(
                        text = "معادل: $formatted تومان",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = EmeraldPrimary,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (showError) {
                    Text(
                        text = "تمامی فیلدها را به درستی پر نمایید.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = ErrorRed),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                Button(
                    onClick = {
                        val bal = initialBalance.toLongOrNull()
                        if (bankName.isBlank() || cardNumber.length < 16 || bal == null || cardHolderName.isBlank()) {
                            showError = true
                        } else {
                            val formattedNumber = "${cardNumber.substring(0,4)}****${cardNumber.substring(12,16)}"
                            onConfirm(bankName, formattedNumber, bal, cardHolderName)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("confirm_add_card_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("ثبت کارت", style = MaterialTheme.typography.bodyLarge.copy(color = Color.White, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

// --- 2. LOANS & INSTALLMENTS SCREEN ---

@Composable
fun LoansSubScreen(
    loans: List<Loan>,
    cards: List<BankCard>,
    onBack: () -> Unit,
    onPayInstallment: (Loan) -> Unit,
    onAddLoan: (bankName: String, loanName: String, totalAmount: Long, paidAmount: Long, installmentAmount: Long, dueDate: String) -> Unit
) {
    var showAddLoanDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundLight)
                    .statusBarsPadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("loans_back_button")
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NavySecondary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "مدیریت وام‌ها و اقساط",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = NavySecondary)
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddLoanDialog = true },
                containerColor = EmeraldPrimary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.testTag("add_loan_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Loan", modifier = Modifier.size(24.dp))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundLight)
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // General Loans stats header card
            val totalLoanSum = loans.sumOf { it.totalAmount }
            val paidLoanSum = loans.sumOf { it.paidAmount }
            val remainingDebt = totalLoanSum - paidLoanSum

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                colors = CardDefaults.cardColors(containerColor = NavySecondary),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "وضعیت بدهی کل وام‌ها",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.6f))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${formatNumber(remainingDebt)} تومان",
                        style = MaterialTheme.typography.displayLarge.copy(color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("جمع کل وام‌ها", style = MaterialTheme.typography.labelMedium.copy(color = Color.White.copy(alpha = 0.5f)))
                            Text("${formatNumber(totalLoanSum)} تومان", style = MaterialTheme.typography.bodyLarge.copy(color = Color.White, fontWeight = FontWeight.Bold))
                        }
                        Column {
                            Text("پرداخت شده تاکنون", style = MaterialTheme.typography.labelMedium.copy(color = Color.White.copy(alpha = 0.5f)))
                            Text("${formatNumber(paidLoanSum)} تومان", style = MaterialTheme.typography.bodyLarge.copy(color = Color.White, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }

            Text(
                text = "لیست قراردادهای وام فعال",
                style = MaterialTheme.typography.bodyLarge.copy(color = SlateGray, fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(vertical = 12.dp)
            )

            if (loans.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("هیچ وامی ثبت نشده است. وام جدیدی اضافه کنید.")
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(loans) { loan ->
                        LoanCardItem(
                            loan = loan,
                            cards = cards,
                            onPayClick = { onPayInstallment(loan) }
                        )
                    }
                }
            }
        }

        if (showAddLoanDialog) {
            AddLoanDialog(
                onDismiss = { showAddLoanDialog = false },
                onConfirm = { bName, lName, tot, paid, inst, due ->
                    onAddLoan(bName, lName, tot, paid, inst, due)
                    showAddLoanDialog = false
                }
            )
        }
    }
}

@Composable
fun LoanCardItem(
    loan: Loan,
    cards: List<BankCard>,
    onPayClick: () -> Unit
) {
    val progress = if (loan.totalAmount > 0) loan.paidAmount.toFloat() / loan.totalAmount.toFloat() else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AlertOrange.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.MonetizationOn, contentDescription = null, tint = AlertOrange)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = loan.bankName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = NavySecondary)
                        )
                        Text(
                            text = loan.loanName,
                            style = MaterialTheme.typography.labelLarge.copy(color = SlateGray)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AlertOrange.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "سررسید: ${loan.dueDate}",
                        style = MaterialTheme.typography.labelMedium.copy(color = AlertOrange, fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("کل وام", style = MaterialTheme.typography.labelMedium.copy(color = SlateGray))
                    Text("${formatNumber(loan.totalAmount)} تومان", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = NavySecondary))
                }
                Column {
                    Text("باقیمانده", style = MaterialTheme.typography.labelMedium.copy(color = SlateGray))
                    Text("${formatNumber(loan.totalAmount - loan.paidAmount)} تومان", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = NavySecondary))
                }
                Column {
                    Text("مبلغ قسط", style = MaterialTheme.typography.labelMedium.copy(color = SlateGray))
                    Text("${formatNumber(loan.installmentAmount)} تومان", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = NavySecondary))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "پیشرفت بازپرداخت:",
                    style = MaterialTheme.typography.labelMedium.copy(color = SlateGray)
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = NavySecondary)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = progress,
                color = EmeraldPrimary,
                trackColor = IceSlate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action payment link
            Button(
                onClick = onPayClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("pay_installment_button_${loan.id}"),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.NotificationsActive, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("ثبت و پرداخت قسط این ماه", style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
fun AddLoanDialog(
    onDismiss: () -> Unit,
    onConfirm: (bankName: String, loanName: String, totalAmount: Long, paidAmount: Long, installmentAmount: Long, dueDate: String) -> Unit
) {
    var bankName by remember { mutableStateOf("") }
    var loanName by remember { mutableStateOf("") }
    var totalAmount by remember { mutableStateOf("") }
    var paidAmount by remember { mutableStateOf("") }
    var installmentAmount by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("add_loan_dialog_surface"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "افزودن وام جدید",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = NavySecondary)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = SlateGray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text("نام بانک (مثال: بانک ملت)") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_loan_bank")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = loanName,
                    onValueChange = { loanName = it },
                    label = { Text("بابت وام (مثال: وام خودرو)") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_loan_name")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = totalAmount,
                    onValueChange = { input -> if (input.all { it.isDigit() }) totalAmount = input },
                    label = { Text("کل مبلغ وام (تومان)") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("input_loan_total")
                )

                if (totalAmount.isNotEmpty()) {
                    val formatted = totalAmount.toLongOrNull()?.let { formatNumber(it) } ?: totalAmount
                    Text(
                        text = "معادل: $formatted تومان",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = EmeraldPrimary,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = paidAmount,
                    onValueChange = { input -> if (input.all { it.isDigit() }) paidAmount = input },
                    label = { Text("کل مبالغ پرداخت شده تاکنون") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("input_loan_paid")
                )

                if (paidAmount.isNotEmpty()) {
                    val formatted = paidAmount.toLongOrNull()?.let { formatNumber(it) } ?: paidAmount
                    Text(
                        text = "معادل: $formatted تومان",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = EmeraldPrimary,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = installmentAmount,
                    onValueChange = { input -> if (input.all { it.isDigit() }) installmentAmount = input },
                    label = { Text("مبلغ قسط ماهیانه") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("input_loan_installment")
                )

                if (installmentAmount.isNotEmpty()) {
                    val formatted = installmentAmount.toLongOrNull()?.let { formatNumber(it) } ?: installmentAmount
                    Text(
                        text = "معادل: $formatted تومان",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = EmeraldPrimary,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text("تاریخ قسط (مثال: 15 هر ماه)") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_loan_duedate")
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (showError) {
                    Text(
                        text = "تمامی فیلدها را به درستی پر نمایید.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = ErrorRed),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                Button(
                    onClick = {
                        val tot = totalAmount.toLongOrNull()
                        val paid = paidAmount.toLongOrNull()
                        val inst = installmentAmount.toLongOrNull()

                        if (bankName.isBlank() || loanName.isBlank() || tot == null || paid == null || inst == null || dueDate.isBlank()) {
                            showError = true
                        } else {
                            onConfirm(bankName, loanName, tot, paid, inst, dueDate)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("confirm_add_loan_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("ثبت وام", style = MaterialTheme.typography.bodyLarge.copy(color = Color.White, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}
