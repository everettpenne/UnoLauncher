package com.jake.duolauncher.search.agent

/** A person the planner may name, already resolved to a number. */
internal data class KnownPerson(val name: String, val number: String)

/**
 * What grounding found for a request: the people and apps it may refer to. Resolved before planning, so the model is given concrete names and
 * numbers instead of inventing them — the difference between "text Sam" working and "text Sam" being confidently addressed to nobody.
 */
internal class Grounding(
    val people: List<KnownPerson> = emptyList(),
    val apps: List<String> = emptyList(),
    /** The messages waiting as notifications that a reply could go to, newest first (who, and in which app). */
    val replies: List<ReplyTarget> = emptyList(),
) {
    val isEmpty: Boolean get() = people.isEmpty() && apps.isEmpty() && replies.isEmpty()
}

/**
 * The pure part of grounding: which of the known people and apps a request actually points at, and the `<known>` block the planner reads.
 * Only what is mentioned goes in, so a prompt does not carry every contact on the phone.
 */
internal object GroundingLogic {
    private const val MAX_PEOPLE = 4
    private const val MAX_APPS = 6
    private val word = Regex("""[\p{L}\p{N}']+""")

    private const val MAX_REPLIES = 4
    private val peopleLine = Regex("""(?m)^People: (.*)$""")
    private val person = Regex("""^(.*) \(([^()]*)\)$""")

    /** The people a `<known>` block names, read back from it ("People: Sam Patel (555 0142); Ana (555 0177)"). */
    fun peopleIn(known: String): List<KnownPerson> = peopleLine.find(known)?.groupValues?.get(1).orEmpty().split("; ")
        .mapNotNull { each -> person.find(each.trim())?.let { KnownPerson(it.groupValues[1].trim(), it.groupValues[2].trim()) } }
    private val replyWords = setOf("reply", "respond", "answer", "tell", "say", "write", "text", "message")

    /** Whether a request reads as wanting to answer a message. */
    fun aboutReplying(query: String): Boolean = word.findAll(query.lowercase()).any { it.value in replyWords }

    /** The block for the planner's `<known>` section, or "" when the request names nothing known. */
    fun block(query: String, people: List<KnownPerson>, apps: List<String>, replies: List<ReplyTarget> = emptyList()): String = buildString {
        // Senders' names are written by other people: they are given as names only, cleaned and capped, and only for a request
        // about replying or one that names the sender.
        val waiting = replies.sortedByDescending { it.postedAt }.distinctBy { it.who.lowercase() to it.app.lowercase() }
            .filter { aboutReplying(query) || mentions(query, it.who) }.take(MAX_REPLIES)
        if (waiting.isNotEmpty()) {
            append("Messages waiting for a reply (newest first): ")
            append(waiting.joinToString("; ") { "${ReplyLogic.clean(it.who)} in ${ReplyLogic.clean(it.app)}" })
            append('\n')
        }
        val chosenPeople = people.filter { mentions(query, it.name) }.take(MAX_PEOPLE)
        if (chosenPeople.isNotEmpty()) {
            append("People: ")
            append(chosenPeople.joinToString("; ") { "${it.name} (${it.number})" })
            append('\n')
        }
        val chosenApps = apps.filter { mentions(query, it) }.take(MAX_APPS)
        if (chosenApps.isNotEmpty()) {
            append("Apps: ")
            append(chosenApps.joinToString(", "))
            append('\n')
        }
    }.trimEnd()

    /**
     * Whether [query] seems to name [name]: a word of the name matched by a word of the query, whole or as a prefix of at least three letters
     * ("Sam" for "Samantha"), or the other way round ("messages" for the app "Messages").
     *
     * Prefix matching errs on the side of including: "timer" also mentions a contact called Tim. That costs one line in the prompt and the
     * prompt says to use a fact only when it answers the question, which is cheaper than missing the person the request is about.
     */
    fun mentions(query: String, name: String): Boolean {
        val asked = word.findAll(query.lowercase()).map { it.value }.toList()
        val parts = word.findAll(name.lowercase()).map { it.value }.toList()
        if (asked.isEmpty() || parts.isEmpty()) return false
        return parts.any { part ->
            part.length >= 2 && asked.any { said ->
                said == part || (part.length >= 3 && said.startsWith(part)) || (said.length >= 3 && part.startsWith(said))
            }
        }
    }

    private val contactVerb = setOf("call", "dial", "ring", "phone", "text", "message", "sms", "tell", "ask", "msg")
    private val notNames = setOf("the", "a", "an", "my", "to", "at", "on", "in", "and", "that", "this", "it", "him", "her", "them", "back", "saying",
        "please", "now", "up", "me", "for", "with", "i", "i'll", "i'm") + contactVerb

    /**
     * The words of a request that may be a person's name, to look up in contacts: one that follows a verb for reaching someone
     * ("text sam"), and any capitalised word that is not the first. At most [MAX_PEOPLE]; nothing is looked up for a request with neither.
     */
    fun namesIn(query: String): List<String> {
        val words = Regex("""[\p{L}']+""").findAll(query).map { it.value }.toList()
        val out = LinkedHashSet<String>()
        words.forEachIndexed { i, w ->
            val lower = w.lowercase()
            if (lower.length < 2 || lower in notNames) return@forEachIndexed
            val afterVerb = i > 0 && words[i - 1].lowercase() in contactVerb
            if (afterVerb || (i > 0 && w.first().isUpperCase())) out += w
        }
        return out.take(MAX_PEOPLE)
    }

    /** The request the loop runs, with the grounded names folded in. */
    fun planning(
        query: String,
        context: String,
        people: List<KnownPerson>,
        apps: List<String>,
        tools: List<Tool>,
    ): PlanRequest = PlanRequest(query, context, tools, block(query, people, apps))
}
