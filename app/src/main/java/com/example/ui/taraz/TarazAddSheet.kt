package com.example.ui.taraz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import com.example.data.BankCard
import com.example.data.Category
import com.example.data.Transaction
import com.example.data.countsAsExpense
import com.example.data.isCash
import com.example.ui.AddAccountSheet
import com.example.ui.CategoryIcons
import com.example.ui.CategoryPickerSheet
import com.example.utils.NumberToPersianWords
import java.util.Calendar

private enum class Kind { EXPENSE, INCOME, TRANSFER }

private data class Suggestion(val title: String, val amount: Long, val category: String, val account: String)

/**
 * Pick the expense the user most often records around this time of day — the
 * "ناهار ۳۵۰٬۰۰۰ از سامان" chip. Nothing is filled until they press "استفاده".
 */
private fun suggest(history: List<Transaction>, now: Long = System.currentTimeMillis()): Suggestion? {
    val hourNow = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    val since = now - 60L * 24 * 60 * 60 * 1000
    return history.asSequence()
        .filter { it.countsAsExpense && it.date >= since && it.title.isNotBlank() }
        .filter {
            val h = Calendar.getInstance().apply { timeInMillis = it.date }.get(Calendar.HOUR_OF_DAY)
            kotlin.math.abs(h - hourNow) <= 1
        }
        .groupBy { it.title.trim() }
        .filter { it.value.size >= 2 }
        .maxByOrNull { it.value.size }
        ?.value?.maxByOrNull { it.date }
        ?.let { Suggestion(it.title.trim(), it.amount, it.category, it.bankName) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TarazAddSheet(
    cards: List<BankCard>,
    categories: List<Category>,
    history: List<Transaction>,
    startAsTransfer: Boolean = false,
    onDismiss: () -> Unit,
    onSaveEntry: (title: String, amount: Long, category: String, isExpense: Boolean, bankName: String) -> Unit,
    onTransfer: (fromCardName: String, toCardName: String, amount: Long) -> Unit,
    onCreateCategory: (name: String, iconKey: String, colorHex: String, isIncome: Boolean) -> Unit,
    onCreateAccount: (name: String, cardNumber: String, balance: Long, accountType: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var kind by remember { mutableStateOf(if (startAsTransfer) Kind.TRANSFER else Kind.EXPENSE) }
    var amountText by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var categoryName by remember { mutableStateOf<String?>(null) }
    var accountName by remember { mutableStateOf(cards.firstOrNull()?.bankName) }
    var fromName by remember { mutableStateOf(cards.firstOrNull()?.bankName) }
    var toName by remember { mutableStateOf(cards.getOrNull(1)?.bankName) }
    var applied by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var showNewAccount by remember { mutableStateOf(false) }

    val suggestion = remember(history) { suggest(history) }
    val amount = amountText.toLongOrNull() ?: 0L
    val isTransfer = kind == Kind.TRANSFER
    val visibleCategories = categories.filter { it.isIncome == (kind == Kind.INCOME) }.sortedBy { it.sortOrder }

    val problem: String? = when {
        cards.isEmpty() -> "اول یک حساب بسازید"
        amount <= 0 -> "اول مبلغ را وارد کنید"
        isTransfer && cards.size < 2 -> "برای انتقال دو حساب لازم است"
        isTransfer && (fromName == null || toName == null) -> "حساب مبدأ و مقصد را انتخاب کنید"
        isTransfer && fromName == toName -> "مبدأ و مقصد نمی‌توانند یکی باشند"
        !isTransfer && categoryName == null -> "یک دسته انتخاب کنید"
        !isTransfer && accountName == null -> "یک حساب انتخاب کنید"
        else -> null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Tz.Surface,
        modifier = Modifier.testTag("add_entry_sheet")
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TzSegmented(
                    options = listOf(Kind.EXPENSE to "هزینه", Kind.INCOME to "درآمد", Kind.TRANSFER to "انتقال"),
                    selected = kind,
                    onSelect = {
                        kind = it
                        categoryName = null
                    },
                    modifier = Modifier.weight(1f),
                    height = 38.dp
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Tz.Field)
                        .tzClick(onDismiss)
                        .semantics { contentDescription = "بستن" },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Close, null, tint = Tz.Ink, modifier = Modifier.size(20.dp)) }
            }

            if (suggestion != null && !applied && kind == Kind.EXPENSE && amount == 0L) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFF1F4FF))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = Tz.Cobalt, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${suggestion.title} · ${TzFormat.money(suggestion.amount)} · ${suggestion.account}",
                            style = tzText(13.sp, FontWeight.SemiBold), maxLines = 1
                        )
                        Text("این ساعت معمولاً همین را ثبت می‌کنید", style = tzText(12.sp, color = Tz.Muted))
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .height(36.dp)
                            .clip(CircleShape)
                            .background(Tz.Cobalt)
                            .tzClick {
                                amountText = suggestion.amount.toString()
                                title = suggestion.title
                                categoryName = suggestion.category
                                if (cards.any { it.bankName == suggestion.account }) accountName = suggestion.account
                                applied = true
                            }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("استفاده", style = tzText(12.sp, FontWeight.Bold, Color.White)) }
                }
            }

            // Amount
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    TzFormat.money(amount),
                    style = tzText(
                        54.sp, FontWeight.Light,
                        when {
                            amount == 0L -> Color(0xFFC1C6CF)
                            kind == Kind.INCOME -> Tz.Green
                            else -> Tz.Ink
                        },
                        letterSpacing = (-1.5).sp
                    )
                )
                Text(
                    if (amount > 0) NumberToPersianWords.convert(amount) + " تومان" else "تومان",
                    style = tzText(13.sp, color = Tz.Muted, align = TextAlign.Center)
                )
            }

            if (!isTransfer) {
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
                    items(visibleCategories, key = { it.id }) { c ->
                        CategoryBubble(
                            label = c.name,
                            iconKey = c.iconKey,
                            selected = categoryName == c.name,
                            onClick = { categoryName = c.name }
                        )
                    }
                    item {
                        CategoryBubble(label = "بیشتر", iconKey = null, selected = false, onClick = { showPicker = true })
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AccountPicker(
                        cards = cards,
                        selected = accountName,
                        prefix = if (kind == Kind.INCOME) "به" else "از",
                        onSelect = { accountName = it },
                        onNew = { showNewAccount = true },
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Tz.Field)
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("امروز، " + TzFormat.time(System.currentTimeMillis()), style = tzText(13.sp, FontWeight.Medium)) }
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Tz.Field)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (title.isEmpty()) Text("عنوان (اختیاری)، مثلاً ناهار", style = tzText(13.sp, color = Tz.Faint))
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it.take(60) },
                        singleLine = true,
                        textStyle = tzText(14.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Tz.Field)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    AccountPicker(cards, fromName, "از", { fromName = it }, { showNewAccount = true }, Modifier.fillMaxWidth(), filled = false)
                    Hairline()
                    AccountPicker(cards, toName, "به", { toName = it }, { showNewAccount = true }, Modifier.fillMaxWidth(), filled = false)
                }
                Spacer(Modifier.height(6.dp))
                Text("انتقال بین حساب‌های خودتان در درآمد و هزینه شمرده نمی‌شود.", style = tzText(12.sp, color = Tz.Muted))
            }

            Spacer(Modifier.height(14.dp))
            Keypad(
                onDigit = { d ->
                    amountText = when {
                        amountText.isEmpty() && (d == "0" || d == "000") -> ""
                        (amountText + d).length > 12 -> amountText
                        else -> amountText + d
                    }
                },
                onBackspace = { amountText = amountText.dropLast(1) }
            )
            Spacer(Modifier.height(10.dp))
            if (cards.isEmpty()) {
                PrimaryButton("ساخت اولین حساب", onClick = { showNewAccount = true })
            } else {
                PrimaryButton(
                    text = problem ?: when (kind) {
                        Kind.EXPENSE -> "ثبت هزینهٔ ${TzFormat.money(amount)} تومانی"
                        Kind.INCOME -> "ثبت درآمد ${TzFormat.money(amount)} تومانی"
                        Kind.TRANSFER -> "ثبت انتقال ${TzFormat.money(amount)} تومانی"
                    },
                    enabled = problem == null,
                    onClick = {
                        if (isTransfer) {
                            onTransfer(fromName!!, toName!!, amount)
                        } else {
                            val cat = categoryName!!
                            onSaveEntry(title.trim().ifBlank { cat }, amount, cat, kind == Kind.EXPENSE, accountName!!)
                        }
                        onDismiss()
                    }
                )
            }
        }
    }

    if (showPicker) {
        CategoryPickerSheet(
            categories = categories,
            isIncomeMode = kind == Kind.INCOME,
            onPick = {
                categoryName = it.name
                showPicker = false
            },
            onCreate = { name, icon, color, isIncome ->
                onCreateCategory(name, icon, color, isIncome)
                categoryName = name
            },
            onDismiss = { showPicker = false }
        )
    }
    if (showNewAccount) {
        AddAccountSheet(
            existingNames = cards.map { it.bankName },
            onDismiss = { showNewAccount = false },
            onConfirm = { name, number, balance, type ->
                onCreateAccount(name, number, balance, type)
                if (accountName == null) accountName = name
                if (fromName == null) fromName = name
                showNewAccount = false
            }
        )
    }
}

