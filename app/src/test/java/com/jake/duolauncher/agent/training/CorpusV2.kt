package com.jake.duolauncher.agent.training

import com.jake.duolauncher.search.agent.AgentTurns
import com.jake.duolauncher.search.agent.Catalogue
import com.jake.duolauncher.search.agent.Effect
import com.jake.duolauncher.search.agent.GroundingLogic
import com.jake.duolauncher.search.agent.Kind
import com.jake.duolauncher.search.agent.KnownPerson
import com.jake.duolauncher.search.agent.LookSource
import com.jake.duolauncher.search.agent.Param
import com.jake.duolauncher.search.agent.Params
import com.jake.duolauncher.search.agent.ParseResult
import com.jake.duolauncher.search.agent.Plan
import com.jake.duolauncher.search.agent.PlanParser
import com.jake.duolauncher.search.agent.Step
import com.jake.duolauncher.search.agent.Tool
import com.jake.duolauncher.search.agent.ToolEnv
import com.jake.duolauncher.search.agent.ToolRegistry
import com.jake.duolauncher.search.agent.ToolResult
import com.jake.duolauncher.search.context.ContextText
import kotlin.random.Random

internal enum class Split2 { TRAIN, DEV, TEST }

/**
 * One example of the protocol-2 corpus: the whole prompt (the system turn differs from row to row, because the tool list does) and the
 * reply. [tools] are the names in that row's list; [grammar] is that list's grammar, kept for the rows a model is scored on.
 */
internal class Row2(val family: String, val query: String, val user: String, val completion: String, val listed: List<Tool>) {
    val tools: List<String> get() = listed.map { it.name }
    /** Made when asked for and not kept: tens of thousands of rows each with nine thousand characters of list would not fit in memory. */
    val system: String get() = CorpusV2.system(listed)
    val grammar: String get() = Catalogue.grammar(listed, CorpusV2.PROTOCOL)
}

/** A tool that exists only in the corpus: a made-up capability, so the planner learns to read the list it is given and not to remember one. */
internal class MadeUpTool(name: String, summary: String, params: List<Param>, examples: List<String>) :
    Tool(name, summary, params, Effect.Local, examples = examples) {
    override suspend fun execute(params: Params, env: ToolEnv) = ToolResult(true, "ok")
}

/**
 * The protocol-2 corpus (docs/agent/protocol-v2.md): what the 4B and 2B planners are trained on.
 *
 * It is the first corpus and more. What is added, and why:
 *  - **every row has its own tool list**: the tools the reply uses, most of the others, a few made-up ones, in a shuffled order. A
 *    planner trained on one fixed list remembers it; one trained on many reads the list in front of it, so a tool added to the launcher
 *    later is used without training again. How well that works is measured, not assumed: [heldOut] tools are in no training row.
 *  - **a request whose tool is not in the list is refused**, the same words as a row that uses it when it is.
 *  - **the tools added with protocol 2** (camera, email, ringer, play, brightness, device, a new contact, an app's settings, the alarms,
 *    the calendar, a reminder, Uno's own), in three sets of phrasings and values that share nothing: train, dev and test.
 *  - **looking a fact up** (`{"look":[…]}`), then planning from what was found; **using a fact already given** without looking;
 *    **planning again after a step failed**; **answering again after a reply that broke a rule**; and **plans of four and five steps**.
 *
 * Every reply is read back by the real parser against its own row's tool list before it is kept. Deterministic for a given seed.
 */
internal object CorpusV2 {
    const val SEED = 20261128L
    const val PROTOCOL = Catalogue.V2

    // ------------------------------------------------------------------------------------------------ made-up tools

    private fun text(name: String, optional: Boolean = false) = Param(name, Kind.Text, required = !optional, maxLength = 80)
    private fun pick(name: String, vararg choices: String) = Param(name, Kind.Enum, choices = choices.toList())
    private fun flag(name: String) = Param(name, Kind.Bool)
    private fun number(name: String, min: Int, max: Int) = Param(name, Kind.Int, min = min, max = max)

    /** A made-up tool with the requests that use it: each a request and the values it gives. */
    internal class MadeUp(val tool: MadeUpTool, val lack: String, val asks: List<Pair<String, Map<String, String>>>)

    private fun made(name: String, summary: String, params: List<Param>, examples: List<String>, lack: String, vararg asks: Pair<String, Map<String, String>>) =
        MadeUp(MadeUpTool(name, summary, params, examples), lack, asks.toList())

