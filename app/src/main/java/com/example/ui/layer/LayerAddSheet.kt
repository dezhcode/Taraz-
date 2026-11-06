package com.example.ui.layer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import com.example.data.BankCard
import com.example.data.Category
import com.example.data.Transaction
import com.example.data.isTransfer
import com.example.ui.CategoryIcons
import com.example.utils.NumberToPersianWords
import java.util.Calendar

private enum class EntryType(val label: String, val cta: String) {
    EXPENSE("هزینه", "ثبت هزینه"), INCOME("درآمد", "ثبت درآمد"), TRANSFER("انتقال", "ثبت انتقال")
}

private data class Suggestion(val title: String, val category: String, val bank: String, val amount: Long)

/**
 * شیت ثبت تراکنش طرح «لایه»: نوع، پیشنهاد هوشمند ساعت، مبلغ درشت + حروف،
 * دسته‌ها، حساب، و صفحه‌کلید عددی.
 */
@Composable
fun LayerAddSheet(
    categories: List<Category>,
    cards: List<BankCard>,
    transactions: List<Transaction>,
    startAsExpense: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (title: String, amount: Long, category: String, isExpense: Boolean, bankName: String, date: Long?) -> Unit,
    onTransfer: (from: String, to: String, amount: Long) -> Unit,
    persons: List<com.example.data.Person> = emptyList(),
    personBalances: Map<Int, Long> = emptyMap(),
    onPersonMoney: (personId: Int, amount: Long, outgoing: Boolean, account: String?, note: String) -> Unit = { _, _, _, _, _ -> },
    isListening: Boolean = false,
    onVoice: ((com.example.ui.FinanceViewModel.EntryDraft) -> Unit) -> Unit = {},
    onReadReceipt: (android.net.Uri, (com.example.ui.FinanceViewModel.EntryDraft?, String?) -> Unit) -> Unit = { _, _ -> }
) {
    BackHandler(onBack = onDismiss)
    // تاریخی که از رسید یا جملهٔ «دیروز…» آمده؛ null یعنی همین الان
    var dateOverride by remember { mutableStateOf<Long?>(null) }
    var helperMsg by remember { mutableStateOf<String?>(null) }
    // اگر این پول برای یک شخص بود (قرض دادن/گرفتن یا پس دادن)، در حساب همان شخص ثبت می‌شود
    var personId by remember { mutableStateOf<Int?>(null) }

    var type by remember { mutableStateOf(if (startAsExpense) EntryType.EXPENSE else EntryType.INCOME) }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var showAllCats by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var editingNote by remember { mutableStateOf(false) }
    var account by remember { mutableStateOf(cards.firstOrNull()?.bankName) }
    var transferTo by remember { mutableStateOf(cards.getOrNull(1)?.bankName) }
    var suggestionUsed by remember { mutableStateOf(false) }

    val n = amount.toLongOrNull() ?: 0L
    val typeCats = categories.filter { it.isIncome == (type == EntryType.INCOME) }.sortedBy { it.sortOrder }

    fun applyDraft(d: com.example.ui.FinanceViewModel.EntryDraft) {
        if (d.amount > 0) amount = d.amount.toString()
        type = if (d.isExpense) EntryType.EXPENSE else EntryType.INCOME
        val cats = categories.filter { it.isIncome == !d.isExpense }
        category = d.category?.let { c -> cats.firstOrNull { it.name == c }?.name }
        d.title?.let { note = it.take(60) }
        d.account?.let { a -> cards.firstOrNull { it.bankName == a || it.bankName.contains(a) || a.contains(it.bankName) }?.let { account = it.bankName } }
        d.personName?.let { p -> persons.firstOrNull { it.name == p || it.name.contains(p) || p.contains(it.name) }?.let { personId = it.id } }
        dateOverride = d.date
        helperMsg = if (d.amount > 0) "پر شد؛ پیش از ثبت بررسی کنید." else "مبلغ را نفهمیدم؛ دستی وارد کنید."
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val micLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) onVoice { applyDraft(it) } else helperMsg = "برای ثبت صوتی، دسترسی میکروفون لازم است." }
    val photoLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            helperMsg = "در حال خواندن رسید…"
            onReadReceipt(uri) { d, err -> if (d != null) applyDraft(d) else helperMsg = err }
        }
    }
    val suggestion = remember(transactions) { findSuggestion(transactions) }

    Box(Modifier.fillMaxSize().background(Color(0x990E1116)).pointerInput(Unit) { detectTapGestures { onDismiss() } }) {
        Column(
            Modifier
                .statusBarsPadding()
                .padding(top = 34.dp)
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(L.Surface)
                .pointerInput(Unit) { detectTapGestures { } }
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(5.dp).clip(PillShape).background(L.Handle))

            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.clip(PillShape).background(L.Chip).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    EntryType.entries.forEach { t ->
                        val on = t == type
                        Box(
                            Modifier
                                .height(38.dp)
                                .then(if (on) Modifier.shadow(2.dp, PillShape) else Modifier)
                                .clip(PillShape)
                                .background(if (on) L.Surface else Color.Transparent)
                                .clickable { type = t; category = null; showAllCats = false; if (t == EntryType.TRANSFER) personId = null }
                                .semantics { selected = on }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) { Text(t.label, style = lt(13.sp, FontWeight.SemiBold, if (on) L.Ink else L.Muted)) }
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(44.dp).clip(PillShape).background(L.Chip).clickable(onClick = onDismiss)
                        .semantics { contentDescription = "بستن" },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Close, null, tint = L.Ink, modifier = Modifier.size(20.dp)) }
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (suggestion != null && !suggestionUsed && type == EntryType.EXPENSE) {
                    Row(
                        Modifier.padding(top = 14.dp).fillMaxWidth().clip(ItemShape).background(L.PrimaryTint).padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, tint = L.Primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${suggestion.title} · ${money(suggestion.amount)} · ${suggestion.bank}", style = lt(13.sp, FontWeight.SemiBold), maxLines = 1)
                            Text("این ساعت معمولاً همین را ثبت می‌کنید", style = lt(12.sp, color = L.Muted))
                        }
                        Box(
                            Modifier.height(36.dp).clip(PillShape).background(L.Primary).clickable {
                                amount = suggestion.amount.toString()
                                category = suggestion.category
                                note = suggestion.title
                                if (cards.any { it.bankName == suggestion.bank }) account = suggestion.bank
                                suggestionUsed = true
                            }.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("استفاده", style = lt(12.sp, FontWeight.Bold, Color.White)) }
                    }
                }

                // مبلغ
                Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        money(n),
                        style = lt(54.sp, FontWeight.Normal, if (n == 0L) L.Placeholder else if (type == EntryType.INCOME) L.Green else L.Ink, letterSpacing = (-1.5).sp)
                    )
                    Text(
                        if (n == 0L) "تومان" else NumberToPersianWords.convert(n) + " تومان",
                        style = lt(13.sp, color = L.Muted), maxLines = 2
                    )
                }

                if (type != EntryType.TRANSFER) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                        Row(
                            Modifier.height(38.dp).clip(PillShape).background(if (isListening) L.Primary else L.Chip)
                                .clickable {
                                    val ok = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
                                        android.content.pm.PackageManager.PERMISSION_GRANTED
                                    if (ok) { helperMsg = if (isListening) null else "بگویید، مثلاً «۸۰ تومن ناهار»"; onVoice { applyDraft(it) } }
                                    else micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Mic, null, tint = if (isListening) Color.White else L.Ink, modifier = Modifier.size(18.dp))
                            Text(if (isListening) "گوش می‌دهم…" else "بگویید", style = lt(13.sp, FontWeight.SemiBold, if (isListening) Color.White else L.Ink))
                        }
                        if (type == EntryType.EXPENSE) {
                            Row(
                                Modifier.height(38.dp).clip(PillShape).background(L.Chip).clickable { photoLauncher.launch("image/*") }.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.ReceiptLong, null, tint = L.Ink, modifier = Modifier.size(18.dp))
                                Text("عکس رسید", style = lt(13.sp, FontWeight.SemiBold))
                            }
                        }
                    }
                    helperMsg?.let {
                        Text(it, style = lt(12.sp, color = L.PrimaryText), textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                }

                if (type != EntryType.TRANSFER) {
                    val shown = if (showAllCats) typeCats else typeCats.take(4)
                    val rows = (shown.map { it as Category? } + if (!showAllCats && typeCats.size > 4) listOf(null) else emptyList()).chunked(5)
                    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        rows.forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { c ->
                                    val on = c != null && c.name == category
                                    Column(
                                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                            .clickable { if (c == null) showAllCats = true else category = c.name }
                                            .semantics { selected = on },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(if (on) L.Primary else L.Chip),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                if (c == null) Icons.Default.MoreHoriz else CategoryIcons.vectorFor(c.iconKey),
                                                null, tint = if (on) Color.White else L.Ink, modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Text(c?.name ?: "بیشتر", style = lt(12.sp, FontWeight.Medium, if (on) L.Primary else L.Ink), maxLines = 1)
                                    }
                                }
                                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccountPicker(
                            cards = cards, selected = account, onSelect = { account = it },
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            Modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).background(L.Chip)
                                .clickable { editingNote = !editingNote }.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("یادداشت", style = lt(13.sp, FontWeight.Medium, if (note.isBlank()) L.Ink else L.Primary)) }
                        Box(
                            Modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).background(L.Chip).padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                dateOverride?.let { com.example.utils.JalaliDate.fromTimestamp(it).formatShort() } ?: "امروز، ${timeOfDay(System.currentTimeMillis())}",
                                style = lt(13.sp, FontWeight.Medium, if (dateOverride != null) L.Primary else L.Ink),
                                modifier = Modifier.clickable(enabled = dateOverride != null) { dateOverride = null }
                            )
                        }
                    }
                    if (editingNote || note.isNotBlank()) {
                        Box(
                            Modifier.padding(top = 8.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp))
                                .background(L.Chip).padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (note.isEmpty()) Text("مثلاً: ناهار با همکارها", style = lt(13.sp, color = L.Muted))
                            BasicTextField(
                                value = note, onValueChange = { note = it.take(60) }, singleLine = true,
                                textStyle = lt(14.sp), cursorBrush = SolidColor(L.Primary),
                                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "یادداشت" }
                            )
                        }
                    }
                    if (persons.isNotEmpty()) {
                        Text(
                            if (type == EntryType.EXPENSE) "به کسی دادید؟" else "از کسی گرفتید؟",
                            style = lt(12.sp, FontWeight.SemiBold, L.Muted), modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                        )
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LChip("هیچ‌کس", personId == null) { personId = null }
                            persons.sortedBy { it.name }.forEach { p -> LChip(p.name, personId == p.id) { personId = p.id } }
                        }
                        val chosen = persons.firstOrNull { it.id == personId }
                        if (chosen != null) {
                            val bal = personBalances[chosen.id] ?: 0L
                            val out = type == EntryType.EXPENSE
                            Text(
                                when {
                                    out && bal < 0 -> "از بدهی شما به ${chosen.name} کم می‌شود (پس دادن)."
                                    out -> "به طلب شما از ${chosen.name} اضافه می‌شود (قرض دادن)."
                                    bal > 0 -> "از طلب شما از ${chosen.name} کم می‌شود (پس گرفتن)."
                                    else -> "به بدهی شما به ${chosen.name} اضافه می‌شود (قرض گرفتن)."
                                } + " در هزینه و درآمد ماه حساب نمی‌شود.",
                                style = lt(12.sp, color = L.PrimaryText, lineHeight = 20.sp), modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                } else {
                    Column(Modifier.padding(top = 10.dp).fillMaxWidth().clip(ItemShape).background(L.Chip).padding(horizontal = 14.dp, vertical = 4.dp)) {
                        TransferRow("از", cards, account) { account = it }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(L.Line))
                        TransferRow("به", cards.filter { it.bankName != account }, transferTo) { transferTo = it }
                    }
                }
            }

            // صفحه‌کلید عددی
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column {
                    listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("000", "0", "back")).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { k ->
                                Box(
                                    Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(16.dp)).clickable {
                                        amount = when {
                                            k == "back" -> amount.dropLast(1)
                                            amount.isEmpty() || amount == "0" -> if (k == "0" || k == "000") "" else k
                                            amount.length + k.length <= 12 -> amount + k
                                            else -> amount
                                        }
                                    }.semantics { contentDescription = if (k == "back") "پاک کردن" else fa(k) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (k == "back") Icon(Icons.AutoMirrored.Filled.Backspace, null, tint = L.Ink, modifier = Modifier.size(24.dp))
                                    else Text(fa(k), style = lt(26.sp))
                                }
                            }
                        }
                    }
                }
            }

            val transferOk = type != EntryType.TRANSFER || (account != null && transferTo != null && account != transferTo)
            val enabled = n > 0 && transferOk
            Box(
                Modifier.padding(top = 10.dp).fillMaxWidth().height(56.dp).clip(PillShape)
                    .background(if (enabled) L.Primary else L.PrimaryDisabled)
                    .clickable(enabled = enabled) {
                        val pid = personId
                        if (type == EntryType.TRANSFER) {
                            onTransfer(account!!, transferTo!!, n)
                        } else if (pid != null) {
                            onPersonMoney(pid, n, type == EntryType.EXPENSE, account, note)
                        } else {
                            val cat = category ?: typeCats.firstOrNull()?.name ?: if (type == EntryType.INCOME) "درآمد" else "سایر"
                            onSave(note.ifBlank { cat }, n, cat, type == EntryType.EXPENSE, account ?: "کیف پول", dateOverride)
                        }
                        onDismiss()
                    },
                contentAlignment = Alignment.Center
            ) {
                val who = persons.firstOrNull { it.id == personId }
                Text(if (who != null && type != EntryType.TRANSFER) "ثبت در حساب ${who.name}" else type.cta, style = lt(16.sp, FontWeight.Bold, Color.White))
            }
        }
    }
}

