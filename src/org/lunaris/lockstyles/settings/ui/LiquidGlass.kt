/*
 * SPDX-FileCopyrightText: 2025-2026 AxionOS
 * SPDX-FileCopyrightText: 2026 Lunaris-AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.lockstyles.settings.ui

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lunaris.lockstyles.GalleryApplier

/*
 * Liquid glass: each surface is a lens over the page's backdrop. The backdrop is rmp22's
 * ax_blur material from AxionOS, the wallpaper blurred by 34dp under an accent2 tint; the
 * lens bends it towards the rim, splits its colours a little there and lights the edge, as
 * Apple's glass.
 */

/** The page backdrop, drawn once at a quarter of the screen and scaled up. */
class Backdrop(val bitmap: Bitmap, val screenWidth: Int, val screenHeight: Int) {
    val shader: BitmapShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        .apply {
            filterMode = BitmapShader.FILTER_MODE_LINEAR
            setLocalMatrix(Matrix().apply {
                setScale(screenWidth / bitmap.width.toFloat(), screenHeight / bitmap.height.toFloat())
            })
        }
}

val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

private const val SCALE = 4

@Composable
fun rememberBackdrop(): Backdrop? {
    val context = LocalContext.current
    val density = LocalDensity.current
    val backdrop by produceState<Backdrop?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = context.getSystemService(android.view.WindowManager::class.java)!!
                    .maximumWindowMetrics.bounds
                val w = bounds.width()
                val h = bounds.height()
                val wm = WallpaperManager.getInstance(context)
                val drawable = wm.getDrawable(WallpaperManager.FLAG_LOCK) ?: wm.drawable
                    ?: return@runCatching null
                val small = Bitmap.createBitmap(w / SCALE, h / SCALE, Bitmap.Config.ARGB_8888)
                Canvas(small).also { drawable.setBounds(0, 0, small.width, small.height) }
                    .let { drawable.draw(it) }
                // Lighter than ax_blur's 34dp, so the wallpaper reads through the glass.
                val radius = with(density) { 14.dp.toPx() } / SCALE
                val blurred = GalleryApplier.blur(small, radius).copy(Bitmap.Config.ARGB_8888, true)
                // ax_blur's surface: the wallpaper's accent2, dark, over the blur.
                val tint = context.getColor(android.R.color.system_accent2_900)
                Canvas(blurred).drawColor((tint and 0x00FFFFFF) or (0x59 shl 24))
                Backdrop(blurred, w, h)
            }.getOrNull()
        }
    }
    return backdrop
}

private const val GLASS_SHADER = """
uniform shader backdrop;
uniform float2 size;
uniform float2 origin;
uniform float radius;
uniform float refraction;   // how far the rim bends the backdrop, px
uniform float depth;        // how deep the lens reaches in from the rim, px
uniform float dispersion;   // colour split at the rim
uniform half4 tint;         // premultiplied
uniform float rim;          // edge light strength

// A squircle: rounded corners that ease into the sides, as Apple's continuous corners.
float squircle(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    float2 m = max(q, 0.0) / r;
    float corner = r * (pow(pow(m.x, 4.0) + pow(m.y, 4.0), 0.25) - 1.0);
    return min(max(q.x, q.y), 0.0) + corner;
}

half4 main(float2 p) {
    float2 half_ = size * 0.5;
    float2 c = p - half_;
    float sd = squircle(c, half_, radius);
    if (sd > 1.0) return half4(0.0);

    float2 n = float2(squircle(c + float2(1.0, 0.0), half_, radius) -
                          squircle(c - float2(1.0, 0.0), half_, radius),
                      squircle(c + float2(0.0, 1.0), half_, radius) -
                          squircle(c - float2(0.0, 1.0), half_, radius));
    n = n / max(length(n), 1e-4);

    // A convex slab: flat in the middle, bending harder towards the rim.
    float t = clamp(-sd / depth, 0.0, 1.0);
    float bend = (1.0 - t) * (1.0 - t);
    float2 at = origin + p - n * refraction * bend;
    float2 split = n * refraction * bend * dispersion;
    half3 col = half3(backdrop.eval(at - split).r, backdrop.eval(at).g,
                      backdrop.eval(at + split).b);

    // Clear glass: what's behind a little brighter and richer, under a milky tint.
    half luma = dot(col, half3(0.2126, 0.7152, 0.0722));
    col = mix(half3(luma), col, 1.2) * 1.1;
    col = col * (1.0 - tint.a) + tint.rgb;

    // A soft glow just inside the rim, as light gathers in the edge of the glass.
    col += half3(0.10 * (1.0 - t) * (1.0 - t));

    // A thin light line all round, with strong glints on the lit and the opposite corner.
    float line = smoothstep(1.6, 0.0, -sd);
    float facing = dot(n, normalize(float2(-0.6, -0.8)));
    float glint = pow(max(facing, 0.0), 3.0) + 0.6 * pow(max(-facing, 0.0), 3.0);
    col += half3(line * rim * (0.35 + 0.65 * glint));

    float a = clamp(0.5 - sd, 0.0, 1.0);
    return half4(col * a, a);
}
"""

