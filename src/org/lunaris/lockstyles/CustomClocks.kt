package org.lunaris.lockstyles

import android.content.Context
import android.net.Uri
import android.os.UserHandle
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.provider.Settings
import android.util.AttributeSet
import android.util.Log
import android.util.LruCache
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextClock
import android.widget.TextView

/**
 * The custom lock screen clock styles behind Settings' "Custom clock style". Settings owns the
 * list; SystemUI owns the layouts that are actually shown, so previews are drawn from those,
 * placed, sized and coloured the way SystemUI's ClockStyle does it.
 */
object CustomClocks {

    private const val TAG = "CustomClocks"
    private const val SETTINGS = "com.android.settings"
    private const val SYSTEMUI = "com.android.systemui"
    private const val KEY_STYLE = "lock_screen_custom_clock_style"
    private const val KEY_COLOR_MODE = "lock_screen_custom_clock_color_mode"
    private const val KEY_CUSTOM_COLOR = "lock_screen_custom_clock_custom_color"
    private const val KEY_SIZE = "lock_screen_custom_clock_size_scale"
    private const val KEY_MARGIN_TOP = "lock_screen_custom_clock_margin_top"
    private const val KEY_OPACITY = "lock_screen_custom_clock_opacity"
    private const val CATEGORY_HIDE_CLOCK = "android.theme.customization.hideclock"
    private const val CATEGORY_SMARTSPACE = "android.theme.customization.smartspace"
    private const val OVERLAY_HIDE_CLOCK = "com.android.systemui.clocks.hideclock"
    private const val OVERLAY_HIDE_SMARTSPACE = "com.android.systemui.hide.smartspace"
    private const val KEY_MARGIN_START = "lock_screen_custom_clock_margin_start"
    private const val KEY_GRADIENT = "lock_screen_custom_clock_gradient_enabled"
    private const val KEY_GRADIENT_START = "lock_screen_custom_clock_gradient_color_start"
    private const val KEY_GRADIENT_END = "lock_screen_custom_clock_gradient_color_end"
    private const val KEY_GRADIENT_ANCHOR_Y = "lock_screen_custom_clock_gradient_anchor_y"
    private const val KEY_GRADIENT_RADIUS = "lock_screen_custom_clock_gradient_radius"
    private const val KEY_ALBUM_ART_COLOR = "lock_screen_custom_clock_album_art_color"
    private const val KEY_AOD_ANIM = "lock_screen_custom_clock_aod_anim"
    private const val KEY_WOBBLE = "lock_screen_custom_clock_wobble_on_charge"
    private const val KEY_WEATHER = "custom_clock_weather"
    private const val COLOR_MODE_ACCENT = "accent"
    private const val PREVIEW_SHADOW = 0x59000000
    private const val CLOCK_UTILS = "org.lunaris.settings.fragments.lockscreen.ClockUtils"

    /** Styles whose colours SystemUI never changes. */
    private val NO_COLOR = setOf(1, 2, 25, 26, 45)

    private class Catalogue(
        val names: List<String>,
        /** Per style, a layout in [inflateContext]'s package. */
        val layouts: IntArray,
        val inflateContext: Context,
        val systemUi: Context?,
    )

    /** Where SystemUI puts the clock, in pixels of the real screen. */
    class Frame(val top: Int, val side: Int, val width: Int, val scale: Float, val alpha: Float)

