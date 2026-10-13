package com.jake.duolauncher

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Restoring a backup with a whole-number float (written by Android's org.json as 1) must leave a value getFloat can read. */
@RunWith(AndroidJUnit4::class)
class SettingsRestoreFloatInstrumentedTest {
    @Test fun wholeNumberFloatIsRestoredAsAFloatEvenOnAFreshInstall() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val extras = context.getSharedPreferences(SettingsBackup.EXTRAS, Context.MODE_PRIVATE)
        val appearance = context.getSharedPreferences(SettingsBackup.APPEARANCE, Context.MODE_PRIVATE)
        val before = extras.all to appearance.all
        try {
            extras.edit().remove("kbHapticStrength").commit(); appearance.edit().remove("islandScale").commit()
            val json = SettingsBackup.encode(SettingsSnapshot(mapOf("kbHapticStrength" to 1f), mapOf("islandScale" to 1f), emptyMap(), emptyMap(), emptyMap()))
            SettingsBackup.apply(context, SettingsBackup.decode(org.json.JSONObject(json.toString())))
            assertEquals(1f, extras.getFloat("kbHapticStrength", 0f), 0f)
            assertEquals(1f, appearance.getFloat("islandScale", 0f), 0f)
            // A value an older build left behind as an Int is still read.
            extras.edit().remove("kbHapticStrength").putInt("kbHapticStrength", 1).commit()
            assertEquals(1f, extras.lenientFloat("kbHapticStrength", .5f), 0f)
        } finally {
            extras.edit().apply { remove("kbHapticStrength"); (before.first["kbHapticStrength"] as? Float)?.let { putFloat("kbHapticStrength", it) } }.commit()
            appearance.edit().apply { remove("islandScale"); (before.second["islandScale"] as? Float)?.let { putFloat("islandScale", it) } }.commit()
        }
    }
}
