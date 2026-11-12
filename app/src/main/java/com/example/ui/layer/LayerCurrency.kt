package com.example.ui.layer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppCurrency

/** توضیح کوتاه هر واحد پول برای صفحهٔ انتخاب */
private fun AppCurrency.hint(): String = when (this) {
    AppCurrency.TOMAN -> "پیامک بانک ÷ ۱۰ ثبت می‌شود"
    AppCurrency.RIAL -> "دقیقاً همان عدد پیامک بانک"
    AppCurrency.USD -> "با سنت · بدون پیامک بانک"
}

/** همان مبلغ نمونه به هر واحد: ۱۲٬۵۰۰ تومان، ۱۲۵٬۰۰۰ ریال، ۱۲٫۵۰ دلار */
private fun AppCurrency.sample(): String = when (this) {
    AppCurrency.TOMAN -> com.example.data.Money.withLabel(12_500, this)
    AppCurrency.RIAL -> com.example.data.Money.withLabel(125_000, this)
    AppCurrency.USD -> com.example.data.Money.withLabel(1_250, this)
}

/**
 * فهرست تومان / ریال / دلار با دکمهٔ انتخاب، توضیح، و همان مبلغ نمونه به هر واحد؛ در صفحهٔ شروع و
 * تنظیمات. داخل یک کارت سفید گذاشته می‌شود.
 */
@Composable
fun CurrencyOptions(selected: AppCurrency, onSelect: (AppCurrency) -> Unit) {
    Column(Modifier.selectableGroup()) {
        AppCurrency.entries.forEachIndexed { i, c ->
            val on = c == selected
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp)
                    .selectable(selected = on, role = Role.RadioButton) { onSelect(c) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(L.Surface)
                        .border(if (on) 7.dp else 2.dp, if (on) L.Primary else L.Placeholder, CircleShape)
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(c.label, style = lt(16.sp, FontWeight.Bold, if (on) L.PrimaryText else L.Ink))
                    Text(c.hint(), style = lt(12.sp, color = L.Muted))
                }
                Text(c.sample(), style = lt(13.sp, FontWeight.SemiBold, L.Muted), maxLines = 1)
            }
            if (i < AppCurrency.entries.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(L.Line))
        }
    }
}

/**
 * تغییر واحد پول از تنظیمات. همهٔ مبلغ‌های ثبت‌شده به واحد تازه تبدیل می‌شوند؛ اگر پای دلار در میان
 * باشد، نرخ دلار (پیش‌فرض: قیمت روز) پرسیده می‌شود.
 */
@Composable
fun CurrencySheet(
    current: AppCurrency,
    suggestedUsdToman: Long?,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onOpenBackup: () -> Unit,
    onConvert: (AppCurrency, Long?) -> Unit
) {
    var choice by remember { mutableStateOf(current) }
    // نرخ دلار همیشه به تومان پرسیده می‌شود، مستقل از واحد دفتر
    var rateText by remember {
        mutableStateOf(suggestedUsdToman?.let { com.example.data.Money.format(it, AppCurrency.TOMAN) }.orEmpty())
    }
    val needsRate = choice != current && (choice == AppCurrency.USD || current == AppCurrency.USD)
    val rate = com.example.data.Money.parse(rateText, AppCurrency.TOMAN)
    val ok = choice != current && (!needsRate || rate > 0) && !busy

    LayerSheet(
        title = "واحد پول",
        onDismiss = onDismiss,
        topGap = 60.dp,
        bottomBar = {
            BigButton(
                when {
                    busy -> "در حال تبدیل…"
                    choice == current -> "واحد فعلی: ${current.label}"
                    else -> "تبدیل همهٔ مبالغ به ${choice.label}"
                },
                enabled = ok
            ) { onConvert(choice, if (needsRate) rate else null) }
        }
    ) {
        CurrencyOptions(choice) { choice = it }
        if (choice != current) {
            val how = when {
                current == AppCurrency.TOMAN && choice == AppCurrency.RIAL -> "همهٔ مبالغ (موجودی حساب‌ها، تراکنش‌ها، اقساط، چک‌ها و …) در ۱۰ ضرب می‌شوند."
                current == AppCurrency.RIAL && choice == AppCurrency.TOMAN -> "همهٔ مبالغ بر ۱۰ تقسیم و به نزدیک‌ترین تومان گرد می‌شوند."
                else -> "همهٔ مبالغ با نرخ دلار زیر تبدیل می‌شوند."
            }
            Callout(
                "مبالغ تبدیل می‌شوند",
                "$how پیش از تغییر، یک نسخهٔ پشتیبان بگیرید.",
                L.AmberSoft, L.AmberInk
            )
            if (needsRate) {
                LField(
                    rateText, { rateText = com.example.data.Money.inputText(it, AppCurrency.TOMAN) },
                    "نرخ هر دلار (تومان)", placeholder = "مثلاً ۹۰٬۰۰۰",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    hint = if (suggestedUsdToman != null) "قیمت روز دلار پیشنهاد شده؛ در صورت نیاز تغییر دهید." else "قیمت روز در دسترس نیست؛ نرخ را وارد کنید."
                )
            }
            Text(
                "ساخت پشتیبان",
                style = lt(13.sp, FontWeight.SemiBold, L.PrimaryText),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpenBackup).padding(vertical = 6.dp)
            )
        }
        error?.let { Text(it, style = lt(12.sp, color = L.RedText)) }
        Spacer(Modifier.height(4.dp))
    }
}
