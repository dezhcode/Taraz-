package com.example.ui.taraz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.FinanceViewModel
import kotlinx.coroutines.delay

/** Sign-in: name and phone, then the 6-digit code; or continue without an account. */
@Composable
fun TarazAuthScreen(vm: FinanceViewModel) {
    var step by remember { mutableIntStateOf(1) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var seconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(seconds) {
        if (seconds > 0) {
            delay(1000)
            seconds -= 1
        }
    }

    fun send() {
        when {
            name.isBlank() -> error = "نام خود را بنویسید."
            phone.length != 11 || !phone.startsWith("09") -> error = "شمارهٔ موبایل باید ۱۱ رقم و با ۰۹ شروع شود."
            else -> {
                busy = true
                error = null
                vm.requestOtp(name.trim(), phone) { ok, msg ->
                    busy = false
                    if (ok) {
                        step = 2
                        code = ""
                        seconds = 120
                    } else {
                        error = msg
                    }
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Tz.Ground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        if (step == 1) {
            Spacer(Modifier.height(40.dp))
            Box(
                Modifier
                    .size(64.dp)
                    .shadow(12.dp, RoundedCornerShape(20.dp), ambientColor = Tz.Cobalt, spotColor = Tz.Cobalt)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Tz.Cobalt),
                contentAlignment = Alignment.Center
            ) { Text("تراز", style = tzText(20.sp, FontWeight.Bold, Color.White)) }
            Spacer(Modifier.height(22.dp))
            Text("به تراز خوش آمدید", style = tzText(26.sp, FontWeight.Bold))
            Text("حساب‌ها، اقساط و خرج روزانه‌تان را یک‌جا ببینید.", style = tzText(14.sp, color = Tz.Muted, lineHeight = 24.sp))
            Spacer(Modifier.height(28.dp))
            TzTextField("نام شما", name, { name = it.take(40); error = null }, "مثلاً مریم")
            Spacer(Modifier.height(12.dp))
            TzTextField(
                "شمارهٔ موبایل",
                phone,
                { phone = TzFormat.digitsToLatin(it).take(11); error = null },
                "۰۹۱۲۳۴۵۶۷۸۹",
                numeric = false
            )
            Text("یک کد ۶ رقمی به این شماره پیامک می‌شود.", style = tzText(12.sp, color = Tz.Muted), modifier = Modifier.padding(top = 6.dp))
            ErrorLine(error)
            Spacer(Modifier.height(28.dp))
            PrimaryButton(if (busy) "در حال ارسال کد…" else "دریافت کد تأیید", onClick = { send() }, enabled = !busy)
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(1.dp).background(Tz.Border))
                Text("یا", style = tzText(12.sp, color = Tz.Muted), modifier = Modifier.padding(horizontal = 10.dp))
                Box(Modifier.weight(1f).height(1.dp).background(Tz.Border))
            }
            SecondaryButton("ادامه بدون ورود", onClick = { vm.loginAsGuest(name.ifBlank { "کاربر مهمان" }) }, height = 50.dp)
            Text(
                "بدون ورود، داده‌ها فقط روی همین گوشی می‌ماند.",
                style = tzText(12.sp, color = Tz.Muted, align = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        } else {
            TextAction("ویرایش شماره", onClick = { step = 1; error = null })
            Spacer(Modifier.height(20.dp))
            Text("کد تأیید را وارد کنید", style = tzText(24.sp, FontWeight.Bold))
            Text(
                "کد ۶ رقمی به ${TzFormat.fa(phone)} پیامک شد.",
                style = tzText(14.sp, color = Tz.Muted, lineHeight = 24.sp)
            )
            Spacer(Modifier.height(24.dp))
            CodeBoxes(code) { code = TzFormat.digitsToLatin(it).take(6); error = null }
            ErrorLine(error)
            Spacer(Modifier.height(14.dp))
            if (seconds > 0) {
                Text("ارسال دوبارهٔ کد تا ${TzFormat.fa("%d:%02d".format(seconds / 60, seconds % 60))} دیگر", style = tzText(13.sp, color = Tz.Muted))
            } else {
                TextAction("ارسال دوبارهٔ کد", onClick = { send() })
            }
            Spacer(Modifier.height(40.dp))
            PrimaryButton(
                when {
                    busy -> "در حال بررسی…"
                    code.length < 6 -> "کد ۶ رقمی را وارد کنید"
                    else -> "تأیید و ورود"
                },
                enabled = code.length == 6 && !busy,
                onClick = {
                    busy = true
                    error = null
                    vm.verifyOtp(name.trim(), phone, code) { ok, msg ->
                        busy = false
                        if (!ok) {
                            code = ""
                            error = msg
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ErrorLine(error: String?) {
    if (error != null) {
        Text(
            error,
            style = tzText(13.sp, FontWeight.SemiBold, Tz.RedText, lineHeight = 22.sp),
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Tz.RedTint)
                .padding(12.dp)
        )
    }
}

/** Six boxes over one invisible field, so paste and SMS autofill still work. */
@Composable
private fun CodeBoxes(code: String, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box {
            BasicTextField(
                value = code,
                onValueChange = onChange,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                textStyle = tzText(1.sp, color = Color.Transparent),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent),
                modifier = Modifier
                    .matchParentSizeSafe()
                    .focusRequester(focus)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until 6) {
                    val filled = i < code.length
                    val active = i == code.length
                    Box(
                        Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Tz.Surface)
                            .border(if (active) 2.dp else 1.dp, if (active) Tz.Cobalt else if (filled) Tz.BarSoft else Tz.Border, RoundedCornerShape(14.dp))
                            .tzClick { runCatching { focus.requestFocus() } },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (filled) TzFormat.fa(code[i]) else "", style = tzText(24.sp, FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}

private fun Modifier.matchParentSizeSafe(): Modifier = this
    .fillMaxWidth()
    .height(56.dp)

