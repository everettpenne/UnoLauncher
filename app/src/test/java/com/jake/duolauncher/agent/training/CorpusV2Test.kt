package com.jake.duolauncher.agent.training

import com.jake.duolauncher.search.agent.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The protocol-2 contract and its corpus: what the 4B and 2B planners are trained to read and write. */
class CorpusV2Test {
    private val train by lazy { CorpusV2.build(Split2.TRAIN) }
    private val dev by lazy { CorpusV2.build(Split2.DEV) }
    private val test by lazy { CorpusV2.build(Split2.TEST) }

    /** Pinned: a planner trained on this corpus is only right while the launcher sends exactly this. Changing a tool's words, a
     *  rule or a source changes it, and means training again, or at least scoring again (docs/agent/protocol-v2.md). */
    @Test fun theContractIsPinned() {
        assertEquals("the first planners' list is untouched", "b13ce3444d307385", Catalogue.hash(ToolRegistry.builtIn))
        assertEquals(PINNED, Catalogue.hash(ToolRegistry.next, Catalogue.V2))
        assertEquals(32, ToolRegistry.next.size)
        assertEquals(ToolRegistry.next.size, ToolRegistry.next.map { it.name }.toSet().size)
    }

    @Test fun everyReplyIsOneItsOwnListAllows() {
        for (row in train + dev + test) {
            val tools = row.tools.map { name -> ToolRegistry.next.firstOrNull { it.name == name } ?: (CorpusV2.madeUp + CorpusV2.heldOut).first { it.tool.name == name }.tool }
            assertTrue("${row.family}: ${row.query} -> ${row.completion}", PlanParser.parse(row.completion, tools, Catalogue.V2) is ParseResult.Ok)
        }
    }

    /** A corpus file holds a row's tools by name; the system turn and the grammar put together from the pieces must be the real ones. */
    @Test fun aRowsListIsPutTogetherFromItsPieces() {
        val (before, after) = CorpusV2.grammarFrame()
        for (row in (train.take(40) + test.take(40) + test.filter { it.family == "unseen-tool" }.take(5))) {
            assertEquals(row.system, CorpusV2.systemHead + row.listed.joinToString("\n") { CorpusV2.toolBlock(it) } + CorpusV2.SYSTEM_TAIL)
            val rules = row.listed.map { CorpusV2.grammarLine(it) }
            val built = (before + listOf("step ::= " + rules.joinToString(" | ") { it.first }) + rules.map { it.second } + after).joinToString("\n") + "\n"
            assertEquals(row.grammar, built)
        }
    }

    @Test fun theThreeSplitsShareNoRequest() {
        fun keys(rows: List<Row2>) = rows.map { it.query.lowercase().trim() }.toSet()
        assertTrue("dev in train", (keys(dev) intersect keys(train)).isEmpty())
        assertTrue("test in train", (keys(test) intersect keys(train)).isEmpty())
        assertTrue("test in dev: ${(keys(test) intersect keys(dev)).take(3)}", (keys(test) intersect keys(dev)).isEmpty())
    }

    @Test fun theToolsHeldOutAreInNoTrainingOrDevRow() {
        val held = CorpusV2.heldOut.map { it.tool.name }.toSet()
        assertTrue((train + dev).none { row -> row.tools.any { it in held } || held.any { "\"tool\":\"$it\"" in row.completion } })
        assertTrue(test.count { it.family == "unseen-tool" } >= 60)
        assertTrue(test.any { it.family == "unseen-tool-absent" })
        assertTrue("made-up names do not clash with real ones", (CorpusV2.madeUp + CorpusV2.heldOut).none { m -> ToolRegistry.next.any { it.name == m.tool.name } })
    }

    @Test fun everyToolAndEveryKindOfTurnIsTrained() {
        val used = train.flatMap { row -> Regex("\"tool\":\"([a-z_]+)\"").findAll(row.completion).map { it.groupValues[1] }.toList() }.groupingBy { it }.eachCount()
        for (tool in ToolRegistry.next) assertTrue("${tool.name}: ${used[tool.name]} rows", (used[tool.name] ?: 0) >= 30)
        val families = train.groupingBy { it.family }.eachCount()
        for (family in listOf("new-tool", "made-up-tool", "tool-absent", "look", "found", "found-nothing", "found-ask", "fact-given", "fact-missing", "fact-ask", "failed", "retry", "multi-4", "multi-5", "event-time", "clarify"))
            assertTrue("$family: ${families[family]}", (families[family] ?: 0) >= 20)
        assertTrue("a list is rarely the whole list", train.map { it.tools.toSet() }.toSet().size > train.size / 2)
        assertTrue(train.size > 20_000)
    }

    @Test fun aRequestIsRefusedOnlyWhenItsToolIsNotListed() {
        val absent = train.first { it.family == "tool-absent" }
        assertTrue(absent.completion.startsWith("{\"refuse\":\"I can't "))
        val same = train.first { it.query == absent.query && it.family != "tool-absent" }
        assertTrue("the same words act when the tool is there", same.completion.startsWith("{\"steps\""))
        val name = Regex("\"tool\":\"([a-z_]+)\"").find(same.completion)!!.groupValues[1]
        assertFalse(name in absent.tools)
        assertTrue(name in same.tools)
    }

