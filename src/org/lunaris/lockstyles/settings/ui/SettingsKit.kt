/*
 * SPDX-FileCopyrightText: 2026 Lunaris-AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.lockstyles.settings.ui

import android.app.WallpaperManager
import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lunaris.lockstyles.settings.LockSetting
import org.lunaris.lockstyles.settings.state

/*
 * The settings look: the lock screen wallpaper, blurred, behind glass cards. Rows in a group are
 * separate cards with tighter inner corners, as in Lunaris' expressive Settings.
 */

val Accent = Color(0xFFB9A7FF)
private val OnGlass = Color.White
private val OnGlassDim = Color(0xB3FFFFFF)
private val Glass = Color(0x1FFFFFFF)
private val GlassPressed = Color(0x33FFFFFF)
private val Hairline = Color(0x1AFFFFFF)
private const val OUTER = 26
private const val INNER = 6

/** The blurred lock screen wallpaper every settings page sits on. */
@Composable
fun GlassBackdrop(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val wallpaper by produceState<Bitmap?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val wm = WallpaperManager.getInstance(context)
                (wm.getDrawable(WallpaperManager.FLAG_LOCK) ?: wm.drawable)?.let { d ->
                    // Small is enough under this much blur, and much cheaper to blur.
                    val w = 270
                    val h = 600
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                        d.setBounds(0, 0, w, h)
                        d.draw(android.graphics.Canvas(it))
                    }
                }
            }.getOrNull()
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B10))) {
        wallpaper?.let {
            Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(60.dp))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color(0x99000000), Color(0xCC000000)))))
        content()
    }
}

/** A settings page: back button, large title, then [content]'s rows. */
@Composable
fun SettingsPage(title: String, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    GlassBackdrop {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp)) {
                    Box(
                        Modifier.padding(top = 8.dp).size(44.dp).clip(CircleShape)
                            .background(Glass).clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = OnGlass)
                    }
                    Text(title, color = OnGlass, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp, top = 28.dp, bottom = 16.dp))
                }
            }
            content()
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
            .background(Brush.verticalGradient(listOf(Color(0xE6000000), Color(0x99000000)))))
    }
}

/** A titled group of rows; each row is its own glass card. */
fun LazyListScope.group(title: String?, rows: List<@Composable (RowShape) -> Unit>) {
    if (rows.isEmpty()) return
    item { Group(title, rows) }
}

@Composable
fun Group(title: String?, rows: List<@Composable (RowShape) -> Unit>) {
    if (rows.isEmpty()) return
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (title != null) {
            Text(title, color = Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            rows.forEachIndexed { i, row ->
                row(RowShape(first = i == 0, last = i == rows.lastIndex))
            }
        }
    }
}

class RowShape(first: Boolean, last: Boolean) {
    val shape = RoundedCornerShape(
        topStart = (if (first) OUTER else INNER).dp, topEnd = (if (first) OUTER else INNER).dp,
        bottomStart = (if (last) OUTER else INNER).dp, bottomEnd = (if (last) OUTER else INNER).dp)
}

@Composable
private fun GlassRow(
    shape: RowShape,
    enabled: Boolean = true,
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier.fillMaxWidth().clip(shape.shape).background(Glass)
            .border(0.5.dp, Hairline, shape.shape)
            .then(if (onClick != null && enabled) Modifier.clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            } else Modifier)
            .alpha(if (enabled) 1f else 0.4f)
            .heightIn(min = 72.dp)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}

@Composable
private fun Labels(title: String, summary: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, color = OnGlass, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        if (!summary.isNullOrEmpty()) {
            Text(summary, color = OnGlassDim, fontSize = 13.sp, lineHeight = 17.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
fun glassSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color(0xFF1B1340), checkedTrackColor = Accent,
    checkedBorderColor = Accent, uncheckedThumbColor = Color(0xFFB0B0B8),
    uncheckedTrackColor = Color(0x33FFFFFF), uncheckedBorderColor = Color(0x55FFFFFF))

/** The on/off switch at the top of a page, as Singularity's MainSwitchPreference. */
fun LazyListScope.mainSwitch(title: String, setting: LockSetting<Boolean>,
        onChange: (Boolean) -> Unit = {}) {
    item {
        var on by setting.state()
        val bg by animateColorAsState(if (on) Accent.copy(alpha = 0.35f) else Glass,
            label = "main")
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
                .clip(RoundedCornerShape(OUTER.dp)).background(bg)
                .clickable { on = !on; onChange(on) }
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = OnGlass, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            Switch(on, { on = it; onChange(it) }, colors = glassSwitchColors())
        }
    }
}

fun switchRow(
    title: String,
    summary: String?,
    setting: LockSetting<Boolean>,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit = {},
): @Composable (RowShape) -> Unit = { shape ->
    var on by setting.state()
    GlassRow(shape, enabled, onClick = { on = !on; onChange(on) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Labels(title, summary, Modifier.weight(1f).padding(end = 12.dp))
            Switch(on, { on = it; onChange(it) }, enabled = enabled,
                colors = glassSwitchColors())
        }
    }
}

fun sliderRow(
    title: String,
    summary: String?,
    setting: LockSetting<Int>,
    range: IntRange,
    step: Int = 1,
    unit: String = "",
    enabled: Boolean = true,
): @Composable (RowShape) -> Unit = { shape ->
    var value by setting.state()
    // Written on release only; every tick would restart SystemUI's observers.
    var dragging by remember { mutableStateOf<Float?>(null) }
    GlassRow(shape, enabled, onClick = null) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = OnGlass, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${dragging?.roundToInt() ?: value}$unit", color = Accent,
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 12.dp))
            }
            if (!summary.isNullOrEmpty()) {
                Text(summary, color = OnGlassDim, fontSize = 13.sp, lineHeight = 17.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp))
            }
            val steps = ((range.last - range.first) / step - 1).coerceAtLeast(0)
            Slider(
                value = dragging ?: value.coerceIn(range).toFloat(),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { value = (it / step).roundToInt() * step }
                    dragging = null
                },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = if (steps in 1..60) steps else 0,
                enabled = enabled,
                colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent,
                    inactiveTrackColor = Color(0x33FFFFFF), activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent),
            )
        }
    }
}

