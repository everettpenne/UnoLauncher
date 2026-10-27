package com.jake.duolauncher

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.island.*
import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.widgets.*

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.widget.RemoteViews
import android.appwidget.AppWidgetManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap

internal data class WidgetCatalogEntry(
    val provider: AppWidgetProviderInfo,
    val providerLabel: String,
    val appLabel: String,
    val description: String,
    val userSerial: Long = 0,
    val profileLabel: String = "Personal",
    val isWork: Boolean = false,
)

internal data class WidgetPickerSession(
    val provider: AppWidgetProviderInfo?,
    val slot: Int,
    val span: WidgetSpan,
    val pointer: Offset,
    val dragging: Boolean,
    val targetIndex: Int? = null,
    val candidate: WidgetPlacement? = null,
    val builtinId: Int? = null,
)

internal fun widgetCatalog(context: Context, providers: List<AppWidgetProviderInfo>, profile: AppProfile): List<WidgetCatalogEntry> {
    val pm = context.packageManager
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    return providers.map { provider ->
        val packageName = provider.provider.packageName
        val appLabel = runCatching {
            launcherApps.getApplicationInfo(packageName, 0, provider.profile).loadLabel(pm).toString()
        }.recoverCatching {
            if (provider.profile != Process.myUserHandle()) throw it
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }
            .getOrDefault(packageName)
        val providerLabel = provider.loadLabel(pm).toString()
        val description = runCatching { provider.loadDescription(context)?.toString().orEmpty() }.getOrDefault("")
        WidgetCatalogEntry(provider, providerLabel, appLabel, description, profile.userSerial, profile.label, profile.isWork)
    }.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.providerLabel.lowercase() }))
}

private sealed interface CatalogPreview {
    data class Remote(val views: RemoteViews) : CatalogPreview
    data class Picture(val bitmap: Bitmap) : CatalogPreview
    data object Missing : CatalogPreview
}

private object WidgetPreviewCache {
    private const val MAX_BYTES = 16 * 1024 * 1024
    private val values = object : LinkedHashMap<String, CatalogPreview>(16, .75f, true) {}
    private var bytes = 0
    @Synchronized fun get(key: String) = values[key]
    @Synchronized fun put(key: String, value: CatalogPreview) {
        values.remove(key)?.let { bytes -= it.cost }
        values[key] = value; bytes += value.cost
        val iterator = values.entries.iterator()
        while (bytes > MAX_BYTES && iterator.hasNext()) { bytes -= iterator.next().value.cost; iterator.remove() }
    }
    private val CatalogPreview.cost get() = when (this) {
        is CatalogPreview.Picture -> bitmap.allocationByteCount
        is CatalogPreview.Remote -> 64 * 1024
        CatalogPreview.Missing -> 1
    }
}

private suspend fun loadWidgetPreview(context: Context, provider: AppWidgetProviderInfo, span: WidgetSpan): CatalogPreview {
    val density = context.resources.displayMetrics.densityDpi
    val key = "${provider.provider.flattenToString()}|${provider.profile.hashCode()}|$density|${span.width}x${span.height}"
    WidgetPreviewCache.get(key)?.let { return it }
    val loaded = withContext(Dispatchers.IO) {
        val manager = AppWidgetManager.getInstance(context)
        var remote: RemoteViews? = null
        if (android.os.Build.VERSION.SDK_INT >= 35 &&
            provider.generatedPreviewCategories and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0) {
            remote = runCatching { manager.getWidgetPreview(provider.provider, provider.profile,
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) }.getOrNull()
        }
        if (remote == null && provider.previewLayout != 0)
            remote = runCatching { RemoteViews(provider.provider.packageName, provider.previewLayout) }.getOrNull()
        remote?.let(CatalogPreview::Remote) ?: run {
            val drawable = runCatching { provider.loadPreviewImage(context, density) }.getOrNull()
                ?: runCatching { provider.loadIcon(context, density) }.getOrNull()
            drawable
        }
    }
    // Drawable.draw may touch theme/view state. Convert it on Compose's UI thread; only resource
    // discovery and generated-preview IPC belong on the worker dispatcher.
    val result = when (loaded) {
        is CatalogPreview -> loaded
        is Drawable -> CatalogPreview.Picture(loaded.catalogBitmap())
        else -> CatalogPreview.Missing
    }
    WidgetPreviewCache.put(key, result)
    return result
}

