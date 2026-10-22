package com.jake.duolauncher.keyboard

/** One thing copied: its text, when it was copied, and whether the person pinned it to keep. */
internal data class Clip(val text: String, val at: Long, val pinned: Boolean = false)

/** The keyboard's clipboard history, as Fossify Keyboard has it: what was copied lately, to paste again, and clips pinned to keep.
 *
 * Recent clips live in memory only and fade after [RECENT_FOR_MS] (an hour, as Gboard keeps them), so a copied address or code does
 * not linger. Only pinned clips are kept on the phone ([PersonalStore]), because pinning one is asking for that. Copies Android marks
 * as sensitive (a password manager's) are never added: the service checks that before calling [add]. Pure Kotlin and unit-tested.
 */
internal class ClipHistory(private val clock: () -> Long) {
    private val clips = ArrayList<Clip>()

    @Synchronized fun add(text: String) {
        val t = text.take(MAX_LENGTH)
        if (t.isBlank()) return
        val pinned = clips.firstOrNull { it.text == t }?.pinned == true
        clips.removeAll { it.text == t }
        clips.add(0, Clip(t, clock(), pinned))
        prune()
    }

    /** Pinned clips first (most recently pinned or copied first), then the recent ones still within the hour. */
    @Synchronized fun list(): List<Clip> { prune(); return clips.filter { it.pinned } + clips.filter { !it.pinned } }

    /** The newest clip if it was copied within [withinMs]: the strip offers it as a one-tap paste. */
    @Synchronized fun fresh(withinMs: Long = FRESH_FOR_MS): Clip? = clips.firstOrNull()?.takeIf { clock() - it.at <= withinMs }

    @Synchronized fun setPinned(text: String, pinned: Boolean) {
        val i = clips.indexOfFirst { it.text == text }
        if (i >= 0) clips[i] = clips[i].copy(pinned = pinned)
        prune()
    }

    @Synchronized fun remove(text: String) { clips.removeAll { it.text == text } }

    /** Removes every recent clip; pinned ones stay. */
    @Synchronized fun clearRecent() { clips.removeAll { !it.pinned } }

    @Synchronized fun pinned(): List<Clip> = clips.filter { it.pinned }

    /** Restores the pinned clips kept on the phone (recents never are). */
    @Synchronized fun restorePinned(pinned: List<Clip>) {
        clips.removeAll { it.pinned }
        pinned.forEach { p -> if (clips.none { it.text == p.text }) clips += p.copy(pinned = true) }
    }

    private fun prune() {
        val now = clock()
        clips.removeAll { !it.pinned && now - it.at > RECENT_FOR_MS }
        val recent = clips.filter { !it.pinned }
        if (recent.size > MAX_RECENT) clips.removeAll(recent.drop(MAX_RECENT).toSet())
        val pinned = clips.filter { it.pinned }
        if (pinned.size > MAX_PINNED) clips.removeAll(pinned.drop(MAX_PINNED).toSet())
    }

    companion object {
        const val RECENT_FOR_MS = 60 * 60 * 1000L
        const val FRESH_FOR_MS = 90 * 1000L
        const val MAX_RECENT = 20
        const val MAX_PINNED = 50
        const val MAX_LENGTH = 20_000

        /** A clip's text on one short line, for the strip and the clipboard panel. */
        fun preview(text: String, max: Int = 60): String {
            val one = text.replace(Regex("\\s+"), " ").trim()
            return if (one.length <= max) one else one.take(max - 1).trimEnd() + "…"
        }
    }
}
