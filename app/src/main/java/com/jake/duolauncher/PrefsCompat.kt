package com.jake.duolauncher

import android.content.SharedPreferences

/**
 * A float setting that survives having been stored as another number type. Reading with getFloat throws ClassCastException
 * for an Int, and an old settings restore could leave one behind; a crash here is at startup, so it must never throw.
 */
internal fun SharedPreferences.lenientFloat(key: String, default: Float): Float =
    (all[key] as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: default
