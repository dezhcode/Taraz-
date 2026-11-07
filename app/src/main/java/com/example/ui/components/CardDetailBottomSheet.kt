package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AccountType
import com.example.data.BankCard
import com.example.data.BankSender
import com.example.ui.formatNumber
import com.example.ui.EmeraldPrimary
import com.example.ui.BackgroundLight
import com.example.ui.NavySecondary
import com.example.ui.SlateGray
import java.util.Locale

// Custom filters for premium English styled bold inputs
class CardNumberFilter : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text.replace(" ", "").replace("-", "")
        val trimmed = if (raw.length >= 16) raw.substring(0, 16) else raw
        var out = ""
        for (i in trimmed.indices) {
            out += trimmed[i]
            if (i % 4 == 3 && i != 15) {
                out += "  " // 2 spaces for compact readable spacing
            }
        }

        val creditCardOffsetTranslator = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 4) return offset
                if (offset <= 8) return offset + 2
                if (offset <= 12) return offset + 4
                if (offset <= 16) return offset + 6
                return 22
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 4) return offset
                if (offset <= 10) return offset - 2
                if (offset <= 16) return offset - 4
                if (offset <= 22) return offset - 6
                return 16
            }
        }
        return TransformedText(AnnotatedString(out), creditCardOffsetTranslator)
    }
}

class ThousandsSeparatorFilter : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text
        if (originalText.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val value = originalText.toLongOrNull() ?: 0L
        val formatted = String.format(Locale.ENGLISH, "%,d", value)

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                val sub = originalText.substring(0, offset.coerceAtMost(originalText.length))
                val subVal = sub.toLongOrNull() ?: 0L
                val subFormatted = String.format(Locale.ENGLISH, "%,d", subVal)
                return subFormatted.length
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 0) return 0
                if (offset >= formatted.length) return originalText.length
                var digitCount = 0
                for (i in 0 until offset.coerceAtMost(formatted.length)) {
                    if (formatted[i].isDigit()) {
                        digitCount++
                    }
                }
                return digitCount.coerceAtMost(originalText.length)
            }
        }
        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

