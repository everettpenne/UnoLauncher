package com.jake.duolauncher

import com.jake.duolauncher.island.*

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import java.lang.ref.WeakReference

internal enum class ShadePanel { NOTIFICATIONS, QUICK_SETTINGS }

internal enum class ShadeOpenResult { OPENED, SERVICE_DISABLED, SERVICE_STARTING, ACTION_REJECTED }

/**
 * The platform exposes shade expansion to third-party apps only as accessibility global actions.
 * This service observes no events and cannot inspect windows or inject gestures, with one opt-in exception: while the person has chosen
 * apps to hide the island over, it receives window-change events and keeps only the package name of the app in front (see [syncEvents]).
 */
class SystemShadeAccessibilityService : AccessibilityService() {
    private var islandHost: IslandOverlayHost? = null
    private val prefs by lazy { getSharedPreferences("extras", MODE_PRIVATE) }
    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "islandEverywhere" || key == "island" || key == "homeIslandOverlay") syncIsland()
        if (key == "islandHideOverOn" || key == "islandHideOver" || key == "islandEverywhere") syncEvents()
    }
    private val appearancePrefs by lazy { getSharedPreferences("appearance", MODE_PRIVATE) }

    override fun onServiceConnected() {
        // The declaration lets Settings describe the service. Once bound, unsubscribe from
        // even our own package's events: global actions do not require event delivery.
        serviceInfo = serviceInfo.apply { eventTypes = 0 }
        instance = WeakReference(this)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        appearancePrefs.registerOnSharedPreferenceChangeListener(prefListener)
        syncIsland()
        syncEvents()
    }

    /**
     * Window-change events, only while the island is shown over apps and the person has chosen apps to hide it over: then the package of
     * each new window is all that is read (the service still cannot read window content), to know when one of those apps comes to the front.
     * Otherwise the service is unsubscribed from everything, as it always was.
     */
    private fun syncEvents() {
        val wanted = prefs.getBoolean("islandEverywhere", false) && prefs.getBoolean("islandHideOverOn", false) &&
            !prefs.getString("islandHideOver", null).isNullOrBlank()
        serviceInfo = serviceInfo.apply {
            eventTypes = if (wanted) AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED else 0
            // The declaration limits events to Uno itself; the window changes wanted here are other apps', so the limit is lifted only then.
            packageNames = if (wanted) null else arrayOf(packageName)
        }
        if (!wanted) IslandRuntime.foregroundPackage = null
    }

    /** The island above other apps, as an accessibility overlay: that window type is layered above the status bar,
     * so the island is drawn over the status icons and receives taps in the camera strip, which a plain application
     * overlay (layered beneath the status bar) can do neither of. It needs no "display over other apps" permission.
     * It draws only the island and reads nothing from other apps.
     */
    private fun syncIsland() {
        // Everywhere; or, when asked ("Keep the Home island still"), on Home alone: this window then draws the Home island too, so it stays
        // still at the camera while Android animates the launcher's own window (unlocking, closing an app). Otherwise the launcher draws it.
        val wanted = prefs.getBoolean("islandEverywhere", false) || (appearancePrefs.getBoolean("island", true) && prefs.getBoolean("homeIslandOverlay", false))
        if (wanted && islandHost == null) {
            stopService(android.content.Intent(this, IslandOverlayService::class.java))
            islandHost = IslandOverlayHost(this, android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                needsOverlayPermission = false, onRefused = { islandHost = null }).also { it.start() }
        } else if (!wanted) {
            islandHost?.stop(); islandHost = null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val keyboard = runCatching { android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD) }
            .getOrNull()?.substringBefore('/')
        if (HideOverApps.ignored(pkg, keyboard)) return
        IslandRuntime.foregroundPackage = pkg
    }
    override fun onInterrupt() = Unit
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        runCatching { prefs.unregisterOnSharedPreferenceChangeListener(prefListener); appearancePrefs.unregisterOnSharedPreferenceChangeListener(prefListener) }
        IslandRuntime.foregroundPackage = null
        islandHost?.stop(); islandHost = null
        if (instance.get() === this) instance.clear()
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        islandHost?.stop(); islandHost = null
        if (instance.get() === this) instance.clear()
        super.onDestroy()
    }

    companion object {
        private var instance = WeakReference<SystemShadeAccessibilityService>(null)
        internal fun isConnected() = instance.get() != null
        /** True while this service is connected, in which case it, not [IslandOverlayService], draws the island. */
        internal fun hostsIsland() = instance.get() != null

        /** Locks the screen, as the power button would (a double tap on empty Home space). */
        internal fun lock(context: Context): ShadeOpenResult {
            val service = instance.get()
            if (service != null) return if (service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)) ShadeOpenResult.OPENED else ShadeOpenResult.ACTION_REJECTED
            return if (isEnabled(context)) ShadeOpenResult.SERVICE_STARTING else ShadeOpenResult.SERVICE_DISABLED
        }

        internal fun open(context: Context, panel: ShadePanel): ShadeOpenResult {
            val service = instance.get()
            if (service != null) {
                val action = when (panel) {
                    ShadePanel.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
                    ShadePanel.QUICK_SETTINGS -> GLOBAL_ACTION_QUICK_SETTINGS
                }
                return if (service.performGlobalAction(action)) ShadeOpenResult.OPENED
                else ShadeOpenResult.ACTION_REJECTED
            }
            return if (isEnabled(context)) ShadeOpenResult.SERVICE_STARTING
            else ShadeOpenResult.SERVICE_DISABLED
        }

        private fun isEnabled(context: Context): Boolean {
            val component = ComponentName(context, SystemShadeAccessibilityService::class.java)
            val manager = context.getSystemService(AccessibilityManager::class.java)
            return manager.getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            ).any {
                val service = it.resolveInfo?.serviceInfo ?: return@any false
                ComponentName(service.packageName, service.name) == component
            }
        }
    }
}

/** The double tap on empty Home space: locks the screen, or says what is needed when the optional accessibility service is not running. */
internal fun lockFromHome(context: Context) {
    LockRequests.count++
    if (SystemShadeAccessibilityService.lock(context) != ShadeOpenResult.OPENED)
        android.widget.Toast.makeText(context, R.string.lock_needs_service, android.widget.Toast.LENGTH_LONG).show()
}

/** How many times Home has asked to lock the screen (a double tap), so a test can tell a double tap from a tap. */
internal object LockRequests { @Volatile var count = 0 }
