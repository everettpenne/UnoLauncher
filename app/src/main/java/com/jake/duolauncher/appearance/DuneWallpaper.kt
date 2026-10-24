package com.jake.duolauncher.appearance

import androidx.compose.ui.graphics.asAndroidBitmap
import com.jake.duolauncher.island.*

import com.jake.duolauncher.*
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.content.SharedPreferences
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.SideEffect
import android.graphics.Bitmap
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun DuneWallpaper(modifier: Modifier = Modifier) {
    val palette = LocalDuoPalette.current
    val context = LocalContext.current.applicationContext
    val revision = LauncherBackgroundCache.revision.intValue
    SideEffect { LauncherBackgroundRuntime.builtin = builtinBackground(context) }
    // Start making the chosen scene for this screen as soon as it is the chosen background, so it is ready by the first frame.
    LaunchedEffect(palette.dark, revision) {
        if (LauncherBackgroundRuntime.builtin != BuiltinBackground.DUNES) {
            val m = context.resources.displayMetrics
            DuneSceneCache.request(LauncherBackgroundRuntime.builtin, m.widthPixels, m.heightPixels, palette.dark)
        }
    }
    val initial = remember(revision) { LauncherBackgroundCache.bitmap?.takeUnless { it.isRecycled } }
    val photo = produceState(initialValue = initial, key1 = context, key2 = revision) {
        value = withContext(Dispatchers.IO) { loadLauncherBackground(context) }
    }.value
    // Read here, in composition, so a scene finishing redraws this even if the drawing's own watch of it were missed.
    val sceneVersion = DuneSceneCache.version.intValue
    Canvas(Modifier.fillMaxSize().then(modifier)) { if (sceneVersion >= 0) drawLauncherBackground(photo?.asImageBitmap(), palette.dark) }
}

internal fun DrawScope.drawLauncherBackground(photo: ImageBitmap?, dark: Boolean = false) {
    if (photo == null || photo.width <= 0 || photo.height <= 0) {
        drawBuiltin(LauncherBackgroundRuntime.builtin, dark)
        return
    }
    val destinationWidth = size.width.toInt().coerceAtLeast(1)
    val destinationHeight = size.height.toInt().coerceAtLeast(1)
    val sourceAspect = photo.width.toFloat() / photo.height
    val destinationAspect = destinationWidth.toFloat() / destinationHeight
    val sourceWidth: Int
    val sourceHeight: Int
    if (sourceAspect > destinationAspect) {
        sourceHeight = photo.height
        sourceWidth = (sourceHeight * destinationAspect).toInt().coerceIn(1, photo.width)
    } else {
        sourceWidth = photo.width
        sourceHeight = (sourceWidth / destinationAspect).toInt().coerceIn(1, photo.height)
    }
    drawImage(
        image = photo,
        srcOffset = IntOffset((photo.width - sourceWidth) / 2, (photo.height - sourceHeight) / 2),
        srcSize = IntSize(sourceWidth, sourceHeight),
        dstSize = IntSize(destinationWidth, destinationHeight),
    )
}

