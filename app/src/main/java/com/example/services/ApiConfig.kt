package com.example.services

import android.content.Context
import java.net.URI

object ApiConfig {
    const val ALLOWED_HOST = "dezhcode.pyho.ir"
    const val ALLOWED_PATH = "/taraz"
    const val DEFAULT_BASE_URL = "https://$ALLOWED_HOST$ALLOWED_PATH"

    /** Keys the app used to persist a user-chosen server address under. */
    private const val LEGACY_PREFS_NAME = "ai_prefs"
    private const val LEGACY_FALLBACK_PREFS_NAME = "fidar_prefs"
    private const val LEGACY_URL_KEY = "ai_server_url"

    private var activeBaseUrl: String = DEFAULT_BASE_URL

    /**
     * The base URL is a constant now, not a preference: an attacker who could
     * change it would redirect every balance, loan figure and chat message to
     * a server of their choosing. Whatever address a previous version stored
     * is dropped here, and the stale keys are wiped so nothing reads them.
     */
    fun initialize(context: Context) {
        activeBaseUrl = DEFAULT_BASE_URL
        clearLegacyStoredUrl(context)
    }

    fun getBaseUrl(): String = activeBaseUrl

    /**
     * Returns true when [url] was accepted. A rejected address changes
     * nothing — the caller keeps talking to whatever it was already using.
     */
    fun setBaseUrl(context: Context, url: String): Boolean {
        val sanitized = sanitizeUrl(url)
        if (!isAllowed(sanitized)) return false
        activeBaseUrl = sanitized
        return true
    }

    /**
     * Host AND path are both checked. Host alone is not enough: the bare
     * `https://dezhcode.pyho.ir` passes a host test but drops the `/taraz`
     * prefix every endpoint hangs off, so every call 404s.
     *
     * The host is compared for equality, never with `contains` — that would
     * accept `dezhcode.pyho.ir.example.com`. [URI] also puts any `user@host`
     * part in its own field, so `https://dezhcode.pyho.ir@evil.com` resolves
     * to the host `evil.com` and is refused.
     */
    internal fun isAllowed(url: String): Boolean {
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return false
        }
        if (!"https".equals(uri.scheme, ignoreCase = true)) return false
        val host = uri.host?.lowercase() ?: return false
        if (host != ALLOWED_HOST) return false
        val path = uri.path.orEmpty().removeSuffix("/")
        return path.equals(ALLOWED_PATH, ignoreCase = true)
    }

    /**
     * Tidies an address into the form [isAllowed] expects. It never makes an
     * address allowed that was not: a foreign host stays foreign.
     */
    internal fun sanitizeUrl(url: String): String {
        var clean = url.trim()
        if (clean.isEmpty()) return ""
        clean = clean.replace(Regex("/c/api/chat/?$", RegexOption.IGNORE_CASE), "")
        clean = clean.removeSuffix("/")
        // Financial data must never travel in cleartext: a bare host gets
        // https, and an explicit http:// address is upgraded, not honoured.
        clean = when {
            clean.startsWith("http://", ignoreCase = true) ->
                "https://" + clean.substring("http://".length)
            clean.startsWith("https://", ignoreCase = true) -> clean
            else -> "https://$clean"
        }
        return clean
    }

    private fun clearLegacyStoredUrl(context: Context) {
        listOf(LEGACY_PREFS_NAME, LEGACY_FALLBACK_PREFS_NAME).forEach { name ->
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            if (prefs.contains(LEGACY_URL_KEY)) {
                prefs.edit().remove(LEGACY_URL_KEY).apply()
            }
        }
    }

    fun getHttpUrl(endpoint: String): String = "${getBaseUrl()}/${endpoint.removePrefix("/")}"

    fun getWsUrl(endpoint: String): String {
        val base = getBaseUrl()
        val suffix = endpoint.removePrefix("/")
        val wsBase = when {
            base.startsWith("https://", ignoreCase = true) ->
                base.replaceFirst("https://", "wss://", ignoreCase = true)
            base.startsWith("http://", ignoreCase = true) ->
                base.replaceFirst("http://", "ws://", ignoreCase = true)
            else -> "wss://$base"
        }
        return "$wsBase/$suffix"
    }
}
