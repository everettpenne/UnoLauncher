package com.jake.duolauncher.agent.training

import com.jake.duolauncher.search.agent.Kind
import com.jake.duolauncher.search.agent.Tool
import com.jake.duolauncher.search.agent.ToolRegistry
import kotlin.random.Random

/** One training example: what the person said, the facts shown beside it, and the reply the planner should give. */
internal class Example(
    val query: String,
    val reply: String,
    val family: String,
    val context: String = "",
    val grounding: String = "",
)

/**
 * The training corpus, generated from the tool registry: adding a tool and regenerating is the whole workflow, and the corpus cannot describe a
 * tool that does not exist or miss one that does.
 *
 * Value pools are per tool and per parameter, because the catalogue declares types and bounds but not meaning: a timer is in minutes, an
 * alarm at a clock time, an app is a label the phone has. Deterministic for a given seed.
 */
internal object TrainingCorpus {
    const val SEED = 20261113L
    private const val PER_TOOL = 180

    /**
     * The frozen eval set, read from the test resources. Every example it already holds is left out of the corpus by construction, so a
     * measurement is never taken on something the model was shown. The generated queries are new phrasings of the same capabilities.
     */
    private val evalQueries: Set<String> by lazy {
        val stream = TrainingCorpus::class.java.getResourceAsStream("/agent/phrases.json") ?: return@lazy emptySet()
        com.jake.duolauncher.search.agent.EvalSet.parse(stream.bufferedReader().readText())
            .map { it.query.lowercase().trim() }.toSet()
    }

    fun generate(tools: List<Tool> = ToolRegistry.builtIn, seed: Long = SEED): List<Example> {
        val rnd = Random(seed)
        val out = LinkedHashMap<String, Example>()
        fun keep(examples: List<Example>) = examples.forEach { out.putIfAbsent(it.query, it) }

        val singles = tools.filter { it.name != "reply" }.flatMap { single(it, rnd) }
        keep(singles)
        keep(replies(tools, rnd))
        keep(multiStep(tools))
        keep(clarifyExamples())
        keep(refuseExamples())
        keep(grounded(tools))
        keep(hardNegatives(tools))

        return out.values.filter { it.query.lowercase().trim() !in evalQueries }
    }

    // ---------------------------------------------------------------- singles

    private fun single(tool: Tool, rnd: Random, count: Int = PER_TOOL): List<Example> {
        val combos = when (tool.name) {
            "alarm" -> alarmCombos(tool)
            "timer" -> timerCombos(tool)
            else -> cartesian(tool, 80).shuffled(rnd)
        }
        val out = ArrayList<Example>()
        var i = 0
        // Enough passes for a tool whose parameters are few: a toggle has one combination and many phrasings.
        while (out.size < count && i < combos.size * 20) {
            val values = combos[i % combos.size]
            val phrasing = phrases(tool, values)
            if (phrasing.isEmpty()) { i++; continue }
            val query = phrasing[i / combos.size % phrasing.size]
            i++
            if (out.any { it.query == query }) continue
            out += Example(query, TrainingFormat.step(tool, values), "single")
        }
        return out
    }

    private fun timerCombos(tool: Tool): List<Map<String, String>> {
        val seconds = pool("timer.seconds")
        val labels = pool("timer.label")
        return seconds.flatMap { s -> labels.map { l -> mapOf("seconds" to s, "label" to l) } }.shuffled(Random(7))
    }

    private fun alarmCombos(tool: Tool): List<Map<String, String>> =
        pool("alarm.at").map { mapOf("at" to it, "in_minutes" to "") } +
            pool("alarm.in_minutes").map { mapOf("at" to "", "in_minutes" to it) }

