package com.jake.duolauncher.search.agent

import com.jake.duolauncher.AppEntry

/** Where an action's effect lands. Confirmation is a property of this, not of how risky something feels: a result you can see
 *  needs no tap, and an effect you cannot see (a message that leaves the phone) does. */
internal enum class Effect { Local, LeavesPhone }

/** What a tool needs from the phone before it can be offered at all. Tools whose capability is missing are simply not listed. */
internal enum class Capability { Contacts, CalendarRead, Notifications, Accessibility, OwnApp }

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
    fun openAlarm(hour: Int?, minute: Int?, inMinutes: Int?)
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
) {
    /** Checks requiredness, type, range and enum membership. A tool overrides this only for a rule of its own. */
    open fun validate(raw: Map<String, String>): Validation<Params> {
        val known = params.mapTo(mutableSetOf()) { it.name }
        val filtered = raw.filterKeys { it in known }
        for (p in params) {
            val value = filtered[p.name]?.trim().orEmpty()
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
        return Validation.Ok(Params(filtered))
    }

    abstract suspend fun execute(params: Params, env: ToolEnv): ToolResult
}
