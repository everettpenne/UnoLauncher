package com.jake.duolauncher.agent.training

import com.jake.duolauncher.search.agent.GroundingLogic
import com.jake.duolauncher.search.agent.Kind
import com.jake.duolauncher.search.agent.KnownPerson
import com.jake.duolauncher.search.agent.ReplyTarget
import com.jake.duolauncher.search.agent.SearchOffer
import com.jake.duolauncher.search.agent.Tool
import com.jake.duolauncher.search.agent.ToolRegistry
import kotlin.random.Random

/** Which set a row is for. The dev set is built from names, values and phrasings the training set never uses. */
internal enum class Split { TRAIN, DEV }

/**
 * What the phone knows beside a request: the contacts it could resolve, the messages waiting for a reply, and what is running. The
 * `<known>` and `<context>` blocks are built from this exactly as the launcher builds them, so a row is the prompt the phone sends.
 * [running]: lines for the context's live sources by id ("timer", "playing"); a null value means that source says nothing.
 */
internal class World(
    val people: List<KnownPerson> = emptyList(),
    val waiting: List<ReplyTarget> = emptyList(),
    val running: Map<String, String?> = emptyMap(),
)

/** One training example: what the person said, the facts shown beside it, and the reply the planner should give. */
internal class Example(
    val query: String,
    val reply: String,
    val family: String,
    val context: String = "",
    val grounding: String = "",
    val world: World? = null,
    /** The plan as steps, kept so a row can be re-written (its values lower-cased with the request). */
    val steps: List<Pair<Tool, Map<String, String>>>? = null,
)

/**
 * The training corpus, generated from the tool registry.
 *
 * The planner's job in every row is to pick a tool and copy the person's words: a time or a length goes into the plan as it was said and
 * the launcher reads it (SpokenTime), so no row asks the model to calculate. Rows come in contrasting sets, because what a 2B model lacks
 * is not phrasings but boundaries: the same words with a message waiting (reply), with a number known (message) and with neither (ask).
 *
 * Deterministic for a given seed.
 */
internal object TrainingCorpus {
    const val SEED = 20261115L

    /** The frozen eval sets. Their requests are never training or dev rows. */
    private val evalQueries: Set<String> by lazy {
        listOf("/agent/phrases.json", "/agent/phrases-fresh.json", "/agent/phrases-followup.json").flatMap { resource ->
            TrainingCorpus::class.java.getResourceAsStream(resource)?.bufferedReader()?.readText()?.let {
                com.jake.duolauncher.search.agent.EvalSet.parse(it).map { row -> row.query.lowercase().trim() }
            }.orEmpty()
        }.toSet()
    }

    private val devQueries: Set<String> by lazy { build(ToolRegistry.builtIn, SEED, Split.DEV).map { it.query.lowercase().trim() }.toSet() }

    fun generate(tools: List<Tool> = ToolRegistry.builtIn, seed: Long = SEED, split: Split = Split.TRAIN): List<Example> {
        val rows = build(tools, seed, split)
        val kept = if (split == Split.TRAIN) rows.filter { it.query.lowercase().trim() !in devQueries } else rows
        val facts = Random(seed + 1 + split.ordinal)
        return kept.map { dressed(it, facts) }
    }

    /** The dev set: a few hundred rows across every family, none of whose names, values or phrasings are in the training set. */
    fun dev(tools: List<Tool> = ToolRegistry.builtIn): List<Example> = generate(tools, SEED, Split.DEV)

    private fun build(tools: List<Tool>, seed: Long, split: Split): List<Example> {
        val gen = Gen(tools, split, Random(seed + 17 * split.ordinal))
        gen.singles()
        gen.replies()
        gen.people()
        gen.clarifies()
        gen.mentions()
        gen.refusals()
        gen.searches()
        gen.answered()
        gen.running()
        gen.pairs()
        gen.triples()
        return gen.rows().filter { it.query.lowercase().trim() !in evalQueries }
    }

    /** [example] with the context and grounding the launcher would send beside that request. */
    fun dressed(example: Example, rnd: Random): Example {
        // An answered question: the facts are read for the answer, and names are looked for in the request and the answer together.
        val request = example.query.substringBefore("\nYou asked: ")
        val answer = example.query.substringAfter("\nAnswer: ", "")
        val factsFor = answer.ifEmpty { example.query }
        val knownFor = if (answer.isEmpty()) example.query else "$request $answer"
        val world = example.world
        if (world == null) {
            // A row with no world of its own (the frozen eval rows): a reply gets the message it answers, as before.
            // Parsed, not matched: the emitter writes these rows with org.json, which keeps no key order.
            val to = replyTo(example.reply)
            val grounding = if (to != null) TrainingContext.waitingFor(knownFor, to, rnd)
                else GroundingLogic.block(knownFor, emptyList(), TrainingContext.commonApps, emptyList())
            return Example(example.query, example.reply, example.family, TrainingContext.forQuery(factsFor, rnd), grounding, null, example.steps)
        }
        val context = TrainingContext.forQuery(factsFor, rnd, world.running)
        val grounding = GroundingLogic.block(knownFor, world.people, TrainingContext.commonApps, world.waiting)
        return Example(example.query, example.reply, example.family, context, grounding, world, example.steps)
    }

    /** Who a plan's reply step answers, or null for a plan with no reply. */
    internal fun replyTo(reply: String): String? {
        val steps = runCatching { org.json.JSONObject(reply).optJSONArray("steps") }.getOrNull() ?: return null
        for (i in 0 until steps.length()) {
            val step = steps.optJSONObject(i) ?: continue
            if (step.optString("tool") == "reply") return step.optJSONObject("params")?.optString("to")?.takeIf { it.isNotEmpty() }
        }
        return null
    }

    // ------------------------------------------------------------------------------------------------ words

    private class Words(split: Split) {
        private val train = split == Split.TRAIN
        private fun <T> pick(a: List<T>, b: List<T>) = if (train) a else b

