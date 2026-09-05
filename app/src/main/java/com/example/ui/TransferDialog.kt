package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.BankCard
import com.example.ui.components.IranianBankLogo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferDialog(
    cards: List<BankCard>,
    onDismiss: () -> Unit,
    onConfirm: (fromCardName: String, toCardName: String, amount: Long) -> Unit
) {
    var fromCard by remember { mutableStateOf(cards.firstOrNull()) }
    
    // Automatically select a different second card as destination, if available
    var toCard by remember { 
        mutableStateOf(cards.firstOrNull { it.bankName != fromCard?.bankName } ?: cards.getOrNull(1)) 
    }
    
    var amountStr by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Update destination card automatically if source changes to the same card
    LaunchedEffect(fromCard) {
        if (fromCard != null && toCard?.bankName == fromCard?.bankName) {
            toCard = cards.firstOrNull { it.bankName != fromCard?.bankName }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .border(1.dp, IceSlate, RoundedCornerShape(28.dp))
                .testTag("transfer_dialog_surface"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
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
                                .background(Color(0xFFEFF6FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "انتقال وجه بین کارت‌ها",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = NavySecondary)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_transfer_dialog")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = SlateGray)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (cards.size < 2) {
                    // Edge Case: Not enough cards
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFFFFBEB))
                            .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(16.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "کارت‌های بانکی کافی نیست!",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "برای انتقال وجه داخلی، باید حداقل ۲ کارت بانکی در سیستم تعریف کرده باشید.",
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF92400E)),
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("متوجه شدم", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                    }
                } else {
                    // 1. Source Card Selector (کارت مبدأ)
                    Text(
                        text = "کارت مبدأ (برداشت از):",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = SlateGray),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(cards) { card ->
                            val isSelected = fromCard?.bankName == card.bankName
                            val cardGradient = when {
                                card.bankName.contains("ملت") -> listOf(Color(0xFFDC2626), Color(0xFF1E293B))
                                card.bankName.contains("سامان") -> listOf(Color(0xFF2563EB), Color(0xFF1D4ED8))
                                card.bankName.contains("بلو") -> listOf(Color(0xFF06B6D4), Color(0xFF0891B2))
                                card.bankName.contains("ملی") -> listOf(Color(0xFF2D7D5F), Color(0xFF225E47))
                                card.bankName.contains("رسالت") -> listOf(Color(0xFF0EA5E9), Color(0xFF0369A1))
                                else -> listOf(Color(0xFF475569), Color(0xFF334155))
                            }

                            Card(
                                modifier = Modifier
                                    .width(160.dp)
                                    .height(86.dp)
                                    .clickable { fromCard = card },
                                shape = RoundedCornerShape(14.dp),
                                border = if (isSelected) BorderStroke(3.dp, EmeraldPrimary) else BorderStroke(1.dp, IceSlate),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Brush.linearGradient(cardGradient.map { it.copy(alpha = if (isSelected) 1f else 0.85f) }))
                                        .padding(10.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = card.bankName,
                                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                                            )
                                            IranianBankLogo(bankName = card.bankName, size = 18.dp)
                                        }
                                        Column {
                                            Text(
                                                text = "موجودی:",
                                                style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.7f))
                                            )
                                            Text(
                                                text = "${formatNumber(card.balance)} تومان",
                                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.Black)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Visually pleasing divider arrow
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(IceSlate),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = SlateGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Destination Card Selector (کارت مقصد)
                    Text(
                        text = "کارت مقصد (واریز به):",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = SlateGray),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Filter out the selected "from" card so users can't transfer to itself
                        val destinationCards = cards.filter { it.bankName != fromCard?.bankName }
                        items(destinationCards) { card ->
                            val isSelected = toCard?.bankName == card.bankName
                            val cardGradient = when {
                                card.bankName.contains("ملت") -> listOf(Color(0xFFDC2626), Color(0xFF1E293B))
                                card.bankName.contains("سامان") -> listOf(Color(0xFF2563EB), Color(0xFF1D4ED8))
                                card.bankName.contains("بلو") -> listOf(Color(0xFF06B6D4), Color(0xFF0891B2))
                                card.bankName.contains("ملی") -> listOf(Color(0xFF2D7D5F), Color(0xFF225E47))
                                card.bankName.contains("رسالت") -> listOf(Color(0xFF0EA5E9), Color(0xFF0369A1))
                                else -> listOf(Color(0xFF475569), Color(0xFF334155))
                            }

                            Card(
                                modifier = Modifier
                                    .width(160.dp)
                                    .height(86.dp)
                                    .clickable { toCard = card },
                                shape = RoundedCornerShape(14.dp),
                                border = if (isSelected) BorderStroke(3.dp, Color(0xFF2563EB)) else BorderStroke(1.dp, IceSlate),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Brush.linearGradient(cardGradient.map { it.copy(alpha = if (isSelected) 1f else 0.85f) }))
                                        .padding(10.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = card.bankName,
                                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                                            )
                                            IranianBankLogo(bankName = card.bankName, size = 18.dp)
                                        }
                                        Column {
                                            Text(
                                                text = "موجودی:",
                                                style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.7f))
                                            )
                                            Text(
                                                text = "${formatNumber(card.balance)} تومان",
                                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.Black)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 3. Amount Input (مبلغ انتقال)
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() }) {
                                amountStr = input
                                showError = false
                            }
                        },
                        label = { Text("مبلغ انتقال وجه (تومان)", style = MaterialTheme.typography.bodyMedium) },
                        placeholder = { Text("مثال: ۵۰,۰۰۰", style = MaterialTheme.typography.bodyMedium) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = NavySecondary,
                            textAlign = TextAlign.Start
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF2563EB),
                            unfocusedBorderColor = IceSlate
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("transfer_amount_input")
                    )

                    if (amountStr.isNotEmpty()) {
                        val formatted = amountStr.toLongOrNull()?.let { formatNumber(it) } ?: amountStr
                        Text(
                            text = "معادل: $formatted تومان",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFF2563EB),
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(top = 4.dp).align(Alignment.Start)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (showError) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium.copy(color = ErrorRed, fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    // 4. Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Cancel
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, IceSlate),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            Text(
                                "انصراف",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = SlateGray)
                            )
                        }

                        // Confirm
                        Button(
                            onClick = {
                                val amount = amountStr.toLongOrNull()
                                val srcCard = fromCard
                                val destCard = toCard

                                when {
                                    srcCard == null || destCard == null -> {
                                        errorMessage = "لطفاً کارت مبدأ و مقصد را انتخاب نمایید."
                                        showError = true
                                    }
                                    amount == null || amount <= 0 -> {
                                        errorMessage = "لطفاً مبلغ معتبری برای انتقال وارد نمایید."
                                        showError = true
                                    }
                                    srcCard.balance < amount -> {
                                        errorMessage = "موجودی کارت مبدأ کافی نیست! (موجودی: ${formatNumber(srcCard.balance)} تومان)"
                                        showError = true
                                    }
                                    else -> {
                                        onConfirm(srcCard.bankName, destCard.bankName, amount)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(52.dp)
                                .testTag("confirm_transfer_button")
                        ) {
                            Text(
                                "تأیید و انتقال وجه",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = Color.White)
                            )
                        }
                    }
                }
            }
        }
    }
}
