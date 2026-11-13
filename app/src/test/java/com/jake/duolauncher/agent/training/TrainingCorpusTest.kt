package com.jake.duolauncher.agent.training

import com.jake.duolauncher.search.agent.EvalRow
import com.jake.duolauncher.search.agent.EvalSet
import com.jake.duolauncher.search.agent.ParseResult
import com.jake.duolauncher.search.agent.Plan
import com.jake.duolauncher.search.agent.PlanParser
import com.jake.duolauncher.search.agent.ToolRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The corpus is code, and this keeps it honest: every reply has to parse through the same parser a model's reply goes through, the canonical
 * form has to survive that parse unchanged, every tool has to be represented, and the eval set has to stay out of training.
 */
class TrainingCorpusTest {
    private val tools = ToolRegistry.builtIn
    private val corpus = TrainingCorpus.generate(tools)

    @Test fun theCorpusIsDeterministic() {
        assertEquals(corpus.map { it.query to it.reply }, TrainingCorpus.generate(tools).map { it.query to it.reply })
    }

    @Test fun everyReplyParsesAndSurvivesTheRoundTrip() {
        val failures = corpus.mapNotNull { example ->
            when (val parsed = PlanParser.parse(example.reply, tools)) {
                is ParseResult.Failed -> "${example.query}: ${parsed.why}"
                is ParseResult.Ok -> {
                    val again = when (val plan = parsed.plan) {
                        is Plan.Steps -> TrainingFormat.steps(plan.steps.map { it.tool to it.params.values })
                        is Plan.Clarify -> TrainingFormat.clarify(plan.question)
                        is Plan.Refuse -> TrainingFormat.refuse(plan.reason)
                    }
                    if (again != example.reply) "${example.query}: canonical form changed\n  was ${example.reply}\n  now $again" else null
                }
            }
        }
        assertTrue("examples the parser does not accept:\n${failures.take(10).joinToString("\n")}", failures.isEmpty())
    }

    @Test fun everyReplyIsWithinTheGrammarsAlphabet() {
        val bad = corpus.filter { example ->
            when (val parsed = PlanParser.parse(example.reply, tools)) {
                is ParseResult.Failed -> true
                is ParseResult.Ok -> when (val plan = parsed.plan) {
                    is Plan.Steps -> plan.steps.any { step -> step.params.values.values.any { !TrainingFormat.grammarSafe(it) } }
                    is Plan.Clarify -> !TrainingFormat.grammarSafe(plan.question)
                    is Plan.Refuse -> !TrainingFormat.grammarSafe(plan.reason)
                }
            }
        }
        assertTrue("replies the grammar could not have produced: ${bad.take(5).map { it.query }}", bad.isEmpty())
    }

    @Test fun everyToolHasEnoughExamples() {
        val counts = corpus.mapNotNull { example ->
            (PlanParser.parse(example.reply, tools) as? ParseResult.Ok)?.plan?.let { it as? Plan.Steps }
        }.flatMap { plan -> plan.steps.map { it.tool.name } }.groupingBy { it }.eachCount()
        val thin = tools.map { it.name }.filter { (counts[it] ?: 0) < 40 }
        assertTrue("tools with fewer than forty examples: $thin (counts $counts)", thin.isEmpty())
    }

    @Test fun multiStepPlansRespectTheLoopsCaps() {
        val plans = corpus.mapNotNull {
            ((PlanParser.parse(it.reply, tools) as? ParseResult.Ok)?.plan as? Plan.Steps)?.steps
        }
        assertTrue("no three-step plan in the corpus", plans.any { it.size == 3 })
        assertTrue("no two-step plan in the corpus", plans.any { it.size == 2 })
        assertTrue("a plan exceeds three steps", plans.all { it.size <= 3 })
        assertTrue("a tool appears more than twice in a plan", plans.all { steps -> steps.groupingBy { it.tool.name }.eachCount().values.all { it <= 2 } })
    }

    @Test fun askingAndRefusingAreBothCovered() {
        assertTrue("clarify rows: ${corpus.count { it.family == "clarify" }}", corpus.count { it.family == "clarify" } >= 250)
        assertTrue("refuse rows: ${corpus.count { it.family == "refuse" }}", corpus.count { it.family == "refuse" } >= 200)
        assertTrue("multi rows: ${corpus.count { it.family == "multi" }}", corpus.count { it.family == "multi" } >= 400)
        assertTrue("grounding rows: ${corpus.count { it.family == "grounding" }}", corpus.count { it.family == "grounding" } >= 300)
        assertTrue("hard rows: ${corpus.count { it.family == "hard" }}", corpus.count { it.family == "hard" } >= 120)
        assertTrue("corpus is ${corpus.size}", corpus.size >= 3000)
    }

