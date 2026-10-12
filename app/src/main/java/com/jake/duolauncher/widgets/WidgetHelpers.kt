package com.jake.duolauncher.widgets

import com.jake.duolauncher.island.*
import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/** Drawing helpers shared by the Uno widgets. A widget file may use these; it must not depend on another widget's file. */

@Composable
internal fun rememberNow(periodMs: Long): LocalDateTime {
    val now by produceState(LocalDateTime.now(), periodMs) { while (true) { value = LocalDateTime.now(); delay(periodMs) } }
    return now
}

internal fun onInk(ink: GlassInk) = if (ink.primary.luminance() > .5f) Color.Black else Color.White

@Composable
internal fun CardTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val ink = LocalGlassInk.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(icon, null, tint = ink.soft(), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, color = ink.soft(), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
