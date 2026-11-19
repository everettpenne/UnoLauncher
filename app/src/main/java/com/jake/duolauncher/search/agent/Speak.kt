package com.jake.duolauncher.search.agent

/** How a tool says what it did, in words a person reads in a summary line. Pure. */
internal object Speak {
    /** "20 minutes", "1 hour 5 minutes", "45 seconds". */
    fun duration(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val rest = seconds % 60
        val parts = buildList {
            if (hours > 0) add("$hours hour${if (hours == 1) "" else "s"}")
            if (minutes > 0) add("$minutes minute${if (minutes == 1) "" else "s"}")
            if (rest > 0 && hours == 0) add("$rest second${if (rest == 1) "" else "s"}")
        }
        return parts.joinToString(" ").ifEmpty { "0 seconds" }
    }

    /** "06:45", from a 24-hour clock time. */
    fun clock(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

    /** "today at 15:00", "tomorrow at 09:30", "on Friday at 12:00", "on 3 December at 10:00": a moment, relative to [now]. */
    fun dayTime(at: java.time.LocalDateTime, now: java.time.LocalDateTime): String {
        val days = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), at.toLocalDate())
        val day = when {
            days == 0L -> "today"
            days == 1L -> "tomorrow"
            days in 2..7 -> "on " + at.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
            else -> "on ${at.dayOfMonth} " + at.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
        }
        return "$day at ${clock(at.hour, at.minute)}"
    }
}
