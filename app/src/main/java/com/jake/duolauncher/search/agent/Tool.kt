package com.jake.duolauncher.search.agent

import com.jake.duolauncher.AppEntry

/** Where an action's effect lands. Confirmation is a property of this, not of how risky something feels: a result you can see
 *  needs no tap, and an effect you cannot see (a message that leaves the phone) does. */
internal enum class Effect { Local, LeavesPhone }

/** What a tool needs from the phone before it can be offered at all. Tools whose capability is missing are simply not listed. */
internal enum class Capability { Contacts, CalendarRead, Notifications, Accessibility, OwnApp,
    /** May change the ringer (ring, vibrate, silent): needs Do Not Disturb access for silent. */
    Ringer,
    /** May write Android's own settings (screen brightness): "Modify system settings", given by the person. */
    SystemSettings }

internal enum class Kind { Int, Text, Bool, Enum }

/** One parameter of a tool: its name, its kind, and the bounds the grammar and the validation both use. */
internal class Param(
    val name: String,
    val kind: Kind,
    val required: Boolean = true,
    val choices: List<String> = emptyList(),
    val min: Int = Int.MIN_VALUE,
    val max: Int = Int.MAX_VALUE,
    val maxLength: Int = 120,
)

/** The values a plan supplied, as strings: the model writes JSON, so everything arrives as text and is coerced here. */
internal class Params(val values: Map<String, String>) {
    fun int(name: String): Int? = values[name]?.trim()?.toIntOrNull()
    fun text(name: String): String? = values[name]?.trim()?.takeIf { it.isNotEmpty() }
    fun bool(name: String): Boolean? = when (values[name]?.trim()?.lowercase()) {
        "true", "on", "yes", "1" -> true
        "false", "off", "no", "0" -> false
        else -> null
    }
}

internal sealed interface Validation<out T> {
    data class Ok<T>(val value: T) : Validation<T>
    data class Rejected(val why: String) : Validation<Nothing>
}

/** What a tool did, said in a sentence. `say` is the confirmation for a silent action, so it names the result, never "Done".
 *  [undo] restores what the tool changed, when it can. */
internal class ToolResult(val ok: Boolean, val say: String, val undo: (suspend () -> ToolResult)? = null)

/**
 * Everything a tool may touch. Tools never see the model, the loop or the UI, so each one is a small, testable function of this interface —
 * a fake implementation drives the unit tests.
 */
