package com.jake.duolauncher

import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.island.*
import com.jake.duolauncher.feed.*
import com.jake.duolauncher.search.*
import com.jake.duolauncher.panel.*
import com.jake.duolauncher.appearance.*
import com.jake.duolauncher.folders.*
import com.jake.duolauncher.widgets.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.composed
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.animation.togetherWith
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

@Composable
internal fun WidgetSlot(id: Int, slot: Int, controller: WidgetController, modifier: Modifier, onAdd: () -> Unit,
    /** Draws a built-in widget: its id and the slot that owns its saved data (a stacked widget keeps the slot it had). */
    fallback: @Composable (faceId: Int, faceSlot: Int) -> Unit) {
    var restoreMessage by remember(slot) { mutableStateOf<String?>(null) }
    val glass = LocalPageGlass.current
    // A stack shows one widget at a time: the base widget, then each widget merged into it.
    val members = WidgetStacks.stacks[slot].orEmpty()
    var stackPage by rememberSaveable(slot) { mutableIntStateOf(0) }
    val shown = StackRules.clampPage(stackPage, members.size)
    val stackHaptic = LocalHapticFeedback.current
    val rootView = LocalView.current.rootView
    // What a screen reader says about a stack, and the actions it offers in place of the swipe and the rail's dots.
    val stackPosition = stringResource(com.jake.duolauncher.R.string.stack_position, shown + 1, members.size + 1,
        stackMemberLabel(if (shown == 0) id else members.getOrElse(shown - 1) { id }, controller))
    val nextInStack = stringResource(com.jake.duolauncher.R.string.stack_next_widget)
    val previousInStack = stringResource(com.jake.duolauncher.R.string.stack_previous_widget)
    var windowTopLeft by remember { mutableStateOf(Offset.Zero) }
    val bodyKey = remember { Any() }
    DisposableEffect(Unit) { onDispose { StackBodyRegistry.bounds.remove(bodyKey) } }
    SideEffect { if (members.isEmpty()) StackBodyRegistry.bounds.remove(bodyKey) }
    // Glass sits behind the provider's RemoteViews; it shows through transparent widgets
    // and is covered by widgets that paint their own opaque background.
    BoxWithConstraints(modifier.then(if (glass != null) Modifier.liquidGlass(glass.backdrop, Corner.large,
        glass.tint.copy(alpha = .14f), blurRadius = 1f, settings = glass.settings) else Modifier)
        .clip(Corner.large)
        .onGloballyPositioned {
            windowTopLeft = it.boundsInWindow().topLeft
            if (members.isNotEmpty()) StackBodyRegistry.bounds[bodyKey] = it.boundsInRoot()
        }
        // Swiping up or down on a stack moves through its widgets, as on iOS. Sideways swipes are left to the pager, and a
        // provider's own vertical scrolling (a list widget) keeps its touches: the dots still switch those.
        .stackSwipe(members.isNotEmpty(), { shown }, members.size + 1, { page -> stackPage = page },
            blockedByProvider = { down ->
                val onScreen = IntArray(2).also(rootView::getLocationOnScreen)
                val pageId = if (shown == 0) id else members.getOrElse(shown - 1) { 0 }
                pageId >= 0 && nativeWidgetConsumesVerticalGesture(rootView, down + windowTopLeft + Offset(onScreen[0].toFloat(), onScreen[1].toFloat()))
            }, onSwitched = { UnoFeedback.play(Cue.TICK, stackHaptic) })
        .then(if (members.isEmpty()) Modifier else Modifier.semantics {
            stateDescription = stackPosition
            liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
            customActions = buildList {
                if (shown < members.size) add(CustomAccessibilityAction(nextInStack) { stackPage = shown + 1; true })
                if (shown > 0) add(CustomAccessibilityAction(previousInStack) { stackPage = shown - 1; true })
            }
        })
        .testTag("widget-slot-$slot")) {
        val displayedContentSize = WidgetContentSize(maxWidth.value, maxHeight.value)
        if (id == NEEDS_BINDING_WIDGET) {
            val restore = controller.restoreDescriptor(slot)
            Surface(Modifier.fillMaxSize().testTag("widget-restore-$slot"), color = Glass.copy(alpha = .88f),
                shape = Corner.large, border = androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = .7f))) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(restore?.title ?: "Saved widget", color = Ink, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Text(restore?.profileLabel ?: "Unavailable profile", color = Ink.copy(alpha = .72f),
                        style = MaterialTheme.typography.bodySmall)
                    restoreMessage?.let { Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
                    Row {
                        TextButton(onClick = {
                            if (!controller.rebindRestoredWidget(slot, contentSize = displayedContentSize))
                                restoreMessage = "That provider or profile isn’t available. Choose a replacement."
                        },
                            modifier = Modifier.testTag("widget-restore-reconnect-$slot")) { Text(stringResource(com.jake.duolauncher.R.string.reconnect)) }
                        TextButton(onClick = onAdd, modifier = Modifier.testTag("widget-restore-replace-$slot")) { Text(stringResource(com.jake.duolauncher.R.string.replace)) }
                    }
                }
            }
            return@BoxWithConstraints
        }
        val shownId = if (shown == 0) id else members[shown - 1]
        // What a page draws is decided by the widget's id, not by its position: AnimatedContent keeps the content it first made for a
        // state, so keying it by position meant that after a widget was removed from the stack the page at that position went on
        // drawing the removed widget (it left the list but was still in the stack). Keyed by id, a removed widget's page is torn down.
        val pageContentFor: @Composable (Int, Boolean) -> Unit = { pageId, isBase ->
            val info = remember(pageId) { if (pageId >= 0) controller.manager.getAppWidgetInfo(pageId) else null }
            if (StackRules.isUno(pageId)) key(pageId) { fallback(StackRules.unoWidgetId(pageId), StackRules.unoSlot(pageId)) }
            else if (info == null) { if (isBase) fallback(id, slot) }
            else {
                key(pageId) {
                    AndroidView(factory = { context -> controller.host.createView(context, pageId, info) },
                        modifier = Modifier.fillMaxSize())
                }
            }
        }
        val order by rememberUpdatedState(listOf(id) + members)
        if (members.isEmpty()) pageContentFor(id, true)
        else androidx.compose.animation.AnimatedContent(targetState = shownId, modifier = Modifier.fillMaxSize(), label = "stack page",
            transitionSpec = {
                // The next widget rises in from below, the previous one comes down from above.
                val up = order.indexOf(targetState) > order.indexOf(initialState)
                (androidx.compose.animation.slideInVertically { if (up) it / 3 else -it / 3 } + androidx.compose.animation.fadeIn()) togetherWith
                    (androidx.compose.animation.slideOutVertically { if (up) -it / 3 else it / 3 } + androidx.compose.animation.fadeOut())
            }) { pageId -> pageContentFor(pageId, pageId == id) }
        var nameVisible by remember { mutableStateOf(false) }
        LaunchedEffect(shown) { if (members.isNotEmpty()) { nameVisible = true; delay(1100); nameVisible = false } }
        androidx.compose.animation.AnimatedVisibility(nameVisible && members.isNotEmpty(), Modifier.align(Alignment.TopCenter).padding(top = 6.dp),
            enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
            Text(stackMemberLabel(shownId, controller), color = Color.White, fontSize = 11.sp, maxLines = 1,
                modifier = Modifier.background(Color.Black.copy(alpha = .45f), Corner.pill).padding(horizontal = 10.dp, vertical = 3.dp)
                    .testTag("stack-widget-name"))
        }
        if (members.isNotEmpty()) StackRail(shown, members.size + 1, { stackPage = it },
            Modifier.align(Alignment.CenterEnd).padding(end = 4.dp))
    }
}

