package com.jake.duolauncher.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jake.duolauncher.GlassCard
import com.jake.duolauncher.HomeHabits
import com.jake.duolauncher.LocalHomeApps
import com.jake.duolauncher.appearance.LocalGlassInk
import com.jake.duolauncher.appearance.Corner
import com.jake.duolauncher.appearance.UnoType
import com.jake.duolauncher.appearance.glassIcon
import com.jake.duolauncher.search.SpotlightHistory

/** How many apps the Suggested apps widget shows in a given space: one for each 70 dp across (at most four) and each 62 dp down (at
 *  most two), which is an icon and its name. A 4 × 2 widget shows eight, a 2 × 2 four. Pure, so it is unit-tested. */
internal object SuggestionGrid {
    fun columns(widthDp: Float) = (widthDp / 70f).toInt().coerceIn(2, 4)
    fun rows(heightDp: Float) = (heightDp / 62f).toInt().coerceIn(1, 2)
}

/**
 * Suggested apps: the apps you usually open about now, then the ones you open most. A tap opens one. It learns the hours only while
 * **Learn from how you use Home** is on (Home Screen settings); until then it shows the apps you open most and most lately, which
 * the launcher has always kept for search, and says how to turn the rest on. Hidden apps and apps Focus is hiding are never shown.
 */
@Composable
internal fun SuggestionsFace(slot: Int, onOptions: () -> Unit) {
    val context = LocalContext.current
    val home = LocalHomeApps.current
    val ink = LocalGlassInk.current
    remember { HomeHabits.init(context); true }
    // Worked out again when something is learned, when the list of apps changes, and each time Home comes back to the front.
    var resumed by remember { mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { resumed++ }
    GlassCard(onClick = {}, modifier = Modifier.testTag("uno-suggestions-$slot"), padding = 10.dp) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = SuggestionGrid.columns(maxWidth.value)
            val hint = home != null && !HomeHabits.learning
            val rows = SuggestionGrid.rows(maxHeight.value - if (hint) 16f else 0f)
            val count = columns * rows
            val apps = home?.apps.orEmpty()
            val picked = remember(apps, HomeHabits.revision, HomeHabits.learning, resumed, count) {
                val byId = apps.filter { it.available }.associateBy { it.id }
                val frecent = runCatching { SpotlightHistory(context).scores() }.getOrDefault(emptyMap()).entries.sortedByDescending { it.value }.map { it.key }
                // With nothing opened yet, the first apps in the list, so the widget is never empty.
                HomeHabits.suggestions(byId.keys, frecent + byId.keys, count).mapNotNull(byId::get)
            }
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                for (r in 0 until rows) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for (c in 0 until columns) {
                        val app = picked.getOrNull(r * columns + c)
                        Column(Modifier.weight(1f).then(if (app != null && home != null && !LocalWidgetPreview.current)
                            Modifier.clip(Corner.small).clickable(role = Role.Button, onClickLabel = "Open ${app.label}") { home.launch(app) } else Modifier)
                            .padding(vertical = 1.dp).testTag("uno-suggestion"), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (app != null) Image(app.image, null, Modifier.size(44.dp).glassIcon().clip(Corner.icon))
                            // The picker's preview, and a phone with fewer apps than places: a quiet tile where an app would be.
                            else Box(Modifier.size(44.dp).background(ink.soft(.12f), Corner.icon))
                            Text(app?.label.orEmpty(), Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp), color = ink.primary, style = UnoType.caption2,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                }
                if (hint) Text(androidx.compose.ui.res.stringResource(com.jake.duolauncher.R.string.suggestions_turn_on), Modifier.fillMaxWidth().clip(Corner.small)
                    .clickable(role = Role.Button) { home?.openSettings?.invoke() }.padding(horizontal = 4.dp).testTag("uno-suggestions-hint"),
                    color = ink.soft(.6f), style = UnoType.caption2, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                else Spacer(Modifier.size(0.dp))
            }
        }
    }
}
