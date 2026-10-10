package com.jake.duolauncher

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.security.MessageDigest

/** One GitHub release of Uno, with the URLs that matter for installing it. */
internal data class UnoRelease(
    val tag: String,           // version name without the leading v
    val name: String,
    val publishedAt: String,
    val apkUrl: String,
    val sumsUrl: String?,
)

internal data class UpdateState(
    val releases: List<UnoRelease> = emptyList(),
    val installed: String = "",
    val checking: Boolean = false,
    val checkedAt: Long = 0L,
    val error: String? = null,
    val downloadingTag: String? = null,
    val readyTag: String? = null,
    val autoUpdate: Boolean = false,
)

private sealed interface UpdateResult {
    data class Success(val value: String) : UpdateResult
    data class Failure(val message: String) : UpdateResult
}

private sealed interface DownloadResult {
    data class Success(val file: File) : DownloadResult
    data class Failure(val message: String) : DownloadResult
}

/** Orders Uno version names: numeric dotted parts first, then the suffix after '-'.
 * "0.16.10-uno01" > "0.16.9-uno01", and a suffixed build beats the bare version.
 */
internal fun compareVersionNames(a: String, b: String): Int {
    fun parts(v: String): Pair<List<Int>, String> {
        val match = Regex("^(\\d+(?:\\.\\d+)*)(?:-(.*))?$").find(v.trim())
        if (match == null) return emptyList<Int>() to v.trim()
        return match.groupValues[1].split('.').map { it.toIntOrNull() ?: 0 } to match.groupValues[2].orEmpty()
    }
    val (numbersA, suffixA) = parts(a)
    val (numbersB, suffixB) = parts(b)
    for (index in 0 until maxOf(numbersA.size, numbersB.size)) {
        val x = numbersA.getOrElse(index) { 0 }
        val y = numbersB.getOrElse(index) { 0 }
        if (x != y) return x.compareTo(y)
    }
    return suffixA.compareTo(suffixB)
}

/** Parses the GitHub releases JSON into the entries a user could install. */
internal fun parseReleases(json: String): List<UnoRelease> = runCatching {
    val array = JSONArray(json)
    buildList {
        for (index in 0 until array.length()) {
            val release = array.getJSONObject(index)
            val assets = release.optJSONArray("assets") ?: continue
            var apkUrl: String? = null
            var sumsUrl: String? = null
            for (assetIndex in 0 until assets.length()) {
                val asset = assets.getJSONObject(assetIndex)
                val name = asset.optString("name", "")
                val url = asset.optString("browser_download_url", "")
                when {
                    name.endsWith(".apk") -> apkUrl = url
                    name == "SHA256SUMS.txt" -> sumsUrl = url
                }
            }
            if (apkUrl != null) add(UnoRelease(
                tag = release.optString("tag_name", "").removePrefix("v"),
                name = release.optString("name", ""),
                publishedAt = release.optString("published_at", "").take(10),
                apkUrl = apkUrl,
                sumsUrl = sumsUrl))
        }
    }
}.getOrDefault(emptyList())

/** Extracts the expected SHA-256 for [apkName] from a SHA256SUMS text. */
internal fun expectedShaFor(sumsText: String, apkName: String): String? =
    sumsText.lineSequence()
        .firstOrNull { it.contains(apkName) }
        ?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.lowercase()

/** Checks GitHub for Uno releases, downloads and verifies APKs, and hands the ready file
 * back through [onReady] (main thread) so the owner can open Android's install prompt.
 * Everything is optional and explicit: nothing downloads unless asked or auto-update is on.
 */