/** The one corner radius every glass surface uses. */
val GlassRadius = 24.dp

/**
 * Draws this element as a squircle of liquid glass over [LocalBackdrop]. Without a backdrop
 * yet, it falls back to a plain translucent fill.
 */
fun Modifier.liquidGlass(
    radius: Dp = GlassRadius,
    refraction: Dp = 14.dp,
    depth: Dp = 22.dp,
    tint: Int = 0x24FFFFFF,
    rim: Float = 0.9f,
): Modifier = composed {
    val backdrop = LocalBackdrop.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    this
        .onGloballyPositioned { origin = it.positionInWindow() }
        .drawWithCache {
            if (backdrop == null) {
                return@drawWithCache onDrawBehind {
                    drawRect(androidx.compose.ui.graphics.Color(tint))
                }
            }
            val shader = RuntimeShader(GLASS_SHADER).apply {
                setInputShader("backdrop", backdrop.shader)
                setFloatUniform("size", size.width, size.height)
                with(density) {
                    // Never more than half the short side, so small tiles stay squircles.
                    setFloatUniform("radius",
                        minOf(radius.toPx(), minOf(size.width, size.height) / 2f))
                    setFloatUniform("refraction", refraction.toPx())
                    setFloatUniform("depth", depth.toPx())
                }
                setFloatUniform("dispersion", 0.25f)
                val a = (tint ushr 24) / 255f
                setFloatUniform("tint", ((tint shr 16) and 0xFF) / 255f * a,
                    ((tint shr 8) and 0xFF) / 255f * a, (tint and 0xFF) / 255f * a, a)
                setFloatUniform("rim", rim)
            }
            val brush = ShaderBrush(shader)
            onDrawBehind {
                // Where this is on screen changes while scrolling, so it's set every frame.
                shader.setFloatUniform("origin", origin.x, origin.y)
                drawRect(brush)
            }
        }
}

/** The page background: the same backdrop the glass bends, at full size. */
fun Modifier.backdrop(backdrop: Backdrop?): Modifier = drawWithCache {
    val paint = Paint().apply { shader = backdrop?.shader }
    onDrawBehind {
        if (backdrop == null) {
            drawRect(androidx.compose.ui.graphics.Color(0xFF0B0B10))
        } else {
            drawContext.canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
        }
    }
}

/**
 * The same squircle as the glass, for clipping ripples and content to it: each corner is a
 * superellipse, |x|^4 + |y|^4 = r^4.
 */
class SquircleShape(private val radius: Dp = GlassRadius) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline {
        val r = minOf(with(density) { radius.toPx() }, minOf(size.width, size.height) / 2f)
        val path = androidx.compose.ui.graphics.Path()
        // Corner centres, clockwise from the top right, with the quarter each one draws.
        val corners = listOf(
            Triple(size.width - r, r, 270.0), Triple(size.width - r, size.height - r, 0.0),
            Triple(r, size.height - r, 90.0), Triple(r, r, 180.0))
        corners.forEachIndexed { i, (cx, cy, start) ->
            for (step in 0..STEPS) {
                val t = Math.toRadians(start + 90.0 * step / STEPS)
                val cos = Math.cos(t)
                val sin = Math.sin(t)
                val x = cx + r * Math.signum(cos) * Math.sqrt(Math.abs(cos))
                val y = cy + r * Math.signum(sin) * Math.sqrt(Math.abs(sin))
                if (i == 0 && step == 0) path.moveTo(x.toFloat(), y.toFloat())
                else path.lineTo(x.toFloat(), y.toFloat())
            }
        }
        path.close()
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }

    private companion object {
        const val STEPS = 16
    }
}
