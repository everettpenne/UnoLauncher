package com.jake.duolauncher

import com.jake.duolauncher.island.*

import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Situations where several island effects meet (a video call, music with a timer, an event during a call), checked for what stays
 * visible, with pictures kept for looking at.
 */
@RunWith(AndroidJUnit4::class)
class IslandScenarioInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val state = IslandState()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var session: MediaSession? = null

    @After fun reset() {
        NotificationFeed.liveUpdates = emptyList(); NotificationFeed.ongoingCall = null; NotificationFeed.nowPlaying = null
        session?.release()
        IslandTools.cancelTimer(context)
        IslandDebug.reset()
    }

    private fun show() {
        compose.setContent {
            DuoTheme(false) {
                DynamicIsland(state, glass = null, deviceStatus = DeviceStatus(battery = 80), feedHeadline = null,
                    environmentOverride = IslandEnvironment(PxRect(509f, 40f, 571f, 102f), 1080f, 80f, screenHeight = 2400f),
                    onSearch = {}, onOpenFeed = {}, onCustomize = {})
            }
        }
        // The island listens to the camera and microphone itself and reports their (idle) state as soon as it starts; wait for that first
        // answer so it does not overwrite what a test sets.
        Thread.sleep(2500)
        compose.waitForIdle()
    }

    private fun present(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Opens the island with the clock held still (so the idle auto-close cannot run), lets it finish growing, and takes the picture. */
    private fun openShot(name: String) {
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.toggle() }
        compose.mainClock.advanceTimeBy(1_500)
        shot(name, settle = false)
        compose.mainClock.autoAdvance = true
    }

    private fun shot(name: String, settle: Boolean = true) {
        if (settle) compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val crop = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, minOf(900, bitmap.height))
        File(context.filesDir, "tour-$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun call() = OngoingCall("Ada Lovelace", "com.example.phone", System.currentTimeMillis() - 83_000, null)

    private fun playMusic(playing: Boolean = true, colour: Int = 0xFFE2582B.toInt()) {
        val art = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).also { Canvas(it).drawColor(colour) }
        val s = MediaSession(context, "uno-island-test").also { session = it }
        s.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Night Drive")
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "The Examples").putLong(MediaMetadata.METADATA_KEY_DURATION, 240_000).putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art).build())
        s.setPlaybackState(PlaybackState.Builder().setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, 30_000, 1f)
            .setActions(PlaybackState.ACTION_PLAY_PAUSE).build())
        s.isActive = true
        compose.runOnIdle {
            NotificationFeed.nowPlaying = NowPlaying("Night Drive", "The Examples", art, playing, s.controller)
            state.setPlaying(System.currentTimeMillis(), playing)
        }
        compose.waitForIdle()
    }

    @Test fun aVideoCallKeepsItsTimerOnThePillAndShowsTheCameraAndMicrophoneAsDots() {
        show()
        compose.runOnIdle { state.cameraActive = true; state.micActive = true; NotificationFeed.ongoingCall = call() }
        compose.waitForIdle()
        assertTrue("the call is on the pill", present("island-call"))
        assertTrue("camera dot", present("island-camera")); assertTrue("microphone dot", present("island-mic"))
        assertEquals("nothing needed a bubble", 0, compose.onAllNodesWithTag("island-bubble-call", useUnmergedTree = true).fetchSemanticsNodes().size)
        shot("video-call")
        // Opening the island keeps the dots and shows the call card.
        openShot("video-call-open")
        assertTrue(present("island-call-row")); assertTrue(present("island-camera")); assertTrue(present("island-mic"))
    }

    @Test fun anEventDuringACallShowsOverTheCallAndTheDotsStay() {
        show()
        compose.runOnIdle { state.cameraActive = true; state.micActive = true; NotificationFeed.ongoingCall = call() }
        compose.runOnIdle { state.showEvent(IslandEvent("Silent", IslandSymbol.SILENT)) }
        compose.waitForIdle()
        val tree = compose.onRoot(useUnmergedTree = true).printToString(maxDepth = 12)
        assertTrue("dots missing; tree:\n$tree", present("island-camera") && present("island-mic"))
        assertEquals("the event took the pill, so the call text is not drawn at the same time", 0,
            compose.onAllNodesWithTag("island-call", useUnmergedTree = true).fetchSemanticsNodes().size)
        shot("event-during-call")
    }

    @Test fun musicPlayingWashesTheIslandInTheCoverColourAndShowsItsGlyph() {
        show()
        playMusic(playing = true)
        shot("music-playing")
        openShot("music-open")
        assertTrue(present("island-media"))
    }

    private fun setSession(playing: Boolean, audioActive: Boolean) {
        val art = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).also { Canvas(it).drawColor(0xFFE2582B.toInt()) }
        compose.runOnIdle {
            NotificationFeed.nowPlaying = NowPlaying("Night Drive", "The Examples", art, playing, session!!.controller)
            state.setPlaying(System.currentTimeMillis(), audioActive)
        }
        compose.waitForIdle()
    }

    @Test fun theSessionDecidesPlayingBeforeTheAudioSystemHasSaidAnything() {
        show()
        playMusic(playing = true)
        // The player says it is playing, the audio system has not caught up (or is momentarily silent): playing.
        setSession(playing = true, audioActive = false)
        assertTrue(present("island-playing"))
        // The player says paused while the audio system still reports activity (it lags a pause): paused at once.
        setSession(playing = false, audioActive = true)
        assertTrue(present("island-paused") && !present("island-playing"))
    }

    @Test fun pausedControlsLeaveWhenTheirWindowEndsAndComeBackOnPlay() {
        show()
        playMusic(playing = true)
        // Paused a minute and a half ago: nothing left to resume.
        compose.runOnIdle {
            NotificationFeed.nowPlaying = null
            state.setPlaying(System.currentTimeMillis() - 95_000L, true); state.setPlaying(System.currentTimeMillis() - 95_000L, false)
        }
        compose.waitForIdle()
        assertTrue("controls gone", !present("island-paused") && !present("island-playing"))
        // Playing again brings them straight back.
        compose.runOnIdle { state.setPlaying(System.currentTimeMillis(), true) }
        compose.waitForIdle()
        assertTrue(present("island-playing"))
    }

    @Test fun tappingTheTrackInTheOpenIslandOpensThePlayersApp() {
        show()
        playMusic(playing = true)
        openShot("open-player-before")
        assertTrue("the card is tappable", present("island-open-player"))
        // The session is this app's own and offers no now-playing screen, so the player's app is its launcher activity. The start is
        // caught by a monitor that does not let it happen: really opening the launcher inside the test's own process starved the test.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, true)
        try {
            compose.onAllNodesWithTag("island-open-player", useUnmergedTree = true)[0].performClick()
            val launched = monitor.waitForActivityWithTimeout(10_000)
            assertTrue("the player's app was asked to open", monitor.hits > 0 || launched != null)
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun tappingTheAppIconOnANotificationFlashOpensThatNotification() {
        show()
        val icon = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).also { Canvas(it).drawColor(0xFF3B82F6.toInt()) }
        val app = AppEntry("com.android.settings/com.android.settings.Settings", "Settings", icon)
        // The notification's own tap action: something other than the app's front page, so the two can be told apart.
        val open = android.app.PendingIntent.getActivity(context, 0,
            android.content.Intent(android.provider.Settings.ACTION_DATE_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        compose.runOnIdle { state.showPeek(app, "Settings", open) }
        compose.waitForIdle()
        assertTrue("the app icon is on the pill and tappable", present("island-peek-app"))
        shot("notification-flash")
        val device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        compose.onAllNodesWithTag("island-peek-app", useUnmergedTree = true)[0].performClick()
        var top = ""
        val end = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < end && !top.contains("DateTime", ignoreCase = true) && !top.contains("settings", ignoreCase = true)) {
            Thread.sleep(500)
            top = device.executeShellCommand("dumpsys activity activities").lineSequence().firstOrNull { "topResumedActivity" in it || "ResumedActivity" in it }.orEmpty()
        }
        assertTrue("the notification's screen came to the front: $top", top.contains("settings", ignoreCase = true))
    }

    /** Paused [secondsAgo] seconds ago, at [positionMs] of [durationMs], as the player's session would report it. */
    private fun pausedAgo(secondsAgo: Long, positionMs: Long, durationMs: Long, title: String? = "Night Drive") {
        val art = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).also { Canvas(it).drawColor(0xFFE2582B.toInt()) }
        val then = System.currentTimeMillis() - secondsAgo * 1_000L
        compose.runOnIdle {
            NotificationFeed.nowPlaying = NowPlaying(title, "The Examples", art, false, session!!.controller,
                PlaybackState.STATE_PAUSED, positionMs, durationMs)
            state.setPlaying(then, true); state.setPlaying(then, false)
        }
        compose.waitForIdle()
        // The island noted the change at this moment; move that to where the test says it happened.
        compose.runOnIdle { state.setLastPlayingAtForTest(then) }
        compose.waitForIdle()
    }


    @Test fun pausedMediaWorthReturningToLeavesThePillButStaysOnTheOpenIsland() {
        show()
        playMusic(playing = true)
        pausedAgo(secondsAgo = 20, positionMs = 60_000, durationMs = 240_000)
        assertTrue("off the pill", !present("island-paused") && !present("island-playing"))
        shot("resumable-resting")
        openShot("resumable-open")
        assertTrue("still on the open island", present("island-media"))
    }

    @Test fun justPausedMediaLingersOnThePillForAFewSeconds() {
        show()
        playMusic(playing = true)
        pausedAgo(secondsAgo = 2, positionMs = 60_000, durationMs = 240_000)
        assertTrue("still bars on the pill", present("island-paused"))
    }

    @Test fun aFinishedVideoLeavesAfterTheLingerWithNothingToReturnTo() {
        show()
        playMusic(playing = true)
        pausedAgo(secondsAgo = 20, positionMs = 239_000, durationMs = 240_000)
        assertTrue(!present("island-paused") && !present("island-playing"))
        compose.runOnIdle { state.toggle() }
        compose.waitForIdle()
        assertTrue("and not on the open island either", !present("island-media"))
    }

    @Test fun aShortClipAndAnUntitledPauseAreNotWorthReturningTo() {
        show()
        playMusic(playing = true)
        pausedAgo(secondsAgo = 20, positionMs = 10_000, durationMs = 25_000)
        compose.runOnIdle { state.toggle() }; compose.waitForIdle()
        assertTrue("short clip", !present("island-media"))
        compose.runOnIdle { state.collapse() }
        pausedAgo(secondsAgo = 20, positionMs = 60_000, durationMs = 240_000, title = null)
        compose.runOnIdle { state.toggle() }; compose.waitForIdle()
        assertTrue("no title", !present("island-media"))
    }

    @Test fun resumableMediaLeavesTheOpenIslandWhenTheWindowEnds() {
        show()
        playMusic(playing = true)
        pausedAgo(secondsAgo = 180, positionMs = 60_000, durationMs = 240_000)
        compose.runOnIdle { state.toggle() }; compose.waitForIdle()
        assertTrue("past the default two minutes", !present("island-media"))
    }

    @Test fun aSessionSayingPlayingOverSilenceStopsShowingAsPlaying() {
        show()
        playMusic(playing = true)
        // Audio has been quiet for ten seconds while the session still says playing (a muted autoplay video).
        compose.runOnIdle { state.setPlaying(System.currentTimeMillis() - 10_000L, true); state.setPlaying(System.currentTimeMillis() - 10_000L, false) }
        compose.waitForIdle()
        assertTrue("not shown as playing", !present("island-playing"))
    }

    @Test fun musicAndACallShareThePillWithTheCallInFull() {
        show()
        playMusic(playing = true)
        compose.runOnIdle { NotificationFeed.ongoingCall = call() }
        compose.waitForIdle()
        assertTrue(present("island-call"))
        shot("music-and-call")
    }

    @Test fun manyActivitiesSpillIntoAtMostTwoBubblesAndTheDotsStillShow() {
        show()
        playMusic(playing = true)
        compose.runOnIdle {
            NotificationFeed.ongoingCall = call()
            state.cameraActive = true; state.recordingActive = true
            NotificationFeed.liveUpdates = listOf(LiveUpdate("k", "com.example.ride", "Ride", "Ride to the airport", null, "12 min", 40, 100, false, null, false, null, 0xFF1A73E8.toInt(), 1L, null))
        }
        compose.waitForIdle()
        assertTrue(present("island-call")); assertTrue(present("island-camera")); assertTrue(present("island-recording"))
        assertTrue("at most two bubbles",
            listOf("call", "timer", "stopwatch", "update", "media").sumOf { compose.onAllNodesWithTag("island-bubble-$it", useUnmergedTree = true).fetchSemanticsNodes().size } <= 2)
        shot("crowded")
    }

    private fun freshUpdate() = LiveUpdate("k2", "com.example.ride", "Ride", "Ride to the airport", null, "12 min", 40, 100, false, null, false, null, 0xFF1A73E8.toInt(), System.currentTimeMillis(), null)

    private fun capsuleCount(kind: String) = compose.onAllNodesWithTag("island-capsule-$kind", useUnmergedTree = true).fetchSemanticsNodes().size +
        compose.onAllNodesWithTag("island-capsule-left-$kind", useUnmergedTree = true).fetchSemanticsNodes().size +
        compose.onAllNodesWithTag("island-bubble-$kind", useUnmergedTree = true).fetchSemanticsNodes().size

    @Test fun tappingAnExtraCapsuleMergesItIntoThePillAndTheOldFrontSplitsOff() {
        show()
        playMusic(playing = true)
        compose.runOnIdle { NotificationFeed.ongoingCall = call(); NotificationFeed.liveUpdates = listOf(freshUpdate()) }
        compose.waitForIdle()
        assertEquals("media is the extra capsule before", 1, capsuleCount("media"))
        compose.onNodeWithTag("island-capsule-media", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("media merged into the pill, so it has no capsule", 0, capsuleCount("media"))
        assertEquals("the old front detached into a capsule", 1, capsuleCount("call"))
        shot("after-swap")
    }

    @Test fun anActivityThatEndsFadesItsCapsuleOutAndLeavesNothingBehind() {
        show()
        playMusic(playing = true)
        compose.runOnIdle { NotificationFeed.ongoingCall = call(); NotificationFeed.liveUpdates = listOf(freshUpdate()) }
        compose.waitForIdle()
        assertEquals(1, capsuleCount("media"))
        compose.runOnIdle { NotificationFeed.nowPlaying = null; state.setPlaying(System.currentTimeMillis(), false) }
        compose.waitForIdle()
        assertEquals("the ended activity's capsule is gone once its exit has played", 0, capsuleCount("media"))
        compose.runOnIdle { state.cameraActive = true }
        compose.waitForIdle()
        assertTrue(present("island-camera"))
        compose.runOnIdle { state.cameraActive = false }
        compose.waitForIdle()
        assertTrue("the camera dot is gone once it has shrunk away", !present("island-camera"))
    }

    @Test fun fourActivitiesAndTheDotsAllShowAsCircles() {
        show()
        playMusic(playing = true)
        compose.runOnIdle {
            NotificationFeed.ongoingCall = call(); NotificationFeed.liveUpdates = listOf(freshUpdate())
            IslandTools.startTimer(context, 600_000L)
            state.cameraActive = true; state.micActive = true
        }
        compose.waitForIdle()
        assertTrue("circles fill the room before the edge, no more", capsuleCount("timer") + capsuleCount("update") + capsuleCount("media") in 1..3)
        shot("four-activities")
    }

    @Test fun withThreeActivitiesTheSecondIsOnTheLeftAndTheThirdOnTheRight() {
        show()
        // Forced from the debug page: a call (in the pill), a timer (to its left) and a live update (to its right).
        compose.runOnIdle { IslandDebug.init(context); IslandDebug.force(LiveKind.CALL, true); IslandDebug.force(LiveKind.TIMER, true); IslandDebug.force(LiveKind.UPDATE, true) }
        compose.waitForIdle()
        assertEquals("the second is on the left", 1, compose.onAllNodesWithTag("island-capsule-left-timer", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals("the third is on the right", 1, compose.onAllNodesWithTag("island-capsule-update", useUnmergedTree = true).fetchSemanticsNodes().size)
        val left = compose.onAllNodesWithTag("island-capsule-left-timer", useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot
        val right = compose.onAllNodesWithTag("island-capsule-update", useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot
        assertTrue("the left capsule is left of the right one", left.right < right.left)
        shot("three-activities")
        // Widening the left capsule by hand makes it wider.
        val before = left.width
        compose.runOnIdle { IslandDebug.set(DebugTarget.LEFT, DebugGroup(dw = 20f)) }
        compose.waitForIdle()
        assertTrue("debug width applies", compose.onAllNodesWithTag("island-capsule-left-timer", useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot.width > before + 10f)
    }
}
