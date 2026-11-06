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
            value = text, onChange = { text = groupedInput(it) }, label = "سقف هزینهٔ ماهانه (تومان)", placeholder = "۰",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            hint = "هزینهٔ این ماه تاکنون ${money(spent)} تومان است. با رسیدن به ۸۰٪ سقف، در صفحهٔ خانه هشدار نمایش داده می‌شود."
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
            Callout("اول یک حساب بسازید", "تراکنش تکراری روی یکی از حساب‌ها ثبت می‌شود.", L.AmberSoft, L.AmberInk, Modifier.padding(horizontal = 20.dp))
        }
        val candidates = remember(transactions, items) { detectRecurringCandidates(transactions, items) }
        if (candidates.isNotEmpty()) {
            SectionTitle("پیدا شد: این‌ها هر ماه تکرار شده‌اند")
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
                "اگر اشتراکی را دیگر لازم ندارید، لغوش کنید؛ ${money(candidates.sumOf { it.amount })} تومان در ماه از این‌ها می‌رود.",
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
        val day = parseAmount(dayText).toInt()
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
                value = amountText, onChange = { amountText = groupedInput(it) }, label = "مبلغ (تومان)", placeholder = "۰",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            LField(
                value = dayText, onChange = { dayText = it.filter { c -> c.isDigit() }.take(2) }, label = "روز ماه (۱ تا ۳۱)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                hint = "در ماه‌های کوتاه‌تر، آخرین روز ماه ثبت می‌شود."
            )
            Text("حساب", style = lt(13.sp, FontWeight.SemiBold))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cards.forEach { c -> LChip(c.bankName, bank == c.bankName) { bank = c.bankName } }
            }
            Text("دسته", style = lt(13.sp, FontWeight.SemiBold))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cats.forEach { c -> LChip(c.name, category == c.name) { category = c.name } }
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
    onAutoNow: () -> Unit = {}
) {
    var mode by remember { mutableStateOf<String?>(null) } // "create" | "restore" | "auto"
    LayerScreen(title = "پشتیبان و خروجی", subtitle = "داده‌هایتان را بیرون از گوشی هم نگه دارید", onBack = onBack) {
        message?.let { (ok, text) ->
            Callout(text, null, if (ok) L.GreenSoft else L.RedSoft, if (ok) L.GreenText else L.RedText, Modifier.padding(horizontal = 20.dp))
        }
        if (auto != null) {
            LayerCard(radius = 20.dp, padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("پشتیبان خودکار", style = lt(15.sp, FontWeight.Bold))
                        Text("بی‌آنکه کاری کنید، خودش فایل رمزدار می‌سازد", style = lt(12.sp, color = L.Muted))
                    }
                    LSwitch(auto.enabled, "پشتیبان خودکار") { on ->
                        when {
                            !on -> onAutoEnabled(false)
                            auto.folderLabel == null -> onPickFolder()
                            !auto.hasPassword -> mode = "auto"
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
                        Text(if (auto.hasPassword) "گذاشته شده" else "گذاشته نشده", style = lt(14.sp, FontWeight.SemiBold))
                    }
                    SoftButton(if (auto.hasPassword) "تغییر" else "گذاشتن", height = 36.dp) { mode = "auto" }
                }
                Spacer(Modifier.height(10.dp))
                when {
                    auto.lastError != null -> Callout(auto.lastError, null, L.RedSoft, L.RedText)
                    auto.lastAt > 0 -> Callout(
                        "آخرین پشتیبان: ${weekdayName(auto.lastAt)} ${JalaliDate.fromTimestamp(auto.lastAt).formatShort()}، ${timeOfDay(auto.lastAt)} · ${fa(auto.lastSize / 1024)} کیلوبایت",
                        null, L.GreenSoft, L.GreenText
                    )
                }
                if (auto.folderLabel != null && auto.hasPassword) {
                    Spacer(Modifier.height(8.dp))
                    SoftButton(if (busy) "در حال ساخت…" else "تهیهٔ پشتیبان اکنون", height = 36.dp) { if (!busy) onAutoNow() }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "رمز یک بار پرسیده و رمزگذاری‌شده روی گوشی نگه داشته می‌شود. ۵ نسخهٔ آخر می‌ماند و قدیمی‌ترها پاک می‌شوند. پوشه را می‌توانید با گوگل‌درایو هم‌گام کنید.",
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
            "فایل پشتیبان رمزگذاری می‌شود و بدون رمز باز نمی‌شود؛ رمز را جایی یادداشت کنید، تراز آن را نگه نمی‌دارد. " +
                "فایل را در درایو، تلگرام یا هر جای امن دیگری نگه دارید تا در صورت مفقود شدن گوشی، اطلاعات از دست نرود.",
            style = lt(12.sp, color = L.Muted, lineHeight = 21.sp), modifier = Modifier.padding(horizontal = 24.dp)
        )
        if (busy) Text("در حال انجام…", style = lt(13.sp, FontWeight.SemiBold, L.PrimaryText), modifier = Modifier.padding(horizontal = 24.dp))
    }

    mode?.let { m ->
        var pass by remember(m) { mutableStateOf("") }
        var pass2 by remember(m) { mutableStateOf("") }
        val creating = m == "create" || m == "auto"
        val ok = if (creating) pass.length >= 6 && pass == pass2 else pass.isNotEmpty()
        LayerSheet(
            title = if (creating) "رمز فایل پشتیبان" else "رمز فایل پشتیبان را بنویسید",
            onDismiss = { mode = null },
            topGap = 200.dp,
            bottomBar = {
                BigButton(
                    when (m) { "auto" -> "ذخیرهٔ رمز"; "create" -> "ساخت و ذخیرهٔ فایل"; else -> "انتخاب فایل و بازگردانی" },
                    enabled = ok
                ) {
                    when (m) {
                        "auto" -> { onAutoPassword(pass); onAutoEnabled(true) }
                        "create" -> onCreate(pass)
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
                    hint = if (pass2.isNotEmpty() && pass != pass2) "با رمز بالا یکی نیست." else null
                )
            } else {
                Text("بعد از انتخاب فایل، همهٔ داده‌های فعلی تراز با محتوای فایل جایگزین می‌شود.", style = lt(12.sp, color = L.RedInk, lineHeight = 21.sp))
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
