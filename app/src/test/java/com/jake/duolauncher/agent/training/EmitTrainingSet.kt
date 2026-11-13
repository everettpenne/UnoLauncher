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
 * (`{"user":…,"completion":…}`, one example per line), `manifest.json` (what catalogue the corpus is for), and `rows.json` (the eval set).
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

        val rows = javaClass.getResourceAsStream("/agent/phrases.json")?.bufferedReader()?.readText()
            ?: error("the eval set is missing from the test resources")
        File(dir, "rows.json").writeText(rows)

        val byFamily = all.groupingBy { it.family }.eachCount()
        val manifest = JSONObject()
            .put("formatVersion", TrainingFormat.VERSION)
            .put("catalogueHash", TrainingFormat.catalogueHash(tools))
            .put("tools", JSONArray(tools.map { it.name }))
            .put("seed", TrainingCorpus.SEED)
            .put("examples", all.size)
            .put("train", train.size)
            .put("val", validation.size)
            .put("byFamily", JSONObject(byFamily))
            .put("evalRows", JSONArray(rows))
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
