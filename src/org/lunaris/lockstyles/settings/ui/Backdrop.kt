/*
 * SPDX-FileCopyrightText: 2026 Lunaris-AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.lockstyles.settings.ui

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lunaris.lockstyles.GalleryApplier

/** The lock screen wallpaper, blurred, that the settings pages sit on. */
val LocalBackdrop = staticCompositionLocalOf<Bitmap?> { null }

private const val SCALE = 4

@Composable
fun rememberBackdrop(): Bitmap? {
    val context = LocalContext.current
    val radius = with(LocalDensity.current) { 32.dp.toPx() } / SCALE
    val backdrop by produceState<Bitmap?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = context.getSystemService(android.view.WindowManager::class.java)!!
                    .maximumWindowMetrics.bounds
                val wm = WallpaperManager.getInstance(context)
                val drawable = wm.getDrawable(WallpaperManager.FLAG_LOCK) ?: wm.drawable
                    ?: return@runCatching null
                // A quarter of the screen is plenty under this much blur.
                val small = Bitmap.createBitmap(bounds.width() / SCALE, bounds.height() / SCALE,
                    Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, small.width, small.height)
                drawable.draw(Canvas(small))
                GalleryApplier.blur(small, radius)
            }.getOrNull()
        }
    }
    return backdrop
}