    /** Every combination of the tool's parameters, drawn from the pools. */
    private fun cartesian(tool: Tool, limit: Int): List<Map<String, String>> {
        var combos: List<Map<String, String>> = listOf(emptyMap())
        for (param in tool.params) {
            val values = when (param.kind) {
                Kind.Int -> pool("${tool.name}.${param.name}").ifEmpty {
                    (param.min.coerceAtLeast(0)..(param.min.coerceAtLeast(0) + 6)).map { it.toString() }
                }
                Kind.Bool -> listOf("true", "false")
                Kind.Enum -> param.choices
                Kind.Text -> pool("${tool.name}.${param.name}").ifEmpty { listOf("") }
            }
            combos = combos.flatMap { combo -> values.map { value -> combo + (param.name to value) } }
        }
        return combos.shuffled(Random(11)).take(limit)
    }

    private fun phrases(tool: Tool, v: Map<String, String>): List<String> {
        val text = { name: String -> v[name].orEmpty() }
        return when (tool.name) {
            "timer" -> {
                val m = minutes(text("seconds"))
                val label = text("label")
                if (label.isEmpty()) listOf("set a timer for $m minutes", "start a $m minute timer", "timer for $m minutes",
                    "count down $m minutes", "start timing $m minutes", "set a $m min timer", "timer $m minutes",
                    "put a timer on for $m minutes")
                else listOf("set a $label timer for $m minutes", "$label timer for $m minutes", "start a $m minute timer for the $label")
            }
            "alarm" -> if (text("at").isNotEmpty()) listOf("wake me at ${text("at")}", "set an alarm for ${text("at")}",
                "alarm at ${text("at")}", "wake me up at ${text("at")}")
            else listOf("set an alarm in ${text("in_minutes")} minutes", "wake me up in ${text("in_minutes")} minutes",
                "alarm in ${text("in_minutes")} minutes")
            "focus" -> if (text("on") == "true") listOf("turn on focus", "turn on do not disturb", "silence my phone",
                "do not disturb on", "put my phone on silent", "start focus mode", "enable do not disturb", "silence notifications")
            else listOf("turn off focus", "turn off do not disturb", "turn focus off", "let notifications through again",
                "turn off silent mode", "end focus mode", "disable do not disturb", "unsilence my phone")
            "stopwatch" -> listOf("start the stopwatch", "time my swim", "start timing my run", "stopwatch on", "begin the stopwatch",
                "stop the stopwatch", "end the stopwatch", "stop timing", "start the lap timer", "time this", "start a stopwatch",
                "take a split", "begin timing this", "stop the lap timer", "stop timing now")
            "torch" -> listOf("turn on the flashlight", "flashlight on", "switch the torch on", "turn the torch on",
                "turn off the flashlight", "flashlight off", "switch the torch off", "turn the torch off", "torch please",
                "turn the light on", "light up the room", "turn the flashlight on please", "put the torch on")
            "note" -> listOf("remember to ${text("text")}", "note that ${text("text")}", "write down ${text("text")}",
                "save a note: ${text("text")}", "note to self: ${text("text")}", "make a note that ${text("text")}")
            "open_app" -> listOf("open ${text("app")}", "launch ${text("app")}", "start ${text("app")}",
                "open the ${text("app")} app", "show me ${text("app")}", "go to ${text("app")}")
            "dial" -> listOf("dial ${text("number")}", "call ${text("number")}", "show me the dialer for ${text("number")}",
                "phone ${text("number")}", "ring ${text("number")}")
            "message" -> listOf("text ${text("number")}", "send a text to ${text("number")}", "message ${text("number")}",
                "sms ${text("number")}", "open messages for ${text("number")}")
            "open_url" -> listOf("open ${text("url")}", "go to ${text("url")}", "open the page ${text("url")}",
                "open the site ${text("url")}")
            "calendar_event" -> {
                val inMinutes = text("in_minutes").toIntOrNull() ?: 0
                val title = text("title")
                val duration = text("duration_minutes")
                val whenText = if (inMinutes == 0) "now" else "in $inMinutes minutes"
                listOf("put $title in my calendar $whenText for $duration minutes",
                    "add $title to my calendar $whenText for $duration minutes",
                    "schedule $title $whenText for $duration minutes",
                    "calendar: $title $whenText for $duration minutes")
            }
            "web_search" -> listOf("search the web for ${text("query")}", "search for ${text("query")}", "look up ${text("query")}",
                "google ${text("query")}", "look online for ${text("query")}")
            "settings_panel" -> when (text("panel")) {
                "wifi" -> listOf("open wifi settings", "turn off wifi", "turn on wifi", "wifi settings", "show me wifi settings")
                "bluetooth" -> listOf("open bluetooth settings", "turn bluetooth off", "turn on bluetooth", "bluetooth settings")
                "nfc" -> listOf("turn on nfc", "open nfc settings", "nfc settings")
                "airplane" -> listOf("turn on airplane mode", "airplane mode", "turn off airplane mode", "open airplane mode settings")
                "display" -> listOf("open display settings", "show display settings", "display settings")
                "sound" -> listOf("open sound settings", "sound settings", "show me sound settings")
                "battery" -> listOf("open battery settings", "battery settings", "show battery settings")
                else -> listOf("open ${text("panel")} settings")
            }
            "reply" -> listOf("reply to ${text("to")} saying ${text("text")}", "tell ${text("to")} ${text("text")}",
                "answer ${text("to")} with ${text("text")}", "reply to ${text("to")}: ${text("text")}",
                "message back ${text("to")} that ${text("text")}")
            else -> emptyList()
        }
    }