    @Test fun theEvalSetIsNotInTheTrainingData() {
        val evalQueries = EvalSet.parse(TrainingCorpusTest::class.java.getResourceAsStream("/agent/phrases.json")!!.bufferedReader().readText())
            .map { it.query.lowercase().trim() }.toSet()
        val leaked = corpus.filter { it.query.lowercase().trim() in evalQueries }
        assertTrue("eval queries in training data: ${leaked.map { it.query }}", leaked.isEmpty())
    }

    @Test fun theSystemAndUserTurnsAreTheServersPrompt() {
        val system = TrainingFormat.system(tools)
        assertEquals("<|im_start|>system\n", system.take("<|im_start|>system\n".length))
        assertTrue(system.contains(TrainingFormat.SYSTEM))
        assertTrue(system.contains("Tools:"))
        assertTrue(system.endsWith("<|im_end|>\n"))
        assertTrue(system.contains("timer"))
        assertEquals("<|im_start|>user\nRequest: set a timer for 10 minutes<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n",
            TrainingFormat.turn("set a timer for 10 minutes"))
        val withFacts = TrainingFormat.turn("text Sam", context = "Local time: 12:25", grounding = "People: Sam Patel (+1 555 0100)")
        assertTrue(withFacts.contains("<context>\nLocal time: 12:25\n</context>"))
        assertTrue(withFacts.contains("<known>\nPeople: Sam Patel (+1 555 0100)\n</known>"))
        assertEquals(system + TrainingFormat.turn("open Camera"), TrainingFormat.prompt(tools, "open Camera"))
    }

    @Test fun theSystemTextMatchesUnoLabsPlanner() {
        // Pinned here and in Uno Lab's PlannerTest: the corpus is only valid for the prompt the phone actually uses.
        val expected = "You are the planner for Uno, a phone assistant. Turn what the person asks into the JSON described below. " +
            "Use only the tools listed. Prefer the fewest steps. If a detail is missing (which person, which time), ask a question instead " +
            "of guessing. If it cannot be done with these tools, refuse in one sentence and say what is missing. Reply with JSON only."
        assertEquals(expected, TrainingFormat.SYSTEM)
    }

    @Test fun theExclusionListIsTheRealEvalSet() {
        // If the resource moved, the generator would stop excluding anything and this test is the one that notices.
        val evalRows = EvalSet.parse(TrainingCorpusTest::class.java.getResourceAsStream("/agent/phrases.json")!!.bufferedReader().readText())
        assertTrue(evalRows.size >= 40)
        val leaked = corpus.filter { example -> evalRows.any { it.query.lowercase().trim() == example.query.lowercase().trim() } }
        assertTrue("the generator did not exclude: ${leaked.take(3).map { it.query }}", leaked.isEmpty())
    }

    @Test fun anUnusedOptionalNumberIsTheEmptyStringThePromptAsksFor() {
        val alarm = corpus.first { it.query.startsWith("wake me at") }
        assertTrue(alarm.reply, alarm.reply.contains("\"in_minutes\":\"\""))
        assertTrue(TrainingFormat.steps(listOf(ToolRegistry.byName("alarm")!! to mapOf("at" to "06:30", "in_minutes" to ""))).contains("\"in_minutes\":\"\""))
    }

    @Test fun theFreshHeldOutSetParsesAndIsAlsoExcluded() {
        val fresh = EvalSet.parse(TrainingCorpusTest::class.java.getResourceAsStream("/agent/phrases-fresh.json")!!.bufferedReader().readText())
        assertEquals(57, fresh.size)
        assertTrue(fresh.any { it.expected is EvalRow.Expected.Clarify })
        assertTrue(fresh.any { it.expected is EvalRow.Expected.Refuse })
        assertTrue(fresh.any { (it.expected as? EvalRow.Expected.Steps)?.steps?.size == 2 })
        val leaked = corpus.filter { example -> fresh.any { it.query.lowercase().trim() == example.query.lowercase().trim() } }
        assertTrue("fresh rows in training data: ${leaked.map { it.query }}", leaked.isEmpty())
        // Every fresh row scores exactly against its own reply.
        val report = PlanScorer.score(fresh, fresh.associate { it.query to EvalSet.reply(it) }, tools)
        assertEquals(report.total, report.exact)
    }

    @Test fun theManifestNamesTheCatalogueItWasBuiltFor() {
        assertNotNull(TrainingFormat.catalogueHash(tools))
        assertEquals(16, TrainingFormat.catalogueHash(tools).length)
    }
}