internal interface ToolEnv {
    val noteSlot: Int?
    val apps: List<AppEntry>
    fun focusOn(): Boolean
    fun toggleFocus()
    fun startTimer(seconds: Long)
    fun cancelTimer()
    /** True when the stopwatch is now running. */
    fun toggleStopwatch(): Boolean
    /** True when the torch is now on. */
    fun toggleTorch(): Boolean
    /** Turns the torch on or off, whatever it was. */
    fun setTorch(on: Boolean) { toggleTorch() }
    fun stopwatchRunning(): Boolean = false
    /** Whether the stopwatch has any time on it, running or stopped. */
    fun stopwatchStarted(): Boolean = stopwatchRunning()
    fun resetStopwatch() {}
    fun timerRunning(): Boolean = false
    /** The time now, for a tool that turns "tomorrow at 3" into a moment. */
    fun now(): java.time.LocalDateTime = java.time.LocalDateTime.now()
    /** Opens the messages app at [number] with [text] typed in and not sent. */
    fun openMessage(number: String, text: String) = openMessages(number)
    /** "play", "pause", "next" or "previous" on what is playing; false when nothing is. */
    fun media(action: String): Boolean = false
    /** Media volume as a percentage, or null when it cannot be read. */
    fun volume(): Int? = null
    /** Sets media volume to [percent]; false when Android refused. */
    fun setVolume(percent: Int): Boolean = false
    /** The Checklist widget's slot on Home, or null when there is none. */
    val checklistSlot: Int? get() = null
    /** Adds [item] to the checklist; false when it is already there or the list is full. */
    fun addChecklist(item: String): Boolean = false
    fun removeChecklist(item: String) {}
    /** Hands [place] to the maps app as a search. */
    fun openDirections(place: String) {}
    fun openAlarm(hour: Int?, minute: Int?, inMinutes: Int?)
    /** The same, named [label] so it rings with its reason on it (a reminder). */
    fun openAlarm(hour: Int?, minute: Int?, inMinutes: Int?, label: String?) = openAlarm(hour, minute, inMinutes)
    /** The launcher's Style now ("calm", "balanced" or "expressive"), or null when it cannot be read from here. */
    fun style(): String? = null
    /** Sets the launcher's Style; false when it could not be set. */
    fun setStyle(level: String): Boolean = false
    /** Opens the launcher's own settings ("all") or its keyboard's ("keyboard"); false when it could not. */
    fun openUnoSettings(page: String): Boolean = false
    /** Opens the app; false when Android would not start it (the entry is stale, the profile is gone, or it is not allowed). */
    fun openApp(entry: AppEntry): Boolean
    fun openDialer(number: String)
    fun openMessages(number: String)
    fun openUrl(url: String)
    /** Opens the calendar's editor with the event prefilled; nothing is written until the person saves there. */
    fun openCalendar(title: String, inMinutes: Int, durationMinutes: Int)
    fun openWebSearch(query: String)
    /** Opens one of Android's own settings pages: "wifi", "bluetooth", "nfc", "airplane", "display", "sound", "battery". */
    fun openSettingsPanel(panel: String)
    /** Writes the note, returning what it held before (for undo); null when there is no Note widget on Home. */
    fun writeNote(text: String): String?
    /** What is in the calendar from now for the next few days, soonest first; empty when it cannot be read. Read only for a step that
     *  points at the calendar ("before my first meeting"), never to fill a prompt. */
    fun events(): List<CalendarEvent> = emptyList()
    // ---- Protocol 2's tools (tools/MoreTools.kt). Each says no until a phone says otherwise, so a tool is offered only where it works.
    /** Opens the camera for a "photo", a "selfie" or a "video"; false when there is no camera app. */
    fun openCamera(mode: String): Boolean = false
    /** Opens a draft email; false when there is no mail app. */
    fun composeEmail(to: String, subject: String, body: String): Boolean = false
    /** "ring", "vibrate" or "silent"; null when it cannot be read. */
    fun ringer(): String? = null
    fun setRinger(mode: String): Boolean = false
    /** Asks the music app to play [what] by name; false when no app takes the request. */
    fun playFromSearch(what: String): Boolean = false
    /** Screen brightness as a percentage; null when it cannot be read. */
    fun brightness(): Int? = null
    fun setBrightness(percent: Int): Boolean = false
    /** "screenshot", "lock", "notifications", "quick_settings" or "recents". */
    fun deviceAction(action: String): Boolean = false
    /** Opens the new-contact form filled in; false when there is no contacts app. */
    fun addContact(name: String, number: String): Boolean = false
    fun openAppInfo(entry: AppEntry): Boolean = false
    fun showAlarms(): Boolean = false
    fun openCalendarAt(day: java.time.LocalDate): Boolean = false
    /** What the phone can offer right now; a tool that needs something not in here is not offered (ToolRegistry.available). */
    fun capabilities(): Set<Capability> = emptySet()
    /** The messages that can be answered from their notification, newest first: who sent each and in which app. Never their words. */
    fun replyTargets(): List<ReplyTarget> = emptyList()
    /** Sends [text] as the reply to the notification [key] names; false when it is gone or the app refused it. */
    fun sendReply(key: String, text: String): Boolean = false
}

/** Something a step needs that only the person can give: the [question] to ask, and the [param] their answer fills, when one value
 *  is all that is missing (the turn then carries on from the answer without planning again). */
internal data class Missing(val question: String, val param: String? = null)

/**
 * A value with the request's own command words taken off its front. A small model asked to "pencil in physio at 3" may call the event
 * "pencil in physio": the first words of the value are the first words of the request, and they are a way of asking, not part of
 * what was asked for. Only the phrases a tool lists are taken off, and only when the request itself begins with them, so "add pop
 * tarts to my list" keeps its "pop". Pure, so it is unit-tested.
 */
