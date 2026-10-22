package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** The keyboard sees everything typed, and Uno's APK holds the internet permission (for feeds you add). So the keyboard code must
 * not be able to use the network or log what is typed: this fails the build if any file in its package imports something that could.
 * What it keeps (learned words, pinned clips, recent emoji) is written by exactly one file, PersonalStore.kt, into the app's no-backup
 * storage; learning is gated on the field; and the clipboard is read only on Paste, or by the history when it is on and the copy is
 * not marked sensitive.
 */
class KeyboardPrivacyTest {
    private val network = listOf(
        "java.net", "javax.net", "okhttp", "android.net.http", "android.net.ConnectivityManager", "android.net.Network",
        "android.webkit", "HttpURLConnection", "Socket", "URL(", "FeedFetcher", "DiscoverClient",
        "android.util.Log", "Log.",
        // The clipboard may be read, never written by the keyboard itself, and never cleared: copy and cut go through the app.
        "setPrimaryClip", "ClipData.new", "clearPrimaryClip",
    )
    private val storage = listOf("FileOutputStream", "openFileOutput", "ContentResolver.insert", "AtomicFile", "writeText", "writeBytes",
        "getExternal", "Environment.", "MediaStore", "filesDir", "cacheDir")

    private fun keyboardSources(): List<File> {
        val dir = listOf("src/main/java/com/jake/duolauncher/keyboard", "app/src/main/java/com/jake/duolauncher/keyboard")
            .map(::File).first { it.isDirectory }
        return dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private fun code(file: File) = file.readLines().filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") || it.trimStart().startsWith("/**") }

    @Test fun thereAreKeyboardSourcesToCheck() = assertTrue(keyboardSources().size >= 3)

    @Test fun noKeyboardFileCanReachTheNetworkOrLog() {
        keyboardSources().forEach { file ->
            val lines = code(file)
            network.forEach { word -> assertFalse("${file.name} mentions \"$word\"", lines.any { line -> line.contains(word) }) }
        }
    }

    @Test fun onlyThePersonalStoreWritesAnything() {
        keyboardSources().filter { it.name != "PersonalStore.kt" }.forEach { file ->
            val lines = code(file)
            storage.forEach { word -> assertFalse("${file.name} mentions \"$word\"", lines.any { line -> line.contains(word) }) }
        }
    }

    @Test fun thePersonalStoreKeepsToTheAppsNoBackupStorage() {
        val store = keyboardSources().first { it.name == "PersonalStore.kt" }.readText()
        assertTrue(store.contains("noBackupFilesDir"))
        listOf("getExternal", "Environment.", "MediaStore", "cacheDir", "ContentResolver", "SharedPreferences").forEach {
            assertFalse("PersonalStore mentions $it", store.contains(it))
        }
        // Nothing is read or written before the phone's storage is unlocked.
        assertTrue(store.contains("isUserUnlocked"))
    }

    @Test fun learningIsGatedOnTheField() {
        val service = keyboardSources().first { it.name == "UnoKeyboardService.kt" }.readText()
        val learn = service.substringAfter("private fun learnCurrent(").substringBefore("\n    }\n")
        assertTrue("learnCurrent checks whether this field may be learned from", learn.contains("if (!learning) return"))
        assertTrue(service.contains("learning = LearningPolicy.allowed("))
        // Every place that teaches the model goes through learnCurrent.
        assertEquals(1, service.split("model.learnWord(").size - 1)
        val model = keyboardSources().first { it.name == "PersonalModel.kt" }.readText()
        assertTrue(model.contains("IME_FLAG_NO_PERSONALIZED_LEARNING"))
    }

    @Test fun theClipboardIsReadOnlyOnPasteOrByTheHistoryWhenItIsOn() {
        val service = keyboardSources().first { it.name == "UnoKeyboardService.kt" }.readText()
        val readers = listOf("getItemAt", "coerceToText")
        val paste = service.substringAfter("override fun paste()").substringBefore("override fun pasteClip")
        val history = service.substringAfter("private fun recordClip()").substringBefore("/** Shown unless")
        readers.forEach { word ->
            assertEquals("\"$word\" must appear only in paste and recordClip", service.split(word).size - 1, paste.split(word).size - 1 + history.split(word).size - 1)
        }
        assertTrue("the history reads only when it is on", history.contains("if (!clipHistoryOn || ui.password) return"))
        assertTrue("copies marked sensitive are skipped", history.contains("EXTRA_IS_SENSITIVE"))
        assertTrue(history.indexOf("EXTRA_IS_SENSITIVE") < history.indexOf("getItemAt"))
        // The change listener only learns that the clipboard changed (and checks its type) before handing over to recordClip.
        val listener = service.substringAfter("private fun updatePasteAvailable()").substringBefore("/** Adds what was just copied")
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