    /** The frozen sets of the first contract, as rows of this one: every one a reply the scorer can read, the questions and refusals too. */
    @Test fun theRegressionRowsAreAllReadable() {
        val rows = listOf("/agent/phrases.json", "/agent/phrases-fresh.json", "/agent/phrases-followup.json").flatMap { CorpusV2.regression(it) }
        assertEquals(140, rows.size)
        for (row in rows) {
            val plan = (PlanParser.parse(row.completion, row.listed, Catalogue.V2) as? ParseResult.Ok)?.plan
            assertTrue("${row.query} -> ${row.completion}", plan != null)
            if (plan is Plan.Clarify) assertTrue(plan.question.isNotBlank())
            if (plan is Plan.Refuse) assertTrue(plan.reason.isNotBlank())
        }
        assertTrue(rows.any { it.completion.startsWith("{\"clarify\"") } && rows.any { it.completion.startsWith("{\"refuse\"") })
    }

    /** A fact the launcher sends with the request is never looked for, and what is found is in the words the phone's formatter writes. */
    @Test fun aFactIsLookedForOnlyWhenThePhoneWouldNotHaveSentIt() {
        fun sent(query: String, source: String) = com.jake.duolauncher.search.context.ContextPolicy
            .select(com.jake.duolauncher.search.context.PhoneContext.sources, query.lineSequence().first(), action = true, off = emptySet()).any { it.id == source }
        for (row in train + dev + test) {
            if (row.family == "look" && row.completion == "{\"look\":[\"calendar\"]}") assertFalse("the calendar was sent with: ${row.query}", sent(row.query, "calendar"))
            if (row.family == "look") assertFalse("a look with the fact already there: ${row.query}", "Calendar (next 7 days)" in row.user || "Calendar today" in row.user)
            if (row.family.startsWith("found") && "- calendar: " in row.user) assertTrue(row.user, "- calendar: Calendar (next 7 days):; " in row.user || "- calendar: Calendar: nothing in the next 7 days" in row.user)
        }
        assertTrue(train.any { it.family == "fact-given" && "- Calendar (next 7 days):" in it.user })
        assertTrue(train.any { it.family == "found" && "- contacts: " in it.user })
    }

    // ---------------------------------------------------------------------------------------------- the contract in the loop

    private val tools = ToolRegistry.next
    private fun loop(replies: List<String>, env: ToolEnv, asked: MutableList<String>, facts: String = "") = AgentLoop(object : Planner {
        var i = 0
        override suspend fun plan(request: PlanRequest): ParseResult { asked += request.query; return PlanParser.parse(replies[minOf(i++, replies.lastIndex)], request.tools, request.protocol) }
    }, Executor(env, maxSteps = Catalogue.MAX_STEPS_V2, maxPerTool = Executor.MAX_PER_TOOL_V2), tools = tools, confirm = { true }, protocol = Catalogue.V2, facts = { _, _ -> facts })

    @Test fun aPlannerThatLooksIsAskedAgainWithWhatWasFound() = runBlocking {
        val env = RecordingEnv(); val asked = ArrayList<String>()
        val outcome = loop(listOf("""{"look":["contacts"]}""", """{"steps":[{"tool":"dial","params":{"number":"555 0144"}}]}"""), env, asked,
            facts = "- contacts: Mateo (555 0144)").run("give mateo a ring")
        assertTrue("$outcome", outcome is AgentOutcome.Done)
        assertEquals("a number that was found is one the launcher may dial", listOf("dial 5550144"), env.did)
        assertEquals("give mateo a ring\nFound:\n- contacts: Mateo (555 0144)", asked[1])
        // It may not look twice.
        val twice = loop(listOf("""{"look":["contacts"]}""", """{"look":["calendar"]}"""), RecordingEnv(), ArrayList()).run("give mateo a ring")
        assertTrue(twice is AgentOutcome.Failed)
        // Nothing found is said as nothing, per source.
        val nothing = ArrayList<String>()
        loop(listOf("""{"look":["calendar","contacts"]}""", """{"refuse":"I can't see that."}"""), RecordingEnv(), nothing).run("x")
        assertEquals("x\nFound:\n- calendar: nothing\n- contacts: nothing", nothing[1])
    }

    @Test fun aFirstStepThatFailedIsPlannedAgainOnce() = runBlocking {
        val noList = object : ToolEnv by RecordingEnv() { override val checklistSlot: Int? = null; override fun capabilities() = Capability.entries.toSet() }
        val asked = ArrayList<String>()
        val env = RecordingEnv()
        val failing = object : ToolEnv by env { override val checklistSlot: Int? = null }
        val outcome = loop(listOf("""{"steps":[{"tool":"open_app","params":{"app":"Spotify"}}]}""", """{"refuse":"Spotify is not installed on this phone."}"""), failing, asked).run("open Spotify")
        assertEquals(AgentOutcome.Refused("Spotify is not installed on this phone."), outcome)
        assertTrue(asked[1], asked[1].startsWith("open Spotify\nTried: {") && asked[1].contains("\nFailed: "))
        assertEquals("asked twice and no more", 2, asked.size)
        assertTrue(noList.checklistSlot == null)
    }

    @Test fun theFirstContractKnowsNoneOfThis() {
        assertTrue(PlanParser.parse("""{"look":["calendar"]}""", ToolRegistry.builtIn) is ParseResult.Failed)
        assertFalse("look" in Catalogue.grammar(ToolRegistry.builtIn))
        assertTrue(Catalogue.grammar(tools, Catalogue.V2).contains("look ::= "))
        assertTrue((PlanParser.parse("""{"look":["everything"]}""", tools, Catalogue.V2) as ParseResult.Failed).retry)
        val six = "{\"steps\":[" + List(6) { """{"tool":"torch","params":{"on":true}}""" }.joinToString(",") + "]}"
        assertTrue(PlanParser.parse(six, tools, Catalogue.V2) is ParseResult.Failed)
    }

    private companion object { const val PINNED = "81b67dca241ba53c" }
}
