package com.jake.duolauncher.agent.training

import com.jake.duolauncher.search.agent.ToolRegistry
import org.json.JSONArray
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Makes eval rows from an exported assistant log: `ASSISTANT_LOG=path/to/assistant-log.jsonl ./gradlew :app:testDebugUnitTest --tests '*ImportAssistantLog*'`.
 *
 * Beside the log it writes `log-rows.json` and `log-rows-turns.json` (turns rated up: ready to score a model on, and to add to the
 * eval set) and `log-review.json` (turns that went wrong: each needs its `expect` written before it is a row). The log is the person's
 * own and stays where they put it; nothing is copied into the repository.
 */
class ImportAssistantLog {
    @Test fun import() {
        val path = System.getenv("ASSISTANT_LOG")
        assumeTrue("set ASSISTANT_LOG=<file> to make rows from an exported log", !path.isNullOrBlank())
        val file = File(path!!)
        val entries = LogRows.read(file.readText())
        val kept = LogRows.kept(entries, ToolRegistry.builtIn)
        val review = LogRows.review(entries)
        File(file.parentFile, "log-rows.json").writeText(JSONArray(kept).toString(1))
        File(file.parentFile, "log-rows-turns.json").writeText(JSONArray(LogRows.turns(entries, kept)).toString(1))
        File(file.parentFile, "log-review.json").writeText(JSONArray(review).toString(1))
        println("${entries.size} turns in the log, ${LogRows.planned(entries).size} planned: ${kept.size} rows kept, ${review.size} to review")
    }
}
