package com.example.data

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Backups in the user's own Google Drive through the Drive REST API, after signing in with their
 * Google account. Only the `drive.file` scope is asked for: Taraz sees only the files it made
 * itself (a «Taraz» folder holding dated, encrypted backups), never the rest of the user's Drive.
 *
 * Sign-in needs Google Play services on the phone and the app registered in Google Cloud (an
 * Android OAuth client for this package and signing key, with the Drive API turned on). Without
 * either, the file-picker way in [AutoBackup] still works.
 */
object DriveApi {
    const val SCOPE = "https://www.googleapis.com/auth/drive.file"
    private const val PREFS = "fidar_prefs"
    private const val FOLDER = "Taraz"
    /** Dated backups kept in Drive; older ones are deleted. */
    private const val KEEP = 10

    data class Status(val connected: Boolean, val email: String?, val lastAt: Long, val lastSize: Long, val error: String?)
    data class RemoteFile(val id: String, val name: String, val modified: Long, val size: Long)

    class AuthNeeded : Exception("برای پشتیبان درایو دوباره با حساب گوگل وارد شوید.")

    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build()
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun request(): AuthorizationRequest = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    fun status(c: Context): Status {
        val p = prefs(c)
        return Status(
            connected = p.getBoolean("drive_api_connected", false),
            email = p.getString("drive_api_email", null),
            lastAt = p.getLong("drive_api_last", 0L),
            lastSize = p.getLong("drive_api_last_size", 0L),
            error = p.getString("drive_api_error", null)
        )
    }

    /** True when Google Play services can run sign-in on this phone. */
    fun playServicesAvailable(c: Context): Boolean = runCatching {
        com.google.android.gms.common.GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(c) ==
            com.google.android.gms.common.ConnectionResult.SUCCESS
    }.getOrDefault(false)

    /**
     * A fresh access token without showing anything, for a user who has already allowed access
     * (background backups). Null when the user must sign in again.
     */
    fun tokenSilently(c: Context): String? = try {
        val r = Tasks.await(Identity.getAuthorizationClient(c).authorize(request()), 30, TimeUnit.SECONDS)
        if (r.hasResolution()) null else r.accessToken
    } catch (e: Exception) { null }

    /** Signed in: remembers the account (its e-mail, read from Drive) for the screen. */
    suspend fun connect(c: Context, token: String) = withContext(Dispatchers.IO) {
        val email = runCatching {
            JSONObject(get(token, "https://www.googleapis.com/drive/v3/about?fields=user(emailAddress,displayName)"))
                .getJSONObject("user").optString("emailAddress").takeIf { it.isNotBlank() }
        }.getOrNull()
        prefs(c).edit().putBoolean("drive_api_connected", true).putString("drive_api_email", email).remove("drive_api_error").apply()
    }

    /** Signs out: the token is revoked at Google (best effort) and nothing is kept here. Backups stay in Drive. */
    suspend fun disconnect(c: Context, token: String?) = withContext(Dispatchers.IO) {
        if (token != null) runCatching {
            client.newCall(Request.Builder().url("https://oauth2.googleapis.com/revoke?token=" + URLEncoder.encode(token, "UTF-8"))
                .post(ByteArray(0).toRequestBody(null)).build()).execute().close()
        }
        prefs(c).edit().remove("drive_api_connected").remove("drive_api_email").remove("drive_api_folder")
            .remove("drive_api_last").remove("drive_api_last_size").remove("drive_api_error").apply()
    }

    // ─── REST ───

    private class HttpError(val code: Int, msg: String) : java.io.IOException(msg)

    private fun send(token: String, b: Request.Builder): ByteArray {
        client.newCall(b.header("Authorization", "Bearer $token").build()).execute().use { r ->
            val body = r.body?.bytes() ?: ByteArray(0)
            if (!r.isSuccessful) {
                val why = runCatching { JSONObject(String(body)).getJSONObject("error").optString("message") }.getOrNull()
                throw HttpError(r.code, "HTTP ${r.code}" + (why?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""))
            }
            return body
        }
    }

    private fun get(token: String, url: String): String = String(send(token, Request.Builder().url(url)))

    private fun q(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun parseTime(s: String): Long = runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .parse(s.take(19))!!.time
    }.getOrDefault(0L)

