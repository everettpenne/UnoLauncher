package com.jake.duolauncher.island

import com.jake.duolauncher.*
import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** An app in the island's quick-launch strip. */
internal data class QuickApp(val component: ComponentName, val label: String, val icon: ImageBitmap)

/** The island's quick-launch strip: the apps picked for the control panel's shortcuts, one tap away from the open island (HyperOS's
 * island carries app shortcuts the same way). Pure picking rules here; the lookup uses only the package manager.
 */
internal object IslandQuick {
    const val MAX = 5

    /** The launchable components (flattened, "package/class") named by saved app ids, in order, from the personal profile only (a work app cannot be started
     * from here without a profile), without repeats and at most [MAX].
     */
    fun components(ids: List<String>): List<String> = ids.mapNotNull { id ->
        parseProfileAppId(id)?.takeIf { it.userSerial == null }?.component?.takeIf { '/' in it && !it.startsWith('/') && !it.endsWith('/') }
    }.distinct().take(MAX)

    /** The strip's apps, looked up ahead of time and kept, so opening the island never waits on the package manager (loading five icons
     * while the island was growing made it stutter, and the strip's arrival changed the island's height half way through the animation).
     */
    var cached by androidx.compose.runtime.mutableStateOf(emptyList<QuickApp>())
        private set
    private var loadedFor: String? = null

    /** Looks the apps up again only if the chosen shortcuts or the switch changed; cheap otherwise. Call off the main thread. */
    fun refreshIfChanged(context: Context) {
        val prefs = context.getSharedPreferences("extras", Context.MODE_PRIVATE)
        val signature = (if (enabled(context)) "on:" else "off:") + prefs.getString("shortcuts", null).orEmpty()
        if (signature == loadedFor) return
        val apps = if (enabled(context)) resolve(context) else emptyList()
        loadedFor = signature
        cached = apps
    }

    fun enabled(context: Context) = context.getSharedPreferences("extras", Context.MODE_PRIVATE).getBoolean("islandShortcuts", true)

    /** Looks the apps up. Apps that are gone are skipped. Not for the main thread (it loads icons). */
    fun resolve(context: Context): List<QuickApp> {
        val pm = context.packageManager
        val ids = PanelLayout.parseIds(context.getSharedPreferences("extras", Context.MODE_PRIVATE).getString("shortcuts", null))
        return components(ids).mapNotNull { flat ->
            runCatching {
                val component = ComponentName.unflattenFromString(flat) ?: return@runCatching null
                val info = pm.getActivityInfo(component, 0)
                QuickApp(component, info.loadLabel(pm).toString(), pm.getActivityIcon(component).toBitmap(96, 96).asImageBitmap())
            }.getOrNull()
        }
    }
}

/** What the call card's End button needs. */
internal object CallActions {
    /** [android.app.Notification.CallStyle.CALL_TYPE_ONGOING], the value of the notification's call-type extra. */
    const val CALL_TYPE_ONGOING = 2

    /** Which of a call notification's actions ends the call: for an ongoing call Android puts the hang-up action first, ahead of any the
     * app adds. Null for an incoming or screening call, where the actions are answer and decline in an order that is not defined.
     */
    fun hangUpIndex(callType: Int, actionCount: Int): Int? = if (callType == CALL_TYPE_ONGOING && actionCount >= 1) 0 else null
}

/** The colour of an app's icon, for tinting what the island shows about it. Worked out once per app and kept. */
internal object AppTints {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private const val NONE = 0

    /** The icon's dominant vivid colour as ARGB, or null for a grey, black or white icon. [icon] is only called the first time. */
    fun of(packageName: String, icon: () -> android.graphics.Bitmap?): Int? =
        cache.getOrPut(packageName) { runCatching { icon()?.artTintArgb() }.getOrNull() ?: NONE }.takeIf { it != NONE }
}
