package com.example.ui.layer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.data.AccountType
import com.example.data.BankBin
import com.example.data.BankCard
import com.example.data.isCredit
import com.example.data.BankSender
import com.example.data.Category
import com.example.data.Transaction
import com.example.data.isCash
import com.example.data.isTransfer
import com.example.ui.CardNumberTransformation
import com.example.utils.JalaliDate

private fun hasAutoSms(card: BankCard, senders: List<BankSender>): Boolean =
    senders.any { it.isEnabled && (it.bankName.contains(card.bankName) || card.bankName.contains(it.bankName)) }

private fun senderFor(card: BankCard, senders: List<BankSender>): BankSender? =
    senders.firstOrNull { it.bankName.contains(card.bankName) || card.bankName.contains(it.bankName) }

/** نشان کوچک رنگی بانک */
@Composable
private fun BankChip(card: BankCard, color: Color, w: Int = 54, h: Int = 36) {
    val logo = remember(card.bankName, card.cardNumber, card.accountType) {
        if (card.isCash) null else BankLogos.assetFor(card.bankName, card.cardNumber)
    }
    // لوگو خودش زمینهٔ سفید دارد؛ پس پشتش دیگر قاب رنگی نمی‌آید
    if (logo != null) {
        Box(
            Modifier.width(w.dp).height(h.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)
                .border(1.dp, L.Line, RoundedCornerShape(8.dp)).padding(4.dp),
            contentAlignment = Alignment.Center
        ) { BankLogo(logo, Modifier.fillMaxSize(), card.bankName) }
        return
    }
    Box(
        Modifier.width(w.dp).height(h.dp).clip(RoundedCornerShape(8.dp)).background(if (card.isCash) L.GreenSoft else color),
        contentAlignment = if (card.isCash) Alignment.Center else Alignment.BottomStart
    ) {
        if (card.isCash) Icon(Icons.Default.Payments, null, tint = L.GreenText, modifier = Modifier.size(20.dp))
        else Text(card.bankName.removePrefix("بانک ").take(6), style = lt(12.sp, FontWeight.Bold, Color.White), modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), maxLines = 1)
    }
}

