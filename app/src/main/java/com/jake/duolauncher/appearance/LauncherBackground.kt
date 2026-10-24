package com.jake.duolauncher.appearance

import com.jake.duolauncher.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

private const val BACKGROUND_PREFS = "launcher_background"
private const val BACKGROUND_ENABLED = "photoEnabled"
private const val BACKGROUND_ID = "photoId"
private const val BACKGROUND_FILE = "launcher-background.jpg"
private const val MAX_BACKGROUND_EDGE = 2048
private const val BUILTIN_BACKGROUND = "builtinBackground"
private const val SYSTEM_SYNC = "systemWallpaperSync"

/** The launcher's own drawn backgrounds, offered beside a committed photo. */
enum class BuiltinBackground(val label: String) {
    DUNES("Uno dunes"),
    DEEP_DUNES("Deep dunes"),
    DUNE_GLASS("Dune glass"),
    DUNE_RANGE("Dune range"),
    DUNE_BLOOM("Dune bloom"),
    DUNE_FLOW("Dune flow"),
    MOLTEN("Molten metal"),
}

/** The built-in background currently selected, read by [drawLauncherBackground] without a context.
 * Kept in sync at every entry point that draws the wallpaper, so the Home screen, the Discover
 * frame and the live wallpaper all agree even though they draw from different code paths.
 */
internal object LauncherBackgroundRuntime {
    // Compose state, so a drawing that reads it is redrawn when the choice changes.
    var builtin: BuiltinBackground by androidx.compose.runtime.mutableStateOf(BuiltinBackground.DUNES)
}

internal fun builtinBackground(context: Context): BuiltinBackground = runCatching {
    BuiltinBackground.valueOf(
        launcherBackgroundPreferences(context).getString(BUILTIN_BACKGROUND, BuiltinBackground.DUNES.name)
            ?: BuiltinBackground.DUNES.name)
}.getOrDefault(BuiltinBackground.DUNES)

internal fun setBuiltinBackground(context: Context, value: BuiltinBackground) {
    launcherBackgroundPreferences(context).edit().putString(BUILTIN_BACKGROUND, value.name).apply()
    LauncherBackgroundRuntime.builtin = value
}

internal object LauncherBackgroundCache {
    @Volatile var bitmap: Bitmap? = null
    @Volatile var identity: String? = null
    val revision = mutableIntStateOf(0)
    private val listeners = mutableSetOf<() -> Unit>()

    fun changed(bitmap: Bitmap?, identity: String? = null) {
        this.bitmap = bitmap
        this.identity = identity
        revision.intValue++
        listeners.toList().forEach { it() }
    }
    fun listen(listener: () -> Unit) { listeners += listener }
    fun forget(listener: () -> Unit) { listeners -= listener }
}

internal fun launcherBackgroundFile(context: Context) = File(context.filesDir, BACKGROUND_FILE)
internal fun launcherBackgroundPreferences(context: Context) =
    context.getSharedPreferences(BACKGROUND_PREFS, Context.MODE_PRIVATE)
internal fun launcherBackgroundEnabled(context: Context) =
    launcherBackgroundPreferences(context).getBoolean(BACKGROUND_ENABLED, false) &&
        launcherBackgroundFile(context).isFile
internal fun launcherBackgroundIdentity(context: Context): String? {
    if (!launcherBackgroundEnabled(context)) return null
    return launcherBackgroundPreferences(context).getString(BACKGROUND_ID, null)
        ?: "legacy-${launcherBackgroundFile(context).lastModified()}"
}

internal fun loadLauncherBackground(context: Context): Bitmap? {
    if (!launcherBackgroundEnabled(context)) return null
    val file = launcherBackgroundFile(context)
    val identity = launcherBackgroundIdentity(context)
    LauncherBackgroundCache.bitmap?.let {
        if (!it.isRecycled && LauncherBackgroundCache.identity == identity) return it
    }
    return BitmapFactory.decodeFile(file.absolutePath)
}

