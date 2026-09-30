/*
 * SPDX-FileCopyrightText: 2024-2025 Lunaris AOSP
 * SPDX-FileCopyrightText: 2026 Lunaris-AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.lockstyles.settings.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.lunaris.lockstyles.settings.LockSetting
import org.lunaris.lockstyles.settings.state

/*
 * The settings look follows LunarisDolby's Material 3 Expressive: groups are extra-large
 * cards holding tonal tiles, a tile turns primary when it's on, and everything gives a little
 * under the finger. The wallpaper shows faintly, blurred, behind it all.
 */

/** The theme's primary colour, for the few places that colour by hand. */
val Accent: Color
    @Composable get() = MaterialTheme.colorScheme.primary

/** Shrinks a little while pressed and springs back, as LunarisDolby's squishable. */
@Composable
fun Modifier.squishable(interaction: MutableInteractionSource, enabled: Boolean = true): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.96f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium), label = "squish")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** The tile look for picker items: tonal, or primary with an outline when chosen. */
@Composable
fun Modifier.pickTile(on: Boolean): Modifier {
    val shape = MaterialTheme.shapes.large
    val colors = MaterialTheme.colorScheme
    return clip(shape)
        .background(if (on) colors.primaryContainer else colors.surfaceVariant.copy(alpha = 0.6f))
        .border(if (on) 2.dp else 0.dp, if (on) colors.primary else Color.Transparent, shape)
}

/** The page every settings screen sits on: the surface, over a faint blurred wallpaper. */
@Composable
fun GlassBackdrop(content: @Composable () -> Unit) {
    val backdrop = LocalBackdrop.current ?: rememberBackdrop()
    val surface = MaterialTheme.colorScheme.surface
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().background(surface)) {
            backdrop?.let {
                Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.55f))
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                listOf(surface.copy(alpha = 0.55f), surface.copy(alpha = 0.85f)))))
            content()
        }
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
                    FilledTonalIconButton(onClick = onBack,
                        modifier = Modifier.padding(top = 8.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
                    }
                    Text(title, style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 16.dp))
                }
            }
            content()
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)))
    }
}

/** A titled group of rows, as one extra-large card. */
fun LazyListScope.group(title: String?, rows: List<@Composable (RowShape) -> Unit>) {
    if (rows.isEmpty()) return
    item { Group(title, rows) }
}

@Composable
fun Group(title: String?, rows: List<@Composable (RowShape) -> Unit>) {
    if (rows.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.9f)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 2.dp))
            }
            rows.forEachIndexed { i, row ->
                row(RowShape(first = i == 0, last = i == rows.lastIndex))
            }
        }
    }
}

/** Where a row sits in its group. */
class RowShape(val first: Boolean, val last: Boolean)

/** One tonal tile in a group; primary-tinted while [highlighted]. */
@Composable
private fun Tile(
    enabled: Boolean = true,
    highlighted: Boolean = false,
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val colors = MaterialTheme.colorScheme
    val color by animateColorAsState(
        if (highlighted) colors.primaryContainer.copy(alpha = 0.5f)
        else colors.surfaceVariant.copy(alpha = 0.6f), label = "tile")
    Surface(
        shape = MaterialTheme.shapes.large,
        color = color,
        modifier = Modifier.fillMaxWidth()
            .squishable(interaction, enabled && onClick != null)
            .clip(MaterialTheme.shapes.large)
            .then(if (onClick != null && enabled) Modifier.clickable(interaction, null) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            } else Modifier)
            .alpha(if (enabled) 1f else 0.4f),
    ) {
        Box(Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart) { content() }
    }
}

@Composable
private fun Labels(title: String, summary: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface)
        if (!summary.isNullOrEmpty()) {
            Text(summary, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A switch with a check or cross in its thumb, as LunarisDolby's. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveSwitch(checked: Boolean, enabled: Boolean = true,
        onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        thumbContent = {
            Crossfade(checked, animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
                label = "thumb") { on ->
                Icon(if (on) Icons.Rounded.Check else Icons.Rounded.Close, null,
                    modifier = Modifier.size(16.dp))
            }
        },
    )
}

/** The on/off switch at the top of a page, as Singularity's MainSwitchPreference. */
fun LazyListScope.mainSwitch(title: String, setting: LockSetting<Boolean>,
        onChange: (Boolean) -> Unit = {}) {
    item {
        var on by setting.state()
        val interaction = remember { MutableInteractionSource() }
        val colors = MaterialTheme.colorScheme
        val color by animateColorAsState(if (on) colors.primaryContainer else colors.surfaceBright,
            label = "main")
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = color,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
                .squishable(interaction)
                .clip(MaterialTheme.shapes.extraLarge)
                .clickable(interaction, null) { on = !on; onChange(on) },
        ) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (on) colors.onPrimaryContainer else colors.onSurface,
                    modifier = Modifier.weight(1f))
                ExpressiveSwitch(on) { on = it; onChange(it) }
            }
        }
    }
}

