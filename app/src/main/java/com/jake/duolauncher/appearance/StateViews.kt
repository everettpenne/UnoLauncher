package com.jake.duolauncher.appearance

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jake.duolauncher.*

/*
 * The launcher's three quiet states, in one shape each, so "nothing here", "still loading" and "that failed" never look like three different apps:
 * [StateMessage] (a glass disc with a symbol, a title, a line and an optional button), and [SkeletonRows] (grey rows that shimmer where the content
 * will be). They match the suite's Glass kit (GlassEmptyState and friends) in proportions and wording.
 */

/** Nothing to show, or something failed: a symbol on a faint disc, a short title, a helpful line, and an optional action. */
@Composable
internal fun StateMessage(icon: ImageVector, title: String, message: String?, modifier: Modifier = Modifier, tag: String? = null, action: (@Composable () -> Unit)? = null) {
    val ink = LocalContentColor.current
    // A state arrives, it does not pop: it fades in while rising a few dp, so a list that empties (a search with no match) or a feed that fails
    // settles into its message rather than snapping to it.
    val arrive = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { arrive.animateTo(1f, androidx.compose.animation.core.spring(.9f, 300f)) }
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 28.dp).graphicsLayer { alpha = arrive.value.coerceIn(0f, 1f); translationY = (1f - arrive.value) * 12.dp.toPx() }.then(if (tag != null) Modifier.testTag(tag) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(72.dp).clip(androidx.compose.foundation.shape.CircleShape).background(ink.copy(alpha = .07f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = ink.copy(alpha = .62f), modifier = Modifier.size(30.dp))
        }
        Text(title, style = UnoType.headline, color = ink, textAlign = TextAlign.Center)
        if (message != null) Text(message, style = UnoType.footnote, color = ink.copy(alpha = .66f), textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(4.dp)); action() }
    }
}

/**
 * Rows the shape of the content that is coming, with a light that sweeps across them: still while the system's animations are off (a person who turned
 * them off gets grey rows, not a moving one).
 */
@Composable
internal fun SkeletonRows(count: Int = 5, modifier: Modifier = Modifier, rowHeight: androidx.compose.ui.unit.Dp = 56.dp) {
    val animate = androidx.compose.ui.platform.LocalContext.current.let { context ->
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    val sweep = if (animate) rememberInfiniteTransition(label = "skeleton").animateFloat(-1f, 2f,
        infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "skeleton sweep").value else .5f
    val base = LocalContentColor.current.copy(alpha = .08f)
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(count) { index ->
            Row(Modifier.fillMaxWidth().height(rowHeight).drawWithContent {
                drawContent()
                if (animate) drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = .22f), Color.Transparent),
                    start = Offset(size.width * (sweep - .3f), 0f), end = Offset(size.width * (sweep + .3f), size.height * .4f)))
            }, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(rowHeight * .78f).clip(Corner.icon).background(base))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(if (index % 2 == 0) .55f else .7f).height(12.dp).clip(Corner.small).background(base))
                    Box(Modifier.fillMaxWidth(if (index % 2 == 0) .35f else .42f).height(10.dp).clip(Corner.small).background(base.copy(alpha = .06f)))
                }
            }
        }
    }
}
