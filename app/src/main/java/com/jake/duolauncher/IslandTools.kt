package com.jake.duolauncher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/** The island's long-press tools: a timer, a stopwatch and the flashlight.
 *
 * The timer is an alarm-clock alarm ([AlarmManager.setAlarmClock]) when the user has allowed exact alarms,
 * and an inexact one otherwise, so it fires even if the launcher isn't on screen; a trigger inside the
 * app covers the case where the launcher is alive either way. When it fires, [TimerReceiver] rings the default alarm sound and vibrates for up to 30 seconds, until
 * the island is tapped. If Android had killed the whole process by then there is nobody to ring, so the
 * saved end time is checked again on the next start and shown as done.
 */
internal object IslandTools {
    private const val PREFS = "island_tools"
    private const val ALARM_ACTION = "com.jake.duolauncher.TIMER_DONE"
    private const val RING_LIMIT_MS = 30_000L

    var timerEndAt by mutableLongStateOf(0L); private set
    var timerTotal by mutableLongStateOf(0L); private set
    var ringing by mutableStateOf(false); private set
    var swRunning by mutableStateOf(false); private set
    private var swBase by mutableLongStateOf(0L)
    private var swStartedAt by mutableLongStateOf(0L)
    var toolsOpen by mutableStateOf(false)
    /** Bumped by every tool button so the island's idle timeout restarts while it is being used. */
    var interactions by mutableIntStateOf(0); private set

    private var ringtone: Ringtone? = null
    private val main = Handler(Looper.getMainLooper())
    private var loaded = false

    val timerActive: Boolean get() = timerEndAt > 0L

    /** Whether the timer can fire to the second with the launcher closed (the user allowed exact alarms). */
    fun exactAlarmsAllowed(context: Context): Boolean =
        runCatching { context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }.getOrDefault(false)
    fun elapsedMs(nowMs: Long): Long = swBase + if (swRunning) (nowMs - swStartedAt).coerceAtLeast(0L) else 0L
    val stopwatchStarted: Boolean get() = swRunning || swBase > 0L
    fun touch() { interactions++ }

    fun load(context: Context, nowMs: Long = System.currentTimeMillis()) {
        if (loaded) return
        loaded = true
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        timerEndAt = p.getLong("timerEnd", 0L); timerTotal = p.getLong("timerTotal", 0L)
        swBase = p.getLong("swBase", 0L); swStartedAt = p.getLong("swStart", 0L); swRunning = p.getBoolean("swRunning", false)
        // The alarm fired while nobody was around to ring: say so, quietly.
        if (timerEndAt in 1..nowMs) { ringing = true }
    }

