package com.jake.duolauncher.island

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.sqrt

/** How the open island's first lines are laid out round a camera at the top. Pure, so it is unit-tested. */
internal object OpenIslandLayout {
    /** The panel's side padding, and the clear space kept between what sits beside the camera and the camera. */
    const val SIDE_DP = 18f
    const val CAMERA_GAP_DP = 12f
    /** Narrower than this, the date or the battery would not fit beside the camera. */
    const val MIN_EAR_DP = 84f
    /** The clear space kept between what sits beside the camera and the panel's rounded corner. */
    const val CORNER_CLEAR_DP = 8f
    const val MAX_HERO_LINES = 2

    /** The room, in dp, on the left and right of a camera hole running from [holeLeftDp] to [holeRightDp] in a panel [panelWidthDp] wide;
     *  null when either side is too narrow to use (the panel then keeps the date under the time). */
    fun ears(holeLeftDp: Float, holeRightDp: Float, panelWidthDp: Float): Pair<Float, Float>? {
        val left = holeLeftDp - SIDE_DP - CAMERA_GAP_DP
        val right = panelWidthDp - holeRightDp - SIDE_DP - CAMERA_GAP_DP
        return if (left >= MIN_EAR_DP && right >= MIN_EAR_DP) left to right else null
    }

    /** A formatted time split into its digits and whatever follows them ("12:41" and " PM"), so the two can be set at different sizes. */
    /** How far, in dp, what sits in the band beside the camera has to come in from the panel's side beyond [SIDE_DP] so its top clears the panel's rounded
     *  corner by [CORNER_CLEAR_DP]: the corner curves away just where that band is, and text at the plain side padding all but touched it. [cornerDp] is the
     *  panel's corner radius and [textTopDp] how far down the panel the text's top is. */
    fun earIndent(cornerDp: Float, textTopDp: Float): Float {
        val fromCorner = (cornerDp - textTopDp).coerceAtLeast(0f)
        val curve = cornerDp - sqrt((cornerDp * cornerDp - fromCorner * fromCorner).coerceAtLeast(0f))
        return (curve + CORNER_CLEAR_DP - SIDE_DP).coerceAtLeast(0f)
    }

    /** The small lines opposite the time, in the order they are shown top to bottom. */
    enum class HeroLine { CLOCK, NOTE, CHARGE, ALARM }

    /** Which of them show: at most [MAX_HERO_LINES], or they would be taller than the time and push the buttons down. A running timer or stopwatch is
     *  worth the most and the label about what the panel is showing the least. */
    fun heroLines(clock: Boolean, note: Boolean, charge: Boolean, alarm: Boolean): List<HeroLine> =
        buildList { if (clock) add(HeroLine.CLOCK); if (charge) add(HeroLine.CHARGE); if (alarm) add(HeroLine.ALARM); if (note) add(HeroLine.NOTE) }
            .take(MAX_HERO_LINES).sorted()

    /** The label opposite the time, or null when what is below it already says so (a music card does not need "Now playing" over it) or the time left
     *  on a running timer or stopwatch is there instead. */
    fun heroNote(narration: String?, rowBelowSaysIt: Boolean, clockShown: Boolean): String? = narration.takeUnless { rowBelowSaysIt || clockShown }

    fun splitMeridiem(time: String): Pair<String, String> {
        val end = time.indexOfLast { it.isDigit() } + 1
        return if (end <= 0) time to "" else time.substring(0, end) to time.substring(end)
    }
}

/** The open panel's height budget for the rows that depend on what the person has set up, so the panel is never shorter than its content. */
internal object PanelHeight {
    /** The feed headline under the music or the clock: its spacer and two lines of text. */
    const val HEADLINE_ROW_DP = 42f
    /** The same with one line of text, for a panel that already has something else to show. */
    const val HEADLINE_ONE_LINE_DP = 24f
    /** The text in the panel (the time, the date, the headline, the track) is sized in sp, so a larger system font needs a taller panel. This much
     * of the panel's height is text; it grows with the font scale. */
    const val TEXT_BUDGET_DP = 80f
    /** Fonts are scaled up to this much for the panel's height; beyond it the text is clipped rather than the island taking the whole screen. */
    const val MAX_FONT_SCALE = 1.6f
    /** The most [extraDp] can add, for the overlay window that has to be big enough for the open island in every case. */
    const val MAX_EXTRA_DP = HEADLINE_ROW_DP + TEXT_BUDGET_DP * (MAX_FONT_SCALE - 1f)
    /** The open panel's horizontal padding (18 dp each side), which the action row must fit between. */
    const val FACE_PADDING_DP = 36f
    /** The usable width, in dp, at which all three action labels (Search / Feed / Settings) fit in Inter. */
    const val ACTION_LABELS_MIN_DP = 270f

    /** The feed headline's lines: it is the panel's only news when the panel is otherwise idle, and a footnote when it has a card or music to show. */
    fun headlineLines(panelBusy: Boolean): Int = if (panelBusy) 1 else 2