    /** Replies are their own family: the tool needs a waiting message, and the parameters are a person and a sentence. */
    private fun replies(tools: List<Tool>, rnd: Random): List<Example> {
        val tool = tools.firstOrNull { it.name == "reply" } ?: return emptyList()
        val combos = cartesian(tool, 60)
        return combos.mapNotNull { values ->
            val phrasing = phrases(tool, values)
            if (phrasing.isEmpty()) null else Example(phrasing[rnd.nextInt(phrasing.size)], TrainingFormat.step(tool, values), "single")
        }
    }

    // ---------------------------------------------------------------- multi-step

    private fun multiStep(tools: List<Tool>): List<Example> {
        val t = { name: String -> tools.first { it.name == name } }
        fun step(name: String, values: Map<String, String>) = t(name) to values
        val timer = { seconds: String, label: String -> step("timer", mapOf("seconds" to seconds, "label" to label)) }
        val focus = { on: Boolean -> step("focus", mapOf("on" to on.toString())) }
        val torch = step("torch", emptyMap())
        val stopwatch = step("stopwatch", emptyMap())
        val note = { text: String -> step("note", mapOf("text" to text)) }
        fun plan(vararg steps: Pair<Tool, Map<String, String>>) = TrainingFormat.steps(steps.toList())
        val rows = listOf(
            "set a 10 minute timer and turn on focus" to plan(timer("600", ""), focus(true)),
            "start a 20 minute timer and put my phone on silent" to plan(timer("1200", ""), focus(true)),
            "turn on the flashlight and set a timer for 5 minutes" to plan(torch, timer("300", "")),
            "turn on the torch and start timing my run" to plan(torch, stopwatch),
            "open the camera and turn on the flashlight" to plan(step("open_app", mapOf("app" to "Camera")), torch),
            "note that I parked on level 3 and set a timer for 45 minutes" to plan(note("parked on level 3"), timer("2700", "")),
            "search the web for rain tomorrow and set an alarm for 07:00" to plan(
                step("web_search", mapOf("query" to "rain tomorrow")), step("alarm", mapOf("at" to "07:00", "in_minutes" to ""))),
            "put lunch in my calendar in 60 minutes for 60 minutes and turn on focus" to plan(
                step("calendar_event", mapOf("title" to "Lunch", "in_minutes" to "60", "duration_minutes" to "60")), focus(true)),
            "stop the stopwatch and remember to stretch" to plan(stopwatch, note("stretch")),
            "text 555 0142 and set a timer for 10 minutes" to plan(step("message", mapOf("number" to "555 0142")), timer("600", "")),
            "open the mail website and start a 15 minute timer" to plan(
                step("open_url", mapOf("url" to "https://example.com")), timer("900", "")),
            "turn on the flashlight, start a 10 minute timer and note that I am outside" to plan(
                torch, timer("600", ""), note("I am outside")),
            "set a 5 minute timer, turn on focus and open the camera" to plan(timer("300", ""), focus(true),
                step("open_app", mapOf("app" to "Camera"))),
            "silence my phone and set an alarm in 30 minutes" to plan(focus(true),
                step("alarm", mapOf("at" to "", "in_minutes" to "30"))),
            "open the settings and turn on the flashlight" to plan(step("open_app", mapOf("app" to "Settings")), torch),
            "put standup in my calendar in 15 minutes for 30 minutes and set a 30 minute timer" to plan(
                step("calendar_event", mapOf("title" to "Standup", "in_minutes" to "15", "duration_minutes" to "30")), timer("1800", "")),
            "start a 30 minute timer and open the mail website" to plan(
                timer("1800", ""), step("open_url", mapOf("url" to "https://example.com"))),
            "turn on focus and start timing" to plan(focus(true), stopwatch),
            "note that the parcel arrived and set an alarm in 20 minutes" to plan(
                note("the parcel arrived"), step("alarm", mapOf("at" to "", "in_minutes" to "20"))),
            "search for soup recipes and set a timer for 45 minutes" to plan(
                step("web_search", mapOf("query" to "soup recipes")), timer("2700", "")),
            "open the camera and start a 2 minute timer" to plan(step("open_app", mapOf("app" to "Camera")), timer("120", "")),
            "reply to Dana saying okay and turn on focus" to plan(
                step("reply", mapOf("to" to "Dana", "text" to "okay")), focus(true)),
            "set a 90 minute timer, open the clock and turn on focus" to plan(
                timer("5400", ""), step("open_app", mapOf("app" to "Clock")), focus(true)),
            "wake me in 45 minutes and put my phone on silent" to plan(
                step("alarm", mapOf("at" to "", "in_minutes" to "45")), focus(true)),
            "open the gallery and turn off the flashlight" to plan(step("open_app", mapOf("app" to "Gallery")), torch),
            "text 555 0177 and start a 5 minute timer" to plan(step("message", mapOf("number" to "555 0177")), timer("300", "")),
        )
        return rows.map { (query, reply) -> Example(query, reply, "multi") }
    }