        val contacts = pick(
            listOf(KnownPerson("Sam Patel", "+1 555 0100"), KnownPerson("Ana Ruiz", "+1 555 0123"), KnownPerson("Dana Kim", "555 0177"),
                KnownPerson("Jo Lee", "020 7946 0011"), KnownPerson("Alex Chen", "555 0142"), KnownPerson("Mum", "555 0199"),
                KnownPerson("Chris Ortiz", "+44 20 7946 0000"), KnownPerson("Taylor Reed", "555 0155"), KnownPerson("Priya Shah", "555 0166"),
                KnownPerson("Omar Haddad", "555 0188")),
            listOf(KnownPerson("Noor Aziz", "555 0111"), KnownPerson("Felix Braun", "555 0122"), KnownPerson("Ingrid Olsen", "+47 555 0133"),
                KnownPerson("Mateo Diaz", "555 0144"), KnownPerson("Gran", "555 0156")))
        val firsts = contacts.map { it.name.substringBefore(' ') }
        val strangers = pick(listOf("Morgan", "Elena", "Viktor", "Robin", "Casey", "Dad"), listOf("Yuki", "Bram", "Tess"))
        val messengers = listOf("Messages", "Signal", "WhatsApp", "Telegram")
        val numbers = pick(listOf("555 0142", "+1 555 0100", "020 7946 0000", "555 0177", "555 0123", "+44 20 7946 0958"),
            listOf("555 0164", "07700 900123", "+1 555 0190"))
        val lengths = pick(
            listOf("5 minutes", "10 minutes", "15 minutes", "20 minutes", "25 minutes", "30 minutes", "45 minutes", "90 seconds", "2 minutes",
                "3 minutes", "an hour", "half an hour", "an hour and a half", "2 hours", "1 hour 20 minutes", "40 min", "12 minutes",
                "ten minutes", "twenty minutes", "forty five minutes", "7 minutes", "8 minutes", "1 minute", "30 seconds", "two hours",
                "a couple of minutes", "4 minutes", "6 minutes", "50 minutes", "3 hours"),
            listOf("35 minutes", "70 minutes", "9 minutes", "an hour and 10 minutes", "2 and a half hours", "55 seconds", "eighteen minutes",
                "4 hours", "75 seconds", "a quarter of an hour"))
        val labels = pick(listOf("pasta", "tea", "eggs", "laundry", "bread", "coffee", "rice", "pizza"), listOf("soup", "bath", "oven"))
        val clocks = pick(
            listOf("6:15", "6:30", "7am", "7:15", "7:45 am", "8", "9:30", "noon", "5pm", "18:45", "21:00", "half past six", "quarter to 7",
                "6 tomorrow morning", "10 tonight", "5:30 am", "midnight", "8 o'clock", "6.45", "7 in the morning"),
            listOf("5:50", "11pm", "quarter past 8", "4:40 am", "20:15", "9 tonight", "half past ten"))
        val delays = pick(
            listOf("in 10 minutes", "in 20 minutes", "in 45 minutes", "in an hour", "in half an hour", "in 90 minutes", "in 2 hours",
                "in an hour and a half", "in 5 minutes", "in 25 minutes"),
            listOf("in 35 minutes", "in 3 hours", "in 12 minutes", "in 50 minutes"))
        val starts = pick(
            listOf("in 2 hours", "tomorrow at 3pm", "friday at 9:30", "tonight at 8", "at 4pm", "monday at noon", "in half an hour",
                "next tuesday at 10am", "tomorrow morning at 9", "on thursday at 2", "in 45 minutes", "at 6:30pm", "in an hour",
                "tomorrow at 11", "saturday at 10am", "wednesday at 14:00", "in 20 minutes", "today at 5pm", "sunday at 1pm", "at noon", "now"),
            listOf("in 3 hours", "tomorrow at 7pm", "on tuesday at 8:15", "thursday at noon", "at 9pm", "in 40 minutes",
                "the day after tomorrow at 10am"))
        val spans = pick(listOf("30 minutes", "an hour", "45 minutes", "2 hours", "90 minutes", "an hour and a half", "15 minutes", "half an hour"),
            listOf("20 minutes", "3 hours", "75 minutes"))
        val titles = pick(
            listOf("dentist", "lunch", "standup", "gym", "call with Ana", "school pickup", "doctor", "team meeting", "coffee with Jo", "yoga",
                "car service", "book club", "dinner with Mum", "flight check-in", "parents evening", "swimming"),
            listOf("physio", "vet appointment", "piano lesson", "interview", "brunch with Noor"))
        /** Things said with "a" or "the", which is not part of the title. */
        val things = pick(listOf("meeting", "haircut", "eye test", "check-up", "workout", "viewing"), listOf("site visit", "review"))
        val facts = pick(
            listOf("the door code is 4411", "the wifi password is on the fridge", "the car is on level 3", "Ana's birthday is on the 9th",
                "the meeting moved to Thursday", "my locker is number 27", "the plumber comes on Friday", "the spare key is under the mat"),
            listOf("the gate code is 9920", "the bins go out on Tuesday"))
        val todos = pick(
            listOf("buy milk and eggs", "call the vet", "book the dentist", "renew the passport", "water the plants", "pick up the parcel",
                "email the landlord", "pay the gas bill", "return the library books", "order printer ink"),
            listOf("defrost the chicken", "send the invoice", "charge the bike lights"))
        val apps = pick(listOf("Camera", "Messages", "Clock", "Phone", "Maps", "Photos", "Calendar", "Settings", "Files", "Contacts", "Calculator",
            "Notes", "Music"), listOf("Gallery", "Vanadium", "Signal", "Weather", "F-Droid"))
        val lookups = pick(
            listOf("train times to York", "banana bread recipe", "weather tomorrow", "capital of Peru", "how to tie a tie", "flights to Rome",
                "best pizza near me", "python list sort", "how tall is Everest", "cheap hotels in Lisbon", "opening hours for the library",
                "sourdough starter", "pixel camera reviews", "bus 42 timetable", "symptoms of a cold", "how to descale a kettle"),
            listOf("ferry times to Skye", "lentil soup recipe", "population of Chile", "how to patch a tyre", "cinema listings tonight"))
        val hosts = pick(listOf("example.com", "wikipedia.org", "news.example.com", "openstreetmap.org", "grapheneos.org", "archive.org",
            "duckduckgo.com", "bbc.co.uk"), listOf("signal.org", "python.org", "f-droid.org"))
        val sayings = pick(
            listOf("running late", "thanks", "on my way", "got it", "see you at 6", "can't talk now", "will call later", "sounds good",
                "yes please", "no worries", "be there in 10", "love you", "ok", "happy birthday", "I'll bring the keys", "not today sorry"),
            listOf("almost there", "call you after lunch", "sure thing", "save me a seat", "five minutes"))
        val items = pick(
            listOf("milk", "eggs", "bread", "batteries", "call the vet", "toothpaste", "bin bags", "olive oil", "book the MOT", "dog food",
                "light bulbs", "coffee", "post the letter", "washing powder"),
            listOf("oat milk", "tape", "renew the bus pass", "lemons"))
        val places = pick(
            listOf("the airport", "Central Station", "12 Mill Lane", "the nearest pharmacy", "the dentist", "Victoria Park", "the city library",
                "work", "home", "the hospital", "a petrol station", "Luigi's restaurant", "the post office", "24 Harbour Road"),
            listOf("the ferry terminal", "the vet", "8 Orchard Close", "the nearest cash machine", "Riverside Market"))
        val percents = pick(listOf("20", "30", "40", "50", "60", "75", "80"), listOf("35", "65", "90"))
        val cities = pick(listOf("York", "Lisbon", "Rome", "Dublin", "Oslo"), listOf("Bergen", "Porto"))
        val dishes = pick(listOf("banana bread", "pancakes", "chilli", "ramen"), listOf("dal", "focaccia"))
        /** Round a command ("can you …"), and round anything at all ("… please"): a question or "that's enough" takes only the second kind. */
        val wraps = pick(listOf("can you %s", "please %s", "%s please", "could you %s", "I need you to %s", "%s, cheers", "hey, %s"),
            listOf("would you %s", "%s for me", "ok %s", "%s thanks"))
        val softWraps = pick(listOf("%s please", "hey, %s", "%s, cheers"), listOf("ok %s", "%s thanks"))
    }

    // ------------------------------------------------------------------------------------------------ generator

    /** The words a request that is a command starts with: only those take "can you" in front. */
    private val COMMANDS = ("set start open add put turn switch call text message send tell reply answer respond wake remember note write save make " +
        "search look google find check get give take show go navigate play pause stop skip mute cancel kill end clear dismiss launch bring dial ring " +
        "phone schedule book count time begin enable disable silence raise lower crank load let say sms delete forget scrap drop run pull fire " +
        "pencil stick pop jot keep visit guide shine zero wipe reset clock halt hold restart resume continue unpause press carry order install pay " +
        "read summarise scan print translate uninstall track edit post change block convert connect pair move share forward cast renew reorder " +
        "hang record download top unsubscribe tick unlock see do abort bin bump up leave shut adjust").split(' ').toSet()

    private class Gen(val tools: List<Tool>, val split: Split, val rnd: Random) {
        val w = Words(split)
        private val out = LinkedHashMap<String, Example>()
        private var made = 0

        /** The same words with different facts are different rows (that is the point of them), so a training row is known by its
         *  request and its reply. A dev row is scored by its request alone, so there each request is kept once. */
        private fun key(query: String, reply: String) = if (split == Split.TRAIN) "$query\u0000$reply" else query

        fun rows(): List<Example> = out.values.mapIndexed { i, e -> if (i % 5 == 2) messy(e) else e }.distinctBy { key(it.query, it.reply) }

        private fun cap(train: Int, dev: Int) = if (split == Split.TRAIN) train else dev
        private fun has(vararg names: String) = names.all { name -> tools.any { it.name == name } }
        private fun tool(name: String) = tools.first { it.name == name }
        private fun step(name: String, vararg values: Pair<String, String>) = tool(name) to mapOf(*values)
        private fun <T> List<T>.at(i: Int, stride: Int = 1, shift: Int = 0): T = this[Math.floorMod(i * stride + shift, size)]

        /** The world most rows are asked in: the phone's contacts, and a message or two waiting from people the request does not name. */
        private fun anyWorld(query: String): World {
            val others = w.contacts.filter { !GroundingLogic.mentions(query, it.name) }.shuffled(rnd).take(rnd.nextInt(3))
            return World(w.contacts, others.mapIndexed { i, p -> ReplyTarget("k$i", p.name, w.messengers.at(rnd.nextInt(8)), 900L - i) })
        }

        private fun add(family: String, query: String, reply: String, world: World?, steps: List<Pair<Tool, Map<String, String>>>? = null) {
            out.putIfAbsent(key(query, reply), Example(query, reply, family, world = world ?: anyWorld(query), steps = steps))
        }

