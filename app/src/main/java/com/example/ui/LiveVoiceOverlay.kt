package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.alpha
import com.example.ai.LiveVoiceMode
import com.example.ai.LiveVoicePhase
import com.example.ai.LiveVoiceState
import com.example.ai.TARAZ_VOICE_PRESETS
import com.example.ui.theme.HorizonGreen
import com.example.ui.theme.HorizonGreenGlow
import kotlin.math.cos
import kotlin.math.sin

private val LiveBgTop = Color(0xFF06140F)
private val LiveBgMid = Color(0xFF0B2219)
private val LiveCard = Color(0xFF12291F)
private val LiveSurface = Color(0xFF0E1F18)
private val LiveBorder = Color(0xFF24443A)
private val LiveAmber = Color(0xFFF5AF02)
private val LiveSky = Color(0xFF38BDF8)
private val LiveRose = Color(0xFFE53238)
private val LiveMuted = Color(0xFF94A3B8)

/**
 * صفحه گفت‌وگوی زنده صوتی با دستیار مالی تراز
 * (منتقل‌شده از LiveVoiceOverlayScreen پروژه AI Studio)
 */
@Composable
fun LiveVoiceOverlayScreen(
    liveState: LiveVoiceState,
    realtimeWebView: android.webkit.WebView? = null,
    onSelectVoice: (String) -> Unit,
    onToggleMute: () -> Unit,
    onSendSpokenPrompt: (String) -> Unit,
    onEndCall: () -> Unit
) {
    BackHandler { onEndCall() }

    var customVoicePrompt by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(liveState.turns.size, liveState.currentAssistantText.isNotEmpty()) {
        val count = liveState.turns.size + if (liveState.currentAssistantText.isNotEmpty()) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }
    val isRealtime = liveState.mode == LiveVoiceMode.REALTIME

    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse_scale"
    )
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Restart),
        label = "wave_phase"
    )

    val orbColor by animateColorAsState(
        targetValue = when (liveState.phase) {
            LiveVoicePhase.CONNECTING, LiveVoicePhase.PROCESSING -> LiveAmber
            LiveVoicePhase.LISTENING_USER -> if (liveState.isMuted) Color(0xFF64748B) else HorizonGreenGlow
            LiveVoicePhase.USER_SPEAKING -> HorizonGreenGlow
            LiveVoicePhase.SPEAKING_ASSISTANT -> LiveSky
            LiveVoicePhase.IDLE -> HorizonGreenGlow
        },
        label = "orb_color"
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(LiveBgTop, LiveBgMid, LiveBgTop)))
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("live_voice_overlay")
        ) {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                // ۱. هدر
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(orbColor)
                    )
                    Column {
                        Text(
                            text = "گفت‌وگوی صوتی با تراز",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "سوال مالی‌ات رو بپرس؛ پیشنهادها برای تایید توی چت ثبت می‌شن",
                            style = MaterialTheme.typography.labelSmall,
                            color = LiveMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ۲. انتخاب صدای دستیار
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TARAZ_VOICE_PRESETS.forEach { preset ->
                        val selected = liveState.selectedVoiceId == preset.id
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectVoice(preset.id) },
                            label = {
                                Text(
                                    text = preset.nameFa,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = Color(0xFFE2E8F0),
                                iconColor = Color(0xFFE2E8F0),
                                selectedContainerColor = HorizonGreenGlow.copy(alpha = 0.22f),
                                selectedLabelColor = HorizonGreenGlow,
                                selectedLeadingIconColor = HorizonGreenGlow
                            ),
                            modifier = Modifier.testTag("voice_preset_${preset.id}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ۳. گوی موج صوتی
                Box(modifier = Modifier.size(170.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val baseRadius = size.minDimension * 0.27f
                        val dynamicScale = pulseScale + (liveState.audioLevel * 0.25f)

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(orbColor.copy(alpha = 0.35f), orbColor.copy(alpha = 0.08f), Color.Transparent),
                                center = center,
                                radius = baseRadius * 1.85f * dynamicScale
                            ),
                            radius = baseRadius * 1.85f * dynamicScale,
                            center = center
                        )
                        val points = 60
                        for (i in 0 until points) {
                            val angle = (i.toFloat() / points) * 6.28318f
                            val waveOffset = sin(angle * 6f + wavePhase) * (10f + liveState.audioLevel * 22f)
                            val r = baseRadius * 1.25f + waveOffset
                            drawCircle(
                                color = orbColor.copy(alpha = 0.65f),
                                radius = 3.2f,
                                center = Offset(center.x + cos(angle) * r, center.y + sin(angle) * r)
                            )
                        }
                        drawCircle(color = orbColor.copy(alpha = 0.22f), radius = baseRadius * dynamicScale, center = center)
                        drawCircle(
                            color = orbColor,
                            radius = baseRadius * 0.82f,
                            center = center,
                            style = Stroke(width = 4.dp.toPx())
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = when {
                                liveState.phase == LiveVoicePhase.SPEAKING_ASSISTANT -> Icons.Default.GraphicEq
                                liveState.isMuted -> Icons.Default.MicOff
                                else -> Icons.Default.Mic
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (liveState.phase) {
                                LiveVoicePhase.CONNECTING -> "در حال اتصال..."
                                LiveVoicePhase.LISTENING_USER -> if (liveState.isMuted) "بی‌صدا" else "گوش می‌کنم..."
                                LiveVoicePhase.USER_SPEAKING -> "در حال شنیدن..."
                                LiveVoicePhase.PROCESSING -> "در حال بررسی..."
                                LiveVoicePhase.SPEAKING_ASSISTANT -> "در حال پاسخ..."
                                LiveVoicePhase.IDLE -> "آماده"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                Surface(
                    color = LiveCard.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = liveState.currentPartialUserText.ifBlank { liveState.statusBanner },
                        color = Color(0xFFD1FAE5),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ۴. زیرنویس و تاریخچه گفت‌وگوی صوتی
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = CardDefaults.cardColors(containerColor = LiveSurface.copy(alpha = 0.9f)),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, LiveBorder)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (liveState.turns.isEmpty()) {
                            item {
                                Text(
                                    text = if (isRealtime) "راحت صحبت کن؛ هر وقت ساکت بشی دستیار جواب میده."
                                    else "با میکروفون صحبت کن یا یکی از سوال‌های پایین رو انتخاب کن.",
                                    color = LiveMuted,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 18.dp)
                                )
                            }
                        }
                        items(liveState.turns) { turn ->
                            val isUser = turn.role == "user"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isUser) Arrangement.Start else Arrangement.End
                            ) {
                                Surface(
                                    color = if (isUser) HorizonGreen.copy(alpha = 0.35f) else LiveCard,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isUser) HorizonGreenGlow.copy(alpha = 0.5f) else LiveSky.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = if (isUser) "🎙️ شما" else "🔊 تراز",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isUser) HorizonGreenGlow else LiveSky
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = com.example.ai.DuckVoiceController.cleanForSpeech(turn.text),
                                            fontSize = 13.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                        if (liveState.currentAssistantText.isNotEmpty()) {
                            item {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    Surface(
                                        color = LiveCard,
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, LiveSky)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = "🔊 تراز در حال صحبت…",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = LiveSky
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = liveState.currentAssistantText,
                                                fontSize = 13.sp,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ۵. در حالت جایگزین (بدون WebRTC): سوال‌های سریع و ورودی متنی
                if (!isRealtime) {
                // ۵. سوال‌های سریع مالی
                val quickVoicePrompts = listOf(
                    "امروز چقدر خرج کردم؟",
                    "قسط بعدی‌ام کیه؟",
                    "موجودی کارت‌هام چقدره؟"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickVoicePrompts.forEachIndexed { idx, qText ->
                        Surface(
                            color = LiveCard,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, LiveBorder),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(enabled = liveState.phase != LiveVoicePhase.PROCESSING) {
                                    onSendSpokenPrompt(qText)
                                }
                                .testTag("live_quick_prompt_$idx")
                        ) {
                            Text(
                                text = "🗣️ $qText",
                                color = Color(0xFFE2E8F0),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ورودی متنی داخل گفت‌وگوی زنده
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customVoicePrompt,
                        onValueChange = { customVoicePrompt = it },
                        placeholder = { Text("یا جمله‌ات رو بنویس تا با صدا جواب بدم…", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = HorizonGreenGlow,
                            unfocusedBorderColor = LiveBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedPlaceholderColor = LiveMuted,
                            unfocusedPlaceholderColor = LiveMuted,
                            cursorColor = HorizonGreenGlow
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("live_voice_text_input")
                    )
                    IconButton(
                        onClick = {
                            if (customVoicePrompt.isNotBlank()) {
                                onSendSpokenPrompt(customVoicePrompt.trim())
                                customVoicePrompt = ""
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(HorizonGreen)
                            .testTag("live_voice_send_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "ارسال", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ۶. کنترل‌ها
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onToggleMute,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (liveState.isMuted) LiveAmber else LiveCard,
                            contentColor = if (liveState.isMuted) Color.Black else Color.White
                        ),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        modifier = Modifier.testTag("live_mute_button")
                    ) {
                        Icon(
                            imageVector = if (liveState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (liveState.isMuted) "روشن کردن میکروفون" else "بی‌صدا",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Button(
                        onClick = onEndCall,
                        colors = ButtonDefaults.buttonColors(containerColor = LiveRose, contentColor = Color.White),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                        modifier = Modifier.testTag("live_end_call_button")
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "پایان گفت‌وگو", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // میزبان WebView جلسه WebRTC (۱dp و نامرئی) تا پخش صدا و تایمرهای مرورگر فعال بمانند
            if (realtimeWebView != null) {
                AndroidView(
                    factory = { _ ->
                        (realtimeWebView.parent as? android.view.ViewGroup)?.removeView(realtimeWebView)
                        realtimeWebView
                    },
                    modifier = Modifier
                        .size(1.dp)
                        .alpha(0f)
                        .align(Alignment.BottomStart)
                )
            }
        }
    }
}
