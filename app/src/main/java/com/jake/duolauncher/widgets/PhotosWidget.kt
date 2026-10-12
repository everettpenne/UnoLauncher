package com.jake.duolauncher.widgets

import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.*
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The Photos widget: its face (the drawing) and its own helpers. Registered in [UnoWidgets].
 *
 * The pictures are chosen with Android's document picker, which grants read access to the chosen images only (no
 * photo-library permission); each choice is kept so it survives a restart. Pictures are decoded on this phone, never
 * uploaded anywhere, and a shown bitmap is never recycled out from under Compose.
 */
@Composable
internal fun PhotosFace(slot: Int, onOptions: () -> Unit) {
    val context = LocalContext.current
    val photos = PhotoGallery.parse(WidgetData.get(slot, "photos"))
    var editing by remember { mutableStateOf(false) }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (!uris.isNullOrEmpty()) {
            uris.forEach { runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
            WidgetData.set(slot, "photos", PhotoGallery.serialize(PhotoGallery.addAll(photos, uris.map { it.toString() })))
        }
    }
    // One picture per interval, derived from the clock, so the rotation picks up where it was after a restart.
    val epochSecond = rememberNow(60_000).atZone(ZoneId.systemDefault()).toEpochSecond()
    val current = photos.getOrNull(PhotoGallery.indexFor(photos.size, epochSecond))
    val picture by produceState<ImageBitmap?>(null, current) {
        value = null
        if (current != null) {
            value = PhotoCache.get(current)
                ?: withContext(Dispatchers.IO) { decodeSampled(context, current, PhotoGallery.MAX_DECODE_PX) }
                    ?.also { PhotoCache.put(current, it) }
        }
    }
    GlassCard(onClick = { editing = true }, modifier = Modifier.testTag("uno-photos-$slot"),
        padding = if (photos.isEmpty()) 14.dp else 0.dp) {
        if (photos.isEmpty()) {
            val ink = LocalGlassInk.current
            CardTitle(Icons.Rounded.PhotoLibrary, "Photos")
            Text("Tap to choose photos", color = ink.soft(), fontSize = 13.sp)
        } else if (picture != null) {
            Image(picture!!, "Chosen photo ${PhotoGallery.indexFor(photos.size, epochSecond) + 1} of ${photos.size}",
                Modifier.fillMaxSize().testTag("photos-image"), contentScale = ContentScale.Crop)
        }
    }
    if (editing) {
        AlertDialog(onDismissRequest = { editing = false }, title = { Text("Photos") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (photos.isEmpty()) "No photos chosen yet."
                    else "${photos.size} photo${if (photos.size == 1) "" else "s"}, changing every ${PhotoGallery.INTERVAL_MINUTES} minutes.")
                Text("Android's picker grants access to the pictures you choose only; they are shown on this phone and never sent anywhere.",
                    fontSize = 12.sp)
                OutlinedButton(onClick = { pickPhotos.launch(arrayOf("image/*")) }, Modifier.fillMaxWidth().testTag("photos-choose")) {
                    Text(if (photos.isEmpty()) "Choose photos" else "Add photos")
                }
                if (photos.isNotEmpty()) OutlinedButton(onClick = { WidgetData.set(slot, "photos", "") },
                    Modifier.fillMaxWidth().testTag("photos-clear")) { Text("Remove all") }
            }
        }, confirmButton = { TextButton(onClick = { editing = false }, Modifier.testTag("photos-done")) { Text("Done") } })
    }
}

/** A small in-memory cache of decoded pictures. Dropped entries are left to the garbage collector: a bitmap that
 *  Compose may still be drawing is never recycled. */
private object PhotoCache {
    private const val MAX = 12
    private val values = LinkedHashMap<String, ImageBitmap>(16, .75f, true)
    @Synchronized fun get(key: String): ImageBitmap? = values[key]
    @Synchronized fun put(key: String, value: ImageBitmap) {
        values[key] = value
        if (values.size > MAX) values.remove(values.keys.first())
    }
}

/** Decodes [uriString] downsampled so its long side is about [maxPx]. Android-facing and deliberately thin; the
 *  testable rules live in [PhotoGallery]. */
private fun decodeSampled(context: Context, uriString: String, maxPx: Int): ImageBitmap? = runCatching {
    val uri = Uri.parse(uriString)
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
}.getOrNull()

/** The Photos widget's rules, kept apart from the drawing so each is unit-tested. */
internal object PhotoGallery {
    const val MAX_PHOTOS = 30
    const val INTERVAL_MINUTES = 15L
    const val INTERVAL_SECONDS = INTERVAL_MINUTES * 60L
    const val MAX_DECODE_PX = 1080

    /** One content URI per line (a URI never contains a newline). Blanks and anything that is not a content URI are
     *  skipped, repeats dropped, and the list never holds more than [MAX_PHOTOS]. */
    fun parse(raw: String?): List<String> = raw.orEmpty().split('\n').map { it.trim() }
        .filter { it.startsWith("content://") }.distinct().take(MAX_PHOTOS)

    fun serialize(photos: List<String>): String = photos.joinToString("\n")

    fun addAll(photos: List<String>, added: List<String>): List<String> =
        (photos + added.filter { it.startsWith("content://") }).distinct().take(MAX_PHOTOS)

    /** Which picture shows at [epochSecond]: whole intervals counted from the epoch, wrapped round the list. */
    fun indexFor(count: Int, epochSecond: Long, intervalSeconds: Long = INTERVAL_SECONDS): Int =
        if (count <= 0) 0 else Math.floorMod(epochSecond / intervalSeconds, count.toLong()).toInt()
}
