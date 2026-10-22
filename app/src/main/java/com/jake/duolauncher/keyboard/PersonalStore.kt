package com.jake.duolauncher.keyboard

import android.content.Context
import android.os.UserManager
import android.util.AtomicFile
import java.io.File
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Where the keyboard keeps what it has learned, the clips you pinned and your recent emoji. This is the one keyboard file allowed to
 * write anything (`KeyboardPrivacyTest` holds every other to that), and it writes only to the app's no-backup storage: never to shared
 * storage, never into a backup or a settings export, and nothing here is ever sent anywhere. "Forget everything" in Settings deletes it.
 *
 * The keyboard and the launcher's settings run in one process, so both see the same [model], [clips] and [emoji]. Files are read on a
 * background thread on first use and written a few seconds after a change (and when the keyboard closes), atomically, so a crash never
 * leaves half a file. Before the phone is first unlocked its storage is locked, so nothing is read or written until then.
 */
internal object PersonalStore {
    val model = PersonalModel()
    val clips = ClipHistory { System.currentTimeMillis() }
    val emoji = EmojiRecents()
    @Volatile var loaded = false; private set

    private var dir: File? = null
    private val io = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "uno-keyboard-store").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }
    private var pending: ScheduledFuture<*>? = null

    private fun unlocked(context: Context) = runCatching { context.getSystemService(UserManager::class.java)?.isUserUnlocked != false }.getOrDefault(false)

    /** Reads the files once, in the background; [then] runs on that thread when they are in. */
    fun load(context: Context, then: () -> Unit = {}) {
        if (loaded) { then(); return }
        val app = context.applicationContext
        io.execute {
            if (!loaded && unlocked(app)) {
                val d = File(app.noBackupFilesDir, "keyboard").apply { mkdirs() }
                dir = d
                read(File(d, MODEL))?.let(model::decode)
                read(File(d, CLIPS))?.let { text -> clips.restorePinned(decodeClips(text)) }
                read(File(d, EMOJI))?.let { text -> emoji.restore(text.split(' ').filter { it.isNotBlank() }) }
                loaded = true
            }
            then()
        }
    }

    /** Writes the changes a few seconds from now, gathering a burst of typing into one write. */
    fun saveSoon() {
        synchronized(this) {
            if (pending?.isDone == false) return
            pending = io.schedule({ writeAll() }, 4, TimeUnit.SECONDS)
        }
    }

    /** Writes now (in the background): the keyboard is closing. */
    fun saveNow() { io.execute { writeAll() } }

    /** Deletes everything learned, the pinned clips and the recent emoji, from memory and from the phone. */
    fun forgetEverything() {
        model.clear(); emoji.clear(); clips.restorePinned(emptyList()); clips.clearRecent()
        io.execute { dir?.listFiles()?.forEach { it.delete() } }
    }

    private fun writeAll() {
        val d = dir ?: return
        if (!loaded) return
        if (model.changed) write(File(d, MODEL), model.encode())
        write(File(d, CLIPS), encodeClips(clips.pinned()))
        write(File(d, EMOJI), emoji.list().joinToString(" "))
    }

    private fun read(file: File): String? = runCatching { if (file.isFile) AtomicFile(file).readFully().toString(Charsets.UTF_8) else null }.getOrNull()

    private fun write(file: File, text: String) {
        val f = AtomicFile(file)
        val out = runCatching { f.startWrite() }.getOrNull() ?: return
        runCatching { out.write(text.toByteArray(Charsets.UTF_8)); f.finishWrite(out) }.onFailure { f.failWrite(out) }
    }

    /** Pinned clips, one per line: the text in Base64 (it may hold tabs and line breaks), a tab, and when it was copied. */
    internal fun encodeClips(list: List<Clip>): String =
        list.joinToString("\n") { Base64.getEncoder().encodeToString(it.text.toByteArray(Charsets.UTF_8)) + "\t" + it.at }

    internal fun decodeClips(text: String): List<Clip> = text.lineSequence().mapNotNull { line ->
        val f = line.split('\t')
        runCatching { Clip(String(Base64.getDecoder().decode(f[0]), Charsets.UTF_8), f.getOrNull(1)?.toLongOrNull() ?: 0L, pinned = true) }.getOrNull()
    }.toList()

    private const val MODEL = "personal.txt"
    private const val CLIPS = "pinned-clips.txt"
    private const val EMOJI = "recent-emoji.txt"
}