fun switchRow(
    title: String,
    summary: String?,
    setting: LockSetting<Boolean>,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit = {},
): @Composable (RowShape) -> Unit = {
    var on by setting.state()
    Tile(enabled, highlighted = on, onClick = { on = !on; onChange(on) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Labels(title, summary, Modifier.weight(1f).padding(end = 12.dp))
            ExpressiveSwitch(on, enabled) { on = it; onChange(it) }
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
): @Composable (RowShape) -> Unit = {
    var value by setting.state()
    // Written on release only; every tick would restart SystemUI's observers.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val haptics = LocalHapticFeedback.current
    Tile(enabled, onClick = null) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Surface(shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(start = 12.dp)) {
                    Text("${dragging?.roundToInt() ?: value}$unit",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }
            }
            if (!summary.isNullOrEmpty()) {
                Text(summary, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp))
            }
            val steps = ((range.last - range.first) / step - 1).coerceAtLeast(0)
            Slider(
                value = dragging ?: value.coerceIn(range).toFloat(),
                onValueChange = {
                    if (dragging?.roundToInt() != it.roundToInt()) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    }
                    dragging = it
                },
                onValueChangeFinished = {
                    dragging?.let { value = (it / step).roundToInt() * step }
                    dragging = null
                },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = if (steps in 1..60) steps else 0,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant),
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
): @Composable (RowShape) -> Unit = {
    var value by setting.state()
    ChoiceRow(title, value, entries, enabled) { value = it; onChange(it) }
}

/** A choice kept by the caller rather than in a setting. */
fun <T> localChoiceRow(
    title: String,
    value: T,
    entries: List<Pair<T, String>>,
    enabled: Boolean = true,
    onChange: (T) -> Unit,
): @Composable (RowShape) -> Unit = {
    ChoiceRow(title, value, entries, enabled, onChange)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(
    title: String,
    value: T,
    entries: List<Pair<T, String>>,
    enabled: Boolean,
    onChange: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Tile(enabled, onClick = { open = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Labels(title, entries.firstOrNull { it.first == value }?.second, Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp))
                entries.forEach { (v, label) ->
                    val on = v == value
                    Tile(highlighted = on, onClick = { onChange(v); open = false }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label, style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f))
                            if (on) Icon(Icons.Rounded.Check, null,
                                tint = MaterialTheme.colorScheme.primary)
                        }
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
): @Composable (RowShape) -> Unit = {
    Tile(enabled, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Surface(Modifier.size(40.dp), shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.width(16.dp))
            }
            Labels(title, summary, Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
): @Composable (RowShape) -> Unit = {
    var value by setting.state()
    var open by remember { mutableStateOf(false) }
    Tile(enabled, onClick = { open = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Labels(title, "#%08X".format(value), Modifier.weight(1f))
            Box(Modifier.size(32.dp).clip(CircleShape).background(Color(value))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
        }
    }
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            var hex by remember { mutableStateOf("%08X".format(value)) }
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                SWATCHES.chunked(6).forEach { line ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        line.forEach { c ->
                            Box(Modifier.size(44.dp).clip(CircleShape).background(Color(c))
                                .border(if (c == value) 3.dp else 1.dp,
                                    if (c == value) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clickable { value = c; hex = "%08X".format(c) })
                        }
                    }
                }
                Surface(shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("#", style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        BasicTextField(
                            value = hex,
                            onValueChange = { text ->
                                hex = text.uppercase().filter { it in "0123456789ABCDEF" }.take(8)
                                hex.takeIf { it.length == 6 || it.length == 8 }?.let {
                                    value = (if (it.length == 6) "FF$it" else it).toLong(16)
                                        .toInt()
                                }
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f).padding(start = 4.dp),
                        )
                        Box(Modifier.size(36.dp).clip(CircleShape).background(Color(value)))
                    }
                }
            }
        }
    }
}

fun footer(text: String): @Composable (RowShape) -> Unit = {
    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp).padding(top = 2.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A plain action row, e.g. "Extract now". */
fun actionRow(title: String, summary: String?, enabled: Boolean = true,
        onClick: () -> Unit): @Composable (RowShape) -> Unit = {
    Tile(enabled, onClick = onClick) { Labels(title, summary) }
}

@Composable
fun Spacing() = Spacer(Modifier.height(8.dp))
