package com.example

import android.content.Context
import android.content.SharedPreferences
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

/**
 * The session token and the user's identity used to sit in plain
 * SharedPreferences. These cases cover the one-way move into the encrypted
 * store — above all, that nothing is dropped on the way.
 *
 * The migration is driven through [SecurePrefs.migrateFromLegacyIfNeeded]
 * with an ordinary SharedPreferences standing in for the encrypted one.
 * That is deliberate: EncryptedSharedPreferences needs a real
 * AndroidKeyStore, which the JVM test runtime does not have, and the part
 * worth testing is the copy-then-erase order rather than the cipher. The
 * encryption itself belongs to an instrumentation test on a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecurePrefsMigrationTest {

    private lateinit var context: Context
    private lateinit var secureStub: SharedPreferences

    private val sensitiveKeys = listOf("session_token", "user_name", "user_phone", "user_email")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SecurePrefs.resetCacheForTesting()
        legacy().edit().clear().commit()
        secureStub = context.getSharedPreferences("secure_stub", Context.MODE_PRIVATE)
        secureStub.edit().clear().commit()
    }

    private fun legacy() = context.getSharedPreferences("fidar_prefs", Context.MODE_PRIVATE)

    private fun migrate() = SecurePrefs.migrateFromLegacyIfNeeded(context, secureStub)

    private fun seedLegacyCredentials() {
        legacy().edit()
            .putString("session_token", "09121112233:1758200000:abcdef")
            .putString("user_name", "بنیامین")
            .putString("user_phone", "09121112233")
            .putString("user_email", "09121112233@fidar.app")
            .putBoolean("alerts_enabled", true)
            .putBoolean("user_logged_in", true)
            .commit()
    }

    @Test
    fun `credentials arrive in the secure store intact`() {
        seedLegacyCredentials()

        migrate()

        assertEquals("09121112233:1758200000:abcdef", secureStub.getString("session_token", null))
        assertEquals("بنیامین", secureStub.getString("user_name", null))
        assertEquals("09121112233", secureStub.getString("user_phone", null))
        assertEquals("09121112233@fidar.app", secureStub.getString("user_email", null))
    }

    @Test
    fun `credentials no longer remain in the plain store`() {
        seedLegacyCredentials()

        migrate()

        sensitiveKeys.forEach { key ->
            assertFalse("$key was left behind in fidar_prefs", legacy().contains(key))
            assertNull(legacy().getString(key, null))
        }
    }

    @Test
    fun `flags the reminder workers read are left in place`() {
        seedLegacyCredentials()

        migrate()

        // LoanReminderWorker and OneOffReminderWorker run in another process
        // and read these directly; moving them would silence every reminder.
        assertTrue(legacy().getBoolean("alerts_enabled", false))
        assertTrue(legacy().getBoolean("user_logged_in", false))
    }

    @Test
    fun `running the migration twice does not destroy the credentials`() {
        seedLegacyCredentials()

        migrate()
        migrate()

        assertEquals("09121112233:1758200000:abcdef", secureStub.getString("session_token", null))
        assertEquals("بنیامین", secureStub.getString("user_name", null))
    }

    @Test
    fun `a second run does not resurrect a key the user logged out of`() {
        seedLegacyCredentials()
        migrate()

        secureStub.edit().remove("session_token").commit()
        migrate()

        assertNull(secureStub.getString("session_token", null))
    }

    @Test
    fun `a first install with nothing to migrate is handled`() {
        migrate()

        sensitiveKeys.forEach { key ->
            assertNull(secureStub.getString(key, null))
        }
        assertTrue(secureStub.getBoolean("migrated_v1", false))
    }

    @Test
    fun `a refused server address leaves the active one untouched`() {
        ApiConfig.initialize(context)
        val before = ApiConfig.getBaseUrl()

        assertFalse(ApiConfig.setBaseUrl(context, "https://evil.com/taraz"))

        assertEquals(before, ApiConfig.getBaseUrl())
        assertTrue(ApiConfig.setBaseUrl(context, "https://dezhcode.pyho.ir/taraz"))
    }

    @Test
    fun `initialize wipes any server address a previous version stored`() {
        legacy().edit().putString("ai_server_url", "https://evil.com/taraz").commit()

        ApiConfig.initialize(context)

        assertFalse(legacy().contains("ai_server_url"))
        assertEquals(ApiConfig.DEFAULT_BASE_URL, ApiConfig.getBaseUrl())
    }
}