@Composable
private fun CategoryBubble(label: String, iconKey: String?, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .tzClick(onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (selected) Tz.Cobalt else Tz.Field),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (iconKey != null) CategoryIcons.vectorFor(iconKey) else Icons.Filled.MoreHoriz,
                contentDescription = null,
                tint = if (selected) Color.White else Tz.Ink,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = tzText(12.sp, if (selected) FontWeight.Bold else FontWeight.Medium, if (selected) Tz.Cobalt else Tz.Ink, align = TextAlign.Center),
            maxLines = 1
        )
    }
}

@Composable
internal fun AccountPicker(
    cards: List<BankCard>,
    selected: String?,
    prefix: String,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true
) {
    var open by remember { mutableStateOf(false) }
    val card = cards.firstOrNull { it.bankName == selected }
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (filled) Tz.Field else Color.Transparent)
                .tzClick { open = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(prefix, style = tzText(12.sp, color = Tz.Muted))
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(width = 26.dp, height = 18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (card != null) accountColor(card.bankName, card.isCash) else Tz.Border)
            )
            Spacer(Modifier.width(8.dp))
            Text(card?.bankName ?: "انتخاب حساب", style = tzText(13.sp, FontWeight.SemiBold), maxLines = 1, modifier = Modifier.weight(1f))
            if (card != null) {
                Text(TzFormat.short(card.balance), style = tzText(12.sp, color = Tz.Muted))
                Spacer(Modifier.width(4.dp))
            }
            Icon(Icons.Filled.ExpandMore, null, tint = Tz.Muted, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Tz.Surface) {
            cards.forEach { c ->
                DropdownMenuItem(
                    text = {
                        Text("${c.bankName}  ·  ${TzFormat.money(c.balance)}", style = tzText(14.sp, if (c.bankName == selected) FontWeight.Bold else FontWeight.Normal))
                    },
                    onClick = {
                        onSelect(c.bankName)
                        open = false
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("+ حساب جدید", style = tzText(14.sp, FontWeight.Bold, Tz.CobaltText)) },
                onClick = {
                    open = false
                    onNew()
                }
            )
        }
    }
}

/** Phone-style keypad; digits keep their usual left-to-right order even in RTL. */
@Composable
private fun Keypad(onDigit: (String) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("000", "0", "back"))
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { key ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Tz.Ground)
                                .border(1.dp, Tz.Hairline, RoundedCornerShape(16.dp))
                                .tzClick { if (key == "back") onBackspace() else onDigit(key) }
                                .semantics { contentDescription = if (key == "back") "پاک کردن" else TzFormat.fa(key) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (key == "back") {
                                Icon(Icons.AutoMirrored.Filled.Backspace, null, tint = Tz.Ink, modifier = Modifier.size(22.dp))
                            } else {
                                Text(TzFormat.fa(key), style = tzText(24.sp, FontWeight.Medium))
                            }
                        }
                    }
                }
            }
        }
    }
}
