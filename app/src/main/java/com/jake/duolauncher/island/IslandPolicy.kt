package com.jake.duolauncher.island

/**
 * The island's timing and clean-up rules, as plain functions. DynamicIsland's effects ask these and do what they say, so the rules are
 * unit-tested (IslandPolicyTest) and are one place, not spread through the composable. This is the first step of the roadmap's "one owner
 * for the island's state and effects": the effects can later move to a single controller without the rules changing.
 */
internal object IslandPolicy {
    /** How long an event flash shows before the island moves on to the next one (or back to the clock). */
    const val FLASH_MS = 2_600L
    /** An open island with nothing in particular on it tucks back in after this long. */
    const val IDLE_COLLAPSE_MS = 5_000L
    /** Longer while music plays, since that is when its buttons are being used. */
    const val PLAYING_COLLAPSE_MS = 12_000L
    /** A reply being typed into a notification card. */
    const val EDITING_COLLAPSE_MS = 30_000L

    /** What an idle island does next, and after how long. */
    sealed interface IdleStep {
        /** Nothing: it is held open (a finger on it, a pull in progress) or already closed. */
        data object Stay : IdleStep
        /** A peek is a glance: it ends by itself. */
        data class EndPeek(val afterMs: Long) : IdleStep
        /** The open island closes. */
        data class Collapse(val afterMs: Long) : IdleStep
    }

    /** [peekSeconds] is the open notification card's own time (chosen per app), or null when no card is showing. */
    fun idleStep(expanded: Boolean, peeked: Boolean, holdingOpen: Boolean, pulling: Boolean, peekSeconds: Int?, editing: Boolean,
        playing: Boolean): IdleStep = when {
        holdingOpen || pulling -> IdleStep.Stay
        peeked && !expanded -> IdleStep.EndPeek(IslandDrag.PEEK_HOLD_MS)
        !expanded -> IdleStep.Stay
        peekSeconds != null && editing -> IdleStep.Collapse(EDITING_COLLAPSE_MS)
        peekSeconds != null -> IdleStep.Collapse(peekSeconds * 1000L)
        playing -> IdleStep.Collapse(PLAYING_COLLAPSE_MS)
        else -> IdleStep.Collapse(IDLE_COLLAPSE_MS)
    }

    /** A pinned activity that has ended is forgotten, so it does not jump to the front again if it restarts much later. */
    fun forgetFront(front: LiveKind?, live: Collection<LiveKind>): Boolean = front != null && front !in live

    /** A player that closes (its session ends) takes its controls with it, unless audio is still playing. */
    fun endPlaybackOnClose(sessionPresent: Boolean, audioPlaying: Boolean): Boolean = !sessionPresent && !audioPlaying

    /** A timer whose alarm never arrived (the process was stopped, or Android delayed it) still rings once its end has passed. */
    fun timerOverdue(timerActive: Boolean, ringing: Boolean, nowMs: Long, endAtMs: Long): Boolean = timerActive && !ringing && nowMs >= endAtMs

    /** When the camera, microphone or screen recording came on: now when one starts, kept while any stays on, 0 when all are off. */
    fun privacySince(anyActive: Boolean, nowMs: Long): Long = if (anyActive) nowMs else 0L
}
