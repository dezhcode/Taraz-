package com.example.services

import android.content.Context

object ApiConfig {
    const val DEFAULT_BASE_URL = "https://dezhcode.pyho.ir/taraz"

    fun initialize(context: Context) {
        val aiPrefs = context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)
        aiPrefs.edit().remove("ai_server_url").apply()

        val legacyPrefs = context.getSharedPreferences("fidar_prefs", Context.MODE_PRIVATE)
        legacyPrefs.edit().remove("ai_server_url").apply()
    }

    fun getBaseUrl(): String {
        return DEFAULT_BASE_URL
    }

    fun getHttpUrl(endpoint: String): String {
        val base = getBaseUrl()
        val suffix = endpoint.removePrefix("/")
        return "$base/$suffix"
    }

    fun getWsUrl(endpoint: String): String {
        val base = getBaseUrl()
        val suffix = endpoint.removePrefix("/")
        
        val wsBase = when {
            base.startsWith("https://", ignoreCase = true) -> base.replaceFirst("https://", "wss://", ignoreCase = true)
            base.startsWith("http://", ignoreCase = true) -> base.replaceFirst("http://", "ws://", ignoreCase = true)
            else -> "wss://$base"
        }
        return "$wsBase/$suffix"
    }
}
