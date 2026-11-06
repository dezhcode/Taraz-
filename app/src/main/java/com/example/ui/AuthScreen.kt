package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.layer.BigButton
import com.example.ui.layer.L
import com.example.ui.layer.LField
import com.example.ui.layer.SoftBigButton
import com.example.ui.layer.cardSurface
import com.example.ui.layer.fa
import com.example.ui.layer.lt
import kotlinx.coroutines.delay

private const val PREFS = "taraz_prefs"
private const val KEY_ONBOARDED = "onboarding_done"

/**
 * ورود به تراز — طرح نهایی: سه اسلاید معرفی (فقط بار اول)، سپس شمارهٔ موبایل،
 * سپس کد ۶ رقمی. «ادامه بدون ورود» کاربر را مهمان می‌کند.
 */
@Composable
fun AuthScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE) }
    var onboarded by remember { mutableStateOf(prefs.getBoolean(KEY_ONBOARDED, false)) }
    fun finishOnboarding() {
        prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
        onboarded = true
    }

    var step by remember { mutableIntStateOf(1) } // 1: نام و شماره، 2: کد
    var fullName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var otpInput by remember { mutableStateOf("") }
    var isSendingOtp by remember { mutableStateOf(false) }
    var isVerifyingOtp by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var timerSeconds by remember { mutableIntStateOf(120) }
    var isTimerRunning by remember { mutableStateOf(false) }

    LaunchedEffect(isTimerRunning, timerSeconds) {
        if (isTimerRunning && timerSeconds > 0) {
            delay(1000L)
            timerSeconds -= 1
        } else if (timerSeconds == 0) {
            isTimerRunning = false
        }
    }

    fun sendCode() {
        if (fullName.isBlank()) { errorMessage = "لطفاً نام خود را وارد کنید."; return }
        if (phoneNumber.length != 11 || !phoneNumber.startsWith("09")) {
            errorMessage = "لطفاً شمارهٔ موبایل معتبر ۱۱ رقمی (۰۹…) وارد کنید."
            return
        }
        isSendingOtp = true
        errorMessage = null
        viewModel.requestOtp(fullName, phoneNumber) { success, msg ->
            isSendingOtp = false
            if (success) {
                step = 2
                otpInput = ""
                timerSeconds = 120
                isTimerRunning = true
            } else {
                errorMessage = msg
            }
        }
    }

    Box(modifier.fillMaxSize().background(L.Bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        if (!onboarded) {
            OnboardingPager(
                onFinish = { finishOnboarding() },
                onLogin = { finishOnboarding() }
            )
        } else {
            AnimatedContent(targetState = step, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "auth_step") { s ->
                if (s == 1) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
                        Column(Modifier.padding(top = 56.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(L.Primary), contentAlignment = Alignment.Center) {
                                Image(
                                    painter = painterResource(id = R.drawable.logo_taraz_mark),
                                    contentDescription = "تراز",
                                    colorFilter = ColorFilter.tint(Color.White),
                                    modifier = Modifier.size(40.dp).testTag("auth_logo")
                                )
                            }
                            Text("به تراز خوش آمدید", style = lt(26.sp, FontWeight.Bold), modifier = Modifier.padding(top = 8.dp))
                            Text("حساب‌ها، اقساط و خرج روزانه‌تان را یک‌جا ببینید.", style = lt(14.sp, color = L.Muted, lineHeight = 26.sp))
                        }
                        Spacer(Modifier.height(28.dp))
                        LField(fullName, { fullName = it; errorMessage = null }, "نام شما", placeholder = "مثلاً مریم")
                        Spacer(Modifier.height(12.dp))
                        LField(
                            phoneNumber, { v -> phoneNumber = v.filter { it.isDigit() }.take(11); errorMessage = null }, "شمارهٔ موبایل",
                            placeholder = "09123456789",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            hint = "یک کد ۶ رقمی به این شماره پیامک می‌شود."
                        )
                        if (errorMessage != null) {
                            Text(errorMessage!!, style = lt(12.sp, FontWeight.SemiBold, L.RedText), modifier = Modifier.padding(top = 10.dp))
                        }
                        Spacer(Modifier.height(32.dp))
                        BigButton(
                            if (isSendingOtp) "در حال ارسال پیامک…" else "دریافت کد تأیید",
                            Modifier.testTag("auth_send_otp_button"), enabled = !isSendingOtp
                        ) { sendCode() }
                        Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.weight(1f).height(1.dp).background(L.Disabled))
                            Text("یا", style = lt(12.sp, color = L.Muted))
                            Box(Modifier.weight(1f).height(1.dp).background(L.Disabled))
                        }
                        SoftBigButton("ادامه بدون ورود", Modifier.testTag("auth_guest_button")) {
                            viewModel.loginAsGuest(fullName.ifBlank { "کاربر مهمان" })
                        }
                        Text(
                            "بدون ورود، داده‌ها فقط روی همین گوشی می‌ماند.",
                            style = lt(12.sp, color = L.Muted), textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                        )
                    }
                } else {
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
                        Box(
                            Modifier.padding(top = 16.dp).height(40.dp).clip(RoundedCornerShape(12.dp))
                                .clickable { step = 1; errorMessage = null }.padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("‹ ویرایش شماره", style = lt(14.sp, FontWeight.SemiBold, L.PrimaryText)) }

                        Text("کد تأیید را وارد کنید", style = lt(24.sp, FontWeight.Bold), modifier = Modifier.padding(top = 20.dp))
                        Text(
                            "کد ۶ رقمی به ${fa(phoneNumber)} پیامک شد.",
                            style = lt(14.sp, color = L.Muted, lineHeight = 26.sp), modifier = Modifier.padding(top = 8.dp)
                        )

                        // ۶ خانه + فیلد پنهان
                        Box(Modifier.padding(top = 24.dp).fillMaxWidth()) {
                            BasicTextField(
                                value = otpInput,
                                onValueChange = { v -> if (v.length <= 6 && v.all { it.isDigit() }) { otpInput = v; errorMessage = null } },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                cursorBrush = SolidColor(Color.Transparent),
                                textStyle = lt(1.sp, color = Color.Transparent),
                                modifier = Modifier.fillMaxWidth().height(56.dp).focusRequester(focus).testTag("auth_otp_hidden_input")
                            )
                            Row(
                                Modifier.fillMaxWidth().semantics { contentDescription = "کد تأیید" },
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (i in 0 until 6) {
                                    val active = i == otpInput.length
                                    Box(
                                        Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(14.dp))
                                            .background(if (active) L.PrimarySoft else L.Surface)
                                            .clickable { runCatching { focus.requestFocus() } },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(otpInput.getOrNull(i)?.let { fa(it) } ?: "", style = lt(24.sp, FontWeight.SemiBold))
                                    }
                                }
                            }
                        }

                        if (errorMessage != null) {
                            Text(errorMessage!!, style = lt(12.sp, FontWeight.SemiBold, L.RedText), modifier = Modifier.padding(top = 12.dp))
                        }

                        if (isTimerRunning) {
                            val mm = timerSeconds / 60
                            val ss = timerSeconds % 60
                            Text(
                                "ارسال دوبارهٔ کد تا ${fa(String.format("%d:%02d", mm, ss))} دیگر",
                                style = lt(13.sp, color = L.Muted), modifier = Modifier.padding(top = 14.dp)
                            )
                        } else {
                            Box(
                                Modifier.padding(top = 8.dp).height(40.dp).clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = !isSendingOtp) { sendCode() }
                                    .padding(horizontal = 6.dp).testTag("auth_resend_otp_button"),
                                contentAlignment = Alignment.CenterStart
                            ) { Text("ارسال دوبارهٔ کد", style = lt(13.sp, FontWeight.Bold, L.PrimaryText)) }
                        }

                        Spacer(Modifier.height(32.dp))
                        BigButton(
                            when {
                                isVerifyingOtp -> "در حال بررسی کد…"
                                otpInput.length == 6 -> "تأیید و ورود"
                                else -> "کد ۶ رقمی را وارد کنید"
                            },
                            Modifier.testTag("auth_verify_otp_button"),
                            enabled = otpInput.length == 6 && !isVerifyingOtp
                        ) {
                            isVerifyingOtp = true
                            errorMessage = null
                            // کد را سرور بررسی می‌کند؛ برنامه آن را نگه نمی‌دارد.
                            viewModel.verifyOtp(fullName, phoneNumber, otpInput) { success, msg ->
                                isVerifyingOtp = false
                                if (!success) { otpInput = ""; errorMessage = msg }
                            }
                        }
                        Box(
                            Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp))
                                .clickable { viewModel.loginAsGuest(fullName.ifBlank { "کاربر مهمان" }) }
                                .testTag("auth_step2_guest_button"),
                            contentAlignment = Alignment.Center
                        ) { Text("انصراف و ورود بدون حساب", style = lt(14.sp, FontWeight.SemiBold, L.PrimaryText)) }
                    }
                }
            }
        }
    }
}

