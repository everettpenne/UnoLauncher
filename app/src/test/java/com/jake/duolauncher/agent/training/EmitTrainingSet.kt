package com.jake.duolauncher.agent.training

import com.jake.duolauncher.search.agent.ToolRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Writes the corpus for training: `EMIT_TRAINING_SET=1 ./gradlew :app:testDebugUnitTest --tests '*EmitTrainingSet*'`.
 *
 * Output (in `app/build/agent-training/`): `system.txt` (the system turn, shared by every example), `train.jsonl`/`val.jsonl`
 * (`{"user":…,"completion":…}`, one example per line), `manifest.json` (what catalogue the corpus is for), `rows.json`, `rows-fresh.json` and `rows-followup.json` (the frozen eval sets) and
 * `rows-dev.json` (the dev set), each with a `-turns.json` holding the prompt the phone would send for every row.
 * A test rather than a Gradle task so it reuses the app's unit-test classpath and the real registry with no build-file changes.
 */
class EmitTrainingSet {
    @Test fun write() {
        assumeTrue("set EMIT_TRAINING_SET=1 to write the corpus", System.getenv("EMIT_TRAINING_SET") == "1")
        val tools = ToolRegistry.builtIn
        val all = TrainingCorpus.generate(tools)
        val dir = File(System.getenv("EMIT_TRAINING_SET_DIR") ?: "build/agent-training").apply { mkdirs() }
        File(dir, "system.txt").writeText(TrainingFormat.system(tools))

        val train = ArrayList<Example>()
        val validation = ArrayList<Example>()
        all.forEachIndexed { index, example -> if (index % 7 == 0) validation += example else train += example }
        File(dir, "train.jsonl").writeText(train.joinToString("\n") { line(it) } + "\n")
        File(dir, "val.jsonl").writeText(validation.joinToString("\n") { line(it) } + "\n")

        // rows-followup.json: a request, the question the planner asked and the person's answer; the plan should act on the answer.
        for ((name, resource) in listOf("rows.json" to "/agent/phrases.json", "rows-fresh.json" to "/agent/phrases-fresh.json",
                "rows-followup.json" to "/agent/phrases-followup.json")) {
            val text = javaClass.getResourceAsStream(resource)?.bufferedReader()?.readText()
                ?: error("$resource is missing from the test resources")
            File(dir, name).writeText(text)
            // The same rows as the planner would be sent them: with the phone's facts and, for a reply, the messages waiting. A score
            // taken on a bare "Request: …" is a score on a prompt the phone never sends.
            val rnd = kotlin.random.Random(TrainingCorpus.SEED + name.length)
            val rows = JSONArray(text)
            val turns = JSONArray()
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val reply = row.optJSONArray("steps")?.let { JSONObject().put("steps", it).toString() }.orEmpty()
                val dressed = TrainingCorpus.dressed(Example(row.getString("q"), reply, "eval"), rnd)
                turns.put(JSONObject().put("q", dressed.query).put("user", TrainingFormat.turn(dressed.query, dressed.context, dressed.grounding)))
            }
            File(dir, name.removeSuffix(".json") + "-turns.json").writeText(turns.toString(1))
        }

        // The dev set: rows built from names, values and phrasings the training set never uses, with the facts each was built with.
        val dev = TrainingCorpus.dev(tools)
        val devRows = JSONArray()
        val devTurns = JSONArray()
        for (example in dev) {
            val reply = JSONObject(example.reply)
            val row = JSONObject().put("q", example.query).put("family", example.family)
            when {
                reply.has("steps") -> row.put("steps", reply.getJSONArray("steps"))
                reply.has("clarify") -> row.put("clarify", true)
                reply.has("refuse") -> row.put("refuse", true)
                reply.has("search") -> row.put("search", true)
            }
            devRows.put(row)
            devTurns.put(JSONObject().put("q", example.query).put("user", TrainingFormat.turn(example.query, example.context, example.grounding)))
        }
        File(dir, "rows-dev.json").writeText(devRows.toString(1))
        File(dir, "rows-dev-turns.json").writeText(devTurns.toString(1))

        val byFamily = all.groupingBy { it.family }.eachCount()
        val perTool = java.util.TreeMap<String, Int>()
        val perPanel = java.util.TreeMap<String, Int>()
        for (example in all) {
            val steps = runCatching { JSONObject(example.reply).optJSONArray("steps") }.getOrNull() ?: continue
            for (i in 0 until steps.length()) {
                val step = steps.optJSONObject(i) ?: continue
                val tool = step.optString("tool")
                perTool[tool] = (perTool[tool] ?: 0) + 1
                if (tool == "settings_panel") {
                    val panel = step.optJSONObject("params")?.optString("panel").orEmpty()
                    perPanel[panel] = (perPanel[panel] ?: 0) + 1
                }
            }
        }
        val manifest = JSONObject()
            .put("formatVersion", TrainingFormat.VERSION)
            .put("catalogueHash", TrainingFormat.catalogueHash(tools))
            .put("tools", JSONArray(tools.map { it.name }))
            .put("seed", TrainingCorpus.SEED)
            .put("examples", all.size)
            .put("train", train.size)
            .put("val", validation.size)
            .put("dev", dev.size)
            .put("byFamily", JSONObject(byFamily))
            .put("perTool", JSONObject(perTool as Map<*, *>))
            .put("perPanel", JSONObject(perPanel as Map<*, *>))
            .put("evalRows", JSONArray(File(dir, "rows.json").readText()))
            .put("replyShape", "compact JSON, numbers and booleans bare, text and enums quoted, every parameter present")
            .put("deferred", "answer-mode examples: Lab owns that prompt; generated with the answer set in Phase 2")
        File(dir, "manifest.json").writeText(manifest.toString(2))
        println("corpus: ${all.size} examples (train ${train.size}, val ${validation.size}), hash ${manifest.getString("catalogueHash")} -> $dir")
    }

    private fun line(example: Example): String = JSONObject()
        .put("user", TrainingFormat.turn(example.query, example.context, example.grounding))
        .put("completion", example.reply)
        .put("query", example.query)
        .put("family", example.family)
        .toString()
}
