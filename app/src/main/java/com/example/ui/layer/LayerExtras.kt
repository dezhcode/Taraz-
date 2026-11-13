package com.example.ui.layer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BankCard
import com.example.data.Category
import com.example.data.RecurringTransaction
import com.example.data.Transaction
import com.example.data.countsAsExpense
import com.example.utils.JalaliDate

// ───────────────────────── بودجهٔ دسته‌ها ─────────────────────────

/** خرج هر دسته در ماه جاری (فقط هزینه‌های واقعی، بدون انتقال) */
fun spentThisMonthByCategory(transactions: List<Transaction>, now: Long = System.currentTimeMillis()): Map<String, Long> {
    val (start, end) = JalaliDate.monthRange(now)
    return transactions.filter { it.countsAsExpense && it.date >= start && it.date < end }
        .groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amount } }
}

/** دسته‌هایی که ۸۰٪ بودجه‌شان یا بیشتر خرج شده، پرمصرف‌ترین اول */
fun budgetAlerts(categories: List<Category>, transactions: List<Transaction>): List<Triple<Category, Long, Int>> {
    val spent = spentThisMonthByCategory(transactions)
    return categories.filter { it.monthlyBudget > 0 && !it.isIncome }
        .map { c -> val s = spent[c.name] ?: 0L; Triple(c, s, (s * 100 / c.monthlyBudget).toInt()) }
        .filter { it.third >= 80 }
        .sortedByDescending { it.third }
}

@Composable
fun CategoryBudgetSheet(category: Category, spent: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by remember { mutableStateOf(if (category.monthlyBudget > 0) money(category.monthlyBudget) else "") }
    LayerSheet(
        title = "بودجهٔ ماهانهٔ «${category.name}»",
        onDismiss = onDismiss,
        topGap = 220.dp,
        bottomBar = {
            BigButton("ذخیره") { onSave(parseAmount(text)); onDismiss() }
            if (category.monthlyBudget > 0) SoftBigButton("برداشتن بودجه") { onSave(0); onDismiss() }
        }
    ) {
        LField(
            value = text, onChange = { text = groupedInput(it) }, label = "سقف هزینهٔ ماهانه (${com.example.data.Money.label})", placeholder = "۰",
            keyboardOptions = moneyKeyboard,
            hint = "هزینهٔ این ماه تاکنون ${money(spent)} ${com.example.data.Money.label} است. با رسیدن به ۸۰٪ سقف، در صفحهٔ خانه هشدار نمایش داده می‌شود."
        )
        Spacer(Modifier.height(4.dp))
    }
}

// ───────────────────────── تراکنش‌های تکراری ─────────────────────────

/** خرجی که در چند ماه گذشته تقریباً با همان مبلغ هر ماه تکرار شده (اشتراک، اینترنت، اجاره…) */
data class RecurringCandidate(val title: String, val amount: Long, val category: String, val bankName: String, val day: Int, val months: Int)

