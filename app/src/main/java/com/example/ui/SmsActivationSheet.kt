package com.example.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.BankCard
import com.example.sms.data.repository.InboxSender
import com.example.sms.engine.BankCatalog
import com.example.sms.engine.SmsBank
import com.example.sms.presentation.ActivationStep
import com.example.sms.presentation.BankSource
import com.example.sms.presentation.SmsActivationState
import com.example.sms.presentation.SmsActivationViewModel
import com.example.ui.components.IranianBankLogo
import com.example.ui.theme.HorizonBorder
import com.example.ui.theme.HorizonClay
import com.example.ui.theme.HorizonClayTint
import com.example.ui.theme.HorizonGreen
import com.example.ui.theme.HorizonGreenTint
import com.example.ui.theme.HorizonInk
import com.example.ui.theme.HorizonInkMuted
import com.example.ui.theme.HorizonSurface
import com.example.utils.JalaliDate
import com.example.utils.NotificationHelper

private val SMS_PERMISSIONS = arrayOf(
    android.Manifest.permission.READ_SMS,
    android.Manifest.permission.RECEIVE_SMS
)

/**
 * Turns the SMS reader on for one card, in the order the user thinks of it:
 * which card (and so which bank), allow reading messages, then which sender
 * number the bank writes from — picked from the senders actually in the inbox.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsActivationSheet(
    card: BankCard,
    startAtSender: Boolean,
    onDismiss: () -> Unit,
    viewModel: SmsActivationViewModel = viewModel()
) {
    LaunchedEffect(card.id, startAtSender) { viewModel.start(card, startAtSender) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> viewModel.onPermissionResult(SMS_PERMISSIONS.all { result[it] == true }) }

    // Back from the system settings page, the permission may have been granted there.
    LifecycleResumeEffect(Unit) {
        viewModel.recheckPermission()
        onPauseOrDispose { }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = HorizonSurface,
        modifier = Modifier.testTag("sms_activation_sheet")
    ) {
        Column(
            modifier = Modifier
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "فعال‌سازی پیامک‌خوان",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold, fontSize = 17.sp, color = HorizonInk
                )
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = card.bankName,
                style = MaterialTheme.typography.bodySmall.copy(color = HorizonInkMuted, fontSize = 12.sp)
            )

            if (state.step != ActivationStep.DONE) {
                Spacer(Modifier.height(14.dp))
                StepIndicator(state.step)
            }
            Spacer(Modifier.height(16.dp))

            when (state.step) {
                ActivationStep.CARD -> CardStep(
                    state = state,
                    onDigits = viewModel::onCardDigits,
                    onBankPicked = viewModel::onBankPicked,
                    onContinue = viewModel::continueFromCard
                )
                ActivationStep.PERMISSION -> PermissionStep(
                    denied = state.permissionDenied,
                    onAllow = { permissionLauncher.launch(SMS_PERMISSIONS) },
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        )
                    },
                    onBack = viewModel::back
                )
                ActivationStep.SENDER -> SenderStep(
                    state = state,
                    onQuery = viewModel::onQuery,
                    onShowAll = viewModel::onShowAll,
                    onSelect = viewModel::selectSender,
                    onManualNumber = viewModel::onManualNumber,
                    onUseManual = viewModel::useManualNumber,
                    onImportDays = viewModel::onImportDays,
                    onRefresh = viewModel::loadSenders,
                    onActivate = viewModel::activate,
                    onBack = viewModel::back
                )
                ActivationStep.DONE -> DoneStep(state = state, onClose = onDismiss)
            }
        }
    }
}

// ---- progress ---------------------------------------------------------------------

@Composable
private fun StepIndicator(current: ActivationStep) {
    val steps = listOf(
        ActivationStep.CARD to "کارت",
        ActivationStep.PERMISSION to "مجوز پیامک",
        ActivationStep.SENDER to "سرشماره"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, (step, label) ->
            val reached = current.ordinal >= step.ordinal
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (reached) HorizonGreen else HorizonBorder)
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "${JalaliDate.toPersianDigits(index + 1)}. $label",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        fontWeight = if (current == step) FontWeight.Bold else FontWeight.Normal,
                        color = if (reached) HorizonInk else HorizonInkMuted
                    )
                )
            }
        }
    }
}

// ---- step 1: the card ---------------------------------------------------------------

@Composable
private fun CardStep(
    state: SmsActivationState,
    onDigits: (String) -> Unit,
    onBankPicked: (SmsBank) -> Unit,
    onContinue: () -> Unit
) {
    var picking by remember { mutableStateOf(false) }

    Text(
        text = "شمارهٔ کارتتان را وارد کنید. از روی شش رقم اول آن، بانک شناسایی می‌شود و پیامک‌های همان بانک خوانده می‌شود.",
        style = MaterialTheme.typography.bodySmall.copy(color = HorizonInkMuted, fontSize = 12.sp, lineHeight = 19.sp)
    )
    Spacer(Modifier.height(12.dp))

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        OutlinedTextField(
            value = state.cardDigits,
            onValueChange = onDigits,
            label = { Text("شمارهٔ کارت") },
            placeholder = { Text("6037 9911 1234 5678") },
            visualTransformation = CardNumberTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sms_card_number")
        )
    }

    Spacer(Modifier.height(12.dp))
    val bank = state.bank
    if (bank != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(HorizonGreenTint)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("sms_detected_bank")
        ) {
            IranianBankLogo(bankName = bank.displayName, size = 34.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = bank.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = HorizonInk)
                )
                Text(
                    text = when (state.bankSource) {
                        BankSource.CARD_NUMBER -> "شناسایی شده از روی شمارهٔ کارت"
                        BankSource.ACCOUNT_NAME -> "برداشت از نام حساب — اگر درست نیست تغییر دهید"
                        else -> "انتخاب شما"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(color = HorizonInkMuted, fontSize = 11.sp)
                )
            }
            TextButton(onClick = { picking = true }) { Text("تغییر", color = HorizonGreen) }
        }
    } else {
        OutlinedButton(
            onClick = { picking = true },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sms_pick_bank")
        ) {
            Icon(Icons.Default.CreditCard, contentDescription = null, tint = HorizonGreen)
            Spacer(Modifier.width(8.dp))
            Text("بانک را خودم انتخاب می‌کنم", color = HorizonGreen)
        }
        if (state.cardDigits.length >= 6) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "بانک این کارت از روی شماره شناسایی نشد.",
                style = MaterialTheme.typography.bodySmall.copy(color = HorizonInkMuted, fontSize = 11.5.sp)
            )
        }
    }

    ErrorLine(state.error)
    Spacer(Modifier.height(18.dp))
    PrimaryAction(text = "ادامه", enabled = state.cardComplete, tag = "sms_card_continue", onClick = onContinue)

    if (picking) {
        BankPickerDialog(
            onPick = { picked ->
                onBankPicked(picked)
                picking = false
            },
            onDismiss = { picking = false }
        )
    }
}

@Composable
private fun BankPickerDialog(onPick: (SmsBank) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val banks = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) BankCatalog.banks
        else BankCatalog.banks.filter { it.displayName.contains(q) || it.patternName.contains(q) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HorizonSurface,
        title = { Text("انتخاب بانک", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("جستجوی بانک") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(banks, key = { it.patternName }) { bank ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(bank) }
                                .padding(vertical = 10.dp)
                        ) {
                            IranianBankLogo(bankName = bank.displayName, size = 30.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(bank.displayName, style = MaterialTheme.typography.bodyMedium.copy(color = HorizonInk))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}

// ---- step 2: permission -------------------------------------------------------------

@Composable
private fun ColumnScope.PermissionStep(
    denied: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(HorizonGreenTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Sms, contentDescription = null, tint = HorizonGreen, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "اجازهٔ خواندن پیامک",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = HorizonInk)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "تراز برای دیدن پیامک‌های بانکی‌تان و ثبت تراکنش‌های تازه، به مجوز «پیامک» نیاز دارد. فقط پیامک‌های سرشماره‌ای که خودتان انتخاب می‌کنید بررسی می‌شود، و هر تراکنش پیش از ثبت، منتظر تأیید شماست.",
            style = MaterialTheme.typography.bodySmall.copy(color = HorizonInkMuted, fontSize = 12.sp, lineHeight = 19.sp),
            textAlign = TextAlign.Center
        )
        if (denied) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "مجوز داده نشد. بدون آن پیامک‌خوان کار نمی‌کند؛ می‌توانید دوباره تلاش کنید یا آن را از تنظیمات گوشی بدهید. اگر گزینهٔ پیامک خاکستری است (در اندروید ۱۳ به بالا برای برنامه‌های نصب‌شده از بیرون فروشگاه)، در صفحهٔ تنظیمات برنامه از منوی سه‌نقطه «اجازهٔ تنظیمات محدودشده» را بزنید.",
                style = MaterialTheme.typography.bodySmall.copy(color = HorizonClay, fontSize = 12.sp, lineHeight = 19.sp),
                textAlign = TextAlign.Center
            )
        }
    }
    Spacer(Modifier.height(18.dp))
    PrimaryAction(text = "فعال‌سازی پیامک‌خوان", enabled = true, tag = "sms_allow_permission", onClick = onAllow)
    if (denied) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onOpenSettings,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("sms_open_settings")
        ) { Text("باز کردن تنظیمات برنامه", color = HorizonGreen) }
    }
    TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
        Text("بازگشت", color = HorizonInkMuted)
    }
}

// ---- step 3: the sender number ------------------------------------------------------

@Composable
private fun ColumnScope.SenderStep(
    state: SmsActivationState,
    onQuery: (String) -> Unit,
    onShowAll: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    onManualNumber: (String) -> Unit,
    onUseManual: () -> Unit,
    onImportDays: (Int) -> Unit,
    onRefresh: () -> Unit,
    onActivate: () -> Unit,
    onBack: () -> Unit
) {
    Text(
        text = "بانک شما پیامک‌ها را از چه سرشماره‌ای می‌فرستد؟ از بین پیامک‌هایی که در گوشی دارید، همان را انتخاب کنید.",
        style = MaterialTheme.typography.bodySmall.copy(color = HorizonInkMuted, fontSize = 12.sp, lineHeight = 19.sp)
    )
    Spacer(Modifier.height(10.dp))

    OutlinedTextField(
        value = state.query,
        onValueChange = onQuery,
        placeholder = { Text("جستجوی سرشماره") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("sms_sender_search")
    )
    Spacer(Modifier.height(8.dp))

    val visible = state.visibleSenders
    when {
        state.loadingSenders -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = HorizonGreen)
            Spacer(Modifier.width(12.dp))
            Text("در حال بررسی پیامک‌های گوشی…", style = MaterialTheme.typography.bodySmall.copy(color = HorizonInkMuted))
        }
        visible.isEmpty() -> Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text(
                text = if (state.senders.isEmpty()) "پیامکی در گوشی پیدا نشد." else "سرشمارهٔ مطابق جستجو پیدا نشد.",
                style = MaterialTheme.typography.bodySmall.copy(color = HorizonInkMuted)
            )
            TextButton(onClick = onRefresh) { Text("بررسی دوباره", color = HorizonGreen) }
        }
        else -> LazyColumn(modifier = Modifier.heightIn(max = 270.dp).testTag("sms_sender_list")) {
            items(visible, key = { it.address }) { sender ->
                SenderRow(sender = sender, selected = sameSender(sender.address, state.selected), onClick = { onSelect(sender.address) })
            }
        }
    }

    if (!state.showAllSenders && !state.loadingSenders && state.senders.size > visible.size) {
        TextButton(onClick = { onShowAll(true) }) {
            Text("نمایش همهٔ فرستنده‌ها (${JalaliDate.toPersianDigits(state.senders.size)})", color = HorizonGreen)
        }
    }

    // A number typed by hand — for a sender that has not written to this phone yet.
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            OutlinedTextField(
                value = state.manualNumber,
                onValueChange = onManualNumber,
                label = { Text("سرشمارهٔ دیگر") },
                placeholder = { Text("مثلاً 3000 1234") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).testTag("sms_manual_number")
            )
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
            onClick = onUseManual,
            enabled = state.manualNumber.isNotBlank(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.height(56.dp).testTag("sms_manual_use")
        ) { Text("استفاده", color = HorizonGreen) }
    }

    state.selected?.let { PreviewPanel(state = state, onImportDays = onImportDays) }

    ErrorLine(state.error)
    Spacer(Modifier.height(14.dp))
    PrimaryAction(
        text = if (state.saving) "در حال فعال‌سازی…" else "فعال‌سازی پیامک‌خوان",
        enabled = state.selected != null && !state.saving && !state.previewLoading,
        tag = "sms_activate",
        onClick = onActivate
    )
    TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
        Text("بازگشت", color = HorizonInkMuted)
    }
}

private fun sameSender(a: String, b: String?): Boolean =
    b != null && com.example.sms.engine.SmsSenders.same(a, b)

@Composable
private fun SenderRow(sender: InboxSender, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) HorizonGreenTint else Color.Transparent)
            .border(1.dp, if (selected) HorizonGreen else HorizonBorder, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = sender.address,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = HorizonInk),
                        maxLines = 1
                    )
                }
                Spacer(Modifier.width(8.dp))
                when {
                    sender.knownForBank -> Tag("سرشمارهٔ شناخته‌شده", HorizonGreen, HorizonGreenTint)
                    sender.recognized > 0 -> Tag("قابل خواندن", HorizonGreen, HorizonGreenTint)
                }
            }
            Text(
                text = sender.lastBody.replace('\n', ' '),
                style = MaterialTheme.typography.bodySmall.copy(color = HorizonInkMuted, fontSize = 11.sp, lineHeight = 16.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${JalaliDate.toPersianDigits(sender.messageCount)} پیامک · آخرین: ${JalaliDate.fromTimestamp(sender.lastDate).formatShort()}" +
                    if (sender.sampled > 0) " · ${JalaliDate.toPersianDigits(sender.recognized)} از ${JalaliDate.toPersianDigits(sender.sampled)} تراکنش" else "",
                style = MaterialTheme.typography.bodySmall.copy(color = HorizonInkMuted, fontSize = 10.5.sp)
            )
        }
    }
}

@Composable
private fun Tag(text: String, fg: Color, bg: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(color = fg, fontWeight = FontWeight.Bold, fontSize = 10.sp),
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun PreviewPanel(state: SmsActivationState, onImportDays: (Int) -> Unit) {
    Spacer(Modifier.height(10.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HorizonGreenTint.copy(alpha = 0.6f))
            .padding(12.dp)
            .testTag("sms_preview")
    ) {
        val preview = state.preview
        when {
            state.previewLoading || preview == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = HorizonGreen)
                Spacer(Modifier.width(8.dp))
                Text("در حال آزمایش روی پیامک‌های این سرشماره…", style = MaterialTheme.typography.bodySmall.copy(color = HorizonInkMuted))
            }
            preview.recognized > 0 -> {
                Text(
                    text = "از ${JalaliDate.toPersianDigits(preview.sampled)} پیامک اخیر این سرشماره، ${JalaliDate.toPersianDigits(preview.recognized)} مورد به‌عنوان تراکنش خوانده شد:",
                    style = MaterialTheme.typography.bodySmall.copy(color = HorizonInk, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 18.sp)
                )
                Spacer(Modifier.height(6.dp))
                preview.transactions.take(3).forEach { tx ->
                    Text(
                        text = (if (tx.isIncome) "واریز" else "برداشت") +
                            " · ${NotificationHelper.formatAmount(tx.amountToman)} تومان · ${JalaliDate.fromTimestamp(tx.date).formatShort()}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (tx.isIncome) HorizonGreen else HorizonClay, fontSize = 12.sp
                        )
                    )
                }
            }
            preview.sampled > 0 -> WarningLine(
                "در ${JalaliDate.toPersianDigits(preview.sampled)} پیامک اخیر این سرشماره، تراکنشی تشخیص داده نشد. اگر سرشماره درست است ادامه دهید؛ پیامک‌های بعدی بررسی می‌شوند."
            )
            else -> Text(
                text = "این سرشماره هنوز پیامی در گوشی ندارد. از این پس پیامک‌هایش خوانده می‌شود.",
                style = MaterialTheme.typography.bodySmall.copy(color = HorizonInkMuted, fontSize = 12.sp, lineHeight = 18.sp)
            )
        }

        state.takenBy?.let {
            Spacer(Modifier.height(8.dp))
            WarningLine("این سرشماره الان به «$it» وصل است و با فعال‌سازی از آن کارت جدا می‌شود.")
        }

        if (preview != null && preview.recognized > 0 && !state.previewLoading) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "پیامک‌های قبلی هم ثبت شوند؟",
                style = MaterialTheme.typography.bodySmall.copy(color = HorizonInk, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "فقط از الان", 7 to "۷ روز اخیر", 30 to "۳۰ روز اخیر").forEach { (days, label) ->
                    FilterChip(
                        selected = state.importDays == days,
                        onClick = { onImportDays(days) },
                        label = { Text(label, fontSize = 12.sp) },
                        modifier = Modifier.testTag("sms_import_$days")
                    )
                }
            }
            if (state.importDays > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "هر تراکنش قدیمی پیش از ثبت منتظر تأیید شماست. اگر موجودی کارت را بعد از آن تراکنش‌ها وارد کرده‌اید، «فقط از الان» را بزنید تا دوبار حساب نشوند.",
                    style = MaterialTheme.typography.bodySmall.copy(color = HorizonInkMuted, fontSize = 11.sp, lineHeight = 17.sp)
                )
            }
        }
    }
}

// ---- done ---------------------------------------------------------------------------

@Composable
private fun DoneStep(state: SmsActivationState, onClose: () -> Unit) {
    val bank = state.bank
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(Color(0xFF10B981)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "پیامک‌خوان ${bank?.displayName.orEmpty()} فعال شد",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = HorizonInk),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "از این پس، تراکنش‌های پیامک‌های سرشمارهٔ ${state.selected.orEmpty()} برای تأیید شما آماده می‌شود.",
            style = MaterialTheme.typography.bodySmall.copy(color = HorizonInkMuted, fontSize = 12.sp, lineHeight = 19.sp),
            textAlign = TextAlign.Center
        )
        val imported = state.result?.imported ?: 0
        if (state.importDays > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (imported > 0) "${JalaliDate.toPersianDigits(imported)} تراکنش از پیامک‌های قبلی هم در انتظار تأیید شماست."
                else "در پیامک‌های این بازه تراکنشی برای ثبت پیدا نشد.",
                style = MaterialTheme.typography.bodySmall.copy(color = HorizonGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp),
                textAlign = TextAlign.Center
            )
        }
    }
    Spacer(Modifier.height(20.dp))
    PrimaryAction(text = "تمام", enabled = true, tag = "sms_done", onClick = onClose)
}

// ---- small pieces -------------------------------------------------------------------

@Composable
private fun PrimaryAction(text: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = HorizonGreen),
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag(tag)
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp))
    }
}

@Composable
private fun ErrorLine(message: String?) {
    if (message == null) return
    Spacer(Modifier.height(10.dp))
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall.copy(color = HorizonClay, fontWeight = FontWeight.Bold, fontSize = 12.sp),
        modifier = Modifier.testTag("sms_error")
    )
}

@Composable
private fun WarningLine(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(HorizonClayTint)
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = HorizonClay, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall.copy(color = HorizonClay, fontSize = 11.5.sp, lineHeight = 17.sp))
    }
}