private fun Drawable.catalogBitmap(): Bitmap {
    val sourceWidth = intrinsicWidth.takeIf { it > 0 } ?: 144
    val sourceHeight = intrinsicHeight.takeIf { it > 0 } ?: 144
    val scale = minOf(1f, 720f / sourceWidth, 480f / sourceHeight)
    val w = (sourceWidth * scale).toInt().coerceAtLeast(1)
    val h = (sourceHeight * scale).toInt().coerceAtLeast(1)
    return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = android.graphics.Canvas(bitmap)
        setBounds(0, 0, w, h)
        draw(canvas)
    }
}

@Composable
internal fun WidgetProviderPreview(entry: WidgetCatalogEntry, span: WidgetSpan, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preview by produceState<CatalogPreview?>(null, entry.provider, span, androidx.compose.ui.platform.LocalResources.current.displayMetrics.densityDpi) {
        value = loadWidgetPreview(context, entry.provider, span)
    }
    Box(modifier.background(Glass.copy(alpha = .38f)), contentAlignment = Alignment.Center) {
        when (val value = preview) {
            is CatalogPreview.Remote -> AndroidView(factory = { previewContext ->
                object : android.widget.FrameLayout(previewContext) {
                    override fun dispatchTouchEvent(event: android.view.MotionEvent?): Boolean = false
                }.apply {
                    importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    isFocusable = false
                    isClickable = false
                    runCatching { addView(value.views.apply(previewContext, this)) }
                        .onFailure { addView(android.widget.TextView(previewContext).apply { text = entry.providerLabel }) }
                }
            }, modifier = Modifier.fillMaxSize().padding(6.dp))
            is CatalogPreview.Picture -> Image(value.bitmap.asImageBitmap(), null, Modifier.fillMaxSize().padding(8.dp),
                contentScale = ContentScale.Fit)
            CatalogPreview.Missing -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(entry.providerLabel, style = MaterialTheme.typography.bodySmall)
                Text("${span.width} × ${span.height}", style = MaterialTheme.typography.labelSmall)
            }
            null -> CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp)
        }
    }
}

