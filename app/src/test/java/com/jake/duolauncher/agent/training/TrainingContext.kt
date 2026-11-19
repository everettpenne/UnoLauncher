package com.jake.duolauncher.agent.training

import com.jake.duolauncher.search.agent.GroundingLogic
import com.jake.duolauncher.search.agent.ReplyTarget
import com.jake.duolauncher.search.context.ContextPolicy
import com.jake.duolauncher.search.context.ContextText
import com.jake.duolauncher.search.context.PhoneContext
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.random.Random

/**
 * The phone's facts as the planner really gets them, for training and for scoring.
 *
 * On the phone every request to the planner carries a `<context>` block: the date and time always, and whatever else the request's words
 * point at (a timer for "timer", the installed apps for "open", the next alarm for "wake me"). The first corpus had a one-line stand-in on
 * 8 % of its rows, so the model was trained and scored on prompts it never sees in use. This builds the block the way the launcher does:
 * the same selection ([ContextPolicy.select], with `action = true`, so nothing written by other people is in it), the same lines
 * ([ContextText]) and the same assembly ([ContextPolicy.assemble]). Only the values are made up, from a few plausible phones.
 */
internal object TrainingContext {
    private class Phone(val now: ZonedDateTime, val locale: Locale, val is24: Boolean, val firstDay: DayOfWeek, val apps: List<String>)

    val commonApps = listOf("Camera", "Messages", "Clock", "Phone", "Maps", "Photos", "Calendar", "Settings", "Files", "Contacts",
        "Calculator", "Gallery", "Vanadium", "Signal", "Notes", "Music", "Weather", "Aurora Store", "F-Droid", "Uno Lab")
    private val phones = listOf(
        Phone(ZonedDateTime.of(2026, 11, 13, 12, 25, 0, 0, ZoneId.of("America/Chicago")), Locale.US, false, DayOfWeek.SUNDAY, commonApps),
        Phone(ZonedDateTime.of(2026, 11, 14, 7, 5, 0, 0, ZoneId.of("America/New_York")), Locale.US, false, DayOfWeek.SUNDAY, commonApps.shuffled(Random(1))),
        Phone(ZonedDateTime.of(2026, 12, 2, 18, 40, 0, 0, ZoneId.of("Europe/London")), Locale.UK, true, DayOfWeek.MONDAY, commonApps.shuffled(Random(2)).take(14)),
        Phone(ZonedDateTime.of(2027, 1, 20, 22, 15, 0, 0, ZoneId.of("Europe/Berlin")), Locale.forLanguageTag("en-DE"), true, DayOfWeek.MONDAY, commonApps.shuffled(Random(3))),
        Phone(ZonedDateTime.of(2027, 3, 6, 9, 50, 0, 0, ZoneId.of("America/Los_Angeles")), Locale.US, false, DayOfWeek.SUNDAY, commonApps.shuffled(Random(4)).take(10)),
        Phone(ZonedDateTime.of(2027, 6, 21, 15, 30, 0, 0, ZoneId.of("Australia/Sydney")), Locale.forLanguageTag("en-AU"), false, DayOfWeek.MONDAY, commonApps.shuffled(Random(5))),
    )

    /** How often a request goes with no facts at all: the person can switch every source off, and the plan must not depend on them. */
    private const val BARE = .12

    /** The `<context>` block for [query] on one of the phones, or "" for the share of requests that go without one. */
    fun forQuery(query: String, rnd: Random, forced: Map<String, String?> = emptyMap()): String {
        // A row that turns on what is running ("stop it") always has its facts: without them it is a different question.
        if (forced.isEmpty() && rnd.nextDouble() < BARE) return ""
        val phone = phones[rnd.nextInt(phones.size)]
        val chosen = ContextPolicy.select(PhoneContext.sources, query, action = true, off = emptySet())
        val all = chosen + PhoneContext.sources.filter { it.id in forced && it !in chosen }
        return ContextPolicy.assemble(all.map { source -> source to (if (source.id in forced) forced[source.id] else say(source.id, phone, rnd)) })
    }

    /** The lines for what is running, for rows that depend on it. */
    fun timerRunning(): String = ContextText.timer(4 * 60_000L + 30_000L, 10 * 60_000L, false)
    fun stopwatchRunning(): String = ContextText.stopwatch(95_000L, true)
    fun playing(): String = ContextText.nowPlaying("Blue in Green", "Miles Davis", true, null)!!

