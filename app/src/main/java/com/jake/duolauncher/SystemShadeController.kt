package com.jake.duolauncher

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
 * This service deliberately observes no events and cannot inspect windows or inject gestures.
 */
class SystemShadeAccessibilityService : AccessibilityService() {
    private var islandHost: IslandOverlayHost? = null
    private val prefs by lazy { getSharedPreferences("extras", MODE_PRIVATE) }
    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "islandEverywhere") syncIsland()
    }

    override fun onServiceConnected() {
        // The declaration lets Settings describe the service. Once bound, unsubscribe from
        // even our own package's events: global actions do not require event delivery.
        serviceInfo = serviceInfo.apply { eventTypes = 0 }
        instance = WeakReference(this)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        syncIsland()
    }

    /** The island above other apps, as an accessibility overlay: that window type is layered above the status bar,
     * so the island is drawn over the status icons and receives taps in the camera strip, which a plain application
     * overlay (layered beneath the status bar) can do neither of. It needs no "display over other apps" permission.
     * It draws only the island and reads nothing from other apps.
     */
    private fun syncIsland() {
        val wanted = prefs.getBoolean("islandEverywhere", false)
        if (wanted && islandHost == null) {
            stopService(android.content.Intent(this, IslandOverlayService::class.java))
            islandHost = IslandOverlayHost(this, android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                needsOverlayPermission = false, onRefused = { islandHost = null }).also { it.start() }
        } else if (!wanted) {
            islandHost?.stop(); islandHost = null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        runCatching { prefs.unregisterOnSharedPreferenceChangeListener(prefListener) }
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
