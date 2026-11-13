package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.example.utils.JalaliDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Weekly or monthly encrypted backup into a folder the user picked once
 * (Storage Access Framework, so no storage permission). The password is
 * asked once and kept in the encrypted prefs. Only the newest [KEEP] files
 * stay; older automatic ones are deleted.
 */
object AutoBackup {
    private const val PREFS = "fidar_prefs"
    private const val KEEP = 5
    private const val PREFIX = "taraz-auto-"

    data class State(
        val enabled: Boolean,
        val periodDays: Int,
        val folderLabel: String?,
        val hasPassword: Boolean,
        val lastAt: Long,
        val lastSize: Long,
        val lastError: String?,
        /** فایل پشتیبان در گوگل درایو (یا هر جای دیگری که کاربر یک فایل برگزیده)؛ null یعنی وصل نیست */
        val driveName: String? = null,
        val driveIsGoogle: Boolean = false,
        val driveLastAt: Long = 0,
        val driveError: String? = null
    )

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun secure(c: Context) = com.example.utils.SecurePrefs.get(c)

    fun state(c: Context): State {
        val p = prefs(c)
        val tree = p.getString("auto_backup_tree", null)
        return State(
            enabled = p.getBoolean("auto_backup_enabled", false),
            periodDays = p.getInt("auto_backup_period", 7),
            folderLabel = tree?.let { folderLabel(Uri.parse(it)) },
            hasPassword = !secure(c).getString("auto_backup_password", null).isNullOrEmpty(),
            lastAt = p.getLong("auto_backup_last", 0L),
            lastSize = p.getLong("auto_backup_last_size", 0L),
            lastError = p.getString("auto_backup_error", null),
            driveName = p.getString("drive_backup_name", null)?.takeIf { p.getString("drive_backup_uri", null) != null },
            driveIsGoogle = p.getBoolean("drive_backup_google", false),
            driveLastAt = p.getLong("drive_backup_last", 0L),
            driveError = p.getString("drive_backup_error", null)
        )
    }

    // ─── گوگل درایو ───
    // درایو انتخاب «پوشه» را به برنامه‌ها نمی‌دهد، ولی انتخاب «یک فایل» را می‌دهد: کاربر یک بار فایل
    // پشتیبان را در درایو می‌سازد (یا فایل قبلی‌اش را برمی‌گزیند)، تراز اجازهٔ دائمی همان فایل را
    // نگه می‌دارد و هر بار نسخهٔ تازه را روی آن می‌نویسد. درایو خودش آن را بالا می‌برد؛ بی‌اینترنت هم
    // نوشته می‌شود و بعداً همگام می‌شود. کلید API و ورود جدا لازم نیست.

    const val DRIVE_PACKAGE = "com.google.android.apps.docs"

    private fun isGoogleDrive(uri: Uri) = uri.authority?.startsWith(DRIVE_PACKAGE) == true

    /** آیا برنامهٔ گوگل درایو روی گوشی هست (بدون آن، درایو در پنجرهٔ انتخاب فایل دیده نمی‌شود) */
    fun driveAppInstalled(c: Context): Boolean = runCatching {
        c.packageManager.getPackageInfo(DRIVE_PACKAGE, 0); true
    }.getOrDefault(false)

    /** فایل برگزیده را برای پشتیبان‌های بعدی نگه می‌دارد. */
    fun setDriveFile(c: Context, uri: Uri) {
        runCatching {
            c.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }.recoverCatching {
            // بعضی جاها فقط اجازهٔ خواندن ماندگار می‌دهند
            c.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val name = runCatching {
            c.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cur ->
                if (cur.moveToFirst()) cur.getString(0) else null
            }
        }.getOrNull() ?: "taraz-backup.taraz"
        prefs(c).edit()
            .putString("drive_backup_uri", uri.toString())
            .putString("drive_backup_name", name)
            .putBoolean("drive_backup_google", isGoogleDrive(uri))
            .remove("drive_backup_error")
            .apply()
    }

