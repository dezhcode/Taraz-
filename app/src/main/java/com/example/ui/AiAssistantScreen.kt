package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
    messages: List<ChatMessage>,
    isLoading: Boolean,
    toastMessage: String? = null,
    onDismissToast: () -> Unit = {},
    onSendMessage: (String) -> Unit,
    onOpenChat: () -> Unit = {},
    onApproveAction: (String) -> Unit = {},
    onCancelAction: (String) -> Unit = {},
    // ─── قابلیت‌های صوتی (منتقل‌شده از AI Studio) ───
    isDictating: Boolean = false,
    dictationAmplitude: Float = 0f,
    onToggleDictation: ((onText: (String) -> Unit) -> Unit)? = null,
    speakingMessageId: String? = null,
    onSpeakMessage: ((ChatMessage) -> Unit)? = null,
    onStartLiveVoice: (() -> Unit)? = null,
    onMicPermissionDenied: () -> Unit = {},
    onNewChat: (() -> Unit)? = null
) {
    LaunchedEffect(Unit) {
        onOpenChat()
    }
    
    // Auto-dismiss Toast after 6 seconds
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            kotlinx.coroutines.delay(6000)
            onDismissToast()
        }
    }

    var textInput by remember { mutableStateOf("") }
    var isFocused by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // عملی که پس از گرفتن مجوز میکروفون اجرا می‌شود (دیکته یا گفت‌وگوی زنده)
    var pendingMicAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pendingMicAction?.invoke() else onMicPermissionDenied()
        pendingMicAction = null
    }
    fun withMicPermission(action: () -> Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) action() else {
            pendingMicAction = action
            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }
    val startDictation: () -> Unit = {
        withMicPermission {
            onToggleDictation?.invoke { recognized -> textInput = recognized }
        }
    }

    val presets = listOf(
        "هزینه امروز من چقدر است؟",
        "اقساط سررسید شده من کدامند؟",
        "یک پیشنهاد برای پس‌انداز بیشتر بده.",
        "چگونه بودجه‌بندی کنم؟"
    )

    // Automatically scroll to the bottom of the conversation when new messages arrive!
    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .statusBarsPadding()
    ) {
        // هدر طرح «لایه»
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(EmeraldPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("دستیار", style = com.example.ui.layer.lt(22.sp, FontWeight.Bold))
                Text(
                    "هر تغییری قبل از ثبت، از شما تأیید می‌گیرد.",
                    style = com.example.ui.layer.lt(12.sp, color = com.example.ui.layer.L.Muted)
                )
            }
            if (onNewChat != null) {
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(com.example.ui.layer.L.PrimarySoft)
                        .clickable(enabled = !isLoading) { onNewChat() }
                        .padding(horizontal = 12.dp)
                        .testTag("ai_new_chat"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("گفتگوی تازه", style = com.example.ui.layer.lt(13.sp, FontWeight.Bold, com.example.ui.layer.L.PrimaryText))
                }
            }
        }

        // 1. Conversation Chat Window
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    if (msg.status == "pending") {
                        AiTypingIndicator()
                    } else {
                        ChatBubble(
                            message = msg,
                            onApprove = onApproveAction,
                            onCancel = onCancelAction,
                            isSpeaking = speakingMessageId == msg.id,
                            onSpeak = onSpeakMessage
                        )
                    }
                }

                if (isLoading && messages.none { it.status == "pending" }) {
                    item {
                        AiTypingIndicator()
                    }
                }
            }

            // Beautiful gradient overlay fading messages to the background color at the top edge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                BackgroundLight,
                                Color.Transparent
                             )
                        )
                    )
                    .align(Alignment.TopCenter)
            )

            // Beautiful gradient overlay fading messages to the background color at the bottom edge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                BackgroundLight
                            )
                        )
                    )
                    .align(Alignment.BottomCenter)
            )

            // Toast Alert Overlay Component for FastAPI/Copilot failures/timeouts
            androidx.compose.animation.AnimatedVisibility(
                visible = toastMessage != null,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp, start = 8.dp, end = 8.dp)
                    .testTag("ai_error_toast")
            ) {
                if (toastMessage != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)), // Light red card background
                        border = BorderStroke(1.dp, Color(0xFFFCA5A5)), // Pastel red border
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFEE2E2)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Warning",
                                        tint = Color(0xFFEF4444)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "خطای ارتباط با دستیار هوشمند تراز",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            color = Color(0xFF991B1B),
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = toastMessage,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Color(0xFF7F1D1D)
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = onDismissToast,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Preset suggestions Horizontal List
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            BackgroundLight.copy(alpha = 0.5f),
                            BackgroundLight
                        )
                    )
                )
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(presets) { presetText ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(com.example.ui.layer.L.PrimarySoft)
                            .clickable(enabled = !isLoading) {
                                onSendMessage(presetText)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("preset_prompt_${presetText.hashCode()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = presetText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = com.example.ui.layer.L.PrimaryText
                            )
                        )
                    }
                }
            }

            // 3. نوار نوشتن — طرح «لایه»: فیلد سفید، میکروفون، ارسال آبی
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = if (isFocused) 10.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .border(1.dp, com.example.ui.layer.L.Line, RoundedCornerShape(16.dp))
                            .padding(start = 14.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            enabled = !isLoading,
                            placeholder = {
                                Text(
                                    when {
                                        isLoading -> "دستیار در حال پاسخ دادن است…"
                                        isDictating -> "در حال شنیدن… صحبت کنید"
                                        else -> "سؤال بعدی را بپرسید"
                                    },
                                    style = com.example.ui.layer.lt(14.sp, color = com.example.ui.layer.L.Faint)
                                )
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedTextColor = NavySecondary,
                                unfocusedTextColor = NavySecondary,
                                disabledTextColor = com.example.ui.layer.L.Faint,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                cursorColor = EmeraldPrimary
                            ),
                            textStyle = com.example.ui.layer.lt(14.sp),
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { isFocused = it.isFocused }
                                .testTag("ai_chat_text_input")
                        )

                        // دیکته صوتی (صدا → متن)
                        if (onToggleDictation != null) {
                            val micPulse = if (isDictating) 1f + dictationAmplitude.coerceIn(0f, 1f) * 0.25f else 1f
                            IconButton(
                                onClick = startDictation,
                                enabled = !isLoading,
                                modifier = Modifier
                                    .size((40 * micPulse).dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isDictating) ErrorRed else IceSlate)
                                    .testTag("ai_chat_mic_button")
                            ) {
                                Icon(
                                    imageVector = if (isDictating) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = if (isDictating) "توقف دیکته" else "تبدیل صدا به متن",
                                    tint = if (isDictating) Color.White else NavySecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // گفت‌وگوی زنده صوتی
                        if (onStartLiveVoice != null) {
                            IconButton(
                                onClick = { withMicPermission { onStartLiveVoice() } },
                                enabled = !isLoading && !isDictating,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(com.example.ui.layer.L.PrimarySoft)
                                    .testTag("ai_chat_live_voice_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "گفت‌وگوی زنده صوتی",
                                    tint = com.example.ui.layer.L.PrimaryText,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    val canSend = textInput.isNotBlank() && !isLoading
                    Box(
                        modifier = Modifier
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (canSend) EmeraldPrimary else com.example.ui.layer.L.PrimaryDisabled)
                            .clickable(enabled = canSend) {
                                onSendMessage(textInput)
                                textInput = ""
                            }
                            .padding(horizontal = 18.dp)
                            .testTag("ai_chat_send_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("ارسال", style = com.example.ui.layer.lt(15.sp, FontWeight.Bold, Color.White))
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    onApprove: (String) -> Unit,
    onCancel: (String) -> Unit,
    isSpeaking: Boolean = false,
    onSpeak: ((ChatMessage) -> Unit)? = null
) {
    val isUser = message.sender == "user"

    if (isUser) {
        // پرسش شما: کارت سفید با برچسب کوچک
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("chat_bubble_${message.id}"),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("شما پرسیدید", style = com.example.ui.layer.lt(12.sp, color = com.example.ui.layer.L.Muted))
            RichMarkdownContent(text = message.text, isUser = false)
        }
    } else {
        // پاسخ دستیار: متن آزاد روی زمینه، بدون کارت
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .testTag("chat_bubble_${message.id}"),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            RichMarkdownContent(text = message.text, isUser = false)

            message.actionProposed?.let { action ->
                ProposedActionCard(
                    messageId = message.id,
                    action = action,
                    onApprove = onApprove,
                    onCancel = onCancel
                )
            }

            // پخش صوتی پاسخ دستیار (متن → صدا)
            if (onSpeak != null && message.status == "success" && message.text.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    IconButton(
                        onClick = { onSpeak(message) },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSpeaking) ErrorLight else IceSlate)
                            .testTag("chat_speak_${message.id}")
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isSpeaking) "توقف پخش" else "پخش صوتی پاسخ",
                            tint = if (isSpeaking) ErrorRed else SlateGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProposedActionCard(
    messageId: String,
    action: ProposedAction,
    onApprove: (String) -> Unit,
    onCancel: (String) -> Unit
) {
    val actionColor = when {
        action.isApproved -> SuccessGreen
        action.isCancelled -> Color.Gray
        action.type.contains("delete") -> ErrorRed
        else -> EmeraldPrimary
    }
    
    val actionLabel = when (action.type) {
        "add_transaction" -> "ثبت تراکنش جدید"
        "delete_transaction" -> "حذف تراکنش"
        "edit_transaction" -> "ویرایش تراکنش"
        "add_loan" -> "ثبت وام جدید"
        "delete_loan" -> "حذف وام"
        "edit_loan" -> "ویرایش اطلاعات وام"
        "pay_installment" -> "پرداخت قسط وام"
        else -> "عملیات سیستم"
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, com.example.ui.layer.L.Line),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .testTag("proposed_action_card_${messageId}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(actionColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    val icon = when {
                        action.type.contains("add") -> Icons.Default.Add
                        action.type.contains("delete") -> Icons.Default.Delete
                        action.type.contains("pay") -> Icons.Default.Payment
                        else -> Icons.Default.Edit
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = actionLabel,
                        tint = actionColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = NavySecondary
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                text = action.description,
                style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF475569)),
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(14.dp))
            
            when {
                action.isPending -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onApprove(messageId) },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("btn_approve_${messageId}"),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Approve",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تایید و ثبت", style = MaterialTheme.typography.labelLarge.copy(color = Color.White))
                        }
                        
                        OutlinedButton(
                            onClick = { onCancel(messageId) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, com.example.ui.layer.L.Line),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("btn_cancel_${messageId}"),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("لغو عملیات", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                action.isApproved -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(com.example.ui.layer.L.GreenSoft, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Approved",
                            tint = SuccessGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "این تغییرات در سیستم ثبت شد.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = com.example.ui.layer.L.GreenText,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
                action.isCancelled -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = "Cancelled",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "این پیشنهاد توسط شما لغو شد.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFF334155),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RichMarkdownContent(text: String, isUser: Boolean) {
    val textColor = if (isUser) Color.White else NavySecondary
    val lines = text.split("\n")
    var i = 0
    
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        while (i < lines.size) {
            val line = lines[i].trim()
            
            if (line.isEmpty()) {
                i++
                continue
            }
            
            // 1. Check for Table
            if (line.startsWith("|") && i + 1 < lines.size && lines[i+1].trim().startsWith("|")) {
                val tableLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    tableLines.add(lines[i].trim())
                    i++
                }
                RenderTable(tableLines, isUser)
                continue
            }
            
            // 2. Check for Active / Inactive Alerts
            if (line.contains("[اعلان فعال]") || line.startsWith("> [!IMPORTANT]") || line.startsWith("> [!WARNING]") || line.startsWith("> [اعلان فعال]")) {
                val alertText = if (line.startsWith(">")) {
                    line.substringAfter(">").replace("[!IMPORTANT]", "").replace("[!WARNING]", "").replace("[اعلان فعال]", "").trim()
                } else {
                    line.replace("[اعلان فعال]", "").trim()
                }
                RenderAlertCard(text = alertText, isActive = true)
                i++
                continue
            }
            
            if (line.contains("[اعلان غیرفعال]") || line.startsWith("> [اعلان غیرفعال]")) {
                val alertText = if (line.startsWith(">")) {
                    line.substringAfter(">").replace("[اعلان غیرفعال]", "").trim()
                } else {
                    line.replace("[اعلان غیرفعال]", "").trim()
                }
                RenderAlertCard(text = alertText, isActive = false)
                i++
                continue
            }
            
            // 3. Check for Blockquote or Highlight Card
            if (line.startsWith(">")) {
                val blockquoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    blockquoteLines.add(lines[i].trim().substringAfter(">").trim())
                    i++
                }
                RenderHighlightCard(blockquoteLines.joinToString("\n"), isUser)
                continue
            }
            
            // 4. Check for Headers
            if (line.startsWith("#")) {
                val level = line.takeWhile { it == '#' }.length
                val headerText = line.drop(level).trim()
                RenderHeader(headerText, level, textColor)
                i++
                continue
            }
            
            // 5. Check for Lists
            if (line.startsWith("*") || line.startsWith("-") || line.startsWith("•")) {
                val listItems = mutableListOf<String>()
                while (i < lines.size && (lines[i].trim().startsWith("*") || lines[i].trim().startsWith("-") || lines[i].trim().startsWith("•"))) {
                    val itemText = lines[i].trim().drop(1).trim()
                    listItems.add(itemText)
                    i++
                }
                RenderList(listItems, textColor, isUser)
                continue
            }
            
            // 6. Plain Text / Paragraph with Bold/Italic formatting
            RenderParagraph(line, textColor, isUser)
            i++
        }
    }
}

@Composable
fun RenderTable(lines: List<String>, isUser: Boolean) {
    val rows = lines.map { rowLine ->
        rowLine.split("|")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }.filter { row ->
        row.isNotEmpty() && !row.all { cell -> cell.all { it == '-' || it == ':' } }
    }
    
    if (rows.isEmpty()) return
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, IceSlate)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { rowIndex, cells ->
                val isHeader = rowIndex == 0
                val rowBg = if (isHeader) {
                    EmeraldPrimary.copy(alpha = 0.08f)
                } else if (rowIndex % 2 == 0) {
                    IceSlate.copy(alpha = 0.3f)
                } else {
                    Color.White
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBg)
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    cells.forEachIndexed { cellIndex, cellText ->
                        Text(
                            text = parseInlineFormatting(cellText, isUser),
                            style = if (isHeader) {
                                MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.ExtraBold, 
                                    color = EmeraldPrimary,
                                    fontSize = 13.sp
                                )
                            } else {
                                MaterialTheme.typography.bodyMedium.copy(
                                    color = NavySecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            },
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                        if (cellIndex < cells.size - 1) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(16.dp)
                                    .background(IceSlate)
                            )
                        }
                    }
                }
                if (rowIndex < rows.size - 1) {
                    HorizontalDivider(color = IceSlate, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
fun RenderAlertCard(text: String, isActive: Boolean) {
    val bgColor = if (isActive) SuccessGreen.copy(alpha = 0.1f) else SlateGray.copy(alpha = 0.1f)
    val borderColor = if (isActive) SuccessGreen else SlateGray
    val iconColor = if (isActive) SuccessGreen else SlateGray
    val labelText = if (isActive) "اعلان فعال" else "اعلان غیرفعال"
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(borderColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = labelText,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = borderColor
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = parseInlineFormatting(text, isUser = false),
                style = MaterialTheme.typography.bodyMedium.copy(color = NavySecondary)
            )
        }
    }
}

