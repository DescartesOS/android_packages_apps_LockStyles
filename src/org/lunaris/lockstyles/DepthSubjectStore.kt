package org.lunaris.lockstyles

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

object DepthSubjectStore {
    const val AUTHORITY = "org.lunaris.lockstyles.depth"

    fun importSubject(context: Context, uri: Uri): String? = runCatching {
        val resolver = context.contentResolver
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        require(options.outWidth > 0 && options.outHeight > 0)
        options.inJustDecodeBounds = false
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 2560) {
            options.inSampleSize *= 2
        }
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("Cannot decode subject")
        try {
            require(bitmap.hasAlpha()) { "A transparent subject is required" }
            val stepX = (bitmap.width / 64).coerceAtLeast(1)
            val stepY = (bitmap.height / 64).coerceAtLeast(1)
            val transparent = (0 until bitmap.height step stepY).any { y ->
                (0 until bitmap.width step stepX).any { x -> (bitmap.getPixel(x, y) ushr 24) < 255 }
            }
            require(transparent) { "A transparent subject is required" }
            val dir = File(context.filesDir, "depth_subjects").apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.png")
            file.outputStream().use { require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(file.name).build().toString()
        } finally {
            bitmap.recycle()
        }
    }.onFailure { Log.w("DepthSubjectStore", "Cannot import subject", it) }.getOrNull()
}

/** Read access is restricted to privileged wallpaper readers. */
class DepthSubjectProvider : ContentProvider() {
    override fun onCreate() = true

    private fun file(uri: Uri): File {
        val name = uri.lastPathSegment ?: throw FileNotFoundException()
        if (uri.authority != DepthSubjectStore.AUTHORITY || uri.pathSegments.size != 1 ||
            !name.matches(Regex("[0-9a-f-]{36}\\.png"))) throw FileNotFoundException()
        val file = File(File(requireNotNull(context).filesDir, "depth_subjects"), name)
        if (!file.isFile) throw FileNotFoundException()
        return file
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read only")
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri) = "image/png"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val file = file(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {
            addRow(columns.map { column -> when (column) {
                OpenableColumns.DISPLAY_NAME -> file.name
                OpenableColumns.SIZE -> file.length()
                else -> null
            } })
        }
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?,
        selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