    /** What a source says. Null for one that has nothing to say (no timer running) or that this corpus does not imitate. */
    private fun say(id: String, phone: Phone, rnd: Random): String? = when (id) {
        "time" -> ContextText.dateTime(phone.now, phone.locale, phone.is24, phone.firstDay)
        "alarm" -> when (rnd.nextInt(3)) {
            0 -> ContextText.alarm(null, null, null)
            1 -> ContextText.alarm(if (phone.is24) "06:30" else "6:30 AM", "Tomorrow", "in 9 h 12 min")
            else -> ContextText.alarm(if (phone.is24) "07:45" else "7:45 AM", "Monday", null)
        }
        // Live sources: usually nothing is running.
        "timer" -> when (rnd.nextInt(6)) {
            0 -> ContextText.timer(4 * 60_000L + 30_000L, 10 * 60_000L, false)
            1 -> ContextText.stopwatch(95_000L, true)
            else -> null
        }
        "playing" -> if (rnd.nextInt(6) == 0) ContextText.nowPlaying("Blue in Green", "Miles Davis", true, null) else null
        "quiet" -> ContextText.quiet(rnd.nextBoolean(), rnd.nextBoolean(), listOf("normal", "vibrate", "silent")[rnd.nextInt(3)])
        "battery" -> ContextText.battery(listOf(18, 46, 73, 100)[rnd.nextInt(4)], rnd.nextBoolean(), null)
        "storage" -> ContextText.storage(41L * 1024 * 1024 * 1024, 128L * 1024 * 1024 * 1024)
        "network" -> listOf("Network: Wi-Fi, online", "Network: mobile data, online", "Network: no connection; airplane mode on")[rnd.nextInt(3)]
        "audio" -> listOf("Audio connected: phone speaker only", "Audio connected: Pixel Buds (Bluetooth)")[rnd.nextInt(2)]
        "sound" -> "Sound and screen: media volume ${rnd.nextInt(16)} of 25, ring volume ${rnd.nextInt(8)} of 7, brightness ${20 + rnd.nextInt(70)}%"
        "apps" -> ContextText.list("Installed apps (most used first)", phone.apps, 40)
        "notes" -> listOf(null, "Note: \"milk; eggs; call the vet\"", ContextText.checklist(listOf("laundry" to true, "book flights" to false)))[rnd.nextInt(3)]
        "calendar" -> listOf(null, "Calendar today: Standup 9:30 AM; Lunch with Priya 12:30 PM", "Calendar: nothing else today")[rnd.nextInt(3)]
        "countdowns" -> if (rnd.nextBoolean()) ContextText.countdown("Trip", phone.now.toLocalDate(), phone.now.toLocalDate().plusDays(23)) else null
        "versions" -> "Phone: Google Pixel 10a, Android 17; Uno Launcher 0.133.1-uno01; Uno Lab 0.3.9"
        "settings" -> listOf(null, "Uno settings that match: \"Local assistant (Uno Lab)\" is on the search page of Uno's settings")[rnd.nextInt(2)]
        // Not imitated (they need the phone, or the person's permission): they say nothing, which is also what they often say on a phone.
        else -> null
    }

    private val senders = listOf("Sam Patel", "Ana Ruiz", "Dana", "Jo", "Alex", "Mum", "Dad", "Chris", "Taylor", "Morgan", "Priya", "Lee")
    private val messengers = listOf("Messages", "Signal", "WhatsApp")

    /**
     * The `<known>` block for a reply: the messages waiting, newest first, as the launcher lists them
     * ([GroundingLogic.block]). [to] is who the reply is for; when it is one of the words for "the newest one" the list simply has a
     * newest. A couple of other senders are always there, so the name has to be picked rather than copied from a list of one.
     */
    fun waitingFor(query: String, to: String, rnd: Random): String {
        val pronoun = to.lowercase() in setOf("that", "it", "this", "them", "the last message")
        val who = if (pronoun) senders[rnd.nextInt(senders.size)] else senders.firstOrNull { it.startsWith(to, ignoreCase = true) } ?: to
        val others = senders.filter { !it.startsWith(who.substringBefore(' '), ignoreCase = true) }.shuffled(rnd).take(1 + rnd.nextInt(2))
        // For "that", the person meant is the newest; for a name, they may be anywhere in the list.
        val ordered = if (pronoun) listOf(who) + others else (others + who).shuffled(rnd)
        val targets = ordered.mapIndexed { i, name -> ReplyTarget("k$i", name, messengers[rnd.nextInt(messengers.size)], 1_000L - i) }
        return GroundingLogic.block(query, emptyList(), emptyList(), targets)
    }
}
