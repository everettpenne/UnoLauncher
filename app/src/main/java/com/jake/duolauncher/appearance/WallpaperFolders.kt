package com.jake.duolauncher.appearance

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Size

/** A folder the person added for wallpapers: the tree Android granted ([tree]) and what the folder is called. */
internal data class WallpaperFolder(val tree: Uri, val name: String)

/** A picture in a wallpaper folder, by its document address (readable through the folder's grant). */
internal data class WallpaperImage(val uri: Uri, val name: String)

/**
 * Extra places to find wallpapers: folders the person picks with Android's folder picker (Pictures/Wallpapers, Downloads, a memory card). Uno
 * keeps read access to each folder it is given and to nothing else, needs no storage permission for it, and gives the access back when the folder
 * is removed. Only the pictures directly in a folder are listed, by name, at most [MAX_IMAGES] of them.
 */
internal object WallpaperFolders {
    private const val PREFS = "wallpaper_folders"
    private const val KEY = "trees"
    const val MAX_IMAGES = 60
    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "heic", "heif", "avif", "gif", "bmp")

    /** The stored folders, one tree address a line, oldest first and each once. Pure, so it is unit-tested. */
    fun decode(raw: String?): List<String> = raw.orEmpty().split('\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    fun encode(trees: List<String>): String = trees.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n")

    /** Whether a document is a picture Uno can use: by its type, or by its name where the provider reports none. Pure, so it is unit-tested. */
    fun isImage(mime: String?, name: String): Boolean = when {
        mime == "vnd.android.document/directory" -> false
        mime != null && mime.startsWith("image/") -> true
        mime != null && mime != "application/octet-stream" -> false
        else -> name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun stored(context: Context) = decode(runCatching { prefs(context).getString(KEY, null) }.getOrNull())
    private fun store(context: Context, trees: List<String>) = prefs(context).edit().putString(KEY, encode(trees)).apply()

    /** The folders added, oldest first; one whose access Android no longer holds (revoked, or its storage gone) is left out. */
    fun folders(context: Context): List<WallpaperFolder> {
        val granted = runCatching { context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri.toString() }.toSet() }
            .getOrDefault(emptySet())
        return stored(context).filter { it in granted }.map { raw ->
            val tree = Uri.parse(raw)
            WallpaperFolder(tree, displayName(context, tree) ?: tree.lastPathSegment?.substringAfterLast(':')?.ifEmpty { null } ?: "Folder")
        }
    }

    /** Keeps read access to [tree], as Android's folder picker returned it, and remembers it. False when Android would not grant it. */
    fun add(context: Context, tree: Uri): Boolean {
        val granted = runCatching {
            context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.isSuccess
        if (granted) store(context, stored(context) + tree.toString())
        return granted
    }

    /** Forgets [tree] and gives its access back. */
    fun remove(context: Context, tree: Uri) {
        runCatching { context.contentResolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        store(context, stored(context) - tree.toString())
    }

    /** The pictures directly in [tree], by name, at most [MAX_IMAGES]. Off the main thread: it asks the folder's provider. */
    fun images(context: Context, tree: Uri): List<WallpaperImage> = runCatching {
        val parent = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE)
        val found = ArrayList<WallpaperImage>()
        context.contentResolver.query(children, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                if (isImage(cursor.getString(2), name)) found += WallpaperImage(DocumentsContract.buildDocumentUriUsingTree(tree, id), name)
            }
        }
        found.sortedBy { it.name.lowercase() }.take(MAX_IMAGES)
    }.getOrDefault(emptyList())

    /** A small picture of [uri] for its tile, from the provider's own thumbnail where it has one, else a sampled decode. Off the main thread. */
    fun thumbnail(context: Context, uri: Uri, widthPx: Int, heightPx: Int): Bitmap? =
        runCatching { context.contentResolver.loadThumbnail(uri, Size(widthPx, heightPx), null) }.getOrNull()
            ?: runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= widthPx && bounds.outHeight / (sample * 2) >= heightPx) sample *= 2
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }.getOrNull()

    private fun displayName(context: Context, tree: Uri): String? = runCatching {
        val document = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        context.contentResolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
}
