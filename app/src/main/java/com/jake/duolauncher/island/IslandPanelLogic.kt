package com.jake.duolauncher.island

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The open panel's height budget for the rows that depend on what the person has set up, so the panel is never shorter than its content. */
internal object PanelHeight {
    /** The feed headline under the music or the clock: its spacer and two lines of text. */
    const val HEADLINE_ROW_DP = 42f
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

    fun extraDp(hasHeadline: Boolean, fontScale: Float): Float =
        (if (hasHeadline) HEADLINE_ROW_DP else 0f) + TEXT_BUDGET_DP * (fontScale.coerceIn(1f, MAX_FONT_SCALE) - 1f)

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
        charging: Boolean,
    ): String? = when {
        cardShown -> "New notification"
        callActive -> "You're on a call"
        navigating -> "You're navigating"
        ringing -> "Timer finished"
        timerActive -> "Timer"
        stopwatchRunning -> "Stopwatch"
        mediaShown -> "Now playing"
        liveUpdate -> "Live update"
        charging -> "Charging"
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