    /** How SystemUI sizes, places and colours a custom style; Settings' clock options. */
    data class Tuning(
        val scale: Int = 100,
        val opacity: Int = 100,
        val marginTop: Int = 15,
        val marginStart: Int = 0,
        val accent: Boolean = false,
        val gradient: Boolean = false,
        val gradientStart: Int = 0xFF00E5FF.toInt(),
        val gradientEnd: Int = 0xFFFF2DAA.toInt(),
        val gradientAnchorY: Int = 50,
        val gradientRadius: Int = 100,
        val albumArtColour: Boolean = false,
        val aodAnimation: Boolean = true,
        val wobbleOnCharge: Boolean = true,
        val weather: Boolean = true,
    ) {
        companion object {
            fun load(context: Context): Tuning {
                val cr = context.contentResolver
                // Hidden keys are only readable by system apps; a sideloaded build gets defaults.
                fun int(key: String, default: Int) =
                    runCatching { Settings.Secure.getInt(cr, key, default) }.getOrDefault(default)
                val d = Tuning()
                return Tuning(
                    scale = int(KEY_SIZE, d.scale),
                    opacity = int(KEY_OPACITY, d.opacity),
                    marginTop = int(KEY_MARGIN_TOP, d.marginTop),
                    marginStart = int(KEY_MARGIN_START, d.marginStart),
                    accent = runCatching { Settings.Secure.getString(cr, KEY_COLOR_MODE) }
                        .getOrNull() == COLOR_MODE_ACCENT,
                    gradient = int(KEY_GRADIENT, 0) != 0,
                    gradientStart = int(KEY_GRADIENT_START, d.gradientStart),
                    gradientEnd = int(KEY_GRADIENT_END, d.gradientEnd),
                    gradientAnchorY = int(KEY_GRADIENT_ANCHOR_Y, d.gradientAnchorY),
                    gradientRadius = int(KEY_GRADIENT_RADIUS, d.gradientRadius),
                    albumArtColour = int(KEY_ALBUM_ART_COLOR, 0) != 0,
                    aodAnimation = int(KEY_AOD_ANIM, 1) != 0,
                    wobbleOnCharge = int(KEY_WOBBLE, 1) != 0,
                    weather = int(KEY_WEATHER, 1) != 0,
                )
            }
        }

        fun save(context: Context) {
            val cr = context.contentResolver
            fun put(key: String, value: Int) = Settings.Secure.putInt(cr, key, value)
            fun put(key: String, value: Boolean) = put(key, if (value) 1 else 0)
            put(KEY_SIZE, scale)
            put(KEY_OPACITY, opacity)
            put(KEY_MARGIN_TOP, marginTop)
            put(KEY_MARGIN_START, marginStart)
            put(KEY_GRADIENT, gradient)
            put(KEY_GRADIENT_START, gradientStart)
            put(KEY_GRADIENT_END, gradientEnd)
            put(KEY_GRADIENT_ANCHOR_Y, gradientAnchorY)
            put(KEY_GRADIENT_RADIUS, gradientRadius)
            put(KEY_ALBUM_ART_COLOR, albumArtColour)
            put(KEY_AOD_ANIM, aodAnimation)
            put(KEY_WOBBLE, wobbleOnCharge)
            put(KEY_WEATHER, weather)
        }
    }

    @Volatile private var catalogue: Catalogue? = null
    private val previews = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    private fun load(context: Context): Catalogue? {
        catalogue?.let { return it }
        loadFromSystemUI(context)?.let { return it }
        return try {
            val settings = context.createPackageContext(SETTINGS,
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY)
            val utils =
                settings.classLoader.loadClass(CLOCK_UTILS)
            val instance = utils.getField("INSTANCE").get(null)
            val settingsLayouts = utils.getMethod("getCLOCK_LAYOUTS").invoke(instance) as IntArray
            @Suppress("UNCHECKED_CAST")
            val names =
                (utils.getMethod("getClockNames").invoke(instance) as Array<String>).toList()
            val systemUi = runCatching {
                context.createPackageContext(SYSTEMUI,
                    Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY)
            }.getOrNull()
            // SystemUI's copy of each layout, by name, is what the lock screen really shows.
            val layouts = settingsLayouts.map { id ->
                val name = settings.resources.getResourceEntryName(id)
                systemUi?.resources?.getIdentifier(name, "layout", SYSTEMUI) ?: 0
            }
            val useSystemUi = systemUi != null && layouts.drop(1).all { it != 0 }
            Catalogue(
                names,
                if (useSystemUi) layouts.toIntArray() else settingsLayouts,
                ContextThemeWrapper(if (useSystemUi) systemUi else settings,
                    android.R.style.Theme_DeviceDefault_NoActionBar),
                systemUi,
            ).also { catalogue = it }
        } catch (e: Exception) {
            Log.w(TAG, "Settings has no custom clock styles, falling back to SystemUI", e)
            loadFromSystemUI(context)
        }
    }

