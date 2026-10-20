package com.jake.duolauncher.island

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The person's correction to where the status bar's icons are (see [StatusBarGrid], measured on one Pixel 10a). Set on the "Line up with the
 * status bar" page, where guides are drawn over the real status bar so the gaps can be matched by eye; kept in its own preferences file.
 */
internal object StatusBarCalibration {
    var current by mutableStateOf(GridCalibration()); private set
    /** True while the calibration page is open: the launcher draws the guides then. */
    var guidesShown by mutableStateOf(false)
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("statusGrid", Context.MODE_PRIVATE)
        prefs = p
        current = GridCalibration(p.getFloat("left", 0f), p.getFloat("pitch", 0f), p.getFloat("right", 0f)).clamped()
    }

    fun set(cal: GridCalibration) {
        val c = cal.clamped(); current = c
        prefs?.edit()?.putFloat("left", c.leftShift)?.putFloat("pitch", c.pitchDelta)?.putFloat("right", c.rightShift)?.apply()
    }
}
