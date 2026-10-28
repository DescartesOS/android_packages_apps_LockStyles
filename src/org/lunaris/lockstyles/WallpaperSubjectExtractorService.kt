package org.lunaris.lockstyles

import android.app.Service
import android.app.WallpaperManager
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class WallpaperSubjectExtractorService : Service() {
    private val worker = Executors.newSingleThreadExecutor()
    private val generation = AtomicInteger()
    private val handler = Handler(Looper.getMainLooper())
    private val colors = WallpaperManager.OnColorsChangedListener { _, _ ->
        if (Settings.System.getInt(contentResolver, "depth_wallpaper_auto_subject", 0) != 0 &&
            Settings.System.getInt(contentResolver, "depth_wallpaper_enabled", 0) != 0) extract()
    }

    override fun onCreate() {
        super.onCreate()
        WallpaperManager.getInstance(this).addOnColorsChangedListener(colors, handler)
    }

    private fun extract() {
        val request = generation.incrementAndGet()
        val manager = WallpaperManager.getInstance(this)
        val lockId = manager.getWallpaperId(WallpaperManager.FLAG_LOCK)
        val systemId = manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        worker.execute {
            if (request != generation.get()) return@execute
            val subject = runCatching { AutoDepthExtractor.extractCurrent(this) }.getOrNull()
            handler.post {
                if (request != generation.get() ||
                    lockId != manager.getWallpaperId(WallpaperManager.FLAG_LOCK) ||
                    systemId != manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)) return@post
                if (subject != null) {
                    Settings.System.putString(contentResolver, GalleryApplier.DEPTH_SUBJECT, subject)
                    Settings.System.putInt(contentResolver, "depth_wallpaper_enabled", 1)
                } else {
                    Toast.makeText(this, R.string.gallery_depth_extract_error, Toast.LENGTH_LONG).show()
                }
                if (Settings.System.getInt(contentResolver, "depth_wallpaper_auto_subject", 0) == 0)
                    stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        extract()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        generation.incrementAndGet()
        WallpaperManager.getInstance(this).removeOnColorsChangedListener(colors)
        worker.shutdown()
        super.onDestroy()
    }
}
