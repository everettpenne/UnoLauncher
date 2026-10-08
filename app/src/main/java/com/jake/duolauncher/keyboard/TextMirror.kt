package com.jake.duolauncher.keyboard

/** A local copy of the text just before the cursor, kept up to date from what the keyboard itself types and deletes.
 *
 * Asking the app for that text is a round trip on every keystroke, which is the main cost of fast typing. This lets
 * the keyboard answer from memory and only ask when the app changed the text or moved the cursor itself. When in doubt
 * it marks itself invalid and the caller reads from the app instead, so it can be wrong only by being slower.
 */
internal class TextMirror(private val keep: Int = 80) {
    private val text = StringBuilder()
    var valid = false; private set

    fun reset(before: CharSequence) { text.setLength(0); text.append(before.takeLast(keep)); valid = true }
    fun invalidate() { valid = false }
    fun commit(typed: String) { if (!valid) return; text.append(typed); trim() }
    /** Deleting more than the copy holds means the real text goes further back than we know. */
    fun delete(count: Int) { if (!valid) return; if (count > text.length) valid = false else text.setLength(text.length - count) }
    fun get(): String = text.toString()
    private fun trim() { if (text.length > keep * 2) text.delete(0, text.length - keep) }
}

/** Remembers the cursor positions our own edits should produce, so the selection updates the app sends back for them
 * are recognised and ignored, and only a cursor that ended up somewhere unexpected triggers a re-read.
 */
internal class ExpectedCursor(private val size: Int = 24) {
    private val recent = ArrayDeque<Int>()
    fun expect(position: Int) { recent.addLast(position); if (recent.size > size) recent.removeFirst() }
    fun isOurs(position: Int) = position in recent
    fun clear() = recent.clear()
}
