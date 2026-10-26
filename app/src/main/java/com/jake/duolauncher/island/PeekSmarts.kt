package com.jake.duolauncher.island

/**
 * Which notifications drop the island down, beyond the person's own switches: only conversations when they ask (People Only), a chat that
 * floods is quieted for a while, and an app whose drop-downs are never opened stops dropping down by itself (listed in settings, and
 * brought back with a tap). Everything is learned and kept on the phone, from notifications Uno already reads. Pure, so it is unit-tested.
 */
internal object PeekSmarts {
    /** A drop-down from an app this many times in a row, never opened, and its drop-downs stop. */
    const val UNOPENED_LIMIT = 10
    /** This many drop-downs from one app inside [BURST_WINDOW_MS] is a flood: the app is quiet for [BURST_QUIET_MS]. */
    const val BURST_COUNT = 4
    const val BURST_WINDOW_MS = 60_000L
    const val BURST_QUIET_MS = 120_000L
    /** One conversation drops the island down at most once in this long (a chat sending ten messages is one drop-down). */
    const val CONVERSATION_GAP_MS = 15_000L

    /** A message from a person: Android's message category, a messaging-style notification, or one tied to a conversation shortcut. */
    fun isConversation(category: String?, messagingStyle: Boolean, shortcutId: String?): Boolean =
        category == android.app.Notification.CATEGORY_MESSAGE || messagingStyle || !shortcutId.isNullOrBlank()

    /** Whether an app's drop-downs flood: [recent] are the times of its latest drop-downs (newest last). */
    fun flooding(recent: List<Long>, now: Long): Boolean = recent.count { now - it in 0..BURST_WINDOW_MS } >= BURST_COUNT

    /** The unopened counts as stored ("pkg=3,pkg2=10"), and back. */
    fun parseCounts(raw: String?): Map<String, Int> = raw.orEmpty().split(',').mapNotNull { part ->
        val i = part.lastIndexOf('='); if (i <= 0) null else part.substring(0, i).trim().takeIf { it.isNotEmpty() }?.let { k ->
            part.substring(i + 1).trim().toIntOrNull()?.let { k to it } }
    }.toMap()
    fun serializeCounts(counts: Map<String, Int>): String = counts.entries.filter { it.value > 0 }.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }

    /** After a drop-down from [pkg] was shown. */
    fun shown(counts: Map<String, Int>, pkg: String): Map<String, Int> = counts + (pkg to ((counts[pkg] ?: 0) + 1).coerceAtMost(UNOPENED_LIMIT))
    /** After a notification from [pkg] was opened (from the island or the shade): it is wanted after all. */
    fun opened(counts: Map<String, Int>, pkg: String): Map<String, Int> = counts - pkg
    /** Whether [pkg] has been quieted by never being opened. */
    fun quieted(counts: Map<String, Int>, pkg: String): Boolean = (counts[pkg] ?: 0) >= UNOPENED_LIMIT
}

/** Where PeekSmarts' unopened counts live: the extras preferences, so the settings page lists quieted apps and can bring them back. */
internal object PeekLearning {
    private const val KEY = "peekUnopened"
    private fun prefs(context: android.content.Context) = context.getSharedPreferences("extras", android.content.Context.MODE_PRIVATE)
    private fun update(context: android.content.Context, change: (Map<String, Int>) -> Map<String, Int>) {
        val p = prefs(context)
        val next = change(PeekSmarts.parseCounts(p.getString(KEY, null)))
        p.edit().putString(KEY, PeekSmarts.serializeCounts(next)).apply()
    }
    fun shown(context: android.content.Context, pkg: String) = update(context) { PeekSmarts.shown(it, pkg) }
    fun opened(context: android.content.Context, pkg: String) = update(context) { PeekSmarts.opened(it, pkg) }
    /** The apps quieted for never being opened. */
    fun quieted(context: android.content.Context): Set<String> =
        PeekSmarts.parseCounts(prefs(context).getString(KEY, null)).filterValues { it >= PeekSmarts.UNOPENED_LIMIT }.keys
}