    fun disconnectDrive(c: Context) {
        prefs(c).getString("drive_backup_uri", null)?.let { u ->
            runCatching {
                c.contentResolver.releasePersistableUriPermission(Uri.parse(u), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }
        prefs(c).edit().remove("drive_backup_uri").remove("drive_backup_name").remove("drive_backup_google")
            .remove("drive_backup_last").remove("drive_backup_error").apply()
    }

    /** نسخهٔ تازه را روی فایل درایو می‌نویسد. [password]: رمز این بار؛ null یعنی رمز پشتیبان خودکار. خطا، یا null. */
    suspend fun writeDrive(c: Context, password: String? = null): String? = withContext(Dispatchers.IO) {
        val uriStr = prefs(c).getString("drive_backup_uri", null) ?: return@withContext "گوگل درایو وصل نیست."
        val pass = password ?: secure(c).getString("auto_backup_password", null) ?: return@withContext "رمز پشتیبان گذاشته نشده."
        try {
            val db = AppDatabase.getDatabase(c, CoroutineScope(Dispatchers.IO))
            val bytes = Backup.encrypt(Backup.snapshot(db, Attachments.dir(c)), pass)
            val uri = Uri.parse(uriStr)
            // «wt» فایل را از اول می‌نویسد؛ جایی که پشتیبانی نشود «w»
            val out = runCatching { c.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
                ?: c.contentResolver.openOutputStream(uri, "w")
                ?: throw java.io.IOException("نوشتن فایل ممکن نشد")
            out.use { it.write(bytes) }
            prefs(c).edit().putLong("drive_backup_last", System.currentTimeMillis()).remove("drive_backup_error").apply()
            null
        } catch (e: SecurityException) {
            val msg = "دسترسی به فایل درایو از دست رفته؛ دوباره به گوگل درایو وصل شوید."
            prefs(c).edit().putString("drive_backup_error", msg).apply(); msg
        } catch (e: Exception) {
            val msg = "ذخیره در درایو انجام نشد: " + (e.message ?: e.javaClass.simpleName)
            prefs(c).edit().putString("drive_backup_error", msg).apply(); msg
        }
    }

    /** فایل پشتیبان درایو را می‌خواند (برای بازگردانی). */
    suspend fun readDrive(c: Context): Pair<ByteArray?, String?> = withContext(Dispatchers.IO) {
        val uriStr = prefs(c).getString("drive_backup_uri", null) ?: return@withContext null to "گوگل درایو وصل نیست."
        try {
            val bytes = c.contentResolver.openInputStream(Uri.parse(uriStr))?.use { it.readBytes() }
            if (bytes == null || bytes.isEmpty()) null to "فایل درایو خالی است یا هنوز دریافت نشده؛ اینترنت را بررسی کنید."
            else bytes to null
        } catch (e: SecurityException) {
            null to "دسترسی به فایل درایو از دست رفته؛ فایل را دوباره انتخاب کنید."
        } catch (e: Exception) {
            null to "خواندن فایل درایو انجام نشد: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    /** «Download/Taraz» from a tree uri like …/tree/primary%3ADownload%2FTaraz */
    private fun folderLabel(uri: Uri): String = try {
        DocumentsContract.getTreeDocumentId(uri).substringAfter(':').ifBlank { "حافظهٔ دستگاه" }
    } catch (e: Exception) { uri.lastPathSegment ?: "پوشهٔ انتخابی" }

    fun setEnabled(c: Context, on: Boolean) = prefs(c).edit().putBoolean("auto_backup_enabled", on).apply()
    fun setPeriod(c: Context, days: Int) = prefs(c).edit().putInt("auto_backup_period", days).apply()
    fun setPassword(c: Context, password: String) = secure(c).edit().putString("auto_backup_password", password).apply()

    fun setFolder(c: Context, tree: Uri) {
        runCatching {
            c.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        prefs(c).edit().putString("auto_backup_tree", tree.toString()).remove("auto_backup_error").apply()
    }

    /** Runs a backup when one is due (or [force]). Returns an error message, or null when done or not due. */
    suspend fun runIfDue(c: Context, force: Boolean = false): String? = withContext(Dispatchers.IO) {
        val st = state(c)
        if (!force && !st.enabled) return@withContext null
        if (!force && System.currentTimeMillis() - st.lastAt < st.periodDays * 86_400_000L - 3_600_000L) return@withContext null
        val hasDrive = prefs(c).getString("drive_backup_uri", null) != null
        val treeStr = prefs(c).getString("auto_backup_tree", null)
        if (treeStr == null && !hasDrive) return@withContext "پوشه یا گوگل درایو انتخاب نشده."
        val password = secure(c).getString("auto_backup_password", null) ?: return@withContext "رمز پشتیبان گذاشته نشده."
        // گوگل درایو: همان فایل با نسخهٔ تازه
        val driveErr = if (hasDrive) writeDrive(c, password) else null
        if (treeStr == null) {
            if (driveErr == null) prefs(c).edit().putLong("auto_backup_last", System.currentTimeMillis()).remove("auto_backup_error").apply()
            return@withContext driveErr
        }
        try {
            val tree = Uri.parse(treeStr)
            val db = AppDatabase.getDatabase(c, CoroutineScope(Dispatchers.IO))
            val bytes = Backup.encrypt(Backup.snapshot(db, Attachments.dir(c)), password)
            val d = JalaliDate.today()
            val name = PREFIX + "%04d-%02d-%02d.taraz".format(java.util.Locale.US, d.year, d.month, d.day)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val file = DocumentsContract.createDocument(c.contentResolver, parent, "application/octet-stream", name)
                ?: throw java.io.IOException("ساخت فایل در پوشه ممکن نشد")
            c.contentResolver.openOutputStream(file)?.use { it.write(bytes) } ?: throw java.io.IOException("نوشتن فایل ممکن نشد")
            prune(c, tree)
            prefs(c).edit()
                .putLong("auto_backup_last", System.currentTimeMillis())
                .putLong("auto_backup_last_size", bytes.size.toLong())
                .remove("auto_backup_error")
                .apply()
            driveErr
        } catch (e: SecurityException) {
            val msg = "دسترسی به پوشه از دست رفته؛ پوشه را دوباره انتخاب کنید."
            prefs(c).edit().putString("auto_backup_error", msg).apply(); msg
        } catch (e: Exception) {
            val msg = e.message ?: "پشتیبان ساخته نشد."
            prefs(c).edit().putString("auto_backup_error", msg).apply(); msg
        }
    }

    /** Keeps the newest [KEEP] automatic backups in the folder. */
    private fun prune(c: Context, tree: Uri) {
        val treeId = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
        val found = mutableListOf<Pair<String, String>>() // (name, documentId)
        c.contentResolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null, null, null
        )?.use { cur ->
            while (cur.moveToNext()) {
                val name = cur.getString(0) ?: continue
                if (name.startsWith(PREFIX)) found += name to cur.getString(1)
            }
        }
        found.sortedByDescending { it.first }.drop(KEEP).forEach { (_, id) ->
            runCatching { DocumentsContract.deleteDocument(c.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
    }
}
