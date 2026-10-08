package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** The keyboard sees everything typed, and Uno's APK holds the internet permission (for feeds you add). So the keyboard
 * code must not be able to use the network or record what is typed: this fails the build if any file in its package
 * imports something that could, or logs.
 */
class KeyboardPrivacyTest {
    private val forbidden = listOf(
        "java.net", "javax.net", "okhttp", "android.net.http", "android.net.ConnectivityManager", "android.net.Network",
        "android.webkit", "HttpURLConnection", "Socket", "URL(", "FeedFetcher", "DiscoverClient",
        "android.util.Log", "Log.", "FileOutputStream", "openFileOutput", "ContentResolver.insert", "Clipboard",
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

    @Test fun theKeyboardIsNotExportedBeyondTheSystemsInputMethodBinding() {
        val manifest = listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml").map(::File).first { it.isFile }.readText()
        val service = manifest.substringAfter(".keyboard.UnoKeyboardService").substringBefore("</service>")
        assertTrue(service.contains("android.permission.BIND_INPUT_METHOD"))
    }
}
