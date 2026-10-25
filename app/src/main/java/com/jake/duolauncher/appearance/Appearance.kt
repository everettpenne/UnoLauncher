package com.jake.duolauncher.appearance

import com.jake.duolauncher.island.*

import com.jake.duolauncher.*
import android.content.Context
import androidx.compose.runtime.*
import java.time.*
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

enum class AppearanceMode { LIGHT, DARK, SYSTEM, SUNRISE_SUNSET }

/**
 * How Home is laid out on a phone-sized screen. [SIDE] is Uno's own: the dock is a column of four apps on the right, under the status rail,
 * beside the grid. [IOS] is the iPhone's: a strict grid scaled from the iPhone's measurements, widgets only at its three sizes, and the dock a
 * glass slab of four apps along the bottom with a Search pill above it. The inner screen of a foldable keeps the side layout either way.
 */
enum class HomeStyle { SIDE, IOS }
data class AppearanceState(val mode: AppearanceMode = AppearanceMode.LIGHT, val place: String = "",
    val latitude: Double? = null, val longitude: Double? = null, val locationTime: Long = 0,
    val deviceLocation: Boolean = false, val dark: Boolean = false, val fallback: String? = null,
    val locationStatus: String? = null, val liquidGlass: Boolean = true, val island: Boolean = true,
    /** The launcher's accent: Uno orange, or (on) one colour taken from the wallpaper. */
    val wallpaperColor: Boolean = false, val tiltHighlight: Boolean = false,
    /** The wallpaper drifts a little against the pages as they scroll, for depth. Off, or with Android's animations off, it stays put. */
    val wallpaperDepth: Boolean = true,
    /** Home's layout on a phone-sized screen (see [HomeStyle]). */
    val homeStyle: HomeStyle = HomeStyle.SIDE,
    /** Home's glass (the dock, the status rail, widgets, the page dots and the search pill) is clear, with a bright edge, as iOS's Liquid
     * Glass is; off, it keeps its light frosted tint. Sheets and panels keep their tint either way, since their text sits on them. */
    val clearGlass: Boolean = true,
    /** The island's height at rest, 0..1: 0 is the camera's own height (as an iPhone's island is its cutout), 1 the most the screen
     * allows above and below it. The width stays the island's physical width (IslandGeometry.REST_WIDTH_MM) at every height. */
    val islandHeight: Float = 0f) {
    /** What the island's geometry calls its size (IslandGeometry.frame's scale): the height setting. */
    val islandScale: Float get() = islandHeight
    /** The retired knobs' fixed values (see AppearanceStore.load), kept as properties so their readers need not change. */
    val tiltStrength: Float get() = DEFAULT_STRENGTH
    internal val glass: GlassSettings get() = GlassSettings.Default
    /** Home's glass in one word, as the Appearance page offers it. */
    val glassKind: GlassChoice get() = when { !liquidGlass -> GlassChoice.OFF; clearGlass -> GlassChoice.CLEAR; else -> GlassChoice.FROSTED }
}

/** Appearance › Glass: iOS's clear Liquid Glass, the frosted tint, or no lens at all. */
enum class GlassChoice { CLEAR, FROSTED, OFF }

class AppearanceStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private fun currentSystemDark() = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    var state by mutableStateOf(load(currentSystemDark())); private set
    init { DuoAppearanceRuntime.dark = state.dark }
    private fun load(systemDark: Boolean): AppearanceState {
        val mode = runCatching { AppearanceMode.valueOf(prefs.getString("mode", "LIGHT")!!) }.getOrDefault(AppearanceMode.LIGHT)
        val lat = runCatching { prefs.getString("lat", null)?.toDoubleOrNull() }.getOrNull()
        val lon = runCatching { prefs.getString("lon", null)?.toDoubleOrNull() }.getOrNull()
        // 0.104 retired the lens sliders (height, amount, colour fringe), the tilt strength, the island size and Material You: each now has
        // one value (GlassSettings.Default, DEFAULT_STRENGTH, the middle size). Material You's place is taken by Accent › From wallpaper,
        // so someone who had it on keeps colours taken from their wallpaper.
        val materialYou = runCatching { prefs.getBoolean("materialYou", false) }.getOrDefault(false)
        return resolve(AppearanceState(mode, runCatching { prefs.getString("place", "") ?: "" }.getOrDefault(""), lat, lon,
            runCatching { prefs.getLong("locationTime", 0) }.getOrDefault(0),
            runCatching { prefs.getBoolean("deviceLocation", false) }.getOrDefault(false),
            liquidGlass = runCatching { prefs.getBoolean("liquidGlass", true) }.getOrDefault(true),
            wallpaperColor = materialYou || runCatching { prefs.getBoolean("wallpaperColor", false) }.getOrDefault(false),
            wallpaperDepth = runCatching { prefs.getBoolean("wallpaperDepth", true) }.getOrDefault(true),
            homeStyle = runCatching { HomeStyle.valueOf(prefs.getString("homeStyle", null) ?: "SIDE") }.getOrDefault(HomeStyle.SIDE),
            clearGlass = runCatching { prefs.getBoolean("clearGlass", true) }.getOrDefault(true),
            islandHeight = prefs.lenientFloat("islandHeight", 0f).coerceIn(0f, 1f),
            tiltHighlight = runCatching { prefs.getBoolean("tiltHighlight", false) }.getOrDefault(false),
            island = runCatching { prefs.getBoolean("island", true) }.getOrDefault(true)), systemDark)
    }
    fun setMode(mode: AppearanceMode, systemDark: Boolean) { save(state.copy(mode = mode), systemDark) }
    fun setLiquidGlass(value: Boolean) { save(state.copy(liquidGlass = value), currentSystemDark()) }
    fun setWallpaperDepth(value: Boolean) { save(state.copy(wallpaperDepth = value), currentSystemDark()) }
    fun setHomeStyle(value: HomeStyle) { save(state.copy(homeStyle = value), currentSystemDark()) }
    fun setClearGlass(value: Boolean) { save(state.copy(clearGlass = value), currentSystemDark()) }
    fun setGlass(value: GlassChoice) { save(state.copy(liquidGlass = value != GlassChoice.OFF, clearGlass = value != GlassChoice.FROSTED), currentSystemDark()) }
    fun setWallpaperColor(value: Boolean) { save(state.copy(wallpaperColor = value), currentSystemDark()) }
    fun setTiltHighlight(value: Boolean) { save(state.copy(tiltHighlight = value), currentSystemDark()) }
    fun setIsland(value: Boolean) { save(state.copy(island = value), currentSystemDark()) }
    fun setIslandHeight(value: Float) { save(state.copy(islandHeight = value.coerceIn(0f, 1f)), currentSystemDark()) }
    fun setManual(place: String, latitude: Double, longitude: Double, systemDark: Boolean) {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
        save(state.copy(place = place.trim(), latitude = latitude, longitude = longitude,
            locationTime = System.currentTimeMillis(), deviceLocation = false), systemDark)
    }
    fun setDeviceLocation(latitude: Double, longitude: Double, systemDark: Boolean) = save(state.copy(
        place = "Device location (rounded)", latitude = latitude, longitude = longitude,
        locationTime = System.currentTimeMillis(), deviceLocation = true, locationStatus = null), systemDark)
    fun locationStatus(message: String?) { state = state.copy(locationStatus = message) }
    fun clearLocation(systemDark: Boolean) = save(state.copy(place = "", latitude = null, longitude = null,
        locationTime = 0, deviceLocation = false), systemDark)
    fun refresh(systemDark: Boolean) { state = resolve(state, systemDark); DuoAppearanceRuntime.dark = state.dark }
    fun reloadFromPreferences(systemDark: Boolean = currentSystemDark()) {
        val transientStatus = state.locationStatus
        state = load(systemDark).copy(locationStatus = transientStatus)
        DuoAppearanceRuntime.dark = state.dark
    }
    private fun save(value: AppearanceState, systemDark: Boolean) {
        prefs.edit().putString("mode", value.mode.name).putString("place", value.place)
            .putString("lat", value.latitude?.toString()).putString("lon", value.longitude?.toString())
            .putLong("locationTime", value.locationTime).putBoolean("deviceLocation", value.deviceLocation)
            .putBoolean("liquidGlass", value.liquidGlass)
            .putBoolean("island", value.island)
            .putBoolean("wallpaperColor", value.wallpaperColor).putBoolean("tiltHighlight", value.tiltHighlight)
            .putBoolean("wallpaperDepth", value.wallpaperDepth)
            .putString("homeStyle", value.homeStyle.name).putBoolean("clearGlass", value.clearGlass).putFloat("islandHeight", value.islandHeight)
            .remove("unoStatusBar")
            .remove("materialYou").remove("refraction").remove("refractionHeight").remove("refractionAmount").remove("refractionChroma")
            .remove("tiltStrength").remove("islandScale").apply()
        state = resolve(value, systemDark)
        DuoAppearanceRuntime.dark = state.dark
    }
    private fun resolve(value: AppearanceState, systemDark: Boolean): AppearanceState = when (value.mode) {
        AppearanceMode.LIGHT -> value.copy(dark = false, fallback = null)
        AppearanceMode.DARK -> value.copy(dark = true, fallback = null)
        AppearanceMode.SYSTEM -> value.copy(dark = systemDark, fallback = null)
        AppearanceMode.SUNRISE_SUNSET -> {
            val lat = value.latitude; val lon = value.longitude
            if (lat == null || lon == null) value.copy(dark = systemDark, fallback = "Using system theme until a location is set")
            else if (value.deviceLocation && System.currentTimeMillis() - value.locationTime > 30L * 24 * 60 * 60 * 1000)
                value.copy(dark = systemDark, fallback = "Using system theme because the device location is stale")
            else runCatching { val now = ZonedDateTime.now(); value.copy(dark = solarSchedule(now.toLocalDate(), lat, lon, now.zone).isDark(now), fallback = null) }
                .getOrElse { value.copy(dark = systemDark, fallback = "Using system theme because this location is unavailable") }
        }
    }
}

