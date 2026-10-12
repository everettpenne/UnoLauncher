package com.jake.duolauncher.widgets

import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.*
import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The Photos widget: its face (the drawing) and its own helpers. Registered in [UnoWidgets].
 *
 * It draws either pictures chosen by hand with Android's document picker (which grants read access to the chosen images
 * only, with no photo-library permission) or every image in one MediaStore album (which needs the optional Photos & media
 * permission). Tapping opens a full-screen preview that swipes between the pictures and carries a settings button for the
 * customization menu: how often the pictures change, which album, and whether their order is shuffled. Pictures are decoded
 * on this phone, never uploaded anywhere, and a shown bitmap is never recycled out from under Compose.
 */
@Composable
internal fun PhotosFace(slot: Int, onOptions: () -> Unit) {
    val context = LocalContext.current
    val intervalMin = PhotoGallery.intervalMinutes(WidgetData.get(slot, "interval"))
    val shuffle = PhotoGallery.shuffle(WidgetData.get(slot, "shuffle"))
    val albumId = PhotoGallery.albumId(WidgetData.get(slot, "album"))
    val manual = PhotoGallery.parse(WidgetData.get(slot, "photos"))

    // An album is read live from MediaStore (so new pictures join in), but only once a minute at most.
    var albumRefresh by remember { mutableIntStateOf(0) }
    val albumUris by produceState(emptyList<String>(), albumId, albumRefresh) {
        value = if (albumId != null) withContext(Dispatchers.IO) { PhotoAlbums.imagesCached(context, albumId) } else emptyList()
    }
    val sourceUris = if (albumId != null) albumUris else manual
    val ordered = remember(sourceUris, shuffle) { PhotoGallery.order(sourceUris, shuffle) }
    val epochSecond = rememberNow(60_000).atZone(ZoneId.systemDefault()).toEpochSecond()
    val index = PhotoGallery.indexFor(ordered.size, epochSecond, intervalMin * 60L, salt = slot.toLong())
    val currentUri = ordered.getOrNull(index)
    val picture by produceState<ImageBitmap?>(null, currentUri) {
        value = null
        if (currentUri != null) {
            value = PhotoCache.get(currentUri, PhotoGallery.FACE_DECODE_PX)
                ?: withContext(Dispatchers.IO) { decodeSampled(context, currentUri, PhotoGallery.FACE_DECODE_PX) }
                    ?.also { PhotoCache.put(currentUri, PhotoGallery.FACE_DECODE_PX, it) }
        }
    }
    var previewOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    GlassCard(onClick = { previewOpen = true }, modifier = Modifier.testTag("uno-photos-$slot"),
        padding = if (sourceUris.isEmpty()) 14.dp else 0.dp) {
        val ink = LocalGlassInk.current
        when {
            sourceUris.isEmpty() -> {
                CardTitle(Icons.Rounded.PhotoLibrary, "Photos")
                Text(when {
                    albumId != null && !PhotoAlbums.hasAccess(context) -> "Allow Photos access to show this album."
                    albumId != null -> "This album is empty."
                    else -> "Tap to choose photos"
                }, color = ink.soft(), fontSize = 13.sp)
            }
            picture != null -> Image(picture!!, "Photo ${index + 1} of ${ordered.size}",
                Modifier.fillMaxSize().testTag("photos-image"), contentScale = ContentScale.Crop)
        }
    }
    if (previewOpen) {
        val albumName = WidgetData.get(slot, "albumName")
        PhotoPreview(uris = ordered, startIndex = index,
            title = if (albumId != null) albumName.ifBlank { "Album" } else "",
            onDismiss = { previewOpen = false },
            onSettings = { previewOpen = false; menuOpen = true })
    }
    if (menuOpen) {
        PhotosMenu(slot, intervalMin, shuffle, albumId, manual, onDismiss = { menuOpen = false })
    }
}

/** A full-screen preview: swipe between the pictures, tap the picture or the cross to leave, and open the customization
 *  menu from the settings button. */
