package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.EmeraldPrimary
import com.example.ui.BackgroundLight
import com.example.ui.NavySecondary
import com.example.ui.SlateGray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCardBottomSheet(
    onDismiss: () -> Unit,
    onConfirm: (bankName: String, cardNumber: String, balance: Long, cardHolderName: String) -> Unit
) {
    var bankName by remember { mutableStateOf("") }
    var cardNumber by remember { mutableStateOf("") }
    var cardHolderName by remember { mutableStateOf("") }
    var initialBalance by remember { mutableStateOf("") }
    
    var dropdownExpanded by remember { mutableStateOf(false) }
    var showManualBankSelector by remember { mutableStateOf(false) }
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
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.LightGray.copy(alpha = 0.5f))
                    .padding(bottom = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "افزودن کارت بانکی جدید",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = NavySecondary
                ),
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // --- CARD HOLDER NAME ---
            Text(
                text = "نام صاحب کارت / نام حساب",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = NavySecondary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                textAlign = TextAlign.Start
            )

            OutlinedTextField(
                value = cardHolderName,
                onValueChange = { cardHolderName = it },
                placeholder = { Text("مثال: علیرضا محمدی", style = MaterialTheme.typography.bodyMedium.copy(color = SlateGray)) },
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_add_card_holder_name_sheet"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmeraldPrimary,
                    unfocusedBorderColor = Color.LightGray.copy(alpha = 0.5f),
                    focusedContainerColor = BackgroundLight.copy(alpha = 0.2f),
                    unfocusedContainerColor = BackgroundLight.copy(alpha = 0.2f)
                ),
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = SlateGray)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // --- CARD NUMBER ---
            Text(
                text = "شماره 16 رقمی کارت",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = NavySecondary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                textAlign = TextAlign.Start
            )

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                OutlinedTextField(
                    value = cardNumber,
                    onValueChange = { input ->
                        val cleaned = input.filter { it.isDigit() }
                        if (cleaned.length <= 16) {
                            cardNumber = cleaned
                            if (cleaned.length >= 6) {
                                val detectedBank = com.example.sms.util.BankUtils.detectBankFromCardNumber(cleaned)
                                if (detectedBank != null) {
                                    bankName = detectedBank
                                }
                            } else if (cleaned.isEmpty()) {
                                bankName = ""
                            }
                        }
                    },
                    visualTransformation = CardNumberFilter(),
                    placeholder = { Text("مثال: 6037997512345678", style = MaterialTheme.typography.bodyMedium.copy(color = SlateGray, textAlign = TextAlign.End), modifier = Modifier.fillMaxWidth()) },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Left
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_add_card_number_sheet"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = Color.LightGray.copy(alpha = 0.5f),
                        focusedContainerColor = BackgroundLight.copy(alpha = 0.2f),
                        unfocusedContainerColor = BackgroundLight.copy(alpha = 0.2f)
                    ),
                    leadingIcon = {
                        if (bankName.isNotEmpty()) {
                            Box(modifier = Modifier.padding(start = 8.dp)) {
                                IranianBankLogo(bankName = bankName, size = 28.dp)
                            }
                        } else {
                            Icon(imageVector = Icons.Default.CreditCard, contentDescription = null, tint = SlateGray)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- INITIAL CAPITAL / BALANCE ---
            Text(
                text = "موجودی اولیه (تومان)",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = NavySecondary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                textAlign = TextAlign.Start
            )

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                OutlinedTextField(
                    value = initialBalance,
                    onValueChange = { input ->
                        val cleaned = input.filter { it.isDigit() }
                        initialBalance = cleaned
                    },
                    visualTransformation = ThousandsSeparatorFilter(),
                    placeholder = { Text("مثال: 5,000,000", style = MaterialTheme.typography.bodyMedium.copy(color = SlateGray, textAlign = TextAlign.End), modifier = Modifier.fillMaxWidth()) },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Left
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_add_card_balance_sheet"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = Color.LightGray.copy(alpha = 0.5f),
                        focusedContainerColor = BackgroundLight.copy(alpha = 0.2f),
                        unfocusedContainerColor = BackgroundLight.copy(alpha = 0.2f)
                    ),
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.AttachMoney, contentDescription = null, tint = SlateGray)
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- MORE OPTIONS / MANUAL BANK SELECTOR ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showManualBankSelector = !showManualBankSelector }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = "نام بانک",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = NavySecondary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        textAlign = TextAlign.Start
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(BackgroundLight.copy(alpha = 0.5f))
                                .border(
                                    1.dp,
                                    if (dropdownExpanded) EmeraldPrimary else Color.LightGray.copy(alpha = 0.5f),
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { dropdownExpanded = true }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (bankName.isNotEmpty()) {
                                    IranianBankLogo(bankName = bankName, size = 28.dp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = bankName,
                                        style = MaterialTheme.typography.bodyLarge.copy(color = NavySecondary, fontWeight = FontWeight.Medium)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalance,
                                        contentDescription = null,
                                        tint = SlateGray
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "انتخاب بانک از لیست...",
                                        style = MaterialTheme.typography.bodyLarge.copy(color = SlateGray)
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (dropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = NavySecondary
                            )
                        }

                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .heightIn(max = 280.dp)
                                .background(Color.White)
                        ) {
                            popularBanks.forEach { bank ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            IranianBankLogo(bankName = bank, size = 24.dp)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = bank,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Medium,
                                                    color = NavySecondary
                                                )
                                            )
                                        }
                                    },
                                    onClick = {
                                        bankName = bank
                                        dropdownExpanded = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            if (showError) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- ACTION BUTTONS ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        val bal = initialBalance.toLongOrNull()
                        if (bankName.isBlank()) {
                            errorMessage = "لطفاً یک بانک را انتخاب کنید."
                            showError = true
                        } else if (cardHolderName.isBlank()) {
                            errorMessage = "لطفاً نام صاحب کارت را وارد کنید."
                            showError = true
                        } else if (cardNumber.length < 16) {
                            errorMessage = "شماره کارت باید دقیقاً ۱۶ رقم باشد."
                            showError = true
                        } else if (bal == null) {
                            errorMessage = "لطفاً موجودی اولیه معتبری وارد کنید."
                            showError = true
                        } else {
                            showError = false
                            val formattedNumber = "${cardNumber.substring(0,4)}****${cardNumber.substring(12,16)}"
                            onConfirm(bankName, formattedNumber, bal, cardHolderName)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("btn_add_card_confirm_sheet"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text(
                        text = "ثبت کارت",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                    )
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(0.7f)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateGray),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)
                ) {
                    Text(
                        text = "انصراف",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                    )
                }
            }
        }
    }
}
