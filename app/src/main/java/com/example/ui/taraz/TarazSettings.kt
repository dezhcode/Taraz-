package com.example.ui.taraz

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.CategoryPickerSheet
import com.example.ui.FinanceViewModel
import com.example.ui.SmsParserSettingsScreen
import com.example.utils.NotificationHelper

@Composable
fun TarazSettingsScreen(vm: FinanceViewModel, onBack: () -> Unit) {
    val profile by vm.userProfile.collectAsStateWithLifecycle()
    val biometric by vm.biometricEnabled.collectAsStateWithLifecycle()
    val alerts by vm.alertsEnabled.collectAsStateWithLifecycle()
    val budget by vm.dailyBudget.collectAsStateWithLifecycle()
    val senders by vm.bankSenders.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showSms by remember { mutableStateOf(false) }
    var showBudget by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var serverStatus by remember { mutableStateOf<String?>(null) }

    if (showSms) {
        SmsParserSettingsScreen(viewModel = vm, onBack = { showSms = false })
        return
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(Tz.Ground)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item { TzTopBar("تنظیمات", onBack = onBack) }

        item {
            TzCard(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val name = profile?.name?.takeIf { it.isNotBlank() } ?: "کاربر تراز"
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(Tz.CobaltTint2),
                        contentAlignment = Alignment.Center
                    ) { Text(name.take(1), style = tzText(18.sp, FontWeight.Bold, Tz.Cobalt)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = tzText(16.sp, FontWeight.SemiBold))
                        Text(
                            when {
                                !profile?.phone.isNullOrBlank() -> TzFormat.fa(profile?.phone ?: "")
                                profile?.isGuest == true -> "مهمان · داده‌ها فقط روی همین گوشی است"
                                else -> profile?.email.orEmpty()
                            },
                            style = tzText(13.sp, color = Tz.Muted)
                        )
                    }
                }
            }
        }

        item { Group("امنیت") }
        item {
            GroupCard {
                SwitchRow("قفل با اثر انگشت", "هر بار که تراز باز می‌شود، هویت شما را می‌پرسد", biometric) { vm.setBiometricEnabled(it) }
            }
        }

        item { Group("اعلان‌ها و بودجه") }
        item {
            GroupCard {
                SwitchRow("یادآور اقساط و اعلان‌ها", "یک روز قبل از هر سررسید خبرتان می‌کنیم", alerts) { vm.setAlertsEnabled(it) }
                Hairline()
                LinkRow("ارسال اعلان آزمایشی", "ببینید اعلان‌های تراز روی گوشی‌تان چطور می‌آید") {
                    if (alerts) {
                        NotificationHelper.showNotification(context, "اعلان آزمایشی تراز", "اعلان‌ها روشن است و یادآور اقساط به همین شکل می‌آید.")
                        note = "اعلان آزمایشی فرستاده شد."
                    } else {
                        note = "اول «یادآور اقساط و اعلان‌ها» را روشن کنید."
                    }
                }
                Hairline()
                LinkRow("بودجهٔ روزانه", null, value = if (budget > 0) TzFormat.compact(budget) else "تعیین نشده") { showBudget = true }
            }
        }

        item { Group("داده‌ها") }
        item {
            GroupCard {
                LinkRow(
                    "پیامک‌های بانکی",
                    "تراکنش‌ها را از پیامک بانک برای تأیید شما آماده می‌کند",
                    value = "${TzFormat.fa(senders.count { it.isEnabled })} بانک روشن"
                ) { showSms = true }
                Hairline()
                LinkRow("دسته‌بندی‌ها", "دیدن دسته‌ها و ساخت دستهٔ تازه", value = "${TzFormat.fa(categories.size)} دسته") { showCategories = true }
            }
        }

        item { Group("دستیار") }
        item {
            GroupCard {
                LinkRow("آزمایش اتصال به سرور دستیار", serverStatus ?: "بررسی می‌کند دستیار هوشمند در دسترس است یا نه") {
                    serverStatus = "در حال بررسی…"
                    vm.testAiServerConnection { ok, msg -> serverStatus = (if (ok) "متصل است · " else "وصل نشد · ") + msg }
                }
            }
        }

        if (note != null) {
            item {
                Text(
                    note ?: "",
                    style = tzText(13.sp, FontWeight.SemiBold, Tz.GreenText),
                    modifier = Modifier
                        .padding(start = 20.dp, end = 20.dp, top = 12.dp)
                        .fillMaxWidth()
                        .clip(Tz.RowShape)
                        .background(Tz.GreenTint)
                        .padding(12.dp)
                )
            }
        }

        item {
            GroupCard(Modifier.padding(top = 18.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .tzClick { confirmLogout = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Tz.CobaltText, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("خروج از حساب", style = tzText(14.sp, FontWeight.SemiBold, Tz.CobaltText))
                }
                Hairline()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .tzClick { confirmReset = true }
                        .padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("پاک کردن همهٔ داده‌ها", style = tzText(14.sp, FontWeight.SemiBold, Tz.Red))
                    Text("تراکنش‌ها، حساب‌ها و اقساط برای همیشه پاک می‌شوند", style = tzText(12.sp, color = Tz.Muted))
                }
            }
        }
        item {
            Text("تراز · نسخهٔ ۲", style = tzText(12.sp, color = Tz.Muted), modifier = Modifier.fillMaxWidth().padding(top = 14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }

    if (showBudget) {
        AmountDialog(
            title = "بودجهٔ روزانه",
            initial = budget,
            help = "سقف خرج هر روز. برای خاموش کردن، صفر بگذارید.",
            onDismiss = { showBudget = false },
            onConfirm = {
                vm.setDailyBudget(it)
                showBudget = false
            }
        )
    }
    if (showCategories) {
        CategoryPickerSheet(
            categories = categories,
            isIncomeMode = false,
            onPick = { showCategories = false },
            onCreate = { name, icon, color, isIncome -> vm.addCategory(name, icon, color, isIncome) },
            onDismiss = { showCategories = false }
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            title = "همهٔ داده‌ها پاک شود؟",
            text = "همهٔ تراکنش‌ها، حساب‌ها، اقساط و تنظیمات برای همیشه پاک می‌شوند و برگشت ندارند.",
            confirm = "بله، همه پاک شود",
            danger = true,
            onDismiss = { confirmReset = false },
            onConfirm = {
                vm.resetAllData()
                confirmReset = false
                note = "همهٔ داده‌ها پاک شد."
            }
        )
    }
    if (confirmLogout) {
        ConfirmDialog(
            title = "از حساب خارج می‌شوید؟",
            text = "برای ورود دوباره، کد تأیید به شمارهٔ شما پیامک می‌شود.",
            confirm = "خروج",
            danger = false,
            onDismiss = { confirmLogout = false },
            onConfirm = {
                confirmLogout = false
                vm.logout()
            }
        )
    }
}

@Composable
private fun Group(title: String) {
    Text(title, style = tzText(13.sp, FontWeight.Bold, Tz.Muted), modifier = Modifier.padding(start = 26.dp, end = 26.dp, top = 18.dp, bottom = 6.dp))
}

@Composable
private fun GroupCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    TzCard(modifier.padding(horizontal = 20.dp), padding = PaddingValues(horizontal = 16.dp)) { content() }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = tzText(14.sp, FontWeight.SemiBold))
            if (subtitle != null) Text(subtitle, style = tzText(12.sp, color = Tz.Muted))
        }
        Spacer(Modifier.width(10.dp))
        TzSwitch(checked, onChange, title)
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String?, value: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .tzClick(onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = tzText(14.sp, FontWeight.Medium))
            if (subtitle != null) Text(subtitle, style = tzText(12.sp, color = Tz.Muted))
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, style = tzText(12.sp, color = Tz.Muted))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color(0xFF8E95A2), modifier = Modifier.size(20.dp))
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    danger: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tz.Surface,
        title = { Text(title, style = tzText(18.sp, FontWeight.Bold)) },
        text = { Text(text, style = tzText(14.sp, color = Tz.Muted, lineHeight = 24.sp)) },
        confirmButton = { TextAction(confirm, onClick = onConfirm, color = if (danger) Tz.Red else Tz.CobaltText) },
        dismissButton = { TextAction("انصراف", onClick = onDismiss, color = Tz.Muted) }
    )
}
