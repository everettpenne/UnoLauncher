package com.jake.duolauncher.agent

import com.jake.duolauncher.search.AskMode
import com.jake.duolauncher.search.AssistantClient
import com.jake.duolauncher.search.AssistantState
import com.jake.duolauncher.search.agent.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabPlannerTest {
    private class FakeClient(private val reply: String?) : AssistantClient {
        var lastRequest: String? = null
        override val state = MutableStateFlow(AssistantState.OFF)
        override fun open() = Unit
        override fun close() = Unit
        override fun ask(query: String, mode: AskMode, context: String): Flow<String> = emptyFlow()
        override fun continueInLab(context: android.content.Context, query: String) = Unit
        override suspend fun plan(request: String): String? {
            lastRequest = request
            return reply
        }
    }

    @Test fun thePlannerSendsTheCatalogueAndTheGrammarWithTheQuestion() = runBlocking {
        val client = FakeClient("""{"steps":[{"tool":"timer","params":{"seconds":"1200","label":""}}]}""")
        val planner = LabPlanner { client }
        val result = planner.plan(PlanRequest("timer for 20 minutes", "Today: Friday", ToolRegistry.builtIn, "Clock is installed"))
        val request = JSONObject(client.lastRequest!!)
        assertEquals("timer for 20 minutes", request.getString("query"))
        assertEquals(Catalogue.hash(ToolRegistry.builtIn), request.getString("hash"))
        assertEquals(Catalogue.grammar(ToolRegistry.builtIn), request.getString("grammar"))
        val plan = (result as ParseResult.Ok).plan as Plan.Steps
        assertEquals("timer", plan.steps.first().tool.name)
    }

    @Test fun aLabThatIsNotThereIsAFailureNotACrash() = runBlocking {
        assertTrue(LabPlanner { null }.plan(PlanRequest("hi", "", ToolRegistry.builtIn)) is ParseResult.Failed)
    }

    @Test fun aBlankOrUnparseableReplyIsAFailure() = runBlocking {
        assertTrue(LabPlanner { FakeClient(null) }.plan(PlanRequest("hi", "", ToolRegistry.builtIn)) is ParseResult.Failed)
        assertTrue(LabPlanner { FakeClient("  ") }.plan(PlanRequest("hi", "", ToolRegistry.builtIn)) is ParseResult.Failed)
        assertTrue(LabPlanner { FakeClient("not json") }.plan(PlanRequest("hi", "", ToolRegistry.builtIn)) is ParseResult.Failed)
    }
}
