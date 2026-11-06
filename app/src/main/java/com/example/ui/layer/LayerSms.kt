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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.example.data.BankCard
import com.example.data.Category
import com.example.data.PendingTransaction
import com.example.sms.permission.SmsPermissionHelper
import com.example.sms.presentation.SmsParserViewModel
import com.example.ui.FinanceViewModel
import com.example.utils.JalaliDate

private fun bankColor(name: String): Color = when {
    name.contains("ملت") -> Color(0xFFB4232F)
    name.contains("سامان") -> Color(0xFF0F3B66)
    name.contains("ملی") -> Color(0xFF0E6E8A)
    name.contains("تجارت") -> Color(0xFF1F4E9C)
    name.contains("صادرات") -> Color(0xFF1E6BFF)
    name.contains("پاسارگاد") -> Color(0xFF7A5AA8)
    name.contains("بلو") -> Color(0xFF1E6BFF)
    else -> Color(0xFF5C6B73)
}

/**
 * پیامک‌های بانکی — طرح نهایی: وضعیت دسترسی، فهرست بانک‌ها با تیک، کادر «منتظر تأیید»،
 * و فهرست پیامک‌های ناشناخته برای آموزش قالب.
 */
@Composable
fun LayerSmsSetupScreen(
    viewModel: FinanceViewModel,
    onBack: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenLearn: () -> Unit
) {
    val context = LocalContext.current
    val smsViewModel: SmsParserViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val scanned by smsViewModel.scannedSenders.collectAsState()
    val pending by smsViewModel.pendingTransactions.collectAsState()
    val unknown by smsViewModel.unknownSmsList.collectAsState()
    val isLoading by smsViewModel.isLoading.collectAsState()
    val error by smsViewModel.errorMessage.collectAsState()

    var hasPermission by remember { mutableStateOf(SmsPermissionHelper.hasSmsPermissions(context)) }
    var dirty by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { p ->
        hasPermission = (p[android.Manifest.permission.READ_SMS] ?: false) && (p[android.Manifest.permission.RECEIVE_SMS] ?: false)
        if (hasPermission) smsViewModel.triggerScan()
    }
    LaunchedEffect(hasPermission) { if (hasPermission) smsViewModel.triggerScan() }

    val count = scanned.count { it.isEnabled }

    LayerScreen(
        title = "پیامک‌های بانکی",
        subtitle = "ثبت خودکار تراکنش از پیامک بانک",
        onBack = onBack,
        bottomBar = {
            BigButton(
                text = when {
                    dirty -> "ذخیره · ${fa(count)} بانک انتخاب شده"
                    saved -> "ذخیره شد"
                    else -> "تغییری نداده‌اید"
                },
                enabled = dirty
            ) {
                smsViewModel.saveChoices()
                dirty = false
                saved = true
            }
        }
    ) {
        if (hasPermission) {
            Row(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(L.GreenSoft).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(L.Green), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("دسترسی به پیامک داده شده", style = lt(14.sp, FontWeight.Bold, L.GreenInk))
                    Text("پیامک‌ها روی همین گوشی خوانده می‌شوند.", style = lt(12.sp, color = L.GreenInk2))
                }
            }
        } else {
            Callout(
                title = "تراز اجازهٔ خواندن پیامک ندارد",
                body = "بدون این اجازه، تراکنش‌های بانکی خودکار ثبت نمی‌شوند. پیامک‌ها فقط روی همین گوشی خوانده می‌شوند.",
                bg = L.AmberSoft, titleColor = L.AmberInk,
                trailing = {
                    Box(
                        Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(L.Surface)
                            .clickable { launcher.launch(SmsPermissionHelper.REQUIRED_PERMISSIONS) }.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("اجازه می‌دهم", style = lt(13.sp, FontWeight.Bold, L.PrimaryText)) }
                }
            )
        }

        Row(Modifier.padding(horizontal = 24.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("پیامک کدام بانک‌ها خوانده شود؟", style = lt(15.sp, FontWeight.Bold), modifier = Modifier.weight(1f))
            Box(
                Modifier.height(36.dp).clip(RoundedCornerShape(10.dp)).clickable(enabled = hasPermission && scanned.isNotEmpty()) {
                    smsViewModel.selectAllSenders(); dirty = true; saved = false
                }.padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) { Text("انتخاب همه", style = lt(13.sp, FontWeight.Bold, if (hasPermission) L.PrimaryText else L.Faint)) }
        }

        LayerCard(radius = 20.dp, padding = 0.dp) {
            when {
                !hasPermission -> Text("برای دیدن فهرست بانک‌ها، اول اجازهٔ پیامک را بدهید.", style = lt(13.sp, color = L.Muted), modifier = Modifier.padding(16.dp))
                isLoading -> Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = L.Primary, strokeWidth = 2.dp)
                    Text("در حال یافتن پیامک‌های بانکی…", style = lt(13.sp, color = L.Muted))
                }
                error != null -> Text(error ?: "", style = lt(13.sp, FontWeight.SemiBold, L.RedText), modifier = Modifier.padding(16.dp))
                scanned.isEmpty() -> Text("پیامک بانکی‌ای روی گوشی پیدا نشد.", style = lt(13.sp, color = L.Muted), modifier = Modifier.padding(16.dp))
                else -> scanned.forEachIndexed { i, s ->
                    Row(
                        Modifier.fillMaxWidth().heightIn56().clickable {
                            smsViewModel.toggleSenderSelection(s.senderNumber); dirty = true; saved = false
                        }.padding(horizontal = 14.dp)
                            .semantics { role = Role.Checkbox; stateDescription = if (s.isEnabled) "انتخاب شده" else "انتخاب نشده"; contentDescription = s.bankName },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(if (s.isEnabled) L.Primary else L.Surface)
                                .border(2.dp, if (s.isEnabled) L.Primary else L.Chevron, RoundedCornerShape(7.dp)),
                            contentAlignment = Alignment.Center
                        ) { if (s.isEnabled) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                        LogoOrColor(remember(s.bankName) { BankLogos.assetFor(s.bankName) }, bankColor(s.bankName), 26.dp, 18.dp)
                        Column(Modifier.weight(1f)) {
                            Text(s.bankName, style = lt(14.sp, FontWeight.SemiBold), maxLines = 1)
                            Text("${fa(s.messageCount)} پیامک · شماره ${fa(s.senderNumber)}", style = lt(12.sp, color = L.Muted), maxLines = 1)
                        }
                    }
                    if (i < scanned.size - 1) Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(L.Line))
                }
            }
        }

        if (pending.isNotEmpty()) {
            Callout(
                title = "${fa(pending.size)} پیامک منتظر تأیید شماست",
                body = "تا تأیید نکنید، در حساب‌ها ثبت نمی‌شوند.",
                bg = L.AmberSoft, titleColor = L.AmberInk,
                trailing = {
                    Box(
                        Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(L.Surface).clickable(onClick = onOpenReview).padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("بررسی", style = lt(13.sp, FontWeight.Bold, L.PrimaryText)) }
                }
            )
        }
        if (unknown.any { !it.isIgnored }) {
            Callout(
                title = "${fa(unknown.count { !it.isIgnored })} پیامک را نشناختیم",
                body = "قالبشان را به تراز یاد بدهید تا بعد از این خودکار خوانده شوند.",
                bg = L.PrimarySoft, titleColor = L.PrimaryText,
                trailing = {
                    Box(
                        Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(L.Surface).clickable(onClick = onOpenLearn).padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("آموزش", style = lt(13.sp, FontWeight.Bold, L.PrimaryText)) }
                }
            )
        }
    }
}

