package com.example.ui.layer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Category
import com.example.data.FinancialGoal
import com.example.data.Loan
import com.example.data.Transaction
import com.example.data.countsAsExpense
import com.example.ui.CategoryIcons
import com.example.ui.UserProfile
import com.example.utils.JalaliDate
import com.example.utils.NumberToPersianWords

/** میانگین خرج روزانه در ۳۰ روز گذشته */
fun averageDailySpend(transactions: List<Transaction>): Long {
    val from = System.currentTimeMillis() - 30 * DAY_MS
    val sum = transactions.filter { it.countsAsExpense && it.date >= from }.sumOf { it.amount }
    return sum / 30
}

// ───────────────────────── سقف خرج روزانه ─────────────────────────

@Composable
fun LayerBudgetSheet(current: Long, avgDaily: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by remember { mutableStateOf(if (current > 0) money(current) else "") }
    val value = parseAmount(text)
    val presets = listOf(500_000L to "۵۰۰ هزار", 1_000_000L to "۱ میلیون", 1_200_000L to "۱٫۲ میلیون", 2_000_000L to "۲ میلیون")

    LayerSheet(
        title = "سقف خرج روزانه",
        onDismiss = onDismiss,
        topGap = 130.dp,
        bottomBar = {
            BigButton("ذخیرهٔ سقف", enabled = value > 0) { onSave(value) }
            if (current > 0) {
                Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).clickable { onSave(0L) }, contentAlignment = Alignment.Center) {
                    Text("برداشتن سقف", style = lt(14.sp, FontWeight.SemiBold, L.RedText))
                }
            }
        }
    ) {
        Text("روزشمار خانه با این عدد مقایسه می‌کند.", style = lt(13.sp, color = L.Muted))
        Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (text.isEmpty()) Text("۰", style = lt(54.sp, FontWeight.Light, L.Placeholder, letterSpacing = (-1.5).sp), textAlign = TextAlign.Center)
                BasicTextField(
                    value = text,
                    onValueChange = { text = groupedInput(it) },
                    singleLine = true,
                    textStyle = lt(54.sp, FontWeight.Light, letterSpacing = (-1.5).sp).copy(textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(L.Primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "سقف خرج روزانه به تومان" }
                )
            }
            Text(if (value == 0L) "تومان در روز" else NumberToPersianWords.convert(value) + " تومان در روز", style = lt(13.sp, color = L.Muted))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            presets.forEach { (amt, label) ->
                val on = value == amt
                Box(
                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(if (on) L.Primary else L.Chip)
                        .clickable { text = money(amt) },
                    contentAlignment = Alignment.Center
                ) { Text(label, style = lt(13.sp, if (on) FontWeight.Bold else FontWeight.Medium, if (on) Color.White else L.Ink)) }
            }
        }
        if (avgDaily > 0) {
            val saveMonthly = if (value in 1 until avgDaily) (avgDaily - value) * 30 else 0L
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(L.PrimaryTint).padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = L.PrimaryText, modifier = Modifier.size(18.dp))
                Text(
                    "میانگین خرج روزانهٔ شما در ۳۰ روز گذشته ${money(avgDaily)} تومان بوده." +
                        if (saveMonthly > 0) " با این سقف، ماهی حدود ${millions(saveMonthly).removeSuffix("م")} میلیون کمتر خرج می‌کنید." else "",
                    style = lt(12.sp, color = L.Info, lineHeight = 23.sp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

// ───────────────────────── اهداف پس‌انداز ─────────────────────────

@Composable
fun LayerGoalsScreen(
    goals: List<FinancialGoal>,
    onBack: () -> Unit,
    onAddGoal: (title: String, target: Long, current: Long) -> Unit,
    onAdjust: (FinancialGoal, Long) -> Unit,
    onDelete: (FinancialGoal) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var moneyFor by remember { mutableStateOf<Pair<FinancialGoal, Boolean>?>(null) } // (goal, isDeposit)
    val active = goals.filter { !it.isCompleted }
    val main = active.firstOrNull()
    val others = active.drop(1)
    val done = goals.filter { it.isCompleted }
    val saved = active.sumOf { it.currentAmount }

    LayerScreen(
        title = "اهداف پس‌انداز",
        subtitle = if (active.isEmpty()) "هنوز هدفی ندارید" else "${fa(active.size)} هدف فعال · ${millions(saved).removeSuffix("م")} میلیون جمع شده",
        onBack = onBack,
        trailing = {
            Row(
                Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(L.PrimarySoft).clickable { showAdd = true }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.Add, null, tint = L.PrimaryText, modifier = Modifier.size(18.dp))
                Text("هدف جدید", style = lt(14.sp, FontWeight.Bold, L.PrimaryText))
            }
        }
    ) {
        if (main == null && done.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 70.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(88.dp).clip(CircleShape).background(L.NavSelected), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Flag, null, tint = L.Primary, modifier = Modifier.size(40.dp))
                }
                Text("اولین هدفتان را بگذارید", style = lt(17.sp, FontWeight.Bold))
                Text("مثلاً سفر، لپ‌تاپ یا صندوق اضطراری.", style = lt(13.sp, color = L.Muted))
            }
        }
        if (main != null) {
            val frac = if (main.targetAmount > 0) main.currentAmount.toFloat() / main.targetAmount else 0f
            LayerCard(radius = 24.dp, padding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(L.NavSelected), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Flag, null, tint = L.Primary, modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("هدف اصلی", style = lt(12.sp, FontWeight.Bold, L.PrimaryText))
                        Text(main.title, style = lt(17.sp, FontWeight.Bold))
                    }
                    Text("${fa((frac * 100).toInt())}٪", style = lt(22.sp, FontWeight.Bold, L.Primary))
                }
                LProgress(frac, L.Primary, height = 10.dp, modifier = Modifier.padding(top = 14.dp))
                Row(Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    Text(money(main.currentAmount), style = lt(13.sp, FontWeight.Bold))
                    Text(" از ${money(main.targetAmount)}", style = lt(13.sp, color = L.Muted), modifier = Modifier.weight(1f))
                }
                Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton("افزودن پول", Modifier.weight(1f), height = 48.dp) { moneyFor = main to true }
                    Box(
                        Modifier.height(48.dp).clip(RoundedCornerShape(14.dp)).background(L.PrimarySoft).clickable { moneyFor = main to false }.padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("برداشت", style = lt(14.sp, FontWeight.Bold, L.PrimaryText)) }
                }
            }
        }
        if (others.isNotEmpty()) {
            SectionTitle("اهداف دیگر")
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                others.forEach { g ->
                    val frac = if (g.targetAmount > 0) g.currentAmount.toFloat() / g.targetAmount else 0f
                    Column(Modifier.fillMaxWidth().cardSurface(18.dp).clickable { moneyFor = g to true }.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(g.title, style = lt(14.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f))
                            Text("${fa((frac * 100).toInt())}٪", style = lt(13.sp, FontWeight.Bold))
                        }
                        LProgress(frac, L.Ink, height = 6.dp)
                        Text("${millions(g.currentAmount).removeSuffix("م")} از ${millions(g.targetAmount).removeSuffix("م")} میلیون", style = lt(12.sp, color = L.Muted))
                    }
                }
            }
        }
        done.forEach { g ->
            Row(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(L.GreenSoft).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(L.Green), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(g.title, style = lt(14.sp, FontWeight.Bold, L.GreenInk))
                    Text("انجام شد · ${JalaliDate.fromTimestamp(g.createdAt).formatMonthYear()}", style = lt(12.sp, color = L.GreenInk2))
                }
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).clickable { onDelete(g) }.semantics { contentDescription = "حذف هدف ${g.title}" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Delete, null, tint = L.GreenInk2, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    if (showAdd) {
        LayerAddGoalSheet(onDismiss = { showAdd = false }, onSave = { t, target, cur -> onAddGoal(t, target, cur); showAdd = false })
    }
    moneyFor?.let { (goal, deposit) ->
        var amount by remember(goal.id, deposit) { mutableStateOf("") }
        val v = parseAmount(amount)
        LayerSheet(
            title = (if (deposit) "افزودن به «" else "برداشت از «") + goal.title + "»",
            onDismiss = { moneyFor = null },
            topGap = 200.dp,
            bottomBar = {
                BigButton(if (deposit) "افزودن" else "برداشت", enabled = v > 0) {
                    onAdjust(goal, if (deposit) v else -v); moneyFor = null
                }
                Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).clickable { onDelete(goal); moneyFor = null }, contentAlignment = Alignment.Center) {
                    Text("حذف این هدف", style = lt(14.sp, FontWeight.SemiBold, L.RedText))
                }
            }
        ) {
            LField(amount, { amount = groupedInput(it) }, "مبلغ (تومان)", placeholder = "۰", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                hint = "تا الان ${money(goal.currentAmount)} از ${money(goal.targetAmount)} تومان جمع شده.")
        }
    }
}

