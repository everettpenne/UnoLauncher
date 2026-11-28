package com.jake.duolauncher.agent.training

import com.jake.duolauncher.search.agent.Catalogue
import com.jake.duolauncher.search.agent.ToolRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Writes the protocol-2 corpus: `EMIT_CORPUS_V2=1 ./gradlew :app:testDebugUnitTest --tests '*EmitCorpusV2*'`, into
 * `app/build/agent-training-v2/` (or `EMIT_CORPUS_V2_DIR`).
 *
 *  - `train.jsonl`, `dev.jsonl`, `test.jsonl`, `regression.jsonl`: a line an example, `{"family","query","tools","user","completion"}`.
 *    `tools` is the row's tool list, by name and in order; the system turn and the grammar are put together from `parts.json`.
 *  - `parts.json`: the system turn's head and tail, each tool's block, and the grammar's frame and each tool's rule.
 *  - `samples.jsonl`: some rows whole (`system`, `grammar`), to check that assembly against, character for character.
 *  - `catalogue.txt`, `grammar.gbnf`: the whole list, as a phone with everything allowed sends it.
 *  - `manifest.json`: what this corpus is for and how big it is.
 */
class EmitCorpusV2 {
    private fun line(r: Row2, whole: Boolean = false) = JSONObject().put("family", r.family).put("query", r.query).put("tools", JSONArray(r.tools))
        .put("user", r.user).put("completion", r.completion).apply { if (whole) { put("system", r.system); put("grammar", r.grammar) } }.toString()

    @Test fun write() {
        assumeTrue("set EMIT_CORPUS_V2=1 to write the corpus", System.getenv("EMIT_CORPUS_V2") == "1")
        val dir = File(System.getenv("EMIT_CORPUS_V2_DIR") ?: "build/agent-training-v2").apply { mkdirs() }
        val counts = JSONObject()
        val samples = ArrayList<String>()
        fun write(name: String, rows: List<Row2>) {
            File(dir, "$name.jsonl").bufferedWriter().use { out -> for (row in rows) { out.write(line(row)); out.write("\n") } }
            counts.put(name, JSONObject(rows.groupingBy { it.family }.eachCount().toSortedMap() as Map<*, *>).put("_total", rows.size))
            rows.filterIndexed { i, _ -> i % (rows.size / 12 + 1) == 0 }.forEach { samples += line(it, whole = true) }
        }
        for (split in Split2.entries) write(split.name.lowercase(), CorpusV2.build(split))
        write("regression", listOf("/agent/phrases.json", "/agent/phrases-fresh.json", "/agent/phrases-followup.json").flatMap { CorpusV2.regression(it) })
        File(dir, "samples.jsonl").writeText(samples.joinToString("\n") + "\n")

        val (before, after) = CorpusV2.grammarFrame()
        File(dir, "parts.json").writeText(JSONObject()
            .put("systemHead", CorpusV2.systemHead).put("systemTail", CorpusV2.SYSTEM_TAIL)
            .put("toolBlocks", JSONObject(CorpusV2.everyTool.associate { it.name to CorpusV2.toolBlock(it) }))
            .put("grammarBefore", JSONArray(before)).put("grammarAfter", JSONArray(after))
            .put("grammarRules", JSONObject(CorpusV2.everyTool.associate { tool -> CorpusV2.grammarLine(tool).let { tool.name to JSONObject().put("name", it.first).put("line", it.second) } }))
            .toString(1))
        File(dir, "catalogue.txt").writeText(CorpusV2.system(ToolRegistry.next))
        File(dir, "grammar.gbnf").writeText(Catalogue.grammar(ToolRegistry.next, Catalogue.V2))
        File(dir, "manifest.json").writeText(JSONObject()
            .put("protocol", Catalogue.V2).put("seed", CorpusV2.SEED)
            .put("catalogueHash", Catalogue.hash(ToolRegistry.next, Catalogue.V2))
            .put("tools", JSONArray(ToolRegistry.next.map { it.name }))
            .put("madeUpTools", JSONArray(CorpusV2.madeUp.map { it.tool.name }))
            .put("heldOutTools", JSONArray(CorpusV2.heldOut.map { it.tool.name }))
            .put("maxSteps", Catalogue.MAX_STEPS_V2)
            .put("launcherCommit", System.getenv("LAUNCHER_COMMIT") ?: "uncommitted")
            .put("counts", counts).toString(2))
        println("wrote ${dir.absolutePath}: $counts")
    }
}