@Composable
private fun AccountPicker(cards: List<BankCard>, selected: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).background(L.Chip)
                .clickable(enabled = cards.isNotEmpty()) { open = true }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val card = cards.firstOrNull { it.bankName == selected }
            AccountSwatch(selected.orEmpty(), cards)
            Spacer(Modifier.width(8.dp))
            Text(card?.let { shortAccountLabel(it) } ?: "حسابی ندارید", style = lt(13.sp, FontWeight.Medium), modifier = Modifier.weight(1f), maxLines = 1)
            Icon(Icons.Default.KeyboardArrowDown, null, tint = L.Muted, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = L.Surface) {
            cards.forEach { c ->
                DropdownMenuItem(
                    text = { Text("${shortAccountLabel(c)} · ${millions(c.balance)}", style = lt(14.sp)) },
                    onClick = { onSelect(c.bankName); open = false }
                )
            }
        }
    }
}

@Composable
private fun TransferRow(label: String, cards: List<BankCard>, selected: String?, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().height(52.dp).clickable(enabled = cards.isNotEmpty()) { open = true },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = lt(12.sp, color = L.Muted), modifier = Modifier.width(28.dp))
            val card = cards.firstOrNull { it.bankName == selected }
            AccountSwatch(selected.orEmpty(), cards, color = if (card != null) L.Primary.copy(alpha = 0.85f) else L.Rail)
            Spacer(Modifier.width(10.dp))
            Text(card?.let { shortAccountLabel(it) } ?: "انتخاب حساب", style = lt(14.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f))
            if (card != null) Text(millions(card.balance), style = lt(12.sp, color = L.Muted))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = L.Surface) {
            cards.forEach { c ->
                DropdownMenuItem(text = { Text(shortAccountLabel(c), style = lt(14.sp)) }, onClick = { onSelect(c.bankName); open = false })
            }
        }
    }
}

/** پرتکرارترین هزینه‌ای که در همین ساعت از روز (±۱ ساعت) در ۶۰ روز اخیر ثبت شده */
private fun findSuggestion(transactions: List<Transaction>): Suggestion? {
    val now = Calendar.getInstance()
    val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val since = System.currentTimeMillis() - 60 * DAY_MS
    val c = Calendar.getInstance()
    val candidates = transactions.filter { tx ->
        if (!tx.isExpense || tx.isTransfer || tx.date < since) return@filter false
        c.timeInMillis = tx.date
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        kotlin.math.abs(m - nowMin) <= 60
    }
    val best = candidates.groupBy { Suggestion(it.title, it.category, it.bankName, it.amount) }
        .maxByOrNull { it.value.size } ?: return null
    return if (best.value.size >= 2) best.key else null
}