        private fun act(family: String, query: String, world: World?, vararg steps: Pair<Tool, Map<String, String>>) =
            add(family, query, TrainingFormat.steps(steps.toList()), world, steps.toList())

        private fun ask(query: String, question: String, world: World? = null, family: String = "clarify") =
            add(family, query, TrainingFormat.clarify(TrainingFormat.requireGrammarSafe(question)), world)

        /** [base] as it is said, and once more with one of the wrappers people put round a request. */
        private fun said(base: String): List<String> {
            val wraps = if (base.substringBefore(' ').lowercase().trimEnd(',', ':') in COMMANDS) w.wraps else w.softWraps
            val first = wraps.at(made++).format(base)
            return if (split == Split.TRAIN) listOf(base, first, wraps.at(made++, 1, 1).format(base)).distinct() else listOf(base, first)
        }

        /**
         * A fifth of rows as they are typed in a hurry: all in lower case. What the plan copies from the request is lower-cased with
         * it, since the planner copies what it was given; a phone number and a web address stay as they are.
         */
        private fun messy(e: Example): Example {
            if (e.query.contains('\n')) return e
            val query = e.query.lowercase()
            val steps = e.steps
            val reply = when {
                steps != null -> TrainingFormat.steps(steps.map { (tool, values) ->
                    tool to values.mapValues { (name, value) ->
                        val kind = tool.params.firstOrNull { it.name == name }?.kind
                        if (kind == Kind.Text && name !in setOf("number", "url")) value.lowercase() else value
                    }
                })
                e.reply.startsWith("{\"search\"") -> TrainingFormat.search(SearchOffer.forRequest(query))
                else -> e.reply
            }
            return Example(query, reply, e.family, world = e.world, steps = steps)
        }

        // ---------------------------------------------------------------------------------------- one tool, one step