@Composable
private fun PhotoPreview(uris: List<String>, startIndex: Int, title: String,
    onDismiss: () -> Unit, onSettings: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val context = LocalContext.current
        val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, (uris.size - 1).coerceAtLeast(0)), pageCount = { uris.size })
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("photos-preview")) {
            HorizontalPager(pager, Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() }) { page ->
                val uri = uris[page]
                val bitmap by produceState<ImageBitmap?>(null, uri) {
                    value = PhotoCache.get(uri, PhotoGallery.PREVIEW_DECODE_PX)
                        ?: withContext(Dispatchers.IO) { decodeSampled(context, uri, PhotoGallery.PREVIEW_DECODE_PX) }
                            ?.also { PhotoCache.put(uri, PhotoGallery.PREVIEW_DECODE_PX, it) }
                }
                if (bitmap != null) Image(bitmap!!, "Photo ${page + 1} of ${uris.size}",
                    Modifier.fillMaxSize().testTag("photos-preview-image"), contentScale = ContentScale.Fit)
            }
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Color.Black.copy(alpha = .55f)).padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("${pager.currentPage + 1} of ${uris.size}" + if (title.isNotEmpty()) " · $title" else "",
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 1)
                IconButton(onClick = onSettings, Modifier.testTag("photos-preview-settings")) {
                    Icon(Icons.Rounded.Tune, "Photo settings", tint = Color.White)
                }
                IconButton(onClick = onDismiss, Modifier.testTag("photos-preview-close")) {
                    Icon(Icons.Rounded.Close, "Close", tint = Color.White)
                }
            }
        }
    }
}

/** The customization menu: how often the pictures change, whether they shuffle, and where they come from — a hand-picked
 *  set or one album. */
@Composable
private fun PhotosMenu(slot: Int, intervalMin: Long, shuffle: Boolean, albumId: String?, manual: List<String>,
    onDismiss: () -> Unit) {
    val context = LocalContext.current
    var showAlbums by remember { mutableStateOf(false) }
    var albumRefresh by remember { mutableIntStateOf(0) }
    val albums by produceState(emptyList<PhotoAlbum>(), showAlbums, albumRefresh) {
        value = if (showAlbums) withContext(Dispatchers.IO) { PhotoAlbums.list(context) } else emptyList()
    }
    val requestPhotos = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { albumRefresh++ }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (!uris.isNullOrEmpty()) {
            uris.forEach { runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
            WidgetData.set(slot, "photos", PhotoGallery.serialize(PhotoGallery.addAll(manual, uris.map { it.toString() })))
            WidgetData.set(slot, "album", "")
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = Corner.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.testTag("photos-menu")) {
            Column(Modifier.padding(20.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Photos settings", style = MaterialTheme.typography.titleLarge)
                Text("Change every", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PhotoGallery.INTERVALS.forEach { minutes ->
                        FilterChip(selected = minutes == intervalMin,
                            onClick = { WidgetData.set(slot, "interval", minutes.toString()) },
                            label = { Text(if (minutes >= 60) "${minutes / 60} h" else "$minutes min") },
                            modifier = Modifier.testTag("photos-interval-$minutes"))
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Shuffle", style = MaterialTheme.typography.titleSmall)
                        Text("Mix the order so the same first photo does not always lead.", fontSize = 12.sp)
                    }
                    Switch(shuffle, { WidgetData.set(slot, "shuffle", if (it) "1" else "") },
                        Modifier.testTag("photos-shuffle"))
                }
                HorizontalDivider()
                Text("Where the photos come from", style = MaterialTheme.typography.titleSmall)
                // Manual source.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Chosen photos", style = MaterialTheme.typography.bodyLarge)
                        Text("${manual.size} photo${if (manual.size == 1) "" else "s"} · pick them yourself", fontSize = 12.sp)
                    }
                    if (albumId != null) TextButton(onClick = { WidgetData.set(slot, "album", "") },
                        Modifier.testTag("photos-source-manual")) { Text("Use these") }
                }
                OutlinedButton(onClick = { pickPhotos.launch(arrayOf("image/*")) },
                    Modifier.fillMaxWidth().testTag("photos-choose")) { Text("Choose photos…") }
                // Album source.
                if (!PhotoAlbums.hasAccess(context)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("To draw from an album, Uno needs Photos & media access — read only, to list your albums and show them.",
                            Modifier.weight(1f), fontSize = 12.sp)
                        OutlinedButton(onClick = { requestPhotos.launch(PhotoAlbums.permission()) },
                            Modifier.testTag("photos-allow")) { Text("Allow") }
                    }
                } else {
                    OutlinedButton(onClick = { showAlbums = !showAlbums },
                        Modifier.fillMaxWidth().testTag("photos-album-toggle")) {
                        Text(if (showAlbums) "Hide albums" else (if (albumId != null) WidgetData.get(slot, "albumName").ifBlank { "Album" } else "Choose an album…"))
                    }
                    if (showAlbums) {
                        if (albums.isEmpty()) Text("No albums found.", fontSize = 12.sp)
                        albums.forEach { album ->
                            AlbumRow(album, selected = album.bucketId == albumId, onClick = {
                                WidgetData.set(slot, "album", album.bucketId)
                                WidgetData.set(slot, "albumName", album.name)
                            })
                        }
                    }
                }
                Text("Android grants read access to the chosen pictures or album only; they are shown on this phone and never sent anywhere.",
                    fontSize = 11.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, Modifier.testTag("photos-done")) { Text("Done") }
                }
            }
        }
    }
}

