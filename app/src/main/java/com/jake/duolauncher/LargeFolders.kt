package com.jake.duolauncher

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/** A large folder, as in HyperOS: a folder that takes several Home cells and shows its apps on the face, so they launch without
 * opening it. It is placed as a built-in widget (FOLDER_WIDGET), so placing, moving, resizing, paging and removing it all use the
 * widget machinery, and its contents live here.
 */
internal data class LargeFolder(val title: String = "Folder", val appIds: List<String> = emptyList())

/** Pure rules for large folders, so they are unit-tested apart from storage and drawing. */
internal object LargeFolderRules {
    const val MAX_APPS = 24
    const val MAX_TITLE = 24

    fun parse(raw: String?): Map<Int, LargeFolder> = runCatching {
        val root = JSONObject(raw ?: return emptyMap())
        buildMap {
            root.keys().forEach { key ->
                val slot = key.toIntOrNull() ?: return@forEach
                val item = root.optJSONObject(key) ?: return@forEach
                val ids = item.optJSONArray("a") ?: JSONArray()
                val apps = List(ids.length()) { ids.optString(it) }.filter { it.isNotBlank() }.distinct().take(MAX_APPS)
                put(slot, LargeFolder(item.optString("t", "Folder").trim().take(MAX_TITLE).ifBlank { "Folder" }, apps))
            }
        }
    }.getOrDefault(emptyMap())

    fun serialize(folders: Map<Int, LargeFolder>): String = JSONObject().also { root ->
        folders.forEach { (slot, folder) ->
            root.put(slot.toString(), JSONObject().put("t", folder.title).put("a", JSONArray(folder.appIds)))
        }
    }.toString()

    /** Adds the app, or takes it out when it is already there. A full folder refuses (null: nothing changed). */
    fun toggle(folder: LargeFolder, appId: String): LargeFolder? = when {
        appId in folder.appIds -> folder.copy(appIds = folder.appIds - appId)
        folder.appIds.size >= MAX_APPS -> null
        else -> folder.copy(appIds = folder.appIds + appId)
    }

    fun rename(folder: LargeFolder, title: String) = folder.copy(title = title.take(MAX_TITLE))

    /** Keeps only the folders whose Home widget still exists, so a slot reused by something else never inherits old contents. */
    fun prune(folders: Map<Int, LargeFolder>, placedSlots: Set<Int>) = folders.filterKeys { it in placedSlots }

    /** Icon and cell sizes (dp) for a face: roomy ones normally, compact ones on a thin face (2 x 1, 1 x 2) so a few icons still fit,
     * and whether the face has room for its title.
     */
    data class Metrics(val icon: Float, val cellW: Float, val cellH: Float, val titled: Boolean)

    fun metrics(widthDp: Float, heightDp: Float): Metrics =
        if (minOf(widthDp, heightDp) < 130f) Metrics(32f, 40f, 40f, false)
        else Metrics(44f, 56f, 62f, heightDp >= 130f)

    /** The columns and how many app icons fit on a face of this size. */
    fun capacity(widthDp: Float, heightDp: Float): Pair<Int, Int> {
        val m = metrics(widthDp, heightDp)
        val columns = ((widthDp - 16f) / m.cellW).toInt().coerceAtLeast(1)
        val rows = ((heightDp - (if (m.titled) 36f else 8f)) / m.cellH).toInt().coerceAtLeast(1)
        return columns to columns * rows
    }
}

