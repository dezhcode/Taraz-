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
import com.example.data.SmsIngest
import com.example.data.PendingTransaction
import com.example.sms.permission.SmsPermissionHelper
import com.example.sms.presentation.SmsParserViewModel
import com.example.ui.FinanceViewModel
import com.example.utils.JalaliDate

internal fun bankColor(name: String): Color = when {
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
    val senders by viewModel.bankSenders.collectAsState()
    val account = remember(current.id, cards, senders) { SmsIngest.accountFor(current, cards, senders) }

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
            // توضیح به دستیار فقط وقتی دستیار روشن است
            if (com.example.Features.AI) Row(
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
