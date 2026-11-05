package com.example.ui.taraz

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.ChatMessage
import kotlinx.coroutines.delay
import java.util.Locale

/*
 * Voice for Taraz's assistant.
 *
 * Two modes, the same shape as a voice chat client:
 *  - dictation: speak, the words land in the message box, you check them and send;
 *  - live conversation: listen → send → wait for the answer → read it aloud → listen again.
 *    The microphone is off while the answer is being read so the phone doesn't hear itself.
 *
 * Both use the phone's own speech engines (Android SpeechRecognizer / TextToSpeech),
 * and the answers come from Taraz's own assistant server.
 */

fun hasMicPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/** Asks for the microphone once; [onResult] gets whether it was granted. */
@Composable
fun rememberMicPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val latest by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { latest(it) }
    return { launcher.launch(Manifest.permission.RECORD_AUDIO) }
}

fun speechErrorText(code: Int): String = when (code) {
    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "صدایی شنیده نشد. دوباره بگویید."
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, 11 /* SERVER_DISCONNECTED */ ->
        "تبدیل صدا به متن به اینترنت نیاز دارد. اتصال را بررسی کنید."
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "اجازهٔ استفاده از میکروفون داده نشده."
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "میکروفون دست برنامهٔ دیگری است. چند لحظه بعد دوباره امتحان کنید."
    12, 13 /* LANGUAGE_NOT_SUPPORTED / UNAVAILABLE */ -> "موتور گفتار این گوشی فارسی را پشتیبانی نمی‌کند."
    SpeechRecognizer.ERROR_AUDIO -> "ضبط صدا ممکن نشد."
    else -> "تبدیل صدا به متن ممکن نشد. دوباره امتحان کنید."
}

private fun isSilence(code: Int) = code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

/** Persian speech → text through the phone's recognition service. Call from the main thread. */
class VoiceListener(context: Context) {
    private val app = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var session = 0

    /** Microphone loudness, 0..1, for the pulsing circle. */
    var level by mutableFloatStateOf(0f)
        private set
    var listening by mutableStateOf(false)
        private set

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(app)

    fun start(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (Int) -> Unit,
        onSpeechStart: () -> Unit = {}
    ) {
        cancel()
        val id = ++session
        val r = try {
            SpeechRecognizer.createSpeechRecognizer(app)
        } catch (e: Exception) {
            onError(SpeechRecognizer.ERROR_CLIENT)
            return
        }
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            private fun live() = id == session
            override fun onReadyForSpeech(params: Bundle?) { if (live()) listening = true }
            override fun onBeginningOfSpeech() { if (live()) onSpeechStart() }
            override fun onRmsChanged(rmsdB: Float) { if (live()) level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { if (live()) level = 0f }
            override fun onError(error: Int) {
                if (!live()) return
                finish()
                onError(error)
            }
            override fun onResults(results: Bundle?) {
                if (!live()) return
                finish()
                onFinal(results.firstText())
            }
            override fun onPartialResults(partialResults: Bundle?) {
                if (!live()) return
                val text = partialResults.firstText()
                if (text.isNotBlank()) onPartial(text)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, app.packageName)
            // Give people a moment to think between sentences before the turn ends.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        listening = true
        try {
            r.startListening(intent)
        } catch (e: Exception) {
            finish()
            onError(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    /** End the turn now; whatever was said is delivered as the final result. */
    fun stop() {
        recognizer?.stopListening()
    }

    /** Drop the turn without a result. */
    fun cancel() {
        session++
        finish()
    }

    private fun finish() {
        listening = false
        level = 0f
        recognizer?.let {
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        recognizer = null
    }

    private fun Bundle?.firstText(): String =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
}

/** Reads answers aloud with the phone's text-to-speech, if it has a Persian voice. */
class VoiceSpeaker(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private val callbacks = mutableMapOf<String, () -> Unit>()

    var ready by mutableStateOf(false)
        private set
    /** True when a Persian voice is installed; otherwise answers are only shown as text. */
    var persian by mutableStateOf(false)
        private set
    var speakingId by mutableStateOf<String?>(null)
        private set

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            main.post {
                val engine = tts
                if (status == TextToSpeech.SUCCESS && engine != null) {
                    val result = runCatching { engine.setLanguage(Locale.forLanguageTag("fa-IR")) }
                        .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
                    persian = result >= TextToSpeech.LANG_AVAILABLE
                    engine.setSpeechRate(1.0f)
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) = finished(utteranceId)
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) = finished(utteranceId)
                        override fun onError(utteranceId: String?, errorCode: Int) = finished(utteranceId)
                        override fun onStop(utteranceId: String?, interrupted: Boolean) {
                            main.post {
                                if (speakingUtterance == utteranceId) clearSpeaking()
                                callbacks.remove(utteranceId)
                            }
                        }
                    })
                }
                ready = true
            }
        }
    }

    private var speakingUtterance: String? = null

    private fun clearSpeaking() {
        speakingId = null
        speakingUtterance = null
    }

    private fun finished(id: String?) {
        main.post {
            if (speakingUtterance == id) clearSpeaking()
            callbacks.remove(id)?.invoke()
        }
    }

    /** Speaks [text]; [onDone] runs when it finishes on its own (not when stopped). */
    fun speak(id: String, text: String, onDone: () -> Unit = {}) {
        val engine = tts
        val clean = speakableText(text)
        if (engine == null || !persian || clean.isBlank()) {
            onDone()
            return
        }
        stop()
        val utterance = id + "#" + System.nanoTime()
        callbacks[utterance] = onDone
        // speakingId is the message id, so a chat bubble can show "stop" while it is read.
        speakingId = id
        speakingUtterance = utterance
        val max = runCatching { TextToSpeech.getMaxSpeechInputLength() }.getOrDefault(3900) - 50
        engine.speak(clean.take(max), TextToSpeech.QUEUE_FLUSH, null, utterance)
    }

    fun stop() {
        callbacks.clear()
        clearSpeaking()
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        stop()
        runCatching { tts?.shutdown() }
        tts = null
    }
}

