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
import androidx.compose.ui.unit.dp

/** Hosts the island's waveform in an ordinary activity. Compose's test rule cancels infinite animations (so tests can go idle), which
 * would hide whether the bars really move; a real activity has no such rule, so what a screenshot shows is what a person would see.
 * With "card", it shows the open island's music card and a pill with the now-playing flame, for checking them by eye on a phone. */
class EqualizerHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val animate = intent.getBooleanExtra("animate", true)
        val card = intent.getBooleanExtra("card", false)
        setContent {
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