internal fun cachedLauncherBackground(context: Context): Bitmap? {
    val identity = launcherBackgroundIdentity(context)
    return LauncherBackgroundCache.bitmap?.takeIf {
        identity != null && !it.isRecycled && LauncherBackgroundCache.identity == identity
    }
}

class LauncherBackgroundController(
    private val activity: ComponentActivity,
    private val onExternalResultChanged: (Boolean) -> Unit,
) : DefaultLifecycleObserver {
    var photoSelected by mutableStateOf(launcherBackgroundEnabled(activity))
        private set
    var builtin by mutableStateOf(builtinBackground(activity))
        private set
    /** Whether choosing a background also sets Android's home and lock screen wallpaper to it. Off until the person turns it on. */
    var systemSync by mutableStateOf(launcherBackgroundPreferences(activity).getBoolean(SYSTEM_SYNC, false))
        private set
    /** Whether the launcher is in dark appearance; the sheet keeps this current so a built-in night scene is the one sent. */
    var dark = false
    var loading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var successMessage by mutableStateOf<String?>(null)
        private set
    var pickerPending by mutableStateOf(false)
        private set
    var previewBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var previewPending by mutableStateOf(false)
        private set
    /** How the photo being previewed is fitted and toned; applied (baked into the saved picture) by [applyPreview]. */
    var adjust by mutableStateOf(WallpaperAdjust())
        private set
    /** A built-in scene being looked at before it is applied ([applyBuiltin]); null when none is. */
    var pendingBuiltin by mutableStateOf<BuiltinBackground?>(null)
        private set
    /** True while an adjusted photo is being made and saved. */
    var applying by mutableStateOf(false)
        private set
    private var generation = 0
    private var preview: StagedBackground? = null
    private val prefs = launcherBackgroundPreferences(activity)
    private val picker = activity.activityResultRegistry.register(
        "duo.background.pick", activity, ActivityResultContracts.PickVisualMedia()
    ) callback@{ uri ->
        if (!pickerPending) return@callback
        if (uri == null) clearPickerPending() else beginPreview(uri, pendingOperation())
    }

    init {
        activity.lifecycle.addObserver(this)
        LauncherBackgroundRuntime.builtin = builtin
        val previewPhase = previewPhase()
        val persistedPreviewFile = if (previewPhase == PHASE_READY) previewFile() else null
        cleanupStagedFiles(persistedPreviewFile)
        val pendingUri = runCatching { prefs.getString(PENDING_URI, null) }.getOrNull()
        pickerPending = runCatching { prefs.getBoolean(PICKER_PENDING, false) }.getOrDefault(false)
        val operation = pendingOperation()
        if (previewPhase != null) adjust = WallpaperAdjust.decode(runCatching { prefs.getString(PREVIEW_ADJUST, null) }.getOrNull())
        releaseStalePreviewGrant(if (previewPhase == PHASE_DECODING) operation else null)
        if (pickerPending && pendingUri != null && operation != null) {
            // Compatibility with controllers that were recreated after receiving a picker URI
            // but before separating picker and preview state.
            beginPreview(Uri.parse(pendingUri), operation)
        } else if (previewPhase == PHASE_DECODING) {
            pickerPending = false
            previewPending = true
            loading = true
            onExternalResultChanged(false)
            if (pendingUri != null && operation != null) decode(Uri.parse(pendingUri), operation)
            else failPreviewRestore("The pending photo selection could not be restored.")
        } else if (previewPhase == PHASE_READY) {
            pickerPending = false
            previewPending = true
            loading = true
            onExternalResultChanged(false)
            prefs.edit().remove(PENDING_URI).apply()
            if (operation != null && persistedPreviewFile?.isFile == true) {
                restorePreview(persistedPreviewFile, operation)
            } else failPreviewRestore("The pending photo preview could not be restored.")
        } else if (pickerPending) {
            onExternalResultChanged(true)
        } else onExternalResultChanged(false)
        if (photoSelected && LauncherBackgroundCache.bitmap == null) activity.lifecycleScope.launch {
            val startingRevision = LauncherBackgroundCache.revision.intValue
            val loaded = withContext(Dispatchers.IO) { loadLauncherBackground(activity.applicationContext) }
            if (loaded != null && startingRevision == LauncherBackgroundCache.revision.intValue &&
                launcherBackgroundEnabled(activity)) LauncherBackgroundCache.changed(loaded, launcherBackgroundIdentity(activity))
        }
    }

    fun choosePhoto() {
        pendingBuiltin = null
        adjust = WallpaperAdjust()
        prefs.edit().remove(PREVIEW_ADJUST).apply()
        discardPreview()
        releasePreviewGrant()
        cleanupStagedFiles()
        generation++; pickerPending = true; loading = false; errorMessage = null; successMessage = null
        prefs.edit().putBoolean(PICKER_PENDING, true).putString(PENDING_OPERATION, UUID.randomUUID().toString())
            .remove(PENDING_URI).remove(PREVIEW_PHASE).remove(PREVIEW_FILE).apply()
        onExternalResultChanged(true)
        try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        catch (error: Exception) {
            errorMessage = error.message ?: "The photo picker is unavailable."
            clearPickerPending()
        }
    }

    /** Drops a committed photo and reveals the currently selected built-in background. */
    fun reset() {
        pendingBuiltin = null
        clearPhoto()
        successMessage = "Using ${builtin.label}."
        if (systemSync) pushToSystem(null)
    }

    /**
     * Shows one of the launcher's own drawn backgrounds in the preview, without applying it: [applyBuiltin] makes it the background,
     * [cancelBuiltin] goes back to the current one. Choosing the scene already in use (with no photo over it) just clears the pending one.
     */
    fun chooseBuiltin(value: BuiltinBackground) {
        if (previewPending) cancelPreview()
        errorMessage = null; successMessage = null
        pendingBuiltin = if (value == builtin && !photoSelected) null else value
    }

    fun applyBuiltin() { pendingBuiltin?.let { pendingBuiltin = null; useBuiltin(it) } }
    fun cancelBuiltin() { pendingBuiltin = null }

    /** Makes one of the launcher's own drawn backgrounds the background, replacing any committed photo. */
    fun useBuiltin(value: BuiltinBackground) {
        clearPhoto()
        setBuiltinBackground(activity, value)
        builtin = value
        successMessage = "Using ${value.label}."
        if (systemSync) pushToSystem(null)
    }

    /** Turns "also set Android's home and lock screen" on or off. Turning it on sends the current background at once. */
    fun chooseSystemSync(on: Boolean) {
        prefs.edit().putBoolean(SYSTEM_SYNC, on).apply()
        systemSync = on
        if (on) pushToSystem(null)
    }

    /**
     * Sets Android's home and lock screen wallpaper to the current background: the committed photo (or [photo], just
     * committed), otherwise the chosen built-in scene drawn at screen size. Done off the main thread because drawing a
     * scene and handing it to the system both take a moment; the result is reported in the same message line as other
     * background changes. A failure leaves the previous system wallpaper in place and says so.
     */
    private fun pushToSystem(photo: Bitmap?) {
        val useDark = dark
        val kind = builtin
        val photoFile = launcherBackgroundFile(activity)
        val usePhoto = photo != null || (launcherBackgroundEnabled(activity) && photoFile.isFile)
        Thread {
            val result = if (!usePhoto) SystemWallpaperSync.push(activity, kind, useDark) else runCatching {
                val bitmap = photo ?: requireNotNull(BitmapFactory.decodeFile(photoFile.path)) { "The photo could not be read." }
                android.app.WallpaperManager.getInstance(activity).setBitmap(bitmap, null, true,
                    android.app.WallpaperManager.FLAG_SYSTEM or android.app.WallpaperManager.FLAG_LOCK)
                Unit
            }
            activity.runOnUiThread {
                result.onSuccess { successMessage = "Android home and lock screen set to match."; errorMessage = null }
                    .onFailure { errorMessage = "Could not set the Android wallpaper: ${it.message ?: "unknown error"}" }
            }
        }.start()
    }

    private fun clearPhoto() {
        generation++; pickerPending = false; loading = false
        discardPreview()
        releasePreviewGrant()
        // A Compose or wallpaper-service canvas may still be drawing the old bitmap.
        LauncherBackgroundCache.changed(null)
        launcherBackgroundFile(activity).delete()
        originalFile().delete()
        prefs.edit().remove(PHOTO_ADJUST).remove(PREVIEW_ADJUST).apply()
        prefs.edit().putBoolean(BACKGROUND_ENABLED, false).remove(BACKGROUND_ID).remove(PICKER_PENDING).remove(PENDING_URI)
            .remove(PENDING_OPERATION).remove(PREVIEW_PHASE).remove(PREVIEW_FILE).apply()
        photoSelected = false; errorMessage = null
        onExternalResultChanged(false)
        cleanupStagedFiles()
    }

    fun clearMessage() { errorMessage = null; successMessage = null }

    fun cancelPendingSelection() {
        if (!pickerPending) return
        generation++; loading = false; clearPickerPending()
    }

    /** Changes how the previewed photo is fitted and toned. Kept with the preview, so it survives the launcher being recreated. */
    fun updateAdjust(next: WallpaperAdjust) {
        if (!previewPending) return
        adjust = next.clamped()
        prefs.edit().putString(PREVIEW_ADJUST, adjust.encode()).apply()
    }

    /**
     * Opens the applied photo again for adjusting, from the original as it was chosen (kept beside the adjusted one), with the adjustments it
     * was applied with. A photo applied before adjustments existed has no original kept; then the applied picture itself is the starting point.
     */
    fun adjustCurrent() {
        if (!photoSelected || previewPending || loading || applying) return
        val kept = originalFile().takeIf { it.isFile }
        val source = kept ?: launcherBackgroundFile(activity).takeIf { it.isFile } ?: return
        val saved = if (kept != null) WallpaperAdjust.decode(runCatching { prefs.getString(PHOTO_ADJUST, null) }.getOrNull()) else WallpaperAdjust()
        pendingBuiltin = null; errorMessage = null; successMessage = null
        generation++
        cleanupStagedFiles()
        val operation = UUID.randomUUID().toString()
        val staged = File(launcherBackgroundFile(activity).parentFile, "$BACKGROUND_FILE.$operation.${UUID.randomUUID()}.tmp")
        if (runCatching { source.copyTo(staged, overwrite = true) }.isFailure) { errorMessage = "The photo could not be opened for adjusting."; return }
        val persisted = prefs.edit().putString(PENDING_OPERATION, operation).putString(PREVIEW_PHASE, PHASE_READY).putString(PREVIEW_FILE, staged.name)
            .putString(PREVIEW_ADJUST, saved.encode()).remove(PENDING_URI).remove(PICKER_PENDING).commit()
        if (!persisted) { staged.delete(); errorMessage = "The photo could not be opened for adjusting."; return }
        adjust = saved
        previewPending = true
        loading = true
        restorePreview(staged, operation)
    }

    fun applyPreview() {
        val staged = preview ?: return
        if (!previewPending || pendingOperation() != staged.operation ||
            previewFile()?.absolutePath != staged.file.absolutePath) return
        val chosen = adjust
        // Keep the photo as chosen, so it can be adjusted again later from the original rather than from an already-adjusted copy.
        runCatching { staged.file.copyTo(originalFile(), overwrite = true) }
        if (chosen.isNeutral) { commitPreview(staged, staged.bitmap, chosen); return }
        // An adjusted photo is made off the main thread: cropping, toning and blurring a large picture takes a moment.
        val token = generation
        applying = true; loading = true
        val aspect = activity.resources.displayMetrics.let { m -> minOf(m.widthPixels, m.heightPixels).toFloat() / maxOf(m.widthPixels, m.heightPixels) }
        activity.lifecycleScope.launch {
            val baked = runCatching {
                withContext(Dispatchers.Default) {
                    val out = WallpaperAdjustMath.bake(staged.bitmap, aspect, chosen, MAX_BACKGROUND_EDGE)
                    FileOutputStream(staged.file).use { o -> require(out.compress(Bitmap.CompressFormat.JPEG, 92, o)) { "The adjusted photo could not be saved." }; o.fd.sync() }
                    out
                }
            }
            applying = false
            if (token != generation || preview !== staged) { loading = false; return@launch }
            baked.onSuccess { commitPreview(staged, it, chosen) }.onFailure { loading = false; errorMessage = it.message ?: "The adjusted photo could not be made." }
        }
    }

    private fun commitPreview(staged: StagedBackground, picture: Bitmap, chosen: WallpaperAdjust) {
        runCatching { staged.commit(launcherBackgroundFile(activity)) }
            .onSuccess {
                releasePreviewGrant(staged.operation)
                prefs.edit().putBoolean(BACKGROUND_ENABLED, true).putString(BACKGROUND_ID, staged.operation)
                    .putString(PHOTO_ADJUST, chosen.encode()).remove(PREVIEW_ADJUST)
                    .remove(PENDING_URI).remove(PENDING_OPERATION).remove(PREVIEW_PHASE).remove(PREVIEW_FILE).apply()
                preview = null
                previewBitmap = null
                previewPending = false
                loading = false
                adjust = WallpaperAdjust()
                LauncherBackgroundCache.changed(picture, staged.operation)
                photoSelected = true
                errorMessage = null
                successMessage = "Launcher background updated."
                if (systemSync) pushToSystem(picture)
                cleanupStagedFiles()
            }
            .onFailure { loading = false; errorMessage = it.message ?: "That photo could not be saved." }
    }

    fun cancelPreview() {
        if (!previewPending) return
        generation++
        loading = false; applying = false
        adjust = WallpaperAdjust()
        prefs.edit().remove(PREVIEW_ADJUST).apply()
        discardPreview()
        clearPreviewPersistence()
        errorMessage = null
        successMessage = null
        cleanupStagedFiles()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        generation++
        preview?.releaseBitmap()
        preview = null
        previewBitmap = null
        activity.lifecycle.removeObserver(this)
    }

    private fun beginPreview(uri: Uri, operation: String?) {
        if (operation == null) {
            errorMessage = "The pending photo selection could not be restored."
            clearPickerPending()
            return
        }
        pickerPending = false
        previewPending = true
        loading = true
        val statePersisted = prefs.edit().remove(PICKER_PENDING).putString(PENDING_URI, uri.toString())
            .putString(PREVIEW_PHASE, PHASE_DECODING).remove(PREVIEW_FILE).commit()
        if (statePersisted && pendingOperation() == operation && !hasPersistedReadAccess(uri)) {
            // Record ownership before requesting the grant so process death cannot leave an
            // untracked permission. Grants that predate this operation remain owner-managed.
            val ownershipRecorded = prefs.edit().putString(PREVIEW_GRANT_URI, uri.toString())
                .putString(PREVIEW_GRANT_OPERATION, operation).commit()
            if (ownershipRecorded && pendingOperation() == operation) {
                val granted = runCatching {
                    activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }.isSuccess
                if (!granted) forgetPreviewGrant(operation)
            }
        }
        onExternalResultChanged(false)
        decode(uri, operation)
    }

    private fun decode(uri: Uri, operation: String?) {
        if (operation == null) { failPreviewRestore("The pending photo selection could not be restored."); return }
        val token = generation
        activity.lifecycleScope.launch {
            // withContext has prompt cancellation: its IO block can finish creating the file
            // and bitmap, then throw cancellation instead of returning that value to us.
            // Retain ownership across that handoff so the abandoned result can be reclaimed.
            val stagedAcrossDispatch = AtomicReference<StagedBackground?>(null)
            val result = try {
                val staged = withContext(Dispatchers.IO) {
                    stage(uri, operation).also(stagedAcrossDispatch::set)
                }
                stagedAcrossDispatch.set(null)
                Result.success(staged)
            } catch (cancel: CancellationException) {
                stagedAcrossDispatch.getAndSet(null)?.discard()
                // A recreated controller owns the persisted operation. The old activity must
                // not turn lifecycle cancellation into a failure that clears that operation.
                throw cancel
            } catch (error: Throwable) {
                Result.failure(error)
            }
            if (token != generation || !previewPending || pendingOperation() != operation) {
                result.getOrNull()?.discard()
                return@launch
            }
            val staged = result.getOrElse {
                failPreviewRestore(it.message ?: "That photo could not be used.")
                return@launch
            }
            val persisted = prefs.edit().putString(PREVIEW_PHASE, PHASE_READY)
                .putString(PREVIEW_FILE, staged.file.name).remove(PENDING_URI).commit()
            if (!persisted || token != generation || !previewPending || pendingOperation() != operation) {
                staged.discard()
                if (!persisted) failPreviewRestore("That photo preview could not be saved.")
                return@launch
            }
            releasePreviewGrant(operation)
            preview = staged
            previewBitmap = staged.publish()
            loading = false
        }
    }

    private fun restorePreview(file: File, operation: String) {
        val token = generation
        activity.lifecycleScope.launch {
            val bitmapAcrossDispatch = AtomicReference<Bitmap?>(null)
            val bitmap = try {
                val restored = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(file.absolutePath).also(bitmapAcrossDispatch::set)
                }
                bitmapAcrossDispatch.set(null)
                restored
            } catch (cancel: CancellationException) {
                bitmapAcrossDispatch.getAndSet(null)
                    ?.takeUnless { it === LauncherBackgroundCache.bitmap || it.isRecycled }?.recycle()
                throw cancel
            } catch (_: Throwable) {
                null
            }
            if (token != generation || !previewPending || pendingOperation() != operation ||
                previewFile()?.absolutePath != file.absolutePath) {
                bitmap?.takeUnless { it === LauncherBackgroundCache.bitmap || it.isRecycled }?.recycle()
                return@launch
            }
            if (bitmap == null) {
                file.delete()
                failPreviewRestore("The pending photo preview could not be restored.")
                return@launch
            }
            val staged = StagedBackground(bitmap, file, operation)
            preview = staged
            previewBitmap = staged.publish()
            loading = false
        }
    }

    private fun clearPickerPending() {
        pickerPending = false
        if (!previewPending) {
            releasePreviewGrant(pendingOperation())
            prefs.edit().remove(PICKER_PENDING).remove(PENDING_URI).remove(PENDING_OPERATION)
                .remove(PREVIEW_PHASE).remove(PREVIEW_FILE).apply()
        } else prefs.edit().remove(PICKER_PENDING).apply()
        onExternalResultChanged(false)
    }

    private fun failPreviewRestore(message: String) {
        loading = false
        errorMessage = message
        discardPreview()
        clearPreviewPersistence()
        cleanupStagedFiles()
    }

    private fun discardPreview() {
        preview?.discard()
        preview = null
        previewBitmap = null
        previewPending = false
    }

    private fun clearPreviewPersistence() {
        releasePreviewGrant(pendingOperation())
        prefs.edit().remove(PICKER_PENDING).remove(PENDING_URI).remove(PENDING_OPERATION)
            .remove(PREVIEW_PHASE).remove(PREVIEW_FILE).apply()
        pickerPending = false
        previewPending = false
        onExternalResultChanged(false)
    }

    private fun pendingOperation() = runCatching { prefs.getString(PENDING_OPERATION, null) }.getOrNull()
    private fun previewPhase() = runCatching { prefs.getString(PREVIEW_PHASE, null) }.getOrNull()
    private fun hasPersistedReadAccess(uri: Uri): Boolean = runCatching {
        activity.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
    }.getOrDefault(false)

    private fun releaseStalePreviewGrant(activeOperation: String?) {
        val recordedOperation = runCatching { prefs.getString(PREVIEW_GRANT_OPERATION, null) }.getOrNull()
        if (recordedOperation == null || recordedOperation != activeOperation) releasePreviewGrant(recordedOperation)
    }

    private fun releasePreviewGrant(operation: String? = null) {
        val recordedOperation = runCatching { prefs.getString(PREVIEW_GRANT_OPERATION, null) }.getOrNull()
        if (operation != null && recordedOperation != operation) return
        val uri = runCatching { prefs.getString(PREVIEW_GRANT_URI, null) }.getOrNull()?.let(Uri::parse)
        if (uri != null) runCatching {
            activity.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        forgetPreviewGrant(recordedOperation)
    }

    private fun forgetPreviewGrant(operation: String?) {
        val recordedOperation = runCatching { prefs.getString(PREVIEW_GRANT_OPERATION, null) }.getOrNull()
        if (operation != null && recordedOperation != operation) return
        prefs.edit().remove(PREVIEW_GRANT_URI).remove(PREVIEW_GRANT_OPERATION).apply()
    }

    /** The photo as chosen, before adjusting, kept beside the applied one so it can be adjusted again. */
    private fun originalFile(): File = File(launcherBackgroundFile(activity).parentFile, ORIGINAL_FILE)

    private fun previewFile(): File? {
        val name = runCatching { prefs.getString(PREVIEW_FILE, null) }.getOrNull() ?: return null
        if (File(name).name != name || !name.startsWith("$BACKGROUND_FILE.") || !name.endsWith(".tmp")) return null
        return File(launcherBackgroundFile(activity).parentFile, name)
    }

    private fun stage(uri: Uri, operation: String): StagedBackground {
        val source = ImageDecoder.createSource(activity.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val width = info.size.width; val height = info.size.height
            require(width > 0 && height > 0) { "The selected image has no usable pixels." }
            val scale = minOf(1f, MAX_BACKGROUND_EDGE.toFloat() / maxOf(width, height))
            decoder.setTargetSize(maxOf(1, (width * scale).toInt()), maxOf(1, (height * scale).toInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
        if (bitmap.byteCount > MAX_BACKGROUND_EDGE * MAX_BACKGROUND_EDGE * 4) {
            bitmap.recycle()
            throw IllegalArgumentException("The selected image is too large.")
        }
        val temporary = File(launcherBackgroundFile(activity).parentFile,
            "$BACKGROUND_FILE.$operation.${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                require(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)) { "The selected image could not be saved." }
                output.fd.sync()
            }
        } catch (error: Exception) {
            temporary.delete()
            bitmap.recycle()
            throw error
        }
        return StagedBackground(bitmap, temporary, operation)
    }

    private class StagedBackground(val bitmap: Bitmap, val file: File, val operation: String) {
        private var published = false

        fun publish(): Bitmap {
            published = true
            return bitmap
        }

        fun commit(destination: File) {
            try { Files.move(file.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
            catch (_: Exception) { Files.move(file.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        }
        fun discard() { file.delete(); releaseBitmap() }
        fun releaseBitmap() {
            // Once Compose has observed this bitmap, RenderThread may retain it beyond the
            // state update that closes preview. Drop controller references and let GC release
            // it after every renderer is finished. Unpublished decode results are controller-owned.
            if (!published && bitmap !== LauncherBackgroundCache.bitmap && !bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun cleanupStagedFiles(keep: File? = null) {
        launcherBackgroundFile(activity).parentFile?.listFiles { file ->
            file.name.startsWith("$BACKGROUND_FILE.") && file.name.endsWith(".tmp")
        }?.filterNot { keep != null && it.absolutePath == keep.absolutePath }?.forEach(File::delete)
    }

    companion object {
        private const val PICKER_PENDING = "pickerPending"
        private const val PENDING_URI = "pendingUri"
        private const val PENDING_OPERATION = "pendingOperation"
        private const val PREVIEW_PHASE = "previewPhase"
        private const val PREVIEW_FILE = "previewFile"
        private const val PREVIEW_GRANT_URI = "previewGrantUri"
        private const val PREVIEW_GRANT_OPERATION = "previewGrantOperation"
        private const val PHASE_DECODING = "decoding"
        private const val PHASE_READY = "ready"
        private const val PREVIEW_ADJUST = "previewAdjust"
        private const val PHOTO_ADJUST = "photoAdjust"
        private const val ORIGINAL_FILE = "launcher-background-original.jpg"
    }
}
