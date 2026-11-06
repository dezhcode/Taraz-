package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ai.LiveVoiceMode
import com.example.ai.LiveVoicePhase
import com.example.ai.LiveVoiceState
import com.example.ai.TARAZ_VOICE_PRESETS
import com.example.ui.layer.L
import com.example.ui.layer.lt

/**
 * گفت‌وگوی زنده — طرح نهایی «لایه»: گوی آبی در سه حلقه، زیرنویس در کارت سفید،
 * انتخاب صدا به‌صورت قرص، و دو دکمهٔ گرد (بی‌صدا / پایان).
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

    val infinite = rememberInfiniteTransition(label = "orb_pulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val pulseAlpha by infinite.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse_alpha"
    )

    val busy = liveState.phase == LiveVoicePhase.CONNECTING || liveState.phase == LiveVoicePhase.PROCESSING
    val orbColor by animateColorAsState(
        targetValue = when {
            busy -> L.Amber
            liveState.phase == LiveVoicePhase.LISTENING_USER && liveState.isMuted -> L.Muted
            else -> L.Primary
        },
        label = "orb_color"
    )
    val haloOuter = if (busy) L.AmberSoft else L.NavSelected
    val haloInner = if (busy) L.AmberHalo else L.Halo

    val phaseTitle = when (liveState.phase) {
        LiveVoicePhase.CONNECTING -> "در حال اتصال…"
        LiveVoicePhase.LISTENING_USER -> if (liveState.isMuted) "میکروفون بی‌صداست" else "گوش می‌کنم…"
        LiveVoicePhase.USER_SPEAKING -> "می‌شنوم…"
        LiveVoicePhase.PROCESSING -> "دارم بررسی می‌کنم…"
        LiveVoicePhase.SPEAKING_ASSISTANT -> "تراز جواب می‌دهد…"
        LiveVoicePhase.IDLE -> "آماده"
    }
    val connected = liveState.phase != LiveVoicePhase.CONNECTING && liveState.phase != LiveVoicePhase.IDLE

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(L.Bg)
                .imePadding()
                .testTag("live_voice_overlay")
        ) {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                // هدر
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("گفت‌وگوی زنده", style = lt(20.sp, FontWeight.Bold))
                        Text("با دستیار تراز حرف بزنید", style = lt(12.sp, color = L.Muted))
                    }
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (connected) L.GreenSoft else L.AmberSoft)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier.size(8.dp).clip(CircleShape)
                                .background(if (connected) L.Green else L.Amber)
                        )
                        Text(
                            if (connected) "متصل" else "در حال اتصال",
                            style = lt(12.sp, FontWeight.Bold, if (connected) L.GreenText else L.AmberText)
                        )
                    }
                }

                // گوی میانی
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.size(170.dp), contentAlignment = Alignment.Center) {
                        val level = liveState.audioLevel.coerceIn(0f, 1f)
                        Box(
                            Modifier.size(170.dp)
                                .scale(pulse + level * 0.08f)
                                .alpha(pulseAlpha)
                                .clip(CircleShape)
                                .background(haloOuter)
                        )
                        Box(Modifier.size(134.dp).clip(CircleShape).background(haloInner))
                        Box(
                            Modifier.size(98.dp).clip(CircleShape).background(orbColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    liveState.phase == LiveVoicePhase.SPEAKING_ASSISTANT -> Icons.Default.GraphicEq
                                    liveState.isMuted -> Icons.Default.MicOff
                                    else -> Icons.Default.Mic
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                    Text(phaseTitle, style = lt(17.sp, FontWeight.Bold))
                    Text(
                        text = liveState.currentPartialUserText.ifBlank { liveState.statusBanner },
                        style = lt(12.sp, color = L.Muted),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 28.dp)
                    )
                }

                // زیرنویس
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(L.Surface)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (liveState.turns.isEmpty() && liveState.currentAssistantText.isEmpty()) {
                            item {
                                Text(
                                    text = if (isRealtime) "راحت صحبت کنید؛ هر وقت ساکت شدید، دستیار جواب می‌دهد."
                                    else "با میکروفون صحبت کنید یا یکی از پرسش‌های پایین را بزنید.",
                                    style = lt(12.sp, color = L.Muted),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp)
                                )
                            }
                        }
                        items(liveState.turns) { turn ->
                            val isUser = turn.role == "user"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isUser) Arrangement.Start else Arrangement.End
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.84f)
                                        .clip(
                                            if (isUser) RoundedCornerShape(16.dp, 16.dp, 16.dp, 6.dp)
                                            else RoundedCornerShape(16.dp, 16.dp, 6.dp, 16.dp)
                                        )
                                        .background(if (isUser) L.Chip else L.PrimarySoft)
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = com.example.ai.DuckVoiceController.cleanForSpeech(turn.text),
                                        style = lt(13.sp, color = if (isUser) L.Ink else L.Info, lineHeight = 23.sp)
                                    )
                                }
                            }
                        }
                        if (liveState.currentAssistantText.isNotEmpty()) {
                            item {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.84f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .border(1.5.dp, L.Rail, RoundedCornerShape(16.dp))
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = liveState.currentAssistantText,
                                            style = lt(13.sp, color = L.Muted, lineHeight = 23.sp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // انتخاب صدا
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TARAZ_VOICE_PRESETS.forEach { preset ->
                        val selected = liveState.selectedVoiceId == preset.id
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(CircleShape)
                                .background(if (selected) L.Primary else L.Surface)
                                .clickable { onSelectVoice(preset.id) }
                                .padding(horizontal = 14.dp)
                                .testTag("voice_preset_${preset.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = preset.nameFa.substringBefore(" ("),
                                style = lt(12.sp, if (selected) FontWeight.Bold else FontWeight.Normal,
                                    if (selected) Color.White else L.Ink)
                            )
                        }
                    }
                }

                // حالت جایگزین: پرسش سریع و ورودی متنی
                if (!isRealtime) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("امروز چقدر خرج کردم؟", "قسط بعدی‌ام کیه؟", "موجودی کارت‌هام چقدره؟")
                            .forEachIndexed { idx, qText ->
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(L.PrimarySoft)
                                        .clickable(enabled = liveState.phase != LiveVoicePhase.PROCESSING) {
                                            onSendSpokenPrompt(qText)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                        .testTag("live_quick_prompt_$idx")
                                ) {
                                    Text(qText, style = lt(12.sp, FontWeight.Bold, L.PrimaryText))
                                }
                            }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(L.Surface)
                                .border(1.dp, L.Line, RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (customVoicePrompt.isEmpty()) {
                                Text("یا جمله‌تان را بنویسید…", style = lt(13.sp, color = L.Faint))
                            }
                            BasicTextField(
                                value = customVoicePrompt,
                                onValueChange = { customVoicePrompt = it },
                                singleLine = true,
                                textStyle = lt(14.sp),
                                cursorBrush = SolidColor(L.Primary),
                                modifier = Modifier.fillMaxWidth().testTag("live_voice_text_input")
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(L.Primary)
                                .clickable {
                                    if (customVoicePrompt.isNotBlank()) {
                                        onSendSpokenPrompt(customVoicePrompt.trim())
                                        customVoicePrompt = ""
                                    }
                                }
                                .testTag("live_voice_send_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "ارسال", tint = Color.White)
                        }
                    }
                }

                // کنترل‌ها
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 22.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(if (liveState.isMuted) L.Amber else L.Surface)
                                .clickable(onClick = onToggleMute)
                                .testTag("live_mute_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (liveState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = if (liveState.isMuted) "روشن کردن میکروفون" else "بی‌صدا کردن میکروفون",
                                tint = if (liveState.isMuted) Color.White else L.Ink,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(if (liveState.isMuted) "روشن" else "بی‌صدا", style = lt(12.sp, color = L.Muted))
                    }
                    Spacer(Modifier.width(28.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(L.Red)
                                .clickable(onClick = onEndCall)
                                .testTag("live_end_call_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = "پایان گفت‌وگو", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        Text("پایان", style = lt(12.sp, color = L.Muted))
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
