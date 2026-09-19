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
            deleteEncryptedPrefsFile(appContext)
            // Retry once as mandated
            createEncryptedPrefs(appContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize EncryptedSharedPreferences", e)
            deleteEncryptedPrefsFile(appContext)
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

    private fun deleteEncryptedPrefsFile(context: Context) {
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
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting master key from AndroidKeyStore", e)
        }
    }

    internal fun migrateFromLegacyIfNeeded(context: Context, securePrefs: SharedPreferences) {
        if (!securePrefs.getBoolean(KEY_MIGRATED_V1, false)) {
            val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            val secureEditor = securePrefs.edit()
            val migratedKeys = mutableListOf<String>()

            for (key in SENSITIVE_KEYS) {
                if (legacyPrefs.contains(key)) {
                    val value = legacyPrefs.getString(key, null)
                    if (value != null) {
                        secureEditor.putString(key, value)
                    }
                    migratedKeys.add(key)
                }
            }

            secureEditor.putBoolean(KEY_MIGRATED_V1, true)
            val commitSuccess = secureEditor.commit()
            if (!commitSuccess) {
                Log.e(TAG, "Failed to commit migrated credentials to securePrefs. Leaving legacy prefs intact.")
                return
            }

            if (migratedKeys.isNotEmpty()) {
                val legacyEditor = legacyPrefs.edit()
                for (key in migratedKeys) {
                    legacyEditor.remove(key)
                }
                legacyEditor.apply()
                Log.i(TAG, "Successfully migrated sensitive credentials from fidar_prefs to taraz_secure_prefs")
            }
        }
    }

    // Visible for testing to reset cached instance
    internal fun resetCacheForTesting() {
        cachedPrefs = null
    }
}