internal object LeadIn {
    private val polite = Regex("""^(?:(?:ok|okay|hey|hi|please|can you|could you|would you|will you)[\s,]+)+""", RegexOption.IGNORE_CASE)

    /** The first line of [said] (the request) without its opening courtesies. */
    fun request(said: String): String = said.lineSequence().firstOrNull().orEmpty().trim().replace(polite, "")

    /** [value] without the one of [phrases] that both it and the request begin with; [value] itself when there is none, when nothing
     *  would be left, or when [only] says the request is not of the kind the phrase belongs to. */
    fun stripped(value: String, said: String, phrases: List<String>, only: (String) -> Boolean = { true }): String {
        val request = request(said)
        val text = value.trim()
        if (!only(request)) return value
        for (phrase in phrases) {
            if (!text.startsWith("$phrase ", ignoreCase = true) || !request.startsWith("$phrase ", ignoreCase = true)) continue
            return text.substring(phrase.length).trim().ifEmpty { return value }
        }
        return value
    }
}

/** A message that can be answered from its notification: the notification's key, who it is from, the app, and when it came. */
internal data class ReplyTarget(val key: String, val who: String, val app: String, val postedAt: Long)

/** Which waiting message a reply is for. Pure, so every case is unit-tested. */
internal object ReplyLogic {
    const val MAX_TEXT = 500
    private val newest = setOf("", "that", "it", "this", "them", "him", "her", "last", "latest", "the last message", "the latest message",
        "the message", "that message", "the last one", "the notification")

    sealed interface Found {
        data class One(val target: ReplyTarget) : Found
        data object None : Found
        data class Many(val targets: List<ReplyTarget>) : Found
    }

    /**
     * [to] is a sender's name, an app's, or a word for "the newest one". A name that fits two different people is not guessed between:
     * the person is asked. Several messages from one person in one app are one conversation, and the newest of them is answered.
     */
    fun resolve(to: String, targets: List<ReplyTarget>): Found {
        val byTime = targets.sortedByDescending { it.postedAt }
        if (byTime.isEmpty()) return Found.None
        val asked = to.trim().lowercase()
        if (asked in newest) return Found.One(byTime.first())
        val byName = byTime.filter { GroundingLogic.mentions(asked, it.who) }
        val matches = byName.ifEmpty { byTime.filter { GroundingLogic.mentions(asked, it.app) } }
        val conversations = matches.distinctBy { it.who.lowercase() to it.app.lowercase() }
        return when (conversations.size) {
            0 -> Found.None
            1 -> Found.One(conversations.first())
            else -> Found.Many(conversations)
        }
    }

    /** Whether [to] is a word for "the newest one" and not a name. */
    fun isNewest(to: String): Boolean = to.trim().lowercase() in newest

    fun nobody(to: String): String =
        if (to.isBlank() || to.trim().lowercase() in newest) "I don't see a message to reply to."
        else "I don't see a message from ${to.trim()} to reply to."

    fun which(targets: List<ReplyTarget>): String =
        "Which one: " + targets.take(4).joinToString(", ") { "${it.who} in ${it.app}" } + "?"

    /** A sender's or app's name as it may be shown and given to the planner: one line, no markup, not long. Others wrote it. */
    fun clean(name: String): String = name.filter { !it.isISOControl() && it !in "<>[]{}|`\"" }.trim().take(40)
}

/**
 * One capability of the assistant. Implements nothing about prompts, grammars or the loop: those are generated from the registry, so a new
 * tool is one file and one line.
 */