    // ---------------------------------------------------------------- clarify, refuse, grounding, hard cases

    private fun clarifyExamples(): List<Example> {
        val rows = listOf(
            "text Sam" to "Which phone number should I text?",
            "call Sam" to "Which phone number should I call?",
            "message Ana" to "What phone number is Ana on?",
            "set an alarm" to "What time should the alarm be for?",
            "set a timer" to "How long should the timer be?",
            "put it in my calendar" to "What should the event be called, and when is it?",
            "add a meeting to my calendar" to "What time should the meeting start, and how long is it?",
            "open it" to "Which app should I open?",
            "search for it" to "What should I search for?",
            "note it down" to "What should the note say?",
            "write that down" to "What should I write down?",
            "reply to that" to "Who should I reply to?",
            "send a message" to "Who should I message, and what should it say?",
            "dial" to "Which number should I dial?",
            "set a timer for later" to "How long from now should the timer start?",
            "remind me" to "What should I remind you about, and when?",
            "schedule something" to "What should I put in the calendar, and when?",
            "wake me up" to "What time should the alarm be for?",
            "open the settings" to "Which settings page should I open?",
            "turn the volume up" to "Do you mean sound settings? I cannot change the volume myself.",
            "call the office" to "Which number for the office should I call?",
            "message my brother" to "Which number for your brother should I use?",
            "set a timer for the pasta" to "How long should the pasta timer be?",
            "alarm for tomorrow" to "What time tomorrow should the alarm be for?",
            "note the address" to "What address should the note say?",
            "open the thing" to "Which app do you mean?",
            "open that app" to "Which app do you mean?",
            "search for that" to "What should I search for?",
            "reply to him" to "Who should I reply to?",
            "send it" to "What should I send, and to whom?",
            "calendar for lunch" to "What time should lunch be, and how long?",
            "set a reminder" to "What should I remind you about, and when?",
            "add an event" to "What should the event be called, and when is it?",
            "put the meeting in" to "What time should the meeting start, and how long is it?",
            "wake me" to "What time should the alarm be for?",
            "time something" to "What should I time?",
        )
        return rows.map { (query, question) -> Example(query, TrainingFormat.clarify(question), "clarify") }
    }