    /** Made-up tools seen in training. They are things a phone could plausibly drive, and none is a real tool's name. */
    val madeUp: List<MadeUp> = listOf(
        made("thermostat", "Sets the home thermostat. degrees: the temperature to hold.", listOf(number("degrees", 10, 30)), listOf("set the heating to 21", "make it 19 degrees at home"),
            "set the thermostat", "set the thermostat to 20" to mapOf("degrees" to "20"), "heating to 18 please" to mapOf("degrees" to "18"), "make the house 22 degrees" to mapOf("degrees" to "22"),
            "turn the heat up to 23" to mapOf("degrees" to "23")),
        made("lights", "Switches the lights in a room. room: which room. on: true or false.", listOf(text("room"), flag("on")), listOf("kitchen lights on", "turn off the bedroom lights"),
            "switch the lights", "turn on the hallway lights" to mapOf("room" to "hallway", "on" to "true"), "lights off in the study" to mapOf("room" to "study", "on" to "false"),
            "switch the garage lights on" to mapOf("room" to "garage", "on" to "true"), "kill the lights in the lounge" to mapOf("room" to "lounge", "on" to "false")),
        made("garage_door", "Opens or closes the garage door.", listOf(pick("action", "open", "close")), listOf("open the garage", "shut the garage door"),
            "move the garage door", "close the garage" to mapOf("action" to "close"), "open the garage door" to mapOf("action" to "open"), "garage door down" to mapOf("action" to "close")),
        made("vacuum", "Controls the robot vacuum.", listOf(pick("action", "start", "stop", "dock")), listOf("start the vacuum", "send the robot home"),
            "run the vacuum", "start hoovering" to mapOf("action" to "start"), "stop the robot vacuum" to mapOf("action" to "stop"), "dock the vacuum" to mapOf("action" to "dock"),
            "run the vacuum" to mapOf("action" to "start")),
        made("sprinkler", "Runs the garden sprinkler. minutes: how long to water.", listOf(number("minutes", 1, 60)), listOf("water the garden for 10 minutes"),
            "run the sprinkler", "water the lawn for 15 minutes" to mapOf("minutes" to "15"), "sprinkler on for 5 minutes" to mapOf("minutes" to "5"), "run the sprinklers 20 minutes" to mapOf("minutes" to "20")),
        made("blinds", "Raises or lowers the blinds in a room.", listOf(text("room"), pick("position", "up", "down", "half")), listOf("blinds down in the bedroom"),
            "move the blinds", "raise the kitchen blinds" to mapOf("room" to "kitchen", "position" to "up"), "lower the blinds in the office" to mapOf("room" to "office", "position" to "down"),
            "bedroom blinds halfway" to mapOf("room" to "bedroom", "position" to "half")),
        made("door_lock", "Locks or unlocks the front door.", listOf(flag("locked")), listOf("lock the front door", "unlock the door"),
            "lock the door", "lock up the house" to mapOf("locked" to "true"), "unlock the front door" to mapOf("locked" to "false"), "is the door locked, lock it" to mapOf("locked" to "true")),
        made("tv", "Controls the television. action: on, off, mute.", listOf(pick("action", "on", "off", "mute")), listOf("turn the tv on", "mute the telly"),
            "control the television", "tv off" to mapOf("action" to "off"), "switch on the television" to mapOf("action" to "on"), "mute the tv" to mapOf("action" to "mute")),
        made("fan", "Sets the ceiling fan speed.", listOf(pick("speed", "off", "low", "medium", "high")), listOf("fan on low", "turn the fan off"),
            "set the fan", "fan to high" to mapOf("speed" to "high"), "put the fan on medium" to mapOf("speed" to "medium"), "switch the fan off" to mapOf("speed" to "off")),
        made("oven", "Preheats the oven. degrees: the temperature.", listOf(number("degrees", 50, 250)), listOf("preheat the oven to 180"),
            "heat the oven", "oven to 200" to mapOf("degrees" to "200"), "preheat to 220 degrees" to mapOf("degrees" to "220"), "warm the oven to 160" to mapOf("degrees" to "160")),
        made("print", "Prints a named document on the home printer.", listOf(text("document")), listOf("print my boarding pass"),
            "print anything", "print the shopping list" to mapOf("document" to "shopping list"), "print out the invoice" to mapOf("document" to "invoice"), "send the ticket to the printer" to mapOf("document" to "ticket")),
        made("car", "Does something to the car remotely. action: lock, unlock, warm, honk.", listOf(pick("action", "lock", "unlock", "warm", "honk")), listOf("lock the car", "warm up the car"),
            "reach the car", "unlock the car" to mapOf("action" to "unlock"), "start warming the car" to mapOf("action" to "warm"), "honk the horn" to mapOf("action" to "honk"), "lock my car" to mapOf("action" to "lock")),
        made("coffee", "Starts the coffee machine. size: the cup size.", listOf(pick("size", "small", "large")), listOf("make me a coffee", "brew a large coffee"),
            "make coffee", "brew a small coffee" to mapOf("size" to "small"), "large coffee please" to mapOf("size" to "large"), "start the coffee machine, large" to mapOf("size" to "large")),
        made("speaker_group", "Plays the current audio on a group of speakers.", listOf(text("group")), listOf("play this downstairs"),
            "move the audio", "play this in the kitchen" to mapOf("group" to "kitchen"), "send the music upstairs" to mapOf("group" to "upstairs"), "play it on the patio speakers" to mapOf("group" to "patio")),
        made("plug", "Switches a smart plug by its name.", listOf(text("name"), flag("on")), listOf("turn on the lamp plug"),
            "switch a plug", "switch the heater plug off" to mapOf("name" to "heater", "on" to "false"), "turn the fairy lights plug on" to mapOf("name" to "fairy lights", "on" to "true"),
            "kettle plug on" to mapOf("name" to "kettle", "on" to "true")),
        made("doorbell", "Shows the doorbell camera, or answers it.", listOf(pick("action", "show", "answer")), listOf("who is at the door", "answer the doorbell"),
            "use the doorbell", "show me the front door" to mapOf("action" to "show"), "answer the door" to mapOf("action" to "answer"), "who's at the door" to mapOf("action" to "show")),
        made("scale", "Logs a weight to the health record. kilograms: the weight.", listOf(number("kilograms", 20, 250)), listOf("log my weight as 72"),
            "log a weight", "record my weight 68 kilograms" to mapOf("kilograms" to "68"), "weight today is 81" to mapOf("kilograms" to "81"), "log 75 kilos" to mapOf("kilograms" to "75")),
        made("water", "Logs a drink of water. glasses: how many.", listOf(number("glasses", 1, 12)), listOf("log two glasses of water"),
            "log water", "i drank 3 glasses of water" to mapOf("glasses" to "3"), "add 1 glass of water" to mapOf("glasses" to "1"), "log 2 glasses of water" to mapOf("glasses" to "2")),
        made("parking", "Remembers where the car is parked. spot: the level, row or street.", listOf(text("spot")), listOf("i parked on level 3"),
            "remember the parking spot", "i parked in row G" to mapOf("spot" to "row G"), "the car is on Mill Lane" to mapOf("spot" to "Mill Lane"), "parked on level 2" to mapOf("spot" to "level 2")),
        made("expense", "Logs an expense. amount: how much. what: what it was for.", listOf(text("amount"), text("what")), listOf("log 12.50 for lunch"),
            "log an expense", "log 4.20 for coffee" to mapOf("amount" to "4.20", "what" to "coffee"), "expense of 30 for petrol" to mapOf("amount" to "30", "what" to "petrol"),
            "add 9.99 for a book" to mapOf("amount" to "9.99", "what" to "a book")),
        made("workout", "Starts tracking a workout. kind: run, walk, ride, swim.", listOf(pick("kind", "run", "walk", "ride", "swim")), listOf("start a run", "track my bike ride"),
            "track a workout", "start tracking a walk" to mapOf("kind" to "walk"), "i'm going for a swim, track it" to mapOf("kind" to "swim"), "begin a run" to mapOf("kind" to "run"),
            "track this ride" to mapOf("kind" to "ride")),
        made("translate_screen", "Translates the words on screen into a language.", listOf(text("language")), listOf("translate this into French"),
            "translate the screen", "translate the screen to Spanish" to mapOf("language" to "Spanish"), "put this into German" to mapOf("language" to "German"),
            "turn this page into Italian" to mapOf("language" to "Italian")),
        made("scan", "Scans a document with the camera and saves it. name: what to call it.", listOf(text("name")), listOf("scan this receipt"),
            "scan a document", "scan this as lease" to mapOf("name" to "lease"), "scan the letter and call it tax" to mapOf("name" to "tax"), "scan a document named passport" to mapOf("name" to "passport")),
        made("hotspot_share", "Shares the phone's connection with a device by name.", listOf(text("device")), listOf("share my internet with the laptop"),
            "share the connection", "share my connection with the tablet" to mapOf("device" to "tablet"), "tether the laptop" to mapOf("device" to "laptop"), "give the console my internet" to mapOf("device" to "console")),
    )

    /**
     * Made-up tools that are in **no** training row, under any name: only in [Split2.TEST] rows of the family "unseen-tool". A planner
     * that uses them correctly has read them from the list. This is the measure of whether a tool added later needs training again.
     */
    val heldOut: List<MadeUp> = listOf(
        made("aquarium", "Feeds the fish or switches the tank light. action: feed, light_on, light_off.", listOf(pick("action", "feed", "light_on", "light_off")), listOf("feed the fish"),
            "look after the aquarium", "feed the fish" to mapOf("action" to "feed"), "turn the tank light on" to mapOf("action" to "light_on"), "aquarium light off" to mapOf("action" to "light_off"),
            "give the fish their food" to mapOf("action" to "feed")),
        made("pet_door", "Locks or unlocks the cat flap.", listOf(flag("locked")), listOf("lock the cat flap"),
            "lock the cat flap", "lock the cat flap for the night" to mapOf("locked" to "true"), "let the cat out, unlock the flap" to mapOf("locked" to "false"), "unlock the pet door" to mapOf("locked" to "false")),
        made("ev_charge", "Sets the car's charge limit. percent: how full to charge.", listOf(number("percent", 50, 100)), listOf("charge the car to 80 percent"),
            "set the charge limit", "charge the car to 90" to mapOf("percent" to "90"), "set the charge limit to 70 percent" to mapOf("percent" to "70"), "only charge to 60" to mapOf("percent" to "60")),
        made("pool", "Sets the pool heater. degrees: the water temperature.", listOf(number("degrees", 15, 35)), listOf("heat the pool to 28"),
            "heat the pool", "pool to 26 degrees" to mapOf("degrees" to "26"), "warm the pool up to 30" to mapOf("degrees" to "30"), "set the pool heater to 24" to mapOf("degrees" to "24")),
        made("ticket", "Shows a saved ticket or pass by name.", listOf(text("name")), listOf("show my train ticket"),
            "show a ticket", "show my gym pass" to mapOf("name" to "gym pass"), "pull up the concert ticket" to mapOf("name" to "concert ticket"), "bring up my boarding pass" to mapOf("name" to "boarding pass")),
        made("air_purifier", "Sets the air purifier. mode: off, auto, sleep, turbo.", listOf(pick("mode", "off", "auto", "sleep", "turbo")), listOf("purifier on auto"),
            "set the air purifier", "put the purifier in sleep mode" to mapOf("mode" to "sleep"), "air purifier to turbo" to mapOf("mode" to "turbo"), "turn the purifier off" to mapOf("mode" to "off")),
        made("meter_reading", "Sends a meter reading to the energy company. reading: the number on the meter.", listOf(text("reading")), listOf("submit a meter reading of 40213"),
            "send a meter reading", "send the meter reading 51882" to mapOf("reading" to "51882"), "submit 30977 as the gas reading" to mapOf("reading" to "30977"), "meter reading is 12045, send it" to mapOf("reading" to "12045")),
        made("bin_day", "Sets a reminder for which bin goes out. bin: general, recycling, garden.", listOf(pick("bin", "general", "recycling", "garden")), listOf("it's recycling this week"),
            "note the bin", "garden waste goes out this week" to mapOf("bin" to "garden"), "remind me it's the general bin" to mapOf("bin" to "general"), "recycling bin this week" to mapOf("bin" to "recycling")),
    )

