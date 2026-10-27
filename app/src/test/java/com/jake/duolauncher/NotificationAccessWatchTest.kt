package com.jake.duolauncher

import android.content.SharedPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationAccessWatchTest {
    private class Prefs : SharedPreferences {
        val map = mutableMapOf<String, Any?>()
        override fun getBoolean(key: String, defValue: Boolean) = map[key] as? Boolean ?: defValue
        override fun edit() = object : SharedPreferences.Editor {
            val pending = mutableMapOf<String, Any?>()
            override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
            override fun apply() { map.putAll(pending) }
            override fun commit(): Boolean { apply(); return true }
            override fun putString(k: String, v: String?) = this; override fun putStringSet(k: String, v: MutableSet<String>?) = this
            override fun putInt(k: String, v: Int) = this; override fun putLong(k: String, v: Long) = this; override fun putFloat(k: String, v: Float) = this
            override fun remove(k: String) = this; override fun clear() = this
        }
        override fun getAll() = map.toMutableMap(); override fun getString(k: String, d: String?) = d
        override fun getStringSet(k: String, d: MutableSet<String>?) = d; override fun getInt(k: String, d: Int) = d
        override fun getLong(k: String, d: Long) = d; override fun getFloat(k: String, d: Float) = d; override fun contains(k: String) = map.containsKey(k)
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    @Test fun nobodyWhoNeverTurnedItOnIsAskedAnything() {
        assertFalse(NotificationAccessWatch.lost(Prefs(), on = false))
    }

    @Test fun accessGoingFromOnToOffIsNoticedUntilAcknowledged() {
        val p = Prefs()
        assertFalse(NotificationAccessWatch.lost(p, on = true))
        assertTrue(NotificationAccessWatch.lost(p, on = false))
        assertTrue(NotificationAccessWatch.lost(p, on = false))
        NotificationAccessWatch.acknowledge(p)
        assertFalse(NotificationAccessWatch.lost(p, on = false))
    }

    @Test fun lostAgainAfterBeingOnAgain() {
        val p = Prefs()
        NotificationAccessWatch.lost(p, on = true); NotificationAccessWatch.lost(p, on = false); NotificationAccessWatch.acknowledge(p)
        assertFalse(NotificationAccessWatch.lost(p, on = true))
        assertTrue(NotificationAccessWatch.lost(p, on = false))
    }
}
