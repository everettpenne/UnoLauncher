package com.jake.duolauncher.island

/**
 * Pure arbitration of "one home per event" (docs/design.md, "Metaphor policy"): every event has exactly
 * one owner among the launcher's own surfaces, so nothing visibly fires twice at the same moment. Nothing
 * here touches Android or Compose, so the ladder is unit-tested and the call sites stay thin.
 *
 * ## The boundary this cannot cross
 *
 * The Android status bar belongs to the system and is never drawn over. A launcher has no API to hide
 * another app's status-bar notification icon: the only call that removes one is
 * [android.service.notification.NotificationListenerService.cancelNotification] (or `cancelAllNotifications`),
 * and calling it for a notification the user did not ask us to silence is hostile — it deletes the
 * notification, can re-trigger the posting app's repost, and reads as "Uno cleared my notifications".
 * So the system's status-bar icon always remains the system's archive. This arbiter only decides which of
 * the launcher's OWN surfaces (pill flash, drop-down card, held dot, badge, status rail) presents each
 * event; it never suppresses or draws over the status bar.
 */
internal object IslandEventArbiter {
    /**
     * How long after the launcher opened the notification shade a notification is treated as "already
     * presented" and not re-announced on the island. A best-effort proxy: a launcher can see the moment
     * it asked Android to open the shade, but cannot see the shade's contents, or when it closes, without
     * the accessibility service reading windows — which the privacy model forbids. The window is one
     * island flash cycle, so a notification that posts as the shade slides open is not said twice.
     */
    const val SHADE_ANNOUNCE_COOLDOWN_MS = 3_000L

    /** Where a message notification's island surface lands. */
    enum class NotificationSurface { CARD, PILL, NONE }

    /**
     * The event-home ladder for a message/app notification (docs/design.md: "the peek card, then the badge
     * on the app's icon"). A card is dropped only when the user opted into one (title/text); a name-only
     * peek is a pill flash. Neither fires while the island is already presenting a notification card (the
     * card already owns the notification surface; a name-only flash on top of it would be the same event
     * twice), or while the user just watched the shade present it.
     *
     * @param hasTitleOrText whether the notification carries a title or text to show (the card level, not
     *   name-only).
     * @param cardAlreadyDown whether the island is already presenting a notification card.
     * @param shadeOpenedAtMs when the launcher last opened the notification shade, 0 if never.
     * @param nowMs now.
     * @param homeCovered whether something the person opened has taken Home over (Spotlight, a settings sheet, the
     *   control panel, a drag: IslandRuntime.homeCovered). While it has, the island holds off: no card drops and no
     *   name-only flash fires underneath it, so a notification never auto-expands the island while the person is
     *   typing a search or changing a setting. The caller keeps the notification as the island's recent one instead.
     */
    fun notificationSurface(hasTitleOrText: Boolean, cardAlreadyDown: Boolean, shadeOpenedAtMs: Long, nowMs: Long,
        homeCovered: Boolean = false): NotificationSurface = when {
        shadeOpenedAtMs > 0L && nowMs - shadeOpenedAtMs in 0 until SHADE_ANNOUNCE_COOLDOWN_MS -> NotificationSurface.NONE
        homeCovered -> NotificationSurface.NONE
        hasTitleOrText -> NotificationSurface.CARD
        cardAlreadyDown -> NotificationSurface.NONE
        else -> NotificationSurface.PILL
    }

    /**
     * Whether the collapsed island draws a battery-*level* ring while charging. The status rail owns the
     * battery level when it is shown, so the island then keeps only the charging icon (a "charging" event,
     * not a level); with the rail off the island owns the level and draws the ring (docs/design.md:
     * "the collapsed island shows the battery only while charging, or its percentage when the rail is off").
     */
    fun chargingRingSurface(statusRailShown: Boolean, charging: Boolean): Boolean = charging && !statusRailShown

    /** Who owns the time (docs/design.md): the rail when shown, else the open island panel; the collapsed island never shows it. */
    enum class TimeOwner { RAIL, PANEL, NONE }

    fun timeOwner(statusRailShown: Boolean, islandExpanded: Boolean): TimeOwner = when {
        statusRailShown -> TimeOwner.RAIL
        islandExpanded -> TimeOwner.PANEL
        else -> TimeOwner.NONE
    }

    /** Who owns the battery level (docs/design.md): the rail's ring when shown, else the island. */
    enum class BatteryOwner { RAIL, ISLAND }

    fun batteryLevelOwner(statusRailShown: Boolean): BatteryOwner = if (statusRailShown) BatteryOwner.RAIL else BatteryOwner.ISLAND

    /**
     * Whether the island steps aside for what has taken Home over ([homeCovered]: Spotlight, a settings sheet, the control panel, a drag).
     * What the person opened wins over the island, whatever it is showing (music, a Live Update, a flash), and the island comes back
     * when it closes; only a ringing timer, which has to be seen and stopped, keeps the island. (Home also closes search and the control
     * panel when a timer rings, since its own island is drawn under them; a settings sheet stays open.)
     */
    fun overlayYields(homeCovered: Boolean, timerRinging: Boolean): Boolean = homeCovered && !timerRinging

    /**
     * Whether an event flash on the pill is being seen, so its few seconds count: the pill is showing ([islandShown]) and is a pill, not
     * open, peeking or being pulled. A flash that arrives while the island is open (the music panel, a card) or stepped aside used to play
     * its seconds out unseen and be lost; now it waits for the pill, unless it has gone stale ([FLASH_STALE_MS]).
     */
    fun flashOnScreen(flashing: Boolean, islandShown: Boolean, expanded: Boolean, peeked: Boolean, pulling: Boolean): Boolean =
        flashing && islandShown && !expanded && !peeked && !pulling

    /**
     * True when a press at [x],[y] (window pixels) lands on the collapsed pill, so a downward pull there
     * belongs to the island alone and must never also be a launcher pull-down (see LauncherScreen's
     * `canStartDownwardSwipe`).
     */
    fun pressOnIslandPill(pill: IslandFrame?, x: Float, y: Float): Boolean {
        val p = pill ?: return false
        return x >= p.left && x <= p.left + p.width && y >= p.top && y <= p.top + p.height
    }
}
