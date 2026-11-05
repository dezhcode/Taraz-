package com.example.ai

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

enum class LiveVoicePhase {
    IDLE,
    CONNECTING,
    LISTENING_USER,
    PROCESSING,
    SPEAKING_ASSISTANT
}

data class LiveTranscriptTurn(
    val role: String, // "user" | "assistant"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class LiveVoiceState(
    val isActive: Boolean = false,
    val phase: LiveVoicePhase = LiveVoicePhase.IDLE,
    val selectedVoiceId: String = "voice-1",
    val isMuted: Boolean = false,
    val audioLevel: Float = 0.08f, // 0f..1f برای گوی موج صوتی
    val currentPartialUserText: String = "",
    val statusBanner: String = "آماده شروع گفت‌وگوی صوتی با دستیار تراز",
    val turns: List<LiveTranscriptTurn> = emptyList()
)

data class VoicePreset(
    val id: String,
    val nameFa: String,
    val pitch: Float,
    val speed: Float
)

val TARAZ_VOICE_PRESETS = listOf(
    VoicePreset("voice-1", "آوا (طبیعی)", 1.0f, 1.0f),
    VoicePreset("voice-2", "سهراب (عمیق)", 0.88f, 0.96f),
    VoicePreset("voice-3", "نیما (پرانرژی)", 1.08f, 1.12f),
    VoicePreset("voice-4", "باران (شفاف)", 1.15f, 1.0f)
)

/**
 * مدیریت قابلیت‌های صوتی (منتقل‌شده از DuckVoiceController پروژه AI Studio):
 * 1) دیکته (صدا → متن): SpeechRecognizer فارسی؛ اگر سرویس تشخیص گفتار روی گوشی نبود،
 *    صدای خام میکروفون ضبط و به /duckchat/v1/dictation فرستاده می‌شود.
 * 2) پخش صوتی پاسخ‌ها (متن → صدا) با TextToSpeech فارسی و پریست‌های صدا.
 * 3) گفت‌وگوی زنده دوطرفه: تشخیص پایان گفتار، بی‌صدا شدن میکروفون هنگام صحبت دستیار
 *    (جلوگیری از اکو) و ادامه خودکار شنیدن.
 *
 * پاسخ هر نوبت از طریق [liveResponder] تولید می‌شود تا گفت‌وگوی زنده هم از همان
 * دستیار مالی تراز (با اطلاعات کارت‌ها، وام‌ها و تراکنش‌ها) استفاده کند.
 */
