package com.jake.duolauncher.island

/**
 * Which activity the pill should lead with right now because it is about to matter, as iOS's island brings an activity forward at its
 * moment and lets it go after. A call always leads; a timer leads in its last minute; a navigation instruction leads when the turn is
 * close; a Live Update leads when it says it is arriving. Otherwise nothing is urgent and the person's own choice (a flick) or the fixed
 * order stands. Pure, so the rules are unit-tested.
 */
internal object IslandUrgency {
    /** A timer comes forward for its last this-many milliseconds. */
    const val TIMER_FINAL_MS = 60_000L
    /** A turn this close (metres) brings navigation forward. */
    const val TURN_NEAR_M = 200

    private val ARRIVING = Regex("""\b(arriving|arrives now|arrived|is here|is outside|outside now|almost there|approaching|0 min|1 min)\b""",
        RegexOption.IGNORE_CASE)

    /** Whether a Live Update's words say it is arriving ("Arriving", "Driver is here", "1 min"). */
    fun arriving(text: String?): Boolean = text != null && ARRIVING.containsMatchIn(text)

    /** The activity that should lead now because it is about to matter, or null. */
    fun urgent(kinds: Collection<LiveKind>, timerRemainingMs: Long?, turnMeters: Int?, updateArriving: Boolean): LiveKind? = when {
        LiveKind.CALL in kinds -> LiveKind.CALL
        LiveKind.TIMER in kinds && timerRemainingMs != null && timerRemainingMs in 0..TIMER_FINAL_MS -> LiveKind.TIMER
        LiveKind.UPDATE in kinds && ((turnMeters != null && turnMeters <= TURN_NEAR_M) || updateArriving) -> LiveKind.UPDATE
        else -> null
    }

    /** What leads: the urgent activity if there is one, else the one the person brought forward. */
    fun front(chosen: LiveKind?, urgent: LiveKind?): LiveKind? = urgent ?: chosen
}
