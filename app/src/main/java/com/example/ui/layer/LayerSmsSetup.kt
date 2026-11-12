package com.example.ui.layer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BankBin
import com.example.data.SmsIngest
import com.example.data.isCash
import com.example.sms.data.scanner.InboxMessage
import com.example.sms.permission.SmsPermissionHelper
import com.example.sms.presentation.SmsParserViewModel
import com.example.ui.FinanceViewModel
import com.example.utils.JalaliDate
import kotlinx.coroutines.launch

// ───────────────────────── مدل‌های صفحه (بدون وابستگی به اندروید، برای تصویر و تست) ─────────────────────────

internal data class SmsCardUi(val bankName: String, val last4: String)

internal data class SmsSenderUi(
    val number: String,
    /** نام بانک؛ خالی وقتی فرستنده بانک شناخته نشده (آن‌وقت خود سرشماره نشان داده می‌شود). */
    val bankName: String,
    val count: Int,
    val sample: String,
    val looksBank: Boolean,
    /** چند پیامک این فرستنده شکل تراکنش بانکی دارند. */
    val txCount: Int = 0
)

internal data class SmsWizardState(
    /** ۰ روشن‌کردن، ۱ کارت، ۲ سرشماره، ۳ آماده */
    val step: Int,
    val permissionGranted: Boolean,
    val readerOn: Boolean,
    val cards: List<SmsCardUi>,
    val cardNumber: String,
    val cardBankGuess: String?,
    val cardName: String,
    val senders: List<SmsSenderUi>,
    val sendersLoading: Boolean,
    val showAllSenders: Boolean,
    val selectedNumber: String?,
    val tab: Int,
    val manualNumber: String,
    val manualBank: String,
    val error: String?,
    val summaryCard: String,
    val summarySender: String,
    val pendingCount: Int
)

internal class SmsWizardActions(
    val onBack: () -> Unit = {},
    val onToggleReader: (Boolean) -> Unit = {},
    val onContinueFromOn: () -> Unit = {},
    val onSkipAll: () -> Unit = {},
    val onCardNumber: (String) -> Unit = {},
    val onCardName: (String) -> Unit = {},
    val onSaveCard: () -> Unit = {},
    val onSkipCard: () -> Unit = {},
    val onTab: (Int) -> Unit = {},
    val onSelectSender: (String) -> Unit = {},
    val onToggleShowAll: () -> Unit = {},
    val onManualNumber: (String) -> Unit = {},
    val onManualBank: (String) -> Unit = {},
    val onSaveSender: () -> Unit = {},
    val onReview: () -> Unit = {},
    val onFinish: () -> Unit = {}
)

// ───────────────────────── اجزای مشترک ─────────────────────────

@Composable
private fun SmsStepper(step: Int) {
    val labels = listOf("روشن‌کردن", "کارت", "سرشماره")
    Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, label ->
            val done = i < step
            val current = i == step
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(if (done) L.Green else if (current) L.Primary else L.Rail))
                Text(
                    (if (done) "✓ " else "${fa(i + 1)} ") + label,
                    style = lt(12.sp, if (current) FontWeight.Bold else FontWeight.Normal, if (done) L.GreenText else if (current) L.PrimaryText else L.Faint)
                )
            }
        }
    }
}

@Composable
private fun Assurance(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.Check, null, tint = L.Green, modifier = Modifier.padding(top = 3.dp).size(18.dp))
        Text(text, style = lt(14.sp, lineHeight = 25.sp), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Hero(title: String, body: String) {
    LayerCard(radius = 24.dp, padding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(L.PrimarySoft), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Sms, null, tint = L.PrimaryText, modifier = Modifier.size(28.dp))
            }
            Text(title, style = lt(21.sp, FontWeight.ExtraBold, lineHeight = 30.sp))
            Text(body, style = lt(14.sp, color = L.Muted, lineHeight = 25.sp))
        }
    }
}

@Composable
private fun BankDot(bankName: String, size: androidx.compose.ui.unit.Dp = 36.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(11.dp)).background(bankColor(bankName)), contentAlignment = Alignment.Center) {
        Text(bankName.removePrefix("بانک").trim().take(1).ifEmpty { "ب" }, style = lt(14.sp, FontWeight.ExtraBold, Color.White))
    }
}

