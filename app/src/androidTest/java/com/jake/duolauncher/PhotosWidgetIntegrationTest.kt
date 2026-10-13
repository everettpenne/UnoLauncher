package com.jake.duolauncher

import com.jake.duolauncher.widgets.*

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotosWidgetIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)
    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun present(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test fun aChosenPhotoIsDrawnInTheWidget() {
        compose.waitUntil(90_000) { !model().state.value.loading }
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "uno-photo-test.jpg"); put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        resolver.openOutputStream(uri)!!.use {
            Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }.compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
        val layoutBefore = model().state.value.layout
        try {
            compose.runOnIdle {
                model().state.value.layout.widgetPlacements.map { it.slot }.forEach { model().removePlacement(DropTarget.Widget(it)) }
            }
            compose.waitForIdle()
            val layout = model().state.value.layout
            val index = (0 until HOME_CELLS).first { widgetCandidate(layout, 0, it, 2, 2) != null }
            compose.runOnIdle { assertTrue(model().placeWidget(widgetCandidate(layout, 0, index, 2, 2)!!.copy(id = UnoWidgets.PHOTOS))) }
            compose.runOnIdle { WidgetData.set(0, "photos", uri.toString()) }
            compose.waitForIdle()
            try { compose.waitUntil(10_000) { present("photos-image") } } catch (e: Throwable) {
                fail("photo not drawn; data=" + WidgetData.get(0, "photos") + " card=" + present("uno-photos-0"))
            }
        } finally { resolver.delete(uri, null, null); compose.runOnIdle { model().restoreLayout(layoutBefore) } }
    }
}