        /** A request for one tool and the values it carries, from the pools; [i] walks the phrasings and the values. */
        private fun clause(name: String, i: Int): Pair<String, Pair<Tool, Map<String, String>>>? {
            val train = split == Split.TRAIN
            fun <T> pick(a: List<T>, b: List<T>) = if (train) a else b
            return when (name) {
                "timer" -> {
                    val length = w.lengths.at(i, 7)
                    val label = if (i % 3 == 0) w.labels.at(i, 5) else ""
                    val frames = if (label.isEmpty()) pick(
                        listOf("set a timer for %s", "timer for %s", "count down %s", "start a timer for %s", "put a timer on for %s", "%s timer",
                            "set a %s timer", "start timing %s"),
                        listOf("give me a timer for %s", "start a countdown of %s", "run a timer for %s"))
                    else pick(listOf("set a $label timer for %s", "$label timer for %s", "timer for %s for the $label", "start a %s timer for the $label"),
                        listOf("give me a $label timer for %s", "run a %s timer for the $label"))
                    val frame = frames.at(i)
                    // "20 minute timer": the length before the noun loses its plural, and is still what the person said.
                    val spoken = if (frame.startsWith("%s timer") || frame.contains(" %s timer")) length.replace(Regex("""(minute|hour|second)s\b"""), "$1") else length
                    frame.format(spoken) to step("timer", "length" to spoken, "label" to label)
                }
                "alarm" -> if (i % 3 != 2) {
                    val time = w.clocks.at(i, 3)
                    pick(listOf("wake me at %s", "set an alarm for %s", "alarm at %s", "wake me up at %s", "alarm for %s", "set my alarm for %s"),
                        listOf("get me up at %s", "I need an alarm for %s")).at(i).format(time) to step("alarm", "when" to time)
                } else {
                    val delay = w.delays.at(i, 3)
                    pick(listOf("set an alarm %s", "wake me up %s", "alarm %s", "wake me %s"), listOf("set an alarm to go off %s", "get me up %s"))
                        .at(i).format(delay) to step("alarm", "when" to delay)
                }
                "calendar_event" -> {
                    val start = w.starts.at(i, 11)
                    val span = if (i % 5 < 3) w.spans.at(i, 3) else ""
                    val tail = if (span.isEmpty()) start else "$start for $span"
                    if (i % 4 == 3) {
                        val thing = w.things.at(i, 5)
                        pick(listOf("add a %s to my calendar %s", "put the %s in my calendar %s", "schedule a %s %s", "book a %s in for %s"),
                            listOf("pencil in a %s %s", "new event: a %s %s")).at(i).format(thing, tail) to
                            step("calendar_event", "title" to thing, "start" to start, "length" to span)
                    } else {
                        val title = w.titles.at(i, 7)
                        pick(listOf("add %s to my calendar %s", "put %s in my calendar %s", "schedule %s %s", "%s %s", "calendar: %s %s",
                            "new event %s %s"), listOf("pencil in %s %s", "diary: %s %s")).at(i).format(title, tail) to
                            step("calendar_event", "title" to title, "start" to start, "length" to span)
                    }
                }
                "note" -> if (i % 2 == 0) {
                    val fact = w.facts.at(i, 3)
                    pick(listOf("remember that %s", "note that %s", "write down that %s", "save a note: %s", "note to self: %s", "make a note that %s"),
                        listOf("jot down that %s", "keep a note that %s")).at(i).format(fact) to step("note", "text" to fact)
                } else {
                    val todo = w.todos.at(i, 3)
                    pick(listOf("remember to %s", "write down %s", "note to self: %s", "make a note to %s", "save a note: %s", "note: %s"),
                        listOf("jot down %s", "keep a note to %s")).at(i).format(todo) to step("note", "text" to todo)
                }
                "open_app" -> {
                    val app = w.apps.at(i, 5)
                    pick(listOf("open %s", "launch %s", "start %s", "open the %s app", "show me %s", "go to %s", "bring up %s", "get me the %s",
                        "pop open %s", "can I have %s", "load up %s"),
                        listOf("fire up %s", "pull up %s", "switch to %s")).at(i).format(app) to step("open_app", "app" to app)
                }
                "web_search" -> {
                    val words = w.lookups.at(i, 5)
                    pick(listOf("search the web for %s", "search for %s", "look up %s", "google %s", "look online for %s", "do a web search for %s"),
                        listOf("search online for %s", "web search %s", "look %s up online")).at(i).format(words) to step("web_search", "query" to words)
                }
                "focus" -> if (i % 2 == 0) pick(
                    listOf("turn on focus", "turn on do not disturb", "do not disturb on", "start focus mode", "enable do not disturb",
                        "silence notifications", "put my phone on do not disturb", "focus on", "go quiet", "dnd on"),
                    listOf("switch focus on", "no interruptions please", "begin focus")).at(i / 2) to step("focus", "on" to "true")
                else pick(
                    listOf("turn off focus", "turn off do not disturb", "turn focus off", "let notifications through again", "end focus mode",
                        "disable do not disturb", "focus off", "stop focus", "dnd off", "I'm done focusing", "unmute my notifications",
                        "stop silencing my phone", "notifications back on", "take the phone off silent", "allow notifications again"),
                    listOf("switch focus off", "leave focus mode", "interruptions are fine again")).at(i / 2) to step("focus", "on" to "false")
                "torch" -> if (i % 2 == 0) pick(
                    listOf("turn on the flashlight", "switch the torch on", "turn the torch on", "torch please", "put the torch on",
                        "I need a light", "torch on", "flashlight please", "turn the flashlight on", "light on", "give me some light",
                        "I can't see, light please", "make some light", "get me a light", "brighten things up with the torch"),
                    listOf("shine the torch", "torch on now", "get the flashlight on")).at(i / 2) to step("torch", "on" to "true")
                else pick(
                    listOf("turn off the flashlight", "flashlight off", "switch the torch off", "turn the torch off", "kill the torch",
                        "torch off", "switch off the flashlight", "put the torch out", "light off", "no more torch"),
                    listOf("flashlight off now", "turn that torch off", "torch out")).at(i / 2) to step("torch", "on" to "false")
                "stopwatch" -> when (i % 5) {
                    0, 1, 2 -> pick(
                        listOf("start the stopwatch", "start timing my run", "stopwatch on", "begin the stopwatch", "start a stopwatch", "time this",
                            "time my lap", "clock my run", "start the lap timer", "time the pasta", "begin timing now", "time my workout",
                            "start timing", "stopwatch please", "time this run", "start counting up"),
                        listOf("get the stopwatch going", "time my walk", "start a lap clock", "time how long this takes")).at(i) to step("stopwatch", "action" to "start")
                    3 -> pick(
                        listOf("end the stopwatch", "stop timing", "stop the stopwatch now", "pause the lap timer", "stopwatch off", "halt the stopwatch",
                            "stop timing my run", "hold the stopwatch"),
                        listOf("stop the lap clock", "that's the stopwatch done")).at(i) to step("stopwatch", "action" to "stop")
                    else -> pick(
                        listOf("clear the stopwatch", "zero the stopwatch", "stopwatch back to zero", "start the stopwatch over", "wipe the stopwatch",
                            "reset my stopwatch"),
                        listOf("put the stopwatch back to zero", "restart the stopwatch from zero")).at(i) to step("stopwatch", "action" to "reset")
                }
                "settings_panel" -> {
                    val panels = mapOf(
                        "wifi" to pick(listOf("open wifi settings", "turn on wifi", "wifi settings", "show me wifi settings", "go to wifi", "switch wifi off"),
                            listOf("take me to the wifi page", "wifi off")),
                        "bluetooth" to pick(listOf("open bluetooth settings", "turn bluetooth off", "turn on bluetooth", "bluetooth settings", "go to bluetooth"),
                            listOf("take me to bluetooth", "bluetooth on")),
                        "nfc" to pick(listOf("turn on nfc", "open nfc settings", "nfc settings", "go to nfc settings"), listOf("nfc off", "show nfc")),
                        "airplane" to pick(listOf("turn on airplane mode", "airplane mode", "turn off airplane mode", "open airplane mode settings",
                            "flight mode on"), listOf("flight mode off", "go to airplane mode")),
                        "display" to pick(listOf("open display settings", "show display settings", "display settings", "go to display settings",
                            "change the screen brightness"), listOf("make the screen dimmer", "take me to display settings")),
                        "sound" to pick(listOf("open sound settings", "sound settings", "show me sound settings", "go to sound settings",
                            "change my ringtone"), listOf("take me to sound settings", "ringtone settings")),
                        "battery" to pick(listOf("open battery settings", "battery settings", "show battery settings", "go to battery settings",
                            "turn on battery saver"), listOf("take me to battery settings", "battery saver")),
                    )
                    val panel = panels.keys.toList().at(i)
                    panels.getValue(panel).at(i / panels.size) to step("settings_panel", "panel" to panel)
                }
                "dial" -> {
                    val number = w.numbers.at(i, 5)
                    pick(listOf("dial %s", "call %s", "phone %s", "ring %s", "show me the dialer for %s", "give %s a call"),
                        listOf("get %s on the phone", "put a call through to %s")).at(i).format(number) to step("dial", "number" to number)
                }
                "message" -> {
                    val number = w.numbers.at(i, 5)
                    if (i % 2 == 0) pick(listOf("text %s", "send a text to %s", "message %s", "sms %s", "open messages for %s"),
                        listOf("start a text to %s", "write to %s")).at(i).format(number) to step("message", "number" to number, "text" to "")
                    else {
                        val words = w.sayings.at(i, 3)
                        pick(listOf("text %s saying %s", "send a text to %s: %s", "message %s that %s", "sms %s with %s", "text %s %s"),
                            listOf("write to %s saying %s", "start a text to %s with %s")).at(i).format(number, words) to
                            step("message", "number" to number, "text" to words)
                    }
                }
                "open_url" -> {
                    val host = w.hosts.at(i, 3)
                    pick(listOf("open %s", "go to %s", "open the page %s", "open the site %s", "take me to %s", "load %s"),
                        listOf("bring up %s", "visit %s")).at(i).format(host) to step("open_url", "url" to "https://$host")
                }
                "timer_cancel" -> pick(
                    listOf("cancel the timer", "kill the timer", "end the countdown", "I don't need the timer", "clear the timer", "turn off the timer",
                        "timer off", "dismiss the timer", "stop the countdown", "cancel that timer", "scrap the timer", "stop my timer",
                        "cancel the countdown", "delete the timer", "the timer can go", "forget the timer"),
                    listOf("drop the timer", "call off the countdown", "abort the timer", "bin the timer")).at(i) to step("timer_cancel")
                "media" -> {
                    val actions = mapOf(
                        "play" to pick(listOf("play", "resume", "play the music", "continue playing", "unpause", "carry on with the song", "press play",
                            "resume the podcast"), listOf("start the music again", "keep playing")),
                        "pause" to pick(listOf("pause", "pause the music", "stop the music", "pause this", "hold the music", "pause the podcast",
                            "stop playing", "pause the song"), listOf("stop the song", "hold playback")),
                        "next" to pick(listOf("next song", "skip", "skip this song", "skip this one", "play the next one", "next", "skip the track",
                            "next episode"), listOf("skip ahead a track", "onto the next song")),
                        "previous" to pick(listOf("previous song", "go back a track", "last song", "play the previous track", "back one song", "previous",
                            "play that last song again", "previous track"), listOf("back a song", "the song before this")),
                    )
                    val action = actions.keys.toList().at(i)
                    actions.getValue(action).at(i / actions.size) to step("media", "action" to action)
                }
                "volume" -> when (i % 6) {
                    0 -> pick(listOf("turn it up", "volume up", "louder", "make it louder", "crank it up", "turn the music up", "raise the volume",
                        "up the volume"), listOf("bump the volume up", "a bit louder")).at(i / 6) to step("volume", "level" to "up")
                    1 -> pick(listOf("turn it down", "volume down", "quieter", "make it quieter", "turn the music down", "lower the volume",
                        "turn the volume down", "bring the volume down"), listOf("drop the volume", "a bit quieter")).at(i / 6) to step("volume", "level" to "down")
                    2 -> pick(listOf("mute", "mute the music", "mute the volume", "sound off", "mute the sound", "volume off"),
                        listOf("mute playback", "kill the sound")).at(i / 6) to step("volume", "level" to "mute")
                    3 -> pick(listOf("max volume", "full volume", "volume all the way up", "volume to max", "as loud as it goes"),
                        listOf("volume to full", "maximum volume")).at(i / 6) to step("volume", "level" to "max")
                    else -> {
                        val n = w.percents.at(i, 3)
                        val (frame, level) = pick(
                            listOf("volume to %s percent" to "$n percent", "set the volume to %s%%" to "$n%", "volume %s" to n,
                                "put the volume at %s percent" to "$n percent", "set volume to %s" to n),
                            listOf("take the volume to %s percent" to "$n percent", "volume at %s%%" to "$n%")).at(i / 6)
                        frame.format(n) to step("volume", "level" to level)
                    }
                }
                "checklist_add" -> {
                    val item = w.items.at(i, 5)
                    pick(listOf("add %s to my list", "put %s on my list", "add %s to the shopping list", "add %s to my to-do list",
                        "put %s on the checklist", "add %s to my checklist", "%s on my list", "list: %s"),
                        listOf("stick %s on my list", "pop %s on the checklist", "add %s to the list of things to do")).at(i).format(item) to
                        step("checklist_add", "item" to item)
                }
                "directions" -> {
                    val place = w.places.at(i, 5)
                    pick(listOf("directions to %s", "navigate to %s", "take me to %s", "how do I get to %s", "show me the way to %s", "route to %s",
                        "get directions to %s"), listOf("get me to %s", "find my way to %s", "guide me to %s")).at(i).format(place) to
                        step("directions", "place" to place)
                }
                else -> null
            }
        }

        fun singles() {
            val want = mapOf(
                "timer" to cap(260, 22), "alarm" to cap(260, 22), "calendar_event" to cap(340, 28), "note" to cap(220, 18),
                "open_app" to cap(180, 16), "web_search" to cap(190, 16), "focus" to cap(100, 10), "torch" to cap(100, 10),
                "stopwatch" to cap(110, 12), "settings_panel" to cap(120, 14), "dial" to cap(100, 10), "message" to cap(170, 14),
                "open_url" to cap(110, 10), "timer_cancel" to cap(60, 8), "media" to cap(120, 12), "volume" to cap(130, 12),
                "checklist_add" to cap(200, 16), "directions" to cap(200, 16),
            )
            for ((name, count) in want) {
                if (!has(name)) continue
                var n = 0
                var i = 0
                while (n < count && i < count * 6) {
                    val (query, step) = clause(name, i++) ?: break
                    for (variant in said(query)) {
                        if (n >= count) continue
                        val before = out.size
                        // "pause" alone is about whatever is running, so it is the music's only when music is what is playing.
                        val bare = name == "media" && query.split(' ').size <= 2 && step.second["action"] in setOf("play", "pause")
                        val world = if (bare) World(w.contacts, running = mapOf("timer" to null, "playing" to TrainingContext.playing())) else null
                        act("single", variant, world, step)
                        if (out.size > before) n++
                    }
                }
            }
        }