private fun Modifier.heightIn56(): Modifier = this.then(Modifier.height(56.dp))

/**
 * بررسی پیامک‌ها — یک‌یکی: متن پیامک، مبلغ، عنوان، حساب، دسته با پیشنهاد تراز.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LayerSmsReviewScreen(
    viewModel: FinanceViewModel,
    pending: List<PendingTransaction>,
    categories: List<Category>,
    cards: List<BankCard>,
    onBack: () -> Unit
) {
    val proposal by viewModel.smsAiProposal.collectAsState()
    val isCategorizing by viewModel.isAiCategorizing.collectAsState()
    var total by remember { mutableIntStateOf(pending.size.coerceAtLeast(1)) }
    if (pending.size > total) total = pending.size
    val current = pending.firstOrNull()
    val done = total - pending.size

    if (current == null) {
        LayerScreen(title = "بررسی پیامک‌ها", subtitle = "همه چیز مرتب است", onBack = onBack, bottomBar = { BigButton("برگشت", onClick = onBack) }) {
            Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(L.GreenSoft), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, null, tint = L.Green, modifier = Modifier.size(44.dp))
                }
                Text("پیامکی برای بررسی نمانده", style = lt(17.sp, FontWeight.Bold))
                Text("پیامک‌های بانکی تازه خودکار اینجا می‌آیند.", style = lt(13.sp, color = L.Muted))
            }
        }
        return
    }

    val kindCats = categories.filter { it.isIncome == !current.isExpense }.map { it.name }
    val guess = kindCats.firstOrNull { current.title.contains(it) || current.smsBody.contains(it) } ?: if (current.isExpense) "سایر" else "درآمد"
    var category by remember(current.id) { mutableStateOf(guess) }
    var title by remember(current.id) { mutableStateOf(current.title.ifBlank { "تراکنش بانکی" }) }
    var explain by remember(current.id) { mutableStateOf("") }
    val account = remember(current.id, cards) {
        cards.firstOrNull { it.bankName.contains(current.card) || current.card.contains(it.bankName) }?.bankName ?: current.card
    }

    LaunchedEffect(proposal) {
        proposal?.let { category = it.category; title = it.title }
    }
    LaunchedEffect(current.id) { viewModel.selectPendingTx(null) }

    val chips = (listOf(category) + kindCats).distinct().take(6)
    val date = JalaliDate.fromTimestamp(current.date)

    LayerScreen(
        title = "بررسی پیامک‌ها",
        subtitle = "${fa(pending.size)} پیامک منتظر تأیید · ${fa(done + 1)} از ${fa(total)}",
        onBack = onBack,
        bottomBar = {
            BigButton("ثبت و پیامک بعدی") {
                viewModel.approvePendingTransaction(current, title.trim().ifBlank { "تراکنش بانکی" }, category, current.amount, account, current.isExpense)
            }
            Box(
                Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).clickable { viewModel.rejectPendingTransaction(current.id) },
                contentAlignment = Alignment.Center
            ) { Text("مال من نیست", style = lt(15.sp, FontWeight.SemiBold, L.PrimaryText)) }
        }
    ) {
        Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0 until total) {
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (i <= done) L.Primary else L.Rail))
            }
        }
        LayerCard(padding = 16.dp) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(L.Bg).padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text("${current.card.ifBlank { "پیامک بانکی" }} · ${date.formatShort()} ${timeOfDay(current.date)}", style = lt(12.sp, FontWeight.Bold))
                Text(current.smsBody, style = lt(12.sp, color = L.Muted, lineHeight = 22.sp), modifier = Modifier.padding(top = 2.dp))
            }
            Row(Modifier.padding(top = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(money(current.amount), style = lt(32.sp, FontWeight.Light, letterSpacing = (-1).sp))
                    Text("تومان", style = lt(13.sp), modifier = Modifier.padding(bottom = 5.dp))
                }
                Box(
                    Modifier.height(28.dp).clip(CircleShape).background(if (current.isExpense) L.AmberSoft else L.GreenSoft).padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) { Text(if (current.isExpense) "هزینه" else "درآمد", style = lt(12.sp, FontWeight.Bold, if (current.isExpense) L.AmberText else L.GreenText)) }
            }
            Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(1.dp).background(L.Line))
            Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("عنوان", style = lt(14.sp, color = L.Muted), modifier = Modifier.width(60.dp))
                Box(Modifier.weight(1f)) {
                    BasicTextField(
                        value = title, onValueChange = { title = it }, singleLine = true,
                        textStyle = lt(14.sp, FontWeight.SemiBold), cursorBrush = SolidColor(L.Primary),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "عنوان تراکنش" }
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(L.Line))
            Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("حساب", style = lt(14.sp, color = L.Muted), modifier = Modifier.width(60.dp))
                AccountSwatch(account, cards, 24.dp, 16.dp)
                Spacer(Modifier.width(8.dp))
                Text(account.ifBlank { "—" }, style = lt(14.sp, FontWeight.SemiBold))
            }
        }

        Column(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(L.PrimaryTint).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, null, tint = L.PrimaryText, modifier = Modifier.size(16.dp))
                Text("پیشنهاد تراز", style = lt(13.sp, FontWeight.Bold, L.PrimaryText))
                Text(if (isCategorizing) "در حال بررسی…" else "دسته را انتخاب کنید", style = lt(12.sp, color = L.Muted))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { c ->
                    val on = c == category
                    Box(
                        Modifier.height(38.dp).clip(CircleShape).background(if (on) L.Primary else L.Surface)
                            .clickable { category = c }.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(c, style = lt(13.sp, if (on) FontWeight.Bold else FontWeight.Medium, if (on) Color.White else L.Ink)) }
                }
            }
            Row(
                Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).background(L.Surface).padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.weight(1f)) {
                    if (explain.isEmpty()) Text("یا بگو این خرید چی بود تا دسته را درست کنم…", style = lt(13.sp, color = L.Faint))
                    BasicTextField(
                        value = explain, onValueChange = { explain = it }, singleLine = true,
                        textStyle = lt(13.sp), cursorBrush = SolidColor(L.Primary),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "توضیح برای دستیار" }
                    )
                }
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(L.PrimarySoft)
                        .clickable(enabled = explain.isNotBlank() && !isCategorizing) { viewModel.categorizeWithAi(current, explain) }
                        .semantics { contentDescription = "ارسال توضیح" },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.AutoMirrored.Filled.Send, null, tint = L.PrimaryText, modifier = Modifier.size(16.dp)) }
            }
        }
    }
}

/**
 * آموزش قالب پیامک — پیامک‌های ناشناخته با پیام راهنما؛ «یاد بگیر» قالب را می‌سازد.
 */
