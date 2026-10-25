/*
 * SPDX-FileCopyrightText: 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.lockstyles.settings

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Singularity's ImageUtils.saveImageToInternalStorage: picked images go to
 * /sdcard/Lunaris-OS/<feature>/<PREFIX>_<time>, where SystemUI reads them, replacing older ones.
 */
object LunarisImages {

    private const val TAG = "LunarisImages"

    fun save(context: Context, uri: Uri, feature: String, prefix: String): String? =
        runCatching {
            val type = context.contentResolver.getType(uri).orEmpty()
            val keepBytes = type == "image/gif" || type == "image/webp"
            val extension = when {
                type == "image/gif" -> ".gif"
                type == "image/webp" -> ".webp"
                else -> ".png"
            }
            val dir = File("/sdcard/DescartesOS/$feature").apply {
                if (!mkdirs() && !exists()) {
                    File("/sdcard/Lunaris-OS/$feature").mkdirs()
                }
            }
            dir.listFiles { _, name -> name.startsWith(prefix) }?.forEach { it.delete() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "${prefix}_$stamp$extension")
            context.contentResolver.openInputStream(uri)!!.use { input ->
                FileOutputStream(file).use { out ->
                    if (keepBytes) {
                        input.copyTo(out)
                    } else {
                        BitmapFactory.decodeStream(input)!!
                            .compress(Bitmap.CompressFormat.PNG, 90, out)
                    }
                }
            }
            file.absolutePath
        }.onFailure { Log.e(TAG, "Could not save $uri for $feature", it) }.getOrNull()
}