/**
 * Up or down on a stack steps through its widgets (up for the next, down for the previous), one step for each [STACK_SWIPE_STEP_DP]
 * travelled. The touch is watched in the Initial pass and only claimed once it is clearly vertical, so a sideways swipe still turns the
 * page and a tap or long press is untouched. [blockedByProvider] leaves a provider's own vertical scrolling alone.
 */
internal fun Modifier.stackSwipe(enabled: Boolean, shown: () -> Int, count: Int, onPage: (Int) -> Unit,
    blockedByProvider: (Offset) -> Boolean, onSwitched: () -> Unit): Modifier = composed {
    val currentShown by rememberUpdatedState(shown)
    val currentBlocked by rememberUpdatedState(blockedByProvider)
    val currentSwitched by rememberUpdatedState(onSwitched)
    val currentOnPage by rememberUpdatedState(onPage)
    pointerInput(enabled, count) {
        if (!enabled) return@pointerInput
        val step = STACK_SWIPE_STEP_DP.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (currentBlocked(down.position)) return@awaitEachGesture
            var claimed = false
            var anchorY = down.position.y
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed || event.changes.any { it.id != down.id && it.pressed }) break
                val delta = change.position - down.position
                if (!claimed) {
                    if (kotlin.math.abs(delta.x) > viewConfiguration.touchSlop && kotlin.math.abs(delta.x) >= kotlin.math.abs(delta.y)) break
                    if (kotlin.math.abs(delta.y) > viewConfiguration.touchSlop && kotlin.math.abs(delta.y) > kotlin.math.abs(delta.x)) {
                        claimed = true; anchorY = down.position.y
                    }
                }
                if (claimed) {
                    change.consume()
                    val travelled = change.position.y - anchorY
                    if (kotlin.math.abs(travelled) >= step) {
                        val next = (currentShown() + if (travelled < 0) 1 else -1).coerceIn(0, count - 1)
                        if (next != currentShown()) { currentOnPage(next); currentSwitched() }
                        anchorY = change.position.y
                    }
                }
            }
        }
    }
}