@Composable
fun LayerSmsLearnScreen(
    viewModel: FinanceViewModel,
    onBack: () -> Unit
) {
    val unknown by viewModel.unknownSmsList.collectAsState()
    val loading by viewModel.isLearningLoading.collectAsState()
    val error by viewModel.learningError.collectAsState()
    val list = unknown.filter { !it.isIgnored }

    LayerScreen(
        title = "آموزش قالب پیامک",
        subtitle = if (list.isEmpty()) "پیامک ناشناخته‌ای نمانده" else "${fa(list.size)} پیامک ناشناخته",
        onBack = onBack
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(L.PrimarySoft).padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Default.AutoAwesome, null, tint = L.PrimaryText, modifier = Modifier.size(18.dp))
            Text(
                "این پیامک‌ها را نشناختیم. تراز قالبشان را یک بار یاد می‌گیرد تا پیامک‌های بعدی همین بانک خودکار خوانده شوند.",
                style = lt(12.sp, color = L.Info, lineHeight = 22.sp)
            )
        }
        if (loading) {
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircleProgress()
                Text("تراز در حال یادگیری قالب است…", style = lt(13.sp, FontWeight.SemiBold, L.PrimaryText))
            }
        }
        if (error != null) {
            Text(error ?: "", style = lt(12.sp, FontWeight.SemiBold, L.RedText), modifier = Modifier.padding(horizontal = 24.dp))
        }
        list.forEach { u ->
            LayerCard(padding = 16.dp) {
                val d = JalaliDate.fromTimestamp(u.timestamp)
                Text("پیامک · ${fa(u.senderNumber)} · ${d.formatShort()} ${timeOfDay(u.timestamp)}", style = lt(12.sp, color = L.Muted))
                Text(u.smsBody, style = lt(14.sp, lineHeight = 30.sp), modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton("یاد بگیر و ثبت کن", Modifier.weight(1f), enabled = !loading, height = 46.dp) { viewModel.learnNewFormatForUnknownSms(u) }
                    Box(
                        Modifier.height(46.dp).clip(RoundedCornerShape(16.dp)).background(L.PrimarySoft).clickable { viewModel.ignoreUnknownSms(u.id) }.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("نادیده", style = lt(14.sp, FontWeight.Bold, L.PrimaryText)) }
                }
            }
        }
    }
}

@Composable
private fun CircleProgress() {
    CircularProgressIndicator(Modifier.size(20.dp), color = L.Primary, strokeWidth = 2.dp)
}