// ───────────────────────── صفحهٔ مرحله‌ای ─────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SmsWizardScreen(s: SmsWizardState, a: SmsWizardActions) {
    LayerScreen(
        title = "پیامک‌های بانکی",
        subtitle = "ثبت خودکار تراکنش از پیامک",
        onBack = a.onBack,
        bottomBar = {
            when (s.step) {
                0 -> {
                    BigButton(if (s.permissionGranted) "ادامه" else "اجازه می‌دهم و ادامه") { a.onContinueFromOn() }
                    Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = a.onSkipAll), contentAlignment = Alignment.Center) {
                        Text("فعلاً نه", style = lt(14.sp, FontWeight.Bold, L.PrimaryText))
                    }
                }
                1 -> {
                    BigButton(if (s.cardNumber.isNotBlank()) "ذخیرهٔ کارت و ادامه" else "ادامه") {
                        if (s.cardNumber.isNotBlank()) a.onSaveCard() else a.onSkipCard()
                    }
                    if (s.cards.isEmpty()) Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = a.onSkipCard), contentAlignment = Alignment.Center) {
                        Text("بعداً اضافه می‌کنم", style = lt(14.sp, FontWeight.Bold, L.PrimaryText))
                    }
                }
                2 -> BigButton(
                    "ثبت این سرشماره",
                    enabled = if (s.tab == 0) s.selectedNumber != null else s.manualNumber.isNotBlank()
                ) { a.onSaveSender() }
                else -> {
                    BigButton(if (s.pendingCount > 0) "بررسی پیامک‌ها" else "پایان") { if (s.pendingCount > 0) a.onReview() else a.onFinish() }
                    if (s.pendingCount > 0) Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = a.onFinish), contentAlignment = Alignment.Center) {
                        Text("پایان", style = lt(14.sp, FontWeight.Bold, L.PrimaryText))
                    }
                }
            }
        }
    ) {
        if (s.step in 0..2) SmsStepper(s.step)
        when (s.step) {
            0 -> StepOn(s, a)
            1 -> StepCard(s, a)
            2 -> StepSender(s, a)
            else -> StepDone(s)
        }
    }
}

@Composable
private fun StepOn(s: SmsWizardState, a: SmsWizardActions) {
    Hero("پیامک‌خوان را روشن کنید", "تراز پیامک بانک را می‌خواند و برداشت و واریز را برای تأیید شما پیشنهاد می‌دهد.")
    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Assurance("فقط پیامک بانک‌هایی که خودتان انتخاب می‌کنید")
        Assurance("پردازش فقط روی همین گوشی؛ هیچ پیامکی ارسال نمی‌شود")
        Assurance("بدون تأیید شما هیچ تراکنشی ثبت نمی‌شود")
        Assurance("رمز یک‌بارمصرف بانک نادیده گرفته می‌شود")
    }
    LayerCard(radius = 18.dp, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("پیامک‌خوان", style = lt(15.sp, FontWeight.Bold))
                Text(
                    if (s.permissionGranted) "اجازهٔ پیامک داده شده است" else "برای روشن‌کردن، اجازهٔ پیامک لازم است",
                    style = lt(12.sp, color = L.Muted)
                )
            }
            LSwitch(s.readerOn, "پیامک‌خوان", onChange = a.onToggleReader)
        }
    }
}

