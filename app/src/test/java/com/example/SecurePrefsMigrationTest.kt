package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.services.ApiConfig
import com.example.utils.SecurePrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecurePrefsMigrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("fidar_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("test_secure_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `migrates four sensitive keys to secure prefs and removes them from fidar_prefs`() {
        val legacyPrefs = context.getSharedPreferences("fidar_prefs", Context.MODE_PRIVATE)
        legacyPrefs.edit()
            .putString("session_token", "token_12345")
            .putString("user_name", "علی محمدی")
            .putString("user_phone", "09123456789")
            .putString("user_email", "ali@fidar.app")
            .putBoolean("alerts_enabled", true)
            .putBoolean("user_logged_in", true)
            .commit()

        val securePrefs = context.getSharedPreferences("test_secure_prefs", Context.MODE_PRIVATE)

        SecurePrefs.migrateFromLegacyIfNeeded(context, securePrefs)

        // Sensitive keys migrated to destination
        assertEquals("token_12345", securePrefs.getString("session_token", null))
        assertEquals("علی محمدی", securePrefs.getString("user_name", null))
        assertEquals("09123456789", securePrefs.getString("user_phone", null))
        assertEquals("ali@fidar.app", securePrefs.getString("user_email", null))
        assertTrue(securePrefs.getBoolean("migrated_v1", false))

        // Sensitive keys removed from legacy prefs
        assertFalse(legacyPrefs.contains("session_token"))
        assertFalse(legacyPrefs.contains("user_name"))
        assertFalse(legacyPrefs.contains("user_phone"))
        assertFalse(legacyPrefs.contains("user_email"))

        // Non-sensitive keys remain in legacy prefs
        assertTrue(legacyPrefs.getBoolean("alerts_enabled", false))
        assertTrue(legacyPrefs.getBoolean("user_logged_in", false))
    }

    @Test
    fun `running migration repeatedly does not corrupt data`() {
        val legacyPrefs = context.getSharedPreferences("fidar_prefs", Context.MODE_PRIVATE)
        legacyPrefs.edit()
            .putString("user_name", "سارا")
            .putString("session_token", "sec_token")
            .commit()

        val securePrefs = context.getSharedPreferences("test_secure_prefs", Context.MODE_PRIVATE)

        SecurePrefs.migrateFromLegacyIfNeeded(context, securePrefs)
        assertEquals("سارا", securePrefs.getString("user_name", null))

        // Second run
        SecurePrefs.migrateFromLegacyIfNeeded(context, securePrefs)
        assertEquals("سارا", securePrefs.getString("user_name", null))
        assertEquals("sec_token", securePrefs.getString("session_token", null))
        assertTrue(securePrefs.getBoolean("migrated_v1", false))
    }

    @Test
    fun `fresh installation with no legacy data executes without error`() {
        val securePrefs = context.getSharedPreferences("test_secure_prefs", Context.MODE_PRIVATE)

        SecurePrefs.migrateFromLegacyIfNeeded(context, securePrefs)

        assertTrue(securePrefs.getBoolean("migrated_v1", false))
        assertNull(securePrefs.getString("user_name", null))
        assertNull(securePrefs.getString("session_token", null))
    }

    @Test
    fun `ApiConfig initialize removes ai_server_url from fidar_prefs and ai_prefs`() {
        val legacyPrefs = context.getSharedPreferences("fidar_prefs", Context.MODE_PRIVATE)
        legacyPrefs.edit().putString("ai_server_url", "https://malicious-url.com").commit()

        val aiPrefs = context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)
        aiPrefs.edit().putString("ai_server_url", "https://old-custom-url.com").commit()

        ApiConfig.initialize(context)

        assertFalse(legacyPrefs.contains("ai_server_url"))
        assertFalse(aiPrefs.contains("ai_server_url"))
    }
}
