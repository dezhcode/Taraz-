package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.ui.theme.*

enum class EvoButtonVariant {
    Primary,
    Secondary,
    Outlined,
    Text
}

@Composable
fun EvoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: EvoButtonVariant = EvoButtonVariant.Primary,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    testTag: String = "evo_button"
) {
    val containerColor = when (variant) {
        EvoButtonVariant.Primary -> EbayBluePrimary
        EvoButtonVariant.Secondary -> EbayChipBackground
        EvoButtonVariant.Outlined -> Color.Transparent
        EvoButtonVariant.Text -> Color.Transparent
    }

    val contentColor = when (variant) {
        EvoButtonVariant.Primary -> Color.White
        EvoButtonVariant.Secondary -> EbayDarkText
        EvoButtonVariant.Outlined -> EbayBluePrimary
        EvoButtonVariant.Text -> EbayBluePrimary
    }

    val border = when (variant) {
        EvoButtonVariant.Outlined -> BorderStroke(1.5.dp, EbayBluePrimary)
        else -> null
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        contentColor = contentColor,
        border = border,
        modifier = modifier
            .height(48.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    cards: List<BankCard>,
    onDismiss: () -> Unit,
    onConfirm: (title: String, amount: Long, category: String, isExpense: Boolean, bankName: String) -> Unit,
    initialIsExpense: Boolean = true
) {
    var isExpense by remember { mutableStateOf(initialIsExpense) }
    var amountStr by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(if (initialIsExpense) "غذا" else "حقوق") }
    var selectedBank by remember { mutableStateOf(cards.firstOrNull()?.bankName ?: "بلو بانک") }
    var showError by remember { mutableStateOf(false) }

    val categories = listOf("غذا", "حقوق", "پوشاک", "تفریح", "قسط", "سایر")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .testTag("add_transaction_dialog_surface"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = BorderStroke(1.dp, EbayBorderGray),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header row in eBay Evo Style
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ثبت تراکنش جدید",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = EbayDarkText,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "فرم ورود داده با استاندارد Evo",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = EbaySecondaryText,
                                fontSize = 11.sp
                            )
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_add_transaction_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "بستن",
                            tint = EbaySecondaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 1. Sleek Expense vs Income Custom Segmented Evo Pill Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(EbayChipBackground)
                        .padding(4.dp)
                ) {
                    // Expense Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isExpense) EbayRed else Color.Transparent)
                            .clickable {
                                isExpense = true
                                selectedCategory = "غذا"
                            }
                            .testTag("toggle_expense_tab"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "برداشت (هزینه)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isExpense) Color.White else EbayDarkText,
                                fontSize = 13.sp
                            )
                        )
                    }

                    // Income Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (!isExpense) EbayGreen else Color.Transparent)
                            .clickable {
                                isExpense = false
                                selectedCategory = "حقوق"
                            }
                            .testTag("toggle_income_tab"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "واریز (درآمد)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (!isExpense) Color.White else EbayDarkText,
                                fontSize = 13.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 2. Evo Styled Amount Input Field
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() }) {
                            amountStr = input
                        }
                    },
                    label = {
                        Text("مبلغ تراکنش (تومان)", style = MaterialTheme.typography.bodyMedium.copy(color = EbaySecondaryText))
                    },
                    placeholder = { Text("0", style = MaterialTheme.typography.bodyMedium) },
                    suffix = {
                        Text("تومان", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = EbaySecondaryText))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isExpense) EbayRed else EbayGreen,
                        textAlign = TextAlign.Start,
                        fontSize = 18.sp
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isExpense) EbayRed else EbayGreen,
                        unfocusedBorderColor = EbayBorderGray,
                        focusedContainerColor = SurfaceWhite,
                        unfocusedContainerColor = SurfaceWhite
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transaction_amount_input")
                )

                if (amountStr.isNotEmpty()) {
                    val formatted = amountStr.toLongOrNull()?.let { formatNumber(it) } ?: amountStr
                    Text(
                        text = "معادل: $formatted تومان",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isExpense) EbayRed else EbayGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, top = 4.dp),
                        textAlign = TextAlign.Start
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Evo Title / Description Field
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = {
                        Text("عنوان یا بابت...", style = MaterialTheme.typography.bodyMedium.copy(color = EbaySecondaryText))
                    },
                    placeholder = { Text("مثال: خرید روزانه، حقوق و...", style = MaterialTheme.typography.bodyMedium) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EbayBluePrimary,
                        unfocusedBorderColor = EbayBorderGray,
                        focusedContainerColor = SurfaceWhite,
                        unfocusedContainerColor = SurfaceWhite
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transaction_title_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 4. Category Selector Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = "دسته‌بندی تراکنش",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = EbayDarkText, fontSize = 13.sp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Horizontal list of Category Pills
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { category ->
                        val isSelected = category == selectedCategory
                        val categoryIcon = when (category) {
                            "غذا" -> Icons.Default.Restaurant
                            "حقوق" -> Icons.Default.Payments
                            "پوشاک" -> Icons.Default.ShoppingBag
                            "تفریح" -> Icons.Default.LocalActivity
                            "قسط" -> Icons.Default.CreditCard
                            else -> Icons.Default.Category
                        }

                        Surface(
                            onClick = { selectedCategory = category },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) EbayBlueLight else EbayChipBackground,
                            border = if (isSelected) BorderStroke(1.dp, EbayBluePrimary) else BorderStroke(1.dp, EbayBorderGray),
                            modifier = Modifier.testTag("add_dialog_category_$category")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = categoryIcon,
                                    contentDescription = category,
                                    tint = if (isSelected) EbayBluePrimary else EbaySecondaryText,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = category,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) EbayBluePrimary else EbayDarkText,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 5. Account / Bank Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = "انتخاب کارت بانکی",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = EbayDarkText, fontSize = 13.sp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(cards) { card ->
                        val isSelected = card.bankName == selectedBank
                        Surface(
                            onClick = { selectedBank = card.bankName },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) EbayDarkText else EbayChipBackground,
                            border = if (isSelected) null else BorderStroke(1.dp, EbayBorderGray),
                            modifier = Modifier.testTag("add_dialog_bank_${card.bankName}")
                        ) {
                            Text(
                                text = card.bankName,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else EbayDarkText,
                                    fontSize = 12.sp
                                ),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (showError) {
                    Text(
                        text = "لطفاً مبلغ و عنوان تراکنش را وارد نمایید.",
                        style = MaterialTheme.typography.bodySmall.copy(color = EbayRed, fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                // 6. Ebay Button Variants Actions (Submit & Cancel)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    EvoButton(
                        text = "انصراف",
                        onClick = onDismiss,
                        variant = EvoButtonVariant.Outlined,
                        modifier = Modifier.weight(1f),
                        testTag = "cancel_transaction_button"
                    )

                    EvoButton(
                        text = "تایید و ثبت",
                        onClick = {
                            val parsedAmount = amountStr.toLongOrNull()
                            if (title.isBlank() || parsedAmount == null || parsedAmount <= 0) {
                                showError = true
                            } else {
                                onConfirm(title, parsedAmount, selectedCategory, isExpense, selectedBank)
                            }
                        },
                        variant = EvoButtonVariant.Primary,
                        modifier = Modifier.weight(1.5f),
                        testTag = "submit_transaction_confirm"
                    )
                }
            }
        }
    }
}