internal class UpdateStore(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onReady: (UnoRelease) -> Unit,
) {
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(UpdateState(
        installed = installedName(),
        autoUpdate = runCatching { prefs.getBoolean("autoUpdate", false) }.getOrDefault(false)))
    val state: StateFlow<UpdateState> = _state
    private var checking = false

    private fun installedName() = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }.getOrDefault("")

    fun setAutoUpdate(value: Boolean) {
        prefs.edit().putBoolean("autoUpdate", value).apply()
        _state.update { it.copy(autoUpdate = value) }
        if (value) checkNow()
    }

    fun checkNow() {
        if (checking) return
        checking = true
        _state.update { it.copy(checking = true, error = null) }
        scope.launch {
            val result = withContext(Dispatchers.IO) { fetchText(RELEASES_URL) }
            when (result) {
                is UpdateResult.Success -> {
                    val releases = parseReleases(result.value)
                    _state.update {
                        it.copy(checking = false, releases = releases, checkedAt = System.currentTimeMillis(),
                            installed = installedName(),
                            error = if (releases.isEmpty()) "No releases were found." else null)
                    }
                    if (_state.value.autoUpdate) maybeAutoUpdate()
                }
                is UpdateResult.Failure -> _state.update { it.copy(checking = false, error = result.message) }
            }
            checking = false
        }
    }

    fun checkIfStale(maxAgeMillis: Long = 4L * 60L * 60L * 1000L) {
        if (!_state.value.autoUpdate) return
        if (System.currentTimeMillis() - (_state.value.checkedAt) > maxAgeMillis) checkNow()
    }

    private fun maybeAutoUpdate() {
        val releases = _state.value.releases
        val newest = releases.firstOrNull() ?: return
        if (compareVersionNames(newest.tag, _state.value.installed) > 0) installRelease(newest.tag)
    }

    fun installRelease(tag: String) {
        val release = _state.value.releases.firstOrNull { it.tag == tag } ?: return
        if (_state.value.readyTag == tag) { onReady(release); return }
        scope.launch {
            _state.update { it.copy(downloadingTag = tag, error = null) }
            val result = withContext(Dispatchers.IO) { download(release) }
            when (result) {
                is DownloadResult.Success -> {
                    _state.update { it.copy(downloadingTag = null, readyTag = tag, installed = installedName()) }
                    onReady(release)
                }
                is DownloadResult.Failure -> _state.update { it.copy(downloadingTag = null, error = result.message) }
            }
        }
    }

    fun readyApk(tag: String): File? {
        val file = File(File(context.cacheDir, "updates"), "$tag.apk")
        return file.takeIf { it.isFile && it.canRead() }
    }

    private fun download(release: UnoRelease): DownloadResult { return try {
        val apkName = release.apkUrl.substringAfterLast('/')
        val expected = release.sumsUrl?.let { url -> (fetchText(url, MAX_SUMS_BYTES) as? UpdateResult.Success)?.value }
            ?.let { expectedShaFor(it, apkName) }
        // Fail closed: a release that publishes no checksum for its APK is not installed unchecked.
        if (expected == null) return DownloadResult.Failure("This release has no published checksum for its APK, so it was not downloaded.")
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "${release.tag}.apk")
        val actual = downloadTo(release.apkUrl, file, MAX_APK_BYTES.toLong())
            ?: return DownloadResult.Failure("The download failed.")
        when (UpdateSecurity.checksum(expected, actual)) {
            UpdateSecurity.Checksum.OK -> Unit
            else -> { file.delete(); return DownloadResult.Failure("The download didn't match its published checksum.") }
        }
        // The same key: the APK must carry exactly the signing certificates of the app already installed, checked here as well
        // as by Android, so a wrongly signed file is refused before it is ever handed to the installer.
        if (!UpdateSecurity.sameSigners(signerHashes(installedInfo()), signerHashes(archiveInfo(file)))) {
            file.delete()
            return DownloadResult.Failure("The update isn't signed by the same key as this app, so it was not installed.")
        }
        DownloadResult.Success(file)
    } catch (e: SecurityException) {
        DownloadResult.Failure("Network access is turned off for Uno Launcher.")
    } catch (e: IOException) {
        DownloadResult.Failure("The download failed.")
    } }

    @Suppress("DEPRECATION")
    private fun installedInfo(): android.content.pm.PackageInfo? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun archiveInfo(file: File): android.content.pm.PackageInfo? = runCatching {
        context.packageManager.getPackageArchiveInfo(file.path, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
    }.getOrNull()

    private fun signerHashes(info: android.content.pm.PackageInfo?): Set<String> =
        info?.signingInfo?.apkContentsSigners?.mapTo(mutableSetOf()) { UpdateSecurity.sha256Hex(it.toByteArray()) }.orEmpty()

    private fun fetchText(url: String, maxBytes: Int = MAX_TEXT_BYTES): UpdateResult {
        try {
            val connection = open(url)
            val code = connection.responseCode
            if (code !in 200..299) return UpdateResult.Failure("The server answered HTTP $code.")
            val bytes = connection.inputStream.use { BoundedRead.readCapped(it, maxBytes) }
                ?: return UpdateResult.Failure("The answer was too large to read.")
            return UpdateResult.Success(bytes.toString(Charsets.UTF_8))
        } catch (e: SecurityException) {
            return UpdateResult.Failure("Network access is turned off for Uno Launcher.")
        } catch (e: UnknownHostException) {
            return UpdateResult.Failure("GitHub couldn't be reached.")
        } catch (e: SocketTimeoutException) {
            return UpdateResult.Failure("GitHub took too long to answer.")
        } catch (e: IOException) {
            return UpdateResult.Failure("The connection failed.")
        }
    }

    /** Streams the APK to [file] with a running size cap and returns its SHA-256, or null on any failure. */
    private fun downloadTo(url: String, file: File, maxBytes: Long): String? {
        try {
            val connection = open(url)
            val code = connection.responseCode
            if (code !in 200..299) return null
            val declared = connection.contentLengthLong
            if (declared > maxBytes) return null
            val sha = file.outputStream().use { out -> connection.inputStream.use { BoundedRead.copyCapped(it, out, maxBytes) } }
            if (sha == null) file.delete()
            return sha
        } catch (e: IOException) {
            file.delete(); return null
        } catch (e: SecurityException) {
            return null
        }
    }

    /** Opens [url], following redirects by hand so each hop must be https on GitHub or its asset hosts. */
    private fun open(url: String): HttpURLConnection {
        var uri = URI(url)
        var hops = 0
        while (true) {
            if (!UpdateSecurity.redirectAllowed(uri)) throw IOException("The update server moved somewhere that isn't GitHub.")
            val connection = uri.toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", UpdateSecurity.userAgent("updater"))
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            val code = connection.responseCode
            if (code !in setOf(301, 302, 303, 307, 308)) return connection
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            if (location == null || ++hops > 5) throw IOException("Too many redirects.")
            uri = uri.resolve(location)
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val RELEASES_URL = "https://api.github.com/repos/everettpenne/UnoLauncher/releases?per_page=20"
        const val MAX_APK_BYTES = 64 * 1024 * 1024
        const val MAX_TEXT_BYTES = 2 * 1024 * 1024
        const val MAX_SUMS_BYTES = 64 * 1024
    }
}

/** The Updates page inside Customize launcher. */
@Composable
internal fun UpdatesPanel(
    updates: UpdateState,
    onCheckNow: () -> Unit,
    onInstall: (String) -> Unit,
    onAutoUpdate: (Boolean) -> Unit,
) {
    SettingsSwitch("Auto-update", updates.autoUpdate, onAutoUpdate, "auto-update-switch")
    Text("Checks GitHub every few hours, downloads the new release, and asks Android to install it. Android always asks you to confirm the install.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onCheckNow, enabled = !updates.checking,
            modifier = Modifier.testTag("update-check-now")) {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Check for updates")
        }
        if (updates.checking) { Spacer(Modifier.width(10.dp)); LinearProgressIndicator(Modifier.weight(1f)) }
    }
    if (updates.checkedAt > 0L) Text("Last checked ${describeFeedAge(System.currentTimeMillis(), updates.checkedAt)}",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    updates.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    Text("Versions", style = MaterialTheme.typography.titleMedium)
    Text("You can install any version, including older ones. Android may clear saved layout data when an older version can't read the newer format.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    updates.releases.forEach { release ->
        val installed = release.tag == updates.installed
        val ready = release.tag == updates.readyTag
        val downloading = release.tag == updates.downloadingTag
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${release.name.ifBlank { release.tag }} · ${release.publishedAt}",
                    style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(release.tag, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                installed -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.DownloadDone, null, Modifier.size(16.dp), tint = IosGreen)
                    Spacer(Modifier.width(4.dp)); Text("Installed", color = IosGreen, style = MaterialTheme.typography.labelMedium)
                }
                downloading -> Text("Downloading…", style = MaterialTheme.typography.labelMedium)
                else -> TextButton(onClick = { onInstall(release.tag) },
                    modifier = Modifier.testTag("install-${release.tag}")) {
                    Text(if (ready) "Install" else "Download")
                }
            }
        }
    }
}
