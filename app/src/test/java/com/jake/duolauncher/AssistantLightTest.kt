package com.jake.duolauncher

import com.jake.duolauncher.search.AssistantLightLogic
import com.jake.duolauncher.search.AssistantSession
import com.jake.duolauncher.search.LightMood
import com.jake.duolauncher.search.agent.AgentState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantLightTest {
    private val idle = AssistantSession.State.Idle
    private fun mood(agent: AgentState?, answer: AssistantSession.State = idle, written: Int = 0) = AssistantLightLogic.mood(agent, answer, written)

    @Test fun aTasksTurnIsTheAssistantsMood() {
        assertEquals(LightMood.THINKING, mood(AgentState.Planning("set a timer")))
        assertEquals(LightMood.WAITING, mood(AgentState.Confirming(listOf("Reply to Sam"))))
        assertEquals(LightMood.WAITING, mood(AgentState.Asking("How long?")))
        assertEquals(LightMood.READY, mood(AgentState.Done("Timer set", true)))
        assertEquals(LightMood.OFF, mood(AgentState.Failed("No.")))
    }

    @Test fun withNoTaskTheAnswerIs() {
        val running = AssistantSession.State.Running("why is the sky blue")
        assertEquals(LightMood.THINKING, mood(null, running))
        assertEquals(LightMood.THINKING, mood(AgentState.Idle, running))
        assertEquals(LightMood.WRITING, mood(AgentState.Idle, running, written = 40))
        assertEquals(LightMood.OFF, mood(null))
    }

    @Test fun theLightIsOnOnlyWhileSomethingIsHappening() {
        assertEquals(1f, AssistantLightLogic.strength(LightMood.THINKING))
        assertEquals(1f, AssistantLightLogic.strength(LightMood.WRITING))
        assertTrue(AssistantLightLogic.strength(LightMood.WAITING) in .5f..1f)
        assertEquals(0f, AssistantLightLogic.strength(LightMood.READY))
        assertEquals(0f, AssistantLightLogic.strength(LightMood.OFF))
        // Ready is seen as it goes: it takes longer to fade than a turn that simply stopped.
        assertTrue(AssistantLightLogic.fadeMs(LightMood.READY) > AssistantLightLogic.fadeMs(LightMood.OFF))
    }

    @Test fun waitingIsAmberAndBreathesAndTheRestKeepTheRainbow() {
        assertEquals(1f to AssistantLightLogic.amber, AssistantLightLogic.tone(LightMood.WAITING))
        assertEquals(0f, AssistantLightLogic.tone(LightMood.THINKING).first)
        for (phase in listOf(0f, .1f, .37f, .5f, .9f)) {
            assertTrue(AssistantLightLogic.breath(LightMood.WAITING, phase) in .69f..1.01f)
            assertEquals(1f, AssistantLightLogic.breath(LightMood.THINKING, phase))
        }
    }

    @Test fun theBandHasNoSeam() {
        for (toward in listOf(0f, .5f, 1f)) {
            val colours = AssistantLightLogic.palette(toward, AssistantLightLogic.amber)
            assertEquals(colours.first(), colours.last())
        }
        // All the way toward one colour, it is that colour all round.
        assertTrue(AssistantLightLogic.palette(1f, AssistantLightLogic.amber).toSet().size == 1)
    }

    @Test fun theWaveFiresOnceWhenAWorkingTurnEndsWell() {
        val L = com.jake.duolauncher.search.AnswerAberrationLogic
        assertTrue(L.fires(LightMood.WRITING, LightMood.READY))
        assertTrue(L.fires(LightMood.THINKING, LightMood.READY))
        // An answer already finished when the screen was drawn, a failure, a question back and a cleared answer do not.
        for ((was, now) in listOf(LightMood.READY to LightMood.READY, LightMood.OFF to LightMood.READY, LightMood.WRITING to LightMood.OFF,
            LightMood.THINKING to LightMood.WAITING, LightMood.READY to LightMood.OFF, LightMood.OFF to LightMood.THINKING)) assertTrue("$was to $now", !L.fires(was, now))
    }

    @Test fun theWaveComesUpFastFadesToNothingAndLeavesTheScreen() {
        val L = com.jake.duolauncher.search.AnswerAberrationLogic
        assertEquals(0f, L.strength(0f)); assertEquals(0f, L.strength(1f))
        val peak = (0..100).maxByOrNull { L.strength(it / 100f) }!! / 100f
        assertTrue("peaks early ($peak)", peak in .08f..(.2f))
        assertTrue(L.strength(peak) > .95f)
        for (i in 0..100) assertTrue(L.strength(i / 100f) in 0f..1f)
        // Only falling after the peak, and nearly gone well before the end, so nothing snaps off.
        var before = L.strength(peak)
        for (i in (peak * 100).toInt() + 1..100) { val s = L.strength(i / 100f); assertTrue(s <= before + 1e-6f); before = s }
        assertTrue(L.strength(.9f) < .02f)
        // The front only moves outward and is past the farthest corner before the run ends.
        var at = -1f
        for (i in 0..100) { val f = L.front(i / 100f); assertTrue(f >= at); at = f }
        assertEquals(0f, L.front(0f)); assertTrue(L.front(.75f) > 1f)
    }
}
