package com.example.services

import android.content.Context
import android.net.Uri

object ApiConfig {
    const val ALLOWED_HOST = "dezhcode.pyho.ir"
    const val DEFAULT_BASE_URL = "https://dezhcode.pyho.ir/taraz"
    
    private var activeBaseUrl: String = DEFAULT_BASE_URL

    fun initialize(context: Context) {
        activeBaseUrl = DEFAULT_BASE_URL
    }

    fun getBaseUrl(): String {
        return activeBaseUrl
    }

    fun setBaseUrl(context: Context, url: String) {
        val sanitized = sanitizeUrl(url)
        activeBaseUrl = if (isAllowed(sanitized)) sanitized else DEFAULT_BASE_URL
    }

    private fun isAllowed(url: String): Boolean {
        return try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: ""
            uri.scheme?.equals("https", ignoreCase = true) == true && host == ALLOWED_HOST
        } catch (_: Exception) {
            false
        }
    }

    private fun sanitizeUrl(url: String): String {
        var clean = url.trim()
        clean = clean.replace(Regex("/c/api/chat/?$", RegexOption.IGNORE_CASE), "")
        clean = clean.removeSuffix("/")
        if (clean.isNotEmpty() && clean.startsWith("http://", ignoreCase = true)) {
            clean = "https://" + clean.substring("http://".length)
        } else if (clean.isNotEmpty() && !clean.startsWith("https://", ignoreCase = true)) {
            clean = "https://$clean"
        }
        if (clean.contains("dezhcode.pyho.ir", ignoreCase = true) && !clean.endsWith("/taraz", ignoreCase = true)) {
            clean = "$clean/taraz"
        }
        return if (clean.isNotEmpty()) clean else DEFAULT_BASE_URL
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
