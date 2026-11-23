package com.jake.duolauncher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What Home learns from how it is used, and what it makes of it: which apps are opened at which hour (for the Suggested apps widget),
 * and which widget of a stack is looked at when (for a stack that turns itself). Pure, with the hour and the day handed in, so every
 * rule is unit-tested.
 *
 * A habit is 24 numbers, one an hour: how often the thing happened in that hour, fading by a tenth a day so last month's routine gives
 * way to this week's. Nothing else is kept: no times, no order, no days.
 */
internal object HabitRules {
    const val HOURS = 24
    /** What is left of a count after a day. */
    const val DAILY_FADE = .9f
    /** A count below this has faded away and is dropped. */
    const val GONE = .05f
    /** A stack turns itself only for a habit at least this strong: about two looks at that hour in the last few days. */
    const val STACK_SURE = 1.5f

    class Habit(val counts: FloatArray, val day: Long) {
        init { require(counts.size == HOURS) }
    }

    fun empty(day: Long) = Habit(FloatArray(HOURS), day)

    /** [habit] as it stands on [day]: every count faded once for each day since it was last written. */
    fun faded(habit: Habit, day: Long): Habit {
        val days = (day - habit.day).coerceIn(0, 400)
        if (days == 0L) return habit
        val keep = Math.pow(DAILY_FADE.toDouble(), days.toDouble()).toFloat()
        return Habit(FloatArray(HOURS) { (habit.counts[it] * keep).let { c -> if (c < GONE) 0f else c } }, day)
    }

    /** [habit] with one more happening at [hour] on [day]. */
    fun noted(habit: Habit?, hour: Int, day: Long): Habit {
        val now = faded(habit ?: empty(day), day)
        return Habit(now.counts.copyOf().also { it[((hour % HOURS) + HOURS) % HOURS] += 1f }, day)
    }

    /** How strongly [habit] says "now" at [hour]: that hour's count, half of each neighbour's and a little of the hours beside those,
     *  so a habit of opening something at about eight still counts at ten to nine. */
    fun pull(habit: Habit, hour: Int, day: Long): Float {
        val c = faded(habit, day).counts
        fun at(offset: Int) = c[((hour + offset) % HOURS + HOURS) % HOURS]
        return at(0) + .5f * (at(-1) + at(1)) + .15f * (at(-2) + at(2))
    }

    fun isGone(habit: Habit, day: Long): Boolean = faded(habit, day).counts.all { it == 0f }

    /**
     * The apps to suggest at [hour]: of [allowed] (what may be shown: never a hidden app, nor one Focus is hiding), those with a habit
     * at this hour, strongest first, then [fallback] (the apps opened most and most lately, which the launcher has always kept for
     * search) to fill the rest. Each app once, at most [limit].
     */
    fun suggest(habits: Map<String, Habit>, hour: Int, day: Long, allowed: Set<String>, fallback: List<String>, limit: Int): List<String> {
        val byHabit = habits.asSequence().filter { it.key in allowed }.map { it.key to pull(it.value, hour, day) }.filter { it.second > 0f }
            .sortedWith(compareByDescending<Pair<String, Float>> { it.second }.thenBy { it.first }).map { it.first }.toList()
        return (byHabit + fallback.filter { it in allowed }).distinct().take(limit.coerceAtLeast(0))
    }

    /** Which of a stack's [members] (its widgets in order, the first being the one on Home) to turn to at [hour], or null to leave it
     *  where it is: the one with the strongest habit now, if that is strong enough to be a habit at all. */
    fun stackPage(habits: List<Habit?>, hour: Int, day: Long): Int? {
        val pulls = habits.map { it?.let { h -> pull(h, hour, day) } ?: 0f }
        val best = pulls.indices.maxByOrNull { pulls[it] } ?: return null
        return best.takeIf { pulls[it] >= STACK_SURE }
    }

    fun encode(habit: Habit): String = habit.day.toString() + "|" + habit.counts.joinToString(",") { if (it == 0f) "0" else "%.2f".format(java.util.Locale.ROOT, it) }

    /** Reads what [encode] wrote; anything else is no habit. */
    fun decode(raw: String?): Habit? {
        val parts = raw?.split('|') ?: return null
        if (parts.size != 2) return null
        val day = parts[0].toLongOrNull() ?: return null
        val counts = parts[1].split(',').map { it.toFloatOrNull()?.takeIf { v -> v.isFinite() && v >= 0f } ?: return null }
        return if (counts.size == HOURS) Habit(counts.toFloatArray(), day) else null
    }
}

