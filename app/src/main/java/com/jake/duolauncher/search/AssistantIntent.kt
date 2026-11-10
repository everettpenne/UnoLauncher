package com.jake.duolauncher.search

import org.json.JSONObject

/**
 * Turns the one JSON object Uno Lab's assistant writes in ACTION mode into a [QuickAction] that Spotlight shows as a row. Nothing here runs
 * anything: the row runs only when it is tapped, because the model can be wrong. Anything unknown, malformed or out of range is null, and
 * Spotlight then asks for a plain answer instead. Pure (org.json only), so it is unit-tested.
 */
internal object AssistantIntent {
    private const val MAX_TEXT = 500
    private const val MAX_NAME = 80
    private val clock = Regex("""^(\d{1,2}):(\d{2})$""")

    fun parse(json: String): QuickAction? {
        val obj = runCatching { JSONObject(extract(json) ?: return null) }.getOrNull() ?: return null
        return when (obj.optString("action")) {
            "timer" -> timer(obj)
            "alarm" -> alarm(obj)
            "stopwatch" -> QuickAction.Stopwatch()
            "torch" -> QuickAction.Torch()
            "focus" -> when (obj.opt("on")) {
                true -> QuickAction.Focus(true, "Turn Focus on", "Hides the apps you picked")
                false -> QuickAction.Focus(false, "Turn Focus off", "Shows the apps Focus hid")
                else -> null
            }
            "note" -> text(obj, "text", MAX_TEXT)?.let { QuickAction.Note(it, "Save note") }
            "open_app" -> text(obj, "name", MAX_NAME)?.let { QuickAction.OpenApp(it, "Open $it") }
            "call" -> text(obj, "name", MAX_NAME)?.let { QuickAction.Call(it, "Call $it") }
            "text" -> text(obj, "name", MAX_NAME)?.let { QuickAction.Text(it, "Text $it") }
            "web" -> text(obj, "query", HandoffLogic.MAX_QUERY)?.let { QuickAction.WebSearch(it, "Search the web for “$it”") }
            else -> null // "none", or anything the schema does not have
        }
    }

    /** The first {...} in [raw]: the grammar asks for exactly one object, but a stray space or newline around it is harmless. */
    private fun extract(raw: String): String? {
        val start = raw.indexOf('{'); val end = raw.lastIndexOf('}')
        return if (start in 0 until end) raw.substring(start, end + 1) else null
    }

    private fun text(obj: JSONObject, key: String, max: Int): String? {
        val value = obj.opt(key) as? String ?: return null
        val clean = value.filter { !it.isISOControl() }.trim()
        return clean.takeIf { it.isNotEmpty() && it.length <= max }
    }

    /** A whole number field, only if it really is one (no strings, no fractions). */
    private fun whole(obj: JSONObject, key: String): Long? = when (val v = obj.opt(key)) {
        is Int -> v.toLong()
        is Long -> v
        is Double -> v.takeIf { it == Math.floor(it) && !it.isInfinite() }?.toLong()
        is java.math.BigDecimal -> runCatching { v.longValueExact() }.getOrNull()
        else -> null
    }

    private fun timer(obj: JSONObject): QuickAction.Timer? {
        val seconds = whole(obj, "seconds") ?: return null
        if (seconds < 1 || seconds > SpotlightSources.MAX_TIMER_SECONDS) return null
        val base = SpotlightSources.timerLabel(seconds)
        val name = text(obj, "label", MAX_NAME)
        return QuickAction.Timer(seconds, if (name != null) "$base · $name" else base)
    }

    private fun alarm(obj: JSONObject): QuickAction.Alarm? {
        if (obj.has("in_minutes")) {
            val minutes = whole(obj, "in_minutes") ?: return null
            if (minutes < 1 || minutes > SpotlightSources.MAX_ALARM_MINUTES) return null
            return QuickAction.Alarm(AlarmTarget.In(minutes.toInt()), SpotlightSources.alarmInLabel(minutes.toInt()))
        }
        val m = clock.matchEntire((obj.opt("at") as? String)?.trim() ?: return null) ?: return null
        val hour = m.groupValues[1].toInt(); val minute = m.groupValues[2].toInt()
        if (hour !in 0..23 || minute !in 0..59) return null
        return QuickAction.Alarm(AlarmTarget.At(hour, minute), SpotlightSources.alarmClockLabel(hour, minute))
    }
}

/** When Spotlight offers to ask Uno Lab's assistant. Pure, so it is unit-tested. */
internal object AskPolicy {
    const val MAX_QUERY = 300

    /**
     * A question, not a name: two words at least, and nothing already answers it. A sum or a conversion ([smart]) or a typed command
     * ([quick]) is answered on the spot, so the model is never offered for it. Nothing runs here: the row only sends the query when tapped.
     */
    fun offer(query: String, smart: SmartAnswer?, quick: QuickAction?, state: AssistantState): Boolean {
        val q = query.trim()
        if (q.isEmpty() || q.length > MAX_QUERY) return false
        if (state != AssistantState.READY && state != AssistantState.LOADING) return false
        if (smart != null || quick != null) return false
        return q.split(Regex("""\s+""")).count { w -> w.any { it.isLetterOrDigit() } } >= 2
    }
}