@Composable
private fun StepCard(s: SmsWizardState, a: SmsWizardActions) {
    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("کارت شما", style = lt(21.sp, FontWeight.ExtraBold))
        Text("با شمارهٔ کارت، تراز می‌داند هر پیامک مال کدام حساب است.", style = lt(14.sp, color = L.Muted, lineHeight = 25.sp))
    }
    if (s.cards.isNotEmpty()) {
        LayerCard(radius = 18.dp, padding = 0.dp) {
            Column(Modifier.padding(horizontal = 14.dp)) {
                s.cards.forEachIndexed { i, c ->
                    LRow(
                        c.bankName, maskedCard(c.last4),
                        divider = i < s.cards.size - 1,
                        leading = { BankDot(c.bankName) },
                        trailing = { StatusPill("ثبت شد", L.GreenSoft, L.GreenText) }
                    )
                }
            }
        }
    }
    LayerCard(radius = 22.dp, padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LField(
                value = s.cardNumber, onChange = a.onCardNumber, label = "شمارهٔ کارت",
                placeholder = "6037 9971 2345 6789",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            if (s.cardBankGuess != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(L.PrimarySoft).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BankDot(s.cardBankGuess, 32.dp)
                    Column(Modifier.weight(1f)) {
                        Text(s.cardBankGuess, style = lt(14.sp, FontWeight.Bold))
                        Text("از روی شمارهٔ کارت تشخیص داده شد", style = lt(12.sp, color = L.Muted))
                    }
                }
            } else if (s.cardNumber.filter { it.isDigit() }.length >= 6) {
                LField(value = s.cardName, onChange = a.onCardName, label = "نام بانک", placeholder = "مثلاً بانک ملی")
            }
            Text("شمارهٔ کارت فقط روی همین گوشی ذخیره می‌شود.", style = lt(12.sp, color = L.Faint, lineHeight = 22.sp))
            if (s.error != null) Text(s.error, style = lt(12.sp, FontWeight.SemiBold, L.RedText))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepSender(s: SmsWizardState, a: SmsWizardActions) {
    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("سرشمارهٔ بانک", style = lt(21.sp, FontWeight.ExtraBold))
        Text("پیامک‌های کدام شماره خوانده شود؟ یکی از پیام‌های بانکی خود را انتخاب کنید.", style = lt(14.sp, color = L.Muted, lineHeight = 25.sp))
    }
    Box(Modifier.padding(horizontal = 20.dp)) { LSegmented(listOf("از میان پیام‌ها", "وارد کردن شماره"), s.tab, a.onTab) }

    if (s.tab == 0) {
        when {
            !s.permissionGranted -> Text("برای دیدن پیام‌ها، اول اجازهٔ پیامک را بدهید.", style = lt(13.sp, color = L.Muted), modifier = Modifier.padding(horizontal = 24.dp))
            s.sendersLoading -> Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), color = L.Primary, strokeWidth = 2.dp)
                Text("در حال خواندن پیام‌ها…", style = lt(13.sp, color = L.Muted))
            }
            s.senders.isEmpty() -> Text("پیام بانکی‌ای روی گوشی پیدا نشد. شماره را خودتان وارد کنید.", style = lt(13.sp, color = L.Muted), modifier = Modifier.padding(horizontal = 24.dp))
            else -> s.senders.forEach { sender ->
                val selected = sender.number == s.selectedNumber
                val preview = remember(sender.sample, sender.bankName, sender.number) { SmsIngest.preview(sender.sample, sender.bankName, sender.number) }
                Column(
                    Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(L.Surface)
                        .border(if (selected) 1.5.dp else 1.dp, if (selected) L.Primary else L.Rail, RoundedCornerShape(20.dp))
                        .clickable { a.onSelectSender(sender.number) }
                        .semantics { role = Role.RadioButton; stateDescription = if (selected) "انتخاب شده" else "انتخاب نشده"; contentDescription = sender.bankName.ifEmpty { sender.number } }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (sender.bankName.isNotEmpty()) BankDot(sender.bankName) else SenderDot()
                        Column(Modifier.weight(1f)) {
                            // فرستندهٔ ناشناخته با خود سرشماره نشان داده می‌شود، نه «بانک نامشخص».
                            Text(sender.bankName.ifEmpty { sender.number }, style = lt(15.sp, FontWeight.Bold), maxLines = 1)
                            Text(senderLine(sender), style = lt(12.sp, color = L.Muted), maxLines = 1)
                        }
                        Box(
                            Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(if (selected) L.Primary else L.Surface)
                                .border(2.dp, if (selected) L.Primary else L.Chevron, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) { if (selected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(15.dp)) }
                    }
                    if (selected) {
                        Text(
                            sender.sample, style = lt(12.sp, color = L.Muted, lineHeight = 22.sp), maxLines = 4,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(L.Bg).padding(10.dp)
                        )
                        if (preview != null) {
                            Text("تراز این پیامک را این‌طور فهمید", style = lt(12.sp, FontWeight.Bold, L.PrimaryText))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusPill(
                                    (if (preview.isExpense) "هزینه · " else "درآمد · ") + money(preview.amount) + " تومان",
                                    if (preview.isExpense) L.AmberSoft else L.GreenSoft,
                                    if (preview.isExpense) L.AmberText else L.GreenText
                                )
                                if (preview.balance > 0) StatusPill("مانده ${money(preview.balance)}", L.Chip, L.Ink)
                                if (preview.card.isNotEmpty()) StatusPill("کارت ${fa(preview.card)}", L.Chip, L.Ink)
                            }
                        } else {
                            Text("این پیامک تراکنش به نظر نمی‌رسد؛ پیامک دیگری از همین شماره را انتخاب کنید یا همین را ثبت کنید.", style = lt(12.sp, color = L.Muted, lineHeight = 21.sp))
                        }
                    }
                }
            }
        }
        if (s.permissionGranted && !s.sendersLoading) {
            Box(
                Modifier.padding(horizontal = 20.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = a.onToggleShowAll).padding(horizontal = 4.dp, vertical = 8.dp)
            ) { Text(if (s.showAllSenders) "فقط فرستنده‌های بانکی" else "نمایش همهٔ فرستنده‌ها", style = lt(13.sp, FontWeight.Bold, L.PrimaryText)) }
        }
    } else {
        LayerCard(radius = 22.dp, padding = 16.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LField(
                    value = s.manualNumber, onChange = a.onManualNumber, label = "سرشماره",
                    placeholder = "مثلاً 98 1000 1234 یا Melli", singleLine = true, ltr = true,
                    hint = "همان شماره یا نامی که بالای پیامک بانک نوشته می‌شود."
                )
                LField(value = s.manualBank, onChange = a.onManualBank, label = "نام بانک", placeholder = "مثلاً بانک ملی")
                if (s.error != null) Text(s.error, style = lt(12.sp, FontWeight.SemiBold, L.RedText))
            }
        }
    }
}