/**
 * Home's habits, kept. They are in a preference file of their own on this phone ("home_habits"): in no Android backup or device
 * transfer, not in Uno's settings backup, never sent anywhere, and forgotten with one tap in Home Screen settings. Nothing is recorded
 * unless **Learn from how you use Home** is on, which it is not until switched on.
 */
internal object HomeHabits {
    private const val FILE = "home_habits"
    private const val LEARN = "learn"
    private const val APP = "app:"
    private const val STACK = "stack:"
    private var prefs: android.content.SharedPreferences? = null

    /** Whether Home is learning. Snapshot state, so what shows it (a widget's hint, the settings switch) follows it. */
    var learning by mutableStateOf(false)
        private set
    /** Moves on whenever something is learned or forgotten, so a suggestion drawn from the habits is worked out again. */
    var revision by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        learning = runCatching { prefs?.getBoolean(LEARN, false) }.getOrNull() == true
    }

    fun setLearning(context: Context, on: Boolean) {
        init(context)
        learning = on
        prefs?.edit()?.putBoolean(LEARN, on)?.apply()
    }

    private fun today(nowMs: Long) = java.time.Instant.ofEpochMilli(nowMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
    private fun hour(nowMs: Long) = java.time.Instant.ofEpochMilli(nowMs).atZone(java.time.ZoneId.systemDefault()).hour

    private fun note(key: String, nowMs: Long) {
        val p = prefs ?: return
        if (!learning) return
        val next = HabitRules.noted(HabitRules.decode(p.getString(key, null)), hour(nowMs), today(nowMs))
        p.edit().putString(key, HabitRules.encode(next)).apply()
        revision++
    }

    /** An app was opened from the launcher. */
    fun noteLaunch(context: Context, appId: String, nowMs: Long = System.currentTimeMillis()) { init(context); note(APP + appId, nowMs) }

    /** A stack was turned by hand to the widget [member] (its own code, which stays the same wherever it sits in the stack: a widget
     *  taken out or added does not hand one widget's habit to another). */
    fun noteStackView(slot: Int, member: Int, nowMs: Long = System.currentTimeMillis()) = note("$STACK$slot:$member", nowMs)

    /** Every app's habit. Ones that have faded away altogether are dropped from storage as they are read. */
    fun apps(nowMs: Long = System.currentTimeMillis()): Map<String, HabitRules.Habit> {
        val p = prefs ?: return emptyMap()
        val day = today(nowMs)
        val gone = ArrayList<String>()
        val out = HashMap<String, HabitRules.Habit>()
        for ((key, value) in p.all) {
            if (!key.startsWith(APP)) continue
            val habit = HabitRules.decode(value as? String)
            if (habit == null || HabitRules.isGone(habit, day)) gone += key else out[key.removePrefix(APP)] = habit
        }
        if (gone.isNotEmpty()) p.edit().apply { gone.forEach(::remove) }.apply()
        return out
    }

    /** The apps to suggest now ([HabitRules.suggest]). */
    fun suggestions(allowed: Set<String>, fallback: List<String>, limit: Int, nowMs: Long = System.currentTimeMillis()): List<String> =
        HabitRules.suggest(if (learning) apps(nowMs) else emptyMap(), hour(nowMs), today(nowMs), allowed, fallback, limit)

    /** Which of [slot]'s stack to turn to now, as a place in [members] (the widgets' codes in order, the one on Home first), or null
     *  to leave it. */
    fun stackPage(slot: Int, members: List<Int>, nowMs: Long = System.currentTimeMillis()): Int? {
        val p = prefs ?: return null
        if (!learning || members.size < 2) return null
        return HabitRules.stackPage(members.map { HabitRules.decode(p.getString("$STACK$slot:$it", null)) }, hour(nowMs), today(nowMs))
    }

    /** How many apps and stacks Home has a habit for, for the settings page. */
    fun learned(): Int = prefs?.all?.keys?.count { it.startsWith(APP) || it.startsWith(STACK) } ?: 0

    /** Forgets everything learned (the switch is left as it is). */
    fun forget() {
        val p = prefs ?: return
        p.edit().apply { p.all.keys.filter { it != LEARN }.forEach(::remove) }.apply()
        revision++
    }
}

/** The apps Home may show and how to open one, for a widget that offers apps. Null outside Home (the widget picker's preview). */
internal class HomeApps(val apps: List<AppEntry>, val launch: (AppEntry) -> Unit, val openSettings: () -> Unit)
internal val LocalHomeApps = androidx.compose.runtime.compositionLocalOf<HomeApps?> { null }