        // ---------------------------------------------------------------------------------------- replies, in contrast

        private fun waiting(sender: KnownPerson?, others: Int, newestIsSender: Boolean = true): List<ReplyTarget> {
            val rest = w.contacts.filter { it != sender }.shuffled(rnd).take(others)
            val order = if (sender == null) rest else if (newestIsSender) listOf(sender) + rest else (rest + sender).shuffled(rnd)
            return order.mapIndexed { i, p -> ReplyTarget("k$i", p.name, w.messengers.at(rnd.nextInt(8)), 1_000L - i) }
        }

        /**
         * The same words, three ways. With a message from the person waiting it is a reply; with only their number known it is a
         * text with the words typed in; with neither, the number is asked for. And "answer that" needs a message to answer.
         */
        fun replies() {
            if (!has("reply", "message")) return
            val train = split == Split.TRAIN
            val named = if (train) listOf("reply to %s saying %s", "tell %s %s", "answer %s: %s", "reply to %s with %s", "text %s back %s",
                "let %s know %s", "respond to %s with %s", "message %s back saying %s")
            else listOf("write back to %s saying %s", "get back to %s with %s", "tell %s that %s")
            val swapped = if (train) listOf("say %s to %s", "send %s to %s as a reply") else listOf("reply %s to %s")
            val newest = if (train) listOf("reply to that saying %s" to "that", "answer that with %s" to "that", "reply saying %s" to "that",
                "respond with %s" to "that", "reply to the last message with %s" to "the last message", "tell them %s" to "them",
                "reply %s" to "that", "answer it with %s" to "it", "write back %s" to "that", "reply to the message saying %s" to "the message")
            else listOf("write back saying %s" to "that", "answer the last message with %s" to "the last message", "get back to them with %s" to "them")
            val count = cap(180, 12)
            for (i in 0 until count) {
                val person = w.contacts.at(i, 3)
                val first = person.name.substringBefore(' ')
                val words = w.sayings.at(i, 5, 1)
                val base = if (i % 5 == 4) swapped.at(i).format(words, first) else named.at(i).format(first, words)
                for (variant in said(base)) {
                    // v3 asked on a quarter of these, and the model learnt to ask for a number whenever a name appeared: an eighth now.
                    when (if (i % 8 == 3) 0 else i % 4) {
                        // A message from them is waiting (their number may be known too): a reply to it.
                        0, 1 -> act("reply", variant, World(if (i % 4 == 0) w.contacts else emptyList(), waiting(person, 1 + i % 2, i % 3 == 0)),
                            step("reply", "to" to first, "text" to words))
                        // Nothing from them is waiting, and their number is known: a text, with the words typed in and not sent.
                        2 -> act("reply", variant, World(w.contacts, waiting(null, i % 3).filter { it.who != person.name }),
                            step("message", "number" to person.number, "text" to words))
                        // Neither: the number is asked for, by name.
                        else -> ask(variant, "I don't have a number for $first. What is it?", World(emptyList(), waiting(null, i % 3).filter { it.who != person.name }), "reply")
                    }
                }
            }
            for (i in 0 until cap(110, 10)) {
                val (frame, to) = newest.at(i)
                val words = w.sayings.at(i, 7, 2)
                for (variant in said(frame.format(words))) {
                    if (i % 4 == 3) ask(variant, "I don't see a message to reply to. Who should it go to?", World(w.contacts, emptyList()), "reply")
                    else act("reply", variant, World(w.contacts, waiting(w.contacts.at(i), 1 + i % 3)), step("reply", "to" to to, "text" to words))
                }
            }
            // Someone the phone has never heard of.
            for (i in 0 until cap(10, 3)) {
                val name = w.strangers.at(i)
                for (variant in said(named.at(i, 3).format(name, w.sayings.at(i, 3))))
                    ask(variant, "I don't have a number for $name. What is it?", World(w.contacts, waiting(null, 1 + i % 2)), "reply")
            }
        }

        /** A name the phone knows becomes its number; one it does not is asked for; one that fits two people is asked about. */
        fun people() {
            if (!has("dial", "message")) return
            val train = split == Split.TRAIN
            val calls = if (train) listOf("call %s", "ring %s", "phone %s", "dial %s", "give %s a call", "get %s on the phone")
                else listOf("put a call through to %s", "call up %s")
            val texts = if (train) listOf("text %s", "message %s", "send %s a text", "sms %s", "start a message to %s")
                else listOf("drop %s a text", "write to %s")
            for (i in 0 until cap(90, 8)) {
                val person = w.contacts.at(i, 3)
                val first = person.name.substringBefore(' ')
                val base = if (i % 2 == 0) calls.at(i / 2).format(first) else texts.at(i / 2).format(first)
                val tool = if (i % 2 == 0) step("dial", "number" to person.number) else step("message", "number" to person.number, "text" to "")
                for (variant in said(base)) {
                    // Contact search is off on some phones, and then nobody is known.
                    if (i % 10 == 9) ask(variant, "I don't have a number for $first. What is it?", World(), "grounding")
                    else act("grounding", variant, World(w.contacts, waiting(null, i % 3).filter { it.who != person.name }), tool)
                }
            }
            for (i in 0 until cap(16, 4)) {
                val name = w.strangers.at(i)
                val base = if (i % 2 == 0) calls.at(i).format(name) else texts.at(i).format(name)
                for (variant in said(base)) ask(variant, "I don't have a number for $name. What is it?", World(w.contacts), "grounding")
            }
            // Two people the name could be.
            val twin = w.contacts.first()
            val first = twin.name.substringBefore(' ')
            val other = KnownPerson("$first ${if (train) "Okafor" else "Lindqvist"}", if (train) "555 0170" else "555 0171")
            for (i in 0 until cap(10, 2)) {
                val base = if (i % 2 == 0) calls.at(i).format(first) else texts.at(i).format(first)
                for (variant in said(base)) ask(variant, "Which $first: ${twin.name} or ${other.name}?", World(w.contacts + other), "grounding")
            }
        }

        // ---------------------------------------------------------------------------------------- asking

