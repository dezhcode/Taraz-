package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import com.example.ui.layer.edgeGlass
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
    // ─── صدا به متن ───
    isDictating: Boolean = false,
    dictationAmplitude: Float = 0f,
    onToggleDictation: ((onText: (String) -> Unit) -> Unit)? = null,
    onMicPermissionDenied: () -> Unit = {},
    onNewChat: (() -> Unit)? = null
) {
    LaunchedEffect(Unit) { onOpenChat() }

    // پیام خطا بعد از ۶ ثانیه خودش می‌رود
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            kotlinx.coroutines.delay(6000)
            onDismissToast()
        }
    }

    val L = com.example.ui.layer.L
    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // گرفتن مجوز میکروفون، بعد شروع شنیدن
    var pendingMicAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pendingMicAction?.invoke() else onMicPermissionDenied()
        pendingMicAction = null
    }
    val startDictation: () -> Unit = {
        val action = { onToggleDictation?.invoke { recognized -> textInput = recognized }; Unit }
        val granted = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) action() else {
            pendingMicAction = action
            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    val presets = listOf(
        "این ماه کجا بیشتر خرج کردم؟",
        "هزینهٔ امروز من چقدر است؟",
        "چقدر بدهکارم و چقدر طلب دارم؟",
        "قسط‌ها و قبض‌های این هفته",
        "یک پیشنهاد برای پس‌انداز بیشتر بده"
    )
    val visible = messages.filter { it.text.isNotBlank() || it.status == "pending" || it.actionProposed != null }
    val empty = visible.isEmpty() && !isLoading

    // با آمدن پیام تازه به پایین گفتگو برو
    LaunchedEffect(visible.size, isLoading) {
        val count = visible.size + if (isLoading && visible.none { it.status == "pending" }) 1 else 0
        if (count > 0) coroutineScope.launch { listState.animateScrollToItem(count - 1) }
    }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    // بعد از فرستادن، کیبورد یک بار بسته می‌شود و بسته می‌ماند
    fun send(text: String) {
        if (text.isBlank() || isLoading) return
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        onSendMessage(text.trim())
        textInput = ""
    }

    // جای خالی زیر گفتگو تا آخرین پیام زیر قرص شناور نماند؛ با بلند شدن قرص بیشتر می‌شود
    var composerPx by remember { mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val composerSpace = maxOf(112.dp, with(density) { composerPx.toDp() } + 40.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(L.Bg)
    ) {
        // ── سرصفحه
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("دستیار", style = com.example.ui.layer.lt(22.sp, FontWeight.Bold))
                Text(
                    "هر تغییری قبل از ثبت، از شما تأیید می‌گیرد",
                    style = com.example.ui.layer.lt(12.sp, color = L.Muted),
                    maxLines = 1
                )
            }
            if (onNewChat != null && !empty) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(L.Surface)
                        .clickable(enabled = !isLoading) { onNewChat() }
                        .semantics { contentDescription = "گفتگوی تازه" }
                        .testTag("ai_new_chat"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = L.PrimaryText, modifier = Modifier.size(20.dp))
                }
            }
        }

        // ── گفتگو، با قرص نوشتن شناور روی آن
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .edgeGlass(top = 14.dp, bottom = composerSpace - 16.dp, solidTop = 0f, solidBottom = 0.1f)
            ) {
                if (empty) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = composerSpace),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(L.PrimarySoft),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.AutoAwesome, null, tint = L.PrimaryText, modifier = Modifier.size(30.dp)) }
                        Text("از حساب‌وکتابتان بپرسید", style = com.example.ui.layer.lt(18.sp, FontWeight.Bold))
                        Text(
                            "خرج‌ها، قسط‌ها، بدهی و طلب، یا یک پیشنهاد. می‌توانید بنویسید یا با میکروفون بگویید.",
                            style = com.example.ui.layer.lt(13.sp, color = L.Muted, lineHeight = 23.sp),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        presets.forEach { p ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(L.Surface)
                                    .clickable { send(p) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                                    .testTag("preset_prompt_${p.hashCode()}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(p, style = com.example.ui.layer.lt(14.sp, FontWeight.Medium), modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ArrowUpward, null, tint = L.PrimaryText, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = composerSpace)
                    ) {
                        items(visible, key = { it.id }) { msg ->
                            if (msg.status == "pending") ThinkingLines()
                            else ChatBubble(message = msg, onApprove = onApproveAction, onCancel = onCancelAction)
                        }
                        if (isLoading && visible.none { it.status == "pending" }) {
                            item { ThinkingLines() }
                        }
                    }
                }
            }

            // پیام خطای ارتباط
            androidx.compose.animation.AnimatedVisibility(
                visible = toastMessage != null,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                    .testTag("ai_error_toast")
            ) {
                if (toastMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(L.RedSoft)
                            .border(1.dp, L.Red.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = L.RedText, modifier = Modifier.size(22.dp))
                        Column(Modifier.weight(1f)) {
                            Text("ارتباط با دستیار برقرار نشد", style = com.example.ui.layer.lt(14.sp, FontWeight.Bold, L.RedText))
                            Text(toastMessage, style = com.example.ui.layer.lt(12.sp, color = L.RedInk, lineHeight = 20.sp))
                        }
                        IconButton(onClick = onDismissToast, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "بستن", tint = L.RedText, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // ── قرص یک‌تکهٔ شناور: ارسال سمت راست، میکروفون سمت چپ.
            // با متن بلندتر از بالا بزرگ می‌شود و دکمه‌ها پایین سر جایشان می‌مانند؛ بیشتر از ۵ سطر، متن اسکرول می‌شود.
            val canSend = textInput.isNotBlank() && !isLoading
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
                    .onSizeChanged { composerPx = it.height }
                    .fillMaxWidth()
                    .shadow(16.dp, RoundedCornerShape(32.dp), ambientColor = Color(0x1F0E1116), spotColor = Color(0x1F0E1116))
                    .clip(RoundedCornerShape(32.dp))
                    .background(if (isDictating) L.RedSoft else L.Surface)
                    .padding(8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (canSend) L.Primary else L.PrimaryDisabled)
                        .clickable(enabled = canSend) { send(textInput) }
                        .semantics { contentDescription = "ارسال پیام" }
                        .testTag("ai_chat_send_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }

                // Enter سطر تازه می‌سازد؛ ارسال فقط با دکمه
                androidx.compose.foundation.text.BasicTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    minLines = 1,
                    maxLines = 5,
                    textStyle = com.example.ui.layer.lt(15.sp, color = L.Ink, lineHeight = 24.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(L.Primary),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Default
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_chat_text_input"),
                    decorationBox = { inner ->
                        Box(
                            Modifier.heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (textInput.isEmpty()) {
                                Text(
                                    when {
                                        isLoading -> "دستیار در حال پاسخ دادن است…"
                                        isDictating -> "در حال شنیدن… صحبت کنید"
                                        else -> "بپرسید یا بگویید چه ثبت کنم"
                                    },
                                    style = com.example.ui.layer.lt(15.sp, color = if (isDictating) L.RedText else L.Faint),
                                    maxLines = 1
                                )
                            }
                            inner()
                        }
                    }
                )

                // صدا → متن
                if (onToggleDictation != null) {
                    val micPulse = if (isDictating) 1f + dictationAmplitude.coerceIn(0f, 1f) * 0.15f else 1f
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer { scaleX = micPulse; scaleY = micPulse }
                            .clip(CircleShape)
                            .background(if (isDictating) L.Red else Color.Transparent)
                            .clickable(enabled = !isLoading, onClick = startDictation)
                            .semantics { contentDescription = if (isDictating) "پایان شنیدن" else "گفتن به جای نوشتن" }
                            .testTag("ai_chat_mic_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isDictating) Icons.Default.Stop else Icons.Default.Mic, null,
                            tint = if (isDictating) Color.White else L.Ink, modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

/** «در حال فکر»: دو خط کم‌رنگ با برق متحرک، درست جایی که پاسخ می‌آید */
@Composable
fun ThinkingLines() {
    val L = com.example.ui.layer.L
    val t = rememberInfiniteTransition(label = "thinking")
    val x by t.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "shine"
    )
    val base = L.Rail
    val shine = L.Chip
    Column(
        Modifier.padding(vertical = 6.dp).semantics { contentDescription = "دستیار در حال فکر کردن" }.testTag("ai_thinking"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(240.dp, 170.dp).forEach { w ->
            Box(
                Modifier
                    .width(w)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .drawBehind {
                        val sw = size.width
                        drawRect(
                            Brush.linearGradient(
                                colors = listOf(base, shine, base),
                                start = androidx.compose.ui.geometry.Offset(sw * (x - 0.5f), 0f),
                                end = androidx.compose.ui.geometry.Offset(sw * (x + 0.5f), 0f)
                            )
                        )
                    }
            )
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    onApprove: (String) -> Unit,
    onCancel: (String) -> Unit
) {
    val L = com.example.ui.layer.L
    val isUser = message.sender == "user"

    if (isUser) {
        // پرسش کاربر: حباب آبی سمت راست صفحه، گوشهٔ تیز بالا-راست
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Absolute.Right) {
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(AbsoluteRoundedCornerShape(topLeft = 20.dp, topRight = 6.dp, bottomRight = 20.dp, bottomLeft = 20.dp))
                    .background(L.Primary)
                    .padding(horizontal = 16.dp, vertical = 11.dp)
                    .testTag("chat_bubble_${message.id}")
            ) {
                Text(
                    persianDigits(message.text),
                    style = com.example.ui.layer.lt(15.sp, color = Color.White, lineHeight = 24.sp)
                )
            }
        }
    } else {
        // پاسخ دستیار: تمام‌عرض، بدون حباب
        Column(
            modifier = Modifier.fillMaxWidth().testTag("chat_bubble_${message.id}"),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.AutoAwesome, null, tint = L.Primary, modifier = Modifier.size(16.dp))
                Text("دستیار تراز", style = com.example.ui.layer.lt(12.sp, color = L.Muted))
            }
            RichMarkdownContent(text = persianDigits(message.text), isUser = false)
            message.actionProposed?.let { action ->
                ProposedActionCard(messageId = message.id, action = action, onApprove = onApprove, onCancel = onCancel)
            }
        }
    }
}

/** رقم‌های لاتین پاسخ را فارسی می‌کند و جداکنندهٔ هزارگان را «٬» */
private fun persianDigits(text: String): String {
    val withSep = Regex("(?<=\\d),(?=\\d{3})").replace(text, "٬")
    return buildString(withSep.length) {
        withSep.forEach { ch -> append(if (ch in '0'..'9') '۰' + (ch - '0') else ch) }
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
        action.isApproved -> com.example.ui.layer.L.Green
        action.isCancelled -> Color.Gray
        action.type.contains("delete") -> com.example.ui.layer.L.RedText
        else -> com.example.ui.layer.L.Primary
    }
    
    val actionLabel = when (action.type) {
        "add_transaction" -> "ثبت تراکنش جدید"
        "delete_transaction" -> "حذف تراکنش"
        "edit_transaction" -> "ویرایش تراکنش"
        "add_loan" -> "ثبت وام جدید"
        "delete_loan" -> "حذف وام"
        "edit_loan" -> "ویرایش اطلاعات وام"
        "pay_installment" -> "پرداخت قسط وام"
        "add_cheque" -> "ثبت چک"
        "add_bill" -> "ثبت قبض"
        "pay_bill" -> "پرداخت قبض"
        "person_entry" -> "ثبت در حساب شخص"
        else -> "عملیات سیستم"
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.layer.L.Surface),
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
                        color = com.example.ui.layer.L.Ink
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                text = action.description,
                style = MaterialTheme.typography.bodyMedium.copy(color = com.example.ui.layer.L.Muted),
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
                            colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.layer.L.Primary),
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
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = com.example.ui.layer.L.RedText),
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
                            tint = com.example.ui.layer.L.Green,
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
                            .background(com.example.ui.layer.L.Chip, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = "Cancelled",
                            tint = com.example.ui.layer.L.Muted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "این پیشنهاد توسط شما لغو شد.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = com.example.ui.layer.L.Ink,
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
    val textColor = if (isUser) Color.White else com.example.ui.layer.L.Ink
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
        colors = CardDefaults.cardColors(containerColor = com.example.ui.layer.L.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, com.example.ui.layer.L.Line)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { rowIndex, cells ->
                val isHeader = rowIndex == 0
                val rowBg = if (isHeader) {
                    com.example.ui.layer.L.Primary.copy(alpha = 0.08f)
                } else if (rowIndex % 2 == 0) {
                    com.example.ui.layer.L.Line.copy(alpha = 0.3f)
                } else {
                    com.example.ui.layer.L.Surface
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
                                    color = com.example.ui.layer.L.Primary,
                                    fontSize = 13.sp
                                )
                            } else {
                                MaterialTheme.typography.bodyMedium.copy(
                                    color = com.example.ui.layer.L.Ink,
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
                                    .background(com.example.ui.layer.L.Line)
                            )
                        }
                    }
                }
                if (rowIndex < rows.size - 1) {
                    HorizontalDivider(color = com.example.ui.layer.L.Line, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
fun RenderAlertCard(text: String, isActive: Boolean) {
    val bgColor = if (isActive) com.example.ui.layer.L.Green.copy(alpha = 0.1f) else com.example.ui.layer.L.Muted.copy(alpha = 0.1f)
    val borderColor = if (isActive) com.example.ui.layer.L.Green else com.example.ui.layer.L.Muted
    val iconColor = if (isActive) com.example.ui.layer.L.Green else com.example.ui.layer.L.Muted
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
                style = MaterialTheme.typography.bodyMedium.copy(color = com.example.ui.layer.L.Ink)
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
        colors = CardDefaults.cardColors(containerColor = com.example.ui.layer.L.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, com.example.ui.layer.L.Line)
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
                    .background(com.example.ui.layer.L.Primary)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = parseInlineFormatting(text, isUser),
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = com.example.ui.layer.L.Ink,
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
                        color = com.example.ui.layer.L.Primary,
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
                        color = if (isUser) Color.White else com.example.ui.layer.L.Ink
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
                        color = com.example.ui.layer.L.Primary,
                        background = com.example.ui.layer.L.Line
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
        colors = CardDefaults.cardColors(containerColor = com.example.ui.layer.L.Surface),
        border = BorderStroke(1.dp, com.example.ui.layer.L.Primary.copy(alpha = 0.25f)),
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
                    .background(com.example.ui.layer.L.Primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = com.example.ui.layer.L.Primary,
                    strokeWidth = 2.dp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "دستیار مالی تراز در حال تحلیل و پاسخ‌دهی...",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = com.example.ui.layer.L.Ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "لطفاً چند لحظه شکیبا باشید",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = com.example.ui.layer.L.Muted,
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
                        .background(com.example.ui.layer.L.Primary.copy(alpha = dot1Alpha))
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(com.example.ui.layer.L.Primary.copy(alpha = dot2Alpha))
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(com.example.ui.layer.L.Primary.copy(alpha = dot3Alpha))
                )
            }
        }
    }
}
