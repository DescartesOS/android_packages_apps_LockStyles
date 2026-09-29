/*
 * SPDX-FileCopyrightText: 2016-2026 crDroid Android Project
 * SPDX-FileCopyrightText: 2023-2024 The risingOS Android Project
 * SPDX-FileCopyrightText: 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.lockstyles.settings.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import org.lunaris.lockstyles.R
import org.lunaris.lockstyles.settings.Keys
import org.lunaris.lockstyles.settings.Lunaris
import org.lunaris.lockstyles.settings.LunarisImages
import org.lunaris.lockstyles.settings.state

/** The pages of the lock screen settings, as Singularity's fragments were. */
enum class Page { MAIN, CLOCK, WIDGETS, DEPTH, MEDIA_ART, PULSE, NOW_PLAYING, UDFPS }

/** Asks before restarting SystemUI, as Singularity's showSystemUiRestartDialog. */
val LocalAskRestart = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
fun LockSettings(start: Page = Page.MAIN, onExit: () -> Unit) {
    var stack by remember { mutableStateOf(listOf(start)) }
    // Opened again on another page, e.g. from Singularity: start over there.
    LaunchedEffect(start) { if (stack.first() != start) stack = listOf(start) }
    var askRestart by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val saved = rememberSaveableStateHolder()
    val backdrop = rememberBackdrop()
    val open: (Page) -> Unit = { stack = stack + it }
    val back: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) else onExit() }
    BackHandler(onBack = back)

    CompositionLocalProvider(LocalAskRestart provides { askRestart = true },
            LocalBackdrop provides backdrop) {
        AnimatedContent(
            targetState = stack,
            transitionSpec = {
                val forward = targetState.size >= initialState.size
                (slideInHorizontally(tween(320)) { if (forward) it / 4 else -it / 4 } +
                    fadeIn(tween(240))) togetherWith
                    (slideOutHorizontally(tween(320)) { if (forward) -it / 4 else it / 4 } +
                        fadeOut(tween(200)))
            },
            label = "page",
        ) { pages ->
            // Keyed by where it sits in the stack, so going back finds it as it was left.
            saved.SaveableStateProvider(pages.joinToString("/")) {
                when (pages.last()) {
                    Page.MAIN -> MainPage(open, back)
                    Page.CLOCK -> ClockPage(open, back)
                    Page.WIDGETS -> WidgetsPage(back)
                    Page.DEPTH -> DepthPage(back)
                    Page.MEDIA_ART -> MediaArtPage(back)
                    Page.PULSE -> PulsePage(back)
                    Page.NOW_PLAYING -> NowPlayingPage(back)
                    Page.UDFPS -> UdfpsPage(back)
                }
            }
        }
    }

    if (askRestart) {
        AlertDialog(
            onDismissRequest = { askRestart = false },
            containerColor = Color(0xF21C1B22),
            title = { Text(stringResource(R.string.systemui_restart_title)) },
            text = { Text(stringResource(R.string.systemui_restart_message)) },
            confirmButton = {
                TextButton({ askRestart = false; Lunaris.restartSystemUI(context) }) {
                    Text(stringResource(R.string.systemui_restart_yes))
                }
            },
            dismissButton = {
                TextButton({ askRestart = false }) {
                    Text(stringResource(R.string.systemui_restart_not_now))
                }
            },
        )
    }
}

/** A list preference's entries and values, zipped. */
@Composable
private fun options(entries: Int, values: Int): List<Pair<String?, String>> =
    stringArrayResource(values).toList().zip(stringArrayResource(entries).toList())

/** Strings for building rows, which happens outside composition. */
@Composable
private fun strings(): (Int) -> String {
    val context = LocalContext.current
    return { context.getString(it) }
}