@Composable
private fun LayerAddGoalSheet(onDismiss: () -> Unit, onSave: (String, Long, Long) -> Unit) {
    var title by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var current by remember { mutableStateOf("") }
    val t = parseAmount(target)
    LayerSheet(
        title = "هدف جدید", onDismiss = onDismiss, topGap = 160.dp,
        bottomBar = { BigButton("ساخت هدف", enabled = title.isNotBlank() && t > 0) { onSave(title.trim(), t, parseAmount(current)) } }
    ) {
        LField(title, { title = it }, "نام هدف", placeholder = "مثلاً سفر شمال")
        LField(target, { target = groupedInput(it) }, "مبلغ هدف (تومان)", placeholder = "۰", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        LField(current, { current = groupedInput(it) }, "تا حالا جمع کرده‌اید (اختیاری)", placeholder = "۰", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    }
}

// ───────────────────────── تنظیمات ─────────────────────────

@Composable
fun LayerSettingsScreen(
    profile: UserProfile?,
    biometricEnabled: Boolean,
    alertsEnabled: Boolean,
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    smsBankCount: Int,
    categoryCount: Int,
    onBack: () -> Unit,
    onBiometricChange: (Boolean) -> Unit,
    onAlertsChange: (Boolean) -> Unit,
    onTestLock: () -> Unit,
    onOpenSms: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenBackup: () -> Unit,
    onTestServer: ((Boolean, String) -> Unit) -> Unit,
    onLogout: () -> Unit,
    onResetAll: () -> Unit
) {
    var serverMsg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var testing by remember { mutableStateOf(false) }
    var askReset by remember { mutableStateOf(false) }
    val name = profile?.name?.ifBlank { "کاربر تراز" } ?: "کاربر تراز"
    val phone = profile?.phone.orEmpty()
    val phoneMasked = if (phone.length >= 7) fa(phone.take(4) + " ••• " + phone.takeLast(4)) else if (profile?.isGuest == true) "حساب مهمان" else ""

    LayerScreen(title = "تنظیمات", onBack = onBack) {
        LayerCard(radius = 20.dp, padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(L.NavSelected), contentAlignment = Alignment.Center) {
                    Text(name.firstOrNull()?.toString() ?: "ت", style = lt(18.sp, FontWeight.Bold, L.Primary))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(name, style = lt(16.sp, FontWeight.SemiBold))
                    if (phoneMasked.isNotEmpty()) Text(phoneMasked, style = lt(13.sp, color = L.Muted))
                }
            }
        }

        SettingsGroup("ظاهر") {
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LSegmented(listOf("روشن", "تیره"), if (darkMode) 1 else 0, { onDarkModeChange(it == 1) })
                Text(
                    if (darkMode) "حالت تیره: برای شب و نور کم، چشم راحت‌تر است." else "حالت روشن: برای روز و نور زیاد.",
                    style = lt(12.sp, color = L.Muted)
                )
            }
        }
        SettingsGroup("امنیت") {
            SwitchRow("قفل با اثر انگشت", "هر بار که تراز باز می‌شود", biometricEnabled, onBiometricChange)
            NavRow("امتحان صفحهٔ قفل", null, divider = false, onClick = onTestLock)
        }
        SettingsGroup("اعلان‌ها") {
            SwitchRow("اعلان‌ها و یادآور اقساط", "سه روز و یک روز قبل از سررسید، و پیامک‌های تازهٔ بانک", alertsEnabled, onAlertsChange, divider = false)
        }
        SettingsGroup("داده‌ها") {
            NavRow("پیامک‌های بانکی", if (smsBankCount > 0) "${fa(smsBankCount)} بانک روشن" else "خاموش", onClick = onOpenSms)
            NavRow("دسته‌بندی‌ها", "${fa(categoryCount)} دسته", onClick = onOpenCategories)
            NavRow("اهداف پس‌انداز", null, onClick = onOpenGoals)
            NavRow("تراکنش‌های تکراری", "اجاره، حقوق، اشتراک", onClick = onOpenRecurring)
            NavRow("پشتیبان و خروجی اکسل", null, divider = false, onClick = onOpenBackup)
        }
        SettingsGroup("دستیار") {
            NavRow(
                "بررسی اتصال دستیار",
                when {
                    testing -> "در حال بررسی…"
                    serverMsg?.first == true -> "متصل"
                    serverMsg?.first == false -> "قطع"
                    else -> "بزنید تا بررسی شود"
                },
                divider = serverMsg != null,
                valueColor = when { serverMsg?.first == true -> L.GreenText; serverMsg?.first == false -> L.RedText; else -> L.Muted },
                onClick = {
                    if (!testing) {
                        testing = true
                        onTestServer { ok, msg -> testing = false; serverMsg = ok to msg }
                    }
                }
            )
            if (serverMsg != null) {
                Text(serverMsg!!.second, style = lt(12.sp, color = L.Muted, lineHeight = 20.sp), modifier = Modifier.padding(bottom = 12.dp))
            }
        }

        LayerCard(radius = 20.dp, padding = 0.dp, modifier = Modifier.padding(top = 6.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onLogout).padding(horizontal = 16.dp).height(52.dp),
                verticalAlignment = Alignment.CenterVertically
            ) { Text("خروج از حساب", style = lt(14.sp, FontWeight.SemiBold, L.PrimaryText)) }
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(L.Line))
            if (askReset) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("همهٔ حساب‌ها، تراکنش‌ها، اقساط و اهداف پاک می‌شوند و برگشتی ندارند. مطمئنید؟", style = lt(13.sp, color = L.RedInk, lineHeight = 23.sp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(L.RedText).clickable(onClick = onResetAll), contentAlignment = Alignment.Center) {
                            Text("بله، همه پاک شود", style = lt(14.sp, FontWeight.Bold, Color.White))
                        }
                        Box(Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(L.Chip).clickable { askReset = false }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                            Text("انصراف", style = lt(14.sp, FontWeight.SemiBold))
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().clickable { askReset = true }.padding(horizontal = 16.dp).height(60.dp), verticalArrangement = Arrangement.Center) {
                    Text("پاک کردن همهٔ داده‌ها", style = lt(14.sp, FontWeight.SemiBold, L.RedText))
                    Text("قبل از پاک شدن، یک بار دیگر از شما تأیید گرفته می‌شود", style = lt(12.sp, color = L.Muted))
                }
            }
        }
        Text("تراز · نسخهٔ ۲٫۰", style = lt(12.sp, color = L.Muted), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = lt(13.sp, FontWeight.Bold, L.Muted), modifier = Modifier.padding(horizontal = 26.dp))
        LayerCard(radius = 20.dp, padding = 0.dp) {
            Column(Modifier.padding(horizontal = 16.dp)) { content() }
        }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit, divider: Boolean = true) {
    LRow(title, sub, divider = divider, trailing = { LSwitch(checked, title, onChange = onChange) })
}