internal fun DrawScope.drawDunes(dark: Boolean = false) {
        val w = size.width; val h = size.height
        drawRect(Brush.verticalGradient(if (dark) listOf(Color(0xFF132832), Color(0xFF263E49), Color(0xFF463F35))
            else listOf(Color(0xFF41687E), Color(0xFF94ADB5), Color(0xFFD8CEB6))))
        fun dune(y: Float, crest: Float, color: Color) {
            val path = Path().apply {
                moveTo(0f, h * y)
                cubicTo(w * .3f, h * (y - crest), w * .6f, h * (y + crest), w, h * (y - crest * .35f))
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(path, color)
        }
        dune(.57f, .17f, if (dark) Color(0xFF5B5040) else Color(0xFFC9B38E))
        dune(.72f, .12f, if (dark) Color(0xFF453D32) else Color(0xFFA49373))
        dune(.85f, .19f, if (dark) Color(0xFF302C26) else Color(0xFF84775F))
        for (n in 0..28) {
            val y = h * (.84f + n * .011f)
            val path = Path().apply {
                moveTo(0f, y)
                cubicTo(w * .35f, y - h * .17f, w * .65f, y + h * .05f, w, y - h * .06f)
            }
            drawPath(path, Color.White.copy(alpha = .045f), style = Stroke(1.3f))
        }
}

/** Draws whichever built-in background is selected. */
internal fun DrawScope.drawBuiltin(builtin: BuiltinBackground, dark: Boolean = false) {
    when (builtin) {
        BuiltinBackground.DUNES -> drawDunes(dark)
        BuiltinBackground.DEEP_DUNES -> drawDuneScene(BuiltinBackground.DEEP_DUNES, dark, grain = false)
        BuiltinBackground.DUNE_RANGE -> drawDuneScene(BuiltinBackground.DUNE_RANGE, dark, grain = false)
        BuiltinBackground.DUNE_GLASS -> drawDuneScene(BuiltinBackground.DUNE_GLASS, dark, grain = true)
        BuiltinBackground.DUNE_BLOOM -> drawDuneScene(BuiltinBackground.DUNE_BLOOM, dark, grain = false)
        BuiltinBackground.DUNE_FLOW -> drawDuneScene(BuiltinBackground.DUNE_FLOW, dark, grain = false)
        BuiltinBackground.MOLTEN -> drawDuneScene(BuiltinBackground.MOLTEN, dark, grain = false)
    }
}

/** Shared film-grain tile: seeded, so the Home canvas and the live wallpaper match exactly. */
private val grainTile: ImageBitmap by lazy {
    val size = 128
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(size * size)
    val random = Random(0x5EED_D00DL)
    for (i in pixels.indices) {
        // Mid-grey speckle, darker and lighter than 128, so an Overlay blend adds texture
        // without shifting the scene's average brightness.
        val v = 128 + random.nextInt(41) - 20
        pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
    bitmap.asImageBitmap()
}

/** The rendered built-in scenes (Dune glass, Dune bloom, Dune flow), one per aspect ratio and light/dark. Big ones are drawn on a background
 *  thread (the Home screen shows a plain gradient for a moment, then redraws when the scene is ready); small ones, such as the thumbnails
 *  the accent and ink sampling and the settings picker draw, are cheap and are made at once. Nothing is kept on disk: it is arithmetic.
 */
internal object DuneSceneCache {
    private const val BASE_WIDTH = 720
    private val scenes = HashMap<String, ImageBitmap>()
    private val pending = HashSet<String>()
    /** Read while drawing, so a finished scene redraws whatever is showing the placeholder. */
    val version = androidx.compose.runtime.mutableIntStateOf(0)
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    private fun key(kind: BuiltinBackground, width: Int, height: Int, dark: Boolean) =
        "${kind.name}-${(width.toFloat() / height * 20f).toInt()}-$dark-${width < 300}"

    internal fun pixels(kind: BuiltinBackground, w: Int, h: Int, dark: Boolean): IntArray = when (kind) {
        BuiltinBackground.DUNE_GLASS -> DuneGlass.render(w, h, dark)
        BuiltinBackground.DUNE_BLOOM -> DuneBloom.render(w, h, dark)
        BuiltinBackground.DUNE_FLOW -> DuneFlow.render(w, h, dark)
        BuiltinBackground.DEEP_DUNES -> DuneDeep.render(w, h, dark)
        BuiltinBackground.DUNE_RANGE -> DuneRange.render(w, h, dark)
        BuiltinBackground.MOLTEN -> MoltenMetal.render(w, h, dark)
        BuiltinBackground.DUNES -> error("Uno dunes is drawn directly, not rendered")
    }

    /** Any built-in scene as a plain bitmap at [width] x [height], for handing to Android as the system wallpaper. The
     *  original dunes are drawn with Compose paths rather than rendered pixels, so they are drawn into a bitmap here. */
    fun systemBitmap(kind: BuiltinBackground, width: Int, height: Int, dark: Boolean): android.graphics.Bitmap =
        if (kind != BuiltinBackground.DUNES) getBlocking(kind, width, height, dark).asAndroidBitmap()
        else {
            val image = ImageBitmap(width, height)
            androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
                androidx.compose.ui.unit.Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr,
                androidx.compose.ui.graphics.Canvas(image), androidx.compose.ui.geometry.Size(width.toFloat(), height.toFloat())) {
                drawDunes(dark)
            }
            image.asAndroidBitmap()
        }

    private fun build(kind: BuiltinBackground, width: Int, height: Int, dark: Boolean): ImageBitmap {
        val small = width < 300
        // Dune range keeps its edges and grain sharp by being rendered at (up to) the screen's own width; the others are soft by design.
        val base = if (kind == BuiltinBackground.DUNE_RANGE) minOf(width, DuneRange.RENDER_WIDTH).coerceAtLeast(BASE_WIDTH) else BASE_WIDTH
        val w = if (small) width.coerceAtLeast(16) else base
        val h = if (small) height.coerceAtLeast(16) else (base.toFloat() * height / width).toInt().coerceAtLeast(16)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val px = pixels(kind, w, h, dark)
        // By night the very top is the deepest black, so the camera cutout and the island's pill disappear into it and the island rises
        // out of the dark.
        if (dark) SceneMath.applyTopBlack(px, w, h)
        bitmap.setPixels(px, 0, w, 0, 0, w, h)
        return bitmap.asImageBitmap()
    }

    /** The scene for this size if it is ready. Starts building it if not (small ones are built here and now). */
    fun get(kind: BuiltinBackground, width: Int, height: Int, dark: Boolean): ImageBitmap? {
        val k = key(kind, width, height, dark)
        synchronized(this) { scenes[k]?.let { return it } }
        if (width < 300) {
            val built = build(kind, width, height, dark)
            synchronized(this) { scenes[k] = built }
            return built
        }
        request(kind, width, height, dark)
        return null
    }

    fun request(kind: BuiltinBackground, width: Int, height: Int, dark: Boolean) {
        if (kind == BuiltinBackground.DUNES) return
        val k = key(kind, width, height, dark)
        synchronized(this) { if (k in scenes || !pending.add(k)) return }
        Thread {
            val built = runCatching { build(kind, width, height, dark) }.getOrNull()
            synchronized(this) { pending.remove(k); if (built != null) scenes[k] = built }
            // Told from the main thread so the redraw is certain: the version is what the drawing watches, the background revision what
            // the Home screen watches.
            if (built != null) main.post { version.intValue++; LauncherBackgroundCache.revision.intValue++ }
        }.apply { name = "dune-scene"; isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }

    /** For the live wallpaper, which has no way to redraw by itself: builds the scene here if it is not yet ready. */
    fun getBlocking(kind: BuiltinBackground, width: Int, height: Int, dark: Boolean): ImageBitmap {
        val k = key(kind, width, height, dark)
        synchronized(this) { scenes[k]?.let { return it } }
        val built = build(kind, width, height, dark)
        synchronized(this) { scenes[k] = built }
        return built
    }
}

/** Draws a rendered scene, with a film of grain over it where [grain] is set (Dune flow and Dune bloom carry their own). */
internal fun DrawScope.drawDuneScene(kind: BuiltinBackground, dark: Boolean, grain: Boolean) {
    val w = size.width.toInt().coerceAtLeast(1); val h = size.height.toInt().coerceAtLeast(1)
    DuneSceneCache.version.intValue   // read, so the draw is redone when a scene finishes
    val scene = DuneSceneCache.get(kind, w, h, dark)
    if (scene == null) {
        // Until the scene is ready: a flat gradient in the scene's own colours.
        val colours = when (kind) {
            BuiltinBackground.DUNE_RANGE -> if (dark) listOf(Color(0xFF07142A), Color(0xFF6A5266), Color(0xFF3E3B4E)) else listOf(Color(0xFF365E78), Color(0xFFF1DDB6), Color(0xFFB38C5E))
            BuiltinBackground.DEEP_DUNES -> if (dark) listOf(Color(0xFF0C1A22), Color(0xFF3A4A52), Color(0xFF2E302B)) else listOf(Color(0xFF365E78), Color(0xFFCFD7CB), Color(0xFFD8B27C))
            BuiltinBackground.DUNE_FLOW -> if (dark) listOf(Color(0xFF06070E), Color(0xFF0B35A8), Color(0xFF2A1410)) else listOf(Color(0xFF0F3A9A), Color(0xFFBFD9F2), Color(0xFFF2D9B8))
            BuiltinBackground.DUNE_BLOOM -> if (dark) listOf(Color(0xFF0A1030), Color(0xFF1B2C8C), Color(0xFF14604A)) else listOf(Color(0xFF2556C9), Color(0xFF1848C0), Color(0xFF2C8C95))
            BuiltinBackground.MOLTEN -> if (dark) listOf(Color(0xFF040202), Color(0xFF1C0D06), Color(0xFF5A2A0E)) else listOf(Color(0xFF101923), Color(0xFF3D5264), Color(0xFF33424E))
            else -> if (dark) listOf(Color(0xFF060A16), Color(0xFF25295A), Color(0xFF4B3F78), Color(0xFF2A2748))
                else listOf(Color(0xFF244E73), Color(0xFF9DBFC4), Color(0xFFF3D5B1), Color(0xFFD9A06A))
        }
        drawRect(Brush.verticalGradient(colours))
        return
    }
    drawImage(scene, dstSize = androidx.compose.ui.unit.IntSize(w, h), filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
    if (grain) drawRect(brush = ShaderBrush(ImageShader(grainTile, TileMode.Repeated, TileMode.Repeated)),
        alpha = if (dark) .12f else .10f, blendMode = BlendMode.Overlay)
}

/** A static scene rendered on surface changes: no animation loop or background polling. */
class DuneWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = DuneEngine()

    inner class DuneEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val painter = CanvasDrawScope()
        private val appearance = AppearanceStore(this@DuneWallpaperService)
        private val appearancePrefs = getSharedPreferences("appearance", MODE_PRIVATE)
        private val backgroundPrefs = launcherBackgroundPreferences(this@DuneWallpaperService)
        private val loader = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var photo: ImageBitmap? = cachedLauncherBackground(this@DuneWallpaperService)?.asImageBitmap()
        private var photoLoad = 0
        private var photoLoading = false
        private var photoFailed = false
        private var visible = false
        private var timeReceiverRegistered = false
        private val timeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (visible) { appearance.reloadFromPreferences(systemDark()); render(surfaceHolder) }
            }
        }
        private fun registerTimeReceiver() {
            if (timeReceiverRegistered) return
            ContextCompat.registerReceiver(this@DuneWallpaperService, timeReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            timeReceiverRegistered = true
        }
        private fun unregisterTimeReceiver() {
            if (!timeReceiverRegistered) return
            unregisterReceiver(timeReceiver)
            timeReceiverRegistered = false
        }
        override fun onSurfaceCreated(holder: SurfaceHolder) { super.onSurfaceCreated(holder); appearance.reloadFromPreferences(systemDark()); render(holder) }
        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height); appearance.reloadFromPreferences(systemDark()); render(holder)
        }
        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                photoLoad++
                photoLoading = false
                photoFailed = false
                photo = cachedLauncherBackground(this@DuneWallpaperService)?.asImageBitmap()
                appearancePrefs.registerOnSharedPreferenceChangeListener(this)
                backgroundPrefs.registerOnSharedPreferenceChangeListener(this)
                registerTimeReceiver()
                appearance.reloadFromPreferences(systemDark()); render(surfaceHolder)
            } else {
                appearancePrefs.unregisterOnSharedPreferenceChangeListener(this)
                backgroundPrefs.unregisterOnSharedPreferenceChangeListener(this)
                unregisterTimeReceiver()
            }
        }
        override fun onDestroy() {
            photoLoad++
            loader.cancel()
            unregisterTimeReceiver()
            appearancePrefs.unregisterOnSharedPreferenceChangeListener(this)
            backgroundPrefs.unregisterOnSharedPreferenceChangeListener(this)
            super.onDestroy()
        }
        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            if (sharedPreferences === backgroundPrefs) {
                photoLoad++
                photo = cachedLauncherBackground(this@DuneWallpaperService)?.asImageBitmap()
                photoLoading = false
                photoFailed = false
            }
            if (visible) { appearance.reloadFromPreferences(systemDark()); render(surfaceHolder) }
        }
        private fun systemDark() = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        private fun render(holder: SurfaceHolder) {
            LauncherBackgroundRuntime.builtin = builtinBackground(this@DuneWallpaperService)
            if (!holder.surface.isValid) return
            if (launcherBackgroundEnabled(this@DuneWallpaperService) && photo == null && !photoFailed) {
                if (photoLoading) return
                photoLoading = true
                val request = ++photoLoad
                loader.launch {
                    val loaded = withContext(Dispatchers.IO) { loadLauncherBackground(this@DuneWallpaperService) }
                    if (request == photoLoad) {
                        photoLoading = false
                        photo = loaded?.asImageBitmap()
                        photoFailed = loaded == null
                        if (visible) render(holder)
                    }
                }
                return
            }
            if (!launcherBackgroundEnabled(this@DuneWallpaperService)) { photo = null; photoFailed = false }
            val canvas = try { holder.lockCanvas() } catch (_: IllegalArgumentException) { null } ?: return
            // The live wallpaper cannot redraw itself when a scene finishes, so a Dune glass scene is made before drawing.
            if (!launcherBackgroundEnabled(this@DuneWallpaperService) && LauncherBackgroundRuntime.builtin != BuiltinBackground.DUNES)
                runCatching { DuneSceneCache.getBlocking(LauncherBackgroundRuntime.builtin, canvas.width, canvas.height, appearance.state.dark) }
            try {
                painter.draw(Density(resources.displayMetrics.density), LayoutDirection.Ltr,
                    androidx.compose.ui.graphics.Canvas(canvas), Size(canvas.width.toFloat(), canvas.height.toFloat())) {
                        drawLauncherBackground(photo, appearance.state.dark)
                    }
            } finally { holder.unlockCanvasAndPost(canvas) }
        }
    }
}
