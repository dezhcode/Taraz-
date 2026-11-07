package com.example.ai

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID

/**
 * گفت‌وگوی زنده واقعی با Duck.ai — پیاده‌سازی LiveVoiceSession از duck_voice.py:
 *
 *   GET  /duckchat/v1/ice-servers → TURN
 *   RTCPeerConnection (iceServers + datachannel «duckai-voice-session»)
 *   POST /duckchat/v1/session  {"sdp": offer, "session": {"voice": "voice-1"}} → answer SDP
 *   صدای میکروفون روی RTP (Opus) می‌رود و رویدادها روی datachannel برمی‌گردند.
 *   VAD سمت سرور است؛ هنگام پخش صدای دستیار میکروفون بی‌صدا می‌شود (جلوگیری از اکو).
 *
 * WebRTC داخل یک WebView مخفی اجرا می‌شود (موتور WebRTC کروم با حذف اکو)، و درخواست‌های
 * HTTP با همان کلاینت Duck.ai (کوکی‌ها + اثبات چالش) از سمت Kotlin ارسال می‌شوند.
 */
class DuckRealtimeVoiceSession(
    context: Context,
    private val duckClient: DuckAiClient,
    private val listener: Listener
) {
    interface Listener {
        fun onConnected()
        fun onUserSpeechStart()
        fun onUserSpeechStop()
        fun onUserTranscriptDelta(delta: String)
        fun onUserTranscript(text: String)
        fun onAssistantDelta(delta: String)
        fun onAssistantDone(text: String)
        fun onAssistantAudioStart()
        fun onAssistantAudioEnd()
        fun onToolCall(name: String)
        /** جلسه بعد از اتصال بسته شد (قطع شبکه، پایان از سمت سرور و ...) */
        fun onDisconnected(reason: String)
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** برای نمایش در UI (۱dp) تا مرورگر صدا و تایمرها را متوقف نکند */
    @Volatile
    var webView: WebView? = null
        private set

    private var pageLoaded: CompletableDeferred<Unit>? = null
    private var offerDeferred: CompletableDeferred<String>? = null
    private var dcOpenDeferred: CompletableDeferred<Unit>? = null

    @Volatile
    private var connected = false
    @Volatile
    private var stopped = false
    @Volatile
    private var connectedAt = 0L
    private var lastAssistantTranscript = ""

    private var previousAudioMode = AudioManager.MODE_NORMAL
    private var previousSpeakerOn = false

    // ─────────────────── اتصال ───────────────────

    /**
     * اتصال کامل؛ در صورت شکست استثنا پرتاب می‌کند تا کنترلر به حالت جایگزین برود.
     * @param contextInstructions اطلاعات مالی کاربر برای session.update (اختیاری)
     */
    suspend fun connect(voice: String, contextInstructions: String?) {
        stopped = false
        connected = false
        val journeyId = UUID.randomUUID().toString().replace("-", "")

        val ice = duckClient.fetchIceServers(journeyId).getOrThrow()

        createWebView()
        withTimeout(10_000) { pageLoaded!!.await() }

        offerDeferred = CompletableDeferred()
        dcOpenDeferred = CompletableDeferred()
        runJs("startSession(${JSONObject.quote(ice.toString())})")

        val offer = withTimeout(20_000) { offerDeferred!!.await() }
        val answer = duckClient.postVoiceSession(offer, voice, journeyId).getOrThrow()
        runJs("applyAnswer(${JSONObject.quote(answer)})")

        // مثل duck_voice.py: اگر در ۳۰ ثانیه datachannel باز نشد، اتصال ناموفق است
        withTimeout(30_000) { dcOpenDeferred!!.await() }
        connected = true
        connectedAt = System.currentTimeMillis()
        routeAudioToSpeaker(true)

        if (!contextInstructions.isNullOrBlank()) {
            val update = JSONObject().apply {
                put("type", "session.update")
                put("session", JSONObject().put("instructions", contextInstructions))
            }
            runJs("sendJson(${JSONObject.quote(update.toString())})")
        }
        mainHandler.post { listener.onConnected() }
    }

    fun setMuted(muted: Boolean) {
        runJs("setUserMuted(${if (muted) "true" else "false"})")
    }

    /** مدت زمانی که از اتصال گذشته (برای تشخیص رد شدن session.update توسط سرور) */
    fun millisSinceConnected(): Long =
        if (connectedAt == 0L) 0L else System.currentTimeMillis() - connectedAt

    fun stop() {
        if (stopped) return
        stopped = true
        connected = false
        routeAudioToSpeaker(false)
        offerDeferred?.cancel()
        dcOpenDeferred?.cancel()
        pageLoaded?.cancel()
        mainHandler.post {
            val wv = webView ?: return@post
            try {
                wv.evaluateJavascript("stopSession()", null)
            } catch (_: Exception) {
            }
            mainHandler.postDelayed({
                try {
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv.destroy()
                } catch (_: Exception) {
                }
            }, 300)
            webView = null
        }
    }

    // ─────────────────── WebView ───────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun createWebView() {
        val loaded = CompletableDeferred<Unit>()
        pageLoaded = loaded
        val created = CompletableDeferred<Unit>()
        mainHandler.post {
            try {
                val wv = WebView(appContext)
                wv.settings.javaScriptEnabled = true
                wv.settings.mediaPlaybackRequiresUserGesture = false
                wv.settings.domStorageEnabled = true
                wv.addJavascriptInterface(Bridge(), "TarazVoice")
                wv.webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest) {
                        mainHandler.post {
                            if (request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                                request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                            } else {
                                request.deny()
                            }
                        }
                    }
                }
                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        loaded.complete(Unit)
                    }
                }
                // مبدأ https تا getUserMedia در «secure context» مجاز باشد
                wv.loadDataWithBaseURL("${DuckAiClient.BASE_URL}/", PAGE_HTML, "text/html", "utf-8", null)
                webView = wv
                created.complete(Unit)
            } catch (e: Exception) {
                created.completeExceptionally(e)
                loaded.completeExceptionally(e)
            }
        }
        created.await()
    }

    private fun runJs(script: String) {
        mainHandler.post {
            try {
                webView?.evaluateJavascript(script, null)
            } catch (_: Exception) {
            }
        }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onJs(kind: String, data: String) {
            mainHandler.post { handleJs(kind, data) }
        }
    }

    private fun handleJs(kind: String, data: String) {
        if (stopped) return
        when (kind) {
            "offer" -> offerDeferred?.complete(data)
            "error" -> {
                Log.w(TAG, "WebRTC error: $data")
                val err = Exception(data)
                offerDeferred?.completeExceptionally(err)
                dcOpenDeferred?.completeExceptionally(err)
                if (connected) disconnect("خطای WebRTC: $data")
            }
            "dc_open" -> dcOpenDeferred?.complete(Unit)
            "dc_closed" -> {
                dcOpenDeferred?.completeExceptionally(Exception("datachannel بسته شد"))
                if (connected) disconnect("جلسه صوتی از طرف سرور بسته شد.")
            }
            "pc_state" -> {
                if (data == "failed" || data == "closed") {
                    dcOpenDeferred?.completeExceptionally(Exception("اتصال WebRTC ناموفق ($data)"))
                    if (connected) disconnect("اتصال صوتی قطع شد.")
                }
            }
            "dc_message" -> handleDataChannelMessage(data)
            "log" -> Log.d(TAG, data)
        }
    }

    private fun disconnect(reason: String) {
        if (!connected) return
        connected = false
        listener.onDisconnected(reason)
    }

    /** نگاشت رویدادهای datachannel دقیقاً مطابق _on_dc_message در duck_voice.py */
    private fun handleDataChannelMessage(raw: String) {
        val v = try {
            JSONObject(raw)
        } catch (_: Exception) {
            return
        }
        when (v.optString("type")) {
            "input_audio_buffer.speech_started" -> listener.onUserSpeechStart()
            "input_audio_buffer.speech_stopped" -> listener.onUserSpeechStop()
            "conversation.item.input_audio_transcription.delta" ->
                listener.onUserTranscriptDelta(v.optString("delta"))
            "conversation.item.input_audio_transcription.completed" ->
                listener.onUserTranscript(v.optString("transcript"))
            "response.created" -> lastAssistantTranscript = ""
            "response.output_audio_transcript.delta" -> listener.onAssistantDelta(v.optString("delta"))
            "response.output_audio_transcript.done" -> {
                val txt = v.optString("transcript")
                if (txt.isNotBlank() && txt != lastAssistantTranscript) {
                    lastAssistantTranscript = txt
                    listener.onAssistantDone(txt)
                }
            }
            "response.output_item.done" -> {
                val item = v.optJSONObject("item") ?: return
                when (item.optString("type")) {
                    "function_call" -> listener.onToolCall(item.optString("name"))
                    "message" -> {
                        val txt = item.optJSONArray("content")?.optJSONObject(0)?.optString("transcript").orEmpty()
                        if (txt.isNotBlank() && txt != lastAssistantTranscript) {
                            lastAssistantTranscript = txt
                            listener.onAssistantDone(txt)
                        }
                    }
                }
            }
            "output_audio_buffer.started" -> listener.onAssistantAudioStart()
            "output_audio_buffer.stopped", "output_audio_buffer.cleared" -> listener.onAssistantAudioEnd()
            "error" -> Log.w(TAG, "datachannel error: $raw")
        }
    }

    // ─────────────────── خروجی صدا ───────────────────

    /** WebRTC صدا را در حالت مکالمه پخش می‌کند؛ بدون این، صدا از گوشی مکالمه (earpiece) می‌آید. */
    @Suppress("DEPRECATION")
    private fun routeAudioToSpeaker(enable: Boolean) {
        try {
            if (enable) {
                previousAudioMode = audioManager.mode
                previousSpeakerOn = audioManager.isSpeakerphoneOn
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager.availableCommunicationDevices
                        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                        ?.let { audioManager.setCommunicationDevice(it) }
                } else {
                    audioManager.isSpeakerphoneOn = true
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager.clearCommunicationDevice()
                } else {
                    audioManager.isSpeakerphoneOn = previousSpeakerOn
                }
                audioManager.mode = previousAudioMode
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Audio routing failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "DuckRealtimeVoice"

        private val PAGE_HTML = """
            <!DOCTYPE html>
            <html><head><meta charset="utf-8"></head>
            <body style="margin:0;background:transparent">
            <audio id="remote" autoplay playsinline></audio>
            <script>
            let pc = null, dc = null, micTrack = null;
            let assistantSpeaking = false, userMuted = false;

            function post(kind, data) {
              try { TarazVoice.onJs(kind, typeof data === 'string' ? data : JSON.stringify(data || {})); } catch (e) {}
            }

            // میکروفون فقط وقتی فعال است که کاربر بی‌صدا نکرده و دستیار در حال صحبت نیست
            // (همان self.mic.muted = True در duck_voice.py)
            function updateMic() {
              if (micTrack) micTrack.enabled = !userMuted && !assistantSpeaking;
            }

            function setUserMuted(m) { userMuted = !!m; updateMic(); }

            function waitIceGathering(timeoutMs) {
              return new Promise(resolve => {
                if (!pc || pc.iceGatheringState === 'complete') return resolve();
                const t = setTimeout(resolve, timeoutMs);
                pc.addEventListener('icegatheringstatechange', () => {
                  if (pc.iceGatheringState === 'complete') { clearTimeout(t); resolve(); }
                });
              });
            }

            async function startSession(iceJson) {
              try {
                const iceServers = JSON.parse(iceJson);
                pc = new RTCPeerConnection({ iceServers: iceServers });

                const stream = await navigator.mediaDevices.getUserMedia({
                  audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true,
                           channelCount: 1, sampleRate: 48000 }
                });
                micTrack = stream.getAudioTracks()[0];
                pc.addTrack(micTrack, stream);

                dc = pc.createDataChannel('duckai-voice-session');
                dc.onopen = () => post('dc_open', '');
                dc.onclose = () => post('dc_closed', '');
                dc.onmessage = (ev) => {
                  try {
                    const t = JSON.parse(ev.data).type || '';
                    if (t === 'output_audio_buffer.started') { assistantSpeaking = true; updateMic(); }
                    else if (t === 'output_audio_buffer.stopped' || t === 'output_audio_buffer.cleared') {
                      assistantSpeaking = false; updateMic();
                    }
                  } catch (e) {}
                  post('dc_message', ev.data);
                };

                pc.ontrack = (ev) => {
                  const audio = document.getElementById('remote');
                  audio.srcObject = (ev.streams && ev.streams[0]) ? ev.streams[0] : new MediaStream([ev.track]);
                  const p = audio.play();
                  if (p && p.catch) p.catch(err => post('log', 'play failed: ' + err));
                };
                pc.onconnectionstatechange = () => post('pc_state', pc.connectionState);

                const offer = await pc.createOffer();
                await pc.setLocalDescription(offer);
                // سرور trickle ICE ندارد؛ offer کامل با candidateها ارسال می‌شود
                await waitIceGathering(4000);
                post('offer', pc.localDescription.sdp);
              } catch (e) {
                post('error', String((e && e.message) || e));
              }
            }

            async function applyAnswer(sdp) {
              try {
                await pc.setRemoteDescription({ type: 'answer', sdp: sdp });
              } catch (e) {
                post('error', 'answer: ' + String((e && e.message) || e));
              }
            }

            function sendJson(text) {
              try { if (dc && dc.readyState === 'open') dc.send(text); } catch (e) {}
            }

            function stopSession() {
              try { if (micTrack) micTrack.stop(); } catch (e) {}
              try { if (dc) dc.close(); } catch (e) {}
              try { if (pc) pc.close(); } catch (e) {}
              pc = null; dc = null; micTrack = null;
            }
            </script>
            </body></html>
        """.trimIndent()
    }
}