        fun clarifies() {
            val train = split == Split.TRAIN
            val whatTime = "What time should the alarm be for?"
            val howLong = "How long should the timer be?"
            val rows = if (train) listOf(
                "set an alarm" to whatTime, "wake me up" to whatTime, "wake me" to whatTime, "alarm for tomorrow" to "What time tomorrow should the alarm be for?",
                "wake me up tomorrow" to "What time tomorrow should the alarm be for?", "wake me for the flight" to whatTime, "set an alarm for the morning" to whatTime,
                "set a timer" to howLong, "start a timer" to howLong, "set a timer for later" to howLong, "timer please" to howLong, "start a countdown" to howLong,
                "put it in my calendar" to "What is the event called, and when does it start?", "add an event" to "What is the event called, and when does it start?",
                "schedule something" to "What is the event called, and when does it start?", "put it in my diary" to "What is the event called, and when does it start?",
                "open it" to "Which app should I open?", "open the app" to "Which app should I open?", "open that app" to "Which app do you mean?",
                "open the thing" to "Which app do you mean?", "open the app I used yesterday" to "Which app do you mean?",
                "search for it" to "What should I search for?", "search for that" to "What should I search for?", "look it up" to "What should I look up?",
                "search the web" to "What should I search for?", "search for the thing" to "What should I search for?",
                "note it down" to "What should the note say?", "write that down" to "What should I write down?", "make a note" to "What should the note say?",
                "write a note" to "What should the note say?", "note the address" to "What is the address?", "note the password" to "What is the password?",
                "write down the number" to "What is the number?", "remember the registration" to "What is the registration?",
                "send a message" to "Who should I message, and what should it say?", "send it" to "What should I send, and to whom?",
                "dial" to "Which number should I dial?", "make a call" to "Which number should I call?", "text someone" to "Who should I text?",
                "call the office" to "I don't have a number for the office. What is it?", "message my brother" to "I don't have a number for your brother. What is it?",
                "text the team" to "I don't have a number for the team. What is it?", "call the surgery" to "I don't have a number for the surgery. What is it?",
                "remind me" to "What should I remind you about, and when?", "set a reminder" to "What should I remind you about, and when?",
                "remind me in a bit" to "What should I remind you about, and when?", "remind me about the meeting" to "When should I remind you about the meeting?",
                "open the settings" to "Which settings page should I open?", "open the website" to "Which web address should I open?",
                "open the browser" to "Which web address should I open?", "go to the site" to "Which web address should I open?",
                "add it to my list" to "What should I add to the list?", "put that on my list" to "What should I add to the list?",
                "add something to the shopping list" to "What should I add to the list?", "get directions" to "Where to?", "navigate" to "Where to?",
                "take me there" to "Where to?", "directions please" to "Where to?", "change the volume" to "How loud: up, down, mute, or a percentage?",
                "set the volume" to "How loud: up, down, mute, or a percentage?",
            ) else listOf(
                "I need an alarm" to whatTime, "get me up tomorrow" to "What time tomorrow should the alarm be for?", "I need a timer" to howLong,
                "run a countdown" to howLong, "pencil something in" to "What is the event called, and when does it start?", "fire it up" to "Which app should I open?",
                "search online" to "What should I search for?", "jot something down" to "What should the note say?", "jot down the postcode" to "What is the postcode?",
                "drop someone a text" to "Who should I text?", "ring the school" to "I don't have a number for the school. What is it?",
                "give me a reminder" to "What should I remind you about, and when?", "visit the page" to "Which web address should I open?",
                "stick it on my list" to "What should I add to the list?", "guide me there" to "Where to?", "adjust the volume" to "How loud: up, down, mute, or a percentage?",
            )
            for ((query, question) in rows) for (variant in said(query)) ask(variant, question)
            // A timer for something, with no length; an event, with no start. The other half of each pair is a single.
            val timerFor = if (train) listOf("set a timer for the %s", "%s timer", "start a timer for the %s", "timer for the %s")
                else listOf("give me a timer for the %s", "run a %s timer")
            for (i in 0 until cap(36, 4)) for (variant in said(timerFor.at(i).format(w.labels.at(i)))) ask(variant, "How long should the ${w.labels.at(i)} timer be?")
            val eventFor = if (train) listOf("add %s to my calendar", "put %s in my calendar", "schedule %s", "new event %s", "add %s to my diary")
                else listOf("pencil in %s", "diary: %s")
            // v3 had 180 of these, and "When does it start?" turned up on list items and bookings: fewer, and the contrast in [mentions].
            for (i in 0 until cap(24, 6)) {
                val title = if (i % 3 == 2) "a " + w.things.at(i) else w.titles.at(i, 3)
                for (variant in said(eventFor.at(i).format(title))) ask(variant, "When does it start?")
            }
            // A day with no time is not a start.
            for (i in 0 until cap(24, 3)) {
                val day = (if (train) listOf("tomorrow", "on friday", "next week", "on monday") else listOf("on sunday", "the day after tomorrow")).at(i)
                for (variant in said(eventFor.at(i).format(w.titles.at(i, 5)) + " " + day)) ask(variant, "What time ${day.removePrefix("on ")}?")
            }
        }

        // ---------------------------------------------------------------------------------------- a name or a day is not a question

        /**
         * Notes and list items that name a person, a business or a day. Everything the tool needs is there, so the plan acts: a name in a
         * note is not someone to call, and a month in a reminder is not an event to schedule. v3 asked for a number or a time on these.
         */
        fun mentions() {
            if (!has("note", "checklist_add")) return
            val train = split == Split.TRAIN
            val people = w.firsts + w.strangers
            val trades = if (train) listOf("plumber", "electrician", "dentist", "vet", "school", "office", "landlord", "garage", "builder", "doctor")
                else listOf("optician", "nursery", "locksmith")
            val months = if (train) listOf("May", "June", "March", "the spring", "October", "next week", "January")
                else listOf("August", "the autumn")
            val noteFrames = if (train) listOf(
                "remember that %s has the spare key" to "%s has the spare key", "remember that %s is picking up the kids" to "%s is picking up the kids",
                "write down that %s owes me 20" to "%s owes me 20", "note that %s prefers tea" to "%s prefers tea",
                "save a note: %s's flight lands at 6" to "%s's flight lands at 6", "remember %s's new address is 4 Elm Road" to "%s's new address is 4 Elm Road",
            ) else listOf("keep a note that %s has the ladder" to "%s has the ladder", "jot down that %s likes lilies" to "%s likes lilies")
            val tradeFrames = if (train) listOf(
                "note to self: call the %s" to "call the %s", "remember to ring the %s" to "ring the %s", "write down email the %s" to "email the %s",
                "make a note to chase the %s" to "chase the %s", "note: pay the %s" to "pay the %s",
            ) else listOf("jot down phone the %s" to "phone the %s")
            val dateFrames = if (train) listOf(
                "remember the car tax is due in %s" to "the car tax is due in %s", "note that the lease ends in %s" to "the lease ends in %s",
                "write down that the boiler check is in %s" to "the boiler check is in %s", "remember the passport expires in %s" to "the passport expires in %s",
            ) else listOf("keep a note that the insurance renews in %s" to "the insurance renews in %s")
            val listFrames = if (train) listOf("put %s on my list", "add %s to my to-do list", "add %s to my checklist", "put %s on the checklist")
                else listOf("stick %s on my list")
            val listItems = if (train) listOf("book the %s", "call the %s", "pay the %s", "email the %s") else listOf("ring the %s")
            for (i in 0 until cap(30, 4)) {
                val person = people.at(i, 5)
                val (q, t) = noteFrames.at(i)
                // With and without the person in the phone's contacts: either way it is a note.
                for (variant in said(q.format(person))) act("mentions", variant, if (i % 2 == 0) null else World(), step("note", "text" to t.format(person)))
            }
            for (i in 0 until cap(30, 4)) {
                val trade = trades.at(i, 3)
                val (q, t) = tradeFrames.at(i)
                for (variant in said(q.format(trade))) act("mentions", variant, null, step("note", "text" to t.format(trade)))
            }
            for (i in 0 until cap(20, 3)) {
                val month = months.at(i, 3)
                val (q, t) = dateFrames.at(i)
                for (variant in said(q.format(month))) act("mentions", variant, null, step("note", "text" to t.format(month)))
            }
            for (i in 0 until cap(30, 4)) {
                val item = listItems.at(i).format(trades.at(i, 7))
                for (variant in said(listFrames.at(i, 3).format(item))) act("mentions", variant, null, step("checklist_add", "item" to item))
            }
        }

        // ---------------------------------------------------------------------------------------- refusing