    fun extraDp(hasHeadline: Boolean, fontScale: Float, headlineLines: Int = 2): Float =
        (if (!hasHeadline) 0f else if (headlineLines <= 1) HEADLINE_ONE_LINE_DP else HEADLINE_ROW_DP) + TEXT_BUDGET_DP * (fontScale.coerceIn(1f, MAX_FONT_SCALE) - 1f)

    /** Whether the three action labels fit across a panel [panelWidthDp] wide at the system [fontScale]. Inter runs wider than the
     * system font, and the labels scale with it, so below the threshold the buttons drop to icon-only instead of clipping. */
    fun actionsFit(panelWidthDp: Float, fontScale: Float): Boolean =
        panelWidthDp - FACE_PADDING_DP >= ACTION_LABELS_MIN_DP * fontScale.coerceIn(1f, MAX_FONT_SCALE)
}

/** The next alarm, in a few characters, for the open panel: "7:30 AM" when it rings within a day, otherwise with the weekday ("Mon 7:30 AM"). */
internal object NextAlarmText {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun format(triggerAtMs: Long, nowMs: Long, is24Hour: Boolean, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String? {
        if (triggerAtMs <= nowMs) return null
        val time = DateTimeFormatter.ofPattern(com.jake.duolauncher.ClockFormat.timePattern(is24Hour, meridiem = true), locale)
        val at = Instant.ofEpochMilli(triggerAtMs).atZone(zone)
        val clock = at.format(time)
        return if (triggerAtMs - nowMs <= DAY_MS) clock else "${at.format(DateTimeFormatter.ofPattern("EEE", locale))} $clock"
    }
}

/** Track times beside the seek bar: elapsed on the left, what is left (with a minus) on the right. */
internal object MediaTimes {
    /** m:ss, or h:mm:ss from an hour. Rounds down: a track at 59.9 seconds is still at 0:59. */
    fun clock(ms: Long): String {
        val total = ms.coerceAtLeast(0L) / 1000L
        val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, s) else "%d:%02d".format(Locale.ROOT, m, s)
    }

    fun elapsed(fraction: Float, durationMs: Long): String = clock((fraction.coerceIn(0f, 1f) * durationMs).toLong())
    fun remaining(fraction: Float, durationMs: Long): String = "-" + clock(((1f - fraction.coerceIn(0f, 1f)) * durationMs).toLong())
}

/** What a screen reader says for the island at rest: its name, then what is live on it, most urgent first. */
internal object IslandSpeech {
    fun describe(flash: String?, ringing: Boolean, onCall: Boolean, timer: Boolean, stopwatch: Boolean, playing: Boolean,
        charging: Boolean = false): String = buildList {
        add("Dynamic island")
        if (flash != null) add(flash)
        if (ringing) add("timer finished")
        if (onCall) add("on a call")
        if (timer && !ringing) add("timer running")
        if (stopwatch) add("stopwatch running")
        if (charging) add("charging")
        if (playing) add("music playing")
    }.joinToString(", ")
}

/**
 * The open panel's first line: a short, honest label for *why* the panel is showing what it is showing. It names the one live
 * thing that currently has the island, most important first, and is null when the panel is simply idle (the clock and shortcuts).
 */
internal object IslandNarration {
    fun label(
        cardShown: Boolean,
        callActive: Boolean,
        navigating: Boolean,
        ringing: Boolean,
        timerActive: Boolean,
        stopwatchRunning: Boolean,
        mediaShown: Boolean,
        liveUpdate: Boolean,
    ): String? = when {
        cardShown -> "New notification"
        callActive -> "You're on a call"
        navigating -> "You're navigating"
        ringing -> "Timer finished"
        timerActive -> "Timer"
        stopwatchRunning -> "Stopwatch"
        mediaShown -> "Now playing"
        liveUpdate -> "Live update"
        else -> null
    }
}

/** The live-activity facts whose arrivals and departures the island speaks aloud, once, at the point they change. */
internal data class IslandLiveFacts(
    val ringing: Boolean = false,
    val call: Boolean = false,
    val timer: Boolean = false,
    val playing: Boolean = false,
)

/** What TalkBack hears when the live facts change. A timer that finishes (ringing) speaks first, and silencing it is not said;
 * every other fact says both its start and its end, so "Call ended" and "Music paused" are heard too, not just their arrivals. */
internal object IslandAnnounce {
    fun transition(prev: IslandLiveFacts, now: IslandLiveFacts): String? = when {
        now.ringing && !prev.ringing -> "Timer finished"
        prev.ringing && !now.ringing -> null // the person just silenced it
        now.call && !prev.call -> "Call started"
        prev.call && !now.call -> "Call ended"
        now.timer && !prev.timer -> "Timer started"
        prev.timer && !now.timer -> "Timer ended"
        now.playing && !prev.playing -> "Music playing"
        prev.playing && !now.playing -> "Music paused"
        else -> null
    }
}