/** «شناسایی شد: بانک ملت» با لوگوی همان بانک */
@Composable
private fun DetectedBankPill(bank: String, logo: String?) {
    Row(
        Modifier.clip(PillShape).background(L.GreenSoft).padding(start = 5.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (logo != null) Box(
            Modifier.size(22.dp).clip(CircleShape).background(Color.White).padding(3.dp),
            contentAlignment = Alignment.Center
        ) { BankLogo(logo, Modifier.fillMaxSize()) }
        else Icon(Icons.Default.Check, null, tint = L.GreenText, modifier = Modifier.size(14.dp))
        Text("شناسایی شد: $bank", style = lt(12.sp, FontWeight.Bold, L.GreenText))
    }
}

/**
 * فهرست حساب‌ها — طرح نهایی: موجودی کل درشت، کارت هر حساب با وضعیت پیامک خودکار.
 */
@Composable
fun LayerAccountsScreen(
    cards: List<BankCard>,
    senders: List<BankSender>,
    totalBalance: Long,
    onBack: () -> Unit,
    onOpenAccount: (BankCard) -> Unit,
    onAdd: () -> Unit,
    onTransfer: () -> Unit,
    holderName: String = "",
    assetsTotal: Long = 0,
    onOpenAssets: () -> Unit = {}
) {
    // چشم سراسری: همهٔ مبلغ‌های این صفحه را با هم پنهان می‌کند
    var hideAmounts by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    ScreenTour("accounts", Tours.accounts)
    LayerScreen(
        title = "حساب‌ها",
        trailing = {
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable { hideAmounts = !hideAmounts }
                    .semantics { contentDescription = if (hideAmounts) "نمایش مبلغ‌ها" else "پنهان کردن مبلغ‌ها" },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (hideAmounts) Icons.Default.VisibilityOff else Icons.Default.Visibility, null,
                    tint = L.Ink, modifier = Modifier.size(20.dp)
                )
            }
        },
        subtitle = if (cards.isEmpty()) "هنوز حسابی ندارید" else "${fa(cards.size)} حساب · برای مشاهدهٔ جزئیات، روی حساب ضربه بزنید",
        onBack = onBack,
        bottomBar = {
            BigButton("افزودن حساب", Modifier.tourTarget("acc.actions"), onClick = onAdd)
            if (cards.size >= 2) SoftBigButton("انتقال بین حساب‌ها", onClick = onTransfer)
        }
    ) {
        Column(Modifier.padding(horizontal = 24.dp).tourTarget("acc.total"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("موجودی کل", style = lt(13.sp, color = L.Muted))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (hideAmounts) "••••••" else money(totalBalance), style = lt(38.sp, FontWeight.Light, letterSpacing = (-1.5).sp))
                Text("تومان", style = lt(14.sp), modifier = Modifier.padding(bottom = 6.dp))
            }
        }
        // دارایی‌ها (ارز، طلا، رمزارز، صندوق) با قیمت روز
        Row(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().tourTarget("acc.assets").cardSurface(18.dp).clickable(onClick = onOpenAssets)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("دارایی‌ها", style = lt(14.sp, FontWeight.SemiBold))
                Text(
                    if (assetsTotal > 0) "دلار، طلا، رمزارز و صندوق · با قیمت روز" else "دلار، طلا، رمزارز یا صندوق اضافه کنید",
                    style = lt(12.sp, color = L.Muted)
                )
            }
            if (assetsTotal > 0) Text(if (hideAmounts) "••••" else money(assetsTotal), style = lt(15.sp, FontWeight.Bold))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = L.Chevron, modifier = Modifier.size(18.dp))
        }

        // کارت‌ها با طرح دوتکه، ورق‌خوردنی
        BankCardPager(cards = cards, holderName = holderName, hideBalance = hideAmounts, onOpen = onOpenAccount)

        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            cards.forEachIndexed { i, card ->
                Row(
                    Modifier.fillMaxWidth().cardSurface(20.dp).clickable { onOpenAccount(card) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BankChip(card, cardColor(i))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(card.bankName, style = lt(15.sp, FontWeight.SemiBold), maxLines = 1)
                            if (card.cardNumber.length >= 4) Text("•••• ${fa(card.cardNumber.takeLast(4))}", style = lt(12.sp, color = L.Muted))
                        }
                        if (card.isCredit) Text(creditUsage(card), style = lt(11.sp, color = L.Muted), maxLines = 1)
                        else if (com.example.Features.SMS && hasAutoSms(card, senders)) StatusPill("پیامک خودکار روشن", L.GreenSoft, L.GreenText)
                        else StatusPill("ثبت دستی", L.Chip, L.Muted)
                    }
                    Text(if (hideAmounts) "••••" else money(card.balance), style = lt(15.sp, FontWeight.SemiBold))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = L.Chevron, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/**
 * جزئیات یک حساب — کارت رنگی بانک، عملیات سریع، کلید پیامک خودکار، آخرین تراکنش‌ها و حذف.
 */
@Composable
fun LayerAccountDetailScreen(
    card: BankCard,
    cardIndex: Int,
    cards: List<BankCard>,
    transactions: List<Transaction>,
    categories: List<Category>,
    senders: List<BankSender>,
    holderName: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onTransfer: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenTransaction: (Transaction) -> Unit,
    onToggleSms: (BankSender) -> Unit,
    onOpenSmsSetup: () -> Unit,
    onDelete: () -> Unit,
    onRepay: () -> Unit = {}
) {
    val color = if (card.isCash) L.Green else cardColor(cardIndex)
    val mine = transactions.filter { it.bankName == card.bankName }.sortedByDescending { it.date }
    val monthStart = JalaliDate.monthRange(System.currentTimeMillis()).first
    val monthCount = mine.count { it.date >= monthStart }
    val sender = senderFor(card, senders)
    val smsOn = sender?.isEnabled == true
    var askDelete by remember { mutableStateOf(false) }
    val logo = remember(card.bankName, card.cardNumber, card.accountType) {
        if (card.isCash) null else BankLogos.assetFor(card.bankName, card.cardNumber)
    }

    LayerScreen(
        title = card.bankName,
        subtitle = "${if (card.isCash) "حساب نقدی" else "حساب"} · ${fa(monthCount)} تراکنش در ${JalaliDate.today().monthName}",
        onBack = onBack
    ) {
        // کارت (طرح دوتکه)
        var revealed by remember(card.id) { mutableStateOf(false) }
        BankCardView(
            card = card,
            fallbackColor = color,
            revealed = revealed,
            onToggleReveal = { revealed = !revealed },
            holderName = holderName,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        // عملیات
        Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile("ویرایش", Icons.Default.Edit, Modifier.weight(1f), onEdit)
            if (card.isCredit && cards.any { !it.isCredit }) ActionTile("بازپرداخت", Icons.Default.Payments, Modifier.weight(1f), onRepay)
            else if (cards.size >= 2) ActionTile("انتقال", Icons.Default.SwapHoriz, Modifier.weight(1f), onTransfer)
            ActionTile("تراکنش‌ها", Icons.Default.List, Modifier.weight(1f), onOpenTransactions)
        }

        // پیامک (در نسخهٔ فعلی خاموش است)
        if (!card.isCash && com.example.Features.SMS) {
            LayerCard(radius = 20.dp, padding = 14.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("ثبت خودکار از پیامک بانک", style = lt(14.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f))
                    LSwitch(smsOn, "ثبت خودکار از پیامک بانک ${card.bankName}") {
                        if (sender != null) onToggleSms(sender) else onOpenSmsSetup()
                    }
                }
                Text(
                    if (smsOn) "روشن است: پیامک‌های برداشت و واریز ${card.bankName} خوانده و برای تأیید شما ثبت می‌شوند."
                    else "خاموش است: تراکنش‌های این حساب را خودتان ثبت می‌کنید.",
                    style = lt(12.sp, color = L.Muted, lineHeight = 21.sp), modifier = Modifier.padding(top = 8.dp)
                )
                Box(Modifier.padding(top = 8.dp)) { SoftButton("تنظیمات پیامک", 36.dp, onOpenSmsSetup) }
            }
        }

        SectionTitle("آخرین تراکنش‌های این حساب")
        LayerCard(radius = 20.dp, padding = 0.dp) {
            if (mine.isEmpty()) {
                Text("هنوز تراکنشی روی این حساب ثبت نشده است.", style = lt(13.sp, color = L.Muted), modifier = Modifier.padding(16.dp))
            } else {
                mine.take(4).forEachIndexed { i, tx ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenTransaction(tx) }.padding(horizontal = 14.dp).height(56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(tx.title, style = lt(14.sp, FontWeight.Medium), maxLines = 1)
                            Text("${weekdayName(tx.date)} ${timeOfDay(tx.date)} · ${tx.category}", style = lt(12.sp, color = L.Muted), maxLines = 1)
                        }
                        Text(if (tx.isTransfer) money(tx.amount) else signedMoney(tx.amount, tx.isExpense), style = lt(14.sp, FontWeight.SemiBold, txColor(tx, L.Muted)))
                    }
                    if (i < minOf(mine.size, 4) - 1) Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(L.Line))
                }
            }
        }

        // حذف
        Column(Modifier.padding(horizontal = 20.dp)) {
            if (askDelete) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(L.RedSoft).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "این حساب از تراز حذف می‌شود، اما تراکنش‌های ثبت‌شده با آن باقی می‌مانند.",
                        style = lt(13.sp, color = L.RedInk, lineHeight = 23.sp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(L.RedText).clickable(onClick = onDelete),
                            contentAlignment = Alignment.Center
                        ) { Text("بله، حذف شود", style = lt(14.sp, FontWeight.Bold, Color.White)) }
                        Box(
                            Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(L.Surface).clickable { askDelete = false }.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("انصراف", style = lt(14.sp, FontWeight.SemiBold)) }
                    }
                }
            } else {
                Box(
                    Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).clickable { askDelete = true },
                    contentAlignment = Alignment.Center
                ) { Text("حذف این حساب", style = lt(14.sp, FontWeight.SemiBold, L.RedText)) }
            }
        }
    }
}

