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
    onUnlockFallback: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var authError by remember { mutableStateOf<String?>(null) }

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
        if (activity != null && BiometricHelper.isBiometricAvailable(context)) {
            BiometricHelper.showBiometricPrompt(
                activity = activity,
                title = "امنیت تراز",
                subtitle = "برای ورود به برنامه اثر انگشت خود را لمس کنید",
                onSuccess = {
                    authError = null
                    onUnlockSuccess()
                },
                onError = { errorMsg ->
                    authError = errorMsg
                }
            )
        } else {
            authError = "سنسور اثر انگشت در این دستگاه یافت نشد یا تعریف نشده است."
        }
    }

    // Automatically trigger biometric authentication when the screen loads
    LaunchedEffect(Unit) {
        triggerBiometricAuthentication()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF020617))))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App icon and security label
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFFFCD34D),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "حفاظت امنیتی تراز",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.8f)
                )
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Large animated Fingerprint Scanning Button
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .clickable { triggerBiometricAuthentication() }
                .testTag("biometric_fingerprint_scanner_button"),
            contentAlignment = Alignment.Center
        ) {
            // Ripple Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(listOf(Color(0xFF2D7D5F).copy(alpha = 0.12f * pulseScale), Color.Transparent)),
                    radius = (size.minDimension / 2) * pulseScale
                )
                drawCircle(
                    color = Color(0xFF2D7D5F).copy(alpha = 0.2f),
                    radius = size.minDimension / 2 - 10.dp.toPx(),
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF2D7D5F), Color(0xFF225E47)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = "اثر انگشت",
                    tint = Color.White,
                    modifier = Modifier.size(54.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "تایید اثر انگشت برای ورود",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "برنامه تراز توسط لایه امنیتی اندروید محافظت می‌شود. لطفاً سنسور اثر انگشت دستگاه را لمس نمایید تا قفل باز شود.",
            style = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFF94A3B8), lineHeight = 24.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (authError != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .testTag("biometric_error_card"),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D).copy(alpha = 0.2f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFFFCA5A5),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = authError!!,
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFFCA5A5), fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Bypass / Fallback button for testing on Emulators or devices without physical biometric modules
        OutlinedButton(
            onClick = {
                // If fingerprint fails or is missing, user can bypass using local simulation password
                onUnlockFallback()
            },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
            modifier = Modifier
                .width(220.dp)
                .height(48.dp)
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                .testTag("biometric_fallback_bypass_button")
        ) {
            Text(
                text = "استفاده از رمز عبور / لغو قفل",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
            )
        }
    }
}