@Composable
private fun StepDone(s: SmsWizardState) {
    Column(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(92.dp).clip(CircleShape).background(L.GreenSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Check, null, tint = L.Green, modifier = Modifier.size(44.dp))
        }
        Text("پیامک‌خوان روشن شد", style = lt(22.sp, FontWeight.ExtraBold))
        Text("از این پس، پیامک‌های بانک برای تأیید شما اینجا می‌آیند.", style = lt(14.sp, color = L.Muted, lineHeight = 25.sp), modifier = Modifier.padding(horizontal = 32.dp))
    }
    LayerCard(radius = 22.dp, padding = 0.dp) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            LRow("کارت", null, trailing = { Text(s.summaryCard.ifBlank { "—" }, style = lt(14.sp, FontWeight.Bold)) })
            LRow("سرشماره", null, trailing = { Text(fa(s.summarySender), style = lt(14.sp, FontWeight.Bold)) })
            LRow(
                "پیامک‌های منتظر تأیید", null, divider = false,
                trailing = { Text(if (s.pendingCount > 0) "${fa(s.pendingCount)} پیامک" else "هنوز نیست", style = lt(14.sp, FontWeight.ExtraBold, L.AmberText)) }
            )
        }
    }
    Text(
        "هر وقت خواستید، از «تنظیمات ← پیامک‌های بانکی» خاموشش کنید.",
        style = lt(12.sp, color = L.Faint, lineHeight = 22.sp), modifier = Modifier.padding(horizontal = 28.dp)
    )
}

// ───────────────────────── مدیریت (بعد از راه‌اندازی) ─────────────────────────

internal data class SmsManagedSender(val number: String, val bankName: String, val enabled: Boolean)