        fun refusals() {
            val rows = if (split == Split.TRAIN) listOf(
                "book me a flight to Tokyo" to "I cannot book travel from here.", "order a pizza" to "I cannot place orders from here.",
                "send an email to my boss" to "I cannot write or send email from here.", "read my bank balance" to "I cannot reach your bank from this phone.",
                "install Spotify" to "I cannot install apps from here.", "call me a taxi" to "I cannot order a taxi from here.",
                "pay the electricity bill" to "I cannot make payments from here.", "summarise this article" to "I cannot read the screen from here.",
                "scan a document" to "I cannot use the camera for scanning from here.", "print the photos" to "I cannot print from here.",
                "translate this paragraph" to "I cannot translate text from here.", "uninstall Facebook" to "I cannot uninstall apps from here.",
                "check my emails" to "I cannot open your email from here.", "set my status to away" to "I have no tool for chat status.",
                "track my parcel" to "I cannot look up deliveries from here.", "edit my spreadsheet" to "I cannot edit files from here.",
                "take a photo" to "I can open the camera, but I cannot take the photo for you.", "book a table for two" to "I cannot make reservations from here.",
                "send a WhatsApp to Ana" to "I can only reply to a message that is waiting, or open a text.", "post a photo to Instagram" to "I cannot post to social apps from here.",
                "turn off the alarm tomorrow" to "I cannot change alarms. I can only open a new one.", "delete my browsing history" to "I cannot clear browser history from here.",
                "change my wallpaper" to "I cannot change the wallpaper from here.", "add a contact" to "I cannot edit your contacts from here.",
                "block this number" to "I cannot block numbers from here.", "find a meeting slot with my team" to "I cannot read other people's calendars from here.",
                "convert this pdf" to "I cannot convert files from here.", "send money to Sam" to "I cannot move money from here.",
                "turn on the hotspot" to "I have no tool for the hotspot.", "clear notifications" to "I cannot clear notifications from here.",
                "restart my phone" to "I cannot restart the phone from here.", "download this file" to "I cannot download files from here.",
                "read my messages aloud" to "I cannot read messages aloud from here.", "start a video call" to "I cannot start video calls from here.",
                "take a screenshot" to "I cannot take screenshots from here.", "record a voice memo" to "I cannot record audio from here.",
                "connect to the office wifi" to "I cannot join networks. I can open the wifi page.", "pair my headphones" to "I cannot pair devices. I can open the bluetooth page.",
                "set a recurring alarm" to "I can open a single alarm, not a recurring one.", "move the meeting to Thursday" to "I cannot change events that already exist.",
                "cancel my alarm" to "I cannot change alarms from here.", "silence the notification" to "I cannot dismiss notifications from here.",
                "reply to the email" to "I cannot write email from here.", "post to the group chat" to "I cannot post in apps from here.",
                "find my keys" to "I cannot track items from here.", "scan a qr code" to "I cannot scan codes from here.",
                "unsubscribe from the newsletter" to "I cannot send email from here.", "order more milk" to "I cannot order shopping from here.",
                "top up my phone credit" to "I cannot make payments from here.", "book the gym class" to "I cannot book classes from here.",
                "pay for parking" to "I cannot make payments from here.", "cast to the tv" to "I cannot cast to other screens from here.",
                "switch to my work profile" to "I cannot switch profiles from here.", "change my keyboard" to "I cannot change input settings from here.",
                "clear all my notes" to "I can save a note, not clear them.", "share my location" to "I cannot share your location from here.",
                "check who called" to "I cannot see the call log from here.", "forward this message" to "I cannot forward messages from here.",
                "mute the group" to "I cannot change chat settings from here.", "start a screen recording" to "I cannot record the screen from here.",
                "order an uber" to "I cannot order rides from here.", "renew the subscription" to "I cannot make payments from here.",
                "change the alarm sound" to "I cannot change alarm settings from here.", "reorder my widgets" to "I cannot edit the home screen from here.",
                "delete the dentist from my calendar" to "I cannot remove events from here.", "tick milk off my list" to "I can add to the list, not tick things off.",
                "play some jazz" to "I can play or pause what is already playing, not choose music.", "play my playlist" to "I can play or pause what is already playing, not choose music.",
                "call Sam now and put it on speaker" to "I can open the dialer, but I cannot place or control a call.", "hang up" to "I cannot control a call from here.",
            ) else listOf(
                "book me a train to Paris" to "I cannot book travel from here.", "send an email to the school" to "I cannot write or send email from here.",
                "pay my phone bill" to "I cannot make payments from here.", "install Duolingo" to "I cannot install apps from here.",
                "order a coffee" to "I cannot place orders from here.", "post this to Mastodon" to "I cannot post to social apps from here.",
                "unlock the front door" to "I have no tool for smart home devices.", "start a video call with Felix" to "I cannot start video calls from here.",
                "delete my photos" to "I cannot delete files from here.", "print the tickets" to "I cannot print from here.",
                "put on some rock music" to "I can play or pause what is already playing, not choose music.", "scan this receipt" to "I cannot use the camera for scanning from here.",
            )
            for ((query, reason) in rows) for (variant in (said(query) + said(query) + said(query)).distinct())
                add("refuse", variant, TrainingFormat.refuse(TrainingFormat.requireGrammarSafe(reason)), null)
        }

        // ---------------------------------------------------------------------------------------- offering a web search

        /**
         * Something to find out, with no word that asks for a search: the planner offers one (the launcher asks before it runs), in
         * the person's own words. "search for …" is a search straight away (a single), and paying or booking is still a refusal.
         */
        fun searches() {
            val train = split == Split.TRAIN
            val fixed = if (train) listOf(
                "check the weather for tomorrow", "find out who won the match", "find out when the pharmacy closes", "tell me the latest news",
                "see if it will rain tomorrow", "check the traffic on the ring road", "find me a plumber near me", "what's the best laptop for students",
                "when does the supermarket close today", "who won the election", "find out how to reset a router", "what's the score in the cricket",
                "find a good sushi place nearby", "how do I get rid of ants", "what's on at the cinema tonight", "check the price of bitcoin",
                "find out what time the game starts", "is the post office open today", "find reviews of the new Pixel", "what's the exchange rate for euros",
                "find out if the trains are running", "what's the news today", "how do I descale a kettle", "find a dentist near me",
                "who is the prime minister of Canada", "check if the pool is open on Sunday", "find out the price of a bus pass", "what should I cook tonight",
                "find a film to watch", "how long do eggs take to boil", "is it going to snow this week", "find out when the clocks change",
            ) else listOf(
                "find out when the bank opens", "check the forecast for Sunday", "what's the best way to clean trainers", "find a locksmith near me",
                "who is playing at the stadium tonight", "how much does a taxi to the airport cost", "check the surf report", "find out the result of the vote",
            )
            for (query in fixed) for (variant in (said(query) + said(query)).distinct()) add("search", variant, TrainingFormat.search(TrainingFormat.requireGrammarSafe(SearchOffer.forRequest(variant))), null)
            val frames = if (train) listOf("what's the weather in %s this weekend", "check the weather in %s", "find a recipe for %s", "get me the train times to %s",
                "how much is a ticket to %s", "find me a hotel in %s", "how do I make %s", "find out what to see in %s", "is it raining in %s",
                "find the best %s recipe")
            else listOf("how warm is it in %s", "find somewhere to eat in %s", "show me how to cook %s")
            for (i in 0 until cap(170, 10)) {
                val frame = frames.at(i)
                val value = if (frame.contains("recipe") || frame.contains("make") || frame.contains("cook")) w.dishes.at(i, 3) else w.cities.at(i, 3)
                for (variant in said(frame.format(value)))
                    add("search", variant, TrainingFormat.search(TrainingFormat.requireGrammarSafe(SearchOffer.forRequest(variant))), null)
            }
        }

        // ---------------------------------------------------------------------------------------- an answered question

