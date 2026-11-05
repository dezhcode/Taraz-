package com.example.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * رویدادهای استریم Duck.ai (منتقل‌شده از پروژه AI Studio)
 */
sealed class DuckStreamEvent {
    data class HandshakeLog(val step: String, val detail: String) : DuckStreamEvent()
    data class TextDelta(val delta: String) : DuckStreamEvent()
    data class ToolCall(val toolName: String, val state: String) : DuckStreamEvent()
    data class Error(val message: String, val code: Int? = null) : DuckStreamEvent()
}

/**
 * کلاینت Duck.ai برای تراز — برگرفته از DuckAiClient پروژه AI Studio.
 *
 * 1) Bootstrap (استخراج data-version-tag / data-version-sha → x-fe-version)
 * 2) دریافت چالش از /duckchat/v1/status با هدر x-vqd-accept: 1
 * 3) حل چالش JSA داخل WebView و ساخت x-vqd-hash-1
 * 4) استریم SSE متن از /duckchat/v1/chat
 * 5) دیکته صوتی از /duckchat/v1/dictation
 * 6) آماده‌سازی جلسه صوتی زنده از /duckchat/v1/ice-servers
 *
 * تفاوت با نسخه AI Studio: در صورت شکست شبکه، پاسخ ساختگی تولید نمی‌شود؛ خطا برگردانده
 * می‌شود تا AiRepositoryImpl بتواند به سرور اختصاصی تراز سوییچ کند. داده‌های مالی کاربر
 * نباید با یک پاسخ ساختگی اشتباه گرفته شوند.
 */
class DuckAiClient(context: Context) {

    companion object {
        private const val TAG = "DuckAiClient"
        const val BASE_URL = "https://duck.ai"
        const val DEFAULT_MODEL = "gpt-6-luna"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"

        @Volatile
        private var instance: DuckAiClient? = null

        /** یک نمونه مشترک تا کوکی‌ها و اثبات چالش بین چت متنی و صوتی به اشتراک گذاشته شوند. */
        fun get(context: Context): DuckAiClient =
            instance ?: synchronized(this) {
                instance ?: DuckAiClient(context.applicationContext).also { instance = it }
            }
    }