@Composable
internal fun VisualWidgetPicker(
    entries: List<WidgetCatalogEntry>?,
    profiles: List<AppProfile>,
    selectedProfile: AppProfile,
    onSelectProfile: (AppProfile) -> Unit,
    onTurnOnWork: (Long) -> Unit,
    hiddenForDrag: Boolean,
    footprint: (AppWidgetProviderInfo) -> WidgetSpan?,
    onBack: () -> Unit,
    onTap: (AppWidgetProviderInfo) -> Unit,
    onBuiltin: (Int) -> Unit,
    onDragStart: (AppWidgetProviderInfo, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDrop: () -> Unit,
    onCancelDrag: () -> Unit,
    glass: PageGlass? = null,
    /** What the preview tiles are drawn with: the wallpaper-only glass the Home widgets use, so a preview shows the wallpaper through it and not the icons behind the picker. */
    previewGlass: PageGlass? = glass,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // The app whose widgets are open (the drill-down); with a search typed, the flat list stays.
    var detailApp by rememberSaveable { mutableStateOf<String?>(null) }
    val latestDragStart by rememberUpdatedState(onDragStart)
    val latestDrag by rememberUpdatedState(onDrag)
    val latestDrop by rememberUpdatedState(onDrop)
    val latestCancelDrag by rememberUpdatedState(onCancelDrag)
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val words = query.trim().lowercase()
    val filtered = remember(entries, words) { entries.orEmpty().filter { entry ->
        words.isEmpty() || listOf(entry.appLabel, entry.providerLabel, entry.description,
            entry.provider.provider.packageName).any { it.lowercase().contains(words) }
    } }
    // With liquid glass the gallery floats as a frosted card over Home; heavy blur keeps the
    // previews readable. Without it, the original near-opaque full-screen panel.
    // Held to a readable width and centred, so on a tablet or an unfolded screen the tiles keep the size they have on a phone instead of
    // stretching to half a metre each.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Surface(Modifier.fillMaxHeight().widthIn(max = 640.dp).fillMaxWidth().alpha(if (hiddenForDrag) 0f else 1f)
        .then(if (hiddenForDrag) Modifier.clearAndSetSemantics { }.focusProperties { canFocus = false } else Modifier)
        .then(if (glass != null) Modifier.statusBarsPadding().navigationBarsPadding().padding(8.dp)
            .liquidGlass(glass.backdrop, Corner.xlarge, glass.tint, kind = GlassKind.REGULAR, settings = glass.settings) else Modifier)
        .testTag("visual-widget-picker"),
        color = if (glass != null) Color.Transparent else Glass.copy(alpha = .96f)) {
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()
            // The gallery behind an open app is hidden and left out of what a screen reader and the keyboard reach.
            .then(if (detailApp != null) Modifier.alpha(0f).clearAndSetSemantics { }.focusProperties { canFocus = false } else Modifier)
            .then(if (glass != null) Modifier.padding(top = 8.dp)
            else Modifier.statusBarsPadding().navigationBarsPadding()).padding(horizontal = 16.dp)) {
            // As iOS's gallery: a large title at the start, a round close button at the end, then a search field and one line saying how to add.
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(com.jake.duolauncher.R.string.widgets), Modifier.weight(1f).padding(start = 4.dp),
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Box(Modifier.size(36.dp).clip(CircleShape).background(Ink.copy(alpha = .10f)).clickable(onClick = onBack)
                    .semantics { contentDescription = "Close widgets" }.testTag("widget-picker-close"), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, null, tint = Ink.copy(alpha = .7f), modifier = Modifier.size(20.dp))
                }
            }
            GlassField(query, { query = it }, Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)
                .testTag("widget-catalog-search"), singleLine = true, placeholder = { Text(stringResource(com.jake.duolauncher.R.string.search_widgets)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) })
            Text(stringResource(com.jake.duolauncher.R.string.widget_picker_hint), Modifier.padding(start = 4.dp, bottom = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (profiles.any { it.isWork }) Row(Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                profiles.forEach { profile ->
                    GlassChip(selected = profile.userSerial == selectedProfile.userSerial,
                        onClick = { onSelectProfile(profile) }, label = { Text(profile.label) })
                }
            }
            if (!selectedProfile.available || !selectedProfile.unlocked || selectedProfile.quiet) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (selectedProfile.quiet) "${selectedProfile.label} apps are paused"
                        else "${selectedProfile.label} profile is unavailable")
                    if (selectedProfile.isWork) GlassButton(prominent = true, onClick = { onTurnOnWork(selectedProfile.userSerial) },
                        modifier = Modifier.padding(top = 12.dp)) { Text(stringResource(com.jake.duolauncher.R.string.turn_on)) }
                }
            }
            LazyColumn(Modifier.fillMaxSize().testTag("widget-catalog-list"), verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)) {
                if (entries == null) item("catalog-loading") {
                    Box(Modifier.fillParentMaxSize().padding(40.dp), contentAlignment = Alignment.TopCenter) {
                        CircularProgressIndicator()
                    }
                }
                if (words.isEmpty() && selectedProfile.isPersonal) {
                    item("duo-widgets") { PickerSection(stringResource(com.jake.duolauncher.R.string.uno_launcher)) }
                    // Clock and Date are drawn as they will look on Home; the rest are plain cards.
                    item("builtin-previews") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PreviewTile("Clock", "2 × 2", 1f, "widget-builtin-$CLOCK_WIDGET", previewGlass, { onBuiltin(CLOCK_WIDGET) },
                                Modifier.weight(1f)) { ClockCard {} }
                            PreviewTile("Date", "2 × 2", 1f, "widget-builtin-$DATE_WIDGET", previewGlass, { onBuiltin(DATE_WIDGET) },
                                Modifier.weight(1f)) { DateCard {} }
                        }
                    }
                    // The widgets with no face to draw, as one grouped list, as iOS's Settings rows are.
                    item("builtin-rows") {
                        Surface(Modifier.fillMaxWidth(), color = Color.White.copy(alpha = .55f), shape = Corner.large) {
                            Column {
                                listOf(Triple(INFO_WIDGET, "Widget panel", "2 × 2"), Triple(FOLDER_WIDGET, "Large folder", "2 × 2"),
                                    Triple(FOLDER_WIDE_PICK, "Large folder, wide", "2 × 1"), Triple(FOLDER_TALL_PICK, "Large folder, tall", "1 × 2"))
                                    .forEachIndexed { index, (id, label, size) ->
                                        if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp), color = Ink.copy(alpha = .10f))
                                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("widget-builtin-$id")
                                            .clickable { focusManager.clearFocus(); keyboard?.hide(); onBuiltin(id) }
                                            .semantics { contentDescription = "Place $label" }.padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(label, style = MaterialTheme.typography.titleSmall)
                                                Text(size, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            PlusBadge()
                                        }
                                    }
                            }
                        }
                    }
                }
                val unoMatches = UnoWidgets.all.filter { words.isEmpty() || "${it.label} ${it.blurb}".lowercase().contains(words) }
                if (selectedProfile.isPersonal && unoMatches.isNotEmpty()) {
                    item("uno-library") { PickerSection(stringResource(com.jake.duolauncher.R.string.uno_widgets)) }
                    // Each Uno widget is its own live face, drawn at the size it will have on Home, so what you tap is what you get.
                    items(unoMatches.chunked(2), key = { row -> "uno-row-${row.first().id}" }) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { def ->
                                PreviewTile(def.label, def.blurb, def.spanX.toFloat() / def.spanY, "widget-builtin-${def.id}", previewGlass,
                                    { focusManager.clearFocus(); keyboard?.hide(); onBuiltin(def.id) }, Modifier.weight(1f),
                                    sizeLine = "${def.spanX} × ${def.spanY}") { def.face(PreviewSamples.slotFor(def.id)) {} }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                if (words.isEmpty() && selectedProfile.isPersonal && filtered.isNotEmpty()) {
                    item("apps-title") { PickerSection(stringResource(com.jake.duolauncher.R.string.widget_apps)) }
                    item("apps-list") {
                        Surface(Modifier.fillMaxWidth(), color = Color.White.copy(alpha = .55f), shape = Corner.large) {
                            Column {
                                filtered.groupBy { it.appLabel }.entries.forEachIndexed { index, (app, group) ->
                                    if (index > 0) HorizontalDivider(Modifier.padding(start = 64.dp), color = Ink.copy(alpha = .10f))
                                    WidgetAppRow(app, group, onClick = { focusManager.clearFocus(); keyboard?.hide(); detailApp = app })
                                }
                            }
                        }
                    }
                }
                (if (words.isEmpty() && selectedProfile.isPersonal) emptyMap() else filtered.groupBy { it.appLabel }).forEach { (app, group) ->
                    item("header-$app") { PickerSection(app) }
                    items(group, key = { it.provider.provider.flattenToString() }) { entry ->
                        val span = footprint(entry.provider)
                        var origin by remember { mutableStateOf(Offset.Zero) }
                        // No card round it: its name, size and what it does, then its real face with the add badge, as every other tile in the gallery.
                        Column(modifier = Modifier.fillMaxWidth()
                            .testTag("widget-provider-${entry.provider.provider.flattenToString()}${if (entry.isWork) "-profile-${entry.userSerial}" else ""}")
                            .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
                            .pointerInput(entry.provider) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { point ->
                                        focusManager.clearFocus(); keyboard?.hide()
                                        latestDragStart(entry.provider, origin + point)
                                    },
                                    onDrag = { change, _ -> change.consume(); latestDrag(origin + change.position) },
                                    onDragEnd = { latestDrop() }, onDragCancel = { latestCancelDrag() })
                            }.clickable(enabled = span != null, onClick = {
                                focusManager.clearFocus(); keyboard?.hide(); onTap(entry.provider)
                            })) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(entry.providerLabel, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 2.dp))
                                if (entry.isWork) AssistChip(onClick = {}, label = { Text(entry.profileLabel) }, modifier = Modifier.padding(start = 8.dp))
                            }
                            Text(span?.let { "${it.width} × ${it.height}" } ?: "Doesn’t fit this layout", Modifier.padding(start = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (span == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            if (entry.description.isNotBlank()) Text(entry.description, Modifier.padding(start = 2.dp, top = 2.dp),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                                overflow = TextOverflow.Ellipsis)
                            val ratio = span?.let { it.width.toFloat() / it.height } ?: 1.5f
                            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                                val boundedWidth = minOf(maxWidth, 220.dp * ratio)
                                val boundedHeight = boundedWidth / ratio
                                val previewTag = "widget-preview-${entry.provider.provider.flattenToString()}${if (entry.isWork) "-profile-${entry.userSerial}" else ""}"
                                Box(Modifier.width(boundedWidth).height(boundedHeight)) {
                                    Box(Modifier.fillMaxSize().clip(Corner.large).testTag(previewTag)) {
                                        WidgetProviderPreview(entry, span ?: WidgetSpan(2, 2), Modifier.fillMaxSize())
                                    }
                                    if (span != null) Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) { PlusBadge() }
                                }
                            }
                        }
                    }
                }
                if (entries != null && filtered.isEmpty()) item { Text(stringResource(com.jake.duolauncher.R.string.no_widgets_found), Modifier.padding(16.dp)) }
            }
        }
        // An app opened from the gallery: its widgets one page at a time, as iOS shows them, with the way to add the one on screen.
        val openGroup = detailApp?.let { app -> filtered.filter { it.appLabel == app } }?.takeIf { it.isNotEmpty() }
        if (openGroup != null) WidgetAppDetail(openGroup, footprint, onBack = { detailApp = null },
            onAdd = { entry -> focusManager.clearFocus(); keyboard?.hide(); onTap(entry.provider) },
            onDragStart = { provider, point -> focusManager.clearFocus(); keyboard?.hide(); latestDragStart(provider, point) },
            onDrag = { point -> latestDrag(point) }, onDrop = { latestDrop() }, onCancelDrag = { latestCancelDrag() },
            modifier = Modifier.fillMaxSize().background(if (glass != null) Color.Transparent else Glass.copy(alpha = .98f)).testTag("widget-app-detail"))
        }
    }
    }
}