@Composable
internal fun SmsManageScreen(
    readerOn: Boolean,
    permissionGranted: Boolean,
    senders: List<SmsManagedSender>,
    cards: List<SmsCardUi>,
    pendingCount: Int,
    unreadCount: Int,
    onBack: () -> Unit,
    onToggleReader: (Boolean) -> Unit,
    onToggleSender: (String, Boolean) -> Unit,
    onRemoveSender: (String) -> Unit,
    onAddSender: () -> Unit,
    onAddCard: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenUnread: () -> Unit,
    /** «وضعیت دریافت پیامک» ([SmsHealthSection])؛ در تصویرهای آزمون خالی می‌ماند. */
    healthSection: (@Composable () -> Unit)? = null
) {
    LayerScreen(title = "پیامک‌های بانکی", subtitle = "ثبت خودکار تراکنش از پیامک", onBack = onBack) {
        LayerCard(radius = 18.dp, padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("پیامک‌خوان", style = lt(15.sp, FontWeight.Bold))
                    Text(
                        when {
                            !permissionGranted -> "اجازهٔ پیامک داده نشده است"
                            readerOn -> "روشن است · فقط روی همین گوشی"
                            else -> "خاموش است"
                        },
                        style = lt(12.sp, color = L.Muted)
                    )
                }
                LSwitch(readerOn, "پیامک‌خوان", onChange = onToggleReader)
            }
        }
        if (pendingCount > 0) Callout(
            title = "${fa(pendingCount)} پیامک منتظر تأیید شماست",
            body = "تا تأیید نکنید، در حساب‌ها ثبت نمی‌شوند.", bg = L.AmberSoft, titleColor = L.AmberInk,
            trailing = { SmallAction("بررسی", onOpenReview) }
        )
        if (unreadCount > 0) Callout(
            title = "${fa(unreadCount)} پیامک خوانده نشد",
            body = "قالب این پیامک‌ها را نمی‌شناسم؛ خودتان ثبتشان کنید.", bg = L.PrimarySoft, titleColor = L.PrimaryText,
            trailing = { SmallAction("دیدن", onOpenUnread) }
        )

        if (readerOn && permissionGranted) healthSection?.invoke()

        SectionTitle("سرشماره‌ها")
        LayerCard(radius = 20.dp, padding = 0.dp) {
            Column(Modifier.padding(horizontal = 14.dp)) {
                if (senders.isEmpty()) Text("هنوز سرشماره‌ای ثبت نشده است.", style = lt(13.sp, color = L.Muted), modifier = Modifier.padding(vertical = 16.dp))
                senders.forEachIndexed { i, s ->
                    LRow(
                        s.bankName, fa(s.number), divider = i < senders.size - 1,
                        leading = { BankDot(s.bankName) },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    Modifier.height(32.dp).clip(RoundedCornerShape(10.dp)).clickable { onRemoveSender(s.number) }.padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text("حذف", style = lt(12.sp, FontWeight.Bold, L.RedText)) }
                                LSwitch(s.enabled, s.bankName, onChange = { onToggleSender(s.number, it) })
                            }
                        }
                    )
                }
            }
        }
        Box(Modifier.padding(horizontal = 20.dp)) { SoftBigButton("+ افزودن سرشماره", onClick = onAddSender) }

        SectionTitle("کارت‌ها")
        LayerCard(radius = 20.dp, padding = 0.dp) {
            Column(Modifier.padding(horizontal = 14.dp)) {
                if (cards.isEmpty()) Text("کارتی با شمارهٔ کارت ثبت نشده است.", style = lt(13.sp, color = L.Muted), modifier = Modifier.padding(vertical = 16.dp))
                cards.forEachIndexed { i, c ->
                    LRow(c.bankName, maskedCard(c.last4), divider = i < cards.size - 1, leading = { BankDot(c.bankName) })
                }
            }
        }
        Box(Modifier.padding(horizontal = 20.dp)) { SoftBigButton("+ کارت جدید", onClick = onAddCard) }
    }
}

@Composable
private fun SmallAction(text: String, onClick: () -> Unit) {
    Box(
        Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(L.Surface).clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, style = lt(13.sp, FontWeight.Bold, L.PrimaryText)) }
}

// ───────────────────────── کنترل‌کنندهٔ زنده ─────────────────────────