/** One album in the picker: its cover, name and size. */
@Composable
private fun AlbumRow(album: PhotoAlbum, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val cover by produceState<ImageBitmap?>(null, album.cover) {
        value = album.cover?.let { uri -> PhotoCache.get(uri, 96)
            ?: withContext(Dispatchers.IO) { decodeSampled(context, uri, 96) }?.also { PhotoCache.put(uri, 96, it) } }
    }
    Row(Modifier.fillMaxWidth().clip(Corner.medium).clickable(onClick = onClick)
        .padding(vertical = 4.dp).testTag("photos-album-${album.bucketId}"), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(Corner.small).background(Color.Black.copy(alpha = .08f)), contentAlignment = Alignment.Center) {
            if (cover != null) Image(cover!!, album.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Rounded.PhotoLibrary, null)
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(album.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text("${album.count} photo${if (album.count == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall)
        }
        if (selected) Icon(Icons.Rounded.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
    }
}

/** A small in-memory cache of decoded pictures, keyed by URI and target size. Dropped entries are left to the garbage
 *  collector: a bitmap that Compose may still be drawing is never recycled. */
private object PhotoCache {
    private const val MAX = 24
    private val values = LinkedHashMap<String, ImageBitmap>(16, .75f, true)
    @Synchronized fun get(uri: String, maxPx: Int): ImageBitmap? = values[key(uri, maxPx)]
    @Synchronized fun put(uri: String, maxPx: Int, value: ImageBitmap) {
        values[key(uri, maxPx)] = value
        if (values.size > MAX) values.remove(values.keys.first())
    }
    private fun key(uri: String, maxPx: Int) = "$uri@$maxPx"
}

/** Decodes [uriString] downsampled so its long side is about [maxPx]. Android-facing and deliberately thin; the
 *  testable rules live in [PhotoGallery]. */
private fun decodeSampled(context: Context, uriString: String, maxPx: Int): ImageBitmap? = runCatching {
    val uri = Uri.parse(uriString)
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    // decodeStream returns null when inJustDecodeBounds is set (it only fills in the size), so that null is not an error.
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: Unit
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
}.getOrNull()

/** An album of images, as MediaStore reports it. */
internal data class PhotoAlbum(val bucketId: String, val name: String, val count: Int, val cover: String?)

/** MediaStore reads for the album source. Every query needs Photos & media access, so each is guarded up front and a
 *  missing permission or a changed library simply reads as empty. */
internal object PhotoAlbums {
    const val CACHE_MS = 60_000L

    /** The runtime permission to ask for, by Android version. */
    fun permission(): String = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasAccess(context: Context): Boolean =
        context.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED

    /** Albums with at least one image, newest first by their newest picture. */
    fun list(context: Context): List<PhotoAlbum> {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) return emptyList()
        if (Build.VERSION.SDK_INT < 33 && context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) return emptyList()
        return runCatching {
            val resolver = context.contentResolver
            val projection = arrayOf(MediaStore.Images.Media.BUCKET_ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media._ID)
            data class Acc(var name: String, var count: Int = 0, var newest: Long = 0L, var cover: String? = null)
            val acc = LinkedHashMap<String, Acc>()
            resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null,
                MediaStore.Images.Media.DATE_ADDED + " DESC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val bucket = cursor.getString(bucketCol)
                    val entry = acc.getOrPut(bucket) { Acc(cursor.getString(nameCol)) }
                    entry.count++
                    if (entry.cover == null) entry.cover = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(idCol)).toString()
                }
            }
            acc.map { (bucket, entry) -> PhotoAlbum(bucket, entry.name.ifBlank { "Album" }, entry.count, entry.cover) }
                .sortedByDescending { it.count }
        }.getOrDefault(emptyList())
    }

    /** Every image in [bucketId], newest first, as content URIs. */
    fun images(context: Context, bucketId: String): List<String> {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) return emptyList()
        if (Build.VERSION.SDK_INT < 33 && context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) return emptyList()
        return runCatching {
            val resolver = context.contentResolver
            val result = mutableListOf<String>()
            resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
                MediaStore.Images.Media.BUCKET_ID + "=?", arrayOf(bucketId),
                MediaStore.Images.Media.DATE_ADDED + " DESC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    result += ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(idCol)).toString()
                }
            }
            result
        }.getOrDefault(emptyList())
    }

    /** [images], but re-queried at most once a minute so the rotating face does not hammer MediaStore. */
    fun imagesCached(context: Context, bucketId: String): List<String> {
        AlbumImageCache.get(bucketId)?.let { return it }
        return images(context, bucketId).also { AlbumImageCache.put(bucketId, it) }
    }
}

