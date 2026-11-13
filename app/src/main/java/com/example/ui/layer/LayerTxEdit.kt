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
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import coil.compose.AsyncImage
import com.example.data.Attachments
import com.example.data.attachmentList
import com.example.data.tagList
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
    onDelete: () -> Unit,
    tagSuggestions: List<String> = emptyList()
) {
    val context = LocalContext.current
    var tags by remember { mutableStateOf(tx.tagList) }
    var photos by remember { mutableStateOf(tx.attachmentList) }
    var added by remember { mutableStateOf(listOf<String>()) }
    var newTag by remember { mutableStateOf("") }
    var addingTag by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<String?>(null) }
    var cameraFile by remember { mutableStateOf<java.io.File?>(null) }
    fun attach(uri: Uri?) {
        if (uri == null) return
        Attachments.save(context, uri)?.let { photos = photos + it; added = added + it }
    }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { attach(it) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null) { attach(Uri.fromFile(f)); f.delete() }
        cameraFile = null
    }
    val close = {
        // photos picked but never saved don't linger on disk
        added.forEach { Attachments.delete(context, it) }
        onDismiss()
    }
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
    val dirty = amount != tx.amount || category != tx.category || account != tx.bankName || title != tx.title || date != tx.date ||
        tags != tx.tagList || photos != tx.attachmentList
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
        onDismiss = close,
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
            BigButton(if (dirty) "ذخیره تغییرات" else "تغییری اعمال نشده است", enabled = canSave) {
                onSave(
                    tx.copy(
                        amount = amount, category = category, bankName = account, title = title.trim(), date = date,
                        tags = tags.joinToString(","), attachments = photos.joinToString(",")
                    )
                )
                (tx.attachmentList - photos.toSet()).forEach { Attachments.delete(context, it) }
                added = emptyList()
                saved = true
            }
            if (askDelete) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(L.RedSoft).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        when {
                            tx.isTransfer -> "این انتقال حذف شود؟ هر دو طرف آن پاک می‌شوند و موجودی هر دو حساب به حالت قبل برمی‌گردد."
                            tx.loanId != null -> "این پرداخت قسط حذف شود؟ ${money(tx.amount)} ${com.example.data.Money.label} به موجودی ${tx.bankName} برمی‌گردد و از مبلغ پرداخت‌شدهٔ وام نیز کسر می‌شود."
                            else -> "این تراکنش حذف شود؟ ${money(tx.amount)} ${com.example.data.Money.label} ${if (tx.isExpense) "به موجودی ${tx.bankName} برمی‌گردد" else "از موجودی ${tx.bankName} کم می‌شود"}."
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
                Text("${com.example.data.Money.label}", style = lt(15.sp), modifier = Modifier.padding(bottom = 8.dp))
            }
            Text(title, style = lt(15.sp, FontWeight.SemiBold), maxLines = 1)
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(L.Line))

        val locked = tx.isTransfer
        ChangeRow("مبلغ", { Text("${money(amount)} ${com.example.data.Money.label}", style = lt(14.sp, FontWeight.SemiBold)) }, onAction = if (locked) null else {
            { editing = if (editing == Editing.AMOUNT) Editing.NONE else Editing.AMOUNT }
        })
        if (editing == Editing.AMOUNT) {
            LField(amountText, { amountText = groupedInput(it) }, "مبلغ جدید (${com.example.data.Money.label})", keyboardOptions = moneyKeyboard)
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
                AddCategoryChip(!tx.isExpense)
            }
        }
        WatchNew(categories, { it.id }) { category = it.name }
        WatchNew(cards, { it.id }) { account = it.bankName }

        ChangeRow("حساب", {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountSwatch(account, cards, 24.dp, 16.dp)
                Text(cards.firstOrNull { it.bankName == account }?.let { shortAccountLabel(it) } ?: account, style = lt(14.sp, FontWeight.SemiBold))
            }
        }, onAction = if (locked) null else { { editing = if (editing == Editing.ACCOUNT) Editing.NONE else Editing.ACCOUNT } })
        if (editing == Editing.ACCOUNT) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                cards.forEach { c -> LChip(c.bankName, c.bankName == account) { account = c.bankName; editing = Editing.NONE } }
                AddAccountChip()
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

        // برچسب‌ها
        Text("برچسب‌ها", style = lt(13.sp, color = L.Muted))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            tags.forEach { t ->
                Row(
                    Modifier.height(32.dp).clip(PillShape).background(L.PrimarySoft).clickable { tags = tags - t }.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("# $t", style = lt(12.sp, FontWeight.Bold, L.PrimaryText))
                    Icon(Icons.Default.Close, "حذف برچسب $t", tint = L.PrimaryText, modifier = Modifier.padding(start = 4.dp).size(14.dp))
                }
            }
            (tagSuggestions - tags.toSet()).take(6).forEach { t ->
                Box(
                    Modifier.height(32.dp).clip(PillShape).background(L.Chip).clickable { tags = tags + t }.padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) { Text("+ $t", style = lt(12.sp, color = L.Muted)) }
            }
            if (addingTag) {
                Box(
                    Modifier.height(32.dp).width(140.dp).clip(PillShape).background(L.Surface).border(1.dp, L.Primary, PillShape).padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        newTag, { v -> newTag = v.replace(",", "").take(24) }, singleLine = true,
                        textStyle = lt(12.sp), cursorBrush = SolidColor(L.Primary),
                        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                            val t = newTag.trim()
                            if (t.isNotEmpty() && t !in tags) tags = tags + t
                            newTag = ""; addingTag = false
                        })
                    )
                }
            } else {
                Box(
                    Modifier.height(32.dp).clip(PillShape).border(1.dp, L.Line, PillShape).clickable { addingTag = true }.padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) { Text("+ برچسب جدید", style = lt(12.sp, FontWeight.SemiBold, L.Primary)) }
            }
        }

        // عکس‌ها (فاکتور، کارت گارانتی)
        Text("تصاویر", style = lt(13.sp, color = L.Muted))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            photos.forEach { name ->
                Box(Modifier.size(76.dp).clip(RoundedCornerShape(14.dp)).background(L.Chip).clickable { viewing = name }) {
                    AsyncImage(
                        model = Attachments.file(context, name), contentDescription = "تصویر پیوست",
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        Modifier.align(Alignment.TopStart).padding(4.dp).size(22.dp).clip(PillShape).background(Color(0x99000000))
                            .clickable { photos = photos - name },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Close, "حذف تصویر", tint = Color.White, modifier = Modifier.size(14.dp)) }
                }
            }
            if (photos.size < 6) {
                PhotoAddTile("دوربین") {
                    val (f, uri) = Attachments.cameraTarget(context)
                    cameraFile = f
                    runCatching { cameraLauncher.launch(uri) }
                }
                PhotoAddTile("گالری") { runCatching { pickLauncher.launch("image/*") } }
            }
        }

        if (locked) {
            Text("انتقال بین حساب‌ها قابل ویرایش نیست؛ در صورت اشتباه، آن را حذف و مجدداً ثبت کنید.", style = lt(12.sp, color = L.Muted, lineHeight = 20.sp))
        }
    }
    viewing?.let { name ->
        Dialog(onDismissRequest = { viewing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { viewing = null }, contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = Attachments.file(context, name), contentDescription = "تصویر پیوست",
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth()
                )
                Text("برای بستن لمس کنید", style = lt(12.sp, color = Color(0xB3FFFFFF)), modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp))
            }
        }
    }
    if (showDatePicker) {
        JalaliDatePickerSheet(initial = date, onDismiss = { showDatePicker = false }, onPick = { date = it })
    }
}

@Composable
private fun PhotoAddTile(label: String, onClick: () -> Unit) {
    Column(
        Modifier.size(76.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, L.Line, RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Text("+", style = lt(20.sp, FontWeight.Light, L.Primary))
        Text(label, style = lt(11.sp, color = L.Muted))
    }
}
