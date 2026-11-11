package com.jake.duolauncher.search

/**
 * The messages that need no model at all: a greeting, a thank-you, a goodbye, or "who are you".
 *
 * A 2B model on a phone CPU spends seconds on these — a whole action round trip that returns "no action", then a prefill of the phone's
 * facts, then the greeting itself — and the answer is the same every time. So it is a table.
 *
 * Matched on the *whole* normalised question, never as a prefix: "hello, what's the weather" and "thanks, now set a timer" are not small
 * talk, and they fall through to Uno Lab as before. Pure, so it is unit-tested.
 */
internal object SmallTalk {
    /** The reply for [query], or null when it is not only small talk. */
    fun replyTo(query: String): String? = replies[normalise(query)]

    /** Lowercased, with punctuation and spacing flattened: "  Hello,  Qwen! " is "hello qwen". Apostrophes stay: "what's". */
    fun normalise(query: String): String =
        query.lowercase().replace(Regex("""[^\p{L}\p{N}\s']"""), " ").replace(Regex("""\s+"""), " ").trim()

    private const val HELLO = "Hi — Qwen here, running on your phone. Ask me anything."
    private const val GREETINGS = "Hello. What would you like to know?"
    private const val THANKS = "You're welcome."
    private const val BYE = "Bye — I'll be here when you need me."
    private const val IDENTITY =
        "I'm Qwen, a small language model running entirely on this phone. Ask me a question and I'll answer from what I know."
    private const val OFFLINE = "I can't reach the internet — I'm running on this phone. Ask me something I can reason about."

    private val replies: Map<String, String> = buildMap {
        listOf("hello", "hi", "hey", "hiya", "hello qwen", "hi qwen", "hey qwen", "hiya qwen", "yo", "yo qwen")
            .forEach { put(it, HELLO) }
        listOf("good morning", "morning", "good afternoon", "good evening").forEach { put(it, GREETINGS) }
        listOf("thanks", "thank you", "thanks qwen", "thank you qwen", "cheers", "ta").forEach { put(it, THANKS) }
        listOf("bye", "goodbye", "bye qwen", "see you", "good night", "goodnight").forEach { put(it, BYE) }
        listOf(
            "who are you", "what are you", "who is this", "what can you do", "what do you do",
            "are you chatgpt", "are you gemini", "are you gpt", "what model are you",
        ).forEach { put(it, IDENTITY) }
        listOf("are you online", "are you connected", "do you have internet", "can you search the web")
            .forEach { put(it, OFFLINE) }
    }
}