/** One widget in the picker: its real face as a preview at Home size, its name, and what it does. The preview is not interactive: a
 *  tap on it places the widget. */
@Composable
private fun PreviewTile(label: String, detail: String, ratio: Float, tag: String, glass: PageGlass?, onPick: () -> Unit, modifier: Modifier,
    sizeLine: String? = null, preview: @Composable () -> Unit) {
    Column(modifier) {
        Box(Modifier.fillMaxWidth().aspectRatio(ratio)) {
            CompositionLocalProvider(LocalWidgetPreview provides true, LocalPageGlass provides glass,
                LocalWidgetStyle provides (glass?.widgetStyle ?: WidgetStyle.CARD)) {
                Box(Modifier.fillMaxSize()) { preview() }
            }
            // The tap catcher sits above the face, so the face itself never sees the touch.
            Box(Modifier.matchParentSize().clip(Corner.large).clickable(onClick = onPick)
                .semantics { contentDescription = "Place $label" }.testTag(tag))
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) { PlusBadge() }
        }
        Text(label, Modifier.padding(top = 8.dp, start = 2.dp), style = MaterialTheme.typography.titleSmall)
        Text(sizeLine ?: detail, Modifier.padding(start = 2.dp), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (sizeLine != null) Text(detail, Modifier.padding(start = 2.dp, top = 2.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/** A section's title in the gallery: large and semibold, as iOS's section headings are. */
@Composable
private fun PickerSection(text: String) {
    Text(text, Modifier.padding(top = 16.dp, start = 4.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}

/** The small round plus on a widget in the gallery: the one affordance that says a tap adds it. */
@Composable
private fun PlusBadge() {
    val (accent, onAccent) = launcherAccent()
    Box(Modifier.size(26.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Add, null, tint = onAccent, modifier = Modifier.size(17.dp))
    }
}

/** One app in the gallery's list: its icon, its name, and how many widgets it has. A tap opens them. */
@Composable
private fun WidgetAppRow(app: String, group: List<WidgetCatalogEntry>, onClick: () -> Unit) {
    val context = LocalContext.current
    val icon = remember(group.first().provider) { runCatching { group.first().provider.loadIcon(context, 0)?.catalogBitmap()?.asImageBitmap() }.getOrNull() }
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
        .semantics { contentDescription = "$app, ${group.size} widgets" }.testTag("widget-app-$app"), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(Corner.icon).background(Ink.copy(alpha = .08f))) {
            if (icon != null) Image(icon, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(if (group.size == 1) com.jake.duolauncher.R.string.widget_count_one else com.jake.duolauncher.R.string.widget_count_many, group.size),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Ink.copy(alpha = .4f))
    }
}

/** An app's widgets, one per page of a pager that swipes sideways, each with its name, size and what it does, then a dot for each page and the
 *  Add Widget button, as iOS's gallery shows one app. */
@Composable
private fun WidgetAppDetail(group: List<WidgetCatalogEntry>, footprint: (AppWidgetProviderInfo) -> WidgetSpan?, onBack: () -> Unit,
    onAdd: (WidgetCatalogEntry) -> Unit, onDragStart: (AppWidgetProviderInfo, Offset) -> Unit, onDrag: (Offset) -> Unit, onDrop: () -> Unit,
    onCancelDrag: () -> Unit, modifier: Modifier = Modifier) {
    val pager = androidx.compose.foundation.pager.rememberPagerState { group.size }
    val current = group[pager.currentPage.coerceIn(0, group.size - 1)]
    val currentSpan = footprint(current.provider)
    Column(modifier.padding(horizontal = 16.dp).statusBarsPadding().navigationBarsPadding().padding(top = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(Ink.copy(alpha = .10f)).clickable(onClick = onBack)
                .semantics { contentDescription = "Back to the widget gallery" }.testTag("widget-detail-back"), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ChevronLeft, null, tint = Ink.copy(alpha = .7f), modifier = Modifier.size(24.dp))
            }
            Text(group.first().appLabel, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), pageSpacing = 16.dp) { page ->
            val entry = group[page]
            val span = footprint(entry.provider)
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(entry.providerLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(span?.let { "${it.width} × ${it.height}" } ?: "Doesn’t fit this layout", style = MaterialTheme.typography.labelMedium,
                    color = if (span == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                if (entry.description.isNotBlank()) Text(entry.description, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                val ratio = span?.let { it.width.toFloat() / it.height } ?: 1.5f
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(top = 14.dp), contentAlignment = Alignment.TopCenter) {
                    val w = minOf(maxWidth, 300.dp * ratio, maxHeight * ratio)
                    // Held and dragged, the preview goes onto Home as in the flat list: the gallery hides, and the drop is made where the finger lifts.
                    var origin by remember { mutableStateOf(Offset.Zero) }
                    Box(Modifier.width(w).height(w / ratio).clip(Corner.large).testTag("widget-detail-preview-$page")
                        .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
                        .pointerInput(entry.provider) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { point -> onDragStart(entry.provider, origin + point) },
                                onDrag = { change, _ -> change.consume(); onDrag(origin + change.position) },
                                onDragEnd = { onDrop() }, onDragCancel = { onCancelDrag() })
                        }) {
                        WidgetProviderPreview(entry, span ?: WidgetSpan(2, 2), Modifier.fillMaxSize())
                    }
                }
            }
        }
        if (group.size > 1) Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) {
            repeat(group.size) { i ->
                Box(Modifier.padding(horizontal = 3.dp).size(7.dp).clip(CircleShape).background(Ink.copy(alpha = if (i == pager.currentPage) .75f else .22f)))
            }
        }
        GlassButton(prominent = true, onClick = { onAdd(current) }, enabled = currentSpan != null,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("widget-detail-add")) { Text(stringResource(com.jake.duolauncher.R.string.add_widget)) }
    }
}