/** شمارهٔ کارت همیشه از چپ به راست دیده می‌شود: •••• •••• •••• ۱۲۳۴ */
private fun maskedCard(last4: String): String = "\u202A•••• •••• •••• ${fa(last4)}\u202C"

private fun groupCardNumber(raw: String): String =
    SmsIngest.latinDigits(raw).filter { it.isDigit() }.take(16).chunked(4).joinToString(" ")

@Composable
fun LayerSmsSetupScreen(
    viewModel: FinanceViewModel,
    onBack: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenUnread: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val smsVm: SmsParserViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val creators = LocalCreators.current

    val readerEnabled by smsVm.readerEnabled.collectAsState()
    val saved by smsVm.savedSelectedSenders.collectAsState()
    val messages by smsVm.recentMessages.collectAsState()
    val pending by smsVm.pendingTransactions.collectAsState()
    val unknown by smsVm.unknownSmsList.collectAsState()
    val allCards by viewModel.cards.collectAsState()

    var hasPermission by remember { mutableStateOf(SmsPermissionHelper.hasSmsPermissions(context)) }
    // اگر قبلاً راه‌اندازی شده، مستقیم صفحهٔ مدیریت؛ وگرنه مرحله‌ها
    var managing by rememberSaveable { mutableStateOf(readerEnabled && hasPermission) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { p ->
        hasPermission = (p[android.Manifest.permission.READ_SMS] ?: false) && (p[android.Manifest.permission.RECEIVE_SMS] ?: false)
        if (hasPermission) {
            smsVm.setReaderEnabled(true)
            if (!managing && step == 0) step = 1
        }
    }

    var cardNumber by rememberSaveable { mutableStateOf("") }
    var cardName by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var manualNumber by rememberSaveable { mutableStateOf("") }
    var manualBank by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var lastSender by rememberSaveable { mutableStateOf("") }
    var lastCard by rememberSaveable { mutableStateOf("") }
    var standalone by rememberSaveable { mutableStateOf(false) }

    val cardsUi = remember(allCards) {
        allCards.filter { !it.isCash && it.cardNumber.filter(Char::isDigit).length >= 4 }
            .map { SmsCardUi(it.bankName, it.cardNumber.filter(Char::isDigit).takeLast(4)) }
    }
    val guess = remember(cardNumber) { BankBin.bankFor(cardNumber) }

    LaunchedEffect(step, hasPermission, managing) {
        if (!managing && step == 2 && hasPermission) smsVm.loadMessages()
    }

    val senderUi = remember(messages, showAll) { groupSenders(messages, showAll) }
    val sendersLoading by smsVm.messagesLoading.collectAsState()

    if (managing) {
        SmsManageScreen(
            readerOn = readerEnabled, permissionGranted = hasPermission,
            senders = saved.map { SmsManagedSender(it.senderNumber, it.bankName, it.isEnabled) },
            cards = cardsUi,
            pendingCount = pending.size, unreadCount = unknown.count { !it.isIgnored },
            onBack = onBack,
            onToggleReader = { on ->
                if (on && !hasPermission) launcher.launch(SmsPermissionHelper.REQUIRED_PERMISSIONS) else smsVm.setReaderEnabled(on)
            },
            onToggleSender = { n, on -> smsVm.setSenderEnabled(n, on) },
            onRemoveSender = { smsVm.removeSender(it) },
            onAddSender = { standalone = true; managing = false; step = 2; selected = null; error = null },
            onAddCard = { creators?.addAccount?.invoke() },
            onOpenReview = onOpenReview, onOpenUnread = onOpenUnread,
            healthSection = { SmsHealthSection() }
        )
        return
    }

    val state = SmsWizardState(
        step = step, permissionGranted = hasPermission, readerOn = readerEnabled && hasPermission,
        cards = cardsUi, cardNumber = cardNumber, cardBankGuess = guess, cardName = cardName,
        senders = senderUi, sendersLoading = sendersLoading, showAllSenders = showAll, selectedNumber = selected,
        tab = tab, manualNumber = manualNumber, manualBank = manualBank, error = error,
        summaryCard = lastCard, summarySender = lastSender, pendingCount = pending.size
    )
    val actions = SmsWizardActions(
        onBack = { if (standalone) { standalone = false; managing = true } else onBack() },
        onToggleReader = { on ->
            if (on && !hasPermission) launcher.launch(SmsPermissionHelper.REQUIRED_PERMISSIONS) else smsVm.setReaderEnabled(on)
        },
        onContinueFromOn = {
            if (!hasPermission) launcher.launch(SmsPermissionHelper.REQUIRED_PERMISSIONS)
            else { smsVm.setReaderEnabled(true); step = 1 }
        },
        onSkipAll = onBack,
        onCardNumber = { cardNumber = groupCardNumber(it); error = null },
        onCardName = { cardName = it },
        onSaveCard = {
            val digits = cardNumber.filter { it.isDigit() }
            val bank = guess ?: cardName.trim()
            when {
                digits.length != 16 -> { error = "شمارهٔ کارت باید ۱۶ رقم باشد." }
                bank.isBlank() -> { error = "نام بانک را وارد کنید." }
                else -> {
                    val existing = allCards.firstOrNull { it.bankName == bank }
                    when {
                        existing == null -> viewModel.addCard(bank, digits, 0L)
                        existing.cardNumber.isBlank() -> viewModel.saveAccount(existing.copy(cardNumber = digits), existing.bankName)
                        else -> viewModel.addCard("$bank ${digits.takeLast(4)}", digits, 0L)
                    }
                    lastCard = "$bank · ${fa(digits.takeLast(4))}"
                    cardNumber = ""; cardName = ""; error = null; step = 2
                }
            }
        },
        onSkipCard = { error = null; step = 2 },
        onTab = { tab = it; error = null },
        onSelectSender = { selected = it },
        onToggleShowAll = { showAll = !showAll },
        onManualNumber = {
            manualNumber = it; error = null
            // سرشمارهٔ شناخته‌شده نام بانکش را خودش پر می‌کند.
            if (manualBank.isBlank()) com.example.sms.data.parser.BankSmsDetector.senderBank(it)?.let { b -> manualBank = b.displayName }
        },
        onManualBank = { manualBank = it },
        onSaveSender = {
            val g = if (tab == 0) senderUi.firstOrNull { it.number == selected } else null
            // بانکِ سرشمارهٔ ناشناخته را حدس نمی‌زنیم: کاربر نامش را در «وارد کردن شماره» می‌نویسد.
            val unknownBank = g != null && g.bankName.isEmpty()
            if (unknownBank) {
                tab = 1; manualNumber = g!!.number; manualBank = ""
                error = "بانک این سرشماره شناخته نشد؛ نام بانک را بنویسید."
            }
            val number = if (tab == 0) g?.number.orEmpty() else manualNumber
            val bank = if (tab == 0) g?.bankName.orEmpty() else manualBank
            if (!unknownBank && number.isNotBlank()) scope.launch {
                val err = smsVm.addSender(number, bank)
                if (err != null) {
                    error = err
                } else {
                    lastSender = number.trim()
                    error = null
                    if (standalone) { standalone = false; managing = true } else step = 3
                }
            }
        },
        onReview = { managing = true; onOpenReview() },
        onFinish = { managing = true }
    )
    SmsWizardScreen(state, actions)
}

/**
 * پیام‌های گوشی را به فرستنده گروه می‌کند. بانک از سرشماره شناخته می‌شود (شماره‌های بانک‌ها در دیتابیس
 * الگو)؛ برای سرشمارهٔ ناشناخته فقط وقتی پیامک‌هایش شکل تراکنش دارند، و بانک از متن همان تراکنش‌ها.
 * فرستنده‌های بانکی اول، و فرستنده‌های دیگر فقط با «نمایش همه».
 */
internal fun groupSenders(messages: List<InboxMessage>, showAll: Boolean): List<SmsSenderUi> =
    messages.groupBy { it.address }
        .map { (address, list) ->
            val txs = list.filter { it.isTransaction }
            val bank = list.firstOrNull { it.senderIsBank }?.bankGuess
                ?: txs.mapNotNull { it.bankGuess.takeIf(String::isNotEmpty) }.groupingBy { it }.eachCount()
                    .maxByOrNull { it.value }?.key
                ?: ""
            SmsSenderUi(
                number = address, bankName = bank, count = list.size,
                sample = (txs.firstOrNull() ?: list.first()).body,
                looksBank = list.any { it.senderIsBank } || txs.isNotEmpty(),
                txCount = txs.size
            )
        }
        .filter { showAll || it.looksBank }
        .sortedWith(
            compareByDescending<SmsSenderUi> { it.looksBank }
                .thenByDescending { it.bankName.isNotEmpty() }
                .thenByDescending { it.txCount }
                .thenByDescending { it.count }
        )
        .take(30)

/** زیرعنوان کارت فرستنده: سرشماره، تعداد پیامک و اگر بانکش شناخته نشده، همین را بگوید. */
private fun senderLine(s: SmsSenderUi): String = buildString {
    if (s.bankName.isNotEmpty()) append(fa(s.number)).append(" · ")
    append(fa(s.count)).append(" پیامک")
    if (s.txCount > 0) append(" · ").append(fa(s.txCount)).append(" تراکنش")
    if (s.bankName.isEmpty()) append(if (s.looksBank) " · بانک شناخته نشد" else " · غیربانکی")
}

/** نشان فرستنده‌ای که بانکش معلوم نیست: خاکستری با علامت پرسش، نه حرف اول «بانک». */
@Composable
private fun SenderDot(size: androidx.compose.ui.unit.Dp = 36.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(11.dp)).background(L.Chip), contentAlignment = Alignment.Center) {
        Text("؟", style = lt(15.sp, FontWeight.ExtraBold, L.Muted))
    }
}