private object AlbumImageCache {
    private val values = HashMap<String, Pair<List<String>, Long>>()
    @Synchronized fun get(bucketId: String): List<String>? =
        values[bucketId]?.takeIf { System.currentTimeMillis() - it.second < PhotoAlbums.CACHE_MS }?.first
    @Synchronized fun put(bucketId: String, uris: List<String>) {
        values[bucketId] = uris to System.currentTimeMillis()
    }
}

/** The Photos widget's rules, kept apart from the drawing so each is unit-tested. */
internal object PhotoGallery {
    const val MAX_PHOTOS = 30
    const val FACE_DECODE_PX = 1080
    const val PREVIEW_DECODE_PX = 2160
    const val INTERVAL_MINUTES = 15L
    const val INTERVAL_SECONDS = INTERVAL_MINUTES * 60L
    /** How often the pictures can change, in minutes. */
    val INTERVALS: List<Long> = listOf(1L, 5L, 15L, 30L, 60L)

    /** One content URI per line (a URI never contains a newline). Blanks and anything that is not a content URI are
     *  skipped, repeats dropped, and the list never holds more than [MAX_PHOTOS]. */
    fun parse(raw: String?): List<String> = raw.orEmpty().split('\n').map { it.trim() }
        .filter { it.startsWith("content://") }.distinct().take(MAX_PHOTOS)

    fun serialize(photos: List<String>): String = photos.joinToString("\n")

    fun addAll(photos: List<String>, added: List<String>): List<String> =
        (photos + added.filter { it.startsWith("content://") }).distinct().take(MAX_PHOTOS)

    /** The change interval in minutes, from the saved setting, or the default when unset or not offered. */
    fun intervalMinutes(raw: String?): Long =
        raw?.trim()?.toLongOrNull()?.takeIf { it in INTERVALS } ?: INTERVAL_MINUTES

    fun shuffle(raw: String?): Boolean = raw?.trim() == "1"

    fun albumId(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }

    /** A deterministic reorder when shuffled, identity otherwise, so the same gallery always mixes the same way. */
    fun order(uris: List<String>, shuffle: Boolean): List<String> = if (shuffle) uris.sortedBy { it.hashCode() } else uris

    /** Which picture shows at [epochSecond]: whole intervals counted from the epoch, wrapped round the list, starting
     *  [salt] intervals along so two widgets do not always begin on the same picture. */
    fun indexFor(count: Int, epochSecond: Long, intervalSeconds: Long = INTERVAL_SECONDS, salt: Long = 0L): Int =
        if (count <= 0) 0 else Math.floorMod(epochSecond / intervalSeconds + salt, count.toLong()).toInt()
}