    // ------------------------------------------------------------------------------------------------ what each real tool is for

    /** What a person would be asking for, for the refusal when the tool is not in the list ("I can't … on this phone."). */
    private val lack = mapOf(
        "timer" to "set a timer", "stopwatch" to "run a stopwatch", "torch" to "switch the flashlight", "focus" to "change Do Not Disturb", "note" to "save a note",
        "alarm" to "set an alarm", "open_app" to "open apps", "dial" to "start a call", "message" to "start a text message", "open_url" to "open web addresses",
        "calendar_event" to "add calendar events", "web_search" to "search the web", "settings_panel" to "open that setting", "reply" to "reply to messages",
        "timer_cancel" to "cancel a timer", "media" to "control what is playing", "volume" to "change the volume", "checklist_add" to "add to a list",
        "directions" to "open directions", "reminder" to "set reminders", "camera" to "open the camera", "email" to "write an email", "ringer" to "change the ringer",
        "play" to "play something by name", "brightness" to "change the brightness", "device" to "do that", "contact_add" to "add a contact",
        "app_info" to "open an app's settings", "alarms_show" to "show the alarms", "calendar_open" to "open the calendar", "uno_style" to "change Uno's Style",
        "uno_settings" to "open Uno's settings")

    fun refusalFor(lacking: String) = "I can't $lacking on this phone."

    // ------------------------------------------------------------------------------------------------ words, in three sets that share nothing

    private class Words(private val split: Split2) {
        private fun <T> of(train: List<T>, dev: List<T>, test: List<T>) = when (split) { Split2.TRAIN -> train; Split2.DEV -> dev; Split2.TEST -> test }
        val people = of(
            listOf(KnownPerson("Sam Patel", "555 0142"), KnownPerson("Ana Ruiz", "555 0177"), KnownPerson("Dana", "020 7946 0000"), KnownPerson("Jo", "555 0110"),
                KnownPerson("Chris Wong", "555 0191"), KnownPerson("Mum", "555 0103"), KnownPerson("Taylor", "+1 555 0166"), KnownPerson("Dr Okafor", "555 0125")),
            listOf(KnownPerson("Mateo", "555 0144"), KnownPerson("Noor", "555 0111"), KnownPerson("Gran", "555 0156"), KnownPerson("Felix", "555 0122")),
            listOf(KnownPerson("Imani", "555 0188"), KnownPerson("Lars Berg", "555 0137"), KnownPerson("Aunt May", "555 0119"), KnownPerson("Yuki", "555 0170")))
        val emails = of(listOf("sam@example.com", "ana.ruiz@example.org", "dana@work.example", "jo@example.net", "billing@example.com"),
            listOf("mateo@example.com", "noor@example.org"), listOf("imani@example.com", "lars.berg@example.org"))
        val songs = of(listOf("Blue in Green" to "Miles Davis", "Hounds of Love" to "Kate Bush", "Pink Moon" to "Nick Drake", "Teardrop" to "Massive Attack", "Heroes" to "David Bowie",
            "Clair de Lune" to "Debussy"), listOf("So What" to "Miles Davis", "River" to "Joni Mitchell"), listOf("Hyperballad" to "Bjork", "Naima" to "John Coltrane"))
        val plays = of(listOf("some jazz", "my running playlist", "the new Kate Bush album", "Radio 4", "the news podcast", "something by Nina Simone", "lo-fi beats", "Abbey Road"),
            listOf("some reggae", "my cooking playlist", "Kind of Blue"), listOf("some bluegrass", "my focus playlist", "Blue Train"))
        val apps = of(listOf("Maps", "Signal", "Photos", "Calculator", "Clock", "Files", "Weather", "Notes", "Camera", "Music"), listOf("Vanadium", "Gallery", "Aurora Store"),
            listOf("Contacts", "F-Droid", "Calendar"))
        val events = of(listOf("Standup" to "9:30 AM", "Dentist" to "2:00 PM", "Lunch with Priya" to "12:30 PM", "Budget review" to "4:15 PM", "School pickup" to "3:20 PM", "Yoga" to "6:00 PM"),
            listOf("Physio" to "10:00 AM", "Parents evening" to "5:30 PM"), listOf("Board meeting" to "11:00 AM", "Haircut" to "1:45 PM"))
        val places = of(listOf("Cafe Lula", "12 Mill Lane", "the Northside Clinic", "Room 4, Harbour House", "Central Library"), listOf("Bergen Station", "the Old Fire Station"),
            listOf("Porto Cathedral", "22 Quay Street"))
        val items = of(listOf("milk", "batteries", "stamps", "call the plumber", "dog food", "printer paper", "book the MOT"), listOf("lemons", "tape", "renew the bus pass"),
            listOf("oat milk", "light bulbs", "post the parcel"))
        val whats = of(listOf("call Sam", "take the bread out", "move the car", "water the plants", "send the invoice", "take my tablets", "leave for the station"),
            listOf("ring the optician", "feed the cat"), listOf("pay the rent", "collect the parcel"))
        val whens = of(listOf("at 5", "in 20 minutes", "at 7:30pm", "in an hour", "at noon", "in 45 minutes", "at 6am"), listOf("at 4", "in 10 minutes", "at 9pm"),
            listOf("at 3", "in half an hour", "at 8:15am"))
        val percents = of(listOf("30", "50", "75", "10", "80"), listOf("40", "65"), listOf("20", "90"))
        val days = of(listOf("tomorrow", "friday", "monday", "today", "saturday"), listOf("wednesday", "sunday"), listOf("thursday", "tuesday"))
        val polite = of(listOf("" to "", "please " to "", "" to " please", "can you " to "", "hey, " to "", "" to " for me", "could you " to ""),
            listOf("" to "", "ok " to "", "" to " thanks", "would you " to ""), listOf("" to "", "right, " to "", "" to " if you can", "i need you to " to ""))
    }

    private fun <T> List<T>.at(i: Int) = this[((i % size) + size) % size]

