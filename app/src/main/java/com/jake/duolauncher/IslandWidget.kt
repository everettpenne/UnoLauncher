package com.jake.duolauncher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * One Android widget hosted inside the open island (the idea behind HyperOS's island widgets). It has its own widget host,
 * separate from Home's, so Home's tidy-up of unused widgets never touches it, and the id is kept in its own preferences.
 */
internal object IslandWidgetState {
    const val HOST_ID = 2048
    /** Room the widget takes in the open panel. */
    const val ROW_DP = 120f
    private const val PREFS = "island_widget"
    private var loaded = false
    /** The bound widget's id, or -1 for none. */
    var id by mutableIntStateOf(-1)
        private set

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        // Preferences only: nothing here talks to the system, since this runs while the island is first composed.
        id = prefs(context).getInt("id", -1)
    }

    fun set(context: Context, value: Int) {
        prefs(context).edit().putInt("id", value).apply()
        id = value
    }
}

internal object IslandWidgetHostHolder {
    @Volatile private var host: AppWidgetHost? = null
    fun get(context: Context): AppWidgetHost = host ?: synchronized(this) {
        host ?: ZeroPaddingWidgetHost(context.applicationContext, IslandWidgetState.HOST_ID).also { host = it }
    }
}

/** The widget itself, for the open island's panel. Draws nothing when none is set or the provider has gone. */
@Composable
internal fun IslandWidgetPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val id = IslandWidgetState.id
    val host = remember { IslandWidgetHostHolder.get(context) }
    DisposableEffect(host) {
        runCatching { host.startListening() }
        onDispose { runCatching { host.stopListening() } }
    }
    val info = remember(id) { if (id >= 0) AppWidgetManager.getInstance(context).getAppWidgetInfo(id) else null }
    if (info != null) AndroidView(factory = { c -> host.createView(c, id, info) },
        modifier = modifier.clip(RoundedCornerShape(14.dp)).testTag("island-widget"))
}

/** Picks, binds and removes the island's widget from Customize. */
internal class IslandWidgetController(private val activity: ComponentActivity) {
    // Lazy: the controller is created with the activity (a result launcher must be registered then), before system services exist.
    private val manager by lazy { AppWidgetManager.getInstance(activity) }
    private val host by lazy { IslandWidgetHostHolder.get(activity) }
    var message by mutableStateOf<String?>(null)
        private set
    private var pendingId = -1

    private val bind = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) configure() else cancel()
    }

    fun providers(): List<AppWidgetProviderInfo> = manager.getInstalledProvidersForProfile(Process.myUserHandle())
        .sortedBy { it.loadLabel(activity.packageManager).lowercase() }

    fun label(info: AppWidgetProviderInfo): String = info.loadLabel(activity.packageManager)

    fun currentLabel(): String? = IslandWidgetState.id.takeIf { it >= 0 }?.let { manager.getAppWidgetInfo(it) }?.let(::label)

    fun start(provider: AppWidgetProviderInfo) {
        cancel()
        // A widget allocated earlier but never finished (the app was closed half way through) is not kept.
        host.appWidgetIds.filter { it != IslandWidgetState.id }.forEach(host::deleteAppWidgetId)
        message = null
        pendingId = host.allocateAppWidgetId()
        try {
            if (manager.bindAppWidgetIdIfAllowed(pendingId, provider.profile, provider.provider, null)) configure()
            else bind.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, provider.profile))
        } catch (_: Exception) { fail() }
    }

    private fun configure() {
        val info = manager.getAppWidgetInfo(pendingId) ?: return fail()
        if (info.configure == null) complete()
        else try { host.startAppWidgetConfigureActivityForResult(activity, pendingId, 0, CONFIGURE, null) }
        catch (_: Exception) { fail() }
    }

    /** Called from the activity's result hook; true when the request was the island widget's. */
    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode != CONFIGURE) return false
        if (resultCode == Activity.RESULT_OK) complete() else cancel()
        return true
    }

    private fun complete() {
        val previous = IslandWidgetState.id
        IslandWidgetState.set(activity, pendingId)
        pendingId = -1
        if (previous >= 0) host.deleteAppWidgetId(previous)
    }

    fun remove() {
        val previous = IslandWidgetState.id
        IslandWidgetState.set(activity, -1)
        if (previous >= 0) host.deleteAppWidgetId(previous)
    }

    private fun cancel() {
        if (pendingId >= 0) host.deleteAppWidgetId(pendingId)
        pendingId = -1
    }

    private fun fail() { cancel(); message = "That widget could not be added." }

    private companion object { const val CONFIGURE = 703 }
}
