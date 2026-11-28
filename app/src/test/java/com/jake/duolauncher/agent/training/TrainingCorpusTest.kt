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
        assertEquals(corpus.map { Triple(it.query, it.reply, it.grounding) }, TrainingCorpus.generate(tools).map { Triple(it.query, it.reply, it.grounding) })
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
                        is Plan.OfferSearch -> TrainingFormat.search(plan.query)
                        is Plan.Look -> example.reply
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
                    is Plan.OfferSearch -> !TrainingFormat.grammarSafe(plan.query)
                    is Plan.Look -> false
                }
            }
        }
        assertTrue("replies the grammar could not have produced: ${bad.take(5).map { it.query }}", bad.isEmpty())
    }

    @Test fun everyToolHasEnoughExamples() {
        val counts = corpus.mapNotNull { example ->
            (PlanParser.parse(example.reply, tools) as? ParseResult.Ok)?.plan?.let { it as? Plan.Steps }
        }.flatMap { plan -> plan.steps.map { it.tool.name } }.groupingBy { it }.eachCount()
        val thin = tools.map { it.name }.filter { (counts[it] ?: 0) < 100 }
        assertTrue("tools with fewer than a hundred examples: $thin (counts $counts)", thin.isEmpty())
    }

    @Test fun everyParameterAndPanelIsExercised() {
        val steps = corpus.mapNotNull { ((PlanParser.parse(it.reply, tools) as? ParseResult.Ok)?.plan as? Plan.Steps)?.steps }.flatten()
        val panels = steps.filter { it.tool.name == "settings_panel" }.map { it.params.values["panel"] }
        for (panel in listOf("wifi", "bluetooth", "nfc", "airplane", "display", "sound", "battery")) {
            assertTrue("settings panel $panel appears ${panels.count { it == panel }} times", panels.count { it == panel } >= 10)
        }
        val replies = steps.filter { it.tool.name == "reply" }
        assertTrue("reply tool used ${replies.size} times", replies.size >= 400)
        val alarms = steps.filter { it.tool.name == "alarm" }.map { com.jake.duolauncher.search.agent.SpokenTime.alarm(it.params.values["when"].orEmpty()) }
        assertTrue("alarm never set for a clock time", alarms.any { it is com.jake.duolauncher.search.agent.SpokenTime.Alarm.At })
        assertTrue("alarm never set for a length from now", alarms.any { it is com.jake.duolauncher.search.agent.SpokenTime.Alarm.In })
        for (action in listOf("start", "stop", "reset")) assertTrue("stopwatch never asked to $action", steps.any { it.tool.name == "stopwatch" && it.params.values["action"] == action })
        for (on in listOf("true", "false")) assertTrue("torch never set $on", steps.any { it.tool.name == "torch" && it.params.values["on"] == on })
        assertTrue("an event with no length said", steps.any { it.tool.name == "calendar_event" && it.params.values["length"].isNullOrEmpty() })
        assertTrue("a text with its words", steps.any { it.tool.name == "message" && !it.params.values["text"].isNullOrEmpty() })
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

    @Test fun everyFamilyHasItsShare() {
        val by = corpus.groupingBy { it.family }.eachCount()
        val want = mapOf("single" to 2500, "multi" to 700, "reply" to 550, "clarify" to 350, "refuse" to 300, "search" to 200,
            "answered" to 350, "running" to 150, "grounding" to 200)
        for ((family, least) in want) assertTrue("$family rows: ${by[family]} of $by", (by[family] ?: 0) >= least)
        assertTrue("corpus is ${corpus.size}", corpus.size >= 6000)
        // Asking, refusing and offering a search are each a real share of what the planner sees, not a rounding error.
        fun share(prefix: String) = corpus.count { it.reply.startsWith(prefix) } * 100 / corpus.size
        assertTrue("clarify ${share("{\"clarify\"")}%", share("{\"clarify\"") >= 8)
        assertTrue("refuse ${share("{\"refuse\"")}%", share("{\"refuse\"") >= 5)
        assertTrue("search ${share("{\"search\"")}%", share("{\"search\"") >= 3)
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

    @Test fun anUnusedOptionalValueIsTheEmptyStringThePromptAsksFor() {
        val bare = corpus.first { it.family == "single" && it.reply.contains("\"tool\":\"timer\"") && it.reply.contains("\"label\":\"\"") }
        assertTrue(bare.reply, bare.reply.startsWith("{\"steps\":[{\"tool\":\"timer\",\"params\":{\"length\":\""))
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

    // ---- the corpus against what the launcher sends and what the launcher will accept ----

    @Test fun rowsCarryThePhonesFactsAsTheLauncherSendsThemAndNothingWrittenByOthers() {
        val withFacts = corpus.count { it.context.startsWith("Phone context (from this phone, now):\n- Today: ") }
        assertTrue("only $withFacts of ${corpus.size} rows have the production context", withFacts > corpus.size * 8 / 10)
        assertTrue("some rows must go with no facts, as when every source is off", corpus.any { it.context.isEmpty() })
        // A request that may act never carries a message, a headline, the clipboard or the screen.
        assertTrue(corpus.none { it.context.contains("written by others") })
        assertTrue(corpus.none { it.context.contains("Local time:") })
    }

    @Test fun aReplyIsShownWhoIsWaitingAndThePersonMeantIsAmongThem() {
        val replies = corpus.filter { it.reply.contains("\"tool\":\"reply\"") }
        assertTrue(replies.size > 100)
        val bad = replies.filter { example ->
            val to = Regex("\"to\":\"([^\"]*)\"").find(example.reply)!!.groupValues[1]
            val named = to.lowercase() !in setOf("that", "it", "this", "them", "the last message", "the message")
            !example.grounding.startsWith("Messages waiting for a reply (newest first): ") || (named && !example.grounding.contains(to, ignoreCase = true))
        }
        assertTrue("replies without their waiting messages: ${bad.take(5).map { it.query + " | " + it.grounding }}", bad.isEmpty())
    }

    private fun stepsOf(example: Example): Plan.Steps? = ((PlanParser.parse(example.reply, tools) as? ParseResult.Ok)?.plan as? Plan.Steps)

    @Test fun noRowIsAPlanTheLauncherWouldSecondGuess() {
        // The launcher asks before running a plan that is not the kind of thing the request named. A training row like that would
        // teach the model plans that always meet a question.
        val flagged = corpus.filter { e -> stepsOf(e)?.let { com.jake.duolauncher.search.agent.RequestKinds.mismatch(e.query, it) } == true }
        assertTrue("${flagged.size} rows the kind check would question: ${flagged.take(8).map { it.query + " => " + it.reply }}", flagged.isEmpty())
    }

    @Test fun noRowActsOnSomethingThePersonDidNotGive() {
        // A number, an address, a time or a length the person did not give is asked for by the launcher, so a row that carries one
        // would teach a plan that always meets a question.
        val flagged = corpus.mapNotNull { e ->
            stepsOf(e)?.steps?.firstNotNullOfOrNull { it.tool.missing(it.params, e.query + "\n" + e.grounding) }?.let { e.query + " => " + e.reply + " (" + it.question + ")" }
        }
        assertTrue("${flagged.size} rows with something made up: ${flagged.take(8)}", flagged.isEmpty())
    }

    @Test fun theSameWordsWithDifferentFactsAreDifferentRows() {
        // What tells them apart is only what the phone knows: a message waiting, a number known, what is running.
        fun facts(e: Example) = e.grounding + "|" + e.context.lines().filter { it.startsWith("- Timer") || it.startsWith("- Stopwatch") || it.startsWith("- Playing") }
        val several = corpus.groupBy { it.query }.filter { (_, rows) -> rows.map { it.reply }.toSet().size > 1 }
        assertTrue("only ${several.size} requests appear with more than one right answer", several.size >= 40)
        val clash = several.flatMap { (_, rows) -> rows.groupBy { facts(it) }.filter { (_, same) -> same.map { it.reply }.toSet().size > 1 }.values.map { it.first() } }
        assertTrue("two answers for the same facts: ${clash.take(4).map { it.query + " | " + facts(it) }}", clash.isEmpty())
        // A text to a name always has that name's number in front of it, and no message of theirs waiting.
        val texts = corpus.filter { it.family == "reply" && it.reply.contains("\"tool\":\"message\"") }
        assertTrue("texts in the reply family: ${texts.size}", texts.size >= 100)
        assertTrue(texts.all { it.grounding.contains("People: ") })
    }

    @Test fun noRowAsksThePlannerToCalculate() {
        // A time or a length is the person's words, not a number worked out from them.
        val steps = corpus.mapNotNull { e -> stepsOf(e)?.let { e to it } }
        val worked = steps.filter { (e, plan) ->
            plan.steps.any { step ->
                listOf("length", "when", "start").any { name ->
                    val value = step.params.values[name].orEmpty()
                    value.isNotEmpty() && !e.query.contains(value, ignoreCase = true)
                }
            }
        }
        assertTrue("${worked.size} rows whose time is not in the request: ${worked.take(5).map { it.first.query + " => " + it.first.reply }}", worked.isEmpty())
    }

    @Test fun theDevSetSharesNothingWithTraining() {
        val dev = TrainingCorpus.dev(tools)
        assertTrue("dev set is ${dev.size}", dev.size in 350..700)
        assertEquals("dev requests are scored by their words, so each appears once", dev.size, dev.map { it.query }.toSet().size)
        val trained = corpus.map { it.query.lowercase() }.toSet()
        assertTrue(dev.none { it.query.lowercase() in trained })
        // Its people and its values are ones training never saw.
        for (name in listOf("Noor", "Felix", "Ingrid", "Mateo", "Yuki", "Bram")) assertTrue(name, corpus.none { it.query.contains(name) })
        for (value in listOf("35 minutes", "the ferry terminal", "oat milk", "physio", "signal.org")) assertTrue(value, corpus.none { it.query.contains(value) })
        val by = dev.groupingBy { it.family }.eachCount()
        for (family in listOf("single", "multi", "reply", "clarify", "refuse", "search", "answered", "running", "grounding")) assertTrue("dev $family: ${by[family]}", (by[family] ?: 0) >= 8)
        // Every dev row is one the launcher would run as written.
        val flagged = dev.mapNotNull { e -> stepsOf(e)?.steps?.firstNotNullOfOrNull { it.tool.missing(it.params, e.query + "\n" + e.grounding) }?.let { e.query + " => " + e.reply } }
        assertTrue("dev rows with something made up: ${flagged.take(5)}", flagged.isEmpty())
    }

    @Test fun threeThingsAtOnceAreWellRepresented() {
        val three = corpus.count { stepsOf(it)?.steps?.size == 3 }
        assertTrue("only $three three-step rows", three >= 100)
    }

    @Test fun anEvalRowThatRepliesIsSentTheMessageItAnswers() {
        // org.json writes "params" and "to" in any order; the waiting message must be found either way.
        listOf("""{"steps":[{"params":{"text":"hi","to":"Sam"},"tool":"reply"}]}""", """{"steps":[{"tool":"reply","params":{"to":"Sam","text":"hi"}}]}""").forEach { reply ->
            val dressed = TrainingCorpus.dressed(Example("reply to Sam saying hi", reply, "eval"), kotlin.random.Random(1))
            assertTrue(dressed.grounding, dressed.grounding.contains("Sam"))
        }
    }

    @Test fun aNoteOrListItemThatNamesSomeoneIsDoneNotAsked() {
        val mentions = corpus.filter { it.family == "mentions" }
        assertTrue(mentions.size >= 150)
        assertTrue(mentions.all { it.reply.startsWith("{\"steps\"") })
        val askedForNumber = corpus.count { it.reply.contains("I don't have a number") }
        assertTrue("number questions: $askedForNumber", askedForNumber in 60..160)
    }
}
