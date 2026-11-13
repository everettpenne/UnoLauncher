package com.jake.duolauncher.agent

import com.jake.duolauncher.search.AssistantLive
import com.jake.duolauncher.search.agent.AgentState
import com.jake.duolauncher.search.agent.Params
import com.jake.duolauncher.search.agent.Step
import com.jake.duolauncher.search.agent.tools.TimerTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The island's line for a task turn: what it is doing, and then what it did. */
class AgentLiveTest {
    @Test fun aRunningTurnSaysWhatItIsDoing() {
        assertEquals("Thinking…" to "Planning what to do", AssistantLive.agentLine(AgentState.Planning("timer")))
        assertEquals("Acting…" to "Doing: timer", AssistantLive.agentLine(AgentState.Acting(Step(TimerTool, Params(emptyMap())))))
        assertEquals("Waiting" to "Timer set for 20 minutes", AssistantLive.agentLine(AgentState.Confirming(listOf("Timer set for 20 minutes"))))
        assertEquals("Waiting" to "Which Sam?", AssistantLive.agentLine(AgentState.Asking("Which Sam?")))
    }

    @Test fun aFinishedTurnSaysWhatItDid() {
        assertEquals("Done" to "Timer set for 20 minutes", AssistantLive.agentLine(AgentState.Done("Timer set for 20 minutes", true)))
        assertEquals("Couldn't do that" to "I'd need calendar access.", AssistantLive.agentLine(AgentState.Failed("I'd need calendar access.")))
    }

    @Test fun anIdleSessionHasNoLine() {
        assertNull(AssistantLive.agentLine(AgentState.Idle))
    }

    @Test fun aFinishedTaskBeingReadInSearchHasNoLineOnTheIslandToo() {
        val done = AgentState.Done("Timer set for 20 minutes", true)
        assertNull(AssistantLive.agentLine(done, searchOpen = true))
        assertNull(AssistantLive.agentLine(AgentState.Failed("no"), searchOpen = true))
        assertEquals("Done" to "Timer set for 20 minutes", AssistantLive.agentLine(done, searchOpen = false))
        // Work in progress, and anything waiting for the person, shows either way.
        assertEquals("Thinking…", AssistantLive.agentLine(AgentState.Planning("q"), searchOpen = true)?.first)
        assertEquals("Waiting", AssistantLive.agentLine(AgentState.Asking("Which one?"), searchOpen = true)?.first)
    }
}
