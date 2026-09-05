package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BankCard
import com.example.data.Transaction
import com.example.ui.components.IranianBankLogo
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumTransactionSheet(
    cards: List<BankCard>,
    existingTransaction: Transaction?,
    onDismiss: () -> Unit,
    onConfirm: (title: String, amount: Long, category: String, isExpense: Boolean, bankName: String) -> Unit,
    onDelete: (Transaction) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // States
    var isExpense by remember { mutableStateOf(existingTransaction?.isExpense ?: true) }
    var amountStr by remember { mutableStateOf(existingTransaction?.amount?.toString() ?: "") }
    var title by remember { mutableStateOf(existingTransaction?.title ?: "") }
    var selectedCategory by remember { mutableStateOf(existingTransaction?.category ?: (if (existingTransaction?.isExpense ?: true) "غذا" else "حقوق")) }
    var selectedBank by remember { mutableStateOf(existingTransaction?.bankName ?: (cards.firstOrNull()?.bankName ?: "بلو بانک")) }

    // Constants
    val categories = if (isExpense) {
        listOf("غذا", "پوشاک", "تفریح", "قسط", "سایر")
    } else {
        listOf("حقوق", "یارانه", "سود سپرده", "فروش کالا", "هدیه", "سایر")
    }

    val quickTitles = if (isExpense) {
        listOf("خرید سوپرمارکت", "رستوران و کافه", "کرایه تاکسی", "خرید پوشاک", "قسط وام", "قبوض", "سایر")
    } else {
        listOf("حقوق ماهانه", "یارانه معیشتی", "کسب‌وکار", "هدیه", "سود بانکی", "فروش کالا", "سایر")
    }

    val amountLong = amountStr.toLongOrNull() ?: 0L
    val isAddMode = existingTransaction == null
    var isAmountFocused by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = SurfaceWhite,
        modifier = Modifier.testTag("premium_transaction_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            // 1. eBay Evo Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isAddMode) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDelete(existingTransaction!!)
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(EbayRedLight)
                            .testTag("delete_transaction_icon_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "حذف تراکنش",
                            tint = EbayRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(40.dp))
                }

                // Header Title
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isAddMode) "ثبت تراکنش جدید" else "ویرایش تراکنش",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = EbayDarkText,
                            fontSize = 18.sp
                        ),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "طراحی بر اساس سیستم دیزاین Evo",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = EbaySecondaryText,
                            fontSize = 11.sp
                        )
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_sheet_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "بستن",
                        tint = EbaySecondaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Scrollable Content Area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 2. HERO AMOUNT DISPLAY CARD (eBay Evo Card)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, EbayBorderGray),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "مبلغ تراکنش (تومان)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = EbaySecondaryText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                androidx.compose.foundation.text.BasicTextField(
                                    value = amountStr,
                                    onValueChange = { input ->
                                        val cleaned = input.filter { it.isDigit() }
                                        if (cleaned.length <= 15) {
                                            amountStr = cleaned
                                        }
                                    },
                                    textStyle = MaterialTheme.typography.displayMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isExpense) EbayRed else EbayGreen,
                                        fontSize = 36.sp,
                                        fontFamily = FontFamily.Monospace,
                                        textAlign = TextAlign.Center
                                    ),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                    ),
                                    visualTransformation = com.example.ui.components.ThousandsSeparatorFilter(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("massive_amount_input")
                                        .onFocusChanged { isAmountFocused = it.isFocused },
                                    singleLine = true,
                                    decorationBox = { innerTextField ->
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                                            if (amountStr.isEmpty()) {
                                                Text(
                                                    text = "0",
                                                    style = MaterialTheme.typography.displayMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = EbaySecondaryText.copy(alpha = 0.4f),
                                                        fontSize = 36.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        textAlign = TextAlign.Center
                                                    )
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
                            }
                        }

                        AnimatedContent(
                            targetState = amountLong,
                            label = "AmountWords"
                        ) { amount ->
                            if (amount > 0) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${com.example.utils.NumberToPersianWords.convert(amount)} تومان",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = EbaySecondaryText,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp
                                    ),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. SEGMENTED EVOS PILL TOGGLE (Expense / Income)
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
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isExpense = true
                                if (selectedCategory !in listOf("غذا", "پوشاک", "تفریح", "قسط", "سایر")) {
                                    selectedCategory = "غذا"
                                }
                            }
                            .testTag("sheet_toggle_expense"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "برداشت (هزینه)",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isExpense) Color.White else EbayDarkText,
                                    fontSize = 13.sp
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = null,
                                tint = if (isExpense) Color.White else EbayRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Income Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (!isExpense) EbayGreen else Color.Transparent)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isExpense = false
                                if (selectedCategory !in listOf("حقوق", "یارانه", "سود سپرده", "فروش کالا", "هدیه", "سایر")) {
                                    selectedCategory = "حقوق"
                                }
                            }
                            .testTag("sheet_toggle_income"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "واریز (درآمد)",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (!isExpense) Color.White else EbayDarkText,
                                    fontSize = 13.sp
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = if (!isExpense) Color.White else EbayGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 4. TITLE INPUT SECTION WITH QUICK SUGGESTIONS
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Create,
                        contentDescription = null,
                        tint = EbaySecondaryText,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "عنوان تراکنش",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = EbayDarkText,
                            fontSize = 13.sp
                        )
                    )
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = {
                        Text(
                            "مثلاً: خرید سوپرمارکت، قسط مسکن...",
                            style = MaterialTheme.typography.bodyMedium.copy(color = EbaySecondaryText.copy(alpha = 0.6f))
                        )
                    },
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
                        .testTag("sheet_title_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Title Suggestions Row (Evo Chips)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(quickTitles) { t ->
                        val isSelected = title == t
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                title = t
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) EbayBlueLight else EbayChipBackground,
                            border = if (isSelected) BorderStroke(1.dp, EbayBluePrimary) else BorderStroke(1.dp, EbayBorderGray)
                        ) {
                            Text(
                                text = t,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) EbayBluePrimary else EbayDarkText,
                                    fontSize = 12.sp
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 5. CATEGORY SELECTION ROW (Evo Pills)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Category,
                        contentDescription = null,
                        tint = EbaySecondaryText,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "دسته‌بندی",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = EbayDarkText,
                            fontSize = 13.sp
                        )
                    )
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(categories) { cat ->
                        val isSelected = selectedCategory == cat
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedCategory = cat
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) EbayBlueLight else EbayChipBackground,
                            border = if (isSelected) BorderStroke(1.dp, EbayBluePrimary) else BorderStroke(1.dp, EbayBorderGray),
                            modifier = Modifier.testTag("sheet_cat_chip_$cat")
                        ) {
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) EbayBluePrimary else EbayDarkText,
                                    fontSize = 12.sp
                                ),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 6. BANK ACCOUNT SELECTION SECTION (Evo Cards format)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = EbaySecondaryText,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "کارت بانکی منبع",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = EbayDarkText,
                            fontSize = 13.sp
                        )
                    )
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(cards) { card ->
                        val isSelected = selectedBank == card.bankName

                        Card(
                            modifier = Modifier
                                .width(150.dp)
                                .height(68.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedBank = card.bankName
                                }
                                .testTag("sheet_bank_chip_${card.bankName}"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) EbayBlueLight else SurfaceWhite
                            ),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) EbayBluePrimary else EbayBorderGray
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start
                            ) {
                                IranianBankLogo(bankName = card.bankName, size = 30.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(verticalArrangement = Arrangement.Center) {
                                    Text(
                                        text = card.bankName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) EbayBluePrimary else EbayDarkText,
                                            fontSize = 13.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = card.cardNumber,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = EbaySecondaryText,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp
                                        ),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // 7. ACTION BUTTONS FOOTER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                EvoButton(
                    text = "انصراف",
                    onClick = onDismiss,
                    variant = EvoButtonVariant.Outlined,
                    modifier = Modifier.weight(1f),
                    testTag = "sheet_cancel_btn"
                )

                EvoButton(
                    text = if (isAddMode) "ثبت تراکنش" else "بروزرسانی تراکنش",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val finalTitle = title.ifBlank { if (isExpense) "هزینه بدون عنوان" else "درآمد بدون عنوان" }
                        onConfirm(finalTitle, amountLong, selectedCategory, isExpense, selectedBank)
                    },
                    variant = EvoButtonVariant.Primary,
                    enabled = amountLong > 0,
                    modifier = Modifier.weight(1.5f),
                    testTag = "sheet_save_btn"
                )
            }
        }
    }
}

