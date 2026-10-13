package com.jake.duolauncher.island

import com.jake.duolauncher.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Importing and exporting island themes through Android's document picker. Only files the person picks are read: there
 * is no intent filter, so no other app can hand the launcher a theme. A picked file is parsed (size-limited, strict) and
 * shown as a preview; nothing changes until the person taps Apply.
 */
internal class IslandThemeController(private val activity: ComponentActivity) {
    /** A theme read from a file, waiting for the person to apply or discard it. */
    var preview by mutableStateOf<IslandTheme?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    private val open = activity.activityResultRegistry.register(
        "uno.island.theme.open", activity, ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) activity.lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { activity.contentResolver.openInputStream(uri)?.use { readAtMost(it, IslandThemeFile.MAX_BYTES + 1) } }.getOrNull()
            }
            when (val result = if (bytes == null) null else IslandThemeFile.parse(bytes)) {
                null -> message = "That file could not be read."
                is IslandThemeFile.Result.Rejected -> { preview = null; message = result.reason }
                is IslandThemeFile.Result.Ok -> { preview = result.theme; message = null }
            }
        }
    }
    private val create = activity.activityResultRegistry.register(
        "uno.island.theme.create", activity, ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) activity.lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { activity.contentResolver.openOutputStream(uri, "wt")?.use { it.write(IslandThemeFile.toJson(IslandThemes.active).toByteArray()) } != null }.getOrDefault(false)
            }
            message = if (ok) "Theme saved." else "The theme could not be saved."
        }
    }

    /** Reads at most [limit] bytes (InputStream.readNBytes needs API 33; this app supports 31). */
    private fun readAtMost(stream: java.io.InputStream, limit: Int): ByteArray {
        val buffer = ByteArray(limit); var total = 0
        while (total < limit) { val n = stream.read(buffer, total, limit - total); if (n < 0) break; total += n }
        return buffer.copyOf(total)
    }

    fun importTheme() { message = null; open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
    fun exportTheme() { message = null; create.launch(IslandThemes.active.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "island" } + ".unoisland.json") }
    /** Puts the current theme on the clipboard as text, to send to someone in any messaging app. */
    fun copyTheme() {
        val clipboard = activity.getSystemService(android.content.ClipboardManager::class.java)
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Uno island theme", IslandThemeFile.toJson(IslandThemes.active)))
        message = activity.getString(com.jake.duolauncher.R.string.island_theme_copied)
    }

    /** Reads a theme from the clipboard text, on the person's tap only, and shows it for review like an imported file. */
    fun pasteTheme() {
        val clipboard = activity.getSystemService(android.content.ClipboardManager::class.java)
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(activity)?.toString()
        if (text.isNullOrBlank()) { message = activity.getString(com.jake.duolauncher.R.string.island_theme_clipboard_empty); return }
        when (val result = IslandThemeFile.parse(text.take(IslandThemeFile.MAX_BYTES + 1).toByteArray())) {
            is IslandThemeFile.Result.Rejected -> { preview = null; message = result.reason }
            is IslandThemeFile.Result.Ok -> { preview = result.theme; message = null }
        }
    }

    fun applyPreview() { preview?.let { IslandThemes.apply(it); message = "Applied ${it.name}." }; preview = null }
    fun cancelPreview() { preview = null }
    fun clearMessage() { message = null }
    fun dispose() { open.unregister(); create.unregister() }

    companion object { var current: IslandThemeController? = null }
}