    private fun loadFromSystemUI(context: Context): Catalogue? {
        val systemUi = runCatching {
            context.createPackageContext(SYSTEMUI,
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY)
        }.getOrNull() ?: return null

        val exported = runCatching {
            context.contentResolver.call(Uri.parse("content://com.android.systemui.clockcatalogue"),
                "clock_layouts", null, null)?.getIntArray("layouts")
        }.getOrNull()
        if (exported != null && exported.size > 1) {
            return Catalogue(
                exported.indices.map { CLOCK_NAMES.getOrNull(it) ?: "Clock $it" },
                exported,
                ContextThemeWrapper(systemUi, android.R.style.Theme_DeviceDefault_NoActionBar),
                systemUi,
            ).also { catalogue = it }
        }
        val res = systemUi.resources
        val names = mutableListOf<String>()
        val layouts = mutableListOf<Int>()

        // Index 0: Default
        names.add(CLOCK_NAMES.firstOrNull() ?: "Default Clock")
        layouts.add(0)

        for (i in 1 until CLOCK_LAYOUT_NAMES.size) {
            val layoutName = CLOCK_LAYOUT_NAMES[i]
            val layoutId = res.getIdentifier(layoutName, "layout", SYSTEMUI)
            layouts.add(layoutId)
            names.add(if (i < CLOCK_NAMES.size) CLOCK_NAMES[i] else layoutName)
        }

        return if (layouts.drop(1).any { it != 0 }) {
            Catalogue(
                names,
                layouts.toIntArray(),
                ContextThemeWrapper(systemUi, android.R.style.Theme_DeviceDefault_NoActionBar),
                systemUi,
            ).also { catalogue = it }
        } else {
            null
        }
    }

    private val CLOCK_LAYOUT_NAMES = arrayOf(
        "keyguard_clock_default",
        "keyguard_clock_oos",
        "keyguard_clock_oos2",
        "keyguard_clock_center",
        "keyguard_clock_simple",
        "keyguard_clock_miui",
        "keyguard_clock_ide",
        "keyguard_clock_moto",
        "keyguard_clock_stylish",
        "keyguard_clock_stylish2",
        "keyguard_clock_stylish3",
        "keyguard_clock_stylish4",
        "keyguard_clock_stylish5",
        "keyguard_clock_stylish6",
        "keyguard_clock_stylish7",
        "keyguard_clock_stylish8",
        "keyguard_clock_stylish9",
        "keyguard_clock_stylish10",
        "keyguard_clock_word",
        "keyguard_clock_life",
        "keyguard_clock_a9",
        "keyguard_clock_nos1",
        "keyguard_clock_nos2",
        "keyguard_clock_num",
        "keyguard_clock_accent",
        "keyguard_clock_analog",
        "keyguard_clock_block",
        "keyguard_clock_bubble",
        "keyguard_clock_label",
        "keyguard_clock_ios",
        "keyguard_clock_taden",
        "keyguard_clock_mont",
        "keyguard_clock_encode",
        "keyguard_clock_nos3",
        "keyguard_anci_clock_outline",
        "keyguard_anci_clock_ovalium",
        "keyguard_anci_clock_rectangle",
        "keyguard_anci_clock_wallet",
        "keyguard_anci_clockdate_clavicula",
        "keyguard_anci_clockdate_kln",
        "keyguard_anci_clockdate_miring",
        "keyguard_anci_clockdate_scapula",
        "keyguard_anci_clockdate_sternum",
        "keyguard_sparkCircle",
        "keyguard_sparkList",
        "keyguard_clock_miui2",
        "keyguard_clock_ios2",
        "keyguard_clock_ios3",
        "keyguard_clock_ios4",
        "keyguard_clock_ios5",
        "keyguard_clock_ios6",
        "keyguard_clock_ios7",
        "keyguard_clock_ios8",
        "keyguard_clock_ios9",
        "keyguard_clock_ios10",
        "keyguard_clock_ios11",
        "keyguard_clock_ios12",
        "keyguard_clock_ios13",
        "keyguard_clock_ios14",
        "keyguard_clock_big1",
        "keyguard_clock_big2",
        "keyguard_clock_big3",
        "keyguard_clock_big4",
        "keyguard_clock_sweet",
        "keyguard_clock_pixel",
        "keyguard_clock_samurai",
        "keyguard_clock_gateway",
        "keyguard_clock_tall",
    )