@Composable
private fun ActionTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(56.dp).cardSurface(16.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = L.PrimaryText, modifier = Modifier.size(18.dp))
        Text(label, style = lt(12.sp, FontWeight.Bold, L.PrimaryText))
    }
}

/**
 * افزودن حساب — شیت طرح نهایی: نوع حساب، شماره کارت با تشخیص بانک، نام، موجودی و پیش‌نمایش.
 */
@Composable
fun LayerAddAccountSheet(
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, cardNumber: String, balance: Long, accountType: String, creditLimit: Long, dueDay: Int) -> Unit
) {
    val types = listOf(AccountType.BANK to "کارت بانکی", AccountType.CREDIT to "اعتباری", AccountType.CASH to "وجه نقد", AccountType.OTHER to "سایر")
    var limitText by remember { mutableStateOf("") }
    var dueDay by remember { mutableStateOf(1) }
    var typeIdx by remember { mutableStateOf(0) }
    var number by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var nameTouched by remember { mutableStateOf(false) }
    var balanceText by remember { mutableStateOf("") }
    val type = types[typeIdx].first
    val detected = remember(number) { BankBin.bankFor(number) }
    val detectedLogo = remember(number) { BankLogos.assetForCard(number) }

    LaunchedEffect(detected) {
        if (detected != null && !nameTouched && type == AccountType.BANK) name = detected
    }

    val cleanName = name.trim()
    val duplicate = existingNames.any { it.equals(cleanName, ignoreCase = true) }
    val credit = type == AccountType.CREDIT
    // در کارت اعتباری «موجودی» یعنی مبلغ استفاده‌شده، که منفی ذخیره می‌شود
    val balance = if (credit) -parseAmount(balanceText) else parseAmount(balanceText)
    val limit = parseAmount(limitText)
    val numberDigits = number.filter { it.isDigit() }
    val canSave = cleanName.isNotEmpty() && !duplicate && (type != AccountType.BANK || numberDigits.isEmpty() || numberDigits.length == 16) &&
        (!credit || limit > 0)

    LayerSheet(
        title = "حساب جدید",
        onDismiss = onDismiss,
        bottomBar = {
            BigButton("افزودن حساب", enabled = canSave) {
                onConfirm(cleanName, if (type == AccountType.BANK) numberDigits else "", balance, type, if (credit) limit else 0, if (credit) dueDay else 0)
            }
        }
    ) {
        LSegmented(types.map { it.second }, typeIdx, { typeIdx = it; if (it != 0) number = "" })

        if (type == AccountType.BANK) {
            LField(
                value = number,
                onChange = { number = it.filter { c -> c.isDigit() }.take(16) },
                label = "شمارهٔ کارت (اختیاری)",
                placeholder = "۶۱۰۴ ۳۳۷۴ …",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = CardNumberTransformation,
                ltr = true
            )
            if (detected != null) DetectedBankPill(detected, detectedLogo)
        }

        LField(
            value = name,
            onChange = { name = it; nameTouched = true },
            label = "نام حساب",
            placeholder = when (type) {
                AccountType.CASH -> "مثلاً صندوق نقدی"; AccountType.OTHER -> "مثلاً صندوق پس‌انداز"
                AccountType.CREDIT -> "مثلاً اعتباری ملت"; else -> "مثلاً بانک ملت"
            },
            hint = if (duplicate) "حسابی با این نام وجود دارد؛ نام دیگری وارد کنید." else "با همین نام در فهرست حساب‌ها نمایش داده می‌شود."
        )
        if (credit) {
            LField(
                value = limitText, onChange = { limitText = groupedInput(it) }, label = "سقف اعتبار (تومان)", placeholder = "۰",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        LField(
            value = balanceText,
            onChange = { balanceText = groupedInput(it) },
            label = if (credit) "مبلغ استفاده‌شده تاکنون (تومان)" else "موجودی فعلی (تومان)",
            placeholder = "۰",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            hint = if (credit) "خریدهای این کارت به بدهی آن افزوده می‌شود؛ بازپرداخت را با دکمهٔ «بازپرداخت» در صفحهٔ همین کارت ثبت کنید."
            else "تا موجودی کل از ابتدا صحیح باشد."
        )
        if (credit) CreditDueDay(dueDay) { dueDay = it }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(L.Bg).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("پیش‌نمایش", style = lt(12.sp, color = L.Muted))
            val previewCard = BankCard(bankName = cleanName.ifEmpty { "نام حساب" }, cardNumber = if (type == AccountType.BANK) numberDigits else "", balance = balance, accountType = type)
            BankChip(previewCard, if (type == AccountType.CASH) L.Green else L.Primary)
            Text(previewCard.bankName, style = lt(14.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f), maxLines = 1)
            Text(money(balance), style = lt(14.sp, FontWeight.SemiBold))
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** روز بازپرداخت کارت اعتباری در هر ماه */
@Composable
private fun CreditDueDay(day: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(L.Bg).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("روز بازپرداخت هر ماه", style = lt(13.sp, color = L.Muted), modifier = Modifier.weight(1f))
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(L.Surface).clickable { onChange(if (day <= 1) 30 else day - 1) },
            contentAlignment = Alignment.Center
        ) { Text("−", style = lt(16.sp, FontWeight.Bold)) }
        Text(fa(day), style = lt(16.sp, FontWeight.Bold), modifier = Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(L.Surface).clickable { onChange(if (day >= 30) 1 else day + 1) },
            contentAlignment = Alignment.Center
        ) { Text("+", style = lt(16.sp, FontWeight.Bold)) }
    }
}

/** «۱۲م از ۵۰م استفاده شده» برای کارت اعتباری */
internal fun creditUsage(card: BankCard): String {
    val used = (-card.balance).coerceAtLeast(0)
    return "${millions(used)} از ${millions(card.creditLimit)} استفاده شده" +
        if (card.dueDay > 0) " · بازپرداخت روز ${fa(card.dueDay)}" else ""
}

/** ویرایش حساب: نام (تراکنش‌ها هم با آن منتقل می‌شوند)، موجودی، شمارهٔ کارت و صاحب کارت */
@Composable
fun LayerEditAccountSheet(
    card: BankCard,
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (BankCard, String) -> Unit
) {
    var name by remember { mutableStateOf(card.bankName) }
    val cleanName = name.trim()
    val nameTaken = !cleanName.equals(card.bankName, ignoreCase = true) &&
        existingNames.any { it.equals(cleanName, ignoreCase = true) }
    val credit = card.isCredit
    var balanceText by remember { mutableStateOf(money(if (credit) -card.balance else card.balance)) }
    var limitText by remember { mutableStateOf(money(card.creditLimit)) }
    var dueDay by remember { mutableStateOf(card.dueDay.coerceIn(1, 30)) }
    var holder by remember { mutableStateOf(card.cardHolderName) }
    var number by remember { mutableStateOf(card.cardNumber) }
    val digits = number.filter { it.isDigit() }
    val canSave = cleanName.isNotEmpty() && !nameTaken &&
        !(card.accountType == AccountType.BANK && digits.isNotEmpty() && digits.length != 16)

    LayerSheet(
        title = "ویرایش ${card.bankName}",
        onDismiss = onDismiss,
        bottomBar = {
            BigButton("ذخیره", enabled = canSave) {
                onSave(card.copy(
                    balance = if (credit) -parseAmount(balanceText) else parseAmount(balanceText),
                    creditLimit = if (credit) parseAmount(limitText) else card.creditLimit,
                    dueDay = if (credit) dueDay else card.dueDay,
                    cardHolderName = holder.trim().ifEmpty { card.cardHolderName }, cardNumber = if (card.isCash) "" else digits), cleanName)
            }
        }
    ) {
        LField(
            value = name, onChange = { name = it }, label = "نام حساب",
            hint = if (nameTaken) "حساب دیگری با این نام وجود دارد." else "تراکنش‌های این حساب نیز با نام جدید نمایش داده می‌شوند."
        )
        LField(
            value = balanceText, onChange = { balanceText = groupedInput(it) }, label = if (credit) "استفاده‌شده (تومان)" else "موجودی (تومان)",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            hint = "در صورت مغایرت موجودی واقعی با تراز، آن را در این بخش اصلاح کنید."
        )
        if (credit) {
            LField(
                value = limitText, onChange = { limitText = groupedInput(it) }, label = "سقف اعتبار (تومان)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            CreditDueDay(dueDay) { dueDay = it }
        }
        if (!card.isCash) {
            LField(
                value = number, onChange = { number = it.filter { c -> c.isDigit() }.take(16) }, label = "شمارهٔ کارت (اختیاری)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), visualTransformation = CardNumberTransformation,
                ltr = true
            )
            val detected = remember(number) { BankBin.bankFor(number) }
            if (detected != null) DetectedBankPill(detected, remember(number) { BankLogos.assetForCard(number) })
            LField(value = holder, onChange = { holder = it }, label = "نام صاحب کارت")
        }
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * بازپرداخت کارت اعتباری: از یک حساب دیگر به این کارت منتقل می‌شود (در جمع هزینه نمی‌آید،
 * چون خریدها قبلاً هزینه شده‌اند) و بدهی کارت کم می‌شود.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun RepayCreditSheet(card: BankCard, cards: List<BankCard>, onDismiss: () -> Unit, onRepay: (from: String, amount: Long) -> Unit) {
    val used = (-card.balance).coerceAtLeast(0)
    val sources = cards.filter { !it.isCredit && it.bankName != card.bankName }
    var from by remember { mutableStateOf(sources.maxByOrNull { it.balance }?.bankName.orEmpty()) }
    var amountText by remember { mutableStateOf(if (used > 0) money(used) else "") }
    val amount = parseAmount(amountText)
    val src = sources.firstOrNull { it.bankName == from }
    LayerSheet(
        title = "بازپرداخت ${card.bankName}",
        onDismiss = onDismiss,
        topGap = 120.dp,
        bottomBar = {
            BigButton("ثبت بازپرداخت", enabled = amount > 0 && from.isNotBlank()) { onRepay(from, amount); onDismiss() }
        }
    ) {
        Text("بدهی این کارت: ${money(used)} تومان", style = lt(14.sp, FontWeight.SemiBold))
        LField(amountText, { amountText = groupedInput(it) }, "مبلغ بازپرداخت (تومان)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            hint = if (src != null && amount > src.balance) "موجودی ${src.bankName} کمتر از این مبلغ است." else null)
        if (used > 0 && amount != used) SoftButton("کل بدهی: ${money(used)}", height = 34.dp) { amountText = money(used) }
        Text("از حساب", style = lt(13.sp, FontWeight.Bold))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sources.forEach { c -> LChip(c.bankName, c.bankName == from) { from = c.bankName } }
            AddAccountChip()
        }
        WatchNew(cards, { it.id }) { if (!it.isCredit) from = it.bankName }
        Text("در هزینه‌ها منظور نمی‌شود؛ خریدهای کارت پیش‌تر به‌عنوان هزینه ثبت شده‌اند.", style = lt(12.sp, color = L.Muted, lineHeight = 20.sp))
    }
}
