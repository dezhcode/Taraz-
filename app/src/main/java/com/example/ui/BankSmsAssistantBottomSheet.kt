package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PendingTransaction
import com.example.ui.theme.*
import java.text.DecimalFormat

@Composable
fun BankSmsAssistantBottomSheet(
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit
) {
    val pendingTxs by viewModel.pendingTransactions.collectAsState()
    val selectedTx by viewModel.selectedPendingTx.collectAsState()
    val aiDesc by viewModel.aiDescription.collectAsState()
    val isDescLoading by viewModel.isAiDescriptionLoading.collectAsState()
    val userText by viewModel.userInputExplanation.collectAsState()
    val isCategorizing by viewModel.isAiCategorizing.collectAsState()
    val proposal by viewModel.smsAiProposal.collectAsState()
    
    // Auto-select first if none is selected
    LaunchedEffect(pendingTxs) {
        if (selectedTx == null && pendingTxs.isNotEmpty()) {
            viewModel.selectPendingTx(pendingTxs.first())
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            color = BackgroundLight
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
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
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(EmeraldPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sms,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "دستیار پیامکی هوشمند تراز",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = NavySecondary
                            )
                        )
                    }
                    
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_close_sms_assistant")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "بستن", tint = SlateGray)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (pendingTxs.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(EmeraldPrimary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "تمامی تراکنش‌های پیامکی ثبت شده‌اند!",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = NavySecondary
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "هیچ پیامک تراکنشی معلقی برای بررسی وجود ندارد.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = SlateGray),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Pending list carousel
                    Text(
                        text = "پیامک‌های نیازمند ثبت در تراز مالی:",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = SlateGray
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(pendingTxs, key = { it.id }) { tx ->
                            val isSelected = selectedTx?.id == tx.id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) EmeraldPrimary else Color.White)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) EmeraldPrimary else IceSlate,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { viewModel.selectPendingTx(tx) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (tx.isExpense) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                        contentDescription = null,
                                        tint = if (isSelected) Color.White else (if (tx.isExpense) ErrorRed else EmeraldPrimary),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${tx.card} | ${formatLocalNumber(tx.amount)} ت",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = if (isSelected) Color.White else NavySecondary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    selectedTx?.let { tx ->
                        // AI friendly narrative card
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.25f)),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                EmeraldPrimary.copy(alpha = 0.04f),
                                                Color.White
                                            )
                                        )
                                    )
                                    .padding(14.dp)
                            ) {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = EmeraldPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "پیام دریافتی از بانک:",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = EmeraldPrimary
                                            )
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (isDescLoading) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = EmeraldPrimary
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                "هوش مصنوعی در حال تحلیل پیامک...",
                                                style = MaterialTheme.typography.bodySmall.copy(color = SlateGray)
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = aiDesc,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = NavySecondary,
                                                fontWeight = FontWeight.SemiBold,
                                                lineHeight = 22.sp
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(IceSlate)
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = tx.smsBody,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = SlateGray,
                                                fontSize = 11.sp
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // AI Proposal Section or Form Input
                        if (proposal != null) {
                            // High-quality M3 custom feedback card
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.5.dp, EmeraldPrimary),
                                colors = CardDefaults.cardColors(containerColor = EmeraldPrimary.copy(alpha = 0.05f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize()
                                    .testTag("proposal_confirmation_box")
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = EmeraldPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "من این تراکنش را براتون آماده کردم. تایید می‌کنید؟",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = EmeraldHover
                                            )
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                "عنوان تراکنش:",
                                                style = MaterialTheme.typography.labelSmall.copy(color = SlateGray)
                                            )
                                            Text(
                                                proposal!!.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = NavySecondary
                                                )
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                "دسته‌بندی هوشمند:",
                                                style = MaterialTheme.typography.labelSmall.copy(color = SlateGray)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(EmeraldPrimary.copy(alpha = 0.15f))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    proposal!!.category,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = EmeraldHover
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { viewModel.approveAiSmsProposal(tx, proposal!!) },
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .weight(1.3f)
                                                .height(42.dp)
                                                .testTag("btn_confirm_ai_proposal")
                                        ) {
                                            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("تایید و ثبت در سیستم", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color.White))
                                        }

                                        OutlinedButton(
                                            onClick = { viewModel.selectPendingTx(tx) }, // Clears proposal state
                                            border = BorderStroke(1.dp, SlateGray),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateGray),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .weight(0.7f)
                                                .height(42.dp)
                                                .testTag("btn_reject_ai_proposal")
                                        ) {
                                            Text("تغییر", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        } else {
                            // User text input & smart analyze
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize()
                            ) {
                                Text(
                                    text = "این تراکنش بابت چی بوده؟ (مثال: ناهار امروز یا خرید سوپرمارکت)",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = NavySecondary
                                    )
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                val focusManager = LocalFocusManager.current
                                OutlinedTextField(
                                    value = userText,
                                    onValueChange = { viewModel.setUserInputExplanation(it) },
                                    placeholder = {
                                        Text(
                                            "بنویسید تا هوش مصنوعی دسته‌بندی و عنوان تراکنش را بسازد...",
                                            style = MaterialTheme.typography.bodySmall.copy(color = SlateGray.copy(alpha = 0.7f))
                                        )
                                    },
                                    trailingIcon = {
                                        if (isCategorizing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = EmeraldPrimary
                                            )
                                        } else {
                                            IconButton(
                                                onClick = {
                                                    focusManager.clearFocus()
                                                    viewModel.categorizeWithAi(tx, userText)
                                                },
                                                enabled = userText.isNotBlank(),
                                                modifier = Modifier.testTag("btn_send_ai_categorization")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Send,
                                                    contentDescription = "تحلیل با هوش مصنوعی",
                                                    tint = if (userText.isNotBlank()) EmeraldPrimary else SlateGray.copy(alpha = 0.5f)
                                                )
                                            }
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = {
                                        focusManager.clearFocus()
                                        viewModel.categorizeWithAi(tx, userText)
                                    }),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = EmeraldPrimary,
                                        unfocusedBorderColor = IceSlate,
                                        focusedLabelColor = EmeraldPrimary,
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_sms_user_explanation")
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // Or Register manually
                                var manualTitle by remember { mutableStateOf("") }
                                var manualCategory by remember { mutableStateOf("سایر") }
                                var showManualForm by remember { mutableStateOf(false) }

                                val manualCategories = if (tx.isExpense) {
                                    listOf("غذا", "پوشاک", "تفریح", "قسط", "سایر")
                                } else {
                                    listOf("حقوق", "سایر")
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable { showManualForm = !showManualForm }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (showManualForm) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = SlateGray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "ثبت دستی تراکنش بدون هوش مصنوعی",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = SlateGray,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }

                                if (showManualForm) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = manualTitle,
                                            onValueChange = { manualTitle = it },
                                            label = { Text("عنوان تراکنش") },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = EmeraldPrimary,
                                                unfocusedBorderColor = IceSlate
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth().testTag("manual_title_input")
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Text(
                                            "دسته‌بندی:",
                                            style = MaterialTheme.typography.labelSmall.copy(color = SlateGray)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            manualCategories.forEach { cat ->
                                                val isCatSelected = manualCategory == cat
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (isCatSelected) EmeraldPrimary.copy(alpha = 0.12f) else Color.White)
                                                        .border(
                                                            width = 1.dp,
                                                            color = if (isCatSelected) EmeraldPrimary else IceSlate,
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                        .clickable { manualCategory = cat }
                                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                                ) {
                                                    Text(
                                                        text = cat,
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            color = if (isCatSelected) EmeraldPrimary else SlateGray,
                                                            fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(
                                                onClick = { viewModel.rejectPendingTransaction(tx.id) },
                                                colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed)
                                            ) {
                                                Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("حذف پیامک")
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Button(
                                                onClick = {
                                                    viewModel.approvePendingTransaction(
                                                        pendingTx = tx,
                                                        finalTitle = manualTitle.ifBlank { "تراکنش بانکی" },
                                                        finalCategory = manualCategory,
                                                        finalAmount = tx.amount,
                                                        finalCard = tx.card,
                                                        isExpense = tx.isExpense
                                                    )
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.testTag("btn_manual_register")
                                            ) {
                                                Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("ثبت تراکنش", style = MaterialTheme.typography.labelMedium.copy(color = Color.White))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom friendly advisory/status card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, IceSlate),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "برای به‌روز ماندن دقیق موجودی کل دارایی شما، لطفاً تراکنش‌های پیامکی ثبت‌نشده را تایید کنید.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = SlateGray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

private fun formatLocalNumber(number: Long): String {
    return DecimalFormat("#,###").format(number)
}
