package com.jake.duolauncher.search

import android.graphics.Bitmap
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.Backdrop

/**
 * A still picture of the screen as a backdrop, for search's glass over another app: a window cannot sample another app's pixels, so the
 * accessibility service takes one screenshot as search opens and the glass bends that. The page sits still behind search, so a still
 * picture reads as the page itself. It is placed by where each glass surface is on the screen, the picture being the whole screen.
 * The picture lives only while search is open (see [AssistOverlay]); nothing is written anywhere.
 */
internal class ScreenBackdrop(image: Bitmap) : Backdrop {
    override val isCoordinatesDependent: Boolean = true
    private val picture = image.asImageBitmap()

    override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?, layerBlock: (GraphicsLayerScope.() -> Unit)?) {
        val at = coordinates?.positionOnScreen() ?: return
        translate(-at.x, -at.y) { drawImage(picture) }
    }
}