    private fun save(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("timerEnd", timerEndAt).putLong("timerTotal", timerTotal)
            .putLong("swBase", swBase).putLong("swStart", swStartedAt).putBoolean("swRunning", swRunning).apply()
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(context.applicationContext, 0,
        Intent(ALARM_ACTION).setClass(context.applicationContext, TimerReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun startTimer(context: Context, durationMs: Long, nowMs: Long = System.currentTimeMillis()) {
        stopRinging(context)
        timerTotal = durationMs; timerEndAt = nowMs + durationMs
        val alarms = context.getSystemService(AlarmManager::class.java)
        val show = PendingIntent.getActivity(context.applicationContext, 0,
            Intent(context.applicationContext, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        // Exact when the user has allowed exact alarms; otherwise an inexact alarm that is still delivered
        // in Doze, usually within a minute or so. The in-process trigger below covers a live launcher.
        runCatching {
            if (alarms.canScheduleExactAlarms()) alarms.setAlarmClock(AlarmManager.AlarmClockInfo(timerEndAt, show), alarmIntent(context))
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timerEndAt, alarmIntent(context))
        }.onFailure { android.util.Log.w("IslandTools", "Could not set the timer alarm; ringing from the app instead", it) }
        // A second, in-process trigger: if the system alarm can't be set (or is late), the timer still ends
        // while the launcher is alive. onAlarm ignores whichever of the two arrives second.
        main.removeCallbacks(fallbackRing)
        fallbackContext = context.applicationContext
        main.postDelayed(fallbackRing, durationMs)
        save(context); touch()
    }

    private var fallbackContext: Context? = null
    private val fallbackRing = Runnable { fallbackContext?.let { onAlarm(it) } }

    fun cancelTimer(context: Context) {
        main.removeCallbacks(fallbackRing)
        runCatching { context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context)) }
        timerEndAt = 0L; timerTotal = 0L; stopRinging(context); save(context); touch()
    }

    /** The alarm fired: ring and vibrate. Called from [TimerReceiver] on the main thread. */
    fun onAlarm(context: Context) {
        // The alarm can wake a cold process before MainActivity has loaded anything.
        load(context)
        if (ringtone != null || timerEndAt == 0L) return
        main.removeCallbacks(fallbackRing)
        ringing = true
        val app = context.applicationContext
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(app, uri)?.apply {
                audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                isLooping = true; play()
            }
        }
        runCatching {
            app.getSystemService(Vibrator::class.java).vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 400), 0))
        }
        main.removeCallbacksAndMessages(null)
        main.postDelayed({ stopRinging(app) }, RING_LIMIT_MS)
    }

    /** Silences the alarm and clears a finished timer. */
    fun stopRinging(context: Context) {
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching { context.applicationContext.getSystemService(Vibrator::class.java).cancel() }
        main.removeCallbacksAndMessages(null)
        if (ringing) { ringing = false; timerEndAt = 0L; timerTotal = 0L; save(context) }
    }

    fun toggleStopwatch(context: Context, nowMs: Long = System.currentTimeMillis()) {
        if (swRunning) { swBase = elapsedMs(nowMs); swRunning = false }
        else { swStartedAt = nowMs; swRunning = true }
        save(context); touch()
    }

    fun resetStopwatch(context: Context) { swBase = 0L; swRunning = false; swStartedAt = 0L; save(context); touch() }
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) { IslandTools.onAlarm(context) }
}

/** The flashlight as the island shares it. It owns no thread of its own: [rememberTorch] gives it a worker
 * only while the tools face is showing, and takes it away again afterwards.
 */
@Stable
internal class TorchState(private val context: Context) {
    var on by mutableStateOf(false); private set
    var available by mutableStateOf(false); private set
    private var id: String? = null
    private var handler: Handler? = null
    private val cameras = context.getSystemService(CameraManager::class.java)
    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { if (cameraId == id) { on = enabled; available = true } }
        override fun onTorchModeUnavailable(cameraId: String) { if (cameraId == id) available = false }
    }
    private fun pick() {
        id = runCatching {
            ControlLogic.pickTorchCamera(cameras.cameraIdList.map { cam ->
                val c = cameras.getCameraCharacteristics(cam)
                TorchCamera(cam, c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK)
            })
        }.getOrNull()
        available = id != null
    }
    fun toggle() {
        val cam = id ?: return
        val next = !on
        handler?.post { try { cameras.setTorchMode(cam, next) } catch (_: CameraAccessException) { available = false } catch (_: IllegalArgumentException) { available = false } }
    }
    internal fun attach(worker: Handler) { handler = worker; worker.post { pick(); runCatching { cameras.registerTorchCallback(callback, worker) } } }
    internal fun detach() { val h = handler; handler = null; h?.post { runCatching { cameras.unregisterTorchCallback(callback) } } }
}

@Composable
internal fun rememberTorch(active: Boolean): TorchState {
    val context = LocalContext.current
    val torch = remember(context) { TorchState(context) }
    DisposableEffect(torch, active) {
        if (!active) return@DisposableEffect onDispose { }
        val worker = HandlerThread("island-torch").apply { start() }
        torch.attach(Handler(worker.looper))
        onDispose { torch.detach(); worker.quitSafely() }
    }
    return torch
}