@Composable
private fun NavRow(title: String, value: String?, divider: Boolean = true, valueColor: Color = L.Muted, onClick: () -> Unit) {
    LRow(
        title, null, divider = divider, onClick = onClick,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (value != null) Text(value, style = lt(12.sp, FontWeight.SemiBold, valueColor))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = L.Chevron, modifier = Modifier.size(18.dp))
            }
        }
    )
}

// ───────────────────────── دسته‌بندی‌ها ─────────────────────────

@Composable
fun LayerCategoriesScreen(
    categories: List<Category>,
    transactions: List<Transaction>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onDelete: (Category) -> Unit,
    onSetBudget: (Category, Long) -> Unit
) {
    var askDelete by remember { mutableStateOf<Category?>(null) }
    var editBudget by remember { mutableStateOf<Category?>(null) }
    val spent = remember(transactions) { spentThisMonthByCategory(transactions) }
    LayerScreen(
        title = "دسته‌بندی‌ها",
        subtitle = "${fa(categories.size)} دسته",
        onBack = onBack,
        bottomBar = { BigButton("دستهٔ جدید", onClick = onAdd) }
    ) {
        Text(
            "روی هر دستهٔ هزینه بزنید تا برایش بودجهٔ ماهانه بگذارید.",
            style = lt(12.sp, color = L.Muted), modifier = Modifier.padding(horizontal = 24.dp)
        )
        listOf(false to "هزینه‌ها", true to "درآمدها").forEach { (income, label) ->
            val list = categories.filter { it.isIncome == income }.sortedBy { it.sortOrder }
            if (list.isNotEmpty()) {
                SectionTitle(label)
                LayerCard(radius = 20.dp, padding = 0.dp) {
                    list.forEachIndexed { i, c ->
                        val col = CategoryIcons.colorOf(c.colorHex)
                        val used = spent[c.name] ?: 0L
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !c.isIncome) { editBudget = c }.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconWell(CategoryIcons.vectorFor(c.iconKey), size = 38.dp, bg = col, fg = Color.White, iconSize = 20.dp, radius = 12.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(c.name, style = lt(14.sp, FontWeight.Medium))
                                if (!c.isIncome && c.monthlyBudget > 0) {
                                    val pct = (used * 100 / c.monthlyBudget).toInt()
                                    LProgress(
                                        (used.toFloat() / c.monthlyBudget).coerceIn(0f, 1f),
                                        if (pct >= 100) L.RedText else if (pct >= 80) L.Amber else L.Primary,
                                        height = 5.dp
                                    )
                                    Text("${millions(used)} از ${millions(c.monthlyBudget)} · ${fa(pct)}٪", style = lt(11.sp, color = if (pct >= 100) L.RedText else L.Muted))
                                } else if (!c.isIncome && used > 0) {
                                    Text("این ماه ${millions(used)}", style = lt(11.sp, color = L.Muted))
                                }
                            }
                            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).clickable { askDelete = c }.semantics { contentDescription = "حذف دستهٔ ${c.name}" }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Delete, null, tint = L.Faint, modifier = Modifier.size(18.dp))
                            }
                        }
                        if (i < list.size - 1) Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(L.Line))
                    }
                }
            }
        }
    }
    editBudget?.let { c ->
        CategoryBudgetSheet(c, spent[c.name] ?: 0L, onDismiss = { editBudget = null }) { onSetBudget(c, it) }
    }
    askDelete?.let { c ->
        LayerSheet(
            title = "حذف دستهٔ «${c.name}»", onDismiss = { askDelete = null }, topGap = 300.dp,
            bottomBar = {
                BigButton("بله، حذف شود", background = L.RedText) { onDelete(c); askDelete = null }
            }
        ) {
            Text("تراکنش‌هایی که با این دسته ثبت شده‌اند باقی می‌مانند.", style = lt(13.sp, color = L.Muted, lineHeight = 23.sp))
        }
    }
}