/** Plain sentences for reading aloud: no tables, markup, or app tags. */
fun speakableText(markdown: String): String = markdown
    .lines()
    .filterNot { it.trimStart().startsWith("|") }
    .joinToString("\n")
    .replace(Regex("""\[تنظیم یادآوری:[^\]]*]"""), "")
    .replace(Regex("""\[اعلان (غیر)?فعال]"""), "")
    .replace(Regex("""(?m)^\s{0,3}(#{1,6}|>|[-•*])\s*"""), "")
    .replace("**", "")
    .replace("`", "")
    .replace(Regex("""[ \t]+"""), " ")
    .replace(Regex("""\n{2,}"""), "\n")
    .trim()

private enum class LivePhase { LISTENING, THINKING, SPEAKING, PAUSED, ERROR }

/**
 * Hands-free conversation with the assistant. Covers the chat while open.
 */
@Composable
fun LiveVoiceScreen(
    messages: List<ChatMessage>,
    isLoading: Boolean,
    listener: VoiceListener,
    speaker: VoiceSpeaker,
    onSend: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val currentMessages by rememberUpdatedState(messages)
    val busy by rememberUpdatedState(isLoading)

    var phase by remember { mutableStateOf(LivePhase.PAUSED) }
    var heard by remember { mutableStateOf("") }
    var reply by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var sentAt by remember { mutableLongStateOf(0L) }
    var misses by remember { mutableIntStateOf(0) }
    var readAloud by remember { mutableStateOf(true) }

    // A plain object so the recognizer callbacks can call back into the loop.
    val loop = remember { arrayOfNulls<() -> Unit>(1) }
    fun listen() = loop[0]?.invoke()

    loop[0] = {
        speaker.stop()
        heard = ""
        note = null
        when {
            !listener.available -> {
                phase = LivePhase.ERROR
                note = "این گوشی سرویس تبدیل گفتار به متن ندارد. برنامهٔ Google یا «Speech Services by Google» را نصب یا فعال کنید."
            }
            busy -> {
                phase = LivePhase.THINKING
            }
            else -> {
                phase = LivePhase.LISTENING
                listener.start(
                    onPartial = { heard = it },
                    onFinal = { text ->
                        if (text.isBlank()) {
                            listen()
                        } else {
                            misses = 0
                            heard = text
                            reply = ""
                            phase = LivePhase.THINKING
                            sentAt = System.currentTimeMillis()
                            onSend(text)
                        }
                    },
                    onError = { code ->
                        if (isSilence(code)) {
                            misses++
                            if (misses < 3) {
                                listen()
                            } else {
                                misses = 0
                                phase = LivePhase.PAUSED
                                note = "چیزی نشنیدم. هر وقت آماده بودید، دکمه را بزنید و حرف بزنید."
                            }
                        } else {
                            phase = LivePhase.ERROR
                            note = speechErrorText(code)
                        }
                    }
                )
            }
        }
    }

    val askMic = rememberMicPermissionRequest { granted ->
        if (granted) {
            listen()
        } else {
            phase = LivePhase.ERROR
            note = "برای گفتگوی صوتی، اجازهٔ میکروفون لازم است. می‌توانید از تنظیمات گوشی آن را بدهید."
        }
    }

    fun begin() {
        if (hasMicPermission(context)) listen() else askMic()
    }

    LaunchedEffect(Unit) { begin() }

    DisposableEffect(Unit) {
        onDispose {
            listener.cancel()
            speaker.stop()
        }
    }

    // Wait for the assistant's answer to the last thing said, then read it out and listen again.
    LaunchedEffect(phase, messages, isLoading) {
        if (phase != LivePhase.THINKING || sentAt == 0L) return@LaunchedEffect
        val last = messages.lastOrNull() ?: return@LaunchedEffect
        if (last.sender != "ai" || last.status == "pending" || last.timestamp < sentAt || isLoading) return@LaunchedEffect
        if (last.status == "error") {
            phase = LivePhase.ERROR
            note = "پاسخی از دستیار نیامد. اتصال اینترنت را بررسی کنید و دوباره امتحان کنید."
            return@LaunchedEffect
        }
        reply = speakableText(last.text)
        val needsTap = last.actionProposed != null
        if (needsTap) note = "دستیار پیشنهاد یک تغییر داده. برای تأییدش، گفتگو را ببندید و روی کارت پیشنهاد بزنید."
        val next: () -> Unit = { if (needsTap) phase = LivePhase.PAUSED else listen() }
        if (readAloud && speaker.persian) {
            phase = LivePhase.SPEAKING
            speaker.speak(last.id, last.text) { if (phase == LivePhase.SPEAKING) next() }
        } else {
            // Without a voice, give time to read the answer before the next turn.
            phase = LivePhase.PAUSED
        }
    }

    // Safety net: a request that never answers shouldn't leave the screen stuck.
    LaunchedEffect(phase, sentAt) {
        if (phase == LivePhase.THINKING) {
            delay(120_000)
            if (phase == LivePhase.THINKING && !busy) {
                phase = LivePhase.ERROR
                note = "پاسخ دیر کرد. دوباره امتحان کنید."
            }
        }
    }

    val pulse by animateFloatAsState(
        targetValue = if (phase == LivePhase.LISTENING) 1f + listener.level * 0.25f else 1f,
        animationSpec = tween(120),
        label = "voice-pulse"
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(Tz.Ground)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("گفتگوی صوتی", style = tzText(20.sp, FontWeight.Bold))
                Text("حرف بزنید؛ تراز گوش می‌دهد و جواب می‌دهد.", style = tzText(13.sp, color = Tz.Muted))
            }
            TextAction("پایان", onClick = onClose, color = Tz.Red)
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val (icon, tint, bg) = when (phase) {
                LivePhase.LISTENING -> Triple(Icons.Filled.Mic, Color.White, Tz.Cobalt)
                LivePhase.THINKING -> Triple(Icons.Filled.HourglassTop, Tz.Cobalt, Tz.CobaltTint2)
                LivePhase.SPEAKING -> Triple(Icons.Filled.GraphicEq, Color.White, Tz.Cobalt)
                LivePhase.PAUSED -> Triple(Icons.Filled.Mic, Tz.Cobalt, Tz.Surface)
                LivePhase.ERROR -> Triple(Icons.Filled.MicOff, Tz.Red, Tz.RedTint)
            }
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(176.dp)
                        .scale(pulse)
                        .clip(CircleShape)
                        .background(if (phase == LivePhase.LISTENING || phase == LivePhase.SPEAKING) Tz.CobaltTint2 else Color.Transparent)
                )
                Box(
                    Modifier
                        .size(128.dp)
                        .clip(CircleShape)
                        .background(bg)
                        .tzClick {
                            when (phase) {
                                LivePhase.LISTENING -> listener.stop()
                                LivePhase.SPEAKING -> listen()
                                LivePhase.PAUSED, LivePhase.ERROR -> begin()
                                LivePhase.THINKING -> {}
                            }
                        }
                        .semantics { contentDescription = "میکروفون" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(48.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                when (phase) {
                    LivePhase.LISTENING -> if (heard.isBlank()) "گوش می‌دهم…" else "دارم می‌شنوم…"
                    LivePhase.THINKING -> "در حال فکر کردن…"
                    LivePhase.SPEAKING -> "در حال خواندن پاسخ…"
                    LivePhase.PAUSED -> "منتظر شما"
                    LivePhase.ERROR -> "متوقف شد"
                },
                style = tzText(16.sp, FontWeight.Bold, if (phase == LivePhase.ERROR) Tz.Red else Tz.Ink),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )

            Spacer(Modifier.height(20.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (heard.isNotBlank()) Turn("شما", heard, Tz.Surface)
                if (reply.isNotBlank()) Turn("تراز", reply, Tz.CobaltTint)
                note?.let {
                    Text(
                        it,
                        style = tzText(13.sp, FontWeight.SemiBold, if (phase == LivePhase.ERROR) Tz.RedText else Tz.Ink, lineHeight = 22.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(Tz.RowShape)
                            .background(if (phase == LivePhase.ERROR) Tz.RedTint else Tz.Field)
                            .padding(12.dp)
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(Tz.RowShape)
                .background(Tz.Surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("خواندن پاسخ با صدا", style = tzText(14.sp, FontWeight.SemiBold))
                Text(
                    when {
                        !speaker.ready -> "در حال آماده‌سازی صدا…"
                        speaker.persian -> "میکروفون هنگام خواندن پاسخ خاموش است."
                        else -> "صدای فارسی روی این گوشی نصب نیست؛ پاسخ‌ها فقط نوشته می‌شوند."
                    },
                    style = tzText(12.sp, color = Tz.Muted, lineHeight = 18.sp)
                )
            }
            if (speaker.persian) {
                TzSwitch(readAloud, {
                    readAloud = it
                    if (!it && phase == LivePhase.SPEAKING) {
                        speaker.stop()
                        phase = LivePhase.PAUSED
                    }
                }, "خواندن پاسخ با صدا")
            }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton(
            when (phase) {
                LivePhase.LISTENING -> "تمام شد، بفرست"
                LivePhase.THINKING -> "منتظر پاسخ…"
                LivePhase.SPEAKING -> "قطع کن، من حرف می‌زنم"
                LivePhase.PAUSED, LivePhase.ERROR -> "حرف بزنید"
            },
            enabled = phase != LivePhase.THINKING,
            icon = if (phase == LivePhase.LISTENING) null else Icons.Filled.Mic,
            onClick = {
                when (phase) {
                    LivePhase.LISTENING -> listener.stop()
                    LivePhase.SPEAKING -> listen()
                    else -> begin()
                }
            }
        )
        Text(
            "صدا با موتور گفتار خود گوشی به متن تبدیل می‌شود.",
            style = tzText(11.sp, color = Tz.Muted, align = TextAlign.Center),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
    }
}

@Composable
private fun Turn(who: String, text: String, background: Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Tz.RowShape)
            .background(background)
            .padding(12.dp)
    ) {
        Text(who, style = tzText(12.sp, FontWeight.Bold, Tz.Muted))
        Text(text, style = tzText(15.sp, lineHeight = 26.sp))
    }
}
