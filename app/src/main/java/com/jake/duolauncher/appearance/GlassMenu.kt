package com.jake.duolauncher.appearance

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow

/**
 * A pop-up menu that is liquid glass, like the menus on iOS: a rounded panel that bends and blurs what is behind it, with the rim light,
 * that springs open from the corner nearest the thing you tapped. With liquid glass off (no [glass]) it is Material's own menu.
 *
 * A pop-up lives in a window of its own, so the glass has to be told where that window sits: it draws the backdrop shifted by the
 * pop-up's position, so the panel bends the part of the screen really behind it. [glass] is the page's glass; pass it where the menu is
 * outside the Home pager and so has none of its own.
 */
@Composable
internal fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    glass: PageGlass? = LocalPageGlass.current,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (glass == null) { DropdownMenu(expanded, onDismissRequest, modifier, offset = offset, content = content); return }
    if (!expanded) return
    val density = androidx.compose.ui.platform.LocalDensity.current
    val gap = with(density) { 6.dp.roundToPx() }
    val dx = with(density) { offset.x.roundToPx() }; val dy = with(density) { offset.y.roundToPx() }
    var popupAt by remember { mutableStateOf(IntOffset.Zero) }
    var origin by remember { mutableStateOf(TransformOrigin(0f, 0f)) }
    val provider = remember(gap, dx, dy) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                // Below the anchor, its start edges aligned; above it when there is no room; kept inside the window.
                val x = (anchorBounds.left + dx).coerceIn(gap, (windowSize.width - popupContentSize.width - gap).coerceAtLeast(gap))
                val below = anchorBounds.bottom + dy + gap
                val flip = below + popupContentSize.height > windowSize.height - gap && anchorBounds.top - gap - popupContentSize.height >= gap
                val y = if (flip) anchorBounds.top - gap - popupContentSize.height else below.coerceAtMost((windowSize.height - popupContentSize.height - gap).coerceAtLeast(gap))
                val at = IntOffset(x, y)
                popupAt = at
                origin = TransformOrigin(
                    ((anchorBounds.left + anchorBounds.width / 2f - x) / popupContentSize.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                    if (flip) 1f else 0f)
                return at
            }
        }
    }
    val open = remember { Animatable(0f, .001f) }
    LaunchedEffect(Unit) { open.animateTo(1f, spring(.72f, 520f, .001f)) }
    val tone = rememberGlassTone()
    val shape = Corner.large
    val tint = MaterialTheme.colorScheme.surface.copy(alpha = .45f)
    // The panel samples the page's backdrop from inside its own window, which starts at the pop-up's corner: draw it shifted back.
    val shifted = rememberBackdrop(glass.backdrop) { drawBackdrop -> translate(-popupAt.x.toFloat(), -popupAt.y.toFloat()) { drawBackdrop() } }
    Popup(popupPositionProvider = provider, onDismissRequest = onDismissRequest, properties = PopupProperties(focusable = true)) {
        Column(modifier
            .graphicsLayer {
                val grow = .82f + .18f * open.value
                scaleX = grow; scaleY = grow; alpha = open.value.coerceIn(0f, 1f); transformOrigin = origin
            }
            .widthIn(min = 200.dp, max = 300.dp).width(IntrinsicSize.Max)
            .drawBackdrop(
                backdrop = shifted,
                shape = { shape },
                effects = {
                    glassColor(tone)
                    blur(10f.dp.toPx())
                    lens(14f.dp.toPx(), 28f.dp.toPx())
                },
                highlight = { GlassRim.Light },
                shadow = null,
                onDrawSurface = { drawRect(tint) })
            .padding(vertical = 8.dp)) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface, content = { content() })
        }
    }
}

/** A row in a [GlassDropdownMenu]: fills with a soft light while pressed, as iOS menu rows do. Same shape as Material's menu item. */
@Composable
internal fun GlassMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp)
        .background(if (pressed) LocalContentColor.current.copy(alpha = .12f) else Color.Transparent)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        leadingIcon?.invoke()
        androidx.compose.material3.ProvideTextStyle(androidx.compose.ui.text.TextStyle(fontSize = 16.sp)) {
            CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = if (enabled) 1f else .45f)) { text() }
        }
    }
}
