package com.jake.duolauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jake.duolauncher.appearance.*
import com.jake.duolauncher.widgets.*

/**
 * The bar over Home while a widget is being placed, laid out as an iOS navigation bar is: Cancel at the start, what is being placed (its size,
 * and arrows to pick the page) in the middle, and the one prominent action, Place, at the end; a small "‹ Widgets" pill under it goes back to the
 * gallery. It was one row of five buttons that did not fit a phone's width ("Cancel" wrapped onto two lines), so it now has three parts that
 * share the width and never wrap, on a phone, a folded cover screen or a tablet alike (it is held to a readable width and centred).
 */
@Composable
internal fun WidgetPlacementBar(
    sizeLabel: String,
    /** The middle shows page arrows (a placement you can still move), and says "Replace here" instead when [replacing]. */
    paging: Boolean,
    replacing: Boolean,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    /** Place is shown once the finger is up (not while dragging a widget in from the gallery). */
    showPlace: Boolean,
    canPlace: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlace: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = launcherAccent().first
    Column(modifier.statusBarsPadding().padding(top = 8.dp, start = 12.dp, end = 12.dp).widthIn(max = 520.dp).fillMaxWidth()
        .testTag("widget-placement-toolbar"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).background(Glass.copy(alpha = .97f), Corner.pill).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            // Cancel is plain accent text, as iOS's is, so Place is the only filled control.
            Box(Modifier.heightIn(min = 48.dp).clip(Corner.pill).clickable(onClick = onCancel).padding(horizontal = 14.dp)
                .semantics { role = Role.Button }.testTag("widget-placement-cancel"), contentAlignment = Alignment.Center) {
                Text(stringResource(com.jake.duolauncher.R.string.cancel), color = accent, fontWeight = FontWeight.Medium,
                    maxLines = 1, softWrap = false)
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (replacing) Text(stringResource(com.jake.duolauncher.R.string.replace_here), color = Ink, maxLines = 1,
                    modifier = Modifier.testTag("widget-replacement-locked"))
                else {
                    if (paging) IconButton(enabled = canGoPrevious, onClick = onPrevious, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.ChevronLeft, "Previous home page", tint = Ink.copy(alpha = if (canGoPrevious) 1f else .35f))
                    }
                    Text(sizeLabel, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
                        modifier = Modifier.padding(horizontal = if (paging) 2.dp else 8.dp))
                    if (paging) IconButton(enabled = canGoNext, onClick = onNext, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.ChevronRight, "Next home page", tint = Ink.copy(alpha = if (canGoNext) 1f else .35f))
                    }
                }
            }
            if (showPlace) GlassButton(enabled = canPlace, onClick = onPlace, prominent = true,
                modifier = Modifier.testTag("widget-placement-apply")) { Text(stringResource(com.jake.duolauncher.R.string.place), maxLines = 1, softWrap = false) }
            else Spacer(Modifier.width(8.dp))
        }
    }
}

/**
 * What a built-in widget looks like in the placement preview: its real face at the size it will have (the clock, the date, any Uno widget), or,
 * for a widget with no face to draw (a large folder, the widget panel), its name and size. A widget from another app draws its own preview.
 */
@Composable
internal fun PlacementBuiltinFace(builtinId: Int?, providerLabel: String?, span: WidgetSpan) {
    androidx.compose.runtime.CompositionLocalProvider(LocalWidgetPreview provides true, LocalWidgetStyle provides WidgetStyle.CARD) {
        val unoWidget = builtinId?.let { UnoWidgets.byId(it) }
        when {
            builtinId == CLOCK_WIDGET -> ClockCard {}
            builtinId == DATE_WIDGET -> DateCard {}
            unoWidget != null -> unoWidget.face(PreviewSamples.slotFor(unoWidget.id)) {}
            else -> Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(providerLabel ?: when (builtinId) { FOLDER_WIDGET, FOLDER_WIDE_PICK, FOLDER_TALL_PICK -> "Large folder"; else -> "Widget panel" },
                    color = Ink, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("${span.width} × ${span.height}", color = Ink.copy(alpha = .7f))
            }
        }
    }
}

/**
 * "‹ Widgets": back to the gallery from placing. It floats near the bottom of the screen, above the page strip, where it covers nothing; at the top,
 * under the bar, it sat on the widgets being placed against.
 */
@Composable
internal fun WidgetPlacementBack(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val accent = launcherAccent().first
    Row(modifier.navigationBarsPadding().padding(bottom = 116.dp).heightIn(min = 44.dp).clip(Corner.pill)
        .background(Glass.copy(alpha = .94f), Corner.pill).clickable(onClick = onBack)
        .padding(start = 8.dp, end = 16.dp).semantics { role = Role.Button }.testTag("widget-placement-back"),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.ChevronLeft, null, tint = accent, modifier = Modifier.size(22.dp))
        Text(stringResource(com.jake.duolauncher.R.string.back_to_widgets), color = accent, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
    }
}
