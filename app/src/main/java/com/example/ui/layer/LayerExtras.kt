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
import com.example.data.Debt
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
            value = text, onChange = { text = groupedInput(it) }, label = "سقف خرج در ماه (تومان)", placeholder = "۰",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            hint = "این ماه تا الان ${money(spent)} تومان خرج شده. با رسیدن به ۸۰٪ در خانه هشدار می‌بینید."
        )
        Spacer(Modifier.height(4.dp))
    }
}

// ───────────────────────── تراکنش‌های تکراری ─────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LayerRecurringScreen(
    items: List<RecurringTransaction>,
    cards: List<BankCard>,
    categories: List<Category>,
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
        if (items.isEmpty()) {
            Text(
                "مبلغ‌هایی که هر ماه تکرار می‌شوند را یک بار اینجا بنویسید؛ تراز روزش که برسد خودش ثبتشان می‌کند.",
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
                        Text(signedMoney(r.amount, r.isExpense), style = lt(14.sp, FontWeight.SemiBold, if (r.isExpense) L.Ink else L.Green))
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
        var category by remember(isExpense) { mutableStateOf(cats.firstOrNull()?.name ?: if (isExpense) "سایر" else "درآمد دیگر") }
        val day = parseAmount(dayText).toInt()
        val amount = parseAmount(amountText)
        val ok = title.isNotBlank() && amount > 0 && day in 1..31 && bank.isNotBlank()
        LayerSheet(
            title = "تراکنش تکراری تازه",
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

// ───────────────────────── طلب و بدهی ─────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LayerDebtsScreen(
    debts: List<Debt>,
    cards: List<BankCard>,
    onBack: () -> Unit,
    onAdd: (person: String, amount: Long, owedToMe: Boolean, note: String) -> Unit,
    onSettle: (Debt, String?) -> Unit,
    onDelete: (Debt) -> Unit
) {
    var adding by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf<Debt?>(null) }
    val open = debts.filter { !it.settled }
    val owedToMe = open.filter { it.owedToMe }.sumOf { it.amount }
    val iOwe = open.filter { !it.owedToMe }.sumOf { it.amount }

    LayerScreen(
        title = "طلب و بدهی",
        subtitle = if (open.isEmpty()) "حساب‌وکتاب با دوستان و آشنایان" else "${fa(open.size)} مورد باز",
        onBack = onBack,
        bottomBar = { BigButton("ثبت طلب یا بدهی") { adding = true } }
    ) {
        Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f).cardSurface(18.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("طلب من", style = lt(12.sp, color = L.Muted))
                Text(money(owedToMe), style = lt(18.sp, FontWeight.Bold, L.Green))
            }
            Column(Modifier.weight(1f).cardSurface(18.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("بدهی من", style = lt(12.sp, color = L.Muted))
                Text(money(iOwe), style = lt(18.sp, FontWeight.Bold, L.RedText))
            }
        }
        if (debts.isEmpty()) {
            Text(
                "پولی که به کسی قرض داده‌اید یا از کسی گرفته‌اید را اینجا بنویسید تا یادتان بماند.",
                style = lt(13.sp, color = L.Muted, lineHeight = 23.sp), modifier = Modifier.padding(horizontal = 24.dp)
            )
        } else {
            LayerCard(radius = 20.dp, padding = 0.dp) {
                debts.forEachIndexed { i, d ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(d.person, style = lt(14.sp, FontWeight.SemiBold, if (d.settled) L.Muted else L.Ink), maxLines = 1)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (d.settled) StatusPill("تسویه شد", L.GreenSoft, L.GreenText)
                                else if (d.owedToMe) StatusPill("طلب", L.GreenSoft, L.GreenText)
                                else StatusPill("بدهی", L.RedSoft, L.RedText)
                                Text(
                                    (if (d.note.isNotBlank()) d.note + " · " else "") + JalaliDate.fromTimestamp(d.createdAt).formatShort(),
                                    style = lt(12.sp, color = L.Muted), maxLines = 1
                                )
                            }
                        }
                        Text(money(d.amount), style = lt(14.sp, FontWeight.SemiBold, if (d.settled) L.Muted else L.Ink))
                        if (!d.settled) SoftButton("تسویه", height = 34.dp) { settling = d }
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).clickable { onDelete(d) }.semantics { contentDescription = "حذف ${d.person}" },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Delete, null, tint = L.Faint, modifier = Modifier.size(18.dp)) }
                    }
                    if (i < debts.size - 1) Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(L.Line))
                }
            }
        }
    }

    if (adding) {
        var owed by remember { mutableStateOf(true) }
        var person by remember { mutableStateOf("") }
        var amountText by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        val amount = parseAmount(amountText)
        LayerSheet(
            title = "طلب یا بدهی تازه",
            onDismiss = { adding = false },
            topGap = 120.dp,
            bottomBar = {
                BigButton("ذخیره", enabled = person.isNotBlank() && amount > 0) {
                    onAdd(person, amount, owed, note)
                    adding = false
                }
            }
        ) {
            LSegmented(listOf("طلب دارم", "بدهکارم"), if (owed) 0 else 1, { owed = it == 0 })
            LField(value = person, onChange = { person = it }, label = "نام شخص", placeholder = "مثلاً علی")
            LField(
                value = amountText, onChange = { amountText = groupedInput(it) }, label = "مبلغ (تومان)", placeholder = "۰",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            LField(value = note, onChange = { note = it }, label = "یادداشت (اختیاری)", placeholder = "مثلاً پول شام")
            Spacer(Modifier.height(4.dp))
        }
    }

    settling?.let { d ->
        var account by remember(d.id) { mutableStateOf<String?>(cards.firstOrNull()?.bankName) }
        LayerSheet(
            title = if (d.owedToMe) "${d.person} طلبتان را داد؟" else "بدهی به ${d.person} را دادید؟",
            onDismiss = { settling = null },
            topGap = 200.dp,
            bottomBar = {
                BigButton("تسویه شد") { onSettle(d, account); settling = null }
            }
        ) {
            Text(
                if (d.owedToMe) "${money(d.amount)} تومان به کدام حساب واریز شد؟" else "${money(d.amount)} تومان از کدام حساب پرداخت شد؟",
                style = lt(14.sp, lineHeight = 24.sp)
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cards.forEach { c -> LChip(c.bankName, account == c.bankName) { account = c.bankName } }
                LChip("هیچ‌کدام (نقدی/بیرون از تراز)", account == null) { account = null }
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
    onExportCsv: () -> Unit
) {
    var mode by remember { mutableStateOf<String?>(null) } // "create" | "restore"
    LayerScreen(title = "پشتیبان و خروجی", subtitle = "داده‌هایتان را بیرون از گوشی هم نگه دارید", onBack = onBack) {
        message?.let { (ok, text) ->
            Callout(text, null, if (ok) L.GreenSoft else L.RedSoft, if (ok) L.GreenText else L.RedText, Modifier.padding(horizontal = 20.dp))
        }
        LayerCard(radius = 20.dp, padding = 0.dp) {
            LRow("ساخت فایل پشتیبان", "همهٔ حساب‌ها، تراکنش‌ها، اقساط و اهداف، با رمز", onClick = { if (!busy) mode = "create" })
            LRow("بازگردانی از پشتیبان", "داده‌های فعلی با محتوای فایل جایگزین می‌شود", onClick = { if (!busy) mode = "restore" })
            LRow("خروجی اکسل (CSV)", "فهرست همهٔ تراکنش‌ها برای اکسل یا گوگل‌شیت", divider = false, onClick = { if (!busy) onExportCsv() })
        }
        Text(
            "فایل پشتیبان رمزگذاری می‌شود و بدون رمز باز نمی‌شود؛ رمز را جایی یادداشت کنید، تراز آن را نگه نمی‌دارد. " +
                "فایل را در درایو، تلگرام یا هر جای امن دیگری نگه دارید تا با گم شدن گوشی چیزی از دست نرود.",
            style = lt(12.sp, color = L.Muted, lineHeight = 21.sp), modifier = Modifier.padding(horizontal = 24.dp)
        )
        if (busy) Text("در حال انجام…", style = lt(13.sp, FontWeight.SemiBold, L.PrimaryText), modifier = Modifier.padding(horizontal = 24.dp))
    }

    mode?.let { m ->
        var pass by remember(m) { mutableStateOf("") }
        var pass2 by remember(m) { mutableStateOf("") }
        val creating = m == "create"
        val ok = if (creating) pass.length >= 6 && pass == pass2 else pass.isNotEmpty()
        LayerSheet(
            title = if (creating) "رمز فایل پشتیبان" else "رمز فایل پشتیبان را بنویسید",
            onDismiss = { mode = null },
            topGap = 200.dp,
            bottomBar = {
                BigButton(if (creating) "ساخت و ذخیرهٔ فایل" else "انتخاب فایل و بازگردانی", enabled = ok) {
                    if (creating) onCreate(pass) else onRestore(pass)
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