class DuckVoiceController(
    private val context: Context,
    private val duckClient: DuckAiClient,
    private val scope: CoroutineScope
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _liveState = MutableStateFlow(LiveVoiceState())
    val liveState: StateFlow<LiveVoiceState> = _liveState.asStateFlow()

    private val _isDictating = MutableStateFlow(false)
    val isDictating: StateFlow<Boolean> = _isDictating.asStateFlow()

    private val _dictationAmplitude = MutableStateFlow(0.1f)
    val dictationAmplitude: StateFlow<Float> = _dictationAmplitude.asStateFlow()

    /** شناسه پیامی که در حال خوانده شدن است (برای نمایش وضعیت دکمه بلندگو) */
    private val _speakingMessageId = MutableStateFlow<String?>(null)
    val speakingMessageId: StateFlow<String?> = _speakingMessageId.asStateFlow()

    private var tts: TextToSpeech? = null
    @Volatile
    private var isTtsReady = false

    private var speechRecognizer: SpeechRecognizer? = null
    private var audioRecordJob: Job? = null
    private var liveLoopJob: Job? = null
    private val recordedPcmStream = ByteArrayOutputStream()
    @Volatile
    private var dictationUsesNative = false

    /** تولید پاسخ برای یک نوبت گفت‌وگوی زنده (متن کاربر → متن پاسخ دستیار) */
    var liveResponder: (suspend (String) -> String)? = null

    private var onDictationResult: ((String) -> Unit)? = null
    private var onDictationError: ((String) -> Unit)? = null

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                val faResult = tts?.setLanguage(Locale("fa", "IR"))
                if (faResult == TextToSpeech.LANG_MISSING_DATA || faResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.US)
                }
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (_liveState.value.isActive && utteranceId == LIVE_UTTERANCE) {
                            _liveState.update {
                                it.copy(
                                    phase = LiveVoicePhase.SPEAKING_ASSISTANT,
                                    statusBanner = "🔊 دستیار در حال صحبت… (میکروفون موقتاً بی‌صدا شد)"
                                )
                            }
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == LIVE_UTTERANCE) {
                            if (_liveState.value.isActive) mainHandler.post { resumeListeningInLiveSession() }
                        } else {
                            _speakingMessageId.value = null
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == LIVE_UTTERANCE) {
                            if (_liveState.value.isActive) mainHandler.post { resumeListeningInLiveSession() }
                        } else {
                            _speakingMessageId.value = null
                        }
                    }
                })
            }
        }
    }

    // ─────────────────── پخش صوتی پاسخ‌ها (Text → Speech) ───────────────────

    fun speakMessageAloud(messageId: String, text: String) {
        if (!isTtsReady || text.isBlank()) return
        if (_speakingMessageId.value == messageId) {
            stopSpeaking()
            return
        }
        applyPreset(_liveState.value.selectedVoiceId)
        _speakingMessageId.value = messageId
        tts?.speak(cleanForSpeech(text), TextToSpeech.QUEUE_FLUSH, null, "MSG_$messageId")
    }

    fun stopSpeaking() {
        tts?.stop()
        _speakingMessageId.value = null
    }

    private fun applyPreset(voiceId: String) {
        val preset = TARAZ_VOICE_PRESETS.find { it.id == voiceId } ?: TARAZ_VOICE_PRESETS.first()
        tts?.setPitch(preset.pitch)
        tts?.setSpeechRate(preset.speed)
    }

    // ─────────────────── ۱. دیکته صوتی (Voice → Text) ───────────────────

    /**
     * @param onResult متن (جزئی یا نهایی) تشخیص‌داده‌شده؛ هر فراخوانی جایگزین قبلی است.
     * @param onError پیام خطای قابل نمایش به کاربر
     */
    fun toggleDictation(onResult: (String) -> Unit, onError: (String) -> Unit) {
        if (_isDictating.value) {
            stopDictation()
            return
        }
        onDictationResult = onResult
        onDictationError = onError
        _isDictating.value = true
        synchronized(recordedPcmStream) { recordedPcmStream.reset() }

        mainHandler.post {
            val recognitionAvailable = try {
                SpeechRecognizer.isRecognitionAvailable(context)
            } catch (_: Exception) {
                false
            }
            dictationUsesNative = recognitionAvailable
            if (recognitionAvailable) {
                startNativeDictation()
            } else {
                // گوشی سرویس تشخیص گفتار ندارد → ضبط صدای خام و ارسال به Duck.ai
                startRawMicCaptureForDictation()
                scope.launch {
                    delay(MAX_RAW_DICTATION_MS)
                    if (_isDictating.value) stopDictation()
                }
            }
        }
    }

    private fun startNativeDictation() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {
                        _dictationAmplitude.value = ((rmsdB + 2f) / 12f).coerceIn(0.08f, 1.0f)
                    }
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        if (!_isDictating.value) return
                        _isDictating.value = false
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "صدایی تشخیص داده نشد، دوباره امتحان کنید."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "دسترسی میکروفون داده نشده است."
                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "تبدیل صدا به متن به اینترنت نیاز دارد."
                            else -> "تبدیل صدا به متن با خطا مواجه شد."
                        }
                        onDictationError?.invoke(msg)
                    }
                    override fun onResults(results: Bundle?) {
                        _isDictating.value = false
                        val recognized = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()?.trim()
                        if (!recognized.isNullOrBlank()) onDictationResult?.invoke(recognized)
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()?.trim()
                        if (!partial.isNullOrBlank()) onDictationResult?.invoke(partial)
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
            speechRecognizer?.startListening(recognizerIntent())
        } catch (e: Exception) {
            _isDictating.value = false
            onDictationError?.invoke("سرویس تشخیص گفتار در دسترس نیست.")
        }
    }

    private fun startRawMicCaptureForDictation() {
        audioRecordJob?.cancel()
        audioRecordJob = scope.launch(Dispatchers.IO) {
            val sampleRate = 16000
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) return@launch
            var recorder: AudioRecord? = null
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf * 2
                )
                if (recorder.state != AudioRecord.STATE_INITIALIZED) return@launch
                recorder.startRecording()
                val buf = ByteArray(1024)
                while (isActive && _isDictating.value) {
                    val read = recorder.read(buf, 0, buf.size)
                    if (read > 0) {
                        synchronized(recordedPcmStream) {
                            if (recordedPcmStream.size() < 320_000) recordedPcmStream.write(buf, 0, read)
                        }
                        var sum = 0.0
                        var i = 0
                        while (i + 1 < read) {
                            val sample = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)).toShort()
                            sum += sample * sample
                            i += 2
                        }
                        val rms = sqrt(sum / (read / 2.0))
                        _dictationAmplitude.value = (rms / 4500.0).toFloat().coerceIn(0.08f, 1.0f)
                    }
                }
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            } finally {
                try {
                    recorder?.stop()
                    recorder?.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun stopDictation() {
        if (!_isDictating.value) return
        if (dictationUsesNative) {
            // نتیجه نهایی از طریق onResults/onError می‌رسد
            mainHandler.post {
                try {
                    speechRecognizer?.stopListening()
                } catch (_: Exception) {
                }
            }
            return
        }
        _isDictating.value = false
        val captureJob = audioRecordJob
        scope.launch {
            captureJob?.join()
            val rawAudio = synchronized(recordedPcmStream) { recordedPcmStream.toByteArray() }
            if (rawAudio.isEmpty()) {
                onDictationError?.invoke("صدایی از میکروفون دریافت نشد.")
                return@launch
            }
            val apiRes = duckClient.transcribeAudioBytes(rawAudio)
            val text = apiRes.getOrNull()
            if (!text.isNullOrBlank()) onDictationResult?.invoke(text)
            else onDictationError?.invoke("تبدیل صدا به متن ناموفق بود.")
        }
    }

    private fun recognizerIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }

    // ─────────────────── ۲. گفت‌وگوی زنده صوتی ───────────────────

    fun setVoicePreset(voiceId: String) {
        _liveState.update { it.copy(selectedVoiceId = voiceId) }
    }

    fun toggleLiveMute() {
        val nowMuted = !_liveState.value.isMuted
        _liveState.update {
            it.copy(
                isMuted = nowMuted,
                statusBanner = if (nowMuted) "🔇 میکروفون توسط شما بسته شد" else "🎙️ میکروفون فعال است — صحبت کنید…"
            )
        }
        mainHandler.post {
            if (nowMuted) {
                try {
                    speechRecognizer?.cancel()
                } catch (_: Exception) {
                }
            } else if (_liveState.value.phase == LiveVoicePhase.LISTENING_USER) {
                beginLiveSpeechRecognitionTurn()
            }
        }
    }

    fun startLiveSession() {
        if (_liveState.value.isActive) return
        stopSpeaking()
        _liveState.value = LiveVoiceState(
            isActive = true,
            phase = LiveVoicePhase.CONNECTING,
            selectedVoiceId = _liveState.value.selectedVoiceId,
            statusBanner = "در حال آماده‌سازی کانال صوتی…"
        )

        liveLoopJob?.cancel()
        liveLoopJob = scope.launch {
            val (_, bannerMsg) = duckClient.initiateLiveVoiceSession(_liveState.value.selectedVoiceId)
            if (!_liveState.value.isActive) return@launch
            _liveState.update {
                it.copy(phase = LiveVoicePhase.LISTENING_USER, statusBanner = "✔ $bannerMsg — صحبت کنید")
            }

            // انیمیشن موج صوتی در حین جلسه
            launch {
                var tick = 0f
                while (isActive && _liveState.value.isActive) {
                    tick += 0.25f
                    val s = _liveState.value
                    val target = when (s.phase) {
                        LiveVoicePhase.LISTENING_USER -> if (s.isMuted) 0.05f else 0.25f + abs(kotlin.math.sin(tick)) * 0.45f
                        LiveVoicePhase.SPEAKING_ASSISTANT -> 0.4f + abs(kotlin.math.cos(tick * 1.4f)) * 0.55f
                        LiveVoicePhase.PROCESSING -> 0.2f
                        else -> 0.1f
                    }
                    _liveState.update { it.copy(audioLevel = target) }
                    delay(60)
                }
            }

            mainHandler.post { beginLiveSpeechRecognitionTurn() }
        }
    }

    private fun beginLiveSpeechRecognitionTurn() {
        val s = _liveState.value
        if (!s.isActive || s.isMuted) return
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                _liveState.update {
                    it.copy(statusBanner = "سرویس تشخیص گفتار روی این گوشی نیست؛ جمله‌تان را تایپ کنید.")
                }
                return
            }
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _liveState.update {
                            it.copy(phase = LiveVoicePhase.LISTENING_USER, statusBanner = "🎙️ در حال شنیدن صدای شما…")
                        }
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {
                        _liveState.update { it.copy(audioLevel = ((rmsdB + 2f) / 12f).coerceIn(0.1f, 1f)) }
                    }
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        _liveState.update {
                            it.copy(phase = LiveVoicePhase.PROCESSING, statusBanner = "⏳ پایان گفتار تشخیص داده شد…")
                        }
                    }
                    override fun onError(error: Int) {
                        // سکوت یا عدم تشخیص: دوباره گوش بده تا جلسه زنده قطع نشود
                        val st = _liveState.value
                        if (st.isActive && !st.isMuted && st.phase != LiveVoicePhase.SPEAKING_ASSISTANT &&
                            (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                        ) {
                            mainHandler.postDelayed({ resumeListeningInLiveSession() }, 300)
                        } else if (st.isActive && st.phase != LiveVoicePhase.SPEAKING_ASSISTANT) {
                            _liveState.update {
                                it.copy(
                                    phase = LiveVoicePhase.LISTENING_USER,
                                    statusBanner = "تشخیص گفتار متوقف شد؛ برای ادامه میکروفون را دوباره روشن کنید یا تایپ کنید."
                                )
                            }
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()?.trim()
                        if (!text.isNullOrBlank()) {
                            sendUserVoiceTurnInLiveSession(text)
                        } else {
                            mainHandler.postDelayed({ resumeListeningInLiveSession() }, 300)
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()?.trim().orEmpty()
                        if (partial.isNotEmpty()) _liveState.update { it.copy(currentPartialUserText = partial) }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
            speechRecognizer?.startListening(recognizerIntent())
        } catch (_: Exception) {
        }
    }

    /**
     * ارسال گفتار کاربر در جلسه زنده (از میکروفون یا کادر متنی) و پخش صوتی پاسخ دستیار.
     */
    fun sendUserVoiceTurnInLiveSession(userSpokenText: String) {
        if (!_liveState.value.isActive || userSpokenText.isBlank()) return
        stopSpeaking()
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {
            }
        }

        _liveState.update {
            it.copy(
                phase = LiveVoicePhase.PROCESSING,
                currentPartialUserText = "",
                turns = it.turns + LiveTranscriptTurn("user", userSpokenText),
                statusBanner = "⚡ دستیار تراز در حال بررسی…"
            )
        }

        scope.launch {
            val reply = (try {
                liveResponder?.invoke(userSpokenText)
                    ?: duckClient.complete(userSpokenText).getOrElse { "متأسفانه الان نمی‌تونم جواب بدم." }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "ارتباط با دستیار برقرار نشد، لطفاً دوباره امتحان کن."
            }).trim()

            if (!_liveState.value.isActive) return@launch
            _liveState.update { it.copy(turns = it.turns + LiveTranscriptTurn("assistant", reply)) }

            if (isTtsReady && reply.isNotBlank() && !_liveState.value.isMuted) {
                applyPreset(_liveState.value.selectedVoiceId)
                _liveState.update {
                    it.copy(phase = LiveVoicePhase.SPEAKING_ASSISTANT, statusBanner = "🔊 دستیار در حال صحبت…")
                }
                tts?.speak(cleanForSpeech(reply).take(600), TextToSpeech.QUEUE_FLUSH, null, LIVE_UTTERANCE)
            } else {
                delay(600)
                resumeListeningInLiveSession()
            }
        }
    }

    private fun resumeListeningInLiveSession() {
        if (!_liveState.value.isActive) return
        _liveState.update {
            it.copy(phase = LiveVoicePhase.LISTENING_USER, statusBanner = "🎙️ نوبت شماست — صحبت کنید")
        }
        beginLiveSpeechRecognitionTurn()
    }

    fun endLiveSession() {
        liveLoopJob?.cancel()
        tts?.stop()
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {
            }
        }
        _liveState.update {
            it.copy(
                isActive = false,
                phase = LiveVoicePhase.IDLE,
                currentPartialUserText = "",
                statusBanner = "گفت‌وگوی صوتی پایان یافت."
            )
        }
    }

    fun release() {
        audioRecordJob?.cancel()
        liveLoopJob?.cancel()
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {
            }
            speechRecognizer = null
        }
        tts?.stop()
        tts?.shutdown()
    }

    companion object {
        private const val LIVE_UTTERANCE = "LIVE_ASSISTANT_SPEECH"
        private const val MAX_RAW_DICTATION_MS = 10_000L

        /** حذف علائم Markdown، جداول و تگ‌های سیستمی قبل از خواندن متن */
        fun cleanForSpeech(text: String): String = text
            .replace(Regex("""\[(PROPOSE_ACTION|تنظیم یادآوری)[^\]]*]"""), " ")
            .replace(Regex("""\[[^\]]*]"""), " ")
            .replace("```", " ")
            .replace(Regex("""[*#`>|•_~]"""), " ")
            .replace(Regex("""-{3,}"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