    private fun refuseExamples(): List<Example> {
        val rows = listOf(
            "book me a flight to Tokyo" to "I cannot book travel from here.",
            "order a pizza" to "I cannot place orders from here.",
            "send an email to my boss" to "I cannot write or send email from here.",
            "read my bank balance" to "I cannot reach your bank from this phone.",
            "install Spotify" to "I cannot install apps from here.",
            "call me a taxi" to "I cannot order a taxi from here.",
            "pay the electricity bill" to "I cannot make payments from here.",
            "summarise this article" to "I cannot read the screen content needed for that.",
            "play my playlist" to "I cannot play music from here.",
            "turn on bluetooth and connect my headphones" to "I can open the bluetooth page, but I cannot pair a device.",
            "scan a document" to "I cannot use the camera for scanning from here.",
            "print the photos" to "I cannot print from here.",
            "translate this paragraph" to "I cannot translate text from here.",
            "uninstall Facebook" to "I cannot uninstall apps from here.",
            "check my emails" to "I cannot open your email from here.",
            "set my status to away" to "I have no tool for chat status.",
            "track my parcel" to "I cannot look up deliveries from here.",
            "edit my spreadsheet" to "I cannot edit files from here.",
            "take a photo" to "I can open the camera, but I cannot take the photo for you.",
            "book a table for two" to "I cannot make reservations from here.",
            "send a WhatsApp to Ana" to "I cannot send messages in other apps from here.",
            "post a photo to Instagram" to "I cannot post to social apps from here.",
            "turn off the alarm tomorrow" to "I cannot change alarms; I can only open a new one.",
            "delete my browsing history" to "I cannot clear browser history from here.",
            "change my wallpaper" to "I cannot change the wallpaper from here.",
            "add a contact" to "I cannot edit your contacts from here.",
            "block this number" to "I cannot block numbers from here.",
            "find a meeting slot with my team" to "I cannot read everyone else's calendar from here.",
            "convert this pdf" to "I cannot convert files from here.",
            "update my address book" to "I cannot edit your contacts from here.",
            "send money to Sam" to "I cannot move money from here.",
            "turn on the hotspot" to "I have no tool for the hotspot.",
            "clear notifications" to "I cannot clear notifications from here.",
            "restart my phone" to "I cannot restart the phone from here.",
            "download this file" to "I cannot download files from here.",
            "read my messages aloud" to "I cannot read messages aloud from here.",
        )
        return rows.map { (query, reason) -> Example(query, TrainingFormat.refuse(reason), "refuse") }
    }