    /** Phrasings of one request, by split: `{x}` is filled from the values. The three lists share no template. */
    private class Ask(val train: List<String>, val dev: List<String>, val test: List<String>) {
        fun of(split: Split2) = when (split) { Split2.TRAIN -> train; Split2.DEV -> dev; Split2.TEST -> test }
    }

    private fun ask(train: String, dev: String, test: String) = Ask(train.split(" | "), dev.split(" | "), test.split(" | "))

    // ------------------------------------------------------------------------------------------------ the generator

    private class Draft(val family: String, val query: String, val reply: String, val uses: List<String>, val context: String? = null, val grounding: String = "",
        /** Tools that must not be in this row's list (a request whose tool is missing is refused). */
        val without: Set<String> = emptySet(), val extra: List<Tool> = emptyList(),
        /** What some of the phone's sources say for this row (a source id to its words; null for "says nothing"). The rest of the
         *  facts are made as for any request, so the row is seen with different facts around the one that matters. */
        val forced: Map<String, String?>? = null)

    private fun steps(vararg steps: Pair<Tool, Map<String, String>>) = TrainingFormat.steps(steps.toList())
    private fun look(vararg sources: LookSource) = "{\"look\":[" + sources.joinToString(",") { "\"${it.id}\"" } + "]}"
    private fun clarify(q: String) = TrainingFormat.clarify(q)
    private fun refuse(q: String) = TrainingFormat.refuse(q)

    private val allReal: List<Tool> = ToolRegistry.next
    private fun real(name: String): Tool = allReal.first { it.name == name }

