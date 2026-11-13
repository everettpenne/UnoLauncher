package com.jake.duolauncher.agent

import com.jake.duolauncher.search.agent.AgentRouting
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The gate that decides whether a question is planned or answered; off until a Lab with the planner API is out. */
class AgentRoutingTest {
    @Test fun nothingIsPlannedWhileTheGateIsOff() {
        assertFalse(AgentRouting.plans("timer for 20 minutes", enabled = false))
        assertFalse(AgentRouting.plans("explain compound interest", enabled = false))
    }

    @Test fun aTaskIsPlannedAndAQuestionIsNotOnceTheGateIsOn() {
        assertTrue(AgentRouting.plans("timer for 20 minutes", enabled = true))
        assertTrue(AgentRouting.plans("turn off the flashlight", enabled = true))
        assertFalse(AgentRouting.plans("explain compound interest", enabled = true))
        assertFalse(AgentRouting.plans("what's my next alarm", enabled = true))
    }

    @Test fun theSwitchIsOneGlobalSetting() {
        // The device pass flips it with `adb shell settings put global unolab_agent 1`.
        assertTrue(AgentRouting.SETTING == "unolab_agent")
    }
}
