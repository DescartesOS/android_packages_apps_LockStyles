/*
 * SPDX-FileCopyrightText: 2016-2026 crDroid Android Project
 * SPDX-FileCopyrightText: 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.lockstyles.settings

import android.content.Context
import android.content.Intent
import android.content.om.OverlayInfo
import android.content.pm.PackageManager
import android.hardware.fingerprint.FingerprintManager
import android.net.Uri
import android.os.SystemProperties
import android.os.Vibrator
import android.util.Log

/**
 * What Singularity reached through com.android.internal.util.lunaris and DeviceUtils. The
 * framework classes are Lunaris' own, so they are called by name: a build without them keeps
 * working, it just can't restart SystemUI or switch overlays.
 */
object Lunaris {

    private const val TAG = "RomUtils"
    private val UTILS_CLASSES = listOf(
        "com.android.internal.util.crdroid.Utils",
        "com.android.internal.util.lunaris.Utils",
    )
    private val THEME_UTILS_CLASSES = listOf(
        "com.android.internal.util.crdroid.ThemeUtils",
        "com.android.internal.util.lunaris.ThemeUtils",
    )
    private const val OMNIJAWS = "org.omnirom.omnijaws"
    private val OMNIJAWS_SETTINGS = Uri.parse("content://org.omnirom.omnijaws.provider/settings")
    private const val PROP_CUSTOM_UDFPS = "persist.sys.udfps.custom"

    /**
     * Restarts SystemUI through the status bar service, as Singularity does. That takes the
     * platform signature, so otherwise it's force-stopped, and comes straight back as it's
     * persistent.
     */
    fun restartSystemUI(context: Context) {
        if (context.checkSelfPermission(android.Manifest.permission.STATUS_BAR_SERVICE) ==
                PackageManager.PERMISSION_GRANTED) {
            for (className in UTILS_CLASSES) {
                runCatching {
                    Class.forName(className).getMethod("restartSystemUI").invoke(null)
                }.onSuccess { return }
            }
        }
        runCatching {
            context.getSystemService(android.app.ActivityManager::class.java)!!.javaClass
                .getMethod("forceStopPackage", String::class.java)
                .invoke(context.getSystemService(android.app.ActivityManager::class.java),
                    SYSTEMUI)
        }.onFailure { Log.w(TAG, "Could not restart SystemUI", it) }
    }

    private const val SYSTEMUI = "com.android.systemui"

    private fun themeUtils(context: Context): Any? {
        for (className in THEME_UTILS_CLASSES) {
            runCatching {
                val clazz = Class.forName(className)
                try {
                    clazz.getMethod("getInstance", Context::class.java).invoke(null, context)
                } catch (_: NoSuchMethodException) {
                    clazz.getConstructor(Context::class.java).newInstance(context)
                }
            }.getOrNull()?.let { return it }
        }
        Log.w(TAG, "ThemeUtils is missing")
        return null
    }

    /** Enables [packageName] in [category], or the target itself to turn the category off. */
    fun setOverlay(context: Context, category: String, packageName: String, target: String) {
        val utils = themeUtils(context) ?: return
        runCatching {
            utils.javaClass.getMethod("setOverlayEnabled", String::class.java,
                String::class.java, String::class.java)
                .invoke(utils, category, packageName, target)
        }.onFailure { Log.w(TAG, "Could not set $category to $packageName", it) }
    }

    /** The overlays of [category], with the one that is on. */
    @Suppress("UNCHECKED_CAST")
    fun overlays(context: Context, category: String): List<OverlayInfo> {
        val utils = themeUtils(context) ?: return emptyList()
        return runCatching {
            utils.javaClass.getMethod("getOverlayInfos", String::class.java)
                .invoke(utils, category) as List<OverlayInfo>
        }.onFailure { Log.w(TAG, "Could not list $category", it) }.getOrDefault(emptyList())
    }

    fun isOmniJawsEnabled(context: Context): Boolean {
        if (!isInstalled(context, OMNIJAWS)) return false
        return runCatching {
            context.contentResolver.query(OMNIJAWS_SETTINGS, arrayOf("enabled"), null, null,
                null)?.use { it.moveToFirst() && it.getInt(0) == 1 } ?: false
        }.getOrDefault(false)
    }

    fun omniJawsSettings(): Intent =
        Intent(Intent.ACTION_MAIN).setClassName(OMNIJAWS, "$OMNIJAWS.SettingsActivity")

    fun hasFingerprint(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) &&
            context.getSystemService(FingerprintManager::class.java)?.isHardwareDetected == true

    fun hasVibrator(context: Context): Boolean =
        context.getSystemService(Vibrator::class.java)?.hasVibrator() == true

    /** Whether the device ships Lunaris' under-display fingerprint customisation. */
    fun hasCustomUdfps(): Boolean = SystemProperties.getBoolean(PROP_CUSTOM_UDFPS, false)

    fun isInstalled(context: Context, pkg: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
}
