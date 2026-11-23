package com.jake.duolauncher.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The kinds of thing a mark can stand for. A kind has one shape and one place in the palette wherever it is met (a search result, a row
 * of Settings, an action in a menu), so the shapes are a vocabulary and not decoration: the cookie is always something to do, the
 * pill somewhere to go.
 */
internal enum class MarkKind {
    /** Something the launcher does when tapped: a quick action, a task, a menu's action. */ DO,
    /** Asking: the assistant, a question. */ ASK,
    /** Going somewhere else: a web search, another app, a link. */ GO,
    /** A setting or a section of them. */ SET,
    /** A person. */ PERSON,
    /** Something kept: a note, a file, a conversation. */ KEEP,
}

/** Which Material shape and which of the palette's colours each [MarkKind] takes. Pure, so it is unit-tested. */
internal object Marks {
    fun shape(kind: MarkKind): Shape = when (kind) {
        MarkKind.DO -> ExpressiveShapes.Cookie9
        MarkKind.ASK -> ExpressiveShapes.SoftBurst
        MarkKind.GO -> ExpressiveShapes.Pill
        MarkKind.SET -> ExpressiveShapes.Cookie4
        MarkKind.PERSON -> ExpressiveShapes.Flower
        MarkKind.KEEP -> ExpressiveShapes.Pentagon
    }

    /** The place in the palette: kinds that sit side by side most often (do, ask, go) take the three different colours. */
    fun colour(kind: MarkKind): Int = when (kind) {
        MarkKind.DO -> 0; MarkKind.ASK -> 1; MarkKind.GO -> 2
        MarkKind.SET -> 0; MarkKind.PERSON -> 1; MarkKind.KEEP -> 2
    }

    /** A kind for the [index]th of a list of things with no kind of their own (the sections of Settings), so neighbours differ. */
    fun cycled(index: Int): MarkKind = MarkKind.entries[((index % MarkKind.entries.size) + MarkKind.entries.size) % MarkKind.entries.size]
}

/**
 * A mark at Expressive: [icon] on a Material shape filled with its kind's tone, drawn the same in a search result, a menu and Settings.
 * It is a flat tonal fill on purpose (the panel it sits in is the glass). Callers draw their own plain mark at the other levels.
 */
@Composable
internal fun ShapedMark(icon: ImageVector, kind: MarkKind, modifier: Modifier = Modifier, size: Dp = 40.dp, iconSize: Dp = 22.dp, description: String? = null) {
    val tone = UnoTones.kind(launcherAccent().first, Marks.colour(kind), deep = true)
    Box(modifier.size(size).background(tone.container, Marks.shape(kind)), contentAlignment = Alignment.Center) {
        Icon(icon, description, Modifier.size(iconSize), tint = tone.onContainer)
    }
}
