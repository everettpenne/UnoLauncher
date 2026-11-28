package com.jake.duolauncher.search.agent.tools

import com.jake.duolauncher.search.agent.*

/*
 * The tools added with protocol 2 (ToolRegistry.next): the everyday things a phone assistant is asked for that the first nineteen did
 * not cover. Each is defined in full (name, summary, parameters, examples, checks), because that is what the planner is trained to
 * read; what a phone cannot do yet is not offered there (a capability it lacks, or ToolEnv's default saying no), so the tool simply
 * is not in that phone's list.
 */

/** Opens the camera, ready to take what was asked for. Nothing is taken until the person presses the shutter. */
internal object CameraTool : Tool(
    name = "camera",
    summary = "Opens the camera. mode: photo, selfie or video.",
    params = listOf(Param("mode", Kind.Enum, choices = listOf("photo", "selfie", "video"))),
    effect = Effect.Local,
    examples = listOf("take a picture", "selfie", "record a video"),
) {
    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val mode = params.text("mode") ?: return ToolResult(false, "I didn't catch what to take.")
        return if (env.openCamera(mode)) ToolResult(true, when (mode) { "video" -> "Camera open for a video"; "selfie" -> "Camera open for a selfie"; else -> "Camera open" })
        else ToolResult(false, "I couldn't open the camera.")
    }
}

/** Starts an email as a draft in the mail app. Nothing is sent: the person presses send there. */
internal object EmailTool : Tool(
    name = "email",
    summary = "Opens a new email as a draft, not sent. to: an email address. subject and body: the words given, if any.",
    params = listOf(Param("to", Kind.Text, maxLength = 120), Param("subject", Kind.Text, required = false, maxLength = 120),
        Param("body", Kind.Text, required = false, maxLength = ReplyLogic.MAX_TEXT)),
    effect = Effect.Local,
    examples = listOf("email sam@example.com", "write an email to jo@example.org saying the report is attached", "email ana@example.com about friday"),
) {
    /** The address must be one the person gave, or one Uno found for a name they gave. */
    override fun missing(params: Params, said: String): Missing? {
        val to = params.text("to").orEmpty()
        return if ('@' in to && said.contains(to, ignoreCase = true)) null else Missing("What is their email address?", "to")
    }
    override fun describe(params: Params, env: ToolEnv): String? = params.text("to")?.let { "Start an email to $it" }
    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val to = params.text("to") ?: return ToolResult(false, "I didn't catch the address.")
        return if (env.composeEmail(to, params.text("subject").orEmpty(), params.text("body").orEmpty())) ToolResult(true, "Email to $to is open, press send there")
        else ToolResult(false, "There is no mail app to write that in.")
    }
}

/** The phone's ringer: ringing, vibrating or silent. Taken back with Undo. */
internal object RingerTool : Tool(
    name = "ringer",
    summary = "Sets how the phone rings for calls and notifications. mode: ring, vibrate or silent.",
    params = listOf(Param("mode", Kind.Enum, choices = listOf("ring", "vibrate", "silent"))),
    effect = Effect.Local,
    needs = setOf(Capability.Ringer),
    examples = listOf("put my phone on vibrate", "ringer on", "silent mode"),
) {
    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val mode = params.text("mode") ?: return ToolResult(false, "Ring, vibrate or silent?")
        val was = env.ringer()
        if (was == mode) return ToolResult(true, "The phone is already on $mode")
        if (!env.setRinger(mode)) return ToolResult(false, "Android would not let me change the ringer.")
        return ToolResult(true, "The phone is on $mode", undo = was?.let { before -> { env.setRinger(before); ToolResult(true, "The phone is on $before again") } })
    }
}

/** Asks the music app to play something by name. */
internal object PlayTool : Tool(
    name = "play",
    summary = "Asks the music app to play something by name: a song, an artist, an album, a playlist, a podcast or a station.",
    params = listOf(Param("what", Kind.Text, maxLength = 120)),
    effect = Effect.Local,
    examples = listOf("play Blue in Green", "put on some Miles Davis", "play my running playlist"),
) {
    /** What to play must be the person's: "play something" is asked about, not chosen for them. */
    override fun missing(params: Params, said: String): Missing? {
        val what = params.text("what") ?: return Missing("What should I play?", "what")
        val words = Regex("""[\p{L}\p{N}']+""").findAll(what.lowercase()).map { it.value }.filter { it.length > 2 }.toList()
        return if (words.isNotEmpty() && words.all { said.lowercase().contains(it) }) null else Missing("What should I play?", "what")
    }
    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val what = params.text("what") ?: return ToolResult(false, "I didn't catch what to play.")
        return if (env.playFromSearch(what)) ToolResult(true, "Asked your music app for “$what”") else ToolResult(false, "No app here can play that by name.")
    }
}

