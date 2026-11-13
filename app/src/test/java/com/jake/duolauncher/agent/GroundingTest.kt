package com.jake.duolauncher.agent

import com.jake.duolauncher.search.agent.GroundingLogic
import com.jake.duolauncher.search.agent.KnownPerson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroundingTest {
    private val sam = KnownPerson("Sam Patel", "+15550100")
    private val ana = KnownPerson("Ana Ruiz", "+15550111")

    @Test fun aWholeNameWordIsAMention() {
        assertTrue(GroundingLogic.mentions("text Sam", "Sam Patel"))
        assertTrue(GroundingLogic.mentions("call ana", "Ana Ruiz"))
        assertFalse(GroundingLogic.mentions("text Ana", "Sam Patel"))
    }

    @Test fun partialNamesMatchAtTheStartOfALongerWord() {
        assertTrue(GroundingLogic.mentions("message Samantha", "Sam Patel"))
        assertTrue(GroundingLogic.mentions("open messages", "Messages"))
    }

    @Test fun shortOrUnrelatedWordsDoNotMatch() {
        assertFalse(GroundingLogic.mentions("turn on the torch", "Tom Ford"))
        assertFalse(GroundingLogic.mentions("open the camera", "Ana Ruiz"))
        assertFalse(GroundingLogic.mentions("", "Sam Patel"))
        // Prefix matching over-includes on purpose: "timer" mentions a contact called Tim, which costs a line and nothing else.
        assertTrue(GroundingLogic.mentions("timer", "Tim"))
    }

    @Test fun theBlockCarriesOnlyWhatIsMentioned() {
        val people = listOf(sam, ana)
        val apps = listOf("Clock", "Camera", "Messages")
        val block = GroundingLogic.block("text Sam and open Camera", people, apps)
        assertTrue(block.contains("Sam Patel (+15550100)"))
        assertFalse(block.contains("Ana Ruiz"))
        assertTrue(block.contains("Apps: Camera"))
        assertFalse(block.contains("Clock"))
    }

    @Test fun nothingKnownMeansNoBlock() {
        assertEquals("", GroundingLogic.block("text Sam", emptyList(), emptyList()))
        assertEquals("", GroundingLogic.block("turn on the torch", listOf(sam), listOf("Clock")))
    }

    @Test fun theRequestCarriesTheGroundedNames() {
        val request = GroundingLogic.planning("text Sam", "Today: Friday", listOf(sam), listOf("Messages"),
            com.jake.duolauncher.search.agent.ToolRegistry.builtIn)
        assertTrue(request.grounding.contains("Sam Patel (+15550100)"))
        assertEquals("text Sam", request.query)
        assertEquals("Today: Friday", request.context)
    }
}
