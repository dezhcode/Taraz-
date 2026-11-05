package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.utils.BiometricHelper

@Composable
fun BiometricLockScreen(
    onUnlockSuccess: () -> Unit,
    onLockUnavailable: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var authError by remember { mutableStateOf<String?>(null) }
    var isUnavailableOnDevice by remember { mutableStateOf(false) }

    // Pulsing animation for the biometric ring
    val infiniteTransition = rememberInfiniteTransition(label = "BiometricPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseRing"
    )

    fun triggerBiometricAuthentication() {
        if (activity == null) {
            authError = "نمایش صفحه احراز هویت ممکن نشد. لطفاً برنامه را دوباره باز کنید."
            return
        }
        if (BiometricHelper.isBiometricAvailable(context)) {
            isUnavailableOnDevice = false
            BiometricHelper.showBiometricPrompt(
                activity = activity,
                title = "امنیت تراز",
                subtitle = "برای ورود به برنامه اثر انگشت یا رمز دستگاه را وارد نمایید",
                onSuccess = {
                    authError = null
                    onUnlockSuccess()
                },
                onError = { errorMsg ->
                    authError = errorMsg
                },
                onFailedAttempt = {
                    authError = "احراز هویت انجام نشد. لطفاً مجدداً تلاش کنید."
                }
            )
        } else {
            isUnavailableOnDevice = true
            authError = "روی این دستگاه قفل صفحه تعریف نشده است. برای فعال شدن قفل تراز، ابتدا از تنظیمات اندروید رمز یا اثر انگشت تعریف کنید."
        }
    }

    // Automatically trigger biometric authentication when the screen loads
    LaunchedEffect(Unit) {
        triggerBiometricAuthentication()
    }

    // The Taraz lock: one clear action, and a plain explanation when it fails.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(com.example.ui.taraz.Tz.Ground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(64.dp))
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                .background(com.example.ui.taraz.Tz.Cobalt),
            contentAlignment = Alignment.Center
        ) {
            Text("تراز", style = com.example.ui.taraz.tzText(17.sp, FontWeight.Bold, Color.White))
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text("تراز قفل است", style = com.example.ui.taraz.tzText(24.sp, FontWeight.Bold))
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "برای دیدن حساب‌ها، هویتتان را تأیید کنید.",
            style = com.example.ui.taraz.tzText(14.sp, color = com.example.ui.taraz.Tz.Muted, align = androidx.compose.ui.text.style.TextAlign.Center)
        )

        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size((120 * pulseScale.coerceIn(1f, 1.12f)).dp)
                .clip(CircleShape)
                .background(com.example.ui.taraz.Tz.CobaltTint2)
                .clickable { triggerBiometricAuthentication() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Fingerprint, contentDescription = "باز کردن با اثر انگشت", tint = com.example.ui.taraz.Tz.Cobalt, modifier = Modifier.size(60.dp))
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            authError ?: "انگشتتان را روی حسگر بگذارید",
            style = com.example.ui.taraz.tzText(
                14.sp, FontWeight.SemiBold,
                if (authError != null) com.example.ui.taraz.Tz.Amber else com.example.ui.taraz.Tz.CobaltText,
                align = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 24.sp
            )
        )
        Spacer(modifier = Modifier.weight(1f))

        if (isUnavailableOnDevice) {
            com.example.ui.taraz.PrimaryButton("ادامه بدون قفل", onClick = { onLockUnavailable() })
        } else {
            com.example.ui.taraz.SecondaryButton("استفاده از رمز گوشی", onClick = { triggerBiometricAuthentication() }, height = 50.dp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "این قفل را از «تنظیمات › امنیت» خاموش یا روشن کنید.",
            style = com.example.ui.taraz.tzText(12.sp, color = com.example.ui.taraz.Tz.Muted, align = androidx.compose.ui.text.style.TextAlign.Center)
        )
    }
}