@Composable
fun LayerAddCategoryScreen(
    onBack: () -> Unit,
    onSave: (name: String, iconKey: String, colorHex: String, isIncome: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var income by remember { mutableStateOf(false) }
    var iconKey by remember { mutableStateOf(CategoryIcons.keys.first()) }
    var colorHex by remember { mutableStateOf(CategoryIcons.palette.first()) }
    val col = CategoryIcons.colorOf(colorHex)

    LayerScreen(
        title = "دستهٔ جدید",
        subtitle = "نام، آیکن و رنگ را انتخاب کنید",
        onBack = onBack,
        bottomBar = { BigButton("ساخت دسته", enabled = name.isNotBlank()) { onSave(name.trim(), iconKey, colorHex, income) } }
    ) {
        LayerCard(radius = 20.dp, padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconWell(CategoryIcons.vectorFor(iconKey), size = 44.dp, bg = col, fg = Color.White, iconSize = 22.dp, radius = 14.dp)
                Column(Modifier.weight(1f)) {
                    Text(name.ifBlank { "نام دسته" }, style = lt(15.sp, FontWeight.SemiBold, if (name.isBlank()) L.Placeholder else L.Ink))
                    Text(if (income) "درآمد" else "هزینه", style = lt(12.sp, color = L.Muted))
                }
                Text("پیش‌نمایش", style = lt(12.sp, color = L.Muted))
            }
        }
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            LSegmented(listOf("هزینه", "درآمد"), if (income) 1 else 0, { income = it == 1 })
            LField(name, { name = it }, "نام دسته", placeholder = "مثلاً قبض برق")
            Text("آیکن", style = lt(13.sp, FontWeight.SemiBold))
            CategoryIcons.keys.chunked(8).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { k ->
                        val on = k == iconKey
                        Box(
                            Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(if (on) col else L.Chip)
                                .clickable { iconKey = k }.semantics { role = Role.RadioButton },
                            contentAlignment = Alignment.Center
                        ) { Icon(CategoryIcons.vectorFor(k), null, tint = if (on) Color.White else L.Ink, modifier = Modifier.size(20.dp)) }
                    }
                }
            }
            Text("رنگ", style = lt(13.sp, FontWeight.SemiBold))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CategoryIcons.palette.forEach { hex ->
                    val on = hex == colorHex
                    Box(Modifier.weight(1f).height(40.dp).clickable { colorHex = hex }.semantics { role = Role.RadioButton }, contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(CategoryIcons.colorOf(hex))
                                .then(if (on) Modifier.border(3.dp, L.Surface, CircleShape).border(5.dp, CategoryIcons.colorOf(hex), CircleShape) else Modifier),
                            contentAlignment = Alignment.Center
                        ) { if (on) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                    }
                }
            }
        }
    }
}

