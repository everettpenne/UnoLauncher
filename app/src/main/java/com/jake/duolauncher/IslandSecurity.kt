package com.jake.duolauncher

/** What is happening on the USB port, from the sticky USB_STATE broadcast. Only an active data connection is worth the island's
 * attention: on a phone with USB-C port control a data link is the thing to notice, and plain charging already has its own event.
 */
internal object UsbState {
    /** Functions that mean the port is carrying data, not just power. */
    private val DATA_FUNCTIONS = setOf("mtp", "ptp", "rndis", "midi", "accessory", "audio_source", "ncm", "uvc", "adb")

    /** True while a host is connected and the port has been configured for at least one data function. [functions] are the
     * names of the boolean extras that are set (the broadcast carries one per active function).
     */
    fun dataActive(connected: Boolean, configured: Boolean, functions: Set<String>): Boolean =
        connected && configured && functions.any { it in DATA_FUNCTIONS }
}

/** What each permission or capability the launcher uses is for, what it can see, and how it is turned off. Pure data, so the
 * wording is tested and the screen only draws it.
 */
internal enum class LedgerAction { OVERLAY_SETTINGS, ACCESSIBILITY_SETTINGS, NOTIFICATION_ACCESS, APP_INFO, NONE }

internal data class LedgerRow(
    val id: String,
    val title: String,
    val status: String,
    val on: Boolean,
    val usedFor: String,
    val canSee: String,
    val change: LedgerAction,
)

internal data class LedgerInputs(
    val overlayAllowed: Boolean,
    val accessibilityEnabledInSettings: Boolean,
    val accessibilityRunning: Boolean,
    val notificationAccess: Boolean,
    val contactsAllowed: Boolean,
    val islandEverywhere: Boolean,
)

internal object PermissionLedger {
    fun rows(i: LedgerInputs): List<LedgerRow> {
        val blocked = i.accessibilityEnabledInSettings && !i.accessibilityRunning
        return listOf(
            LedgerRow("overlay", "Display over other apps",
                if (i.overlayAllowed) "Allowed" else "Not allowed", i.overlayAllowed,
                "Drawing the island above other apps when the accessibility service below is off. It is visible, but Android puts it under the status bar, so it cannot be tapped over the camera. It is drawn over every app, including banking and password apps: Android gives a launcher no way to tell which apps are secure, so turn Island everywhere off if you do not want that.",
                "Nothing. It only draws the island's own pixels.",
                LedgerAction.OVERLAY_SETTINGS),
            LedgerRow("accessibility", "Accessibility service (shade gestures)",
                when {
                    i.accessibilityRunning -> "Running"
                    blocked -> "Turned on, but not running"
                    else -> "Off"
                },
                i.accessibilityRunning,
                "Opening the notifications and Quick Settings panels from Home gestures, and drawing the island above the status bar (as an accessibility overlay window) so it can be tapped over other apps." +
                    if (blocked) " Android is not running it. Advanced Protection or a restricted-settings block can do this; the plain overlay is used instead." else "",
                "Nothing. It subscribes to no events and cannot read the screen or other apps.",
                LedgerAction.ACCESSIBILITY_SETTINGS),
            LedgerRow("notifications", "Notification access",
                if (i.notificationAccess) "Allowed" else "Not allowed", i.notificationAccess,
                "Badges on app icons, the track title and artwork, the call card, Live Updates in the island, and the island's notification peeks, each only if you switch it on.",
                "App names, the current track's title, artist and artwork, the caller's name while a call is active, and a Live Update's title, short status, progress and icon. Never the text of a message. Nothing is stored or sent.",
                LedgerAction.NOTIFICATION_ACCESS),
            LedgerRow("contacts", "Contacts",
                if (i.contactsAllowed) "Allowed" else "Not allowed", i.contactsAllowed,
                "Finding contacts from search, if you switch that on.",
                "Contact names, matched on this device. Nothing is stored or sent.",
                LedgerAction.APP_INFO),
            LedgerRow("indicators", "Camera, microphone, recording, VPN, USB and audio marks",
                "No permission needed", true,
                "The island's camera, microphone and screen-recording marks, and VPN, USB-data and audio-device alerts. Screen recording uses Android's install-time DETECT_SCREEN_RECORDING permission.",
                "Only that something is happening, never which app or what was captured. Android reports these to every app.",
                LedgerAction.NONE),
        )
    }

    /** The plain-language line under the title: which island you actually have right now. */
    fun islandSummary(i: LedgerInputs): String = when {
        !i.islandEverywhere -> "The island is on Home only."
        i.accessibilityRunning -> "The island is above other apps and can be tapped there."
        i.overlayAllowed -> "The island is above other apps but under the status bar, so it cannot be tapped over the camera."
        else -> "The island is set for other apps, but no permission is granted, so it is on Home only."
    }
}