// Utility to display card numbers grouped beautifully
fun formatCardNumberForDisplay(rawNumber: String): String {
    val clean = rawNumber.replace(" ", "").replace("-", "")
    val chunks = clean.chunked(4)
    return chunks.joinToString("   ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailBottomSheet(
    card: BankCard,
    onDismiss: () -> Unit,
    /** The sender feeding this card from SMS, or null when the reader is off. */
    smsLink: BankSender?,
    onEnableSmsBanking: () -> Unit,
    onChangeSmsSender: () -> Unit,
    onDisableSmsBanking: () -> Unit,
    onUpdateCard: (BankCard) -> Unit,
    onDeleteCard: (Int) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var editedBankName by remember { mutableStateOf(card.bankName) }
    var editedCardNumber by remember { mutableStateOf(card.cardNumber.replace("*", "0")) } // Replace mask for editing
    var editedBalanceStr by remember { mutableStateOf(card.balance.toString()) }
    var editedCardHolderName by remember { mutableStateOf(card.cardHolderName) }

    // A number saved elsewhere (the SMS reader's setup) should show without reopening the sheet.
    LaunchedEffect(card.cardNumber) {
        if (!isEditing) editedCardNumber = card.cardNumber.replace("*", "0")
    }

    var dropdownExpanded by remember { mutableStateOf(false) }
    var showManualBankSelector by remember { mutableStateOf(false) }
    var showDisableSmsConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val popularBanks = listOf(
        "بانک ملی",
        "بانک رسالت",
        "بانک ملت",
        "بانک صادرات",
        "بانک تجارت",
        "بانک سامان",
        "بانک سپه",
        "بانک کشاورزی",
        "بانک پارسیان",
        "بانک مسکن",
        "بانک رفاه",
        "بانک اقتصاد نوین",
        "بانک پاسارگاد",
        "بانک شهر",
        "بانک آینده",
        "بلو کارت"
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = "حذف کارت بانکی",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = NavySecondary)
                )
            },
            text = {
                Text(
                    text = "آیا از حذف کارت بانک ${card.bankName} اطمینان دارید؟ تمامی تراکنش‌های این کارت همچنان باقی خواهند ماند.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = SlateGray)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCard(card.id)
                        showDeleteConfirm = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("حذف", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteConfirm = false },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.LightGray)
                ) {
                    Text("انصراف", style = MaterialTheme.typography.bodyMedium.copy(color = SlateGray))
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isEditing) "ویرایش اطلاعات حساب" else "اطلاعات تکمیلی حساب",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = NavySecondary),
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // 1. High-Fidelity Bank Card Graphic Representation (Dynamic Preview!)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .oceanicShimmer(editedBankName, card.id.toString())
                        .padding(24.dp)
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IranianBankLogo(bankName = editedBankName, size = 40.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = editedBankName,
                                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Nfc,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Formatted elegant card number display
                        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val clean = editedCardNumber.filter { it.isDigit() }
                                val padded = clean.padEnd(16, '*')
                                val chunks = padded.chunked(4)
                                chunks.forEach { chunk ->
                                    Text(
                                        text = chunk,
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            color = Color.White.copy(alpha = 0.95f),
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 4.sp
                                        )
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = "صاحب کارت",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.5f))
                                )
                                Text(
                                    text = editedCardHolderName,
                                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "موجودی کل حساب",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.5f))
                                )
                                val previewBalance = editedBalanceStr.toLongOrNull() ?: 0L
                                Text(
                                    text = "${formatNumber(previewBalance)} تومان",
                                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Black)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedContent(targetState = isEditing, label = "editStateTransition") { editMode ->
                if (editMode) {
                    // --- EDITING CONTROLS ---
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Card Holder Name Input
                        Column {
                            Text(
                                text = "نام صاحب کارت / نام حساب",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = NavySecondary),
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            OutlinedTextField(
                                value = editedCardHolderName,
                                onValueChange = { editedCardHolderName = it },
                                shape = RoundedCornerShape(14.dp),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("edit_card_holder_name"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldPrimary,
                                    unfocusedBorderColor = Color.LightGray.copy(alpha = 0.5f)
                                )
                            )
                        }

                        // 2. Card Number Input (Separated each 4 digits dynamically, LTR forced!)
                        Column {
                            Text(
                                text = "شماره ۱۶ رقمی کارت",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = NavySecondary),
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                                OutlinedTextField(
                                    value = editedCardNumber,
                                    onValueChange = { input ->
                                        val cleaned = input.filter { it.isDigit() }
                                        if (cleaned.length <= 16) {
                                            editedCardNumber = cleaned
                                            if (cleaned.length >= 6) {
                                                val detectedBank = com.example.sms.util.BankUtils.detectBankFromCardNumber(cleaned)
                                                if (detectedBank != null) {
                                                    editedBankName = detectedBank
                                                }
                                            } else if (cleaned.isEmpty()) {
                                                editedBankName = ""
                                            }
                                        }
                                    },
                                    visualTransformation = CardNumberFilter(),
                                    placeholder = { Text("مثال: 6037997512345678", style = MaterialTheme.typography.bodyMedium.copy(color = SlateGray, textAlign = TextAlign.End), modifier = Modifier.fillMaxWidth()) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(14.dp),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        textAlign = TextAlign.Left
                                    ),
                                    modifier = Modifier.fillMaxWidth().testTag("edit_card_number"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = EmeraldPrimary,
                                        unfocusedBorderColor = Color.LightGray.copy(alpha = 0.5f)
                                    ),
                                    leadingIcon = {
                                        if (editedBankName.isNotEmpty()) {
                                            Box(modifier = Modifier.padding(start = 8.dp)) {
                                                IranianBankLogo(bankName = editedBankName, size = 28.dp)
                                            }
                                        } else {
                                            Icon(imageVector = Icons.Default.CreditCard, contentDescription = null, tint = SlateGray)
                                        }
                                    }
                                )
                            }
                        }

                        // 3. Card Balance Input (Formatted commas, Monospace bold font!)
                        Column {
                            Text(
                                text = "موجودی کارت (تومان)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = NavySecondary),
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                OutlinedTextField(
                                    value = editedBalanceStr,
                                    onValueChange = { input ->
                                        val cleaned = input.filter { it.isDigit() }
                                        editedBalanceStr = cleaned
                                    },
                                    visualTransformation = ThousandsSeparatorFilter(),
                                    placeholder = { Text("مثال: 5,000,000", style = MaterialTheme.typography.bodyMedium.copy(color = SlateGray, textAlign = TextAlign.End), modifier = Modifier.fillMaxWidth()) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(14.dp),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Left
                                    ),
                                    modifier = Modifier.fillMaxWidth().testTag("edit_card_balance"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = EmeraldPrimary,
                                        unfocusedBorderColor = Color.LightGray.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }

                        // 4. Expandable Manual Bank Selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showManualBankSelector = !showManualBankSelector }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (showManualBankSelector) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = EmeraldPrimary
                            )
                            Text(
                                text = "انتخاب دستی بانک (گزینه‌های بیشتر)",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldPrimary
                                )
                            )
                        }

                        AnimatedVisibility(visible = showManualBankSelector) {
                            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Text(
                                    text = "نام بانک",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = NavySecondary),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(BackgroundLight.copy(alpha = 0.5f))
                                            .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                            .clickable { dropdownExpanded = true }
                                            .padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IranianBankLogo(bankName = editedBankName, size = 28.dp)
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(text = editedBankName, style = MaterialTheme.typography.bodyLarge.copy(color = NavySecondary))
                                        }
                                        Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = NavySecondary)
                                    }

                                    DropdownMenu(
                                        expanded = dropdownExpanded,
                                        onDismissRequest = { dropdownExpanded = false },
                                        modifier = Modifier.fillMaxWidth(0.85f).heightIn(max = 240.dp).background(Color.White)
                                    ) {
                                        popularBanks.forEach { bank ->
                                            DropdownMenuItem(
                                                text = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        IranianBankLogo(bankName = bank, size = 24.dp)
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        Text(text = bank, style = MaterialTheme.typography.bodyMedium.copy(color = NavySecondary))
                                                    }
                                                },
                                                onClick = {
                                                    editedBankName = bank
                                                    dropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (showError) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Save & Cancel Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    val bal = editedBalanceStr.toLongOrNull()
                                    if (editedBankName.isBlank()) {
                                        errorMessage = "نام بانک را وارد یا انتخاب نمایید."
                                        showError = true
                                    } else if (editedCardHolderName.isBlank()) {
                                        errorMessage = "نام صاحب کارت نمی‌تواند خالی باشد."
                                        showError = true
                                    } else if (editedCardNumber.length < 16) {
                                        errorMessage = "شماره کارت باید دقیقاً ۱۶ رقم باشد."
                                        showError = true
                                    } else if (bal == null) {
                                        errorMessage = "لطفاً موجودی معتبری وارد نمایید."
                                        showError = true
                                    } else {
                                        showError = false
                                        val finalCard = card.copy(
                                            bankName = editedBankName,
                                            cardNumber = editedCardNumber,
                                            balance = bal,
                                            cardHolderName = editedCardHolderName
                                        )
                                        onUpdateCard(finalCard)
                                        isEditing = false
                                    }
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                            ) {
                                Text("ذخیره تغییرات", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = Color.White))
                            }

                            OutlinedButton(
                                onClick = {
                                    // Reset edit fields and cancel
                                    editedBankName = card.bankName
                                    editedCardNumber = card.cardNumber.replace("*", "0")
                                    editedBalanceStr = card.balance.toString()
                                    editedCardHolderName = card.cardHolderName
                                    isEditing = false
                                    showError = false
                                },
                                modifier = Modifier.weight(0.7f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color.LightGray)
                            ) {
                                Text("انصراف", style = MaterialTheme.typography.bodyLarge.copy(color = SlateGray))
                            }
                        }
                    }
                } else {
                    // --- READ MODE VIEW CONTROLS ---
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // SMS reader: only a bank card has messages to read.
                        if (card.accountType == AccountType.BANK) {
                            SmsReaderCard(
                                link = smsLink,
                                onEnable = onEnableSmsBanking,
                                onChangeSender = onChangeSmsSender,
                                onDisable = { showDisableSmsConfirm = true }
                            )
                        }

                        // Edit & Delete Management Actions Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Edit Button
                            Button(
                                onClick = { isEditing = true },
                                modifier = Modifier.weight(1f).height(50.dp).testTag("edit_card_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                            ) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ویرایش کارت", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = Color.White))
                            }

                            // Delete Button
                            OutlinedButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier.weight(1f).height(50.dp).testTag("delete_card_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("حذف کارت", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = Color(0xFFEF4444)))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDisableSmsConfirm) {
        AlertDialog(
            onDismissRequest = { showDisableSmsConfirm = false },
            title = {
                Text(
                    text = "غیرفعال‌سازی پیامک‌خوان",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = NavySecondary)
                )
            },
            text = {
                Text(
                    text = "پیامک‌های این کارت دیگر خوانده نمی‌شوند. تراکنش‌هایی که قبلاً ثبت شده‌اند باقی می‌مانند.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = SlateGray)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDisableSmsBanking()
                        showDisableSmsConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("غیرفعال کن", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDisableSmsConfirm = false },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.LightGray)
                ) {
                    Text("انصراف", style = MaterialTheme.typography.bodyMedium.copy(color = SlateGray))
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

/**
 * The SMS reader's status on a card: an invitation to switch it on, or — once
 * it is on — which sender feeds the card, with a way to change or stop it.
 */
@Composable
private fun SmsReaderCard(
    link: BankSender?,
    onEnable: () -> Unit,
    onChangeSender: () -> Unit,
    onDisable: () -> Unit
) {
    if (link == null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEnable() }
                .testTag("enable_sms_banking_button"),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2F6)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0EA5E9).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Sms, contentDescription = null, tint = Color(0xFF0284C7))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "فعال‌سازی دریافت خودکار از پیامک",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = NavySecondary)
                    )
                    Text(
                        text = "کارت و سرشمارهٔ بانک را مشخص کنید تا تراکنش‌ها خودکار ثبت شوند",
                        style = MaterialTheme.typography.bodySmall.copy(color = SlateGray)
                    )
                }
                Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = null, tint = Color(0xFF94A3B8))
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFECFDF5))
                .border(1.dp, Color(0xFFA7F3D0), RoundedCornerShape(16.dp))
                .padding(16.dp)
                .testTag("sms_reader_active_card")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "پیامک‌خوان فعال است",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF065F46))
                    )
                    Text(
                        text = "${link.bankName} · سرشماره ${link.senderNumber}",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF047857)),
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onChangeSender,
                    modifier = Modifier.weight(1f).testTag("sms_change_sender_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF10B981))
                ) {
                    Text("تغییر سرشماره", style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF047857), fontWeight = FontWeight.Bold))
                }
                OutlinedButton(
                    onClick = onDisable,
                    modifier = Modifier.weight(1f).testTag("sms_disable_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                ) {
                    Text("غیرفعال‌سازی", style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFEF4444), fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}
