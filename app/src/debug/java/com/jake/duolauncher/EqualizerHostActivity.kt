package com.jake.duolauncher

import com.jake.duolauncher.island.*
import com.jake.duolauncher.appearance.*

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/** Hosts the island's waveform in an ordinary activity. Compose's test rule cancels infinite animations (so tests can go idle), which
 * would hide whether the bars really move; a real activity has no such rule, so what a screenshot shows is what a person would see.
 * With "card", it shows the open island's music card and a pill with the now-playing flame, for checking them by eye on a phone. */
class EqualizerHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val animate = intent.getBooleanExtra("animate", true)
        val card = intent.getBooleanExtra("card", false)
        val kind = intent.getStringExtra("kind")?.let { runCatching { LiveKind.valueOf(it) }.getOrNull() }
        if (kind == LiveKind.TIMER) IslandTools.startTimer(this, 150_000L)
        if (kind == LiveKind.STOPWATCH) IslandTools.toggleStopwatch(this)
        // UnoType and the file-level typography reference each other; the app always loads the file first, so a host must too.
        InterFamily.hashCode()
        val playful = intent.getBooleanExtra("playful", true)
        setContent {
            if (kind != null) {
                IslandThemes.apply(IslandThemes.active.copy(playful = playful))
                Column(Modifier.fillMaxSize().background(Color(0xFF3A4A58)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(40.dp))
                    val tint = if (kind == LiveKind.TIMER || kind == LiveKind.STOPWATCH) IslandThemes.active.timer else IslandThemes.active.highlight
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(34.dp)).background(Color.Black).padding(18.dp)) {
                        ActivityCard(PeekNotification(packageName, "x", "Mina Okafor", "Call", null, null, 6, tint.toArgb(), quickActions = true, kind = kind),
                            kind, IslandState())
                    }
                }
                return@setContent
            }
            if (!card) Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                EqualizerBars(Color(0xFFE2582B), Modifier.size(width = 40.dp, height = 14.dp), animate = animate)
            } else Column(Modifier.fillMaxSize().background(Color(0xFF3A4A58)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val tint = Color(0xFFE2582B)
                val t by rememberFlameClock(animate)
                val wash = remember { Brush.verticalGradient(listOf(Color.Transparent, tint.copy(alpha = .26f), tint.copy(alpha = .8f))) }
                val tongue = remember {
                    Brush.radialGradient(listOf(tint.copy(alpha = .85f), Color.Transparent), center = androidx.compose.ui.geometry.Offset.Zero, radius = 1f)
                }
                Spacer(Modifier.height(40.dp))
                Box(Modifier.size(width = 150.dp, height = 33.dp).clip(RoundedCornerShape(50)).background(Color.Black)
                    .drawBehind { drawBottomGlow(wash, tongue, 1f, t, animate) })
                Spacer(Modifier.height(24.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(34.dp)).background(Color.Black)
                    .drawBehind { drawBottomGlow(wash, tongue, 1f, t, animate) }.padding(18.dp)) {
                    PlaybackRow(playing = animate, tint = tint, title = "Big City Life", artist = "Luude, Mattafix",
                        onPrevious = {}, onPlayPause = {}, onNext = {}, progress = .42f, durationMs = 214_000L, onSeek = {})
                }
            }
        }
    }
}