@Composable
private fun MainPage(open: (Page) -> Unit, back: () -> Unit) {
    val s = strings()
    val context = LocalContext.current
    val askRestart = LocalAskRestart.current
    val smartspace by Keys.SMARTSPACE.state()
    val omniJaws = remember { Lunaris.isOmniJawsEnabled(context) }
    val weatherOn by Keys.WEATHER.state()
    val weatherAvailable = !smartspace && omniJaws
    val fingerprint = remember { Lunaris.hasFingerprint(context) }
    val vibrator = remember { Lunaris.hasVibrator(context) }

    SettingsPage(s(R.string.lock_screen_title), back) {
        group(s(R.string.qs_clock_title), listOf(
            navRow(s(R.string.ls_clock_title), s(R.string.ls_clock_summary),
                Icons.Rounded.Schedule) { open(Page.CLOCK) },
        ))
        group(s(R.string.lockscreen_interface_category), listOf(
            navRow(s(R.string.ls_media_art_title), s(R.string.ls_media_art_summary),
                Icons.Rounded.Image) { open(Page.MEDIA_ART) },
            switchRow(s(R.string.dynamic_bar_keyguard_music_pill),
                s(R.string.dynamic_bar_keyguard_music_pill_summary), Keys.MUSIC_PILL),
            navRow(s(R.string.pulse_settings_title), s(R.string.pulse_settings_summary),
                Icons.Rounded.GraphicEq) { open(Page.PULSE) },
            navRow(s(R.string.nowplaying_title), s(R.string.nowplaying_summary),
                Icons.Rounded.MusicNote) { open(Page.NOW_PLAYING) },
            switchRow(s(R.string.lockscreen_battery_info_title),
                s(R.string.lockscreen_battery_info_summary), Keys.BATTERY_INFO),
            switchRow(s(R.string.lockscreen_charging_time_title),
                s(R.string.lockscreen_charging_time_summary), Keys.CHARGING_TIME),
            switchRow(s(R.string.lockscreen_emergency_button_title),
                s(R.string.lockscreen_emergency_button_summary), Keys.EMERGENCY_BUTTON),
            switchRow(s(R.string.keyguard_affordance_single_tap_title),
                s(R.string.keyguard_affordance_single_tap_summary), Keys.AFFORDANCE_SINGLE_TAP),
            switchRow(s(R.string.kg_user_switcher_enabled_title),
                s(R.string.kg_user_switcher_enabled_summary), Keys.USER_SWITCHER) {
                askRestart()
            },
            switchRow(s(R.string.keyguard_scrim_transparent_title),
                s(R.string.keyguard_scrim_transparent_summary), Keys.SCRIM_TRANSPARENT) {
                askRestart()
            },
        ))
        group(s(R.string.lockscreen_weather_category), listOf(
            switchRow(s(R.string.lockscreen_smartspace_title),
                s(R.string.lockscreen_smartspace_summary), Keys.SMARTSPACE) { askRestart() },
            navRow(s(R.string.weather_settings_title), s(R.string.weather_settings_summary),
                enabled = Lunaris.isInstalled(context, "org.omnirom.omnijaws")) {
                runCatching { context.startActivity(Lunaris.omniJawsSettings()) }
            },
            switchRow(s(R.string.lockscreen_weather_title),
                if (weatherAvailable) s(R.string.lockscreen_weather_summary)
                else s(R.string.lockscreen_weather_enabled_info),
                Keys.WEATHER, enabled = weatherAvailable) { askRestart() },
            switchRow(s(R.string.lockscreen_weather_location_title),
                s(R.string.lockscreen_weather_location_summary), Keys.WEATHER_LOCATION,
                enabled = weatherOn),
            switchRow(s(R.string.lockscreen_weather_text_title),
                s(R.string.lockscreen_weather_text_summary), Keys.WEATHER_TEXT,
                enabled = weatherOn),
            switchRow(s(R.string.lockscreen_weather_wind_info_title),
                s(R.string.lockscreen_weather_wind_info_summary), Keys.WEATHER_WIND,
                enabled = weatherOn),
            switchRow(s(R.string.lockscreen_weather_humidity_info_title),
                s(R.string.lockscreen_weather_humidity_info_summary), Keys.WEATHER_HUMIDITY,
                enabled = weatherOn),
        ))
        group(s(R.string.gestures_title), buildList {
            add(switchRow(s(R.string.lockscreen_doubletap_sleep_title),
                s(R.string.lockscreen_doubletap_sleep_summary), Keys.DOUBLE_TAP_SLEEP))
            if (fingerprint && vibrator) {
                add(switchRow(s(R.string.fp_success_vibrate_title),
                    s(R.string.fp_success_vibrate_summary), Keys.FP_SUCCESS_VIBRATE))
                add(switchRow(s(R.string.fp_error_vibrate_title),
                    s(R.string.fp_error_vibrate_summary), Keys.FP_ERROR_VIBRATE))
            }
            if (fingerprint) {
                add(switchRow(s(R.string.enable_fingerprint_ripple_effect_title),
                    s(R.string.enable_fingerprint_ripple_effect_summary), Keys.FP_RIPPLE))
            }
        })
        if (Lunaris.hasCustomUdfps()) {
            group(s(R.string.udfps_custom_title), listOf(
                navRow(s(R.string.udfps_cust_title), s(R.string.udfps_cust_summary),
                    Icons.Rounded.Fingerprint) { open(Page.UDFPS) },
            ))
        }
        group(s(R.string.misc_title), listOf(
            switchRow(s(R.string.lockscreen_enable_power_menu_title),
                s(R.string.lockscreen_enable_power_menu_summary), Keys.POWER_MENU),
        ))
        group(s(R.string.credits_title), CREDITS.map { (name, role, url) ->
            navRow(name, s(role)) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        })
    }
}

