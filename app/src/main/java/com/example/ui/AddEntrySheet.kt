package com.example.ui

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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BankCard
import com.example.data.Category
import com.example.ui.theme.*
import com.example.utils.MoneyFormat
import kotlinx.coroutines.delay

private enum class EntryMode { EXPENSE, INCOME, TRANSFER }

/**
 * Latin, Persian and Arabic-Indic digits all reach this field depending on the
 * keyboard the user installed. They all parse, but they must not be *stored* or
 * *shown* mixed — the rest of the app is Latin, so normalise on the way in.
 */
private fun normalizeDigits(input: String): String = buildString {
    for (ch in input) {
        when (ch) {
            in '0'..'9' -> append(ch)
            in '۰'..'۹' -> append('0' + (ch - '۰'))
            in '٠'..'٩' -> append('0' + (ch - '٠'))
            else -> Unit          // separators, spaces, anything else: dropped
        }
    }
}

/**
 * The add sheet: amount first, then what kind of entry this is.
 *
 * Transfer is not a third flavour of the same form — it swaps the middle of the
 * sheet, because moving money between your own accounts has two accounts and no
 * category. It is recorded through the transfer path so it stays out of the
 * month's income and spending.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntrySheet(
    cards: List<BankCard>,
    categories: List<Category>,
    startAsExpense: Boolean = true,
    onDismiss: () -> Unit,
    onSaveEntry: (title: String, amount: Long, category: String, isExpense: Boolean, bankName: String) -> Unit,
    onTransfer: (fromCardName: String, toCardName: String, amount: Long) -> Unit,
    onCreateCategory: (name: String, iconKey: String, colorHex: String, isIncome: Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var mode by remember { mutableStateOf(if (startAsExpense) EntryMode.EXPENSE else EntryMode.INCOME) }
    var amountText by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var selectedAccount by remember { mutableStateOf(cards.firstOrNull()) }
    var fromAccount by remember { mutableStateOf(cards.firstOrNull()) }
    var toAccount by remember { mutableStateOf(cards.firstOrNull { it.bankName != cards.firstOrNull()?.bankName }) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val amount = amountText.toLongOrNull() ?: 0L
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(220)                    // let the sheet finish animating in
        runCatching { focusRequester.requestFocus() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HorizonSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.testTag("add_entry_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()                     // the keyboard must not cover Save
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {

            ModeSelector(
                mode = mode,
                onModeChange = {
                    mode = it
                    error = null
                }
            )

            Spacer(modifier = Modifier.height(22.dp))

            AmountField(
                amountText = amountText,
                onAmountChange = { amountText = normalizeDigits(it).take(12) },
                accent = accentFor(mode),
                focusRequester = focusRequester
            )

            Spacer(modifier = Modifier.height(12.dp))

            QuickAmountChips(
                onAdd = { value ->
                    val current = amountText.toLongOrNull() ?: 0L
                    amountText = (current + value).toString()
                },
                onAppendZeros = {
                    if (amountText.isNotEmpty() && amountText != "0") amountText += "000"
                }
            )

            Spacer(modifier = Modifier.height(22.dp))

            if (mode == EntryMode.TRANSFER) {
                TransferFields(
                    cards = cards,
                    fromAccount = fromAccount,
                    toAccount = toAccount,
                    onFromChange = { fromAccount = it },
                    onToChange = { toAccount = it }
                )
            } else {
                CategoryRow(
                    selected = selectedCategory,
                    onClick = { showCategoryPicker = true }
                )
                Spacer(modifier = Modifier.height(12.dp))
                AccountPicker(
                    label = "از حساب",
                    cards = cards,
                    selected = selectedAccount,
                    onSelect = { selectedAccount = it }
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان (اختیاری)") },
                    placeholder = { Text(selectedCategory?.name ?: "مثلاً خرید نان") },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("entry_title")
                )
            }

            if (error != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = error!!,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = HorizonClay, fontWeight = FontWeight.Bold, fontSize = 12.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Button(
                onClick = {
                    when {
                        amount <= 0L -> error = "مبلغ را وارد کنید."
                        mode == EntryMode.TRANSFER && (fromAccount == null || toAccount == null) ->
                            error = "برای انتقال به دو حساب نیاز است."
                        mode == EntryMode.TRANSFER && fromAccount?.bankName == toAccount?.bankName ->
                            error = "مبدأ و مقصد نمی‌توانند یکی باشند."
                        mode != EntryMode.TRANSFER && selectedCategory == null ->
                            error = "یک دسته انتخاب کنید."
                        mode != EntryMode.TRANSFER && selectedAccount == null ->
                            error = "ابتدا یک کارت بانکی ثبت کنید."
                        else -> {
                            if (mode == EntryMode.TRANSFER) {
                                onTransfer(fromAccount!!.bankName, toAccount!!.bankName, amount)
                            } else {
                                val category = selectedCategory!!
                                onSaveEntry(
                                    title.trim().ifBlank { category.name },
                                    amount,
                                    category.name,
                                    mode == EntryMode.EXPENSE,
                                    selectedAccount!!.bankName
                                )
                            }
                            onDismiss()
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentFor(mode)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("entry_save")
            ) {
                Text(
                    text = when (mode) {
                        EntryMode.EXPENSE -> "ثبت هزینه"
                        EntryMode.INCOME -> "ثبت درآمد"
                        EntryMode.TRANSFER -> "ثبت انتقال"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp
                    )
                )
            }
        }
    }

    if (showCategoryPicker) {
        CategoryPickerSheet(
            categories = categories,
            isIncomeMode = mode == EntryMode.INCOME,
            onPick = {
                selectedCategory = it
                showCategoryPicker = false
            },
            onCreate = { name, iconKey, colorHex, isIncome ->
                onCreateCategory(name, iconKey, colorHex, isIncome)
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false }
        )
    }
}

private fun accentFor(mode: EntryMode): Color = when (mode) {
    EntryMode.EXPENSE -> HorizonClay
    EntryMode.INCOME -> HorizonGreen
    EntryMode.TRANSFER -> Color(0xFF1D6FA3)
}

@Composable
private fun ModeSelector(mode: EntryMode, onModeChange: (EntryMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HorizonBackground)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ModeTab("هزینه", mode == EntryMode.EXPENSE, HorizonClay, { onModeChange(EntryMode.EXPENSE) }, Modifier.weight(1f), "mode_expense")
        ModeTab("درآمد", mode == EntryMode.INCOME, HorizonGreen, { onModeChange(EntryMode.INCOME) }, Modifier.weight(1f), "mode_income")
        ModeTab("انتقال", mode == EntryMode.TRANSFER, Color(0xFF1D6FA3), { onModeChange(EntryMode.TRANSFER) }, Modifier.weight(1f), "mode_transfer")
    }
}

@Composable
private fun ModeTab(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) accent else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 11.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) Color.White else HorizonInkMuted,
                fontSize = 13.sp
            )
        )
    }
}

@Composable
private fun AmountField(
    amountText: String,
    onAmountChange: (String) -> Unit,
    accent: Color,
    focusRequester: FocusRequester
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            BasicTextFieldAmount(
                value = amountText,
                onValueChange = onAmountChange,
                accent = accent,
                focusRequester = focusRequester
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "تومان",
            style = MaterialTheme.typography.bodySmall.copy(
                color = HorizonInkMuted, fontSize = 12.sp
            )
        )
    }
}

@Composable
private fun BasicTextFieldAmount(
    value: String,
    onValueChange: (String) -> Unit,
    accent: Color,
    focusRequester: FocusRequester
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = "0",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 34.sp, color = HorizonInkFaint, textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        textStyle = MaterialTheme.typography.headlineLarge.copy(
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = accent,
            textAlign = TextAlign.Center,
            fontFeatureSettings = "tnum"
        ),
        visualTransformation = ThousandsSeparator,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .testTag("entry_amount")
    )
}

@Composable
private fun QuickAmountChips(onAdd: (Long) -> Unit, onAppendZeros: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(50_000L, 100_000L, 500_000L).forEach { value ->
            QuickChip(
                label = MoneyFormat.amount(value),
                onClick = { onAdd(value) },
                modifier = Modifier.weight(1f),
                testTag = "chip_$value"
            )
        }
        QuickChip(
            label = "000",
            onClick = onAppendZeros,
            modifier = Modifier.width(58.dp),
            testTag = "chip_zeros"
        )
    }
}

@Composable
private fun QuickChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, testTag: String) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(HorizonGreenTint)
            .clickable { onClick() }
            .padding(vertical = 10.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = HorizonGreen, fontWeight = FontWeight.Bold,
                    fontSize = 12.sp, fontFeatureSettings = "tnum"
                )
            )
        }
    }
}

@Composable
private fun CategoryRow(selected: Category?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, HorizonBorder, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp)
            .semantics { contentDescription = "انتخاب دسته" }
            .testTag("entry_category"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = selected?.let { CategoryIcons.colorOf(it.colorHex) } ?: HorizonInkFaint
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = CategoryIcons.vectorFor(selected?.iconKey ?: "other"),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(modifier = Modifier.width(11.dp))
        Text(
            text = selected?.name ?: "انتخاب دسته",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                fontWeight = if (selected != null) FontWeight.Bold else FontWeight.Normal,
                color = if (selected != null) HorizonInk else HorizonInkMuted
            ),
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.ChevronLeft,
            contentDescription = null,
            tint = HorizonInkFaint,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun AccountPicker(
    label: String,
    cards: List<BankCard>,
    selected: BankCard?,
    onSelect: (BankCard) -> Unit
) {
    if (cards.isEmpty()) {
        Text(
            text = "هنوز کارتی ثبت نشده. ابتدا از بخش کارت‌های بانکی یک کارت اضافه کنید.",
            style = MaterialTheme.typography.bodySmall.copy(color = HorizonClay, fontSize = 12.sp)
        )
        return
    }
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(color = HorizonInkMuted, fontSize = 11.sp)
        )
        Spacer(modifier = Modifier.height(7.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            cards.take(3).forEach { card ->
                val on = card.bankName == selected?.bankName
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (on) HorizonGreenTint else Color.Transparent)
                        .border(
                            1.dp,
                            if (on) HorizonGreen else HorizonBorder,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelect(card) }
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = card.bankName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            color = if (on) HorizonGreen else HorizonInkMuted
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun TransferFields(
    cards: List<BankCard>,
    fromAccount: BankCard?,
    toAccount: BankCard?,
    onFromChange: (BankCard) -> Unit,
    onToChange: (BankCard) -> Unit
) {
    Column {
        AccountPicker("از حساب", cards, fromAccount, onFromChange)
        Spacer(modifier = Modifier.height(14.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.SwapVert,
                contentDescription = null,
                tint = HorizonInkFaint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        AccountPicker("به حساب", cards, toAccount, onToChange)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "انتقال بین حساب‌های خودتان در درآمد و هزینهٔ ماه شمرده نمی‌شود.",
            style = MaterialTheme.typography.bodySmall.copy(
                color = HorizonInkMuted, fontSize = 11.5.sp, lineHeight = 18.sp
            )
        )
    }
}

/**
 * Shows 1234567 as 1,234,567 while the field keeps storing plain digits.
 *
 * The offset mapping is the fiddly half: a comma sits before digit i whenever
 * (length - i) is a multiple of three, so the caret has to skip those or it
 * lands a character off the moment the number crosses a thousand.
 */
private object ThousandsSeparator : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        if (digits.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val n = digits.length
        val grouped = buildString {
            digits.forEachIndexed { index, ch ->
                if (index > 0 && (n - index) % 3 == 0) append(',')
                append(ch)
            }
        }

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val safe = offset.coerceIn(0, n)
                var commas = 0
                for (i in 1 until safe) if ((n - i) % 3 == 0) commas++
                return (safe + commas).coerceIn(0, grouped.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val safe = offset.coerceIn(0, grouped.length)
                val commas = grouped.take(safe).count { it == ',' }
                return (safe - commas).coerceIn(0, n)
            }
        }
        return TransformedText(AnnotatedString(grouped), mapping)
    }
}
