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
                // ax_blur's 34dp backdrop radius, at this bitmap's scale.
                val radius = with(density) { 34.dp.toPx() } / SCALE
                val blurred = GalleryApplier.blur(small, radius).copy(Bitmap.Config.ARGB_8888, true)
                // ax_blur's surface: the wallpaper's accent2, dark, over the blur.
                val tint = context.getColor(android.R.color.system_accent2_900)
                Canvas(blurred).drawColor((tint and 0x00FFFFFF) or (0x8A shl 24))
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
uniform float4 radii;       // top-left, top-right, bottom-right, bottom-left
uniform float refraction;   // how far the rim bends the backdrop, px
uniform float depth;        // how deep the lens reaches in from the rim, px
uniform float dispersion;   // colour split at the rim
uniform half4 tint;         // premultiplied
uniform float rim;          // edge light strength

float box(float2 p, float2 b, float4 r) {
    float rr = p.x > 0.0 ? (p.y > 0.0 ? r.z : r.y) : (p.y > 0.0 ? r.w : r.x);
    float2 q = abs(p) - b + rr;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - rr;
}

half4 main(float2 p) {
    float2 half_ = size * 0.5;
    float2 c = p - half_;
    float sd = box(c, half_, radii);
    if (sd > 1.0) return half4(0.0);

    float2 n = float2(box(c + float2(1.0, 0.0), half_, radii) - box(c - float2(1.0, 0.0), half_, radii),
                      box(c + float2(0.0, 1.0), half_, radii) - box(c - float2(0.0, 1.0), half_, radii));
    n = n / max(length(n), 1e-4);

    // A convex slab: flat in the middle, bending harder towards the rim.
    float t = clamp(-sd / depth, 0.0, 1.0);
    float bend = (1.0 - t) * (1.0 - t) * (1.0 - t);
    float2 at = origin + p - n * refraction * bend;
    float2 split = n * refraction * bend * dispersion;
    half3 col = half3(backdrop.eval(at - split).r, backdrop.eval(at).g,
                      backdrop.eval(at + split).b);

    // Glass lifts what's behind it a little.
    half luma = dot(col, half3(0.2126, 0.7152, 0.0722));
    col = mix(half3(luma), col, 1.3) * 1.08;
    col = col * (1.0 - tint.a) + tint.rgb;

    // Light from the top left catches the rim; the far side gets a softer glint.
    float edge = smoothstep(3.0, 0.0, -sd);
    float facing = dot(n, normalize(float2(-0.55, -0.85)));
    float glint = edge * (0.25 + 0.75 * max(facing, 0.0) + 0.35 * max(-facing, 0.0));
    // A faint sheen over the top of the surface.
    float sheen = (1.0 - smoothstep(0.0, size.y * 0.6, p.y)) * 0.06;
    col += half3(rim * glint + sheen);

    float a = clamp(0.5 - sd, 0.0, 1.0);
    return half4(col * a, a);
}
"""

/**
 * Draws this element as liquid glass over [LocalBackdrop], with per-corner radii. Without a
 * backdrop yet, it falls back to a plain translucent fill.
 */
fun Modifier.liquidGlass(
    topStart: Dp,
    topEnd: Dp = topStart,
    bottomEnd: Dp = topStart,
    bottomStart: Dp = topStart,
    refraction: Dp = 10.dp,
    depth: Dp = 18.dp,
    tint: Int = 0x14FFFFFF,
    rim: Float = 0.45f,
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
                    setFloatUniform("radii", topStart.toPx(), topEnd.toPx(), bottomEnd.toPx(),
                        bottomStart.toPx())
                    setFloatUniform("refraction", refraction.toPx())
                    setFloatUniform("depth", depth.toPx())
                }
                setFloatUniform("dispersion", 0.18f)
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
