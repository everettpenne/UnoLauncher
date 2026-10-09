package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** The keyboard sees everything typed, and Uno's APK holds the internet permission (for feeds you add). So the keyboard
 * code must not be able to use the network or record what is typed: this fails the build if any file in its package
 * imports something that could, or logs. The clipboard is the one thing it may read, and only when Paste is tapped.
 */
class KeyboardPrivacyTest {
    private val forbidden = listOf(
        "java.net", "javax.net", "okhttp", "android.net.http", "android.net.ConnectivityManager", "android.net.Network",
        "android.webkit", "HttpURLConnection", "Socket", "URL(", "FeedFetcher", "DiscoverClient",
        "android.util.Log", "Log.", "FileOutputStream", "openFileOutput", "ContentResolver.insert",
        // The clipboard may be read (Paste), never written, and never watched for its contents: see theClipboardIsReadOnlyWhenPasteIsTapped.
        "setPrimaryClip", "ClipData.new", "clearPrimaryClip",
    )

    private fun keyboardSources(): List<File> {
        val dir = listOf("src/main/java/com/jake/duolauncher/keyboard", "app/src/main/java/com/jake/duolauncher/keyboard")
            .map(::File).first { it.isDirectory }
        return dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test fun thereAreKeyboardSourcesToCheck() = assertTrue(keyboardSources().size >= 3)

    @Test fun noKeyboardFileCanReachTheNetworkOrKeepAnythingTyped() {
        keyboardSources().forEach { file ->
            val code = file.readLines().filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") || it.trimStart().startsWith("/**") }
            forbidden.forEach { word ->
                assertFalse("${file.name} mentions \"$word\"", code.any { line -> line.contains(word) })
            }
        }
    }

    @Test fun theClipboardIsReadOnlyWhenPasteIsTapped() {
        val service = keyboardSources().first { it.name == "UnoKeyboardService.kt" }.readText()
        // The only code that reads what is on the clipboard is the paste action, which runs on a tap.
        val readers = listOf("getItemAt", "coerceToText", ".primaryClip?", ".primaryClip.")
        val paste = service.substringAfter("override fun paste()").substringBefore("override fun setOneHanded")
        readers.forEach { word ->
            assertEquals("\"$word\" must appear only in the paste action", service.split(word).size - 1, paste.split(word).size - 1)
        }
        assertTrue("the paste action reads the clipboard", paste.contains("primaryClip"))
        // The listener only learns that the clipboard changed (and checks its type); it never touches the content.
        val listener = service.substringAfter("private fun updatePasteAvailable()").substringBefore("/** Shown unless")
        assertFalse(listener.contains("getItemAt") || listener.contains("coerceToText"))
        // No other keyboard file touches the clipboard at all.
        keyboardSources().filter { it.name != "UnoKeyboardService.kt" }.forEach { assertFalse(it.name, it.readText().contains("ClipboardManager")) }
    }

    @Test fun theKeyboardIsNotExportedBeyondTheSystemsInputMethodBinding() {
        val manifest = listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml").map(::File).first { it.isFile }.readText()
        val service = manifest.substringAfter(".keyboard.UnoKeyboardService").substringBefore("</service>")
        assertTrue(service.contains("android.permission.BIND_INPUT_METHOD"))
    }
}
