package org.lunaris.lockstyles

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.UUID

object AutoDepthExtractor {
    @Synchronized
    fun extract(context: Context, photo: Bitmap): String? {
        val segmenter = PortraitSegmenter(context.applicationContext)
        var subject: Bitmap? = null
        return try {
            segmenter.init()
            check(segmenter.isReady()) { "Subject model is unavailable" }
            val result = segmenter.segment(photo) ?: error("Subject extraction failed")
            subject = result
            val dir = File(context.filesDir, "depth_subjects").apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.png")
            try {
                file.outputStream().use { check(result.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                Uri.Builder().scheme("content").authority(DepthSubjectStore.AUTHORITY)
                    .appendPath(file.name).build().toString()
            } catch (e: Exception) {
                file.delete()
                throw e
            }
        } catch (e: Exception) {
            Log.e("AutoDepthExtractor", "Could not extract subject", e)
            null
        } catch (e: LinkageError) {
            Log.e("AutoDepthExtractor", "Subject runtime is unavailable", e)
            null
        } finally {
            if (subject !== photo) subject?.recycle()
            segmenter.release()
        }
    }

    fun extractCurrent(context: Context): String? {
        val manager = WallpaperManager.getInstance(context)
        val descriptor = manager.getWallpaperFile(WallpaperManager.FLAG_LOCK)
            ?: manager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM) ?: return null
        val photo = descriptor.use { BitmapFactory.decodeFileDescriptor(it.fileDescriptor) }
            ?: return null
        return try { extract(context, photo) } finally { photo.recycle() }
    }
}