/** The screen's brightness. Taken back with Undo. */
internal object BrightnessTool : Tool(
    name = "brightness",
    summary = "Changes the screen brightness. level: up, down, max, min, or a percentage the person said.",
    params = listOf(Param("level", Kind.Text, maxLength = 20)),
    effect = Effect.Local,
    needs = setOf(Capability.SystemSettings),
    examples = listOf("make the screen brighter", "dim the screen", "brightness to 30 percent"),
) {
    private const val STEP = 20
    private val percent = Regex("""^(\d{1,3})\s*(?:%|percent)?$""")

    fun target(level: String, now: Int): Int? = when (val l = SpokenTime.digits(level).trim()) {
        "up", "brighter", "higher" -> (now + STEP).coerceAtMost(100)
        "down", "dimmer", "lower", "darker" -> (now - STEP).coerceAtLeast(1)
        "max", "full", "maximum" -> 100
        "min", "minimum", "lowest" -> 1
        "half" -> 50
        else -> percent.matchEntire(l)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..100 }
    }

    /** A percentage must be one the person gave. */
    override fun missing(params: Params, said: String): Missing? {
        val level = params.text("level") ?: return Missing("How bright?", "level")
        val number = percent.matchEntire(SpokenTime.digits(level).trim())?.groupValues?.get(1) ?: return if (target(level, 50) == null) Missing("How bright?", "level") else null
        return if (SpokenTime.digits(said).contains(number)) null else Missing("How bright?", "level")
    }
    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val before = env.brightness() ?: return ToolResult(false, "I couldn't read the brightness.")
        val to = target(params.text("level").orEmpty(), before) ?: return ToolResult(false, "I didn't catch how bright.")
        if (!env.setBrightness(to)) return ToolResult(false, "Android would not let me change the brightness.")
        return ToolResult(true, "Brightness at $to%", undo = { env.setBrightness(before); ToolResult(true, "Brightness back at $before%") })
    }
}

/** What the phone's own buttons and gestures do, done for the person. */
internal object DeviceTool : Tool(
    name = "device",
    summary = "Does what a button or gesture does. action: screenshot, lock, notifications (opens the shade), quick_settings or recents.",
    params = listOf(Param("action", Kind.Enum, choices = listOf("screenshot", "lock", "notifications", "quick_settings", "recents"))),
    effect = Effect.Local,
    needs = setOf(Capability.Accessibility),
    examples = listOf("take a screenshot", "lock the phone", "show my notifications"),
) {
    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val action = params.text("action") ?: return ToolResult(false, "I didn't catch what to do.")
        return if (env.deviceAction(action)) ToolResult(true, when (action) {
            "screenshot" -> "Screenshot taken"; "lock" -> "Locked"; "notifications" -> "Notifications open"; "quick_settings" -> "Quick settings open"; else -> "Recent apps open"
        }) else ToolResult(false, "Android would not do that from here.")
    }
}

/** Opens the new-contact form filled in. Nothing is saved until the person saves it there. */
internal object ContactAddTool : Tool(
    name = "contact_add",
    summary = "Opens a new contact, filled in and not saved. name: who. number: their phone number.",
    params = listOf(Param("name", Kind.Text, maxLength = 60), Param("number", Kind.Text, maxLength = 40)),
    effect = Effect.Local,
    examples = listOf("save 555 0142 as Sam", "add Dana to my contacts, 555 0177", "new contact Jo 020 7946 0000"),
) {
    /** The number must be one the person gave. */
    override fun missing(params: Params, said: String): Missing? {
        val digits = params.text("number").orEmpty().filter { it.isDigit() }
        return if (digits.length >= 3 && said.filter { it.isDigit() }.contains(digits)) null
        else Missing("What is ${params.text("name") ?: "their"}${if (params.text("name") != null) "'s" else ""} number?", "number")
    }
    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val name = params.text("name") ?: return ToolResult(false, "I didn't catch the name.")
        val number = params.text("number") ?: return ToolResult(false, "I didn't catch the number.")
        return if (env.addContact(name, number)) ToolResult(true, "New contact “$name” is open, tap Save") else ToolResult(false, "There is no contacts app to add that to.")
    }
}