    private fun grounded(tools: List<Tool>): List<Example> {
        val t = { name: String -> tools.first { it.name == name } }
        val people = "People: Sam Patel (+1 555 0100); Ana Ruiz (+1 555 0123)\nApps: Camera, Messages, Clock"
        fun step(name: String, values: Map<String, String>) = TrainingFormat.step(t(name), values)
        val rows = listOf(
            "text Sam" to step("message", mapOf("number" to "+1 555 0100")),
            "message Sam Patel" to step("message", mapOf("number" to "+1 555 0100")),
            "call Ana" to step("dial", mapOf("number" to "+1 555 0123")),
            "dial Ana Ruiz" to step("dial", mapOf("number" to "+1 555 0123")),
            "show me the dialer for Sam" to step("dial", mapOf("number" to "+1 555 0100")),
            "text Ana" to step("message", mapOf("number" to "+1 555 0123")),
            "open Camera" to step("open_app", mapOf("app" to "Camera")),
            "open the Messages app" to step("open_app", mapOf("app" to "Messages")),
            "open Clock" to step("open_app", mapOf("app" to "Clock")),
        )
        val others = "People: Dana Kim (+1 555 0177); Jo Ruiz (+1 555 0188)\nApps: Messages, Gallery, Phone"
        val more = listOf(
            "text Dana" to step("message", mapOf("number" to "+1 555 0177")),
            "call Jo" to step("dial", mapOf("number" to "+1 555 0188")),
            "message Dana Kim" to step("message", mapOf("number" to "+1 555 0177")),
            "dial Jo Ruiz" to step("dial", mapOf("number" to "+1 555 0188")),
            "text Jo" to step("message", mapOf("number" to "+1 555 0188")),
            "show me the dialer for Dana" to step("dial", mapOf("number" to "+1 555 0177")),
            "open Gallery" to step("open_app", mapOf("app" to "Gallery")),
            "open the Phone app" to step("open_app", mapOf("app" to "Phone")),
            "open Messages" to step("open_app", mapOf("app" to "Messages")),
            "send Dana a text" to step("message", mapOf("number" to "+1 555 0177")),
            "ring Jo" to step("dial", mapOf("number" to "+1 555 0188")),
            "open the Gallery app" to step("open_app", mapOf("app" to "Gallery")),
            "message Jo Ruiz" to step("message", mapOf("number" to "+1 555 0188")),
            "dial Dana Kim" to step("dial", mapOf("number" to "+1 555 0177")),
            "text Dana Kim" to step("message", mapOf("number" to "+1 555 0177")),
            "open Phone" to step("open_app", mapOf("app" to "Phone")),
        )
        return rows.map { (query, reply) -> Example(query, reply, "grounding", context = "Local time: 12:25", grounding = people) } +
            more.map { (query, reply) -> Example(query, reply, "grounding", context = "Battery: 84%", grounding = others) }
    }

    private fun hardNegatives(tools: List<Tool>): List<Example> {
        val t = { name: String -> tools.first { it.name == name } }
        fun step(name: String, values: Map<String, String>) = TrainingFormat.step(t(name), values)
        val rows = listOf(
            "open camera" to step("open_app", mapOf("app" to "Camera")),
            "open clock" to step("open_app", mapOf("app" to "Clock")),
            "youtube" to step("open_app", mapOf("app" to "YouTube")),
            "wake me at 6:30" to step("alarm", mapOf("at" to "06:30", "in_minutes" to "")),
            "alarm for 7am" to step("alarm", mapOf("at" to "07:00", "in_minutes" to "")),
            "phone 555 0142" to step("dial", mapOf("number" to "555 0142")),
            "text 555 0142" to step("message", mapOf("number" to "555 0142")),
            "silence my phone" to step("focus", mapOf("on" to "true")),
            "put my phone on silent" to step("focus", mapOf("on" to "true")),
            "how long does this lap take" to step("stopwatch", emptyMap()),
            "egg timer 7 minutes" to step("timer", mapOf("seconds" to "420", "label" to "")),
            "start a timer for half an hour" to step("timer", mapOf("seconds" to "1800", "label" to "")),
            "remember the door code is 4411" to step("note", mapOf("text" to "door code is 4411")),
            "turn bluetooth off" to step("settings_panel", mapOf("panel" to "bluetooth")),
            "turn off wifi" to step("settings_panel", mapOf("panel" to "wifi")),
            "airplane mode" to step("settings_panel", mapOf("panel" to "airplane")),
            "open the web page example.com" to step("open_url", mapOf("url" to "https://example.com")),
            "look online for a banana bread recipe" to step("web_search", mapOf("query" to "banana bread recipe")),
            "switch the flashlight off" to step("torch", emptyMap()),
            "set a timer for 1 minute" to step("timer", mapOf("seconds" to "60", "label" to "")),
            "timer for an hour" to step("timer", mapOf("seconds" to "3600", "label" to "")),
            "timer for half an hour" to step("timer", mapOf("seconds" to "1800", "label" to "")),
            "alarm at 9:30" to step("alarm", mapOf("at" to "09:30", "in_minutes" to "")),
            "wake me in 10 minutes" to step("alarm", mapOf("at" to "", "in_minutes" to "10")),
            "call 020 7946 0000" to step("dial", mapOf("number" to "020 7946 0000")),
            "send a text to 555 0199" to step("message", mapOf("number" to "555 0199")),
            "open example.com" to step("open_url", mapOf("url" to "https://example.com")),
            "search the web for the capital of Peru" to step("web_search", mapOf("query" to "the capital of Peru")),
            "calendar: gym now for 60 minutes" to step("calendar_event", mapOf("title" to "Gym", "in_minutes" to "0", "duration_minutes" to "60")),
            "note down: renew the car insurance" to step("note", mapOf("text" to "renew the car insurance")),
            "turn off do not disturb" to step("focus", mapOf("on" to "false")),
        )
        return rows.map { (query, reply) -> Example(query, reply, "hard") }
    }

