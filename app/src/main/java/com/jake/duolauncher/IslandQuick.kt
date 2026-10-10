package com.jake.duolauncher

import android.content.ComponentName
import android.content.Context
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
