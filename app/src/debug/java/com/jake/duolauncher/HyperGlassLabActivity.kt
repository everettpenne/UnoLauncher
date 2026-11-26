package com.jake.duolauncher

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jake.duolauncher.appearance.HyperGlass
import com.jake.duolauncher.appearance.HyperGlassAppIcon
import com.jake.duolauncher.appearance.HyperGlassIcon
import com.jake.duolauncher.appearance.HyperGlassWallpaper
import com.jake.duolauncher.appearance.HyperGlyphs

/**
 * Debug builds only: a page of hyper glass icons to look at and press (see appearance/HyperGlassIcon.kt). Open it with
 * `adb shell am start -n com.jake.duolauncher.dev/com.jake.duolauncher.HyperGlassLabActivity`. The experiment is not on Home.
 */
class HyperGlassLabActivity : ComponentActivity() {
    companion object {
    /** A stand-in wallpaper with plenty in it to bend: dunes under a sky, a sun, and a few hard lines. It fills the window and stays put
     *  while the page scrolls over it, as a wallpaper does. */
        fun wallpaper(w: Int, h: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.shader = android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0xFF234A7A.toInt(), 0xFF6FA2C8.toInt(), 0xFFF2C58A.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
        paint.color = 0xFFFFE9A8.toInt(); canvas.drawCircle(w * .74f, h * .2f, w * .13f, paint)
        val dunes = intArrayOf(0xFFE2A55E.toInt(), 0xFFC98444.toInt(), 0xFFA9652F.toInt(), 0xFF7C4722.toInt(), 0xFF53301A.toInt())
        for ((i, colour) in dunes.withIndex()) {
            val top = h * (.34f + i * .14f)
            val path = android.graphics.Path().apply {
                moveTo(0f, top + h * .05f * kotlin.math.sin(i * 1.7f))
                var x = 0f
                while (x <= w) { lineTo(x, top + h * .045f * kotlin.math.sin(x / w * (5f + i) + i * 1.3f)); x += w / 48f }
                lineTo(w.toFloat(), h.toFloat()); lineTo(0f, h.toFloat()); close()
            }
            paint.color = colour; canvas.drawPath(path, paint)
        }
        paint.color = 0x55FFFFFF; paint.strokeWidth = w * .006f
        var x = -h.toFloat()
        while (x < w) { canvas.drawLine(x, h.toFloat(), x + h * .5f, 0f, paint); x += w * .16f }
        return bitmap
    }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A handful of this phone's own app icons, to see real pictures inside the glass: their plates are taken off first.
        val found = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).take(8)
            .mapNotNull { info -> runCatching { info.loadIcon(packageManager) }.getOrNull()?.let { info.activityInfo.packageName to it } }
        val ids = found.map { it.first }
        val apps = found.map { it.second }
        val metrics = resources.displayMetrics
        val picture = wallpaper(metrics.widthPixels / 2, metrics.heightPixels / 2).asImageBitmap()
        setContent {
            val accent = com.jake.duolauncher.appearance.hyperGlassAccent()
            val behind = remember { HyperGlassWallpaper(picture) }
            Box(Modifier.fillMaxSize()) {
                Image(picture, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 56.dp, bottom = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    @Composable fun label(text: String) = Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    @Composable fun row(content: @Composable () -> Unit) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { content() }
                    label("Every icon shape: Default, Cookie, Clover")
                    val shapes = listOf(null, AppIconShape.COOKIE, AppIconShape.CLOVER, AppIconShape.FLOWER, AppIconShape.SUNNY, AppIconShape.PENTAGON)
                    row { shapes.take(3).forEachIndexed { i, shape -> HyperGlassIcon(HyperGlyphs.all[i], size = 96.dp, background = Color.Black, shape = shape, onClick = {}) } }
                    label("Flower, Sunny, Pentagon")
                    row { shapes.drop(3).forEachIndexed { i, shape -> HyperGlassIcon(HyperGlyphs.all[i + 3], size = 96.dp, background = Color.Black, shape = shape, onClick = {}) } }
                    if (apps.isNotEmpty()) {
                        label("Mixed: each app in the shape it is given")
                        row { apps.take(4).forEachIndexed { i, icon -> HyperGlassAppIcon(icon, background = Color.Black, shape = AppIconShape.MIXED.forApp(ids[i]), onClick = {}) } }
                        if (apps.size > 4) row { apps.drop(4).forEachIndexed { i, icon -> HyperGlassAppIcon(icon, background = Color.Black, shape = AppIconShape.MIXED.forApp(ids[i + 4]), onClick = {}) } }
                        label("Clear glass in shapes, over the wallpaper")
                        row { shapes.drop(1).take(4).forEachIndexed { i, shape -> HyperGlassAppIcon(apps[i % apps.size], background = HyperGlass.clear, shape = shape, wallpaper = behind, onClick = {}) } }
                    }
                    label("Black")
                    row { HyperGlyphs.all.take(4).forEach { HyperGlassIcon(it, background = Color.Black, onClick = {}) } }
                    row { HyperGlyphs.all.drop(4).forEach { HyperGlassIcon(it, background = Color.Black, onClick = {}) } }
                    if (apps.isNotEmpty()) {
                        label("Apps, plates off, in one colour")
                        row { apps.take(4).forEach { HyperGlassAppIcon(it, background = Color.Black, onClick = {}) } }
                        if (apps.size > 4) row { apps.drop(4).forEach { HyperGlassAppIcon(it, background = Color.Black, onClick = {}) } }
                    }
                    label("Large: press to bend")
                    row {
                        HyperGlassIcon(HyperGlyphs.Camera, size = 132.dp, background = Color.Black, onClick = {})
                        HyperGlassIcon(HyperGlyphs.Compass, size = 132.dp, background = Color.Black, onClick = {})
                    }
                    label("Clear glass: no card, the wallpaper bends")
                    row { HyperGlyphs.all.take(4).forEach { HyperGlassIcon(it, background = HyperGlass.clear, wallpaper = behind, onClick = {}) } }
                    if (apps.isNotEmpty()) row { apps.take(4).forEach { HyperGlassAppIcon(it, background = HyperGlass.clear, wallpaper = behind, onClick = {}) } }
                    row {
                        HyperGlassIcon(HyperGlyphs.Music, size = 132.dp, background = HyperGlass.clear, wallpaper = behind, onClick = {})
                        HyperGlassIcon(HyperGlyphs.Clock, size = 132.dp, background = HyperGlass.clear, wallpaper = behind, onClick = {})
                    }
                    label("White, and the accent")
                    row { HyperGlyphs.all.drop(4).forEach { HyperGlassIcon(it, background = Color.White, onClick = {}) } }
                    row { HyperGlyphs.all.take(4).forEach { HyperGlassIcon(it, background = accent, onClick = {}) } }
                    if (apps.isNotEmpty()) {
                        label("Apps in their own colours: on black, in shapes, in clear glass")
                        row { apps.take(4).forEach { HyperGlassAppIcon(it, background = Color.Black, mono = null, onClick = {}) } }
                        if (apps.size > 4) row { apps.drop(4).forEach { HyperGlassAppIcon(it, background = Color.Black, mono = null, onClick = {}) } }
                        row { apps.take(4).forEachIndexed { i, icon -> HyperGlassAppIcon(icon, background = Color.Black, mono = null, shape = AppIconShape.MIXED.forApp(ids[i]), onClick = {}) } }
                        if (apps.size > 4) row { apps.drop(4).forEach { HyperGlassAppIcon(it, background = HyperGlass.clear, mono = null, wallpaper = behind, onClick = {}) } }
                        row {
                            HyperGlassAppIcon(apps[1 % apps.size], size = 132.dp, background = Color.Black, mono = null, onClick = {})
                            HyperGlassAppIcon(apps[6 % apps.size], size = 132.dp, background = Color.Black, mono = null, onClick = {})
                        }
                    }
                    label("Held at the press's peak")
                    row { HyperGlyphs.all.take(4).forEach { HyperGlassIcon(it, background = Color.Black, strength = HyperGlass.PRESSED) } }
                    label("The pre-made picture older phones get (clear glass is see-through there)")
                    row {
                        HyperGlyphs.all.take(2).forEach { HyperGlassIcon(it, background = Color.Black, forceFallback = true) }
                        HyperGlyphs.all.drop(2).take(2).forEach { HyperGlassIcon(it, background = HyperGlass.clear, forceFallback = true) }
                    }
                }
            }
        }
    }
}
