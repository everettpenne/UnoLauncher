package com.jake.duolauncher.agent.training

import com.jake.duolauncher.search.agent.EvalSet
import com.jake.duolauncher.search.agent.ToolRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutcomeScorerTest {
    private val tools = ToolRegistry.builtIn
    private fun rows(json: String) = EvalSet.parse(json)
    private fun one(row: String, reply: String, turn: String = "") =
        OutcomeScorer.score(rows("[$row]"), mapOf(rows("[$row]").single().query to reply), if (turn.isEmpty()) emptyMap() else mapOf(rows("[$row]").single().query to turn), tools).single()

    private val timer = """{"q":"timer for 20 minutes","steps":[{"tool":"timer","params":{"length":"20 minutes"}}]}"""

    @Test fun theSamePlanEndsTheSameWay() {
        val row = one(timer, """{"steps":[{"tool":"timer","params":{"length":"20 minutes"}}]}""")
        assertTrue(row.same); assertNull(row.cause)
        assertEquals(listOf("timer 1200"), row.got.did)
    }

    @Test fun aPlanWordedAnotherWayThatComesToTheSameThingPasses() {
        val row = one(timer, """{"steps":[{"tool":"timer","params":{"length":"1200 seconds"}}]}""")
        assertTrue("twenty minutes is twenty minutes: ${row.got.did}", row.same)
        val search = one("""{"q":"look up the capital of peru","steps":[{"tool":"web_search","params":{"query":"the capital of peru"}}]}""",
            """{"steps":[{"tool":"web_search","params":{"query":"capital of peru"}}]}""")
        assertTrue("a dropped article is the same search: ${search.got.did}", search.same)
        assertFalse("though the plans are not word for word the same", search.planExact)
    }

    @Test fun theMissesAreToldApartByCause() {
        assertEquals("refused instead of acting", one(timer, """{"refuse":"I can't."}""").cause)
        assertEquals("asked instead of acting", one(timer, """{"clarify":"How long?"}""").cause)
        assertEquals("did a different thing", one(timer, """{"steps":[{"tool":"stopwatch","params":{"action":"start"}}]}""").cause)
        val list = """{"q":"add milk to my list","steps":[{"tool":"checklist_add","params":{"item":"milk"}}]}"""
        assertEquals("did the right thing with a different value", one(list, """{"steps":[{"tool":"checklist_add","params":{"item":"bread"}}]}""").cause)
        // A length the person said outright is put right by the launcher whatever the model wrote, so that turn ends as it should.
        assertNull(one(timer, """{"steps":[{"tool":"timer","params":{"length":"25 minutes"}}]}""").cause)
        assertEquals("the reply could not be used", one(timer, "I would set a timer.").cause)
        assertEquals("acted instead of asking", one("""{"q":"stick it on my list","clarify":true}""", """{"steps":[{"tool":"checklist_add","params":{"item":"it"}}]}""").cause)
        // A timer with a length nobody said is asked about by the launcher, so that turn ends as it should whatever the model wrote.
        assertNull(one("""{"q":"set a timer","clarify":true}""", """{"steps":[{"tool":"timer","params":{"length":"5 minutes"}}]}""").cause)
    }

    /** What the launcher does to a plan after the model wrote it counts: a number nobody gave is asked for, whatever the plan says. */
    @Test fun aNumberTheModelMadeUpEndsAsAQuestion() {
        val row = one("""{"q":"call my dentist","clarify":true}""", """{"steps":[{"tool":"dial","params":{"number":"5550199"}}]}""")
        assertTrue("the launcher asks: ${row.got}", row.same)
        assertTrue(row.got.did.isEmpty())
    }

    @Test fun theFactsBesideARequestAreReadBack() {
        val turn = "<|im_start|>user\n<context>\nPhone context:\n- Timer: 4 min 30 s left\n</context>\n\n<known>\nMessages waiting for a reply (newest first): Sam Patel in Messages; Dana in Signal\n</known>\n\nRequest: x"
        assertEquals("Phone context:\n- Timer: 4 min 30 s left", OutcomeScorer.block(turn, "context"))
        val waiting = OutcomeScorer.waitingIn(OutcomeScorer.block(turn, "known"))
        assertEquals(listOf("Sam Patel" to "Messages", "Dana" to "Signal"), waiting.map { it.who to it.app })
        assertTrue(waiting[0].postedAt > waiting[1].postedAt)
        assertEquals("", OutcomeScorer.block("Request: x", "context"))
    }

    @Test fun aReplyGoesToTheMessageThatIsWaiting() {
        val turn = "<known>\nMessages waiting for a reply (newest first): Dana in Signal; Sam Patel in Messages\n</known>\n"
        val row = one("""{"q":"reply to Sam saying on my way","steps":[{"tool":"reply","params":{"to":"Sam","text":"on my way"}}]}""",
            """{"steps":[{"tool":"reply","params":{"to":"Sam Patel","text":"On my way"}}]}""", turn)
        assertTrue("${row.wanted} / ${row.got}", row.same)
        assertEquals(listOf("reply Sam Patel/Messages: on my way"), row.got.did)
    }
}
