package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.ui.layer.BigButton
import com.example.ui.layer.L
import com.example.ui.layer.SoftBigButton
import com.example.ui.layer.lt
import com.example.utils.BiometricHelper

/**
 * صفحهٔ قفل — طرح نهایی: نشان تراز، حلقهٔ اثر انگشت و دکمهٔ «استفاده از رمز گوشی».
 * منطق بیومتریک همان قبلی است؛ فقط ظاهر عوض شده.
 */
@Composable
fun BiometricLockScreen(
    onUnlockSuccess: () -> Unit,
    onLockUnavailable: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var authError by remember { mutableStateOf<String?>(null) }
    var isUnavailableOnDevice by remember { mutableStateOf(false) }

    val infinite = rememberInfiniteTransition(label = "lock_ring")
    val ring by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ring_scale"
    )
    val ringAlpha by infinite.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ring_alpha"
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
                onError = { errorMsg -> authError = errorMsg },
                onFailedAttempt = { authError = "احراز هویت انجام نشد. لطفاً مجدداً تلاش کنید." }
            )
        } else {
            isUnavailableOnDevice = true
            authError = "روی این دستگاه قفل صفحه تعریف نشده است. برای فعال شدن قفل تراز، ابتدا از تنظیمات اندروید رمز یا اثر انگشت تعریف کنید."
        }
    }

    LaunchedEffect(Unit) { triggerBiometricAuthentication() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(L.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            Modifier.padding(top = 88.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = com.example.R.drawable.logo_taraz_app),
                contentDescription = null,
                modifier = Modifier.size(56.dp)
            )
            Text("تراز قفل است", style = lt(24.sp, FontWeight.Bold), modifier = Modifier.padding(top = 10.dp))
            Text(
                "برای دیدن حساب‌ها و اقساطتان، هویت‌تان را تأیید کنید.",
                style = lt(14.sp, color = L.Muted, lineHeight = 26.sp),
                textAlign = TextAlign.Center
            )
        }

        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(120.dp).scale(ring).alpha(ringAlpha).clip(CircleShape).background(L.NavSelected))
                Box(
                    Modifier.size(120.dp).clip(CircleShape).background(L.NavSelected)
                        .clickable { triggerBiometricAuthentication() }
                        .testTag("biometric_fingerprint_scanner_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = "باز کردن با اثر انگشت", tint = L.Primary, modifier = Modifier.size(56.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("انگشتتان را روی حسگر بگذارید", style = lt(14.sp, FontWeight.SemiBold, L.PrimaryText))

            if (authError != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    authError!!,
                    style = lt(12.sp, FontWeight.SemiBold, L.RedText, lineHeight = 21.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(L.RedSoft)
                        .padding(12.dp)
                        .testTag(if (isUnavailableOnDevice) "biometric_unavailable_message" else "biometric_error_card")
                )
            }
        }

        if (isUnavailableOnDevice) {
            BigButton("متوجه شدم، ادامه", Modifier.testTag("biometric_unavailable_ack_button")) { onLockUnavailable() }
        } else {
            SoftBigButton("استفاده از رمز گوشی", Modifier.testTag("biometric_retry_button")) { triggerBiometricAuthentication() }
        }
        Text(
            "این قفل را از «تنظیمات › امنیت» خاموش یا روشن کنید.",
            style = lt(12.sp, color = L.Muted, lineHeight = 22.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp)
        )
    }
}
