package com.jake.duolauncher

/** Which pattern shows the time, following the phone's 12-hour or 24-hour setting (a system setting, not a style choice). */
internal object ClockFormat {
    /** 24-hour: "13:05". 12-hour: "1:05", or "1:05 PM" when [meridiem] is true. */
    fun timePattern(is24Hour: Boolean, meridiem: Boolean = false): String =
        if (is24Hour) "HH:mm" else if (meridiem) "h:mm a" else "h:mm"
}