fun detectRecurringCandidates(
    transactions: List<Transaction>,
    existing: List<RecurringTransaction>,
    now: Long = System.currentTimeMillis()
): List<RecurringCandidate> {
    val since = shiftJalaliMonth(now, -6)
    val known = existing.map { it.title.trim() }.toSet()
    fun norm(t: String) = t.filter { !it.isDigit() && it !in "۰۱۲۳۴۵۶۷۸۹" }.replace(Regex("\\s+"), " ").trim().lowercase()
    return transactions
        .filter { it.countsAsExpense && it.date >= since && norm(it.title).length >= 2 }
        .groupBy { norm(it.title) }
        .mapNotNull { (_, list) ->
            val sorted = list.map { it.amount }.sorted()
            val median = sorted[sorted.size / 2]
            // همان مبلغ با ۱۰٪ اختلاف
            val similar = list.filter { kotlin.math.abs(it.amount - median) <= median / 10 }
            val byMonth = similar.groupBy { JalaliDate.fromTimestamp(it.date).monthKey() }
            // دست‌کم سه ماه، و ماهی یکی دو بار (خرید روزانه حساب نمی‌شود)
            if (byMonth.size < 3 || byMonth.values.any { it.size > 2 }) return@mapNotNull null
            val title = similar.maxByOrNull { it.date }!!.title.trim()
            if (title in known) return@mapNotNull null
            val days = similar.map { JalaliDate.fromTimestamp(it.date).day }.sorted()
            RecurringCandidate(
                title = title,
                amount = median,
                category = similar.groupingBy { it.category }.eachCount().maxByOrNull { it.value }!!.key,
                bankName = similar.groupingBy { it.bankName }.eachCount().maxByOrNull { it.value }!!.key,
                day = days[days.size / 2],
                months = byMonth.size
            )
        }
        .sortedByDescending { it.amount }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LayerRecurringScreen(
    items: List<RecurringTransaction>,
    cards: List<BankCard>,
    categories: List<Category>,
    transactions: List<Transaction> = emptyList(),
    onBack: () -> Unit,
    onAdd: (title: String, amount: Long, category: String, isExpense: Boolean, bank: String, day: Int) -> Unit,
    onToggle: (RecurringTransaction, Boolean) -> Unit,
    onDelete: (RecurringTransaction) -> Unit
) {
    var adding by remember { mutableStateOf(false) }
    LayerScreen(
        title = "تراکنش‌های تکراری",
        subtitle = if (items.isEmpty()) "اجاره، حقوق، اشتراک‌ها" else "${fa(items.count { it.enabled })} مورد فعال · هر ماه خودکار ثبت می‌شوند",
        onBack = onBack,
        bottomBar = { BigButton("افزودن تراکنش تکراری", enabled = cards.isNotEmpty()) { adding = true } }
    ) {
        if (cards.isEmpty()) {
            Callout("ابتدا یک حساب ایجاد کنید", "تراکنش تکراری روی یکی از حساب‌ها ثبت می‌شود.", L.AmberSoft, L.AmberInk, Modifier.padding(horizontal = 20.dp))
        }
        val candidates = remember(transactions, items) { detectRecurringCandidates(transactions, items) }
        if (candidates.isNotEmpty()) {
            SectionTitle("موارد تکرارشونده در ماه‌های اخیر")
            LayerCard(radius = 20.dp, padding = 0.dp) {
                candidates.take(6).forEachIndexed { i, c ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(c.title, style = lt(14.sp, FontWeight.SemiBold), maxLines = 1)
                            Text("${fa(c.months)} ماه اخیر · حدود روز ${fa(c.day)} · ${c.bankName}", style = lt(12.sp, color = L.Muted), maxLines = 1)
                        }
                        Text(money(c.amount), style = lt(14.sp, FontWeight.SemiBold))
                        SoftButton("افزودن", height = 34.dp) {
                            onAdd(c.title, c.amount, c.category, true, c.bankName, c.day)
                        }
                    }
                    if (i < minOf(candidates.size, 6) - 1) Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(L.Line))
                }
            }
            Text(
                "اشتراک‌های غیرضروری را لغو کنید؛ مجموع این موارد ماهانه ${money(candidates.sumOf { it.amount })} ${com.example.data.Money.label} است.",
                style = lt(12.sp, color = L.Muted, lineHeight = 21.sp), modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
        if (items.isEmpty()) {
            Text(
                "مبالغ تکرارشوندهٔ ماهانه را یک بار ثبت کنید؛ تراز در موعد مقرر آن‌ها را به‌طور خودکار ثبت می‌کند.",
                style = lt(13.sp, color = L.Muted, lineHeight = 23.sp), modifier = Modifier.padding(horizontal = 24.dp)
            )
        } else {
            LayerCard(radius = 20.dp, padding = 0.dp) {
                items.forEachIndexed { i, r ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(r.title, style = lt(14.sp, FontWeight.SemiBold, if (r.enabled) L.Ink else L.Muted), maxLines = 1)
                            Text("روز ${fa(r.dayOfMonth)} هر ماه · ${r.category} · ${r.bankName}", style = lt(12.sp, color = L.Muted), maxLines = 1)
                        }
                        Text(signedMoney(r.amount, r.isExpense), style = lt(14.sp, FontWeight.SemiBold, if (r.isExpense) L.RedText else L.Green))
                        LSwitch(r.enabled, "فعال بودن ${r.title}") { onToggle(r, it) }
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).clickable { onDelete(r) }.semantics { contentDescription = "حذف ${r.title}" },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Delete, null, tint = L.Faint, modifier = Modifier.size(18.dp)) }
                    }
                    if (i < items.size - 1) Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(L.Line))
                }
            }
        }
    }

    if (adding) {
        var isExpense by remember { mutableStateOf(true) }
        var title by remember { mutableStateOf("") }
        var amountText by remember { mutableStateOf("") }
        var dayText by remember { mutableStateOf(fa(JalaliDate.today().day)) }
        var bank by remember { mutableStateOf(cards.firstOrNull()?.bankName.orEmpty()) }
        val cats = categories.filter { it.isIncome == !isExpense }.sortedBy { it.sortOrder }
        var category by remember(isExpense) { mutableStateOf(cats.firstOrNull()?.name ?: if (isExpense) "سایر" else "سایر درآمدها") }
        val day = parseInt(dayText)
        val amount = parseAmount(amountText)
        val ok = title.isNotBlank() && amount > 0 && day in 1..31 && bank.isNotBlank()
        LayerSheet(
            title = "تراکنش تکراری جدید",
            onDismiss = { adding = false },
            topGap = 60.dp,
            bottomBar = {
                BigButton("ذخیره", enabled = ok) {
                    onAdd(title, amount, category, isExpense, bank, day)
                    adding = false
                }
            }
        ) {
            LSegmented(listOf("هزینه", "درآمد"), if (isExpense) 0 else 1, { isExpense = it == 0 })
            LField(value = title, onChange = { title = it }, label = "عنوان", placeholder = if (isExpense) "مثلاً اجارهٔ خانه" else "مثلاً حقوق")
            LField(
                value = amountText, onChange = { amountText = groupedInput(it) }, label = "مبلغ (${com.example.data.Money.label})", placeholder = "۰",
                keyboardOptions = moneyKeyboard
            )
            LField(
                value = dayText, onChange = { dayText = it.filter { c -> c.isDigit() }.take(2) }, label = "روز ماه (۱ تا ۳۱)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                hint = "در ماه‌های کوتاه‌تر، آخرین روز ماه ثبت می‌شود."
            )
            Text("حساب", style = lt(13.sp, FontWeight.SemiBold))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cards.forEach { c -> LChip(c.bankName, bank == c.bankName) { bank = c.bankName } }
                AddAccountChip()
            }
            WatchNew(cards, { it.id }) { bank = it.bankName }
            WatchNew(categories, { it.id }) { c -> if (c.isIncome == !isExpense) category = c.name }
            Text("دسته", style = lt(13.sp, FontWeight.SemiBold))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cats.forEach { c -> LChip(c.name, category == c.name) { category = c.name } }
                AddCategoryChip(!isExpense)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

// ───────────────────────── پشتیبان و خروجی ─────────────────────────

@Composable
fun LayerBackupScreen(
    message: Pair<Boolean, String>?,
    busy: Boolean,
    onBack: () -> Unit,
    onCreate: (password: String) -> Unit,
    onRestore: (password: String) -> Unit,
    onExportCsv: () -> Unit,
    auto: com.example.data.AutoBackup.State? = null,
    onAutoEnabled: (Boolean) -> Unit = {},
    onAutoPeriod: (Int) -> Unit = {},
    onPickFolder: () -> Unit = {},
    onAutoPassword: (String) -> Unit = {},
    onAutoNow: () -> Unit = {},
    driveInstalled: Boolean = true,
    /** اتصال به درایو: ساختن فایل پشتیبان در درایو. رمز تازه وقتی پشتیبان خودکار هنوز رمز ندارد، وگرنه null */
    onDriveConnect: (password: String?) -> Unit = {},
    onDriveSaveNow: () -> Unit = {},
    onDriveRestore: (password: String) -> Unit = {},
    /** گوشی تازه: فایل پشتیبان قبلی را از درایو برمی‌گزیند، بازمی‌گرداند و وصلش می‌کند */
    onDriveOpenExisting: (password: String) -> Unit = {},
    onDriveDisconnect: () -> Unit = {},
    /** روش اول: حساب گوگل و API درایو */
    driveApi: com.example.data.DriveApi.Status? = null,
    drivePlay: Boolean = true,
    onDriveApiConnect: (password: String?) -> Unit = {},
    onDriveApiBackup: () -> Unit = {},
    onDriveApiRestore: () -> Unit = {},
    onDriveApiSignOut: () -> Unit = {},
    /** فهرست پشتیبان‌های درایو برای بازگردانی (null یعنی بسته) */
    driveFiles: List<com.example.data.DriveApi.RemoteFile>? = null,
    onDriveFilesDismiss: () -> Unit = {},
    onDriveFilePick: (com.example.data.DriveApi.RemoteFile, password: String) -> Unit = { _, _ -> }
) {
    var pickedFile by remember { mutableStateOf<com.example.data.DriveApi.RemoteFile?>(null) }
    var mode by remember { mutableStateOf<String?>(null) } // "create" | "restore" | "auto" | "drive" | "drive_restore" | "drive_existing"
    // رمز از کلید «پشتیبان خودکار» خواسته شد: بعد از ذخیرهٔ رمز روشن شود. تغییر رمز به‌تنهایی روشنش نمی‌کند.
    var enableAfterPassword by remember { mutableStateOf(false) }
    LayerScreen(title = "پشتیبان و خروجی", subtitle = "نگهداری نسخهٔ پشتیبان خارج از دستگاه", onBack = onBack) {
        message?.let { (ok, text) ->
            Callout(text, null, if (ok) L.GreenSoft else L.RedSoft, if (ok) L.GreenText else L.RedText, Modifier.padding(horizontal = 20.dp))
        }
        if (auto != null) {
            DriveCard(
                auto = auto, busy = busy, installed = driveInstalled,
                api = driveApi, play = drivePlay,
                onApiConnect = { if (auto.hasPassword) onDriveApiConnect(null) else mode = "api_connect" },
                onApiBackup = onDriveApiBackup, onApiRestore = onDriveApiRestore, onApiSignOut = onDriveApiSignOut,
                onConnect = { if (auto.hasPassword) onDriveConnect(null) else mode = "drive" },
                onSaveNow = onDriveSaveNow,
                onRestore = { mode = "drive_restore" },
                onOpenExisting = { mode = "drive_existing" },
                onDisconnect = onDriveDisconnect
            )
            LayerCard(radius = 20.dp, padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("پشتیبان خودکار", style = lt(15.sp, FontWeight.Bold))
                        Text("تهیهٔ خودکار فایل پشتیبان رمزگذاری‌شده", style = lt(12.sp, color = L.Muted))
                    }
                    LSwitch(auto.enabled, "پشتیبان خودکار") { on ->
                        when {
                            !on -> onAutoEnabled(false)
                            auto.folderLabel == null && auto.driveName == null && driveApi?.connected != true -> onPickFolder()
                            !auto.hasPassword -> { enableAfterPassword = true; mode = "auto" }
                            else -> onAutoEnabled(true)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                LSegmented(listOf("هفتگی", "ماهانه"), if (auto.periodDays >= 28) 1 else 0, { onAutoPeriod(if (it == 1) 30 else 7) })
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("پوشهٔ ذخیره", style = lt(12.sp, color = L.Muted))
                        Text(auto.folderLabel ?: "انتخاب نشده", style = lt(14.sp, FontWeight.SemiBold), maxLines = 1)
                    }
                    SoftButton(if (auto.folderLabel == null) "انتخاب" else "تغییر", height = 36.dp, onClick = onPickFolder)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("رمز فایل‌ها", style = lt(12.sp, color = L.Muted))
                        Text(if (auto.hasPassword) "تعیین شده" else "تعیین نشده", style = lt(14.sp, FontWeight.SemiBold))
                    }
                    SoftButton(if (auto.hasPassword) "تغییر" else "تعیین", height = 36.dp) { enableAfterPassword = false; mode = "auto" }
                }
                Spacer(Modifier.height(10.dp))
                when {
                    auto.lastError != null -> CardNote(auto.lastError, null, L.RedSoft, L.RedText)
                    auto.lastAt > 0 -> CardNote(
                        "آخرین پشتیبان: ${weekdayName(auto.lastAt)} ${JalaliDate.fromTimestamp(auto.lastAt).formatShort()}، ${timeOfDay(auto.lastAt)} · ${fa(auto.lastSize / 1024)} کیلوبایت",
                        null, L.GreenSoft, L.GreenText
                    )
                }
                if ((auto.folderLabel != null || auto.driveName != null || driveApi?.connected == true) && auto.hasPassword) {
                    Spacer(Modifier.height(8.dp))
                    SoftButton(if (busy) "در حال ساخت…" else "تهیهٔ پشتیبان اکنون", height = 36.dp) { if (!busy) onAutoNow() }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "رمز یک بار دریافت و به‌صورت رمزگذاری‌شده روی دستگاه نگهداری می‌شود. در پوشهٔ گوشی پنج نسخهٔ آخر حفظ و نسخه‌های قدیمی‌تر حذف می‌شوند؛ اگر گوگل درایو وصل باشد، پشتیبان در درایو هم ذخیره می‌شود.",
                    style = lt(11.sp, color = L.Muted, lineHeight = 19.sp)
                )
            }
        }

        LayerCard(radius = 20.dp, padding = 0.dp) {
            // فاصلهٔ داخلی از دو طرف تا متن‌ها به لبهٔ کادر نچسبند (مثل گروه‌های تنظیمات)
            Column(Modifier.padding(horizontal = 16.dp)) {
                LRow("ساخت فایل پشتیبان", "همهٔ حساب‌ها، تراکنش‌ها، اقساط و اهداف، با رمز", onClick = { if (!busy) mode = "create" },
                    trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = L.Chevron, modifier = Modifier.size(18.dp)) })
                LRow("بازگردانی از پشتیبان", "داده‌های فعلی با محتوای فایل جایگزین می‌شود", onClick = { if (!busy) mode = "restore" },
                    trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = L.Chevron, modifier = Modifier.size(18.dp)) })
                LRow("خروجی اکسل (CSV)", "فهرست همهٔ تراکنش‌ها برای اکسل یا گوگل‌شیت", divider = false, onClick = { if (!busy) onExportCsv() },
                    trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = L.Chevron, modifier = Modifier.size(18.dp)) })
            }
        }
        Text(
            "فایل پشتیبان رمزگذاری می‌شود و بدون رمز باز نمی‌شود؛ رمز را در جای امنی یادداشت کنید؛ تراز آن را ذخیره نمی‌کند. " +
                "فایل را در درایو، تلگرام یا هر جای امن دیگری نگه دارید تا در صورت مفقود شدن گوشی، اطلاعات از دست نرود.",
            style = lt(12.sp, color = L.Muted, lineHeight = 21.sp), modifier = Modifier.padding(horizontal = 24.dp)
        )
        if (busy) Text("در حال انجام…", style = lt(13.sp, FontWeight.SemiBold, L.PrimaryText), modifier = Modifier.padding(horizontal = 24.dp))
    }

    driveFiles?.let { files ->
        LayerSheet(title = "پشتیبان‌های گوگل درایو", onDismiss = onDriveFilesDismiss, topGap = 160.dp, closeLabel = "بستن") {
            if (files.isEmpty()) {
                Text("هنوز پشتیبانی در پوشهٔ Taraz درایو شما نیست.", style = lt(13.sp, color = L.Muted, lineHeight = 22.sp))
            }
            files.forEachIndexed { i, f ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { pickedFile = f; onDriveFilesDismiss(); mode = "api_restore" }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(if (i == 0) L.GreenSoft else L.Chip), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CloudDone, null, tint = if (i == 0) L.GreenText else L.Muted, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            (if (f.modified > 0) "${weekdayName(f.modified)} ${JalaliDate.fromTimestamp(f.modified).formatShort()} ${fa(JalaliDate.fromTimestamp(f.modified).year)}، ${timeOfDay(f.modified)}" else f.name) +
                                if (i == 0) " · تازه‌ترین" else "",
                            style = lt(14.sp, FontWeight.SemiBold)
                        )
                        Text("${fa(f.size / 1024)} کیلوبایت", style = lt(12.sp, color = L.Muted))
                    }
                }
                if (i < files.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(L.Line))
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    mode?.let { m ->
        var pass by remember(m) { mutableStateOf("") }
        var pass2 by remember(m) { mutableStateOf("") }
        val creating = m == "create" || m == "auto" || m == "drive" || m == "api_connect"
        val ok = if (creating) pass.length >= 6 && pass == pass2 else pass.isNotEmpty()
        LayerSheet(
            title = if (creating) "رمز فایل پشتیبان" else "رمز فایل پشتیبان را وارد کنید",
            onDismiss = { mode = null },
            topGap = 200.dp,
            bottomBar = {
                BigButton(
                    when (m) {
                        "auto" -> "ذخیرهٔ رمز"
                        "create" -> "ساخت و ذخیرهٔ فایل"
                        "drive" -> "ادامه و انتخاب جای فایل در درایو"
                        "api_connect" -> "ادامه و ورود با حساب گوگل"
                        "api_restore" -> "بازگردانی این نسخه"
                        "drive_restore" -> "بازگردانی از درایو"
                        "drive_existing" -> "انتخاب فایل از درایو"
                        else -> "انتخاب فایل و بازگردانی"
                    },
                    enabled = ok
                ) {
                    when (m) {
                        "auto" -> {
                            onAutoPassword(pass)
                            if (enableAfterPassword && auto?.folderLabel != null) onAutoEnabled(true)
                            enableAfterPassword = false
                        }
                        "create" -> onCreate(pass)
                        "drive" -> onDriveConnect(pass)
                        "api_connect" -> onDriveApiConnect(pass)
                        "api_restore" -> pickedFile?.let { onDriveFilePick(it, pass) }
                        "drive_restore" -> onDriveRestore(pass)
                        "drive_existing" -> onDriveOpenExisting(pass)
                        else -> onRestore(pass)
                    }
                    mode = null
                }
            }
        ) {
            LField(
                value = pass, onChange = { pass = it }, label = "رمز",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                hint = if (creating) "دست‌کم ۶ نویسه" else null
            )
            if (creating) {
                LField(
                    value = pass2, onChange = { pass2 = it }, label = "تکرار رمز",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    hint = if (pass2.isNotEmpty() && pass != pass2) "با رمز واردشده مطابقت ندارد." else null
                )
            } else {
                Text("بعد از انتخاب فایل، همهٔ داده‌های فعلی تراز با محتوای فایل جایگزین می‌شود.", style = lt(12.sp, color = L.RedInk, lineHeight = 21.sp))
            }
            if (m == "drive" || m == "api_connect") {
                Text(
                    "این رمز برای ذخیره‌های بعدی در درایو هم روی گوشی (رمزگذاری‌شده) نگه داشته می‌شود. برای بازگردانی در گوشی دیگر لازم است؛ یادداشتش کنید.",
                    style = lt(12.sp, color = L.Muted, lineHeight = 21.sp)
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}


/**
 * گوگل درایو، دو روش:
 *  ۱. ورود با حساب گوگل (API درایو): نسخه‌های تاریخ‌دار در پوشهٔ «Taraz» درایو کاربر؛ بازگردانی از هر نسخه.
 *  ۲. انتخاب یک فایل از روی گوشی (بدون ورود): همان فایل هر بار به‌روز می‌شود؛ برای گوشی‌های بی‌سرویس گوگل.
 */
@Composable
private fun DriveCard(
    auto: com.example.data.AutoBackup.State,
    busy: Boolean,
    installed: Boolean,
    api: com.example.data.DriveApi.Status?,
    play: Boolean,
    onApiConnect: () -> Unit,
    onApiBackup: () -> Unit,
    onApiRestore: () -> Unit,
    onApiSignOut: () -> Unit,
    onConnect: () -> Unit,
    onSaveNow: () -> Unit,
    onRestore: () -> Unit,
    onOpenExisting: () -> Unit,
    onDisconnect: () -> Unit
) {
    val apiOn = api?.connected == true
    val fileOn = auto.driveName != null
    var showFile by remember { mutableStateOf(fileOn) }
    LayerCard(radius = 20.dp, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (apiOn || fileOn) L.GreenSoft else L.PrimarySoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (apiOn || fileOn) Icons.Default.CloudDone else Icons.Default.CloudUpload, null,
                    tint = if (apiOn || fileOn) L.GreenText else L.PrimaryText, modifier = Modifier.size(24.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("گوگل درایو", style = lt(15.sp, FontWeight.Bold))
                Text(
                    when {
                        apiOn -> "وصل با حساب ${api?.email ?: "گوگل"}"
                        else -> "پشتیبان در درایو خودتان؛ در گوشی دیگر هم برمی‌گردد"
                    },
                    style = lt(12.sp, color = L.Muted), maxLines = 1
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // ─── روش ۱: حساب گوگل
        if (apiOn) {
            when {
                api?.error != null -> CardNote(api.error, null, L.RedSoft, L.RedText)
                (api?.lastAt ?: 0) > 0 -> CardNote(
                    "آخرین پشتیبان: ${weekdayName(api!!.lastAt)} ${JalaliDate.fromTimestamp(api.lastAt).formatShort()}، ${timeOfDay(api.lastAt)}",
                    "${fa(api.lastSize / 1024)} کیلوبایت · در پوشهٔ Taraz", L.GreenSoft, L.GreenText
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton(if (busy) "در حال انجام…" else "پشتیبان اکنون", Modifier.weight(1f), enabled = !busy, height = 48.dp, onClick = onApiBackup)
                SoftBigButton("بازگردانی", Modifier.weight(1f), height = 48.dp) { if (!busy) onApiRestore() }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "نسخه‌ها با تاریخ در پوشهٔ «Taraz» درایو شما ذخیره می‌شوند و ۱۰ نسخهٔ آخر می‌ماند. " +
                    if (auto.enabled) "پشتیبان خودکار هم همین‌جا ذخیره می‌کند." else "با روشن کردن «پشتیبان خودکار»، خودکار هم ذخیره می‌شود.",
                style = lt(12.sp, color = L.Muted, lineHeight = 21.sp)
            )
            Text(
                "خروج از حساب گوگل",
                style = lt(13.sp, FontWeight.SemiBold, L.RedText),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onApiSignOut).padding(vertical = 8.dp)
            )
        } else {
            if (!play) {
                CardNote(
                    "سرویس‌های گوگل‌پلی روی این گوشی نیست",
                    "ورود با حساب گوگل بدون آن ممکن نیست؛ از «روش دوم» پایین استفاده کنید.",
                    L.AmberSoft, L.AmberInk
                )
                Spacer(Modifier.height(10.dp))
            }
            BigButton(if (busy) "در حال انجام…" else "ورود با حساب گوگل", enabled = !busy && play, onClick = onApiConnect)
            Spacer(Modifier.height(8.dp))
            Text(
                "تراز فقط به فایل‌هایی که خودش در درایو می‌سازد دسترسی دارد، نه به بقیهٔ فایل‌های شما. پشتیبان‌ها رمزگذاری‌شده‌اند.",
                style = lt(12.sp, color = L.Muted, lineHeight = 21.sp)
            )
        }

        // ─── روش ۲: انتخاب فایل از روی گوشی
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(L.Line))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { showFile = !showFile }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("روش دوم: انتخاب فایل از روی گوشی", style = lt(14.sp, FontWeight.SemiBold))
                Text(
                    if (fileOn) "وصل · ${auto.driveName}" else "بدون ورود؛ با برنامهٔ درایو یا هر جای دیگر",
                    style = lt(12.sp, color = L.Muted), maxLines = 1
                )
            }
            Text(if (showFile) "بستن" else "باز کردن", style = lt(12.sp, FontWeight.Bold, L.PrimaryText))
        }
        if (showFile) {
            if (!fileOn) {
                if (!installed) {
                    CardNote(
                        "برنامهٔ گوگل درایو روی گوشی نصب نیست",
                        "برای ذخیره در درایو، برنامهٔ Google Drive را نصب و وارد شوید؛ یا فایل را در هر جای دیگری بسازید.",
                        L.AmberSoft, L.AmberInk
                    )
                    Spacer(Modifier.height(10.dp))
                }
                SoftBigButton("انتخاب جای فایل پشتیبان", height = 48.dp) { if (!busy) onConnect() }
                Spacer(Modifier.height(8.dp))
                Text(
                    "در پنجره‌ای که باز می‌شود، از منوی کنار «Drive» را بزنید، پوشه را انتخاب کنید و «ذخیره» را بزنید. تراز از آن به بعد همان فایل را به‌روز می‌کند.",
                    style = lt(12.sp, color = L.Muted, lineHeight = 21.sp)
                )
                Text(
                    "بارگذاری فایل پشتیبان از روی گوشی",
                    style = lt(13.sp, FontWeight.SemiBold, L.PrimaryText),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !busy, onClick = onOpenExisting).padding(vertical = 8.dp)
                )
            } else {
                when {
                    auto.driveError != null -> CardNote(auto.driveError, null, L.RedSoft, L.RedText)
                    auto.driveLastAt > 0 -> CardNote(
                        "آخرین ذخیره: ${weekdayName(auto.driveLastAt)} ${JalaliDate.fromTimestamp(auto.driveLastAt).formatShort()}، ${timeOfDay(auto.driveLastAt)}",
                        null, L.GreenSoft, L.GreenText
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftBigButton("ذخیره در فایل", Modifier.weight(1f), height = 46.dp) { if (!busy) onSaveNow() }
                    SoftBigButton("بازگردانی", Modifier.weight(1f), height = 46.dp) { if (!busy) onRestore() }
                }
                Text(
                    "قطع اتصال فایل",
                    style = lt(13.sp, FontWeight.SemiBold, L.RedText),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDisconnect).padding(vertical = 8.dp)
                )
            }
        }
    }
}

/** یادداشت رنگی داخل یک کارت (بی‌حاشیهٔ بیرونی، هم‌عرض محتوای کارت) */
@Composable
private fun CardNote(title: String, body: String? = null, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(title, style = lt(13.sp, FontWeight.Bold, fg))
        if (body != null) Text(body, style = lt(12.sp, color = fg, lineHeight = 20.sp))
    }
}
