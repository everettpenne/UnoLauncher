package com.jake.duolauncher

import com.jake.duolauncher.appearance.WallpaperFolders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The folders kept for wallpapers, and which documents in them count as pictures. */
class WallpaperFoldersTest {
    @Test fun foldersAreKeptOnceEachInTheOrderAdded() {
        val a = "content://com.android.externalstorage.documents/tree/primary%3APictures%2FWallpapers"
        val b = "content://com.android.externalstorage.documents/tree/primary%3ADownload"
        assertEquals(listOf(a, b), WallpaperFolders.decode(WallpaperFolders.encode(listOf(a, b, a))))
        assertEquals(emptyList<String>(), WallpaperFolders.decode(null))
        assertEquals(listOf(a), WallpaperFolders.decode("\n  $a  \n\n"))
    }

    @Test fun picturesAreRecognisedByTypeOrByName() {
        assertTrue(WallpaperFolders.isImage("image/png", "sky.png"))
        assertTrue(WallpaperFolders.isImage("image/jpeg", "anything"))
        assertFalse("a folder is not a picture", WallpaperFolders.isImage("vnd.android.document/directory", "Wallpapers"))
        assertFalse(WallpaperFolders.isImage("application/pdf", "notes.pdf"))
        assertFalse("a known non-image type wins over the name", WallpaperFolders.isImage("video/mp4", "clip.jpg"))
        // A provider that reports no type (or a generic one) is judged by the name.
        assertTrue(WallpaperFolders.isImage(null, "Dunes.WEBP"))
        assertTrue(WallpaperFolders.isImage("application/octet-stream", "night.heic"))
        assertFalse(WallpaperFolders.isImage(null, "readme.txt"))
    }
}
