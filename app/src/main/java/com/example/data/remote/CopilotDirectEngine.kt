package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Direct client for the Copilot AI Native API.
 * Uses the exact same protocol as defined in the FastAPI backend:
 * 1. POST https://copilot.microsoft.com/c/api/start
 * 2. WebSocket wss://copilot.microsoft.com/c/api/chat?api-version=2
 */
object CopilotDirectEngine {
    private const val TAG = "CopilotDirectEngine"
    private const val START_URL = "https://copilot.microsoft.com/c/api/start"
    private const val WS_URL = "wss://copilot.microsoft.com/c/api/chat?api-version=2"
    private const val USER_AGENT = "CopilotNative/30.0.430320002 (Android 9)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun askCopilot(text: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Create Session
            val sessionPayload = JSONObject().apply {
                put("timeZone", "Asia/Tehran")
                put("startNewConversation", true)
                put("teenSupportEnabled", false)
            }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val startRequest = Request.Builder()
                .url(START_URL)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Content-Type", "application/json")
                .addHeader("x-search-uilang", "en-US")
                .post(sessionPayload)
                .build()

            val startResponse = client.newCall(startRequest).execute()
            if (!startResponse.isSuccessful) {
                return@withContext Result.failure(Exception("Copilot session creation failed (HTTP ${startResponse.code})"))
            }

            val responseBody = startResponse.body?.string() ?: ""
            val jsonObj = JSONObject(responseBody)
            val conversationId = jsonObj.optString("currentConversationId", "")
            if (conversationId.isEmpty()) {
                return@withContext Result.failure(Exception("Conversation ID missing in Copilot response"))
            }

            val cookieHeader = startResponse.headers("Set-Cookie")
                .map { it.substringBefore(";") }
                .joinToString("; ")

            // Step 2: Connect via WebSocket and stream response
            val wsResult = withTimeoutOrNull(25_000L) {
                executeWebSocketChat(conversationId, cookieHeader, text)
            }

            if (wsResult != null && wsResult.isNotBlank()) {
                Result.success(wsResult)
            } else {
                Result.failure(Exception("پاسخی از کوپایلوت دریافت نشد یا زمان به پایان رسید"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Direct Copilot call failed: ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    private suspend fun executeWebSocketChat(
        conversationId: String,
        cookieHeader: String,
        text: String
    ): String = suspendCancellableCoroutine { continuation ->
        val wsRequest = Request.Builder()
            .url(WS_URL)
            .addHeader("User-Agent", USER_AGENT)
            .apply {
                if (cookieHeader.isNotBlank()) {
                    addHeader("cookie", cookieHeader)
                }
            }
            .build()

        val answerBuilder = StringBuilder()
        var webSocketRef: WebSocket? = null

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocketRef = webSocket
                try {
                    // Send setOptions
                    val setOptions = JSONObject().apply {
                        put("event", "setOptions")
                        put("supportedCards", JSONArray().apply {
                            put("image")
                            put("video")
                        })
                    }.toString()
                    webSocket.send(setOptions)

                    // Send user prompt
                    val sendEvent = JSONObject().apply {
                        put("event", "send")
                        put("conversationId", conversationId)
                        put("content", JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", text)
                            })
                        })
                    }.toString()
                    webSocket.send(sendEvent)
                } catch (e: Exception) {
                    if (continuation.isActive) {
                        continuation.resumeWith(kotlin.Result.failure(e))
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val data = JSONObject(text)
                    if (data.has("text")) {
                        answerBuilder.append(data.getString("text"))
                    }
                    val event = data.optString("event", "")
                    if (event == "done") {
                        webSocket.close(1000, "Done")
                        if (continuation.isActive) {
                            continuation.resume(answerBuilder.toString())
                        }
                    }
                } catch (_: Exception) {
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (continuation.isActive) {
                    if (answerBuilder.isNotEmpty()) {
                        continuation.resume(answerBuilder.toString())
                    } else {
                        continuation.resumeWith(kotlin.Result.failure(t))
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (continuation.isActive) {
                    continuation.resume(answerBuilder.toString())
                }
            }
        }

        webSocketRef = client.newWebSocket(wsRequest, listener)

        continuation.invokeOnCancellation {
            webSocketRef?.cancel()
        }
    }
}