@Composable
fun RenderHighlightCard(text: String, isUser: Boolean = false) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, IceSlate)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(EmeraldPrimary)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = parseInlineFormatting(text, isUser),
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = NavySecondary,
                    lineHeight = 22.sp
                )
            )
        }
    }
}

@Composable
fun RenderHeader(text: String, level: Int, color: Color) {
    val style = when (level) {
        1 -> MaterialTheme.typography.headlineLarge
        2 -> MaterialTheme.typography.headlineMedium
        else -> MaterialTheme.typography.titleLarge
    }
    Text(
        text = text,
        style = style.copy(fontWeight = FontWeight.Bold, color = color),
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
fun RenderList(items: List<String>, textColor: Color, isUser: Boolean) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
    ) {
        items.forEach { item ->
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = EmeraldPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = parseInlineFormatting(item, isUser),
                    style = MaterialTheme.typography.bodyLarge.copy(color = textColor)
                )
            }
        }
    }
}

@Composable
fun RenderParagraph(text: String, textColor: Color, isUser: Boolean) {
    Text(
        text = parseInlineFormatting(text, isUser),
        style = MaterialTheme.typography.bodyLarge.copy(
            color = textColor,
            lineHeight = 24.sp
        )
    )
}

fun parseInlineFormatting(text: String, isUser: Boolean = false): AnnotatedString {
    return buildAnnotatedString {
        var remaining = text
        while (remaining.isNotEmpty()) {
            val boldIndex = remaining.indexOf("**")
            val codeIndex = remaining.indexOf("`")
            
            if (boldIndex != -1 && (codeIndex == -1 || boldIndex < codeIndex)) {
                append(remaining.substring(0, boldIndex))
                val afterFirstMarker = remaining.substring(boldIndex + 2)
                val secondMarkerIndex = afterFirstMarker.indexOf("**")
                if (secondMarkerIndex != -1) {
                    val boldContent = afterFirstMarker.substring(0, secondMarkerIndex)
                    withStyle(style = SpanStyle(
                        fontWeight = FontWeight.ExtraBold, 
                        color = if (isUser) Color.White else NavySecondary
                    )) {
                        append(boldContent)
                    }
                    remaining = afterFirstMarker.substring(secondMarkerIndex + 2)
                } else {
                    append("**")
                    remaining = afterFirstMarker
                }
            } else if (codeIndex != -1) {
                append(remaining.substring(0, codeIndex))
                val afterFirstMarker = remaining.substring(codeIndex + 1)
                val secondMarkerIndex = afterFirstMarker.indexOf("`")
                if (secondMarkerIndex != -1) {
                    val codeContent = afterFirstMarker.substring(0, secondMarkerIndex)
                    withStyle(style = SpanStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = EmeraldPrimary,
                        background = IceSlate
                    )) {
                        append(codeContent)
                    }
                    remaining = afterFirstMarker.substring(secondMarkerIndex + 1)
                } else {
                    append("`")
                    remaining = afterFirstMarker
                }
            } else {
                append(remaining)
                break
            }
        }
    }
}

@Composable
fun AiTypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "pendingTyping")
    
    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("ai_typing_indicator")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(EmeraldPrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = EmeraldPrimary,
                    strokeWidth = 2.dp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "دستیار مالی تراز در حال تحلیل و پاسخ‌دهی...",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = NavySecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "لطفاً چند لحظه شکیبا باشید",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = SlateGray,
                        fontSize = 11.sp
                    )
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(EmeraldPrimary.copy(alpha = dot1Alpha))
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(EmeraldPrimary.copy(alpha = dot2Alpha))
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(EmeraldPrimary.copy(alpha = dot3Alpha))
                )
            }
        }
    }
}
