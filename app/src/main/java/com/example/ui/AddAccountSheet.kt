package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AccountType
import com.example.data.BankBin
import com.example.ui.components.IranianBankLogo
import com.example.ui.theme.*

/**
 * Adding an account, from wherever the user happens to be — including in the
 * middle of recording a transaction, which is exactly when people notice the
 * account they need is missing.
 *
 * The card number is only asked for when there is a card. Demanding sixteen
 * digits is what made cash impossible to record before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountSheet(
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, cardNumber: String, balance: Long, accountType: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var accountType by remember { mutableStateOf(AccountType.BANK) }
    var name by remember { mutableStateOf("") }
    var cardNumber by remember { mutableStateOf("") }
    var balanceText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    // The first six digits identify the issuer, so the bank can be shown — and
    // its name offered — before the user finishes typing.
    val detectedBank = remember(cardNumber) { BankBin.bankFor(cardNumber) }

    // Fill the name from the card, but never overwrite something the user typed.
    LaunchedEffect(detectedBank) {
        if (detectedBank != null && name.isBlank()) name = detectedBank
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HorizonSurface,
        modifier = Modifier.testTag("add_account_sheet")
    ) {
        Column(
            modifier = Modifier
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "حساب جدید",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold, fontSize = 16.sp, color = HorizonInk
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                AccountTypeTile(
                    label = "کارت بانکی",
                    icon = Icons.Default.CreditCard,
                    selected = accountType == AccountType.BANK,
                    onClick = { accountType = AccountType.BANK; error = null },
                    modifier = Modifier.weight(1f),
                    testTag = "type_bank"
                )
                AccountTypeTile(
                    label = "نقدی",
                    icon = Icons.Default.Wallet,
                    selected = accountType == AccountType.CASH,
                    onClick = { accountType = AccountType.CASH; error = null },
                    modifier = Modifier.weight(1f),
                    testTag = "type_cash"
                )
                AccountTypeTile(
                    label = "سایر",
                    icon = Icons.Default.Savings,
                    selected = accountType == AccountType.OTHER,
                    onClick = { accountType = AccountType.OTHER; error = null },
                    modifier = Modifier.weight(1f),
                    testTag = "type_other"
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(24); error = null },
                label = { Text("نام حساب") },
                placeholder = {
                    Text(
                        when (accountType) {
                            AccountType.CASH -> "مثلاً پول نقد"
                            AccountType.OTHER -> "مثلاً قلک، کارت همسر"
                            else -> "مثلاً بانک ملی"
                        }
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account_name")
            )

            // Only a bank card has a number to give.
            if (accountType == AccountType.BANK) {
                Spacer(modifier = Modifier.height(12.dp))
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = cardNumber,
                        onValueChange = { input ->
                            cardNumber = input.filter { it.isDigit() }.take(16)
                            error = null
                        },
                        label = { Text("شمارهٔ کارت (اختیاری)") },
                        placeholder = { Text("6037 9911 1234 5678") },
                        visualTransformation = CardNumberTransformation,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        trailingIcon = {
                            if (detectedBank != null) {
                                IranianBankLogo(
                                    bankName = detectedBank,
                                    size = 28.dp,
                                    modifier = Modifier
                                        .padding(end = 10.dp)
                                        .testTag("detected_bank_logo")
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_card_number")
                    )
                }
                if (detectedBank != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "شناسایی شد: $detectedBank",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = HorizonGreen, fontWeight = FontWeight.Bold, fontSize = 11.5.sp
                        ),
                        modifier = Modifier.testTag("detected_bank_label")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = balanceText,
                onValueChange = { balanceText = it.filter { ch -> ch.isDigit() }.take(12); error = null },
                label = { Text("موجودی فعلی") },
                placeholder = { Text("0") },
                suffix = { Text("تومان", style = MaterialTheme.typography.bodySmall) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account_balance")
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "موجودی فعلی این حساب را وارد کنید تا ثروت خالص از همان ابتدا درست باشد.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = HorizonInkMuted, fontSize = 11.5.sp, lineHeight = 18.sp
                )
            )

            if (error != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = error!!,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = HorizonClay, fontWeight = FontWeight.Bold, fontSize = 12.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    val clean = name.trim()
                    when {
                        clean.isBlank() -> error = "نام حساب را وارد کنید."
                        // Balances are updated by account NAME, so two accounts
                        // sharing one would move together. Block it at the door.
                        existingNames.any { it.equals(clean, ignoreCase = true) } ->
                            error = "حسابی با این نام از قبل وجود دارد."
                        else -> {
                            onConfirm(
                                clean,
                                if (accountType == AccountType.BANK) cardNumber else "",
                                balanceText.toLongOrNull() ?: 0L,
                                accountType
                            )
                            onDismiss()
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HorizonGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("account_save")
            ) {
                Text(
                    text = "افزودن حساب",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun AccountTypeTile(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) HorizonGreenTint else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) HorizonGreen else HorizonBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .semantics { contentDescription = label }
            .testTag(testTag)
            .padding(vertical = 13.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) HorizonGreen else HorizonInkMuted,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) HorizonGreen else HorizonInkMuted
            ),
            maxLines = 1
        )
    }
}
