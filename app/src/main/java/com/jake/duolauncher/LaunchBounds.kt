package com.jake.duolauncher

import com.jake.duolauncher.island.*

import androidx.compose.ui.geometry.Rect
import kotlin.math.roundToInt

internal fun Rect.toAndroidBounds() = android.graphics.Rect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())