/** An app's own page in Android's settings: where it is uninstalled, and its notifications and permissions are. */
internal object AppInfoTool : Tool(
    name = "app_info",
    summary = "Opens an app's page in Android settings, where it can be uninstalled and its notifications and permissions changed.",
    params = listOf(Param("app", Kind.Text, maxLength = 60)),
    effect = Effect.Local,
    examples = listOf("uninstall Maps", "app info for Signal", "turn off notifications for Photos"),
) {
    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val wanted = params.text("app") ?: return ToolResult(false, "I didn't catch which app.")
        if (env.apps.isEmpty()) return ToolResult(false, "I can't see your apps from here. Open search once, then ask me again.")
        val match = env.apps.firstOrNull { it.label.equals(wanted, ignoreCase = true) } ?: env.apps.firstOrNull { it.label.startsWith(wanted, ignoreCase = true) }
            ?: env.apps.firstOrNull { it.label.contains(wanted, ignoreCase = true) } ?: return ToolResult(false, "I couldn't find an app called \"$wanted\".")
        return if (env.openAppInfo(match)) ToolResult(true, "${match.label}'s settings are open") else ToolResult(false, "Android wouldn't open ${match.label}'s settings.")
    }
}

/** The alarms that are set, in the clock app: an app cannot switch another app's alarm off, so the list is opened. */
internal object AlarmsShowTool : Tool(
    name = "alarms_show",
    summary = "Shows the alarms that are set, in the clock app, to change one or switch it off.",
    effect = Effect.Local,
    examples = listOf("show my alarms", "turn off my alarm", "cancel the 7am alarm"),
) {
    override suspend fun execute(params: Params, env: ToolEnv): ToolResult =
        if (env.showAlarms()) ToolResult(true, "Your alarms are open, change them there") else ToolResult(false, "There is no clock app to show them.")
}

/** The calendar, opened at a day. */
internal object CalendarOpenTool : Tool(
    name = "calendar_open",
    summary = "Opens the calendar at a day. day: today, tomorrow, a weekday or a date, in the person's words.",
    params = listOf(Param("day", Kind.Text, required = false, maxLength = 40)),
    effect = Effect.Local,
    examples = listOf("show my calendar", "open the calendar for friday", "what does tomorrow look like in my calendar"),
) {
    private fun date(params: Params, env: ToolEnv): java.time.LocalDate? {
        val day = params.text("day") ?: return env.now().toLocalDate()
        if (day.lowercase() in setOf("today", "now")) return env.now().toLocalDate()
        return SpokenTime.start("$day at noon", env.now())?.toLocalDate()
    }
    override fun problem(params: Params, env: ToolEnv): String? = if (date(params, env) == null) "Which day?" else null
    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val date = date(params, env) ?: return ToolResult(false, "I didn't catch which day.")
        return if (env.openCalendarAt(date)) ToolResult(true, "Calendar open at ${Speak.dayTime(date.atTime(12, 0), env.now()).substringBefore(" at ")}")
        else ToolResult(false, "There is no calendar app to open.")
    }
}

/** [SettingsPanelTool] with the rest of the pages people ask for. A separate tool object, so the first planners' list is not changed. */
internal object SettingsPagesTool : Tool(
    name = "settings_panel",
    summary = "Opens an Android settings page, for a switch an app cannot flip itself: wifi, bluetooth, nfc, airplane, mobile_data, hotspot, location, " +
        "display, sound, battery, storage, apps, notifications, security, accessibility, date, vpn.",
    params = listOf(Param("panel", Kind.Enum, choices = listOf("wifi", "bluetooth", "nfc", "airplane", "mobile_data", "hotspot", "location", "display",
        "sound", "battery", "storage", "apps", "notifications", "security", "accessibility", "date", "vpn"))),
    effect = Effect.Local,
    examples = listOf("wifi settings", "turn bluetooth off", "turn on my hotspot", "switch location off"),
) {
    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val panel = params.text("panel") ?: return ToolResult(false, "I didn't catch which setting.")
        env.openSettingsPanel(panel)
        return ToolResult(true, "${panel.replace('_', ' ').replaceFirstChar { it.uppercase() }} settings open — switch it there")
    }
}