// ───────────────────────── اعلان‌ها ─────────────────────────

@Composable
fun LayerNotificationsScreen(
    loans: List<Loan>,
    pendingSmsCount: Int,
    todayExpense: Long,
    dailyBudget: Long,
    alertsEnabled: Boolean,
    onAlertsChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onPay: (Loan) -> Unit,
    onReviewSms: () -> Unit
) {
    val now = System.currentTimeMillis()
    val dueSoon = loans.filter { !it.isSettled }
        .map { it to JalaliDate.nextDueTimestamp(it.dueDay, now) }
        .filter { JalaliDate.daysUntil(it.second, now) in 0..7 }
        .sortedBy { it.second }
    val budgetPct = if (dailyBudget > 0) (todayExpense * 100 / dailyBudget).toInt() else -1
    val count = dueSoon.size + (if (pendingSmsCount > 0) 1 else 0) + (if (budgetPct >= 0) 1 else 0)

    LayerScreen(
        title = "اعلان‌ها",
        subtitle = if (count == 0) "چیز تازه‌ای نیست" else "${fa(count)} مورد برای امروز",
        onBack = onBack
    ) {
        if (count == 0) {
            Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(80.dp).clip(CircleShape).background(L.NavSelected), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Notifications, null, tint = L.Primary, modifier = Modifier.size(36.dp))
                }
                Text("همه‌چیز مرتب است", style = lt(16.sp, FontWeight.Bold))
            }
        } else {
            Text("امروز", style = lt(13.sp, FontWeight.Bold, L.Muted), modifier = Modifier.padding(horizontal = 24.dp))
        }
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            dueSoon.forEach { (loan, due) ->
                val days = JalaliDate.daysUntil(due, now)
                NotifItem(
                    iconBg = L.AmberSoft, iconFg = L.AmberText,
                    title = "قسط ${loan.loanName}، ${daysLabel(days)}",
                    sub = "${money(loan.installmentAmount)} تومان · ${weekdayName(due)} ${JalaliDate.fromTimestamp(due).formatShort()}",
                    action = "پرداخت", onAction = { onPay(loan) }
                )
            }
            if (pendingSmsCount > 0) {
                NotifItem(L.Chip, L.Ink, "${fa(pendingSmsCount)} پیامک بانکی منتظر تأیید", "برای ثبت در حساب‌ها بررسی کنید", "بررسی", onReviewSms)
            }
            if (budgetPct >= 0) {
                NotifItem(
                    L.Chip, L.Ink, "${fa(budgetPct)}٪ سقف امروز خرج شده",
                    if (todayExpense <= dailyBudget) "هنوز ${money(dailyBudget - todayExpense)} تومان جا دارید" else "${money(todayExpense - dailyBudget)} تومان از سقف گذشته‌اید",
                    null, {}
                )
            }
        }
        SectionTitle("چه چیزی خبرم کند؟", Modifier.padding(top = 8.dp))
        LayerCard(radius = 20.dp, padding = 0.dp) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SwitchRow("اعلان‌ها و یادآوری اقساط", "یک روز قبل از سررسید و هنگام ثبت‌های تازه", alertsEnabled, onAlertsChange, divider = false)
            }
        }
    }
}

@Composable
private fun NotifItem(iconBg: Color, iconFg: Color, title: String, sub: String, action: String?, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().cardSurface(18.dp).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(iconBg), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Notifications, null, tint = iconFg, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = lt(14.sp, FontWeight.SemiBold))
            Text(sub, style = lt(12.sp, color = L.Muted))
        }
        if (action != null) SoftButton(action, 36.dp, onAction)
    }
}