    fun build(split: Split2, seed: Long = SEED): List<Row2> {
        val rnd = Random(seed + 31L * split.ordinal)
        val w = Words(split)
        val drafts = ArrayList<Draft>()
        fun act(family: String, query: String, tool: String, vararg values: Pair<String, String>, context: String? = null, grounding: String = "") {
            drafts += Draft(family, query, steps(real(tool) to mapOf(*values)), listOf(tool), context, grounding)
        }
        fun said(i: Int, base: String): String { val (a, b) = w.polite.at(i); return (a + base + b) }
        /** For a request whose words are the same in every split (a made-up tool's): outside training it is always dressed, in that
         *  split's own courtesies, so no split holds another's request word for word. */
        fun dressed(i: Int, base: String): String = if (split == Split2.TRAIN) said(i, base) else w.polite.drop(1).at(i).let { (a, b) -> a + base + b }

        // ---- The first corpus's rows (the nineteen tools, follow-ups, questions back, refusals), under protocol 2 and a varied list.
        if (split != Split2.TEST) {
            for (example in TrainingCorpus.generate(ToolRegistry.builtIn, TrainingCorpus.SEED, if (split == Split2.TRAIN) Split.TRAIN else Split.DEV)) {
                val uses = (runCatching { PlanParser.parse(example.reply, ToolRegistry.builtIn) }.getOrNull() as? ParseResult.Ok)?.plan.let { (it as? Plan.Steps)?.steps?.map { s -> s.tool.name } }.orEmpty()
                drafts += Draft("v1-" + example.family, example.query, example.reply, uses, example.context, example.grounding)
            }
        }

        // ---- The tools added with protocol 2.
        val camera = ask("take a picture | open the camera | take a photo of this | camera | snap a photo | i want to take a picture",
            "grab a photo | get the camera up", "photograph this | fire up the camera")
        val selfie = ask("take a selfie | selfie | front camera | take a picture of me", "selfie time | flip to the front camera", "get a selfie | picture of myself")
        val video = ask("record a video | take a video | start filming | video camera | shoot a video", "film this | get a video of this", "record this on video | start recording video")
        for ((mode, asks) in listOf("photo" to camera, "selfie" to selfie, "video" to video)) asks.of(split).forEachIndexed { i, base ->
            for (k in 0 until 3) act("new-tool", said(i + k, base), "camera", "mode" to mode)
        }
        val ring = mapOf(
            "vibrate" to ask("put my phone on vibrate | vibrate mode | set the phone to vibrate | vibrate only | switch to vibrate", "buzz only | make it vibrate instead of ring", "go to vibrate | phone on vibrate please"),
            "silent" to ask("silence the ringer | silent mode | put the phone on silent | mute the ringer | no ringing", "make the phone silent | ringer off", "set it to silent | stop the phone ringing"),
            "ring" to ask("turn the ringer on | ring mode | take my phone off silent | unmute the ringer | make it ring again", "ringer back on | sound on for calls", "let it ring again | take it off vibrate"))
        for ((mode, asks) in ring) asks.of(split).forEachIndexed { i, base -> for (k in 0 until 3) act("new-tool", said(i + k, base), "ringer", "mode" to mode) }
        val play = ask("play {x} | put on {x} | i want to hear {x} | start playing {x} | play me {x} | listen to {x}", "stick on {x} | let's hear {x}", "queue up {x} | throw on {x}")
        play.of(split).forEachIndexed { i, t ->
            for (k in 0 until 6) {
                val x = if (k % 2 == 0) w.plays.at(i + k) else w.songs.at(i + k).let { if (k % 4 == 1) it.first else "${it.first} by ${it.second}" }
                act("new-tool", said(i + k, t.replace("{x}", x)), "play", "what" to x)
            }
        }
        val bright = mapOf(
            "up" to ask("make the screen brighter | brightness up | turn the brightness up | brighten the screen | i can't see the screen, brighter", "bump up the brightness | lighter screen", "raise the brightness | screen brighter please"),
            "down" to ask("dim the screen | brightness down | make the screen darker | turn the brightness down | lower the brightness", "tone the screen down | less bright", "darken the display | drop the brightness"),
            "max" to ask("full brightness | brightness to max | screen as bright as it goes", "max out the brightness", "brightest setting"),
            "min" to ask("lowest brightness | brightness to minimum | screen as dim as it goes", "minimum brightness", "dimmest setting"))
        for ((level, asks) in bright) asks.of(split).forEachIndexed { i, base -> for (k in 0 until 2) act("new-tool", said(i + k, base), "brightness", "level" to level) }
        ask("brightness to {x} percent | set the brightness to {x}% | screen brightness {x} percent | make the brightness {x} percent", "brightness at {x} percent", "put the brightness on {x} percent")
            .of(split).forEachIndexed { i, t -> for (k in 0 until 4) { val x = w.percents.at(i + k); act("new-tool", said(i + k, t.replace("{x}", x)), "brightness", "level" to "$x percent".let { if ("%" in t) "$x%" else it }) } }
        val device = mapOf(
            "screenshot" to ask("take a screenshot | screenshot | screenshot this | capture the screen | grab a screenshot", "screen grab | save what's on screen", "snap the screen | screen capture"),
            "lock" to ask("lock the phone | lock my phone | lock the screen | lock it", "screen lock now | put the phone to sleep", "lock up the phone | turn the screen off and lock"),
            "notifications" to ask("show my notifications | open the notification shade | pull down my notifications | what are my notifications", "notifications please | open notifications", "bring down the notifications | let me see my notifications"),
            "quick_settings" to ask("open quick settings | show the quick settings | pull down quick settings | quick settings", "quick toggles | get the quick settings up", "bring up quick settings | open the toggles"),
            "recents" to ask("show recent apps | open recents | recent apps | what apps are open", "app switcher | switch apps", "show the open apps | bring up recents"))
        for ((action, asks) in device) asks.of(split).forEachIndexed { i, base -> for (k in 0 until 2) act("new-tool", said(i + k, base), "device", "action" to action) }
        ask("save {n} as {p} | add {p} to my contacts, {n} | new contact {p} {n} | create a contact for {p}, number {n} | add {p} {n} to contacts", "store {n} under {p} | put {p} in my contacts with {n}",
            "make a contact called {p} with {n} | keep {n} as {p}").of(split).forEachIndexed { i, t ->
            for (k in 0 until 5) { val p = w.people.at(i + k); act("new-tool", said(i + k, t.replace("{p}", p.name).replace("{n}", p.number)), "contact_add", "name" to p.name, "number" to p.number) }
        }
        ask("add {p} to my contacts | new contact {p} | save {p} as a contact", "put {p} in my contacts", "make a contact for {p}").of(split).forEachIndexed { i, t ->
            for (k in 0 until 3) { val p = w.people.at(i + k).name; drafts += Draft("clarify", said(i + k, t.replace("{p}", p)), clarify("What is $p's number?"), listOf("contact_add")) }
        }
        ask("uninstall {a} | app info for {a} | remove {a} from my phone | turn off notifications for {a} | open {a}'s settings | change {a}'s permissions | delete the {a} app | force stop {a}",
            "get rid of {a} | {a} app settings | stop {a} sending notifications", "take {a} off my phone | show me {a}'s app page | manage {a}'s permissions").of(split).forEachIndexed { i, t ->
            for (k in 0 until 3) { val a = w.apps.at(i + k); act("new-tool", said(i + k, t.replace("{a}", a)), "app_info", "app" to a, grounding = "Apps: $a") }
        }
        ask("show my alarms | what alarms do i have | turn off my alarm | cancel the 7am alarm | delete my alarm | open my alarms | change my alarm | switch off tomorrow's alarm",
            "list my alarms | kill my morning alarm | edit my alarm", "see my alarms | get rid of my alarm | which alarms are on").of(split).forEachIndexed { i, base ->
            for (k in 0 until 3) act("new-tool", said(i + k, base), "alarms_show")
        }
        ask("show my calendar | open the calendar | open my calendar for {d} | show me {d} in the calendar | calendar for {d} | what does {d} look like in my calendar",
            "pull up my calendar for {d} | calendar please", "bring up the calendar on {d} | let me see my calendar").of(split).forEachIndexed { i, t ->
            for (k in 0 until 4) { val d = w.days.at(i + k); val has = "{d}" in t; act("new-tool", said(i + k, t.replace("{d}", d)), "calendar_open", "day" to if (has) d else "") }
        }
        ask("remind me to {w} {t} | remind me {t} to {w} | set a reminder to {w} {t} | reminder {t}: {w} | don't let me forget to {w} {t}", "nudge me to {w} {t} | give me a reminder {t} to {w}",
            "ping me {t} to {w} | i need reminding to {w} {t}").of(split).forEachIndexed { i, t ->
            for (k in 0 until 6) { val what = w.whats.at(i + k); val whn = w.whens.at(i + 2 * k); act("new-tool", said(i + k, t.replace("{w}", what).replace("{t}", whn)), "reminder", "what" to what, "when" to whn) }
        }
        ask("remind me to {w} | set a reminder to {w} | reminder: {w}", "nudge me to {w}", "ping me to {w}").of(split).forEachIndexed { i, t ->
            for (k in 0 until 4) { val what = w.whats.at(i + k); drafts += Draft("clarify", said(i + k, t.replace("{w}", what)), clarify("When should I remind you?"), listOf("reminder")) }
        }
        ask("email {e} | write an email to {e} | send an email to {e} | start an email to {e} | new email to {e}", "drop {e} an email | mail {e}", "compose an email to {e} | draft an email for {e}")
            .of(split).forEachIndexed { i, t -> for (k in 0 until 4) { val e = w.emails.at(i + k); act("new-tool", said(i + k, t.replace("{e}", e)), "email", "to" to e, "subject" to "", "body" to "") } }
        ask("email {e} saying {b} | write to {e}: {b} | send {e} an email saying {b} | email {e} about {s}", "mail {e} to say {b} | email {e} regarding {s}", "draft an email to {e} that says {b} | email {e} on the subject of {s}")
            .of(split).forEachIndexed { i, t ->
                for (k in 0 until 4) {
                    val e = w.emails.at(i + k); val b = listOf("the report is attached", "see you at 3", "thanks for today", "running late", "can we move it to friday").at(i + k); val s = listOf("friday", "the invoice", "our trip").at(i + k)
                    val about = "{s}" in t
                    act("new-tool", said(i + k, t.replace("{e}", e).replace("{b}", b).replace("{s}", s)), "email", "to" to e, "subject" to if (about) s else "", "body" to if (about) "" else b)
                }
            }
        ask("email {p} | send {p} an email | write an email to {p}", "drop {p} an email", "compose an email to {p}").of(split).forEachIndexed { i, t ->
            for (k in 0 until 3) { val p = w.people.at(i + k).name; drafts += Draft("clarify", said(i + k, t.replace("{p}", p)), clarify("What is $p's email address?"), listOf("email")) }
        }
        val styles = ask("use the {s} style | set the style to {s} | make uno {s} | switch to the {s} style", "go {s} with the style", "put uno on the {s} style")
        styles.of(split).forEachIndexed { i, t -> for (s in listOf("calm", "balanced", "expressive")) act("new-tool", said(i, t.replace("{s}", s)), "uno_style", "level" to s) }
        ask("open uno settings | launcher settings | open the launcher's settings | uno's settings", "settings for uno", "get the launcher settings up").of(split).forEachIndexed { i, base -> act("new-tool", said(i, base), "uno_settings", "page" to "all") }
        ask("open the keyboard settings | uno keyboard settings | settings for the keyboard", "keyboard options", "configure the keyboard").of(split).forEachIndexed { i, base -> act("new-tool", said(i, base), "uno_settings", "page" to "keyboard") }
        val panels = mapOf("mobile_data" to ask("turn off mobile data | mobile data settings | switch my data off | data roaming", "cellular data off", "stop using mobile data"),
            "hotspot" to ask("turn on my hotspot | hotspot settings | share my connection | start tethering", "personal hotspot on", "switch the hotspot on"),
            "location" to ask("turn off location | location settings | switch gps off | turn location on", "gps settings", "stop sharing my location with apps"),
            "storage" to ask("how much storage is left, open it | storage settings | free up space", "open storage", "show me what's using space"),
            "apps" to ask("show all my apps in settings | app settings | manage my apps", "apps list in settings", "open the apps settings"),
            "notifications" to ask("notification settings | change my notification settings | manage notifications", "notifications options", "open the settings for notifications"),
            "security" to ask("security settings | change my screen lock | fingerprint settings", "open security", "set up a fingerprint"),
            "accessibility" to ask("accessibility settings | make the text bigger in settings | talkback settings", "open accessibility", "accessibility options"),
            "date" to ask("date and time settings | change the time zone | set the clock", "time settings", "fix the date"),
            "vpn" to ask("vpn settings | turn on my vpn | connect the vpn", "open vpn", "switch the vpn on"))
        for ((panel, asks) in panels) asks.of(split).forEachIndexed { i, base -> for (k in 0 until 2) act("new-tool", said(i + k, base), "settings_panel", "panel" to panel) }

        // ---- Made-up tools: used when listed.
        for (m in madeUp) m.asks.forEachIndexed { i, (query, values) ->
            for (k in 0 until if (split == Split2.TRAIN) 4 else 1)
                drafts += Draft("made-up-tool", dressed(i + k, query), steps(m.tool to values), listOf(m.tool.name), extra = listOf(m.tool))
        }
        // ---- Tools nobody was trained on (test only): the measure of reading the list.
        if (split == Split2.TEST) for (m in heldOut) m.asks.forEachIndexed { i, (query, values) ->
            for (k in 0 until 3) drafts += Draft("unseen-tool", said(i + k, query), steps(m.tool to values), listOf(m.tool.name), extra = listOf(m.tool))
        }

        // ---- The same request with its tool not in the list: refused, saying what is missing.
        val soFar = drafts.toList()
        soFar.filter { it.uses.size == 1 && (it.family == "new-tool" || it.family == "made-up-tool" || it.family == "unseen-tool" || it.family == "v1-single") }
            .filterIndexed { i, _ -> i % (if (split == Split2.TRAIN) 6 else 4) == 0 }.forEach { d ->
                val name = d.uses.single()
                val lacking = lack[name] ?: (madeUp + heldOut).first { it.tool.name == name }.lack
                // A web search can stand in for nothing here: the request is to do something, and the thing that does it is not there.
                drafts += Draft(if (d.family == "unseen-tool") "unseen-tool-absent" else "tool-absent", d.query, refuse(refusalFor(lacking)), emptyList(), d.context, d.grounding, without = setOf(name))
            }

        // ---- Facts. What the phone holds reaches the planner in two ways, and the rows follow what a phone really does.
        // A fact the request's own words point at is sent with the request (the same selection the launcher makes, ContextPolicy):
        // it is used, and nothing is looked for. A fact the words do not point at was not sent: it is looked for, and what comes back
        // (in the words the phone's own formatter writes, ContextText) is planned from. What is playing is always sent when
        // something is, so a request about it with nothing there is told so, never looked for.
        fun sent(query: String, source: String) = com.jake.duolauncher.search.context.ContextPolicy
            .select(com.jake.duolauncher.search.context.PhoneContext.sources, query.lineSequence().first(), action = true, off = emptySet()).any { it.id == source }
        fun found(source: LookSource, said: String?) = ContextText.found(source.id, said)

        val noteSong = ask("make a note of this song | note down what's playing | save this song to my notes | remember this track | write down this song",
            "jot down this tune | note what i'm listening to", "keep a note of this track | put this song in my notes")
        noteSong.of(split).forEachIndexed { i, base ->
            for (k in 0 until 4) {
                val (title, artist) = w.songs.at(i + k); val q = said(i + k, base)
                drafts += Draft("fact-given", q, steps(real("note") to mapOf("text" to "$title by $artist")), listOf("note"), forced = mapOf("playing" to ContextText.nowPlaying(title, artist, true, null)))
                if (k < 2) drafts += Draft("fact-missing", q, refuse("Nothing is playing right now."), listOf("note"), forced = mapOf("playing" to null))
            }
        }

        val listNext = ask("add my next meeting to my list | put my next appointment on my to-do list | add the next event in my calendar to my checklist",
            "stick my next meeting on my list", "get my next appointment onto my checklist")
        val goNext = ask("directions to my next meeting | navigate to my next appointment | take me to my next meeting | how do i get to my next appointment",
            "route to my next meeting", "get me to my next appointment")
        val textTime = ask("text {p} the time of my {e} | message {p} when my {e} is | tell {p} what time my {e} starts", "let {p} know when my {e} is", "send {p} the time of my {e}")
        for (i in 0 until if (split == Split2.TRAIN) 24 else 8) {
            val (event, time) = w.events.at(i); val place = w.places.at(i); val person = w.people.at(i)
            val day = listOf("Sat 28 Nov", "Mon 30 Nov", "Tue 1 Dec", "Wed 2 Dec").at(i)
            val withPlace = ContextText.calendar(listOf(Triple("$day $time", event, place), Triple("Thu 3 Dec 10:00 AM", "Offsite", null)))
            val noPlace = ContextText.calendar(listOf(Triple("$day $time", event, null), Triple("Thu 3 Dec 10:00 AM", "Offsite", "Harbour House")))
            val empty = ContextText.calendar(emptyList())
            // "my next meeting" and "appointment" are words the launcher sends the calendar for: it is there, and it is used.
            listNext.of(split).at(i).let { base -> val q = said(i, base); check(sent(q, "calendar")) { q }
                drafts += Draft("fact-given", q, steps(real("checklist_add") to mapOf("item" to event)), listOf("checklist_add"), forced = mapOf("calendar" to withPlace))
                if (i % 4 == 0) drafts += Draft("fact-missing", q, refuse("There is nothing in your calendar in the next 7 days."), listOf("checklist_add"), forced = mapOf("calendar" to empty))
            }
            goNext.of(split).at(i).let { base -> val q = said(i, base); check(sent(q, "calendar")) { q }
                drafts += Draft("fact-given", q, steps(real("directions") to mapOf("place" to place)), listOf("directions"), forced = mapOf("calendar" to withPlace))
                // The event is there and does not say where: that is asked, not made up.
                drafts += Draft("fact-ask", q, clarify("Where is $event?"), listOf("directions"), forced = mapOf("calendar" to noPlace))
            }
            // An event by its own name ("my dentist", "physio") is not a word the calendar is sent for: it is looked for.
            textTime.of(split).at(i).let { base -> val q = said(i, base.replace("{p}", person.name).replace("{e}", event.lowercase()))
                val known = "People: ${person.name} (${person.number})"
                val plan = steps(real("message") to mapOf("number" to person.number, "text" to "$event is at $time"))
                if (sent(q, "calendar")) drafts += Draft("fact-given", q, plan, listOf("message"), grounding = known, forced = mapOf("calendar" to withPlace))
                else {
                    drafts += Draft("look", q, look(LookSource.CALENDAR), listOf("message"), grounding = known, forced = mapOf("calendar" to null))
                    drafts += Draft("found", AgentTurns.found(q, listOf(LookSource.CALENDAR), found(LookSource.CALENDAR, withPlace)), plan, listOf("message"), grounding = known, forced = mapOf("calendar" to null))
                    if (i % 3 == 0) drafts += Draft("found-nothing", AgentTurns.found(q, listOf(LookSource.CALENDAR), found(LookSource.CALENDAR, empty)),
                        refuse("I don't see ${event.lowercase()} in your calendar."), listOf("message"), grounding = known, forced = mapOf("calendar" to null))
                }
            }
        }
        // Someone named in a way the launcher's own lookup misses (no capital, no "call" or "text" before the name): the planner asks
        // for the contacts, and is told who was found with their number, or nobody.
        val reach = ask("give {p} a ring | get {p} on the phone | write back to {p} saying {b} | drop {p} a line saying {b} | let {p} know {b}",
            "buzz {p} | fire off a note to {p} saying {b}", "get hold of {p} by phone | pass on to {p} that {b}")
        reach.of(split).forEachIndexed { i, t ->
            for (k in 0 until if (split == Split2.TRAIN) 6 else 2) {
                val p = w.people.at(i + k); val b = listOf("on my way", "running late", "call me when you can", "see you at 6").at(i + k)
                val name = p.name.lowercase()
                val q = said(i + k, t.replace("{p}", name).replace("{b}", b)); val texting = "{b}" in t
                val step = if (texting) real("message") to mapOf("number" to p.number, "text" to b) else real("dial") to mapOf("number" to p.number)
                val tool = if (texting) "message" else "dial"
                drafts += Draft("look", q, look(LookSource.CONTACTS), listOf(tool))
                drafts += Draft("found", AgentTurns.found(q, listOf(LookSource.CONTACTS), found(LookSource.CONTACTS, ContextText.people(listOf(p.name to p.number)))), steps(step), listOf(tool))
                drafts += Draft("found-ask", AgentTurns.found(q, listOf(LookSource.CONTACTS), found(LookSource.CONTACTS, null)), clarify("I don't have a number for ${p.name}. What is it?"), listOf(tool))
                // Two it could be: asked, not guessed.
                if (k == 0) drafts += Draft("found-ask", AgentTurns.found(q, listOf(LookSource.CONTACTS), found(LookSource.CONTACTS, ContextText.people(listOf(p.name to p.number, "${p.name.substringBefore(' ')} Okonkwo" to "555 0190")))),
                    clarify("Which one: ${p.name} or ${p.name.substringBefore(' ')} Okonkwo?"), listOf(tool))
                // Already resolved by the launcher: no look.
                drafts += Draft("fact-given", q, steps(step), listOf(tool), grounding = "People: ${p.name} (${p.number})")
            }
        }
        // A condition on a fact the request names: the battery is sent for the word "battery", and is acted on or not.
        val saver = ask("if my battery is under 20 percent open battery settings | open the battery settings if my battery is below 20 percent", "should the battery be under 20 percent, open battery settings",
            "when the battery is below 20 percent open the battery page")
        saver.of(split).forEachIndexed { i, base -> for (k in 0 until 3) { val q = said(i + k, base); check(sent(q, "battery")) { q }
            val low = listOf(14, 9, 18).at(i + k); val high = listOf(82, 55, 97).at(i + k)
            drafts += Draft("fact-given", q, steps(real("settings_panel") to mapOf("panel" to "battery")), listOf("settings_panel"), forced = mapOf("battery" to ContextText.battery(low, false, null)))
            drafts += Draft("fact-missing", q, refuse("The battery is at $high percent, so I left it."), listOf("settings_panel"), forced = mapOf("battery" to ContextText.battery(high, k % 2 == 0, null)))
        } }
        // A time given by the calendar is copied as it was said: the launcher works it out (EventTime), the planner does not.
        ask("wake me {n} before my first meeting tomorrow | set an alarm {n} before my next meeting | alarm {n} before the {e} tomorrow", "get me up {n} before my first meeting tomorrow",
            "alarm for {n} before my next appointment").of(split).forEachIndexed { i, t ->
            for (k in 0 until 4) {
                val n = listOf("30 minutes", "an hour", "15 minutes", "half an hour", "45 minutes").at(i + k); val e = w.events.at(i + k).first.lowercase()
                val q = said(i + k, t.replace("{n}", n).replace("{e}", e)); val tail = t.substringAfter("{n} ").replace("{e}", e)
                act("event-time", q, "alarm", "when" to "$n $tail")
            }
        }

        // ---- A step that failed: another way, a question, or a refusal that says why. Never the same step again.
        fun failedRow(q: String, tried: Pair<Tool, Map<String, String>>, why: String, reply: String, uses: List<String>, grounding: String = "", forced: Map<String, String?>? = null) {
            val step = Step(tried.first, Params(tried.second))
            drafts += Draft("failed", AgentTurns.failed(q, step, why), reply, uses, null, grounding, forced = forced)
        }
        ask("open {a} | launch {a} | start {a}", "fire up {a}", "bring up {a}").of(split).forEachIndexed { i, t ->
            for (k in 0 until if (split == Split2.TRAIN) 6 else 2) {
                val near = listOf("Spotify" to "Spotube", "Chrome" to "Vanadium", "Gmail" to "K-9 Mail", "WhatsApp" to "Signal", "Google Maps" to "Organic Maps", "YouTube" to "NewPipe").at(i + k)
                val q = said(i + k, t.replace("{a}", near.first))
                val tried = real("open_app") to mapOf("app" to near.first); val why = "I couldn't find an app called \"${near.first}\"."
                // Something like it is installed: offered, not opened unasked.
                failedRow(q, tried, why, clarify("${near.first} is not installed. Open ${near.second} instead?"), listOf("open_app"), forced = mapOf("apps" to ContextText.list("Installed apps (most used first)", listOf("Camera", near.second, "Clock", "Files"), 40)))
                // Nothing like it: said plainly.
                if (k % 2 == 0) failedRow(q, tried, why, refuse("${near.first} is not installed on this phone."), listOf("open_app"), forced = mapOf("apps" to ContextText.list("Installed apps (most used first)", listOf("Camera", "Clock", "Files"), 40)))
            }
        }
        w.items.forEachIndexed { i, item ->
            val q = said(i, "add $item to my list")
            failedRow(q, real("checklist_add") to mapOf("item" to item), "There is no Checklist widget on Home.", steps(real("note") to mapOf("text" to item)), listOf("checklist_add", "note"))
        }
        listOf("ringer" to ("mode" to "silent") to ("sound" to "Android would not let me change the ringer."), "brightness" to ("level" to "down") to ("display" to "Android would not let me change the brightness."))
            .forEach { (triedSpec, alt) ->
                val (tool, value) = triedSpec; val (panel, why) = alt
                val asks = if (tool == "ringer") ring.getValue("silent") else bright.getValue("down")
                asks.of(split).forEachIndexed { i, base ->
                    failedRow(said(i, base), real(tool) to mapOf(value), why, steps(real("settings_panel") to mapOf("panel" to panel)), listOf(tool, "settings_panel"))
                }
            }
        listOf("pause the music", "skip this song", "next track").forEachIndexed { i, base ->
            val action = if (i == 0) "pause" else "next"
            failedRow(dressed(i, base), real("media") to mapOf("action" to action), "Nothing is playing.", refuse("Nothing is playing, so there is nothing to ${if (i == 0) "pause" else "skip"}."), listOf("media"))
        }
        w.people.forEachIndexed { i, p ->
            val q = said(i, "reply to ${p.name} saying on my way")
            val tried = real("reply") to mapOf("to" to p.name, "text" to "on my way")
            failedRow(q, tried, "That message from ${p.name} is gone, so nothing was sent.", steps(real("message") to mapOf("number" to p.number, "text" to "on my way")), listOf("reply", "message"), grounding = "People: ${p.name} (${p.number})")
            failedRow(q, tried, "That message from ${p.name} is gone, so nothing was sent.", clarify("That message is gone. What is ${p.name}'s number?"), listOf("reply", "message"))
        }

        // ---- A reply that broke a rule, sent back with the reason: the same request, answered within the rule.
        val broken = listOf(
            Triple("time how long this takes", real("stopwatch") to mapOf("action" to "start"), "action must be one of start, stop, reset"),
            Triple("put my phone on vibrate", real("ringer") to mapOf("mode" to "vibrate"), "mode must be one of ring, vibrate, silent"),
            Triple("take a selfie", real("camera") to mapOf("mode" to "selfie"), "mode must be one of photo, selfie, video"),
            Triple("skip this song", real("media") to mapOf("action" to "next"), "action must be one of play, pause, next, previous"),
            Triple("wifi settings", real("settings_panel") to mapOf("panel" to "wifi"), "panel must be one of wifi, bluetooth, nfc, airplane, mobile_data, hotspot, location, display, sound, battery, storage, apps, notifications, security, accessibility, date, vpn"),
            Triple("take a screenshot", real("device") to mapOf("action" to "screenshot"), "action must be one of screenshot, lock, notifications, quick_settings, recents"),
            Triple("turn the torch on", real("torch") to mapOf("on" to "true"), "on must be true or false"),
            Triple("set a timer for 10 minutes", real("timer") to mapOf("length" to "10 minutes", "label" to ""), "There is no \"countdown\" tool"),
            Triple("open maps", real("open_app") to mapOf("app" to "maps"), "The plan had no steps"))
        broken.forEachIndexed { i, (base, step, why) -> for (k in 0 until if (split == Split2.TRAIN) 6 else 2)
            drafts += Draft("retry", AgentTurns.retry(dressed(i + k, base), why), steps(step), listOf(step.first.name)) }

        // ---- Four and five things at once, in the order they were said.
        val singles = drafts.filter { (it.family == "new-tool" || it.family == "v1-single") && it.uses.size == 1 && it.context == null && it.grounding.isEmpty() && "," !in it.query && " and " !in it.query && " then " !in it.query }
        val joins = if (split == Split2.TRAIN) listOf(", ", " and ", ", then ", " and then ") else if (split == Split2.DEV) listOf(", ", " then ") else listOf(" and ", ", and after that ")
        for (i in 0 until if (split == Split2.TRAIN) 1400 else 120) {
            val count = 4 + i % 2
            val picked = ArrayList<Draft>()
            var guard = 0
            while (picked.size < count && guard++ < 200) {
                val d = singles[rnd.nextInt(singles.size)]
                // Three of one tool at most (the executor's cap), and nothing said twice.
                if (picked.count { it.uses == d.uses } >= 2 || picked.any { it.query == d.query }) continue
                picked += d
            }
            if (picked.size < count) continue
            val query = picked.mapIndexed { j, d -> (if (j == 0) "" else if (j == picked.lastIndex) joins.at(i + 1) else joins.at(0)) + d.query.trim().trimEnd('.', '!') }.joinToString("")
            val all = picked.flatMap { d -> org.json.JSONObject(d.reply).getJSONArray("steps").let { a -> (0 until a.length()).map { a.getJSONObject(it).toString() } } }
            drafts += Draft("multi-${count}", query, "{\"steps\":[" + all.joinToString(",") + "]}", picked.flatMap { it.uses })
        }

        // ---- Each draft with a tool list of its own, read back by the real parser.
        val rows = ArrayList<Row2>(drafts.size)
        val seen = HashSet<String>()
        val decoys = madeUp.map { it.tool }
        for ((index, d) in drafts.withIndex()) {
            // The first corpus's rows are given two lists each in training, so the same words are seen under different lists.
            // In training every row is seen under several lists (and several sets of facts), so the same words are learnt against
            // the list and not beside one. The rows that are new with this contract are few by hand and are seen under more.
            val copies = when {
                split != Split2.TRAIN -> 1
                d.family.startsWith("v1-") -> 2
                d.family.startsWith("multi-") -> 1
                else -> 6
            }
            repeat(copies) { copy ->
                val r = Random(seed * 7919 + index * 13L + copy + split.ordinal * 101L)
                val needed = d.uses.toSet()
                val mine = ArrayList<Tool>()
                for (t in allReal) if (t.name in needed || (t.name !in d.without && r.nextDouble() < KEEP)) mine += t
                for (t in d.extra) if (t.name !in d.without) mine += t
                repeat(r.nextInt(4)) { decoys[r.nextInt(decoys.size)].let { t -> if (t.name !in d.without && mine.none { it.name == t.name }) mine += t } }
                // The usual order half the time; otherwise shuffled, so position says nothing.
                val listed = if (r.nextBoolean()) mine.sortedBy { t -> allReal.indexOfFirst { it.name == t.name }.let { if (it < 0) 999 else it } } else mine.shuffled(r)
                val context = d.context ?: TrainingContext.forQuery(d.query.lineSequence().first(), r, d.forced.orEmpty())
                val user = TrainingFormat.turn(d.query, context, d.grounding)
                if (!seen.add(listed.joinToString(",") { it.name } + user + d.completionKey())) return@repeat
                val reply = canonical(d.reply, listed) ?: error("a reply its own list does not allow: ${d.family}: ${d.query} -> ${d.reply}")
                rows += Row2(d.family, d.query, user, reply, listed)
            }
        }
        return rows
    }

