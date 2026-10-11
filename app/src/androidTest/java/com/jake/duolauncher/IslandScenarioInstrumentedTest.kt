package com.jake.duolauncher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
        IslandTools.toolsOpen = false
        session?.release()
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
}