        /** The request, the question the planner asked, and the person's answer: the plan the three make together. */
        fun answered() {
            val train = split == Split.TRAIN
            fun turn(request: String, question: String, answer: String) = "$request\nYou asked: $question\nAnswer: $answer"
            val shape = if (train) listOf("%s", "%s please", "make it %s", "%s") else listOf("%s", "let's say %s")
            val count = cap(46, 4)
            for (i in 0 until count) {
                val length = w.lengths.at(i, 3, 1)
                for (request in (if (train) listOf("set a timer", "start a timer", "timer please") else listOf("I need a timer")).take(1 + i % 3))
                    act("answered", turn(request, "How long should the timer be?", shape.at(i).format(length)), null, step("timer", "length" to length, "label" to ""))
                val label = w.labels.at(i)
                act("answered", turn("set a timer for the $label", "How long should the $label timer be?", length), null, step("timer", "length" to length, "label" to label))
                val time = w.clocks.at(i, 3, 2)
                act("answered", turn(if (i % 2 == 0) "set an alarm" else "wake me up", "What time should the alarm be for?", shape.at(i).format(time)), null,
                    step("alarm", "when" to time))
                val delay = w.delays.at(i)
                act("answered", turn("wake me up tomorrow", "What time tomorrow should the alarm be for?", w.clocks.at(i, 7)), null, step("alarm", "when" to w.clocks.at(i, 7)))
                act("answered", turn("set an alarm", "What time should the alarm be for?", delay), null, step("alarm", "when" to delay))
                val title = w.titles.at(i, 3)
                val start = w.starts.at(i, 7, 3)
                act("answered", turn("add $title to my calendar", "When does it start?", start), null,
                    step("calendar_event", "title" to title, "start" to start, "length" to ""))
                act("answered", turn("add an event", "What is the event called, and when does it start?", "$title $start"), null,
                    step("calendar_event", "title" to title, "start" to start, "length" to ""))
                val todo = w.todos.at(i, 3)
                act("answered", turn(if (i % 2 == 0) "make a note" else "write that down", "What should the note say?", todo), null, step("note", "text" to todo))
                val lookup = w.lookups.at(i, 3)
                act("answered", turn("search the web", "What should I search for?", lookup), null, step("web_search", "query" to lookup))
                val app = w.apps.at(i, 3)
                act("answered", turn("open the app", "Which app should I open?", app), null, step("open_app", "app" to app))
                val item = w.items.at(i, 3)
                act("answered", turn("add it to my list", "What should I add to the list?", item), null, step("checklist_add", "item" to item))
                val place = w.places.at(i, 3)
                act("answered", turn(if (i % 2 == 0) "get directions" else "navigate", "Where to?", place), null, step("directions", "place" to place))
                val level = listOf("up", "down", "mute", "${w.percents.at(i)} percent").at(i)
                act("answered", turn("change the volume", "How loud: up, down, mute, or a percentage?", level), null, step("volume", "level" to level))
                val name = w.strangers.at(i)
                val number = w.numbers.at(i, 5)
                val words = w.sayings.at(i, 3)
                // A call answered with a number is still a call: v3 had six of these against forty texts, and a call became a text.
                val callFrames = if (train) listOf("call %s", "ring %s", "phone %s", "give %s a call", "get %s on the phone") else listOf("call up %s")
                val who = if (i % 3 == 2) "the " + (if (train) listOf("office", "school", "garage", "bank", "vet") else listOf("clinic")).at(i) else name
                val callNumber = w.numbers.at(i, 7, 1)
                act("answered", turn(callFrames.at(i).format(who), "I don't have a number for $who. What is it?", callNumber), World(w.contacts),
                    step("dial", "number" to callNumber))
                if (i % 2 == 0) act("answered", turn("tell $name $words", "I don't have a number for $name. What is it?", number), World(w.contacts),
                    step("message", "number" to number, "text" to words))
                val host = w.hosts.at(i)
                act("answered", turn("open the website", "Which web address should I open?", host), null, step("open_url", "url" to "https://$host"))
                // An answer that corrects the question: the plan follows the answer, and the question is not asked again.
                val person = w.firsts.at(i, 3)
                val fact = listOf("$person has the spare key", "$person owes me 20", "$person is picking up the kids").at(i)
                act("answered", turn("note that $fact", "I don't have a number for $person. What is it?",
                    listOf("it's just a note", "no, just write it down", "no, it's for a note", "just save the note").at(i)), null, step("note", "text" to fact))
                val trade = listOf("plumber", "dentist", "vet", "electrician", "landlord").at(i)
                act("answered", turn("put call the $trade on my list", "When does it start?", listOf("it's for my list", "no, just add it to the list", "it's a to-do").at(i)), null,
                    step("checklist_add", "item" to "call the $trade"))
                act("answered", turn("remind me to call the $trade", "I don't have a number for the $trade. What is it?", listOf("just note it", "write it down for me", "a note is fine").at(i)), null,
                    step("note", "text" to "call the $trade"))
                act("answered", turn("time my ${listOf("run", "walk", "ride", "workout").at(i)}", "How long should the timer be?", listOf("no, a stopwatch", "just start the stopwatch", "count up, not down").at(i)), null,
                    step("stopwatch", "action" to "start"))
                // A reply with nothing waiting: a number becomes a text with the words; a name and an app is still nothing to reply to.
                val replyWords = w.sayings.at(i, 5)
                act("answered", turn("reply to $name saying $replyWords", "I don't have a number for $name. What is it?", w.numbers.at(i, 3)), World(),
                    step("message", "number" to w.numbers.at(i, 3), "text" to replyWords))
                if (i % 2 == 1) add("answered", turn("reply to $name saying $replyWords", "I don't have a number for $name. What is it?",
                    "$name in ${w.messengers.at(i)}"), TrainingFormat.refuse(TrainingFormat.requireGrammarSafe("There is no message from $name waiting, and I have no number to text.")), World())
                // An answer that does not answer is asked again, not guessed at.
                if (i % 4 == 0) {
                    ask(turn("set a timer", "How long should the timer be?", listOf("not long", "the usual", "a while", "you decide").at(i / 4)), "How many minutes?", family = "answered")
                    ask(turn("set an alarm", "What time should the alarm be for?", listOf("early", "the usual time", "before work", "in the morning").at(i / 4)), "What time exactly?", family = "answered")
                }
            }
        }

        // ---------------------------------------------------------------------------------------- what is running

        /** "stop it" means whatever is running, which the phone's facts say. With two things running, or none, it is a question. */
        fun running() {
            if (!has("timer_cancel", "media", "stopwatch")) return
            val train = split == Split.TRAIN
            val timer = mapOf("timer" to TrainingContext.timerRunning(), "playing" to null)
            val stopwatch = mapOf("timer" to TrainingContext.stopwatchRunning(), "playing" to null)
            val music = mapOf("timer" to null, "playing" to TrainingContext.playing())
            val both = mapOf("timer" to TrainingContext.timerRunning(), "playing" to TrainingContext.playing())
            val none = mapOf<String, String?>("timer" to null, "playing" to null)
            val stops = if (train) listOf("stop it", "turn it off", "cancel it", "I'm done with it", "stop that", "make it stop", "that's enough", "end it")
                else listOf("shut it off", "enough of that")
            val pauses = if (train) listOf("pause it", "hold on", "pause that", "stop for a second") else listOf("hold it there")
            for (i in 0 until cap(22, 9)) {
                // The wording goes with each state in turn, so only the facts tell the rows apart.
                for ((n, base) in (stops + pauses).withIndex()) {
                    if ((n + i) % 3 != 0) continue
                    for (variant in said(base)) {
                        when ((i + n) % 5) {
                            0 -> act("running", variant, World(w.contacts, running = timer), step("timer_cancel"))
                            1 -> act("running", variant, World(w.contacts, running = stopwatch), step("stopwatch", "action" to "stop"))
                            2 -> act("running", variant, World(w.contacts, running = music), step("media", "action" to "pause"))
                            3 -> ask(variant, "Which one: the timer or the music?", World(w.contacts, running = both), "running")
                            else -> ask(variant, "What should I stop?", World(w.contacts, running = none), "running")
                        }
                    }
                }
            }
        }

        // ---------------------------------------------------------------------------------------- two and three things at once

        private val pairable = listOf("timer", "focus", "torch", "note", "open_app", "alarm", "stopwatch", "settings_panel", "web_search",
            "calendar_event", "checklist_add", "media", "volume", "directions", "dial", "message", "open_url", "timer_cancel")

        fun pairs() {
            val names = pairable.filter { has(it) }
            val joins = if (split == Split.TRAIN) listOf(" and ", " then ", ", and ", " and then ", " and also ") else listOf(" and after that ", ", then ")
            val count = cap(330, 24)
            var i = 0
            var made = 0
            while (made < count && i < count * 4) {
                val a = names.at(i, 5)
                val b = names.at(i, 7, 3)
                i++
                if (a == b) continue
                val (qa, sa) = clause(a, i * 3 + 1) ?: continue
                val (qb, sb) = clause(b, i * 5 + 2) ?: continue
                val base = qa + joins.at(i) + qb
                for (variant in said(base)) act("multi", variant, null, sa, sb)
                made++
            }
            // A reply and something else: the reply still has its message.
            if (!has("reply")) return
            for (k in 0 until cap(40, 4)) {
                val person = w.contacts.at(k, 3)
                val first = person.name.substringBefore(' ')
                val words = w.sayings.at(k, 3)
                val (qb, sb) = clause(listOf("focus", "timer", "note", "open_app", "alarm").at(k), k * 7 + 1) ?: continue
                val base = "reply to $first saying $words" + joins.at(k) + qb
                for (variant in said(base)) act("multi", variant, World(w.contacts, waiting(person, 1)), step("reply", "to" to first, "text" to words), sb)
            }
        }

        fun triples() {
            val names = pairable.filter { has(it) && it !in setOf("dial", "message") }
            val count = cap(75, 6)
            var i = 0
            var made = 0
            while (made < count && i < count * 4) {
                val picked = listOf(names.at(i, 3), names.at(i, 5, 2), names.at(i, 7, 5))
                i++
                if (picked.toSet().size < 3) continue
                val clauses = picked.mapIndexed { j, name -> clause(name, i * (j + 2) + j) ?: return }
                val base = clauses[0].first + ", " + clauses[1].first + " and " + clauses[2].first
                for (variant in said(base)) act("multi", variant, null, clauses[0].second, clauses[1].second, clauses[2].second)
                made++
            }
        }
    }
}
