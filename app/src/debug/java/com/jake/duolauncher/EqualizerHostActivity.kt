package com.jake.duolauncher

import com.jake.duolauncher.island.*
import com.jake.duolauncher.appearance.*

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Hosts the island's waveform in an ordinary activity. Compose's test rule cancels infinite animations (so tests can go idle), which
 * would hide whether the bars really move; a real activity has no such rule, so what a screenshot shows is what a person would see. */
class EqualizerHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val animate = intent.getBooleanExtra("animate", true)
        setContent {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                EqualizerBars(Color(0xFFE2582B), Modifier.size(width = 40.dp, height = 14.dp), animate = animate)
            }
        }
    }
}
