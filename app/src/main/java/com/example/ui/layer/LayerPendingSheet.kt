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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BankCard
import com.example.data.BankSender
import com.example.data.Category
import com.example.data.PendingTransaction
import com.example.data.SmsApproval
import com.example.data.SmsIngest
import com.example.utils.JalaliDate

/**
 * شیت «تراکنش‌های تازه» که با باز شدن تراز (یا زدن روی نوتیفیکیشن) می‌آید. هر ردیف با یک «ثبت» در
 * تراز ثبت می‌شود (با دستهٔ پیشنهادی یا دسته‌ای که کاربر انتخاب کرده)، «✕» یعنی مال من نیست، و زدن
 * روی خود ردیف صفحهٔ کامل بررسی را برای ویرایش عنوان، حساب و مبلغ باز می‌کند.
 * موجودی حساب‌ها فقط با همین تأیید کاربر تغییر می‌کند.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LayerPendingSheet(
    pending: List<PendingTransaction>,
    categories: List<Category>,
    cards: List<BankCard>,
    senders: List<BankSender>,
    focusId: Int?,
    onApprove: (PendingTransaction, String) -> Unit,
    /** «ثبت همه»: همهٔ ردیف‌ها با دسته‌شان، یک‌جا (به ترتیب زمان ثبت می‌شوند). */
    onApproveAll: (List<Pair<PendingTransaction, String>>) -> Unit,
    onReject: (PendingTransaction) -> Unit,
    onEdit: (PendingTransaction) -> Unit,
    onLater: () -> Unit
) {
    // تراکنشی که از نوتیفیکیشنش آمده‌ایم اول؛ بقیه تازه‌ترین اول
    val items = remember(pending, focusId) {
        val sorted = pending.sortedByDescending { it.date }
        sorted.filter { it.id == focusId } + sorted.filter { it.id != focusId }
    }
    val chosen = remember { mutableStateMapOf<Int, String>() }
    var openPicker by remember { mutableStateOf<Int?>(null) }

    LayerSheet(
        title = "تراکنش‌های تازه (${fa(items.size)})",
        onDismiss = onLater,
        closeLabel = "بعداً",
        bottomBar = {
            if (items.size > 1) {
                BigButton("ثبت همه (${fa(items.size)})", Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    onApproveAll(items.map { p -> p to (chosen[p.id] ?: SmsApproval.guessCategory(p, categories)) })
                }
            }
        }
    ) {
        Text(
            "تراز این تراکنش‌ها را از پیامک‌های بانکی پیدا کرد. با «ثبت» به حساب‌ها اضافه می‌شوند؛ برای تغییر عنوان، حساب، مبلغ یا نوع تراکنش روی آن بزنید.",
            style = lt(12.sp, color = L.Muted, lineHeight = 21.sp)
        )
        items.forEach { p -> key(p.id) {
            val category = chosen[p.id] ?: SmsApproval.guessCategory(p, categories)
            val account = remember(p.id, cards, senders) { SmsIngest.accountFor(p, cards, senders) }
            PendingRow(
                p = p,
                account = account,
                category = category,
                highlighted = p.id == focusId,
                pickerOpen = openPicker == p.id,
                categoryOptions = categories.filter { it.isIncome == !p.isExpense }.map { it.name }
                    .ifEmpty { listOf(if (p.isExpense) "سایر" else "درآمد") },
                onTogglePicker = { openPicker = if (openPicker == p.id) null else p.id },
                onPick = { chosen[p.id] = it; openPicker = null },
                onApprove = { onApprove(p, category) },
                onReject = { onReject(p) },
                onEdit = { onEdit(p) }
            )
        } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingRow(
    p: PendingTransaction,
    account: String,
    category: String,
    highlighted: Boolean,
    pickerOpen: Boolean,
    categoryOptions: List<String>,
    onTogglePicker: () -> Unit,
    onPick: (String) -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onEdit: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(L.Bg.copy(alpha = 0.55f))
            .border(if (highlighted) 1.5.dp else 0.dp, if (highlighted) L.Primary else Color.Transparent, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().clickable(onClick = onEdit).semantics { contentDescription = "ویرایش این تراکنش" },
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    signedMoney(p.amount, p.isExpense) + " ${com.example.data.Money.label}",
                    style = lt(17.sp, FontWeight.ExtraBold, if (p.isExpense) L.RedText else L.GreenText),
                    modifier = Modifier.weight(1f)
                )
                Text(whenText(p.date), style = lt(12.sp, color = L.Muted))
            }
            val card = p.card.filter { it.isDigit() }.takeLast(4)
            Text(
                (if (p.isExpense) "برداشت" else "واریز") + " · " + account + (if (card.length == 4 && !account.contains(card)) " · کارت ${fa(card)}" else ""),
                style = lt(13.sp, FontWeight.SemiBold), maxLines = 1
            )
            Text(p.smsBody.replace('\n', ' '), style = lt(12.sp, color = L.Muted), maxLines = 1)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(L.Surface)
                    .border(1.dp, if (pickerOpen) L.Primary else L.Rail, RoundedCornerShape(12.dp))
                    .clickable(onClick = onTogglePicker).padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) { Text("دسته: $category ▾", style = lt(13.sp), maxLines = 1) }
            Box(
                Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(L.Primary).clickable(onClick = onApprove)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) { Text("ثبت", style = lt(14.sp, FontWeight.Bold, Color.White)) }
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(L.Surface).clickable(onClick = onReject)
                    .semantics { contentDescription = "مال من نیست" },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Close, null, tint = L.Muted, modifier = Modifier.size(18.dp)) }
        }
        if (pickerOpen) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                categoryOptions.forEach { name -> LChip(name, name == category) { onPick(name) } }
            }
        }
    }
}

/** «امروز ۱۴:۳۰»، «دیروز ۰۹:۱۰» یا «۱۲ آبان ۱۴:۳۰». */
private fun whenText(ts: Long): String {
    val d = JalaliDate.fromTimestamp(ts)
    val today = JalaliDate.today()
    val yesterday = JalaliDate.fromTimestamp(System.currentTimeMillis() - 24L * 3600 * 1000)
    val day = when (d) {
        today -> "امروز"
        yesterday -> "دیروز"
        else -> d.formatShort()
    }
    return "$day ${timeOfDay(ts)}"
}
