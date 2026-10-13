package com.jake.duolauncher.appearance

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/*
 * Third-party icon packs, in the format nearly every pack ships (ADW, Nova, Lawnchair and others read the same thing): an app
 * that answers one of the "theme" intents in [IconPacks.ACTIONS] and carries an `appfilter.xml` that maps an app's launch
 * activity to one of its drawables:
 *
 *     <item component="ComponentInfo{com.android.chrome/com.google.android.apps.chrome.Main}" drawable="chrome" />
 *
 * Uno reads only that list and the pictures it names, from the pack the person picks; it loads no code from the pack, needs no
 * permission, and sends nothing. An app the pack does not cover keeps its own icon and the chosen icon style.
 */

internal data class IconPackInfo(val packageName: String, val label: String)

/** Parsing an `appfilter.xml`, kept apart from Android so it is unit-tested. */
internal object AppFilter {
    /** Enough for the largest packs (around 10,000 items); a file beyond this is cut off rather than read forever. */
    const val MAX_ITEMS = 25_000

    /** "ComponentInfo{com.foo/.Bar}" or "com.foo/com.foo.Bar" as "com.foo/com.foo.Bar", or null if it is not a component. */
    fun componentKey(raw: String): String? {
        val inner = raw.trim().removePrefix("ComponentInfo{").removeSuffix("}").trim()
        val slash = inner.indexOf('/')
        if (slash <= 0 || slash == inner.length - 1) return null
        val pkg = inner.substring(0, slash); var cls = inner.substring(slash + 1)
        if (cls.startsWith(".")) cls = pkg + cls
        if (pkg.any { it.isWhitespace() } || cls.any { it.isWhitespace() }) return null
        return "$pkg/$cls"
    }

    /** The key for an app's launch activity, in the same form as [componentKey]. */
    fun keyOf(component: ComponentName): String = "${component.packageName}/${component.className}"

    /** Every `<item component=… drawable=…/>` in the file, as component key to drawable name. The first mapping for a component wins. */
    fun parse(parser: XmlPullParser): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && map.size < MAX_ITEMS) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                val component = parser.getAttributeValue(null, "component")?.let(::componentKey)
                val drawable = parser.getAttributeValue(null, "drawable")?.trim()
                if (component != null && !drawable.isNullOrEmpty() && drawable.length <= 200 && component !in map) map[component] = drawable
            }
            event = parser.next()
        }
        return map
    }
}

internal object IconPacks {
    /** The intents icon packs answer, so a pack built for any of the common launchers is found. */
    val ACTIONS = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme",
        "org.adw.launcher.icons.ACTION_PICK_ICON", "com.anddoes.launcher.THEME", "com.teslacoilsw.launcher.THEME",
        "app.lawnchair.icons.THEMED_ICON")

    /** Installed icon packs, by name. */
    fun installed(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        return ACTIONS.flatMap { action ->
            runCatching { pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA) }.getOrDefault(emptyList())
        }.map { it.activityInfo.packageName }.distinct().filter { it != context.packageName }.mapNotNull { pkg ->
            runCatching { IconPackInfo(pkg, pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()) }.getOrNull()
        }.sortedBy { it.label.lowercase() }
    }

    /** A pack's version, so icons are rebuilt when the pack updates. 0 if it is not installed. */
    fun version(context: Context, pkg: String): Long =
        runCatching { context.packageManager.getPackageInfo(pkg, 0).longVersionCode }.getOrDefault(0L)

    /** A loaded pack: its appfilter map and its resources, to look icons up from. */
    class Loaded internal constructor(val packageName: String, private val res: Resources, private val map: Map<String, String>) {
        val size: Int get() = map.size
        private val ids = HashMap<String, Int>()

        @Suppress("DiscouragedApi")  // a pack's drawables are only reachable by name
        fun drawableFor(component: ComponentName): Drawable? {
            val name = map[AppFilter.keyOf(component)] ?: return null
            val id = ids.getOrPut(name) { res.getIdentifier(name, "drawable", packageName).takeIf { it != 0 }
                ?: res.getIdentifier(name, "mipmap", packageName) }
            if (id == 0) return null
            return runCatching { res.getDrawable(id, null) }.getOrNull()
        }
    }

    /** Reads [pkg]'s appfilter from `res/xml/appfilter.xml` or `assets/appfilter.xml`. Null if it is missing or unreadable. */
    @Suppress("DiscouragedApi")
    fun load(context: Context, pkg: String): Loaded? = runCatching {
        val res = context.packageManager.getResourcesForApplication(pkg)
        val xmlId = res.getIdentifier("appfilter", "xml", pkg)
        val map = if (xmlId != 0) res.getXml(xmlId).use { AppFilter.parse(it) }
        else res.assets.open("appfilter.xml").use { stream ->
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(stream, null)
            AppFilter.parse(parser)
        }
        if (map.isEmpty()) null else Loaded(pkg, res, map)
    }.getOrNull()
}
