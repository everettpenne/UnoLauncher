package com.jake.duolauncher

import com.jake.duolauncher.island.*
import org.junit.Assert.*
import org.junit.Test

class IslandCardsTest {
    @Test fun segmentsLightOnceTheirShareIsReached() {
        assertEquals(0, IslandCards.filledSegments(24, 100, 4))
        assertEquals(1, IslandCards.filledSegments(25, 100, 4))
        assertEquals(4, IslandCards.filledSegments(100, 100, 4))
        assertEquals(4, IslandCards.filledSegments(250, 100, 4))
        assertEquals(0, IslandCards.filledSegments(5, 0, 4))
    }
    @Test fun ringShrinksFromFullToEmpty() {
        assertEquals(1f, IslandCards.ringRemaining(60_000, 60_000), 0f)
        assertEquals(.5f, IslandCards.ringRemaining(30_000, 60_000), 0f)
        assertEquals(0f, IslandCards.ringRemaining(0, 0), 0f)
        assertEquals(1f, IslandCards.ringRemaining(90_000, 60_000), 0f)
    }
    @Test fun everyKindWithACardHasHeightAndTheOthersNone() {
        listOf(LiveKind.MEDIA, LiveKind.CALL, LiveKind.TIMER, LiveKind.STOPWATCH, LiveKind.UPDATE).forEach { assertTrue(IslandCards.extraDp(it) > 0f) }
        assertEquals(0f, IslandCards.extraDp(LiveKind.CAMERA), 0f)
    }
    @Test fun playfulIsOnByDefaultAndSurvivesAThemeFile() {
        assertTrue(IslandTheme.Classic.playful)
        val off = IslandThemeFile.toJson(IslandTheme.Classic.copy(playful = false))
        assertTrue((IslandThemeFile.parse(off) as IslandThemeFile.Result.Ok).theme.let { !it.playful })
        assertTrue((IslandThemeFile.parse(IslandThemeFile.toJson(IslandTheme.Classic)) as IslandThemeFile.Result.Ok).theme.playful)
    }
}