internal abstract class Tool(
    val name: String,
    val summary: String,
    val params: List<Param> = emptyList(),
    val effect: Effect,
    val needs: Set<Capability> = emptySet(),
    /** A few requests this tool is the answer to, in the person's words. The planner reads them beside the summary: on the phone a
     *  2B model picked the wrong tool for "remember to buy bread" and "time my swim" until it was shown what each is for. */
    val examples: List<String> = emptyList(),
) {
    /** Checks requiredness, type, range and enum membership. A tool overrides this only for a rule of its own. */
    open fun validate(raw: Map<String, String>): Validation<Params> {
        val known = params.mapTo(mutableSetOf()) { it.name }
        val filtered = raw.filterKeys { it in known }
        // An optional value the model filled in only because it had to write something ("none", or a 0 below the smallest allowed)
        // is the same as leaving it out. Seen on the phone: "alarm in 30 minutes" came back with at:"none".
        val given = filtered.filter { (key, raw) ->
            val p = params.first { it.name == key }
            val value = raw.trim()
            p.required || !(value.lowercase() in PLACEHOLDERS || (p.kind == Kind.Int && value.toIntOrNull()?.let { it < p.min } == true))
        }
        for (p in params) {
            val value = given[p.name]?.trim().orEmpty()
            if (value.isEmpty()) {
                if (p.required) return Validation.Rejected("$name needs ${p.name}")
                continue
            }
            when (p.kind) {
                Kind.Int -> {
                    val n = value.toIntOrNull() ?: return Validation.Rejected("${p.name} must be a whole number")
                    if (n < p.min || n > p.max) return Validation.Rejected("${p.name} must be between ${p.min} and ${p.max}")
                }
                Kind.Bool -> if (value.lowercase() !in setOf("true", "false", "on", "off", "yes", "no", "1", "0"))
                    return Validation.Rejected("${p.name} must be true or false")
                Kind.Enum -> if (value !in p.choices) return Validation.Rejected("${p.name} must be one of ${p.choices.joinToString()}")
                Kind.Text -> if (value.length > p.maxLength) return Validation.Rejected("${p.name} is too long")
            }
        }
        return Validation.Ok(Params(given))
    }

    abstract suspend fun execute(params: Params, env: ToolEnv): ToolResult

    private companion object { val PLACEHOLDERS = setOf("none", "null", "n/a", "na", "unused", "-") }

    /**
     * Whether the plan made up something only the person can supply (a web address, a phone number): null when the value is in what
     * they [said] (their request and the names and numbers Uno resolved for it), otherwise a sentence asking for it. On the phone the
     * model answered "order me a pizza" with a pizza site's address it had invented; nothing the person did not give is opened or dialled.
     */
    open fun invented(params: Params, said: String): String? = null

    /** [invented], with the parameter the person's answer would fill. A tool with one value to ask for overrides this. */
    open fun missing(params: Params, said: String): Missing? = invented(params, said)?.let { Missing(it) }

    /**
     * The step with a value the planner got wrong put right from the person's own words, where there is no doubt what they said: a
     * model that turns "tomorrow at 3pm" into "03:00" is corrected, not obeyed and not questioned. Most tools have nothing to mend.
     */
    open fun mended(params: Params, said: String): Params = params

    /**
     * A different step that is plainly what was meant, where this one cannot be: a message to a number nobody gave, when a message
     * from the person named is waiting, is a reply to it. Null when the step stands, which is nearly always. The loop asks every step
     * once, after [mended] and before anything is checked, shown or run, so what replaces a step is checked like any other. [request]
     * is the person's own words; [said] is those and what Uno resolved for them; [tools] are the ones on offer.
     */
    open fun instead(params: Params, request: String, said: String, env: ToolEnv, tools: List<Tool>): Step? = null

    /** Settles, once and before anything is shown or run, what the step will act on (which message a reply is for). The loop shows
     *  and runs the returned values, so what was confirmed is what is done. Most tools have nothing to settle. */
    open fun bind(params: Params, env: ToolEnv): Params = params

    /** Why the step cannot be done as asked, as a sentence for the person (nobody to reply to, two people it could mean); null when
     *  it can. Asked before a confirmation, so nobody is asked to approve something that would fail. */
    open fun problem(params: Params, env: ToolEnv): String? = null

    /** The step in words for the confirmation ("Reply to Sam in Messages: …"); null for the plain form (the tool and its values). */
    open fun describe(params: Params, env: ToolEnv): String? = null
}
