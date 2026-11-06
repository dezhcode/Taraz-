package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Photos attached to transactions (an invoice, a warranty card). Kept inside
 * the app's own storage, shrunk to at most 1600 px; never read for text.
 */
object Attachments {
    private const val MAX = 1600

    fun dir(c: Context): File = File(c.filesDir, "attachments").apply { mkdirs() }

    fun file(c: Context, name: String): File = File(dir(c), name)

    /** A temporary file the camera writes into, and the content uri to hand it. */
    fun cameraTarget(c: Context): Pair<File, Uri> {
        val f = File(File(c.cacheDir, "camera").apply { mkdirs() }, "cap_${System.currentTimeMillis()}.jpg")
        return f to FileProvider.getUriForFile(c, c.packageName + ".files", f)
    }

    /** Copies a picked or captured image into the attachments folder; returns its name, or null. */
    fun save(c: Context, uri: Uri): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > MAX * 2 || bounds.outHeight / sample > MAX * 2) sample *= 2
        val bmp = c.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val scale = minOf(1f, MAX.toFloat() / maxOf(bmp.width, bmp.height))
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val name = "att_${UUID.randomUUID().toString().take(12)}.jpg"
        file(c, name).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        name
    }.getOrNull()

    fun delete(c: Context, name: String) {
        runCatching { file(c, name).delete() }
    }
}