object DuoAppearanceRuntime { @Volatile var dark: Boolean = false }

data class DuoPalette(val ink: androidx.compose.ui.graphics.Color, val glass: androidx.compose.ui.graphics.Color,
    val backgroundTop: androidx.compose.ui.graphics.Color, val backgroundBottom: androidx.compose.ui.graphics.Color, val dark: Boolean)
val LightDuoPalette = DuoPalette(androidx.compose.ui.graphics.Color(0xFF243A46), androidx.compose.ui.graphics.Color(0xFFE8EFF2),
    androidx.compose.ui.graphics.Color(0xFF41687E), androidx.compose.ui.graphics.Color(0xFFD8CEB6), false)
val DarkDuoPalette = DuoPalette(androidx.compose.ui.graphics.Color(0xFFEAF3F6), androidx.compose.ui.graphics.Color(0xFF263A43),
    androidx.compose.ui.graphics.Color(0xFF132832), androidx.compose.ui.graphics.Color(0xFF463F35), true)
val LocalDuoPalette = staticCompositionLocalOf { LightDuoPalette }

@Composable
fun rememberSavedAppearance(): AppearanceState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val store = remember(context) { AppearanceStore(context.applicationContext) }
    DisposableEffect(context, store, lifecycleOwner) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) { store.reloadFromPreferences() }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_DATE_CHANGED)
        }
        var registered = false
        fun register() { if (!registered) {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true; store.reloadFromPreferences()
        } }
        fun unregister() { if (registered) { runCatching { context.unregisterReceiver(receiver) }; registered = false } }
        val observer = LifecycleEventObserver { _, event -> when (event) {
            Lifecycle.Event.ON_START -> register()
            Lifecycle.Event.ON_STOP -> unregister()
            else -> Unit
        } }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) register()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); unregister() }
    }
    return store.state
}
