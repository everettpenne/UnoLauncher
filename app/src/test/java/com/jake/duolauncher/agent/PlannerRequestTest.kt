package com.jake.duolauncher.agent

import com.jake.duolauncher.search.agent.*
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerRequestTest {
    private val tools = ToolRegistry.builtIn

    @Test fun theRequestCarriesEverythingThePlannerNeeds() {
        val json = PlannerRequest.json("timer for 20 minutes", "Today: Friday", "Clock is installed", tools)
        val parsed = JSONObject(json)
        assertEquals(PlannerRequest.VERSION, parsed.getInt("v"))
        assertEquals("timer for 20 minutes", parsed.getString("query"))
        assertEquals("Today: Friday", parsed.getString("context"))
        assertEquals("Clock is installed", parsed.getString("grounding"))
        // The catalogue and grammar are the generated ones, so the planner is never out of step with the tools.
        assertEquals(Catalogue.prompt(tools), parsed.getString("catalogue"))
        assertEquals(Catalogue.grammar(tools), parsed.getString("grammar"))
        assertEquals(Catalogue.hash(tools), parsed.getString("hash"))
    }

    @Test fun quotesAndControlCharactersSurviveTheRoundTrip() {
        val nasty = "say \"hello\"\nnext\\line\ttab \u0001 end — \uD83D\uDE00"
        val parsed = JSONObject(PlannerRequest.json(nasty, "", "", emptyList()))
        assertEquals(nasty, parsed.getString("query"))
    }

    @Test fun aQuestionWithNoFactsStillMakesAValidRequest() {
        val parsed = JSONObject(PlannerRequest.json("hi", "", "", emptyList()))
        assertEquals("", parsed.getString("context"))
        assertTrue(parsed.getString("catalogue").contains("You plan tasks"))
    }
}
