package com.jake.duolauncher

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One of an app's own quick actions ("New incognito tab", "Take a selfie"), as the app publishes it. */
internal class AppShortcut(val info: ShortcutInfo, val label: String, val icon: Bitmap?)

/** Reads and starts the shortcuts apps publish for their launcher icon. Only a Home app may ask for them, so this
 * returns nothing until Uno is the default Home app. Nothing leaves the device: shortcuts are local app data.
 *
 * Every call is a binder round trip, so [load] runs on the IO dispatcher and [start] on its own thread.
 */
internal object AppShortcuts {
    const val MAX = 4

    suspend fun load(context: Context, app: AppEntry): List<AppShortcut> = withContext(Dispatchers.IO) {
        runCatching {
            val launcherApps = context.getSystemService(LauncherApps::class.java)
            if (!launcherApps.hasShortcutHostPermission()) return@runCatching emptyList()
            val query = LauncherApps.ShortcutQuery()
                .setPackage(app.packageName)
                .setActivity(app.component)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
            val found = launcherApps.getShortcuts(query, app.user).orEmpty()
            pick(found.map { ShortcutFacts(it.id, it.isEnabled, it.rank, (it.shortLabel ?: it.longLabel)?.toString().orEmpty()) })
                .mapNotNull { fact -> found.firstOrNull { it.id == fact.id } }
                .map { info ->
                    AppShortcut(info, (info.shortLabel ?: info.longLabel).toString(),
                        runCatching { launcherApps.getShortcutIconDrawable(info, 0)?.toBitmap(96, 96) }.getOrNull())
                }
        }.getOrDefault(emptyList())
    }

    fun start(context: Context, shortcut: AppShortcut) {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        Thread { runCatching { launcherApps.startShortcut(shortcut.info, null, null) } }.start()
    }

    /** The shortcut fields the ordering rules need, so they are testable without Android's ShortcutInfo. */
    data class ShortcutFacts(val id: String, val enabled: Boolean, val rank: Int, val label: String)

    /** Enabled shortcuts with a name, in the app's own order, capped at [MAX]. */
    fun pick(all: List<ShortcutFacts>): List<ShortcutFacts> =
        all.filter { it.enabled && it.label.isNotBlank() }.distinctBy { it.id }.sortedBy { it.rank }.take(MAX)
}