internal const val STACK_SWIPE_STEP_DP = 44

/** Where each visible stack rail is, in root coordinates, so the page gestures can step aside for a touch that starts on one. */
internal object StackRailRegistry { val bounds = mutableMapOf<Any, androidx.compose.ui.geometry.Rect>() }

/** Where each widget stack is, so a vertical swipe that starts on one switches its widget instead of opening the shade, panel or settings. */
internal object StackBodyRegistry { val bounds = mutableMapOf<Any, androidx.compose.ui.geometry.Rect>() }

/** The dots down the right edge of a widget stack. Tapping a dot, or dragging along the rail, switches widgets. It is a
 * rail and not a swipe on the widget itself because vertical swipes on Home already open the panel and notifications.
 */
@Composable
internal fun StackRail(selected: Int, count: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    var heightPx by remember { mutableIntStateOf(1) }
    fun pick(y: Float) {
        val index = ((y / heightPx.coerceAtLeast(1)) * count).toInt().coerceIn(0, count - 1)
        if (index != selected) { UnoFeedback.play(Cue.TICK, haptic); onSelect(index) }
    }
    val registryKey = remember { Any() }
    DisposableEffect(Unit) { onDispose { StackRailRegistry.bounds.remove(registryKey) } }
    Column(modifier.width(28.dp).background(Color.Black.copy(alpha = .22f), Corner.pill).padding(vertical = 8.dp)
        .onSizeChanged { heightPx = it.height }
        // The pager above must leave a touch that starts on the rail alone, or its swipe-up (settings) and swipe-down (panels)
        // take the rail's own vertical drag.
        .onGloballyPositioned { StackRailRegistry.bounds[registryKey] = it.boundsInRoot() }
        .pointerInput(count, selected) {
            detectTapGestures { pick(it.y) }
        }.pointerInput(count, selected) {
            detectDragGestures { change, _ -> change.consume(); pick(change.position.y) }
        }.semantics { hideFromAccessibility() }.testTag("stack-rail"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(count) { index ->
            Box(Modifier.size(if (index == selected) 8.dp else 6.dp)
                .background(Color.White.copy(alpha = if (index == selected) .95f else .5f), CircleShape)
                .testTag("stack-dot-$index"))
        }
    }
}

/** A large folder may be as small as 2 x 1 or 1 x 2 (never 1 x 1), up to the whole grid. */
internal val FOLDER_SPAN_CONSTRAINTS = WidgetSpanConstraints(WidgetSpan(2, 2), WidgetSpan(1, 1), WidgetSpan(GRID_COLUMNS, GRID_ROWS), true, true)

/** How long a dragged widget must rest over another's middle before it will stack onto it. */
internal const val STACK_DWELL_MS = 450L

/** The middle of a widget: only a drag held there stacks, so passing over an edge still just moves the widget. */
internal fun widgetCentre(bounds: androidx.compose.ui.geometry.Rect) = bounds.deflate(minOf(bounds.width, bounds.height) * .2f)

/** Whether [source] may be dropped onto [base]: both can stack, the source is not itself a stack, and the base has room. */
internal fun canStackWidget(source: WidgetPlacement?, base: WidgetPlacement?, controller: WidgetController): Boolean {
    if (source == null || base == null || source.slot == base.slot) return false
    return stackable(source, controller) && stackable(base, controller) &&
        WidgetStacks.members(source.slot).isEmpty() && WidgetStacks.members(base.slot).size < StackRules.MAX_MEMBERS
}

/** Whether a placed widget can join a stack: an Android widget that is bound, or an Uno one (Clock, Date, or from the Uno library). */
internal fun stackable(placement: WidgetPlacement, controller: WidgetController) =
    (placement.id >= 0 && controller.manager.getAppWidgetInfo(placement.id) != null) ||
        placement.id == CLOCK_WIDGET || placement.id == DATE_WIDGET || UnoWidgets.byId(placement.id) != null

/** The name of a stack member: an Uno widget's own name, or the Android widget's label. */
internal fun stackMemberLabel(code: Int, controller: WidgetController) =
    if (StackRules.isUno(code)) widgetLabel(StackRules.unoWidgetId(code), controller) else widgetLabel(code, controller)

internal fun widgetLabel(id: Int, controller: WidgetController) = when (id) {
    CLOCK_WIDGET -> "Clock"
    DATE_WIDGET -> "Date"
    INFO_WIDGET -> "Widget panel"
    FOLDER_WIDGET -> "Large folder"
    EMPTY_WIDGET -> "Add widget"
    else -> UnoWidgets.byId(id)?.label ?: controller.label(id)
}

@Composable
internal fun MovableWidget(id: Int, slot: Int, controller: WidgetController, drag: HomeDragState,
    target: DropTarget?, modifier: Modifier, page: Int, onAdd: () -> Unit) {
    val cell = DropTarget.Widget(slot)
    // A widget about to take another into its stack grows a little and takes a ring and a soft plate, as a folder target does on iOS.
    val stackIntent = drag.stackIntentSlot == slot
    val intent by animateFloatAsState(if (stackIntent) 1f else 0f, label = "stack intent")
    WidgetSlot(id, slot, controller, modifier.dropRegion(drag, cell, page = page, widgetId = id)
        .graphicsLayer { val scale = 1f + .05f * intent; scaleX = scale; scaleY = scale }
        .drawWithContent {
            drawContent()
            if (intent > 0f) {
                drawRect(Color.White.copy(alpha = .16f * intent))
                drawRoundRect(Color.White.copy(alpha = .9f * intent), cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
            }
        }
        .alpha(if (drag.source?.target == cell) .3f else 1f)
        .border(if (drag.active && target == cell) 2.dp else 0.dp,
            if (drag.active && target == cell) Color.White else Color.Transparent, Corner.large)
        .semantics { onLongClick("Move or replace widget") { onAdd(); true } }, onAdd) { faceId, faceSlot ->
        when (faceId) {
            CLOCK_WIDGET -> ClockCard(onAdd)
            DATE_WIDGET -> DateCard(onAdd)
            FOLDER_WIDGET -> LargeFolderFace(faceSlot, onAdd)
            INFO_WIDGET -> if (faceSlot % 3 == 2) ExpandedCard(onAdd) else GlassCard(onClick = onAdd) {
                val ink = LocalGlassInk.current
                Icon(Icons.Rounded.Widgets, null, tint = ink.primary, modifier = Modifier.size(28.dp))
                Text(stringResource(com.jake.duolauncher.R.string.your_widgets), color = ink.primary, fontSize = 15.sp, maxLines = 1)
                Text(stringResource(com.jake.duolauncher.R.string.tap_to_choose), color = ink.soft(), fontSize = 12.sp)
            }
            else -> {
                val library = UnoWidgets.byId(faceId)
                if (library != null) library.face(faceSlot, onAdd)
                else Surface(Modifier.fillMaxSize().clickable(onClick = onAdd), color = Glass.copy(alpha = .18f),
                    shape = Corner.large, border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .25f))) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Add, null, tint = Color.White)
                        Text(if (faceId >= 0) "Widget unavailable" else "Add widget", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