/** سه اسلاید معرفی طرح نهایی؛ تصویر هر اسلاید با کامپوننت‌های ساده ساخته شده */
@Composable
private fun OnboardingPager(onFinish: () -> Unit, onLogin: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val titles = listOf(
        "همهٔ حساب‌هایتان، یک نگاه",
        "پیامک بانک، خودش ثبت می‌شود",
        "از دستیار تراز بپرسید"
    )
    val bodies = listOf(
        "کارت‌ها، پول نقد و اقساط کنار هم؛ با روزشماری که نشان می‌دهد امروز چقدر جا دارید.",
        "هر پیامک برداشت یا واریز اول برای تأیید شما می‌آید. تراز قالب پیامک هر بانک را یاد می‌گیرد.",
        "«این ماه کجا خرج کردم؟» را بپرسید؛ جواب را با عدد و نمودار می‌گیرید. هر تغییری فقط با تأیید شما."
    )
    val last = page == 2

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(L.Primary), contentAlignment = Alignment.Center) {
                Image(painterResource(id = R.drawable.logo_taraz_mark), null, colorFilter = ColorFilter.tint(Color.White), modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onFinish).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text("رد شدن", style = lt(14.sp, FontWeight.SemiBold, L.PrimaryText))
            }
        }

        Box(Modifier.fillMaxWidth().height(380.dp), contentAlignment = Alignment.Center) {
            AnimatedContent(targetState = page, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "onb") { p ->
                Column(Modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (p) {
                        0 -> {
                            Box(Modifier.fillMaxWidth().height(150.dp)) {
                                Box(Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(50.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF0F3B66)))
                                Box(Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp).fillMaxWidth().height(50.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFB4232F)))
                                Column(
                                    Modifier.padding(top = 16.dp).fillMaxWidth().height(134.dp).clip(RoundedCornerShape(22.dp)).background(L.Primary).padding(horizontal = 18.dp, vertical = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("موجودی کل", style = lt(12.sp, color = L.OnPrimarySoft))
                                    Text("۱۸۴٬۶۵۰٬۰۰۰", style = lt(28.sp, FontWeight.Light, Color.White, letterSpacing = (-1).sp))
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth().height(70.dp).cardSurface(20.dp).padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom
                            ) {
                                listOf(30, 10, 12, 18, 9, 22, 14).forEachIndexed { i, h ->
                                    Box(Modifier.weight(1f).height(h.dp).clip(RoundedCornerShape(4.dp)).background(if (i == 0) L.Amber else if (i == 6) L.Primary else L.BarIdle))
                                }
                            }
                        }
                        1 -> {
                            Column(Modifier.fillMaxWidth().cardSurface(18.dp).padding(horizontal = 14.dp, vertical = 12.dp)) {
                                Text("بانک ملت", style = lt(12.sp, FontWeight.Bold))
                                Text("برداشت: ۲۵۳,۴۰۰\nداروخانه دکتر شفا", style = lt(12.sp, color = L.Muted, lineHeight = 22.sp))
                            }
                            Text("↓", style = lt(22.sp, FontWeight.Bold, L.Primary), modifier = Modifier.align(Alignment.CenterHorizontally))
                            Row(Modifier.fillMaxWidth().cardSurface(18.dp).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("داروخانه", style = lt(14.sp, FontWeight.SemiBold))
                                    Text("سلامت · ملت", style = lt(12.sp, FontWeight.SemiBold, L.AmberText))
                                }
                                Text("−۲۵۳٬۴۰۰", style = lt(14.sp, FontWeight.SemiBold))
                            }
                        }
                        else -> {
                            Box(
                                Modifier.align(Alignment.Start).fillMaxWidth(0.8f).clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 6.dp)).background(L.Surface).padding(horizontal = 12.dp, vertical = 10.dp)
                            ) { Text("این ماه کجا بیشتر خرج کردم؟", style = lt(13.sp, lineHeight = 23.sp)) }
                            Box(
                                Modifier.align(Alignment.End).fillMaxWidth(0.88f).clip(RoundedCornerShape(16.dp, 16.dp, 6.dp, 16.dp)).background(L.PrimarySoft).padding(horizontal = 12.dp, vertical = 10.dp)
                            ) { Text("بیشترش خوراک بوده: ۹٫۴ میلیون. شام بیرون نسبت به ماه قبل ۳۰٪ بیشتر شده.", style = lt(13.sp, color = L.Info, lineHeight = 23.sp)) }
                            Box(Modifier.align(Alignment.End).height(32.dp).clip(CircleShape).background(L.Primary).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                                Text("گزارش کامل", style = lt(12.sp, FontWeight.Bold, Color.White))
                            }
                        }
                    }
                }
            }
        }

        Text(titles[page], style = lt(26.sp, FontWeight.Bold, lineHeight = 36.sp))
        Text(bodies[page], style = lt(15.sp, color = L.Muted, lineHeight = 28.sp), modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.Center) {
            for (i in 0 until 3) {
                Box(
                    Modifier.padding(horizontal = 3.dp).height(8.dp).width(if (i == page) 22.dp else 8.dp)
                        .clip(CircleShape).background(if (i == page) L.Primary else L.Rail)
                )
            }
        }
        BigButton(if (last) "شروع کنیم" else "بعدی", height = 56.dp) { if (last) onFinish() else page++ }
        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onLogin), contentAlignment = Alignment.Center) {
            Text("حساب دارم، ورود", style = lt(15.sp, FontWeight.SemiBold, L.PrimaryText))
        }
    }
}
