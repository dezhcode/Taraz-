package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import com.example.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableIntStateOf(1) } // 1: Info input, 2: OTP verification
    var fullName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var otpInput by remember { mutableStateOf("") }
    
    var isVerifyingOtp by remember { mutableStateOf(false) }
    var isSendingOtp by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Resend countdown timer in seconds (120s = 2 minutes)
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, EbayBorderGray),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // The wordmark, not a generic padlock — this is the first screen
                // a new user sees and the only place the full logo appears.
                Image(
                    painter = painterResource(id = R.drawable.logo_taraz_full),
                    contentDescription = "تراز",
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("auth_logo")
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (step == 1) "ورود / ثبت‌نام" else "تأیید کد ۵ رقمی",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = EbayDarkText,
                        fontSize = 22.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (step == 1) 
                        "برای ورود می‌توانید نام و شماره موبایل خود را وارد کرده یا مستقیماً به عنوان مهمان وارد شوید."
                    else 
                        "کد ۵ رقمی ارسال شده به شماره $phoneNumber را وارد کنید.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = EbaySecondaryText,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                AnimatedContent(
                    targetState = step,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "auth_step_transition"
                ) { targetStep ->
                    if (targetStep == 1) {
                        // Step 1: Input Name & Phone
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Full Name Input
                            OutlinedTextField(
                                value = fullName,
                                onValueChange = { 
                                    fullName = it 
                                    errorMessage = null
                                },
                                label = { Text("نام و نام خانوادگی", fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = EbayBluePrimary
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EbayBluePrimary,
                                    unfocusedBorderColor = EbayBorderGray,
                                    focusedLabelColor = EbayBluePrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_name_input")
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Phone Number Input
                            OutlinedTextField(
                                value = phoneNumber,
                                onValueChange = { input ->
                                    if (input.length <= 11) {
                                        phoneNumber = input.filter { it.isDigit() }
                                        errorMessage = null
                                    }
                                },
                                label = { Text("شماره موبایل", fontSize = 13.sp) },
                                placeholder = { Text("09123456789", color = EbaySecondaryText.copy(alpha = 0.5f)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = EbayBluePrimary
                                    )
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EbayBluePrimary,
                                    unfocusedBorderColor = EbayBorderGray,
                                    focusedLabelColor = EbayBluePrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_phone_input")
                            )

                            if (errorMessage != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = errorMessage!!,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = EbayRed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Send OTP Button
                            Button(
                                onClick = {
                                    if (fullName.isBlank()) {
                                        errorMessage = "لطفاً نام و نام خانوادگی خود را وارد کنید."
                                        return@Button
                                    }
                                    if (phoneNumber.length != 11 || !phoneNumber.startsWith("09")) {
                                        errorMessage = "لطفاً یک شماره موبایل معتبر ۱۱ رقمی وارد نمایید."
                                        return@Button
                                    }

                                    isSendingOtp = true
                                    errorMessage = null
                                    viewModel.requestOtp(fullName, phoneNumber) { success, msg ->
                                        isSendingOtp = false
                                        if (success) {
                                            statusMessage = msg
                                            step = 2
                                            timerSeconds = 120
                                            isTimerRunning = true
                                        } else {
                                            errorMessage = msg
                                        }
                                    }
                                },
                                enabled = !isSendingOtp,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EbayBluePrimary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("auth_send_otp_button")
                            ) {
                                if (isSendingOtp) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("در حال ارسال پیامک...", style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "ارسال کد تأیید ۵ رقمی",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 15.sp
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Separator
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f),
                                    color = EbayBorderGray
                                )
                                Text(
                                    text = "یا",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = EbaySecondaryText,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f),
                                    color = EbayBorderGray
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Guest Login Button
                            OutlinedButton(
                                onClick = {
                                    viewModel.loginAsGuest(fullName.ifBlank { "کاربر مهمان" })
                                },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.5.dp, EbayBluePrimary.copy(alpha = 0.35f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = EbayBlueLight.copy(alpha = 0.45f),
                                    contentColor = EbayBluePrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("auth_guest_login_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = EbayBluePrimary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "ورود به عنوان مهمان (بدون شماره)",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = EbayBluePrimary,
                                            fontSize = 14.sp
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        // Step 2: Input 5-Digit OTP Code
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Phone & Edit Option
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(EbayBlueLight)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "شماره: $phoneNumber",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = EbayBluePrimary,
                                        fontSize = 13.sp
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "ویرایش شماره",
                                    tint = EbayBluePrimary,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            step = 1
                                            otpInput = ""
                                            errorMessage = null
                                        }
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // 5 Digit Box UI (Left to Right filling in Latin digits)
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    for (i in 0 until 5) {
                                        val digitChar = otpInput.getOrNull(i)?.toString() ?: ""
                                        val isFocused = otpInput.length == i || (i == 4 && otpInput.length == 5)

                                        Box(
                                            modifier = Modifier
                                                .size(50.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(SurfaceWhite)
                                                .border(
                                                    BorderStroke(
                                                        width = if (isFocused) 2.dp else 1.dp,
                                                        color = if (isFocused) EbayBluePrimary else EbayBorderGray
                                                    ),
                                                    shape = RoundedCornerShape(12.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = digitChar, // Standard Latin digit 0-9
                                                style = MaterialTheme.typography.titleLarge.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = EbayDarkText,
                                                    fontSize = 22.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            // Hidden TextField for 5-digit OTP input
                            val focusRequester = remember { FocusRequester() }
                            LaunchedEffect(Unit) {
                                delay(200)
                                focusRequester.requestFocus()
                            }

                            OutlinedTextField(
                                value = otpInput,
                                onValueChange = { newValue ->
                                    if (newValue.length <= 5 && newValue.all { it.isDigit() }) {
                                        otpInput = newValue
                                        errorMessage = null
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .focusRequester(focusRequester)
                                    .testTag("auth_otp_hidden_input")
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Resend Timer & Button
                            if (isTimerRunning) {
                                val minutes = timerSeconds / 60
                                val secs = timerSeconds % 60
                                val timeStr = String.format("%02d:%02d", minutes, secs)
                                Text(
                                    text = "ارسال مجدد کد تا $timeStr دیگر",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = EbaySecondaryText,
                                        fontSize = 12.sp
                                    )
                                )
                            } else {
                                TextButton(
                                    onClick = {
                                        isSendingOtp = true
                                        errorMessage = null
                                        viewModel.requestOtp(fullName, phoneNumber) { success, msg ->
                                            isSendingOtp = false
                                            if (success) {
                                                statusMessage = msg.ifBlank { "کد تأیید جدید مجدداً ارسال شد." }
                                                timerSeconds = 120
                                                isTimerRunning = true
                                            } else {
                                                errorMessage = msg
                                            }
                                        }
                                    },
                                    modifier = Modifier.testTag("auth_resend_otp_button")
                                ) {
                                    Text(
                                        text = "ارسال مجدد کد تأیید",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = EbayBluePrimary,
                                            fontSize = 13.sp
                                        )
                                    )
                                }
                            }

                            if (errorMessage != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = errorMessage!!,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = EbayRed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Confirm & Login Button
                            Button(
                                onClick = {
                                    if (otpInput.length != 5) {
                                        errorMessage = "لطفاً کد ۵ رقمی را کامل وارد نمایید."
                                        return@Button
                                    }
                                    isVerifyingOtp = true
                                    errorMessage = null
                                    // The code is verified by the server; the app never holds it.
                                    viewModel.verifyOtp(fullName, phoneNumber, otpInput) { success, msg ->
                                        isVerifyingOtp = false
                                        if (!success) {
                                            otpInput = ""
                                            errorMessage = msg
                                        }
                                    }
                                },
                                enabled = otpInput.length == 5 && !isVerifyingOtp,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EbayBluePrimary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("auth_verify_otp_button")
                            ) {
                                if (isVerifyingOtp) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = if (isVerifyingOtp) "در حال بررسی کد..." else "تأیید و ورود به اپلیکیشن",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 15.sp
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            TextButton(
                                onClick = {
                                    viewModel.loginAsGuest(fullName.ifBlank { "کاربر مهمان" })
                                },
                                modifier = Modifier.testTag("auth_step2_guest_button")
                            ) {
                                Text(
                                    text = "انصراف و ورود مستقیم به عنوان مهمان",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = EbaySecondaryText,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.5.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

