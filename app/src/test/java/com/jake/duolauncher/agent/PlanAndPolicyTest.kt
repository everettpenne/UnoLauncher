package com.jake.duolauncher.agent

import com.jake.duolauncher.search.agent.*
import com.jake.duolauncher.search.agent.tools.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanAndPolicyTest {
    private val tools = ToolRegistry.builtIn

    @Test fun aPlanIsStepsInOrder() {
        val result = PlanParser.parse(
            """{"steps":[{"tool":"timer","params":{"seconds":"1200","label":"pasta"}},{"tool":"focus","params":{"on":"true"}}]}""",
            tools)
        val plan = (result as ParseResult.Ok).plan as Plan.Steps
        assertEquals(2, plan.steps.size)
        assertEquals("timer", plan.steps[0].tool.name)
        assertEquals(1200, plan.steps[0].params.int("seconds"))
        assertEquals("pasta", plan.steps[0].params.text("label"))
        assertEquals(true, plan.steps[1].params.bool("on"))
    }

    @Test fun aQuestionAndARefusalArePlansToo() {
        val clarify = PlanParser.parse("""{"clarify":"Which Sam?"}""", tools)
        assertEquals("Which Sam?", ((clarify as ParseResult.Ok).plan as Plan.Clarify).question)
        val refuse = PlanParser.parse("""{"refuse":"I'd need calendar access."}""", tools)
        assertEquals("I'd need calendar access.", ((refuse as ParseResult.Ok).plan as Plan.Refuse).reason)
    }

    @Test fun aFencedReplyIsReadAnyway() {
        val result = PlanParser.parse("```json\n{\"steps\":[{\"tool\":\"torch\",\"params\":{}}]}\n```", tools)
        assertEquals(1, ((result as ParseResult.Ok).plan as Plan.Steps).steps.size)
    }

    @Test fun untrustedRepliesAreRejectedWithAReason() {
        assertTrue(PlanParser.parse("not json", tools) is ParseResult.Failed)
        assertTrue(PlanParser.parse("""{"steps":[]}""", tools) is ParseResult.Failed)
        assertTrue(PlanParser.parse("""{"steps":[{"tool":"launch_missiles","params":{}}]}""", tools) is ParseResult.Failed)
        assertTrue(PlanParser.parse("""{"steps":[{"tool":"timer","params":{"seconds":"soon"}}]}""", tools) is ParseResult.Failed)
        assertTrue(PlanParser.parse("""{"steps":[{"tool":"timer","params":{}}]}""", tools) is ParseResult.Failed)
    }

    @Test fun onlyLeavingThePhoneAsksFirst() {
        val timer = ToolRegistry.byName("timer")!!
        assertTrue(ToolPolicy.needsConfirmation(timer).let { !it })  // local: runs silently
        val send = object : Tool("send", "sends", effect = Effect.LeavesPhone) {
            override suspend fun execute(params: Params, env: ToolEnv) = ToolResult(true, "sent")
        }
        assertTrue(ToolPolicy.needsConfirmation(send))
        // An opt-in tap for a local tool, for anyone who wants it.
        assertTrue(ToolPolicy.needsConfirmation(timer, AgentSettings(alwaysAsk = setOf("timer"))))
        // "Ask before every action": one switch, every tool.
        assertTrue(ToolPolicy.needsConfirmation(timer, AgentSettings(askAll = true)))
    }

    // ---- a plan that is not the kind of thing asked for ----

    private fun steps(vararg names: String) = Plan.Steps(names.map { Step(ToolRegistry.byName(it)!!, Params(emptyMap())) })

    @Test fun aRequestNamesAKindOfActionAndAPlanOfAnotherKindIsAMismatch() {
        // Seen on a phone: "set an alarm for 6am tomorrow" planned as a one-hour timer.
        assertTrue(RequestKinds.mismatch("set an alarm for 6am tomorrow", steps("timer")))
        assertFalse(RequestKinds.mismatch("set an alarm for 6am tomorrow", steps("alarm")))
        assertTrue(RequestKinds.mismatch("remember to buy bread", steps("open_url")))
        assertTrue(RequestKinds.mismatch("text 555 0100", steps("dial")))
        assertFalse(RequestKinds.mismatch("text 555 0100", steps("message")))
        assertFalse(RequestKinds.mismatch("remind me in an hour", steps("timer")))
        assertTrue(RequestKinds.mismatch("remind me in an hour", steps("torch")))
    }

    @Test fun everyKindARequestNamesHasToBeInThePlan() {
        assertFalse(RequestKinds.mismatch("set a timer and turn on focus", steps("timer", "focus")))
        assertTrue(RequestKinds.mismatch("set a timer and turn on focus", steps("timer")))
    }

    @Test fun aRequestThatNamesNoKindIsNeverAMismatch() {
        assertFalse(RequestKinds.mismatch("open the camera", steps("open_app")))
        assertFalse(RequestKinds.mismatch("dentist at three", steps("timer")))
        assertTrue(RequestKinds.named("what a day").isEmpty())
    }
}