    // ---------------------------------------------------------------- value pools

    private fun pool(key: String): List<String> = POOLS[key].orEmpty()

    private fun minutes(seconds: String): String = ((seconds.toIntOrNull() ?: 60) / 60).toString()

    private val POOLS: Map<String, List<String>> = mapOf(
        "timer.seconds" to listOf("60", "120", "180", "300", "420", "600", "720", "900", "1200", "1500", "1800", "2400", "2700", "3600"),
        "timer.label" to listOf("", "", "", "pasta", "tea", "eggs", "laundry", "bread", "coffee"),
        "note.text" to listOf("buy milk and eggs", "call the vet", "door code is 4411", "wifi password is on the fridge",
            "pick up the dry cleaning", "book club moved to Thursday", "water the plants", "renew the car insurance"),
        "alarm.at" to listOf("06:15", "06:30", "07:00", "07:15", "07:45", "08:00", "09:30", "12:30", "17:00", "18:45", "21:00", "22:30"),
        "alarm.in_minutes" to listOf("5", "10", "15", "20", "25", "30", "45", "60", "90", "120"),
        "open_app.app" to listOf("Camera", "Clock", "Settings", "Messages", "Contacts", "Calendar", "Photos", "Calculator", "Maps",
            "Phone", "Gallery", "YouTube", "Play Store", "F-Droid"),
        "dial.number" to listOf("+1 555 0100", "555 0142", "020 7946 0000", "+44 20 7946 0000", "555 0177", "555 0123"),
        "message.number" to listOf("+1 555 0100", "555 0142", "020 7946 0000", "555 0177", "555 0123", "555 0199"),
        "open_url.url" to listOf("https://example.com", "https://wikipedia.org", "https://news.example.com/a?b=c",
            "https://en.wikipedia.org/wiki/Timer", "https://192.168.1.1", "https://weather.example.org", "https://example.org",
            "https://docs.example.net/guide?page=2"),
        "calendar_event.title" to listOf("Dentist", "Lunch", "Standup", "Gym", "Call with Ana", "Pick up kids", "Doctor", "Haircut",
            "Car service", "Piano lesson"),
        "calendar_event.in_minutes" to listOf("0", "5", "10", "15", "30", "45", "60", "90", "120", "240"),
        "calendar_event.duration_minutes" to listOf("15", "30", "45", "60", "90", "120", "180"),
        "web_search.query" to listOf("train times to York", "banana bread recipe", "weather tomorrow", "capital of Peru",
            "best pizza near me", "how to renew a passport", "cheapest flights to Lisbon", "sourdough starter tips"),
        "reply.to" to listOf("Dana", "Jo", "Sam", "Ana", "the last message", "that", "Alex"),
        "reply.text" to listOf("running late", "thanks", "on my way", "got it", "see you at 6", "cannot talk now", "will call later",
            "sounds good"),
    )
}
