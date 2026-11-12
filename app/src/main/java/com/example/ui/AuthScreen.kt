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

    // معرفی برنامه (بار اول) با اجزای واقعی برنامه
    if (!onboarded) {
        com.example.ui.layer.WelcomeIntro(onFinish = { finishOnboarding() })
        return
    }
    // بدون ورود با شماره: صفحهٔ شروع طرح «صفحه خودِ لوگو»
    if (!com.example.Features.LOGIN) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        var currency by remember { mutableStateOf(com.example.data.Money.current) }
        com.example.ui.layer.WelcomeStart(
            name = fullName,
            onNameChange = { fullName = it },
            currency = currency,
            onCurrencyChange = { currency = it },
            onStart = {
                // شروع تازه: هنوز مبلغی ثبت نشده، پس فقط واحد ذخیره می‌شود
                com.example.data.Money.save(ctx, currency)
                viewModel.loginAsGuest(fullName.ifBlank { "کاربر تراز" })
            }
        )
        return
    }

    Box(modifier.fillMaxSize().background(L.Bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        run {
            AnimatedContent(targetState = step, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "auth_step") { s ->
                if (s == 1) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
                        Column(Modifier.padding(top = 56.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Image(
                                painter = painterResource(id = R.drawable.logo_taraz_app),
                                contentDescription = "تراز",
                                modifier = Modifier.size(64.dp).testTag("auth_logo")
                            )
                            Text("به تراز خوش آمدید", style = lt(26.sp, FontWeight.Bold), modifier = Modifier.padding(top = 8.dp))
                            Text("حساب‌ها، اقساط و هزینه‌های روزانهٔ خود را یک‌جا مدیریت کنید.", style = lt(14.sp, color = L.Muted, lineHeight = 26.sp))
                        }
                        Spacer(Modifier.height(28.dp))
                        LField(fullName, { fullName = it; errorMessage = null }, "نام شما", placeholder = "مثلاً مریم")
                        if (!com.example.Features.LOGIN) {
                            // ورود با شماره فعلاً خاموش است: فقط نام، و همه‌چیز روی همین گوشی
                            Text(
                                "اطلاعات شما فقط روی همین دستگاه ذخیره می‌شود. ورود با شمارهٔ موبایل به‌زودی فعال می‌شود.",
                                style = lt(12.sp, color = L.Muted, lineHeight = 20.sp), modifier = Modifier.padding(top = 10.dp)
                            )
                            Spacer(Modifier.height(32.dp))
                            BigButton("شروع", Modifier.testTag("auth_guest_button")) {
                                viewModel.loginAsGuest(fullName.ifBlank { "کاربر تراز" })
                            }
                        } else {
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
                            "بدون ورود، داده‌ها فقط روی همین دستگاه ذخیره می‌شود.",
                            style = lt(12.sp, color = L.Muted), textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                        )
                        }
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


