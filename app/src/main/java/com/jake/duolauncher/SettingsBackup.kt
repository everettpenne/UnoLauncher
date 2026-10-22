package com.jake.duolauncher

import android.content.Context
import com.jake.duolauncher.island.IslandThemeFile
import org.json.JSONObject

/**
 * Uno's own settings inside a layout backup (backup version 3): the switches and sliders of the "extras" and "appearance"
 * preference files. Every value is a plain boolean, number or short string, so the section is validated by shape alone.
 *
 * Left out on purpose: values that belong to one device or one install rather than to the person (the web browser package,
 * the test-only debug switches, the saved location, and every cache or schedule the launcher keeps for itself).
 */
data class SettingsSnapshot(
    val extras: Map<String, Any>, val appearance: Map<String, Any>,
    /** What the typed library widgets (note, counter, countdown) hold, and the apps and names of large folders, keyed by Home slot. */
    val widgetData: Map<String, Any> = emptyMap(), val largeFolders: Map<String, Any> = emptyMap(),
    /** The island theme (one small JSON string under "json"); empty when the person uses Classic. Older backups simply lack it. */
    val islandTheme: Map<String, Any> = emptyMap(),
) {
    val count: Int get() = extras.size + appearance.size
}

internal object SettingsBackup {
    const val EXTRAS = "extras"
    const val APPEARANCE = "appearance"
    const val WIDGET_DATA = "uno_widget_data"
    const val LARGE_FOLDERS = "large_folders"
    const val ISLAND_THEME = "uno_island_theme"
    private const val MAX_KEYS = 200
    private const val MAX_STRING = 2_000
    /** A large folder's list of up to 24 app ids is longer than an ordinary setting. */
    private const val MAX_FOLDER_STRING = 20_000
    private fun maxString(file: String) = when (file) {
        LARGE_FOLDERS -> MAX_FOLDER_STRING
        ISLAND_THEME -> IslandThemeFile.MAX_BYTES
        else -> MAX_STRING
    }

    private val EXCLUDED = mapOf(
        EXTRAS to setOf("webPackage"),
        APPEARANCE to setOf("debugCutout", "debugHud", "lat", "lon", "place", "locationTime", "deviceLocation"),
    )
    /**
     * Settings that are read as floats. JSON cannot tell 1.0 from 1 (org.json writes a whole float without its ".0"), so a
     * restored whole-number float came back as an Int and was stored as one on an install that had never written the key,
     * and the later getFloat threw ClassCastException at startup. These are always written as floats.
     */
    private val FLOAT_KEYS = mapOf(
        EXTRAS to setOf("kbHapticStrength", "kbHeight"),
        APPEARANCE to setOf("refraction", "refractionHeight", "refractionAmount", "refractionChroma", "tiltStrength", "islandScale"),
    )
    fun isFloatKey(file: String, key: String) = key in FLOAT_KEYS[file].orEmpty()
    private val KEY = Regex("[A-Za-z0-9_]{1,48}")

    fun exportable(file: String, key: String) = KEY.matches(key) && key !in EXCLUDED[file].orEmpty()

    /** Keeps only the portable entries of a preferences map (as `SharedPreferences.getAll()` returns it). */
    fun portable(file: String, all: Map<String, *>): Map<String, Any> {
        val kept = LinkedHashMap<String, Any>()
        all.toSortedMap().forEach { (key, value) ->
            if (value == null || !exportable(file, key)) return@forEach
            when (value) {
                is Boolean, is Int, is Long, is Float -> kept[key] = value
                is String -> if (value.length <= maxString(file)) kept[key] = value
                else -> Unit
            }
        }
        return kept
    }

    fun encode(snapshot: SettingsSnapshot): JSONObject {
        fun section(values: Map<String, Any>) = JSONObject().also { json -> values.forEach { (key, value) -> json.put(key, value) } }
        return JSONObject().put(EXTRAS, section(snapshot.extras)).put(APPEARANCE, section(snapshot.appearance))
            .put(WIDGET_DATA, section(snapshot.widgetData)).put(LARGE_FOLDERS, section(snapshot.largeFolders))
            .put(ISLAND_THEME, section(snapshot.islandTheme))
    }