// ───────────────────────── پیامک‌های خوانده‌نشده ─────────────────────────

/**
 * پیامک‌هایی که تراز نتوانست بخواند. آموزش در کار نیست: خودتان ثبت می‌کنید یا نادیده می‌گیرید.
 */
@Composable
fun LayerSmsUnreadScreen(
    viewModel: FinanceViewModel,
    onBack: () -> Unit,
    onManualEntry: () -> Unit
) {
    val unknown by viewModel.unknownSmsList.collectAsState()
    val list = unknown.filter { !it.isIgnored }

    LayerScreen(
        title = "پیامک‌های خوانده‌نشده",
        subtitle = if (list.isEmpty()) "همه‌چیز مرتب است" else "${fa(list.size)} پیامک",
        onBack = onBack
    ) {
        if (list.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(L.GreenSoft), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, null, tint = L.Green, modifier = Modifier.size(44.dp))
                }
                Text("پیامک خوانده‌نشده‌ای نمانده", style = lt(17.sp, FontWeight.Bold))
            }
            return@LayerScreen
        }
        Text(
            "این پیامک‌ها شکل تراکنش بانکی را نداشتند یا جهتشان (برداشت یا واریز) روشن نبود. اگر تراکنش‌اند، با دکمهٔ «ثبت دستی» وارد کنید.",
            style = lt(12.sp, color = L.Muted, lineHeight = 22.sp), modifier = Modifier.padding(horizontal = 24.dp)
        )
        list.forEach { u ->
            LayerCard(padding = 16.dp) {
                val d = JalaliDate.fromTimestamp(u.timestamp)
                Text("${fa(u.senderNumber)} · ${d.formatShort()} ${timeOfDay(u.timestamp)}", style = lt(12.sp, color = L.Muted))
                Text(u.smsBody, style = lt(14.sp, lineHeight = 28.sp), modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton("ثبت دستی", Modifier.weight(1f), height = 46.dp) { viewModel.ignoreUnknownSms(u.id); onManualEntry() }
                    Box(
                        Modifier.height(46.dp).clip(RoundedCornerShape(16.dp)).background(L.PrimarySoft).clickable { viewModel.ignoreUnknownSms(u.id) }.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("نادیده", style = lt(14.sp, FontWeight.Bold, L.PrimaryText)) }
                }
            }
        }
    }
}
