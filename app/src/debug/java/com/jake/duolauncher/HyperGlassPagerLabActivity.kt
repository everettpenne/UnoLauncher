package com.jake.duolauncher

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jake.duolauncher.appearance.Corner
import com.jake.duolauncher.appearance.FrostedBlur
import com.jake.duolauncher.appearance.HyperGlass
import com.jake.duolauncher.appearance.HyperGlassHome
import com.jake.duolauncher.appearance.HyperGlassShapes
import com.jake.duolauncher.appearance.HyperGlassTextures
import com.jake.duolauncher.appearance.SharedWallpaper
import com.jake.duolauncher.appearance.SharedWallpaperRuntime
import com.jake.duolauncher.appearance.hyperGlassWallpaper

/**
 * Debug builds only: three pages of 24 app icons over a wallpaper, to measure what clear hyper glass costs while a page is swiped.
 * `--es mode plain` draws each icon's picture alone (what any icon style costs); `follow` adds the wallpaper behind each icon, redrawn
 * as it moves; `hold` adds it but leaves it be until the swipe ends. `--es shape COOKIE` (or MIXED, ...) picks the icon shape.
 * Measured from outside with `dumpsys gfxinfo` while `input swipe` turns the pages. Each frame's count of wallpaper draws is logged
 * under HyperGlassLab when the activity stops.
 */
class HyperGlassPagerLabActivity : ComponentActivity() {
    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode") ?: "follow"
        val shape = AppIconShape.fromPreference(intent.getStringExtra("shape"))
        val metrics = resources.displayMetrics
        val wallpaper = HyperGlassLabActivity.wallpaper(metrics.widthPixels, metrics.heightPixels)
        // The picture of the wallpaper as Home keeps it for its glass: a third of the size, lightly blurred.
        val shared = SharedWallpaper(com.kyant.backdrop.backdrops.emptyBackdrop()).also {
            it.artWidth = metrics.widthPixels; it.artHeight = metrics.heightPixels
            it.image = FrostedBlur.blurred(wallpaper, 3, 2)
        }
        val found = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .mapNotNull { info -> runCatching { info.loadIcon(packageManager) }.getOrNull()?.let { info.activityInfo.packageName to it } }
        // Each icon as LauncherModel makes it for the Clear style: its own layer, once, as a picture.
        val size = HyperGlassHome.ICON_PX
        val icons = found.map { (id, drawable) ->
            val outline = shape.forApp(id)
            val textures = HyperGlassTextures(HyperGlassTextures.unplated(drawable, size, 0xFFFFFFFF.toInt(), HyperGlassShapes.fit(outline)))
            val pixels = HyperGlass.render(textures.texture, textures.frost, HyperGlassShapes.of(outline, size, size * Corner.ICON_FRACTION).field, HyperGlass.REST, HyperGlass.clear.hashCode().let {
                (0.10f * 255).toInt() shl 24 or 0xFFFFFF })
            val buffer = java.nio.ByteBuffer.allocate(pixels.size * 4)
            for (c in pixels) { buffer.put((c shr 16).toByte()); buffer.put((c shr 8).toByte()); buffer.put(c.toByte()); buffer.put((c ushr 24).toByte()) }
            buffer.rewind()
            id to Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { it.copyPixelsFromBuffer(buffer) }.asImageBitmap()
        }
        setContent {
            val pager = rememberPagerState { 3 }
            DisposableEffect(Unit) {
                val was = SharedWallpaperRuntime.current
                SharedWallpaperRuntime.current = shared
                HyperGlassHome.on = mode != "plain"; HyperGlassHome.shape = shape
                HyperGlassHome.follow = mode != "hold"
                HyperGlassHome.moving = { pager.isScrollInProgress }
                HyperGlassHome.draws = 0
                onDispose { SharedWallpaperRuntime.current = was; HyperGlassHome.on = false; HyperGlassHome.follow = true; HyperGlassHome.moving = { false } }
            }
            Box(Modifier.fillMaxSize()) {
                Image(wallpaper.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                    Column(Modifier.fillMaxSize().padding(top = 72.dp, bottom = 40.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                        repeat(6) { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                repeat(4) { column ->
                                    val (id, icon) = icons[(page * 24 + row * 4 + column) % icons.size]
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Image(icon, null, Modifier.size(60.dp).graphicsLayer { }.hyperGlassWallpaper(id))
                                        Text(id.substringAfterLast('.').take(9), color = Color.White, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        android.util.Log.i("HyperGlassLab", "mode ${intent.getStringExtra("mode") ?: "follow"}: ${HyperGlassHome.draws} wallpaper draws")
    }
}