/** The people this app is built on. */
private val CREDITS = listOf(
    Triple("Ayan Biswas (not-ayan)", R.string.credits_wallwidgy,
        "https://github.com/not-ayan/wallwidgy"),
    Triple("Sushmit Rathore (redducc)", R.string.credits_gallery, "https://github.com/redducc"),
    Triple("Ghosuto", R.string.credits_singularity, "https://github.com/Ghosuto"),
    Triple("rmp22", R.string.credits_ax_blur, "https://github.com/rmp22"),
)

@Composable
private fun ClockPage(open: (Page) -> Unit, back: () -> Unit) {
    val s = strings()
    val context = LocalContext.current
    val gradient by Keys.CLOCK_GRADIENT.state()
    val aodImage by Keys.AOD_IMAGE.state()
    var aodImageUri by Keys.AOD_IMAGE_URI.state()
    val pickAodImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        LunarisImages.save(context, uri, "lockscreen_aod_image", "LOCKSCREEN_CUSTOM_AOD_IMAGE")
            ?.let { aodImageUri = it }
    }
    val colorModes = options(R.array.lockscreen_custom_clock_color_mode_entries,
        R.array.lockscreen_custom_clock_color_mode_values)

    SettingsPage(s(R.string.ls_clock_title), back) {
        item { ClockStylePicker() }
        item { ClockFontPicker() }
        group(null, listOf(
            navRow(s(R.string.lockscreen_widgets_title),
                s(R.string.lockscreen_widgets_enabled_summary), Icons.Rounded.Widgets) {
                open(Page.WIDGETS)
            },
            navRow(s(R.string.depthwall_wallpaper_enabled_title),
                s(R.string.depthwall_wallpaper_enabled_summary), Icons.Rounded.Layers) {
                open(Page.DEPTH)
            },
            switchRow(s(R.string.custom_clock_weather_title),
                s(R.string.custom_clock_weather_summary), Keys.CLOCK_WEATHER),
            switchRow(s(R.string.lockscreen_custom_image_enabled_title),
                s(R.string.lockscreen_custom_image_summary), Keys.AOD_IMAGE),
            actionRow(s(R.string.lockscreen_custom_image_title),
                aodImageUri ?: s(R.string.lockscreen_custom_image_pick_summary),
                enabled = aodImage) {
                pickAodImage.launch(PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        ))
        group(s(R.string.lockscreen_custom_clock_color_category_title), listOf(
            switchRow(s(R.string.clock_album_art_color_title),
                s(R.string.clock_album_art_color_summary), Keys.CLOCK_ALBUM_ART_COLOR),
            choiceRow(s(R.string.lockscreen_custom_clock_color_mode_title),
                Keys.CLOCK_COLOR_MODE, colorModes),
            colorRow(s(R.string.custom_color), Keys.CLOCK_CUSTOM_COLOR),
        ))
        group(s(R.string.lockscreen_custom_clock_gradient_category_title), listOf(
            switchRow(s(R.string.lockscreen_custom_clock_gradient_enabled_title),
                s(R.string.lockscreen_custom_clock_gradient_enabled_summary), Keys.CLOCK_GRADIENT),
            colorRow(s(R.string.lockscreen_custom_clock_gradient_start_title),
                Keys.CLOCK_GRADIENT_START, enabled = gradient),
            colorRow(s(R.string.lockscreen_custom_clock_gradient_end_title),
                Keys.CLOCK_GRADIENT_END, enabled = gradient),
            sliderRow(s(R.string.lockscreen_custom_clock_gradient_anchor_y_title),
                s(R.string.lockscreen_custom_clock_gradient_anchor_y_summary),
                Keys.CLOCK_GRADIENT_ANCHOR_Y, 0..100, unit = "%", enabled = gradient),
            sliderRow(s(R.string.lockscreen_custom_clock_gradient_radius_title),
                s(R.string.lockscreen_custom_clock_gradient_radius_summary),
                Keys.CLOCK_GRADIENT_RADIUS, 25..200, unit = "%", enabled = gradient),
        ))
        group(s(R.string.lockscreen_custom_clock_layout_category_title), listOf(
            sliderRow(s(R.string.clock_size_title), s(R.string.clock_size_summary),
                Keys.CLOCK_SIZE, 50..150, unit = "%"),
            sliderRow(s(R.string.lockscreen_custom_clock_opacity_title),
                s(R.string.lockscreen_custom_clock_opacity_summary), Keys.CLOCK_OPACITY,
                0..100, unit = "%"),
            sliderRow(s(R.string.lockscreen_custom_clock_frame_margin_top_title),
                s(R.string.lockscreen_custom_clock_frame_margin_top_summary),
                Keys.CLOCK_MARGIN_TOP, 0..200, unit = " " + s(R.string.unit_pixels)),
            sliderRow(s(R.string.lockscreen_custom_clock_frame_margin_start_title),
                s(R.string.lockscreen_custom_clock_frame_margin_start_summary),
                Keys.CLOCK_MARGIN_START, -200..200, unit = " " + s(R.string.unit_pixels)),
        ))
        group(s(R.string.lockscreen_custom_clock_animation_category_title), listOf(
            switchRow(s(R.string.lock_screen_custom_clock_wobble_on_charge_title),
                s(R.string.lock_screen_custom_clock_wobble_on_charge_summary), Keys.CLOCK_WOBBLE),
            switchRow(s(R.string.lockscreen_custom_clock_aod_anim_title),
                s(R.string.lockscreen_custom_clock_aod_anim_summary), Keys.CLOCK_AOD_ANIM),
            footer(s(R.string.lockscreen_custom_clock_style_info)),
        ))
    }
}

@Composable
private fun WidgetsPage(back: () -> Unit) {
    val s = strings()
    val info by Keys.INFO_WIDGETS.state()
    val widgets by Keys.WIDGETS.state()
    SettingsPage(s(R.string.lockscreen_widgets_title), back) {
        group(null, listOf(
            switchRow(s(R.string.lockscreen_display_widgets_title),
                s(R.string.lockscreen_display_widgets_summary), Keys.INFO_WIDGETS),
        ))
        item {
            WidgetColorGroup(options(R.array.lockscreen_custom_clock_color_mode_entries,
                R.array.lockscreen_custom_clock_color_mode_values), info)
        }
        group(null, listOf(
            switchRow(s(R.string.lockscreen_widgets_enabled_title),
                s(R.string.lockscreen_widgets_enabled_summary), Keys.WIDGETS),
        ))
        item { WidgetSlots(widgets) }
    }
}

@Composable
private fun DepthPage(back: () -> Unit) {
    val s = strings()
    val context = LocalContext.current
    val depth by Keys.DEPTH.state()
    var subject by Keys.DEPTH_SUBJECT.state()
    val pickSubject = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        LunarisImages.save(context, uri, "depthwallpaper", "DEPTH_WALLPAPER_SUBJECT")
            ?.let { subject = it }
    }
    var confirmClear by remember { mutableStateOf(false) }
    SettingsPage(s(R.string.depthwall_wallpaper_enabled_title), back) {
        group(null, listOf(
            switchRow(s(R.string.depthwall_wallpaper_enabled_title),
                s(R.string.depthwall_wallpaper_enabled_summary), Keys.DEPTH),
            actionRow(s(R.string.depthwall_extract_now_title),
                s(R.string.depthwall_extract_now_summary), enabled = depth) {
                if (!DepthExtractor.extractNow(context)) {
                    Toast.makeText(context, R.string.depthwall_extract_unavailable,
                        Toast.LENGTH_LONG).show()
                }
            },
            switchRow(s(R.string.depthwall_auto_subject_title),
                s(R.string.depthwall_auto_subject_summary), Keys.DEPTH_AUTO_SUBJECT,
                enabled = depth),
            actionRow(s(R.string.depthwall_file_header_select_title),
                subject ?: s(R.string.depthwall_file_header_select_summary), enabled = depth) {
                pickSubject.launch(PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            actionRow(s(R.string.depthwall_clear_subject_title),
                s(R.string.depthwall_clear_subject_summary),
                enabled = depth && subject != null) { confirmClear = true },
        ))
        group(null, listOf(
            sliderRow(s(R.string.depthwall_wallpaper_opacity_title), null, Keys.DEPTH_OPACITY,
                1..100, unit = "%"),
            sliderRow(s(R.string.depth_wallpaper_bottom_fade_title),
                s(R.string.depth_wallpaper_bottom_fade_summary), Keys.DEPTH_BOTTOM_FADE, 0..600),
            sliderRow(s(R.string.depth_wallpaper_fade_curve_title),
                s(R.string.depth_wallpaper_fade_curve_summary), Keys.DEPTH_FADE_CURVE, 0..100,
                unit = "%"),
            sliderRow(s(R.string.depth_wallpaper_bottom_inset_title),
                s(R.string.depth_wallpaper_bottom_inset_summary), Keys.DEPTH_BOTTOM_INSET,
                0..400, unit = " dp"),
            footer(s(R.string.depthwall_footer_text)),
        ))
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Color(0xF21C1B22),
            title = { Text(s(R.string.depthwall_clear_subject_title)) },
            text = { Text(s(R.string.depthwall_clear_subject_confirm_message)) },
            confirmButton = {
                TextButton({ confirmClear = false; subject = null }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton({ confirmClear = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun MediaArtPage(back: () -> Unit) {
    val s = strings()
    val on by Keys.MEDIA_ART.state()
    val aod by Keys.MEDIA_ART_AOD.state()
    val filters = options(R.array.lockscreen_media_art_filter_entries,
        R.array.lockscreen_media_art_filter_values)
    SettingsPage(s(R.string.ls_media_art_title), back) {
        mainSwitch(s(R.string.enable), Keys.MEDIA_ART)
        group(null, listOf(
            switchRow(s(R.string.show_on_aod_title), s(R.string.show_on_aod_summary),
                Keys.MEDIA_ART_AOD, enabled = on),
            sliderRow(s(R.string.aod_brightness_title), null, Keys.MEDIA_ART_AOD_DIM, 0..100,
                unit = "%", enabled = on && aod),
            choiceRow(s(R.string.lockscreen_media_art_filter_title), Keys.MEDIA_ART_FILTER,
                filters, enabled = on),
            sliderRow(s(R.string.lockscreen_media_art_pixel_size_title),
                s(R.string.lockscreen_media_art_pixel_size_summary), Keys.MEDIA_ART_PIXEL_SIZE,
                5..45, enabled = on),
            sliderRow(s(R.string.lockscreen_media_art_fade_level_title),
                s(R.string.lockscreen_media_art_fade_level_summary), Keys.MEDIA_ART_FADE, 0..100,
                enabled = on),
            sliderRow(s(R.string.lockscreen_media_art_blur_level_title),
                s(R.string.lockscreen_media_art_blur_level_summary), Keys.MEDIA_ART_BLUR, 0..200,
                enabled = on),
        ))
    }
}

@Composable
private fun PulsePage(back: () -> Unit) {
    val s = strings()
    val on by Keys.PULSE.state()
    val haptics = options(R.array.pulse_bass_haptics_entries, R.array.pulse_bass_haptics_values)
    val renderers = options(R.array.pulse_renderer_entries, R.array.pulse_renderer_values)
    val capture = options(R.array.pulse_capture_mode_entries, R.array.pulse_capture_mode_values)
    val colors = options(R.array.pulse_color_entries, R.array.pulse_color_values)
    SettingsPage(s(R.string.pulse_settings_title), back) {
        mainSwitch(s(R.string.enable), Keys.PULSE)
        group(null, listOf(
            switchRow(s(R.string.pulse_show_on_ambient_title),
                s(R.string.pulse_show_on_ambient_summary), Keys.PULSE_AMBIENT, enabled = on),
            choiceRow(s(R.string.pulse_bass_haptics_title), Keys.PULSE_BASS_HAPTICS, haptics,
                enabled = on),
            choiceRow(s(R.string.pulse_renderer), Keys.PULSE_RENDERER, renderers, enabled = on),
            choiceRow(s(R.string.pulse_capture_mode_title), Keys.PULSE_CAPTURE_MODE, capture,
                enabled = on),
            switchRow(s(R.string.pulse_rounded_bars_title), s(R.string.pulse_rounded_bars_summary),
                Keys.PULSE_ROUNDED, enabled = on),
            choiceRow(s(R.string.pulse_color), Keys.PULSE_COLOR, colors, enabled = on),
            colorRow(s(R.string.custom_color), Keys.PULSE_CUSTOM_COLOR, enabled = on),
            sliderRow(s(R.string.pulse_bar_count_title), s(R.string.pulse_bar_count_summary),
                Keys.PULSE_BAR_COUNT, 8..64, step = 4,
                unit = " " + s(R.string.pulse_bar_count_units), enabled = on),
            sliderRow(s(R.string.pulse_height_multiplier_title),
                s(R.string.pulse_height_multiplier_summary), Keys.PULSE_HEIGHT, 25..200, step = 5,
                unit = "%", enabled = on),
        ))
    }
}

@Composable
private fun NowPlayingPage(back: () -> Unit) {
    val s = strings()
    val on by Keys.NOW_PLAYING.state()
    val iconStyles = options(R.array.nowplaying_icon_style_entries,
        R.array.nowplaying_icon_style_values)
    val colorModes = options(R.array.nowplaying_color_mode_entries,
        R.array.nowplaying_color_mode_values)
    SettingsPage(s(R.string.nowplaying_title), back) {
        mainSwitch(s(R.string.enable), Keys.NOW_PLAYING)
        group(null, listOf(
            choiceRow(s(R.string.nowplaying_icon_style_title), Keys.NP_ICON_STYLE, iconStyles,
                enabled = on),
            sliderRow(s(R.string.nowplaying_icon_size_title),
                s(R.string.nowplaying_icon_size_summary), Keys.NP_ICON_SIZE, 5..40, unit = " dp",
                enabled = on),
            switchRow(s(R.string.nowplaying_lyrics_mode_title),
                s(R.string.nowplaying_lyrics_mode_summary), Keys.NP_LYRICS, enabled = on),
            switchRow(s(R.string.nowplaying_compact_style_title),
                s(R.string.nowplaying_compact_style_summary), Keys.NP_COMPACT, enabled = on),
            switchRow(s(R.string.nowplaying_expand_dialog),
                s(R.string.nowplaying_expand_dialog_summary), Keys.NP_TAP_EXPAND, enabled = on),
            choiceRow(s(R.string.nowplaying_color_mode_title), Keys.NP_COLOR_MODE, colorModes),
            sliderRow(s(R.string.nowplaying_vertical_position_title),
                s(R.string.nowplaying_vertical_position_summary), Keys.NP_VERTICAL, 10..100,
                unit = "%", enabled = on),
            sliderRow(s(R.string.nowplaying_track_text_size_title),
                s(R.string.nowplaying_track_text_size_summary), Keys.NP_TRACK_SIZE, 8..24,
                unit = " sp", enabled = on),
            sliderRow(s(R.string.nowplaying_artist_text_size_title),
                s(R.string.nowplaying_artist_text_size_summary), Keys.NP_ARTIST_SIZE, 5..20,
                unit = " sp", enabled = on),
        ))
        group(s(R.string.nowplaying_display_title), listOf(
            switchRow(s(R.string.nowplaying_show_on_lockscreen_title),
                s(R.string.nowplaying_show_on_lockscreen_summary), Keys.NP_ON_LOCKSCREEN,
                enabled = on),
            switchRow(s(R.string.nowplaying_show_on_aod_title),
                s(R.string.nowplaying_show_on_aod_summary), Keys.NP_ON_AOD, enabled = on),
            footer(s(R.string.nowplaying_info_summary)),
        ))
    }
}

@Composable
private fun UdfpsPage(back: () -> Unit) {
    val s = strings()
    val context = LocalContext.current
    val type by Keys.UDFPS_ICON_TYPE.state()
    var customIcon by Keys.UDFPS_CUSTOM_ICON.state()
    val types = options(R.array.udfps_icon_type_entries, R.array.udfps_icon_type_values)
    val pickIcon = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        LunarisImages.save(context, uri, "udfps", "UDFPS_CUSTOM_ICON")?.let { customIcon = it }
    }
    SettingsPage(s(R.string.udfps_cust_title), back) {
        group(null, listOf(
            choiceRow(s(R.string.udfps_icon_type_title), Keys.UDFPS_ICON_TYPE, types),
            switchRow(s(R.string.udfps_icon_aod_title), s(R.string.udfps_icon_aod_summary),
                Keys.UDFPS_ICON_AOD),
            actionRow(s(R.string.udfps_custom_icon_select_title),
                if (customIcon != null) s(R.string.udfps_custom_icon_selected)
                else s(R.string.udfps_custom_icon_select_summary), enabled = type == "1") {
                pickIcon.launch(PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        ))
        item {
            UdfpsResourcePicker(s(R.string.udfps_icon_picker_title), UdfpsPacks.ICONS,
                Keys.UDFPS_ICON, enabled = type == "0")
        }
        item {
            UdfpsResourcePicker(s(R.string.udfps_recog_animation_effect_title),
                UdfpsPacks.ANIMATIONS, Keys.UDFPS_ANIM_STYLE, enabled = true)
        }
        group(null, listOf(
            sliderRow(s(R.string.udfps_animation_size_title),
                s(R.string.udfps_animation_size_summary), Keys.UDFPS_ANIM_SIZE, 10..100,
                unit = "%"),
            sliderRow(s(R.string.udfps_animation_offset_title),
                s(R.string.udfps_animation_offset_summary), Keys.UDFPS_ANIM_OFFSET, -50..50,
                unit = " dp"),
            footer(s(R.string.udfps_custom_footer_text)),
        ))
    }
}
