package com.example.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore

object SecurePrefs {
    private const val TAG = "SecurePrefs"
    private const val PREFS_FILE_NAME = "taraz_secure_prefs"
    private const val LEGACY_PREFS_NAME = "fidar_prefs"
    private const val KEY_MIGRATED_V1 = "migrated_v1"

    private val SENSITIVE_KEYS = listOf(
        "session_token",
        "user_name",
        "user_phone",
        "user_email"
    )

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    @Synchronized
    fun get(context: Context): SharedPreferences {
        cachedPrefs?.let { return it }

        val appContext = context.applicationContext
        val prefs = try {
            createEncryptedPrefs(appContext)
        } catch (e: GeneralSecurityException) {
            Log.e(TAG, "Keystore corrupted or GeneralSecurityException encountered. Re-creating prefs file.", e)
            discardBrokenStore(appContext)
            // Retry once as mandated
            createEncryptedPrefs(appContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize EncryptedSharedPreferences", e)
            discardBrokenStore(appContext)
            createEncryptedPrefs(appContext)
        }

        migrateFromLegacyIfNeeded(appContext, prefs)

        cachedPrefs = prefs
        return prefs
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Throws away both halves of a broken store. Deleting the preferences
     * file alone is not enough: if the master key in the AndroidKeyStore is
     * the damaged part — which is what happens after a fingerprint reset or
     * a botched restore — the retry rebuilds the file with the same unusable
     * key and fails again for the same reason.
     */
    private fun discardBrokenStore(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.deleteSharedPreferences(PREFS_FILE_NAME)
            } else {
                val prefsFile = File(context.filesDir.parent, "shared_prefs/$PREFS_FILE_NAME.xml")
                if (prefsFile.exists()) {
                    prefsFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting encrypted shared prefs file", e)
        }
        try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting master key entry", e)
        }
    }

    /**
     * Moves the four identity keys out of the world-readable-by-root plain
     * preferences and into the encrypted store, exactly once.
     *
     * Order matters and both writes are synchronous. With `apply()` the write
     * is queued, so a process death between the two could leave the values
     * erased from the legacy file but never landed in the encrypted one —
     * and with `migrated_v1` already set, nothing would ever retry. The user
     * would silently find themselves logged out. `commit()` on the secure
     * side first, and the legacy keys are cleared only once it reports
     * success.
     */
    internal fun migrateFromLegacyIfNeeded(context: Context, securePrefs: SharedPreferences) {
        if (securePrefs.getBoolean(KEY_MIGRATED_V1, false)) return

        val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val secureEditor = securePrefs.edit()
        val migratedKeys = mutableListOf<String>()

        for (key in SENSITIVE_KEYS) {
            if (!legacyPrefs.contains(key)) continue
            val value = legacyPrefs.getString(key, null)
            if (value != null) {
                secureEditor.putString(key, value)
            }
            migratedKeys += key
        }

        secureEditor.putBoolean(KEY_MIGRATED_V1, true)
        if (!secureEditor.commit()) {
            // Nothing was persisted securely, so the legacy copy stays put and
            // the next launch tries again rather than losing the session.
            Log.e(TAG, "Secure write failed; leaving credentials in $LEGACY_PREFS_NAME for a later retry")
            return
        }

        if (migratedKeys.isNotEmpty()) {
            val legacyEditor = legacyPrefs.edit()
            migratedKeys.forEach { legacyEditor.remove(it) }
            legacyEditor.apply()
            Log.i(TAG, "Migrated ${migratedKeys.size} credential(s) out of $LEGACY_PREFS_NAME")
        }
    }

    // Visible for testing to reset cached instance
    internal fun resetCacheForTesting() {
        cachedPrefs = null
    }
}