internal object LargeFolders {
    private var prefs: android.content.SharedPreferences? = null
    var folders by mutableStateOf<Map<Int, LargeFolder>>(emptyMap()); private set

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences("large_folders", Context.MODE_PRIVATE)
        folders = LargeFolderRules.parse(runCatching { prefs?.getString("folders", null) }.getOrNull())
    }

    /** Re-reads everything (after a settings restore). */
    fun reload() { folders = LargeFolderRules.parse(runCatching { prefs?.getString("folders", null) }.getOrNull()) }

    fun of(slot: Int): LargeFolder = folders[slot] ?: LargeFolder()

    /** Puts the app in, if it is not there and there is room. True when it is in afterwards. */
    fun add(slot: Int, appId: String): Boolean {
        if (appId in of(slot).appIds) return true
        val next = LargeFolderRules.toggle(of(slot), appId) ?: return false
        commit(folders + (slot to next)); return true
    }

    fun toggle(slot: Int, appId: String) { LargeFolderRules.toggle(of(slot), appId)?.let { commit(folders + (slot to it)) } }
    fun rename(slot: Int, title: String) { commit(folders + (slot to LargeFolderRules.rename(of(slot), title))) }
    fun prune(placedSlots: Set<Int>) {
        val next = LargeFolderRules.prune(folders, placedSlots)
        if (next != folders) commit(next)
    }

    private fun commit(next: Map<Int, LargeFolder>) {
        folders = next
        prefs?.edit()?.putString("folders", LargeFolderRules.serialize(next))?.apply()
    }
}

/** What the folder face needs from the launcher screen to start an app: set by LauncherScreen each composition. */
internal object HomeAppsBridge {
    var apps by mutableStateOf<Map<String, AppEntry>>(emptyMap())
    var launch: (AppEntry, android.graphics.Rect?) -> Unit = { _, _ -> }
}

/** The face of a large folder on Home: its title and as many app icons as fit, each opening its app directly. Anything that is
 * not an icon (the title, the empty space) goes to [onOpenOptions].
 */
@Composable
internal fun LargeFolderFace(slot: Int, onOpenOptions: () -> Unit) {
    val folder = LargeFolders.of(slot)
    val apps = HomeAppsBridge.apps
    Surface(Modifier.fillMaxSize().testTag("large-folder-$slot").clickable(onClick = onOpenOptions),
        color = Glass.copy(alpha = .62f), shape = Corner.large, border = BorderStroke(1.dp, Color.White.copy(alpha = .5f))) {
        BoxWithConstraints(Modifier.padding(8.dp)) {
            val m = LargeFolderRules.metrics(maxWidth.value + 16f, maxHeight.value + 16f)
            val (columns, capacity) = LargeFolderRules.capacity(maxWidth.value + 16f, maxHeight.value + 16f)
            val shown = folder.appIds.mapNotNull(apps::get).take(capacity)
            Column(Modifier.fillMaxSize()) {
                if (m.titled) Text(folder.title, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp).testTag("large-folder-title"))
                if (shown.isEmpty()) Text(if (m.titled) "Tap to choose apps" else "Tap", color = Color.White.copy(alpha = .75f), fontSize = 12.sp,
                    modifier = Modifier.padding(4.dp))
                shown.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { app ->
                            Box(Modifier.width(m.cellW.dp).height(m.cellH.dp), contentAlignment = Alignment.TopCenter) {
                                Image(app.icon.asImageBitmap(), null, Modifier.size(m.icon.dp).clip(Corner.icon)
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                        HomeAppsBridge.launch(app, null)
                                    }.semantics { contentDescription = app.label }.testTag("large-folder-app-${app.id}"))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Choose the apps (and the name) of a large folder. */
@Composable
internal fun LargeFolderEditor(slot: Int, apps: List<AppEntry>, onClose: () -> Unit) {
    val folder = LargeFolders.of(slot)
    Column(Modifier.fillMaxWidth().fillMaxHeight(.88f).padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Large folder", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onClose, Modifier.testTag("large-folder-done")) { Text("Done") }
        }
        OutlinedTextField(folder.title, { LargeFolders.rename(slot, it) }, Modifier.fillMaxWidth().testTag("large-folder-name"),
            singleLine = true, label = { Text("Name") })
        Text("${folder.appIds.size} of ${LargeFolderRules.MAX_APPS} apps. Resize it on Home to show more icons at once.",
            Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f)) {
            items(apps, key = { it.id }) { app ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(Corner.medium)
                    .clickable { LargeFolders.toggle(slot, app.id) }.testTag("large-folder-pick-${app.id}"),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(app.id in folder.appIds, { LargeFolders.toggle(slot, app.id) })
                    Image(app.icon.asImageBitmap(), null, Modifier.size(32.dp).clip(Corner.icon))
                    Text(app.label, Modifier.padding(start = 12.dp), maxLines = 1)
                }
            }
        }
    }
}
