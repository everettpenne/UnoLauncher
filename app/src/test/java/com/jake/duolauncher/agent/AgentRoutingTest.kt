package com.jake.duolauncher.agent

import com.jake.duolauncher.search.agent.AgentRouting
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The gate that decides whether a question is planned or answered: off until a Lab with the planner API is out. */
class AgentRoutingTest {
    @Test fun nothingIsPlannedWhileTheGateIsOff() {
        AgentRouting.enabled = false
        assertFalse(AgentRouting.plans("timer for 20 minutes"))
        assertFalse(AgentRouting.plans("explain compound interest"))
    }

    @Test fun aTaskIsPlannedAndAQuestionIsNotOnceTheGateIsOn() {
        AgentRouting.enabled = true
        try {
            assertTrue(AgentRouting.plans("timer for 20 minutes"))
            assertTrue(AgentRouting.plans("turn off the flashlight"))
            assertFalse(AgentRouting.plans("explain compound interest"))
            assertFalse(AgentRouting.plans("what's my next alarm"))
        } finally {
            AgentRouting.enabled = false
        }
    }
}
