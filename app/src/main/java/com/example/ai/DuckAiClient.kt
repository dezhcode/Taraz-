package com.example.ai

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    // ─────────────────── ۱. Handshake & JSA Challenge (مطابق DuckAI.bootstrap/fetch_challenge/solve در duck_cli.py) ───────────────────

    class DuckAiException(message: String, val status: Int? = null) : Exception(message)

    private val handshakeMutex = Mutex()

    /** WebView ثابت برای حل چالش؛ ساختن WebView تازه در هر بار، روی گوشی کند است و از مهلت می‌گذشت */
    @Volatile
    private var solverWebView: WebView? = null
    @Volatile
    private var pendingSolve: CompletableDeferred<JSONObject>? = null

    /** GET / → x-fe-version (مطابق bootstrap) */
    private fun bootstrap() {
        val req = Request.Builder()
            .url("$BASE_URL/")
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        okHttp.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw DuckAiException("duck.ai در دسترس نیست (HTTP ${resp.code})", resp.code)
            val html = resp.body?.string().orEmpty()
            val tag = Regex("""data-version-tag="([^"]*)"""").find(html)?.groupValues?.get(1) ?: "dev"
            val sha = Regex("""data-version-sha="([^"]*)"""").find(html)?.groupValues?.get(1) ?: "hash"
            feVersion = "$tag-$sha"
        }
    }

    /** GET /duckchat/v1/status با x-vqd-accept: 1 → متن JS چالش (مطابق fetch_challenge) */
    private fun fetchChallenge(): String {
        val req = Request.Builder()
            .url("$BASE_URL/duckchat/v1/status")
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("x-vqd-accept", "1")
            .header("Cache-Control", "no-store")
            .header("x-fe-version", feVersion)
            .header("Origin", BASE_URL)
            .header("Referer", "$BASE_URL/")
            .build()
        okHttp.newCall(req).execute().use { resp ->
            val raw = resp.header("x-vqd-hash-1")
            if (raw.isNullOrBlank()) {
                throw DuckAiException("چالشی دریافت نشد (HTTP ${resp.code})", resp.code)
            }
            val padded = raw + "=".repeat((4 - raw.length % 4) % 4)
            return String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
        }
    }

    /**
     * چالش تازه (مطابق rehandshake): bootstrap → fetch_challenge → solve → build_proof.
     * اگر هر مرحله شکست بخورد استثنا پرتاب می‌شود؛ هرگز اثبات ساختگی ساخته نمی‌شود،
     * چون سرور آن را رد می‌کند (۴۱۸) و فقط خطا را پنهان می‌کند.
     */
    suspend fun rehandshakeOrThrow() = handshakeMutex.withLock {
        withContext(Dispatchers.IO) {
            bootstrap()
            val js = fetchChallenge()
            val solved = solveInWebView(js)
            if (solved.has("__error")) {
                throw DuckAiException("خطای داخل چالش: ${solved.optString("__error").take(200)}")
            }
            if (solved.optJSONArray("client_hashes") == null) {
                throw DuckAiException("پاسخ چالش ناقص بود")
            }
            jsaProof = buildProof(solved)
        }
    }

    /** نسخهٔ بی‌استثنا برای گرم‌کردن اولیه */
    suspend fun rehandshake(): Boolean = try {
        rehandshakeOrThrow()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Handshake failed: ${e.message}")
        false
    }

    /** معادل اجرای چالش با Node + jsdom: داخل یک WebView با مبدأ https://duck.ai */
    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private suspend fun solveInWebView(jsSource: String): JSONObject {
        val deferred = CompletableDeferred<JSONObject>()
        pendingSolve = deferred
        withContext(Dispatchers.Main) {
            val wv = solverWebView ?: WebView(appContext).also { w ->
                w.settings.javaScriptEnabled = true
                w.settings.domStorageEnabled = true
                w.settings.userAgentString = USER_AGENT
                w.addJavascriptInterface(SolverBridge(), "DuckBridge")
                solverWebView = w
            }
            val html = """
                <!DOCTYPE html>
                <html><head><meta charset="utf-8"></head><body>
                <script>
                (async function() {
                    try {
                        const res = await eval(${JSONObject.quote(jsSource)});
                        DuckBridge.onSolved(JSON.stringify(res && typeof res === 'object' ? res : {__error: 'empty result'}));
                    } catch (e) {
                        DuckBridge.onSolved(JSON.stringify({__error: String((e && e.stack) || e)}));
                    }
                })();
                </script>
                </body></html>
            """.trimIndent()
            wv.loadDataWithBaseURL("$BASE_URL/", html, "text/html", "utf-8", null)
        }
        // duck_cli.py مهلت ۹۰ ثانیه می‌دهد؛ اینجا ۲۵ ثانیه کافی است (اولین راه‌اندازی WebView کند است)
        return withTimeoutOrNull(25_000L) { deferred.await() }
            ?: throw DuckAiException("حل چالش بیش از حد طول کشید")
    }

    private inner class SolverBridge {
        @JavascriptInterface
        fun onSolved(jsonStr: String) {
            val result = try {
                JSONObject(jsonStr)
            } catch (e: Exception) {
                JSONObject().put("__error", "invalid JSON from challenge")
            }
            pendingSolve?.complete(result)
        }
    }

    /** مطابق build_proof در duck_cli.py: client_hashes = [sha256_b64(x) for x in solved["client_hashes"]] */
    private fun buildProof(solved: JSONObject): String {
        val proof = JSONObject(solved.toString())
        val rawClients = solved.getJSONArray("client_hashes")
        val hashedClients = JSONArray()
        for (i in 0 until rawClients.length()) {
            hashedClients.put(sha256B64(rawClients.optString(i)))
        }
        proof.put("client_hashes", hashedClients)
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

    // ─────────────────── ۲. چت (مطابق DuckAI.chat و stream_events در duck_cli.py) ───────────────────

    /**
     * @param historyMessages جفت‌های (role, text) با role برابر "user" یا "assistant"
     *   (مثل تاریخچه‌ای که chat_turn نگه می‌دارد)
     */
    fun streamChat(
        prompt: String,
        model: String = DEFAULT_MODEL,
        historyMessages: List<Pair<String, String>> = emptyList(),
        retries: Int = 4
    ): Flow<DuckStreamEvent> = flow {
        val messagesArray = JSONArray()
        for ((role, contentText) in historyMessages) {
            if (contentText.isBlank()) continue
            val msgObj = JSONObject().put("role", role)
            if (role == "user") {
                msgObj.put("content", JSONArray().put(JSONObject().put("type", "text").put("text", contentText)))
            } else {
                msgObj.put("content", contentText)
            }
            messagesArray.put(msgObj)
        }
        messagesArray.put(
            JSONObject().put("role", "user")
                .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", prompt)))
        )
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
        var lastStatus: Int? = null
        var response: okhttp3.Response? = null

        // ── تلاش‌ها: ۴۱۸ (امتیاز ضدربات) با چالش تازه معمولاً رفع می‌شود
        for (attempt in 0 until retries) {
            try {
                if (attempt > 0 || feVersion.isEmpty() || jsaProof.isEmpty()) {
                    emit(DuckStreamEvent.HandshakeLog("Handshake", "در حال حل چالش امنیتی Duck.ai…"))
                    rehandshakeOrThrow()
                }
                val req = Request.Builder()
                    .url("$BASE_URL/duckchat/v1/chat")
                    .header("User-Agent", USER_AGENT)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header("Origin", BASE_URL)
                    .header("Referer", "$BASE_URL/")
                    .header("x-fe-version", feVersion)
                    .header("x-fe-signals", feSignals("action", 1200))
                    .header("X-Vqd-Hash-1", jsaProof)
                    .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                val resp = okHttp.newCall(req).execute()
                if (resp.code == 200 && resp.body != null) {
                    response = resp
                    break
                }
                lastStatus = resp.code
                lastError = "HTTP ${resp.code}: ${resp.body?.string().orEmpty().take(200)}"
                resp.close()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e.message ?: lastError
            }
            Log.w(TAG, "Chat attempt ${attempt + 1} failed: $lastError")
            if (attempt < retries - 1) delay(2000L + attempt * 2000L)
        }

        val resp = response
        if (resp == null) {
            emit(DuckStreamEvent.Error("چت پس از $retries تلاش ناموفق بود: $lastError", lastStatus))
            return@flow
        }

        resp.use {
            val reader = it.body!!.charStream().buffered()
            var line: String? = reader.readLine()
            while (line != null) {
                if (line.startsWith("data: ")) {
                    val payload = line.substring(6)
                    // [PING] / [DONE] / [CHAT_TITLE:...]
                    if (payload.isNotEmpty() && !payload.startsWith("[")) {
                        val ev = try { JSONObject(payload) } catch (_: Exception) { null }
                        if (ev != null) {
                            if (ev.optString("action") == "error") {
                                emit(DuckStreamEvent.Error("خطای سرور (${ev.opt("status")}): ${ev.optString("type", "?")}"))
                                return@flow
                            }
                            val msg = ev.opt("message")
                            if (msg is String && msg.isNotEmpty()) {
                                emit(DuckStreamEvent.TextDelta(msg))
                            } else if (ev.optString("role") == "tool-invocation") {
                                emit(DuckStreamEvent.ToolCall(ev.optString("toolName", "WebSearch"), ev.optString("state", "running")))
                            }
                        }
                    }
                }
                line = reader.readLine()
            }
        }
    }.flowOn(Dispatchers.IO)

    /** پاسخ کامل (مطابق ask در duck_cli.py) */
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
        return if (error == null && text.isNotEmpty()) Result.success(text)
        else Result.failure(DuckAiException(error ?: "پاسخ خالی از Duck.ai"))
    }

    // ─────────────────── ۳. دیکته صوتی (مطابق transcribe_bytes در duck_voice.py) ───────────────────

    /** @param webmData فایل webm/opus خام (سرور فقط همین قالب را می‌پذیرد) */
    suspend fun transcribeAudioBytes(webmData: ByteArray, model: String = DEFAULT_MODEL): Result<String> =
        withContext(Dispatchers.IO) {
            var lastErr = ""
            try {
                if (feVersion.isEmpty() || jsaProof.isEmpty()) rehandshakeOrThrow()
                for (attempt in 0 until 3) {
                    val req = Request.Builder()
                        .url("$BASE_URL/duckchat/v1/dictation")
                        .header("User-Agent", USER_AGENT)
                        .header("Content-Type", "audio/webm")
                        .header("x-duckai-chat-model", model)
                        .header("x-fe-version", feVersion)
                        .header("x-vqd-hash-1", jsaProof)
                        .header("x-fe-signals", b64Json(JSONObject().put("start", System.currentTimeMillis()).put("events", JSONArray()).put("end", 1500 + System.currentTimeMillis() % 5000)))
                        .header("Origin", BASE_URL)
                        .header("Referer", "$BASE_URL/")
                        .post(webmData.toRequestBody("audio/webm".toMediaType()))
                        .build()
                    okHttp.newCall(req).execute().use { resp ->
                        val bodyStr = resp.body?.string().orEmpty()
                        if (resp.code == 200) {
                            val json = JSONObject(bodyStr)
                            if (json.optString("action") == "success") {
                                return@withContext Result.success(json.optString("text", ""))
                            }
                            return@withContext Result.failure(DuckAiException("دیکته ناموفق: ${bodyStr.take(200)}"))
                        }
                        lastErr = "HTTP ${resp.code}: ${bodyStr.take(200)}"
                        if (resp.code != 403 && resp.code != 418) {
                            return@withContext Result.failure(DuckAiException("دیکته ناموفق: $lastErr", resp.code))
                        }
                    }
                    rehandshakeOrThrow()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastErr = e.message ?: lastErr
            }
            Log.w(TAG, "Dictation failed: $lastErr")
            Result.failure(DuckAiException("دیکته پس از تلاش ناموفق بود: $lastErr"))
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
