package com.example.services

import android.content.Context

object ApiConfig {
    private const val PREFS_NAME = "ai_prefs"
    private const val KEY_SERVER_URL = "ai_server_url"
    
    // Single source of truth for the default base URL
    const val DEFAULT_BASE_URL = "https://dezhcode.pyho.ir/taraz"
    
    private var activeBaseUrl: String = DEFAULT_BASE_URL

    fun initialize(context: Context) {
        // Fallback to "fidar_prefs" or use the central "ai_prefs"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Also look up in "fidar_prefs" just in case the old value was stored there
        val legacyPrefs = context.getSharedPreferences("fidar_prefs", Context.MODE_PRIVATE)
        val legacyUrl = legacyPrefs.getString("ai_server_url", null)
        
        val savedUrl = prefs.getString(KEY_SERVER_URL, legacyUrl ?: DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        activeBaseUrl = sanitizeUrl(savedUrl)
    }

    fun getBaseUrl(): String {
        return activeBaseUrl
    }

    fun setBaseUrl(context: Context, url: String) {
        val sanitized = sanitizeUrl(url)
        activeBaseUrl = if (sanitized.isNotEmpty()) sanitized else DEFAULT_BASE_URL
        
        // Save to central prefs
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SERVER_URL, activeBaseUrl).apply()
        
        // Keep in sync with legacy "fidar_prefs" for compatibility
        val legacyPrefs = context.getSharedPreferences("fidar_prefs", Context.MODE_PRIVATE)
        legacyPrefs.edit().putString("ai_server_url", activeBaseUrl).apply()
    }

    private fun sanitizeUrl(url: String): String {
        var clean = url.trim()
        clean = clean.replace(Regex("/c/api/chat/?$", RegexOption.IGNORE_CASE), "")
        clean = clean.removeSuffix("/")
        // Financial data must never travel in cleartext: a bare host gets https,
        // and an explicit http:// URL is upgraded rather than honoured.
        if (clean.isNotEmpty() && clean.startsWith("http://", ignoreCase = true)) {
            clean = "https://" + clean.substring("http://".length)
        } else if (clean.isNotEmpty() && !clean.startsWith("https://", ignoreCase = true)) {
            clean = "https://$clean"
        }
        return clean
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
