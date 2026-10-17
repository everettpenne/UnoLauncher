package com.jake.duolauncher

import com.jake.duolauncher.island.*

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Pictures of the island as a Pixel 10a shows it (1080 x 2400 with a small round camera at the top centre), taken in real time after the
 * morph has settled, for looking at. They are written to the app's files directory as island-<state>.png; the test asserts nothing.
 */
@RunWith(AndroidJUnit4::class)
class IslandShotsInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val state = IslandState()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var session: MediaSession? = null

    @After fun reset() {
        NotificationFeed.liveUpdates = emptyList(); NotificationFeed.ongoingCall = null; NotificationFeed.nowPlaying = null
        IslandTools.toolsOpen = false; session?.release()
    }

    private fun show() {
        compose.setContent {
            DuoTheme(false) {
                DynamicIsland(state, glass = null, deviceStatus = DeviceStatus(battery = 76, charging = true), feedHeadline = "Dune: Part Three trailer lands with the first look at the sandworm riding",
                    environmentOverride = IslandEnvironment(PxRect(515f, 40f, 565f, 90f), 1080f, 80f, screenHeight = 2400f),
                    onSearch = {}, onOpenFeed = {}, onCustomize = {})
            }
        }
        Thread.sleep(2500); compose.waitForIdle()
    }

    /** Opens the island with the clock held still (the island tucks itself away after a few seconds, and waiting would run that out), lets it finish growing. */
    private fun openShot(name: String, open: () -> Unit = { state.toggle() }) {
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { open() }
        compose.mainClock.advanceTimeBy(1_500)
        Thread.sleep(1_200)
        takeShot(name)
        compose.mainClock.autoAdvance = true
    }

    private fun shot(name: String) {
        Thread.sleep(2200); compose.waitForIdle()
        takeShot(name)
    }

    private fun takeShot(name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val crop = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, minOf(760, bitmap.height))
        File(context.filesDir, "island-$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun states() {
        show()
        shot("1-idle")
        openShot("2-open")
        compose.runOnIdle { state.collapse() }
        val art = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).also { Canvas(it).drawColor(0xFFE2582B.toInt()) }
        val s = MediaSession(context, "uno-island-shot").also { session = it }
        s.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Night Drive").putString(MediaMetadata.METADATA_KEY_ARTIST, "The Examples")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, 240_000).putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art).build())
        s.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 83_000, 1f).setActions(PlaybackState.ACTION_PLAY_PAUSE).build())
        s.isActive = true
        compose.runOnIdle { NotificationFeed.nowPlaying = NowPlaying("Night Drive", "The Examples", art, true, s.controller); state.setPlaying(System.currentTimeMillis(), true) }
        shot("3-music-pill")
        openShot("4-music-open")
        compose.runOnIdle { state.collapse() }
        compose.runOnIdle { NotificationFeed.nowPlaying = null; state.endPlayback(); state.setPlaying(System.currentTimeMillis(), false) }
        compose.runOnIdle { IslandTools.toolsOpen = true; state.expanded = true }
        shot("5-tools")
        compose.runOnIdle { state.collapse() }
        compose.runOnIdle { state.showPeekCard(PeekNotification("com.example.chat", "Messages", "Ada Lovelace", "Are we still on for lunch? I found the place with the good noodles.", null, null, 6)) }
        shot("6-card")
    }

    @Test fun newThings() {
        show()
        val pi = PendingIntent.getBroadcast(context, 0, Intent("uno.shot").setPackage(context.packageName), PendingIntent.FLAG_MUTABLE)
        val reply = RemoteInput.Builder("k").setLabel("Reply to Ada").build()
        openShot("7-card-actions") { state.showPeekCard(PeekNotification("com.example.chat", "Messages", "Ada Lovelace", "Are we still on for lunch?", null, null, 20, 0xFF30D158.toInt(),
            listOf(PeekAction("Reply", pi, reply), PeekAction("Mark as read", pi), PeekAction("Mute", pi)))) }
        compose.runOnIdle { state.collapse() }
        compose.runOnIdle { state.cameraActive = true; state.micActive = true }
        openShot("8-privacy") { state.toggle() }
        compose.runOnIdle { state.collapse(); state.cameraActive = false; state.micActive = false }
        compose.waitForIdle(); Thread.sleep(1_500)
        // A bubble leaving the pill, caught part-way: the call and the timer are on the pill, the music is the bubble.
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            NotificationFeed.ongoingCall = OngoingCall("Ada Lovelace", "com.example.phone", System.currentTimeMillis() - 83_000, null)
            IslandTools.startTimer(context, 5 * 60_000L)
            state.setPlaying(System.currentTimeMillis(), true)
        }
        compose.mainClock.advanceTimeBy(60)
        Thread.sleep(700)
        takeShot("9-bubble-early")
        compose.mainClock.advanceTimeBy(70)
        Thread.sleep(700)
        takeShot("10-bubble-mid")
        compose.mainClock.advanceTimeBy(1_200)
        Thread.sleep(700)
        takeShot("11-bubble-settled")
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { IslandTools.cancelTimer(context) }
    }

    @Test fun peekHoldRecentAndNavigation() {
        show()
        val pi = PendingIntent.getBroadcast(context, 0, Intent("uno.shot").setPackage(context.packageName), PendingIntent.FLAG_MUTABLE)
        val reply = RemoteInput.Builder("k").setLabel("Reply to Ada").build()
        // A pull that has been let go: the peek.
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.peeked = true }
        compose.mainClock.advanceTimeBy(2_500); Thread.sleep(900); takeShot("12-peek")
        compose.runOnIdle { state.collapse() }
        compose.mainClock.advanceTimeBy(1_500)
        // A notification that arrived a little while ago, held on the open island.
        compose.runOnIdle {
            state.showPeekCard(PeekNotification("com.example.chat", "Messages", "Ada Lovelace", "Are we still on for lunch? I found the noodle place.", null, null, 6, 0xFF30D158.toInt(),
                listOf(PeekAction("Reply", pi, reply), PeekAction("Mark as read", pi))))
            state.collapse()
        }
        compose.mainClock.advanceTimeBy(1_500)
        Thread.sleep(500); takeShot("13-recent-dot")
        compose.runOnIdle { state.peeked = true }
        compose.mainClock.advanceTimeBy(2_500); Thread.sleep(900); takeShot("14-peek-recent")
        compose.runOnIdle { state.toggle() }
        compose.mainClock.advanceTimeBy(1_200); Thread.sleep(700); takeShot("15-open-recent")
        compose.runOnIdle { state.clearRecent(); state.collapse() }
        compose.mainClock.advanceTimeBy(1_500)
        // Navigation, set large.
        val arrow = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888).also { b ->
            val c = Canvas(b); val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); style = android.graphics.Paint.Style.STROKE; strokeWidth = 30f; strokeCap = android.graphics.Paint.Cap.ROUND }
            c.drawLine(60f, 200f, 60f, 90f, paint); c.drawLine(60f, 90f, 160f, 90f, paint); c.drawLine(120f, 50f, 160f, 90f, paint); c.drawLine(120f, 130f, 160f, 90f, paint)
        }
        compose.runOnIdle {
            NotificationFeed.liveUpdates = listOf(LiveUpdate("nav", "app.organicmaps", "Organic Maps", "300 m", "Turn right onto Rue de Rivoli", null, 0, 0, false, null, false,
                null, 0xFF30D158.toInt(), System.currentTimeMillis(), null, arrow, ActivitySource.NAVIGATION))
        }
        compose.mainClock.advanceTimeBy(800); Thread.sleep(600); takeShot("16-nav-pill")
        compose.runOnIdle { state.toggle() }
        compose.mainClock.advanceTimeBy(1_200); Thread.sleep(700); takeShot("17-nav-open")
        compose.runOnIdle { state.collapse() }
        compose.mainClock.advanceTimeBy(1_500)
        compose.runOnIdle { state.showPeekCard(PeekNotification("app.organicmaps", "Organic Maps", "300 m", "Turn right onto Rue de Rivoli", arrow, null, 9, 0xFF30D158.toInt(), navigation = true)) }
        compose.mainClock.advanceTimeBy(1_200); Thread.sleep(700); takeShot("18-nav-cue")
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { NotificationFeed.liveUpdates = emptyList() }
    }

    @Test fun pullFrames() {
        show()
        compose.mainClock.autoAdvance = false
        for ((i, f) in listOf(.05f, .12f, .2f, .3f, .4f, .55f, .8f).withIndex()) {
            compose.runOnIdle { state.pullFraction = f }
            compose.mainClock.advanceTimeBy(100); Thread.sleep(500)
            takeShot("20-pull-$i")
        }
        compose.runOnIdle { state.pullFraction = null; state.collapse() }
        compose.mainClock.autoAdvance = true
    }
}
