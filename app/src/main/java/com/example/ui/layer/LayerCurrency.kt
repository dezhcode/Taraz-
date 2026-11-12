package com.example.ui.layer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppCurrency

/** توضیح کوتاه هر واحد پول برای صفحهٔ انتخاب */
private fun AppCurrency.hint(): String = when (this) {
    AppCurrency.TOMAN -> "مبلغ پیامک‌های بانکی تقسیم بر ۱۰ ثبت می‌شود."
    AppCurrency.RIAL -> "مبلغ پیامک‌های بانکی دقیقاً همان‌طور که در پیامک آمده ثبت می‌شود."
    AppCurrency.USD -> "با دو رقم اعشار (سنت). پیامک‌های بانکی ریالی خوانده نمی‌شوند."
}

/** سه گزینهٔ تومان / ریال / دلار با توضیح؛ در صفحهٔ شروع و تنظیمات */
@Composable
fun CurrencyOptions(selected: AppCurrency, onSelect: (AppCurrency) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppCurrency.entries.forEach { c ->
            val on = c == selected
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (on) L.PrimarySoft else L.Surface)
                    .border(1.dp, if (on) L.Primary else L.Rail, RoundedCornerShape(14.dp))
                    .clickable { onSelect(c) }
                    .semantics { role = Role.RadioButton; this.selected = on }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(c.label, style = lt(15.sp, FontWeight.Bold, if (on) L.PrimaryText else L.Ink))
                    Text(c.hint(), style = lt(12.sp, color = L.Muted, lineHeight = 19.sp))
                }
            }
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