    private val CLOCK_NAMES = listOf(
        "Default Clock",
        "OnePlus Clock",
        "OnePlus Clock 2",
        "Center Clock",
        "Simple Clock",
        "MIUI Clock",
        "IDE Clock",
        "Moto Clock",
        "Stylish Clock",
        "Stylish Clock 2",
        "Stylish Clock 3",
        "Stylish Clock 4",
        "Stylish Clock 5",
        "Stylish Clock 6",
        "Stylish Clock 7",
        "Stylish Clock 8",
        "Stylish Clock 9",
        "Stylish Clock 10",
        "Text Clock",
        "LifeStyle Clock",
        "Android 9 Vibe",
        "NothingOS 1 Clock",
        "NothingOS 2 Clock",
        "Stacked Clock",
        "X Factor",
        "Simple Analog",
        "Block",
        "Bubble",
        "Label Clock",
        "iOS Clock",
        "Taden Clock",
        "Mont Clock",
        "Encode Clock",
        "NOS Clock 3",
        "Anci Outline",
        "Anci Ovalium",
        "Anci Rectangle",
        "Anci Wallet",
        "Anci Clavicula",
        "Anci KLN",
        "Anci Miring",
        "Anci Scapula",
        "Anci Sternum",
        "Spark Circle",
        "Spark List",
        "HyperOS Clock",
        "iOS 2",
        "iOS 3",
        "iOS 4",
        "iOS 5",
        "iOS 6",
        "iOS 7",
        "iOS 8",
        "iOS 9",
        "iOS 10",
        "iOS 11",
        "iOS 12",
        "iOS 13",
        "iOS 14",
        "Big 1",
        "Big 2",
        "Big 3",
        "Big 4",
        "Sweet",
        "Pixel",
        "Samurai",
        "Gateway",
        "Tall clock",
    )

    /** Style names, the first being the default clock. Empty when Settings lacks them. */
    fun names(context: Context): List<String> = load(context)?.names ?: emptyList()

    fun isColourable(style: Int) = style !in NO_COLOR

    fun frame(context: Context, tuning: Tuning = Tuning.load(context)): Frame {
        val density = context.resources.displayMetrics.density
        val screen = context.resources.displayMetrics.widthPixels
        val res = load(context)?.systemUi?.resources
        fun dimen(name: String, fallbackDp: Float): Int {
            val id = res?.getIdentifier(name, "dimen", SYSTEMUI) ?: 0
            return if (id != 0) res!!.getDimensionPixelSize(id) else (fallbackDp * density).toInt()
        }
        val statusBar = dimen("status_bar_height", 28f)
        val side = dimen("below_clock_padding_start", 32f)
        return Frame(
            top = (statusBar * 1.25f + tuning.marginTop * density).toInt(),
            side = side + (tuning.marginStart * density).toInt(),
            width = screen - side * 2,
            scale = tuning.scale.coerceIn(50, 150) / 100f,
            alpha = tuning.opacity.coerceIn(0, 100) / 100f,
        )
    }

    /**
     * The style drawn at [width] pixels with the current time, or null for the default clock.
     * [trim] crops the empty space around it, for small tiles.
     */
    fun preview(context: Context, style: Int, width: Int, color: Int?,
            trim: Boolean = false): Bitmap? {
        if (style <= 0) return null
        val minute = System.currentTimeMillis() / 60_000
        val key = "$style/$width/$color/$minute/$trim"
        previews.get(key)?.let { return it }
        val cat = load(context) ?: return null
        if (style >= cat.layouts.size) return null
        return try {
            val inflater = LayoutInflater.from(cat.inflateContext).cloneInContext(
                cat.inflateContext).apply { factory2 = SystemUiViews(cat.systemUi?.classLoader) }
            val view = inflater.inflate(cat.layouts[style], null)
            prepare(view, if (isColourable(style)) color ?: Color.WHITE else null)
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            if (view.measuredHeight <= 0) return null
            Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
                .let {
                    view.draw(Canvas(it))
                    if (trim) trimmed(it) else it
                }
                ?.also { previews.put(key, it) }
        } catch (e: Exception) {
            Log.w(TAG, "Could not draw clock style $style", e)
            null
        }
    }

