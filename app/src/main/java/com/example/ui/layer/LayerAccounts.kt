package com.example.ui.layer

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
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
import com.example.data.BankSender
import com.example.data.Category
import com.example.data.Transaction
import com.example.data.isCash
import com.example.ui.CardNumberTransformation
import com.example.utils.JalaliDate

private fun hasAutoSms(card: BankCard, senders: List<BankSender>): Boolean =
    senders.any { it.isEnabled && (it.bankName.contains(card.bankName) || card.bankName.contains(it.bankName)) }

private fun senderFor(card: BankCard, senders: List<BankSender>): BankSender? =
    senders.firstOrNull { it.bankName.contains(card.bankName) || card.bankName.contains(it.bankName) }

/** نشان کوچک رنگی بانک */
@Composable
private fun BankChip(card: BankCard, color: Color, w: Int = 54, h: Int = 36) {
    Box(
        Modifier.width(w.dp).height(h.dp).clip(RoundedCornerShape(8.dp)).background(if (card.isCash) L.GreenSoft else color),
        contentAlignment = if (card.isCash) Alignment.Center else Alignment.BottomStart
    ) {
        if (card.isCash) Icon(Icons.Default.Payments, null, tint = L.GreenText, modifier = Modifier.size(20.dp))
        else Text(card.bankName.removePrefix("بانک ").take(6), style = lt(12.sp, FontWeight.Bold, Color.White), modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), maxLines = 1)
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
    onTransfer: () -> Unit
) {
    LayerScreen(
        title = "حساب‌ها",
        subtitle = if (cards.isEmpty()) "هنوز حسابی ندارید" else "${fa(cards.size)} حساب · روی هر حساب بزنید تا جزئیاتش را ببینید",
        onBack = onBack,
        bottomBar = {
            BigButton("افزودن حساب", onClick = onAdd)
            if (cards.size >= 2) SoftBigButton("انتقال بین حساب‌ها", onClick = onTransfer)
        }
    ) {
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("موجودی کل", style = lt(13.sp, color = L.Muted))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(money(totalBalance), style = lt(38.sp, FontWeight.Light, letterSpacing = (-1.5).sp))
                Text("تومان", style = lt(14.sp), modifier = Modifier.padding(bottom = 6.dp))
            }
        }
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
                        if (hasAutoSms(card, senders)) StatusPill("پیامک خودکار روشن", L.GreenSoft, L.GreenText)
                        else StatusPill("ثبت دستی", L.Chip, L.Muted)
                    }
                    Text(money(card.balance), style = lt(15.sp, FontWeight.SemiBold))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = Color(0xFF8E95A2), modifier = Modifier.size(18.dp))
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
    onDelete: () -> Unit
) {
    val color = if (card.isCash) L.Green else cardColor(cardIndex)
    val mine = transactions.filter { it.bankName == card.bankName }.sortedByDescending { it.date }
    val monthStart = JalaliDate.monthRange(System.currentTimeMillis()).first
    val monthCount = mine.count { it.date >= monthStart }
    val sender = senderFor(card, senders)
    val smsOn = sender?.isEnabled == true
    var askDelete by remember { mutableStateOf(false) }

    LayerScreen(
        title = card.bankName,
        subtitle = "${if (card.isCash) "حساب نقدی" else "حساب"} · ${fa(monthCount)} تراکنش در ${JalaliDate.today().monthName}",
        onBack = onBack
    ) {
        // کارت
        Box(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(178.dp).clip(RoundedCornerShape(24.dp)).background(color).padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Column(Modifier.align(Alignment.TopStart), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("موجودی", style = lt(12.sp, color = Color(0xD9FFFFFF)))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(money(card.balance), style = lt(32.sp, FontWeight.Light, Color.White, letterSpacing = (-1).sp))
                    Text("تومان", style = lt(13.sp, color = Color.White), modifier = Modifier.padding(bottom = 5.dp))
                }
            }
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(holderName.ifBlank { card.cardHolderName }, style = lt(13.sp, color = Color.White), modifier = Modifier.weight(1f))
                if (card.cardNumber.length >= 4) {
                    Text("•••• •••• •••• ${fa(card.cardNumber.takeLast(4))}", style = lt(14.sp, color = Color.White, letterSpacing = 1.sp))
                }
            }
        }

        // عملیات
        Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile("ویرایش", Icons.Default.Edit, Modifier.weight(1f), onEdit)
            if (cards.size >= 2) ActionTile("انتقال", Icons.Default.SwapHoriz, Modifier.weight(1f), onTransfer)
            ActionTile("تراکنش‌ها", Icons.Default.List, Modifier.weight(1f), onOpenTransactions)
        }

        // پیامک
        if (!card.isCash) {
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
                Text("هنوز تراکنشی روی این حساب ثبت نشده.", style = lt(13.sp, color = L.Muted), modifier = Modifier.padding(16.dp))
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
                        Text(signedMoney(tx.amount, tx.isExpense), style = lt(14.sp, FontWeight.SemiBold, if (tx.isExpense) L.Ink else L.Green))
                    }
                    if (i < minOf(mine.size, 4) - 1) Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(L.Line))
                }
            }
        }

        // حذف
        Column(Modifier.padding(horizontal = 20.dp)) {
            if (askDelete) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFFFFF1F1)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "این حساب از تراز حذف می‌شود، ولی تراکنش‌هایی که با آن ثبت کرده‌اید باقی می‌مانند.",
                        style = lt(13.sp, color = Color(0xFF7A1D22), lineHeight = 23.sp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(L.RedText).clickable(onClick = onDelete),
                            contentAlignment = Alignment.Center
                        ) { Text("بله، حذف شود", style = lt(14.sp, FontWeight.Bold, Color.White)) }
                        Box(
                            Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).clickable { askDelete = false }.padding(horizontal = 16.dp),
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
    onConfirm: (name: String, cardNumber: String, balance: Long, accountType: String) -> Unit
) {
    val types = listOf(AccountType.BANK to "کارت بانکی", AccountType.CASH to "پول نقد", AccountType.OTHER to "دیگر")
    var typeIdx by remember { mutableStateOf(0) }
    var number by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var nameTouched by remember { mutableStateOf(false) }
    var balanceText by remember { mutableStateOf("") }
    val type = types[typeIdx].first
    val detected = remember(number) { BankBin.bankFor(number) }

    LaunchedEffect(detected) {
        if (detected != null && !nameTouched && type == AccountType.BANK) name = detected
    }

    val cleanName = name.trim()
    val duplicate = existingNames.any { it.equals(cleanName, ignoreCase = true) }
    val balance = parseAmount(balanceText)
    val numberDigits = number.filter { it.isDigit() }
    val canSave = cleanName.isNotEmpty() && !duplicate && (type != AccountType.BANK || numberDigits.isEmpty() || numberDigits.length == 16)

    LayerSheet(
        title = "حساب جدید",
        onDismiss = onDismiss,
        bottomBar = {
            BigButton("افزودن حساب", enabled = canSave) {
                onConfirm(cleanName, if (type == AccountType.BANK) numberDigits else "", balance, type)
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
                visualTransformation = CardNumberTransformation
            )
            if (detected != null) {
                Row(
                    Modifier.clip(PillShape).background(L.GreenSoft).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Check, null, tint = L.GreenText, modifier = Modifier.size(14.dp))
                    Text("شناسایی شد: $detected", style = lt(12.sp, FontWeight.Bold, L.GreenText))
                }
            }
        }

        LField(
            value = name,
            onChange = { name = it; nameTouched = true },
            label = "نام حساب",
            placeholder = when (type) { AccountType.CASH -> "مثلاً کیف پول"; AccountType.OTHER -> "مثلاً صندوق پس‌انداز"; else -> "مثلاً بانک ملت" },
            hint = if (duplicate) "حسابی با این نام دارید؛ نام دیگری بنویسید." else "در فهرست حساب‌ها با همین نام دیده می‌شود."
        )
        LField(
            value = balanceText,
            onChange = { balanceText = groupedInput(it) },
            label = "موجودی فعلی (تومان)",
            placeholder = "۰",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            hint = "تا موجودی کل از همین حالا درست باشد."
        )

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(L.Bg).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("پیش‌نمایش", style = lt(12.sp, color = L.Muted))
            val previewCard = BankCard(bankName = cleanName.ifEmpty { "نام حساب" }, balance = balance, accountType = type)
            BankChip(previewCard, if (type == AccountType.CASH) L.Green else L.Primary)
            Text(previewCard.bankName, style = lt(14.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f), maxLines = 1)
            Text(money(balance), style = lt(14.sp, FontWeight.SemiBold))
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** ویرایش حساب: موجودی و شمارهٔ کارت (نام ثابت می‌ماند چون تراکنش‌ها با آن به حساب وصل‌اند) */
@Composable
fun LayerEditAccountSheet(
    card: BankCard,
    onDismiss: () -> Unit,
    onSave: (BankCard) -> Unit
) {
    var balanceText by remember { mutableStateOf(money(card.balance)) }
    var holder by remember { mutableStateOf(card.cardHolderName) }
    var number by remember { mutableStateOf(card.cardNumber) }
    val digits = number.filter { it.isDigit() }
    val canSave = !(card.accountType == AccountType.BANK && digits.isNotEmpty() && digits.length != 16)

    LayerSheet(
        title = "ویرایش ${card.bankName}",
        onDismiss = onDismiss,
        bottomBar = {
            BigButton("ذخیره", enabled = canSave) {
                onSave(card.copy(balance = parseAmount(balanceText), cardHolderName = holder.trim().ifEmpty { card.cardHolderName }, cardNumber = if (card.isCash) "" else digits))
            }
        }
    ) {
        LField(
            value = balanceText, onChange = { balanceText = groupedInput(it) }, label = "موجودی (تومان)",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            hint = "اگر موجودی واقعی با تراز فرق دارد، اینجا درستش کنید."
        )
        if (!card.isCash) {
            LField(
                value = number, onChange = { number = it.filter { c -> c.isDigit() }.take(16) }, label = "شمارهٔ کارت (اختیاری)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), visualTransformation = CardNumberTransformation
            )
            LField(value = holder, onChange = { holder = it }, label = "نام صاحب کارت")
        }
        Spacer(Modifier.height(4.dp))
    }
}