    private val appContext = context.applicationContext
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                synchronized(cookieStore) {
                    cookieStore.getOrPut(url.host) { mutableListOf() }.apply {
                        removeAll { existing -> cookies.any { it.name == existing.name } }
                        addAll(cookies)
                    }
                }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> =
                synchronized(cookieStore) { cookieStore[url.host]?.toList() ?: emptyList() }
        })
        .build()

    @Volatile
    var feVersion: String = ""
        private set

    @Volatile
    var jsaProof: String = ""
        private set

    // ─────────────────── توابع رمزنگاری و Base64 مطابق duck_cli.py ───────────────────

    private fun b64(data: ByteArray): String = Base64.encodeToString(data, Base64.NO_WRAP)

    private fun sha256B64(text: String): String =
        b64(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)))

    private fun b64Json(jsonObj: JSONObject): String =
        b64(jsonObj.toString().toByteArray(Charsets.UTF_8))

    private fun feSignals(eventName: String = "action", delta: Int = 1200): String {
        val obj = JSONObject().apply {
            put("start", System.currentTimeMillis())
            put("events", JSONArray().put(JSONObject().apply {
                put("name", eventName)
                put("delta", delta)
                put("trusted", true)
            }))
            put("end", 1500)
        }
        return b64Json(obj)
    }

    // ─────────────────── ۱. Handshake & JSA Challenge Solver ───────────────────

    suspend fun rehandshake(): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$BASE_URL/")
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            okHttp.newCall(req).execute().use { resp ->
                val html = resp.body?.string().orEmpty()
                val tag = Regex("""data-version-tag="([^"]*)"""").find(html)?.groupValues?.get(1) ?: "2026.11"
                val sha = Regex("""data-version-sha="([^"]*)"""").find(html)?.groupValues?.get(1) ?: "a8c4e19"
                feVersion = "$tag-$sha"
            }

            val statusReq = Request.Builder()
                .url("$BASE_URL/duckchat/v1/status")
                .header("User-Agent", USER_AGENT)
                .header("x-vqd-accept", "1")
                .header("Cache-Control", "no-store")
                .header("x-fe-version", feVersion)
                .header("Origin", BASE_URL)
                .header("Referer", "$BASE_URL/")
                .build()

            val rawChallengeB64 = okHttp.newCall(statusReq).execute().use { resp ->
                resp.header("x-vqd-hash-1").orEmpty()
            }

            jsaProof = if (rawChallengeB64.isNotEmpty()) {
                val decodedJs = String(Base64.decode(rawChallengeB64, Base64.DEFAULT), Charsets.UTF_8)
                buildProof(solveJsChallengeInWebView(decodedJs))
            } else {
                buildSyntheticProof()
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (feVersion.isEmpty()) feVersion = "2026.11.05-duckai"
            if (jsaProof.isEmpty()) jsaProof = buildSyntheticProof()
            Log.w(TAG, "Handshake failed: ${e.message}")
            false
        }
    }

    private suspend fun solveJsChallengeInWebView(jsSource: String): JSONObject {
        val deferred = CompletableDeferred<JSONObject>()
        var webViewRef: WebView? = null
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            try {
                val webView = WebView(appContext)
                webViewRef = webView
                webView.settings.javaScriptEnabled = true
                webView.settings.userAgentString = USER_AGENT
                webView.addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onSolved(jsonStr: String) {
                        deferred.complete(
                            try {
                                JSONObject(jsonStr)
                            } catch (e: Exception) {
                                createDefaultSolvedJson()
                            }
                        )
                    }
                }, "DuckBridge")

                val wrapperHtml = """
                    <!DOCTYPE html>
                    <html><head><meta charset="utf-8"></head><body>
                    <script>
                    (async function() {
                        try {
                            const res = await eval(${JSONObject.quote(jsSource)});
                            if (res && typeof res === 'object') {
                                DuckBridge.onSolved(JSON.stringify(res));
                            } else {
                                DuckBridge.onSolved('{}');
                            }
                        } catch (e) {
                            DuckBridge.onSolved(JSON.stringify({__error: String(e)}));
                        }
                    })();
                    </script>
                    </body></html>
                """.trimIndent()
                webView.loadDataWithBaseURL(BASE_URL, wrapperHtml, "text/html", "utf-8", null)
            } catch (e: Exception) {
                deferred.complete(createDefaultSolvedJson())
            }
        }
        val result = withTimeoutOrNull(4500L) { deferred.await() } ?: createDefaultSolvedJson()
        // آزادسازی WebView تا نشت حافظه رخ ندهد
        mainHandler.post {
            try {
                webViewRef?.destroy()
            } catch (_: Exception) {
            }
        }
        return result
    }

    private fun createDefaultSolvedJson(): JSONObject = JSONObject().apply {
        put("server_hashes", JSONArray().put("vqd_srv_${System.currentTimeMillis() / 1000}"))
        put("client_hashes", JSONArray().put(USER_AGENT).put("dom_proof_2026"))
        put("signals", JSONObject())
        put("meta", JSONObject().put("v", "2"))
    }

    /** مطابق build_proof در duck_cli.py: client_hashes = [sha256_b64(x) for x in solved["client_hashes"]] */
    private fun buildProof(solved: JSONObject): String {
        val proof = JSONObject(solved.toString())
        val rawClients = solved.optJSONArray("client_hashes") ?: JSONArray().put(USER_AGENT)
        val hashedClients = JSONArray()
        for (i in 0 until rawClients.length()) {
            hashedClients.put(sha256B64(rawClients.optString(i, USER_AGENT)))
        }
        proof.put("client_hashes", hashedClients)
        if (!proof.has("server_hashes")) {
            proof.put("server_hashes", JSONArray().put(sha256B64("duckai_server_seed")))
        }
        val meta = proof.optJSONObject("meta") ?: JSONObject()
        meta.put("origin", BASE_URL)
        meta.put(
            "stack",
            "Error\nat l (https://duck.ai/dist/duckai-dist/entry.duckai.js:2:2129171)\nat async https://duck.ai/dist/duckai-dist/entry.duckai.js:2:1859809"
        )
        meta.put("duration", "58")
        proof.put("meta", meta)
        return b64Json(proof)
    }

    private fun buildSyntheticProof(): String = buildProof(createDefaultSolvedJson())

    // ─────────────────── ۲. استریم چت متنی (/duckchat/v1/chat) ───────────────────

    /**
     * @param historyMessages جفت‌های (role, text) با role برابر "user" یا "assistant"
     */
    fun streamChat(
        prompt: String,
        model: String = DEFAULT_MODEL,
        historyMessages: List<Pair<String, String>> = emptyList()
    ): Flow<DuckStreamEvent> = flow {
        if (feVersion.isEmpty() || jsaProof.isEmpty()) {
            emit(DuckStreamEvent.HandshakeLog("Handshake", "در حال دریافت x-fe-version و حل چالش x-vqd-hash-1..."))
            rehandshake()
        }

        val messagesArray = JSONArray()
        for ((role, contentText) in historyMessages.takeLast(10)) {
            if (contentText.isBlank()) continue
            val msgObj = JSONObject().put("role", role)
            if (role == "user") {
                msgObj.put("content", JSONArray().put(JSONObject().apply {
                    put("type", "text")
                    put("text", contentText)
                }))
            } else {
                msgObj.put("content", contentText)
            }
            messagesArray.put(msgObj)
        }
        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", JSONArray().put(JSONObject().apply {
                put("type", "text")
                put("text", prompt)
            }))
        })

        val bodyJson = JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
            put("canUseTools", true)
            put("reasoningEffort", "none")
            put("canUseApproxLocation", JSONObject.NULL)
            put("canDelegateImageGeneration", JSONObject.NULL)
            put("canShowGreeting", false)
        }

        var lastError = "پاسخی از Duck.ai دریافت نشد"
        var lastCode: Int? = null
        for (attempt in 0 until 2) {
            try {
                if (attempt > 0) rehandshake()

                val req = Request.Builder()
                    .url("$BASE_URL/duckchat/v1/chat")
                    .header("User-Agent", USER_AGENT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header("Origin", BASE_URL)
                    .header("Referer", "$BASE_URL/")
                    .header("x-fe-version", feVersion)
                    .header("x-fe-signals", feSignals("action", 1200))
                    .header("X-Vqd-Hash-1", jsaProof)
                    .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                var receivedAny = false
                okHttp.newCall(req).execute().use { resp ->
                    val body = resp.body
                    if (!resp.isSuccessful || body == null) {
                        lastCode = resp.code
                        lastError = "Duck.ai HTTP ${resp.code}"
                        return@use
                    }
                    val reader = body.charStream().buffered()
                    var line: String? = reader.readLine()
                    while (line != null) {
                        if (line.startsWith("data: ")) {
                            val payload = line.substring(6).trim()
                            if (payload.isNotEmpty() && !payload.startsWith("[")) {
                                val ev = JSONObject(payload)
                                if (ev.optString("action") == "error") {
                                    throw RuntimeException("Server error: ${ev.optString("type")}")
                                }
                                val msg = ev.optString("message", "")
                                if (msg.isNotEmpty()) {
                                    receivedAny = true
                                    emit(DuckStreamEvent.TextDelta(msg))
                                }
                                if (ev.optString("role") == "tool-invocation") {
                                    emit(
                                        DuckStreamEvent.ToolCall(
                                            toolName = ev.optString("toolName", "WebSearch"),
                                            state = ev.optString("state", "running")
                                        )
                                    )
                                }
                            }
                        }
                        line = reader.readLine()
                    }
                }
                if (receivedAny) return@flow
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e.message ?: lastError
                Log.w(TAG, "Chat attempt ${attempt + 1} failed: $lastError")
            }
        }
        emit(DuckStreamEvent.Error(lastError, lastCode))
    }.flowOn(Dispatchers.IO)

    /** پاسخ کامل (غیر استریمی) برای مصرف در AiRepository تراز. */
    suspend fun complete(
        prompt: String,
        model: String = DEFAULT_MODEL,
        historyMessages: List<Pair<String, String>> = emptyList()
    ): Result<String> {
        val builder = StringBuilder()
        var error: String? = null
        streamChat(prompt, model, historyMessages).collect { ev ->
            when (ev) {
                is DuckStreamEvent.TextDelta -> builder.append(ev.delta)
                is DuckStreamEvent.Error -> error = ev.message
                else -> Unit
            }
        }
        val text = builder.toString().trim()
        return if (text.isNotEmpty()) Result.success(text)
        else Result.failure(Exception(error ?: "پاسخ خالی از Duck.ai"))
    }

    // ─────────────────── ۳. دیکته صوتی (/duckchat/v1/dictation) ───────────────────

    suspend fun transcribeAudioBytes(audioBytes: ByteArray, model: String = DEFAULT_MODEL): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (feVersion.isEmpty() || jsaProof.isEmpty()) rehandshake()
                val req = Request.Builder()
                    .url("$BASE_URL/duckchat/v1/dictation")
                    .header("User-Agent", USER_AGENT)
                    .header("Content-Type", "audio/webm")
                    .header("x-duckai-chat-model", model)
                    .header("x-fe-version", feVersion)
                    .header("x-vqd-hash-1", jsaProof)
                    .header("x-fe-signals", feSignals("dictation", 900))
                    .header("Origin", BASE_URL)
                    .header("Referer", "$BASE_URL/")
                    .post(audioBytes.toRequestBody("audio/webm".toMediaType()))
                    .build()

                okHttp.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val json = JSONObject(resp.body?.string().orEmpty())
                        if (json.optString("action") == "success") {
                            val text = json.optString("text", "")
                            if (text.isNotBlank()) return@withContext Result.success(text)
                        }
                    } else {
                        Log.w(TAG, "Dictation HTTP ${resp.code}")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Dictation failed: ${e.message}")
            }
            Result.failure(Exception("تبدیل صدا به متن ناموفق بود"))
        }

    // ─────────────────── ۴. ویس زنده WebRTC (مطابق LiveVoiceSession در duck_voice.py) ───────────────────

    private fun voiceSignals(): String = b64Json(JSONObject().apply {
        put("start", System.currentTimeMillis())
        put("events", JSONArray().put(JSONObject().apply {
            put("name", "startVoiceSession_free")
            put("delta", 119)
            put("trusted", true)
        }))
        put("end", 1500)
    })

    /**
     * GET /duckchat/v1/ice-servers → آرایه iceServers به فرمت RTCPeerConnection مرورگر
     * (urls / username / credential)
     */
    suspend fun fetchIceServers(journeyId: String): Result<JSONArray> = withContext(Dispatchers.IO) {
        try {
            if (feVersion.isEmpty() || jsaProof.isEmpty()) rehandshake()
            val req = Request.Builder()
                .url("$BASE_URL/duckchat/v1/ice-servers")
                .header("User-Agent", USER_AGENT)
                .header("x-fe-version", feVersion)
                .header("x-ddg-journey-id", journeyId)
                .header("x-fe-signals", feSignals("startVoiceSession_free", 119))
                .header("Referer", "$BASE_URL/")
                .build()
            okHttp.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("دریافت ice-servers ناموفق (HTTP ${resp.code})"))
                }
                val servers = JSONObject(resp.body?.string() ?: "{}").optJSONArray("iceServers") ?: JSONArray()
                Result.success(servers)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception("دریافت ice-servers ناموفق: ${e.message}"))
        }
    }

    /**
     * POST /duckchat/v1/session  body: {"sdp": <offer>, "session": {"voice": "voice-1"}}
     * پاسخ: answer SDP. خطای 418/500 گذراست؛ مثل duck_voice.py با چالش تازه تا ۵ بار تکرار می‌شود.
     */
    suspend fun postVoiceSession(offerSdp: String, voice: String, journeyId: String): Result<String> =
        withContext(Dispatchers.IO) {
            if (feVersion.isEmpty() || jsaProof.isEmpty()) rehandshake()
            val body = JSONObject().apply {
                put("sdp", offerSdp)
                put("session", JSONObject().put("voice", voice))
            }.toString()
            var lastError = "شروع جلسه صوتی ناموفق بود"
            for (attempt in 0 until 5) {
                if (attempt > 0) {
                    kotlinx.coroutines.delay(2000L + attempt * 2000L)
                    rehandshake()
                }
                try {
                    val req = Request.Builder()
                        .url("$BASE_URL/duckchat/v1/session")
                        .header("User-Agent", USER_AGENT)
                        .header("Content-Type", "application/json")
                        .header("Origin", BASE_URL)
                        .header("Referer", "$BASE_URL/")
                        .header("x-fe-version", feVersion)
                        .header("x-vqd-hash-1", jsaProof)
                        .header("x-fe-signals", voiceSignals())
                        .header("x-ddg-journey-id", journeyId)
                        .post(body.toRequestBody("application/json".toMediaType()))
                        .build()
                    okHttp.newCall(req).execute().use { resp ->
                        val text = resp.body?.string().orEmpty()
                        if (resp.isSuccessful && text.contains("v=0")) {
                            return@withContext Result.success(text)
                        }
                        lastError = "شروع جلسه صوتی ناموفق (HTTP ${resp.code})"
                        Log.w(TAG, "Voice session attempt ${attempt + 1}: HTTP ${resp.code}")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = "شروع جلسه صوتی ناموفق: ${e.message}"
                }
            }
            Result.failure(Exception(lastError))
        }
}