    /** Reads the section back. Anything outside the allowed shape throws, so a damaged or hostile file is refused whole. */
    fun decode(root: JSONObject): SettingsSnapshot {
        fun section(file: String): Map<String, Any> {
            val json = root.optJSONObject(file) ?: return emptyMap()
            require(json.length() <= MAX_KEYS) { "Settings section is too large" }
            val result = LinkedHashMap<String, Any>()
            json.keys().forEach { key ->
                require(KEY.matches(key)) { "Invalid setting name" }
                if (key in EXCLUDED[file].orEmpty()) return@forEach
                val value = json.get(key)
                when (value) {
                    is Boolean -> result[key] = value
                    is Int, is Long -> result[key] = value
                    is Number -> {
                        val d = value.toDouble()
                        require(d.isFinite()) { "Invalid setting value" }
                        result[key] = d.toFloat()
                    }
                    is String -> { require(value.length <= maxString(file)) { "Setting value is too long" }; result[key] = value }
                    else -> error("Invalid setting value")
                }
            }
            return result
        }
        return SettingsSnapshot(section(EXTRAS), section(APPEARANCE), section(WIDGET_DATA), section(LARGE_FOLDERS), section(ISLAND_THEME))
    }

    fun capture(context: Context) = SettingsSnapshot(
        portable(EXTRAS, context.getSharedPreferences(EXTRAS, Context.MODE_PRIVATE).all),
        portable(APPEARANCE, context.getSharedPreferences(APPEARANCE, Context.MODE_PRIVATE).all),
        portable(WIDGET_DATA, context.getSharedPreferences(WIDGET_DATA, Context.MODE_PRIVATE).all),
        portable(LARGE_FOLDERS, context.getSharedPreferences(LARGE_FOLDERS, Context.MODE_PRIVATE).all),
        portable(ISLAND_THEME, context.getSharedPreferences(ISLAND_THEME, Context.MODE_PRIVATE).all),
    )

    /** Writes the snapshot over the current preferences, converting each value to the type already stored under its key. */
    fun apply(context: Context, snapshot: SettingsSnapshot) {
        fun write(file: String, values: Map<String, Any>) {
            val prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE)
            val existing = prefs.all
            val edit = prefs.edit()
            values.forEach { (key, value) ->
                if (isFloatKey(file, key)) { (value as? Number)?.let { edit.putFloat(key, it.toFloat()) }; return@forEach }
                when (existing[key]) {
                    is Boolean -> (value as? Boolean)?.let { edit.putBoolean(key, it) }
                    is Int -> (value as? Number)?.let { edit.putInt(key, it.toInt()) }
                    is Long -> (value as? Number)?.let { edit.putLong(key, it.toLong()) }
                    is Float -> (value as? Number)?.let { edit.putFloat(key, it.toFloat()) }
                    is String -> (value as? String)?.let { edit.putString(key, it) }
                    // A key this install has never written: take the value's own type.
                    else -> when (value) {
                        is Boolean -> edit.putBoolean(key, value)
                        is Int -> edit.putInt(key, value)
                        is Long -> edit.putLong(key, value)
                        is Float -> edit.putFloat(key, value)
                        is String -> edit.putString(key, value)
                    }
                }
            }
            edit.apply()
        }
        write(EXTRAS, snapshot.extras)
        write(APPEARANCE, snapshot.appearance)
        write(WIDGET_DATA, snapshot.widgetData)
        write(LARGE_FOLDERS, snapshot.largeFolders)
        // A theme in a backup is as untrusted as an imported file: it is written only if it passes the same checks, and
        // a backup without one leaves the current theme alone.
        val theme = snapshot.islandTheme["json"] as? String
        if (theme != null && IslandThemeFile.parse(theme) is IslandThemeFile.Result.Ok) write(ISLAND_THEME, mapOf("json" to theme))
    }
}