    /** How likely a tool the reply does not need is to be in the row's list anyway. */
    private const val KEEP = .82

    private fun Draft.completionKey() = reply

    fun system(tools: List<Tool>): String = "<|im_start|>system\n" + TrainingFormat.SYSTEM + "\n\n" + Catalogue.prompt(tools, PROTOCOL).trim() + "\n<|im_end|>\n"

    /**
     * The system turn and the grammar in pieces, so a corpus file need not repeat nine thousand characters a row: a row names its tools
     * in order, and whoever reads it puts the pieces together ([systemHead] + the tools' blocks joined by a newline + [SYSTEM_TAIL]; the grammar likewise).
     * `samples.jsonl` holds whole rows to check a reader's assembly against, character for character.
     */
    val systemHead: String get() = system(emptyList()).removeSuffix(SYSTEM_TAIL).trimEnd('\n') + "\n"
    const val SYSTEM_TAIL = "\n<|im_end|>\n"
    fun toolBlock(tool: Tool): String = system(listOf(tool)).removePrefix(systemHead).removeSuffix(SYSTEM_TAIL)
    /** A tool's own grammar line, and the name of its rule. */
    fun grammarLine(tool: Tool): Pair<String, String> {
        val lines = Catalogue.grammar(listOf(tool), PROTOCOL).lines()
        return lines[2].removePrefix("step ::= ") to lines[3]
    }
    /** The grammar's lines before the tools' and after them, with "step ::= " to be filled with the rule names joined by " | ". */
    fun grammarFrame(): Pair<List<String>, List<String>> {
        val lines = Catalogue.grammar(listOf(allReal.first()), PROTOCOL).trimEnd('\n').lines()
        return lines.take(2) to lines.drop(4)
    }
    val everyTool: List<Tool> get() = allReal + madeUp.map { it.tool } + heldOut.map { it.tool }

    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 .,:;/?_=&%+#'@!()*$-"
    fun grammarSafe(text: String) = text.length <= 400 && text.all { it in ALPHABET }

