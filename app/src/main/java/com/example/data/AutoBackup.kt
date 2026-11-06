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
        val lastError: String?
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
            lastError = p.getString("auto_backup_error", null)
        )
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
        val treeStr = prefs(c).getString("auto_backup_tree", null) ?: return@withContext "پوشه‌ای انتخاب نشده."
        val password = secure(c).getString("auto_backup_password", null) ?: return@withContext "رمز پشتیبان گذاشته نشده."
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
            null
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
