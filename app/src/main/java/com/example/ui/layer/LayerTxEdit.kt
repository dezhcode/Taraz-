package com.example.ui.layer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BankCard
import com.example.data.Category
import com.example.data.Transaction
import com.example.data.isTransfer
import com.example.utils.JalaliDate

private enum class Editing { NONE, AMOUNT, CATEGORY, ACCOUNT, TITLE }

/**
 * جزئیات و ویرایش تراکنش — شیت طرح نهایی: مبلغ درشت، ردیف‌های قابل تغییر،
 * ذخیره فقط وقتی چیزی عوض شده، و حذف با تأیید دو مرحله‌ای.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LayerTxEditSheet(
    tx: Transaction,
    cards: List<BankCard>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit,
    onDelete: () -> Unit
) {
    var amountText by remember { mutableStateOf(money(tx.amount)) }
    var category by remember { mutableStateOf(tx.category) }
    var account by remember { mutableStateOf(tx.bankName) }
    var title by remember { mutableStateOf(tx.title) }
    var date by remember { mutableStateOf(tx.date) }
    var showDatePicker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(Editing.NONE) }
    var askDelete by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    val amount = parseAmount(amountText)
    val dirty = amount != tx.amount || category != tx.category || account != tx.bankName || title != tx.title || date != tx.date
    val canSave = dirty && amount > 0 && title.isNotBlank()
    val typeLabel = when {
        tx.isTransfer -> "انتقال"
        tx.isExpense -> "هزینه"
        else -> "درآمد"
    }
    val catList = categories.filter { it.isIncome == !tx.isExpense }.map { it.name }
        .let { if (category.isNotBlank() && category !in it) it + category else it }

    LayerSheet(
        title = "جزئیات تراکنش",
        onDismiss = onDismiss,
        closeLabel = "بستن",
        topGap = 110.dp,
        bottomBar = {
            if (saved) {
                Row(
                    Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(L.GreenSoft),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Check, null, tint = L.GreenText, modifier = Modifier.size(16.dp))
                    Text("  تغییرات ذخیره شد", style = lt(13.sp, FontWeight.Bold, L.GreenText))
                }
            }
            BigButton(if (dirty) "ذخیره تغییرات" else "تغییری نداده‌اید", enabled = canSave) {
                onSave(tx.copy(amount = amount, category = category, bankName = account, title = title.trim(), date = date))
                saved = true
            }
            if (askDelete) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(L.RedSoft).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        when {
                            tx.isTransfer -> "این انتقال حذف شود؟ هر دو طرف آن پاک می‌شوند و موجودی هر دو حساب به حالت قبل برمی‌گردد."
                            tx.loanId != null -> "این پرداخت قسط حذف شود؟ ${money(tx.amount)} تومان به موجودی ${tx.bankName} برمی‌گردد و از پرداخت‌شدهٔ وام هم کم می‌شود."
                            else -> "این تراکنش حذف شود؟ ${money(tx.amount)} تومان ${if (tx.isExpense) "به موجودی ${tx.bankName} برمی‌گردد" else "از موجودی ${tx.bankName} کم می‌شود"}."
                        },
                        style = lt(13.sp, color = L.RedInk, lineHeight = 22.sp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(L.RedText).clickable(onClick = onDelete), contentAlignment = Alignment.Center) {
                            Text("بله، حذف شود", style = lt(14.sp, FontWeight.Bold, Color.White))
                        }
                        Box(Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(L.Surface).clickable { askDelete = false }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                            Text("انصراف", style = lt(14.sp, FontWeight.SemiBold))
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).clickable { askDelete = true }, contentAlignment = Alignment.Center) {
                    Text("حذف تراکنش", style = lt(14.sp, FontWeight.SemiBold, L.RedText))
                }
            }
        }
    ) {
        // سرنوشت اصلی
        Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.height(24.dp).clip(PillShape).background(L.Chip).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                Text(typeLabel, style = lt(12.sp, FontWeight.Bold))
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(money(amount), style = lt(42.sp, FontWeight.Light, letterSpacing = (-1.5).sp))
                Text("تومان", style = lt(15.sp), modifier = Modifier.padding(bottom = 8.dp))
            }
            Text(title, style = lt(15.sp, FontWeight.SemiBold), maxLines = 1)
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(L.Line))

        val locked = tx.isTransfer
        ChangeRow("مبلغ", { Text("${money(amount)} تومان", style = lt(14.sp, FontWeight.SemiBold)) }, onAction = if (locked) null else {
            { editing = if (editing == Editing.AMOUNT) Editing.NONE else Editing.AMOUNT }
        })
        if (editing == Editing.AMOUNT) {
            LField(amountText, { amountText = groupedInput(it) }, "مبلغ جدید (تومان)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }

        ChangeRow("دسته", { Text(category.ifBlank { "—" }, style = lt(14.sp, FontWeight.SemiBold)) },
            actionLabel = if (editing == Editing.CATEGORY) "بستن" else "تغییر",
            onAction = if (locked) null else { { editing = if (editing == Editing.CATEGORY) Editing.NONE else Editing.CATEGORY } })
        if (editing == Editing.CATEGORY) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                catList.forEach { c ->
                    val on = c == category
                    Box(
                        Modifier.height(36.dp).clip(RoundedCornerShape(10.dp)).background(if (on) L.Primary else L.Chip)
                            .clickable { category = c; editing = Editing.NONE }.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(c, style = lt(13.sp, FontWeight.SemiBold, if (on) Color.White else L.Ink)) }
                }
            }
        }

        ChangeRow("حساب", {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountSwatch(account, cards, 24.dp, 16.dp)
                Text(cards.firstOrNull { it.bankName == account }?.let { shortAccountLabel(it) } ?: account, style = lt(14.sp, FontWeight.SemiBold))
            }
        }, onAction = if (locked) null else { { editing = if (editing == Editing.ACCOUNT) Editing.NONE else Editing.ACCOUNT } })
        if (editing == Editing.ACCOUNT) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                cards.forEach { c -> LChip(c.bankName, c.bankName == account) { account = c.bankName; editing = Editing.NONE } }
            }
        }

        ChangeRow("زمان", {
            Text("${weekdayName(date)} ${JalaliDate.fromTimestamp(date).formatShort()}، ${timeOfDay(date)}", style = lt(14.sp, FontWeight.SemiBold))
        }, onAction = if (locked) null else { { showDatePicker = true } })

        ChangeRow("توضیح", { Text(title.ifBlank { "—" }, style = lt(14.sp)) },
            onAction = if (locked) null else { { editing = if (editing == Editing.TITLE) Editing.NONE else Editing.TITLE } })
        if (editing == Editing.TITLE) {
            LField(title, { title = it }, "عنوان تراکنش")
        }

        if (locked) {
            Text("انتقال بین حساب‌ها قابل ویرایش نیست؛ اگر اشتباه شده حذفش کنید و دوباره ثبت کنید.", style = lt(12.sp, color = L.Muted, lineHeight = 20.sp))
        }
    }
    if (showDatePicker) {
        JalaliDatePickerSheet(initial = date, onDismiss = { showDatePicker = false }, onPick = { date = it })
    }
}