    /** [reply] as the phone would write it under [tools], or null when the parser refuses it or it holds a character the grammar cannot write. */
    fun canonical(reply: String, tools: List<Tool>): String? {
        val plan = (runCatching { PlanParser.parse(reply, tools, PROTOCOL) }.getOrNull() as? ParseResult.Ok)?.plan ?: return null
        return when (plan) {
            is Plan.Steps -> if (plan.steps.all { s -> s.params.values.values.all(::grammarSafe) }) reply else null
            is Plan.Clarify -> reply.takeIf { grammarSafe(plan.question) }
            is Plan.Refuse -> reply.takeIf { grammarSafe(plan.reason) }
            is Plan.OfferSearch -> reply.takeIf { grammarSafe(plan.query) }
            is Plan.Look -> reply
        }
    }

    /** The frozen eval sets of the first contract, asked under protocol 2 and the whole list: they must not get worse. */
    fun regression(resource: String): List<Row2> {
        val text = CorpusV2::class.java.getResourceAsStream(resource)?.bufferedReader()?.readText() ?: return emptyList()
        val rnd = Random(SEED + resource.length)
        val rows = org.json.JSONArray(text)
        return (0 until rows.length()).mapNotNull { i ->
            val row = rows.getJSONObject(i)
            val reply = when {
                row.has("steps") -> org.json.JSONObject().put("steps", row.getJSONArray("steps")).toString()
                // What is scored is that it asked, refused or offered a search; the words are a stand-in.
                row.has("clarify") -> clarify("Which one do you mean?"); row.has("refuse") -> refuse("I can't do that with the tools I have."); row.has("search") -> TrainingFormat.search("that")
                else -> return@mapNotNull null
            }
            val dressed = TrainingCorpus.dressed(Example(row.getString("q"), if (row.has("steps")) reply else "", "eval"), rnd)
            Row2("regression", dressed.query, TrainingFormat.turn(dressed.query, dressed.context, dressed.grounding), reply, allReal)
        }
    }
}