    /**
     * SystemUI's clock views only need a context, so they're loaded from its code. Its other
     * views (weather and the like) need SystemUI itself; plain views keep the layout's shape.
     */
    private class SystemUiViews(private val loader: ClassLoader?) : LayoutInflater.Factory2 {
        override fun onCreateView(parent: View?, name: String, context: Context,
                attrs: AttributeSet): View? {
            if (!name.contains('.') || name.startsWith("android.")) return null
            if (loader != null && name.startsWith("$SYSTEMUI.clocks.")) {
                try {
                    return loader.loadClass(name).asSubclass(View::class.java)
                        .getConstructor(Context::class.java, AttributeSet::class.java)
                        .newInstance(context, attrs)
                } catch (e: ReflectiveOperationException) {
                    Log.w(TAG, "Could not build $name", e)
                } catch (e: LinkageError) {
                    Log.w(TAG, "Could not build $name", e)
                }
            }
            return if (name.endsWith("TextView")) TextView(context, attrs) else View(context, attrs)
        }

        override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? =
            onCreateView(null, name, context, attrs)
    }

    private fun prepare(view: View, color: Int?) {
        if (view is TextView) {
            // Detached views never tick, and single-line centred text is only scrolled into
            // place once attached, so both are done by hand.
            if (view is TextClock) view.refreshTime()
            if (view.isSingleLine) {
                view.setSingleLine(false)
                view.maxLines = 1
            }
            // As SystemUI does: only text that was white takes the chosen colour.
            if (color != null && (view.currentTextColor and 0xFFFFFF) == 0xFFFFFF) {
                view.setTextColor(color)
            }
            // The lock screen dims the wallpaper behind the clock and the previews don't, so
            // white clocks vanish on light wallpapers without this.
            view.setShadowLayer(view.textSize * 0.08f, 0f, view.textSize * 0.02f, PREVIEW_SHADOW)
        }
        if (view is ViewGroup) {
            view.clipChildren = false
            for (i in 0 until view.childCount) prepare(view.getChildAt(i), color)
        }
    }

    /** Crops away the empty space the layouts leave for the rest of the lock screen. */
    private fun trimmed(bitmap: Bitmap): Bitmap? {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
        var left = w
        var top = h
        var right = -1
        var bottom = -1
        for (y in 0 until h) for (x in 0 until w) {
            if (pixels[y * w + x] ushr 24 > 16) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (right < left || bottom < top) return null
        return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
    }

    /**
     * Applies [style] as Settings would. Switching between the default clock and a custom one
     * needs SystemUI to restart, as parts of the keyguard only read it at startup.
     */
    fun apply(context: Context, style: Int, color: Int?, tuning: Tuning? = null) {
        val resolver = context.contentResolver
        val userId = UserHandle.USER_CURRENT
        val before = runCatching {
            Settings.Secure.getIntForUser(resolver, KEY_STYLE, 0, userId)
        }.getOrDefault(0)
        Settings.Secure.putIntForUser(resolver, KEY_STYLE, style, userId)
        setAospClockHidden(context, style != 0)
        if (style != 0) {
            // Without new tuning, e.g. from the switcher, the current accent or gradient stays.
            val current = tuning ?: Tuning.load(context)
            // As Settings does: a gradient is drawn in the custom colour mode.
            val mode = when {
                current.gradient -> "custom"
                current.accent -> COLOR_MODE_ACCENT
                color != null -> "custom"
                else -> "default"
            }
            Settings.Secure.putString(resolver, KEY_COLOR_MODE, mode)
            if (color != null) Settings.Secure.putInt(resolver, KEY_CUSTOM_COLOR, color)
            tuning?.save(context)
        }
        if (before != style) {
            org.lunaris.lockstyles.settings.Lunaris.restartSystemUI(context)
        }
    }

    /**
     * As Singularity's clock picker does: a custom style hides SystemUI's own clock and
     * smartspace through their theme overlays.
     */
    private fun setAospClockHidden(context: Context, hidden: Boolean) {
        org.lunaris.lockstyles.settings.Lunaris.setOverlay(context, CATEGORY_HIDE_CLOCK,
            if (hidden) OVERLAY_HIDE_CLOCK else "android", "android")
        org.lunaris.lockstyles.settings.Lunaris.setOverlay(context, CATEGORY_SMARTSPACE,
            if (hidden) OVERLAY_HIDE_SMARTSPACE else SYSTEMUI, SYSTEMUI)
    }
}
