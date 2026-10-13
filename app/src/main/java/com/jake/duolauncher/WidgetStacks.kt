package com.jake.duolauncher

import com.jake.duolauncher.island.*

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Pure rules for widget stacks, so they are unit-tested apart from storage and drawing. */
internal object StackRules {
    const val MAX_MEMBERS = 5

    // A stack member is an Android widget id (0 or more), or an Uno widget (Clock, Date or one from the Uno library) coded as
    // a negative number that carries both its kind and the Home slot it had when it joined: that slot keeps the widget's own
    // saved data (a note's text, a counter's count), so the widget is unchanged by being stacked. Slots stay below 100,000.
    private const val UNO_BASE = 100_000
    fun unoCode(widgetId: Int, slot: Int): Int = -((slot + 1) * UNO_BASE) + widgetId
    fun isUno(code: Int): Boolean = code <= -UNO_BASE
    /** The built-in widget id (CLOCK_WIDGET, a library id, ...) inside a member code from [unoCode]. */
    fun unoWidgetId(code: Int): Int = -((-code) % UNO_BASE)
    /** The Home slot whose saved data the member keeps. */
    fun unoSlot(code: Int): Int = (-code) / UNO_BASE - 1
    fun isMember(code: Int) = code >= 0 || isUno(code)

    /** The slots whose Uno widgets live inside stacks whose base is still on Home. Their saved data must be kept. */
    fun memberSlots(stacks: Map<Int, List<Int>>, placedSlots: Set<Int>): Set<Int> =
        stacks.filterKeys { it in placedSlots }.values.flatten().filter(::isUno).map(::unoSlot).toSet()

    /** "12:3,7:4,5" style storage: slot, then its member widget ids. Unreadable parts are skipped, never fatal. */
    fun parse(raw: String?): Map<Int, List<Int>> = raw.orEmpty().split(';').mapNotNull { entry ->
        val (slot, ids) = entry.split(':', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
        val s = slot.trim().toIntOrNull() ?: return@mapNotNull null
        val members = ids.split(',').mapNotNull { it.trim().toIntOrNull() }.filter(::isMember).distinct()
        if (members.isEmpty()) null else s to members
    }.toMap()

    fun serialize(stacks: Map<Int, List<Int>>): String =
        stacks.filterValues { it.isNotEmpty() }.entries.joinToString(";") { (slot, ids) -> "$slot:${ids.joinToString(",")}" }

    /** Adds [id] to [slot]'s stack unless it is already anywhere, or the stack is full. Null means nothing changed. */
    fun add(stacks: Map<Int, List<Int>>, slot: Int, id: Int): Map<Int, List<Int>>? {
        if (!isMember(id) || stacks.values.any { id in it } || (stacks[slot]?.size ?: 0) >= MAX_MEMBERS) return null
        return stacks + (slot to ((stacks[slot] ?: emptyList()) + id))
    }

    fun remove(stacks: Map<Int, List<Int>>, slot: Int, id: Int): Map<Int, List<Int>> {
        val left = stacks[slot].orEmpty() - id
        return if (left.isEmpty()) stacks - slot else stacks + (slot to left)
    }

    /** Widget ids that must not be pruned: members of stacks whose base widget is still on Home. A stack whose base
     * was removed is dropped (its members are then pruned with it) rather than left as a ghost.
     */
    fun retained(stacks: Map<Int, List<Int>>, placedSlots: Set<Int>): Set<Int> =
        stacks.filterKeys { it in placedSlots }.values.flatten().toSet()

    /** Keeps a page index inside a stack that may have just shrunk (page 0 is the base widget). */
    fun clampPage(page: Int, memberCount: Int) = page.coerceIn(0, memberCount)
}

/** Which widgets are stacked behind which Home widget. Kept in its own preference file, outside the layout model, so
 * the layout's validated format, undo and backups are untouched. (A layout export does not include stacks yet.)
 */
internal object WidgetStacks {
    private var prefs: android.content.SharedPreferences? = null
    var stacks by mutableStateOf<Map<Int, List<Int>>>(emptyMap()); private set

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences("widget_stacks", Context.MODE_PRIVATE)
        stacks = StackRules.parse(runCatching { prefs?.getString("stacks", "") }.getOrNull())
    }

    fun members(slot: Int): List<Int> = stacks[slot].orEmpty()

    /** Slots of Uno widgets stacked behind widgets that are placed: not free for a new widget, and their data is kept. */
    fun unoMemberSlots(placedSlots: Set<Int>): Set<Int> = StackRules.memberSlots(stacks, placedSlots)

    fun add(slot: Int, id: Int): Boolean {
        val next = StackRules.add(stacks, slot, id) ?: return false
        commit(next); return true
    }

    fun remove(slot: Int, id: Int) = commit(StackRules.remove(stacks, slot, id))

    /** Read straight from storage so the model can ask without a Compose dependency. */
    fun retainedIds(placedSlots: Set<Int>): Set<Int> = StackRules.retained(stacks, placedSlots)

    private fun commit(next: Map<Int, List<Int>>) {
        stacks = next
        prefs?.edit()?.putString("stacks", StackRules.serialize(next))?.apply()
    }
}
