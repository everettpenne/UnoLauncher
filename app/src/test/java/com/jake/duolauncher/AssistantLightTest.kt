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
}