    /** The «Taraz» folder Taraz made in Drive, made now if missing. */
    private fun folder(c: Context, token: String): String {
        prefs(c).getString("drive_api_folder", null)?.let { id ->
            // هنوز هست و در سطل زباله نیست؟
            val ok = runCatching { JSONObject(get(token, "https://www.googleapis.com/drive/v3/files/$id?fields=id,trashed")).optBoolean("trashed").not() }.getOrDefault(false)
            if (ok) return id
        }
        val found = JSONObject(get(token, "https://www.googleapis.com/drive/v3/files?spaces=drive&fields=files(id)&q=" +
            q("mimeType='application/vnd.google-apps.folder' and name='$FOLDER' and trashed=false"))).getJSONArray("files")
        val id = if (found.length() > 0) found.getJSONObject(0).getString("id") else {
            val meta = JSONObject().put("name", FOLDER).put("mimeType", "application/vnd.google-apps.folder").toString()
            JSONObject(String(send(token, Request.Builder().url("https://www.googleapis.com/drive/v3/files?fields=id")
                .post(meta.toRequestBody("application/json; charset=UTF-8".toMediaType()))))).getString("id")
        }
        prefs(c).edit().putString("drive_api_folder", id).apply()
        return id
    }

    /** Backups in the «Taraz» folder, newest first. */
    suspend fun list(c: Context, token: String): List<RemoteFile> = withContext(Dispatchers.IO) {
        val id = folder(c, token)
        val arr = JSONObject(get(token, "https://www.googleapis.com/drive/v3/files?spaces=drive&pageSize=50&orderBy=modifiedTime%20desc" +
            "&fields=files(id,name,modifiedTime,size)&q=" + q("'$id' in parents and trashed=false"))).getJSONArray("files")
        (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            RemoteFile(it.getString("id"), it.optString("name"), parseTime(it.optString("modifiedTime")), it.optString("size").toLongOrNull() ?: 0L)
        }
    }

    /** Uploads [bytes] as a new dated backup, then keeps only the newest [KEEP]. */
    suspend fun upload(c: Context, token: String, bytes: ByteArray): RemoteFile = withContext(Dispatchers.IO) {
        try {
            val parent = folder(c, token)
            val d = com.example.utils.JalaliDate.today()
            val cal = java.util.Calendar.getInstance()
            val name = "taraz-backup-%04d-%02d-%02d-%02d%02d.taraz".format(java.util.Locale.US, d.year, d.month, d.day,
                cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
            val meta = JSONObject().put("name", name).put("mimeType", "application/octet-stream")
                .put("parents", org.json.JSONArray().put(parent)).toString()
            val body = MultipartBody.Builder().setType("multipart/related".toMediaType())
                .addPart(meta.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .addPart(bytes.toRequestBody("application/octet-stream".toMediaType()))
                .build()
            val o = JSONObject(String(send(token, Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,modifiedTime,size").post(body))))
            runCatching { list(c, token).drop(KEEP).forEach { f -> send(token, Request.Builder().url("https://www.googleapis.com/drive/v3/files/${f.id}").delete()) } }
            prefs(c).edit().putLong("drive_api_last", System.currentTimeMillis()).putLong("drive_api_last_size", bytes.size.toLong())
                .remove("drive_api_error").apply()
            RemoteFile(o.getString("id"), o.optString("name"), parseTime(o.optString("modifiedTime")), bytes.size.toLong())
        } catch (e: HttpError) {
            if (e.code == 401) throw AuthNeeded()
            prefs(c).edit().putString("drive_api_error", "بارگذاری در درایو انجام نشد: ${e.message}").apply()
            throw e
        }
    }

    suspend fun download(token: String, id: String): ByteArray = withContext(Dispatchers.IO) {
        try { send(token, Request.Builder().url("https://www.googleapis.com/drive/v3/files/$id?alt=media")) }
        catch (e: HttpError) { if (e.code == 401) throw AuthNeeded() else throw e }
    }

    /** Background backup: a silent token and an upload. Returns an error message, or null. */
    suspend fun backupInBackground(c: Context, bytes: ByteArray): String? {
        if (!status(c).connected) return null
        val token = withContext(Dispatchers.IO) { tokenSilently(c) } ?: run {
            val msg = "برای پشتیبان درایو دوباره با حساب گوگل وارد شوید."
            prefs(c).edit().putString("drive_api_error", msg).apply()
            return msg
        }
        return try { upload(c, token, bytes); null } catch (e: Exception) { e.message ?: "بارگذاری در درایو انجام نشد." }
    }
}