/** A list preference: the current entry as the summary, the choices in a sheet. */
fun <T> choiceRow(
    title: String,
    setting: LockSetting<T>,
    entries: List<Pair<T, String>>,
    enabled: Boolean = true,
    onChange: (T) -> Unit = {},
): @Composable (RowShape) -> Unit = { shape ->
    var value by setting.state()
    ChoiceRow(shape, title, value, entries, enabled) { value = it; onChange(it) }
}

/** A choice kept by the caller rather than in a setting. */
fun <T> localChoiceRow(
    title: String,
    value: T,
    entries: List<Pair<T, String>>,
    enabled: Boolean = true,
    onChange: (T) -> Unit,
): @Composable (RowShape) -> Unit = { shape ->
    ChoiceRow(shape, title, value, entries, enabled, onChange)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(
    shape: RowShape,
    title: String,
    value: T,
    entries: List<Pair<T, String>>,
    enabled: Boolean,
    onChange: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    GlassRow(shape, enabled, onClick = { open = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Labels(title, entries.firstOrNull { it.first == value }?.second,
                Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = OnGlassDim)
        }
    }
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false },
            containerColor = Color(0xF21C1B22)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(title, color = OnGlass, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                entries.forEach { (v, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            onChange(v)
                            open = false
                        }.padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, color = OnGlass, fontSize = 17.sp,
                            modifier = Modifier.weight(1f))
                        if (v == value) Icon(Icons.Rounded.Check, null, tint = Accent)
                    }
                }
            }
        }
    }
}

fun navRow(
    title: String,
    summary: String?,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
): @Composable (RowShape) -> Unit = { shape ->
    GlassRow(shape, enabled, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Accent.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Accent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(16.dp))
            }
            Labels(title, summary, Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = OnGlassDim)
        }
    }
}

private val SWATCHES = listOf(0xFFFFFFFF, 0xFFB9A7FF, 0xFF0A84FF, 0xFF64D2FF, 0xFF30D158,
    0xFFFFD60A, 0xFFFF9F0A, 0xFFFF453A, 0xFFFF6482, 0xFFBF5AF2, 0xFF8E8E93, 0xFF1C1C1E)
    .map { it.toInt() }

/** A colour preference: swatches, or any colour as hex. */
@OptIn(ExperimentalMaterial3Api::class)
fun colorRow(
    title: String,
    setting: LockSetting<Int>,
    enabled: Boolean = true,
): @Composable (RowShape) -> Unit = { shape ->
    var value by setting.state()
    var open by remember { mutableStateOf(false) }
    GlassRow(shape, enabled, onClick = { open = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Labels(title, "#%08X".format(value), Modifier.weight(1f))
            Box(Modifier.size(32.dp).clip(CircleShape).background(Color(value))
                .border(1.dp, Color(0x55FFFFFF), CircleShape))
        }
    }
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false },
            containerColor = Color(0xF21C1B22)) {
            var hex by remember { mutableStateOf("%08X".format(value)) }
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, color = OnGlass, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                SWATCHES.chunked(6).forEach { line ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        line.forEach { c ->
                            Box(Modifier.size(44.dp).clip(CircleShape).background(Color(c))
                                .border(if (c == value) 3.dp else 1.dp,
                                    if (c == value) Accent else Color(0x55FFFFFF), CircleShape)
                                .clickable { value = c; hex = "%08X".format(c) })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#", color = OnGlassDim, fontSize = 20.sp)
                    BasicTextField(
                        value = hex,
                        onValueChange = { text ->
                            hex = text.uppercase().filter { it in "0123456789ABCDEF" }.take(8)
                            hex.takeIf { it.length == 6 || it.length == 8 }?.let {
                                value = (if (it.length == 6) "FF$it" else it).toLong(16).toInt()
                            }
                        },
                        singleLine = true,
                        textStyle = TextStyle(color = OnGlass, fontSize = 20.sp),
                        cursorBrush = SolidColor(Accent),
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                    )
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Color(value)))
                }
            }
        }
    }
}

fun footer(text: String): @Composable (RowShape) -> Unit = {
    Text(text, color = OnGlassDim, fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
}

/** A plain action row, e.g. "Extract now". */
fun actionRow(title: String, summary: String?, enabled: Boolean = true,
        onClick: () -> Unit): @Composable (RowShape) -> Unit = { shape ->
    GlassRow(shape, enabled, onClick = onClick) { Labels(title, summary) }
}

@Composable
fun Spacing() = Spacer(Modifier.height(8.dp))
