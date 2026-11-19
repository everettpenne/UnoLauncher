package com.jake.duolauncher.agent.training

import com.jake.duolauncher.search.agent.EvalRow
import com.jake.duolauncher.search.agent.ParseResult
import com.jake.duolauncher.search.agent.Plan
import com.jake.duolauncher.search.agent.PlanParser
import com.jake.duolauncher.search.agent.EvalSet
import com.jake.duolauncher.search.agent.ToolRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Scores a model's replies with the real parser: `SCORE_PLANS=path/to/dir ./gradlew :app:testDebugUnitTest --tests '*ScorePlans*'`.
 *
 * The directory holds `rows.json` (the eval set, written by [EmitTrainingSet]) and `replies.jsonl` (`{"query":…,"reply":…}` per line, plus an
 * optional `"tokens"`). Writes `score.json` and prints the summary and the failures, so a training run is judged by the app's own rules.
 */
class ScorePlans {
    @Test fun score() {
        val path = System.getenv("SCORE_PLANS")
        assumeTrue("set SCORE_PLANS=<dir> to score replies", !path.isNullOrBlank())
        val dir = File(path!!)
        // The frozen set by default; ROWS_FILE=rows-fresh.json scores the held-out paraphrase set.
        val rowsFile = System.getenv("ROWS_FILE") ?: "rows.json"
        val rows = EvalSet.parse(File(dir, rowsFile).readText())
        val replies = File(dir, "replies.jsonl").readLines().filter { it.isNotBlank() }.associate { line ->
            val obj = JSONObject(line)
            obj.getString("query") to obj.optString("reply")
        }
        val report = PlanScorer.score(rows, replies, ToolRegistry.builtIn)
        // Per family, for a rows file that says which family each row is (the dev set): one number hides which kind of row moved.
        val families = runCatching {
            val array = JSONArray(File(dir, rowsFile).readText())
            (0 until array.length()).associate { i -> array.getJSONObject(i).let { it.getString("q").trim() to it.optString("family") } }
        }.getOrDefault(emptyMap()).filterValues { it.isNotEmpty() }
        val byFamily = JSONObject()
        report.rows.groupBy { families[it.query] }.filterKeys { it != null }.toSortedMap(compareBy { it }).forEach { (family, scored) ->
            byFamily.put(family, JSONObject().put("exact", scored.count { it.exact }).put("total", scored.size))
        }
        // A row that should have asked, and acted on something nobody gave: the launcher's own check turns that into the question.
        val caught = report.rows.count { score ->
            score.expected is EvalRow.Expected.Clarify && !score.exact && score.reply != null &&
                ((PlanParser.parse(score.reply, ToolRegistry.builtIn) as? ParseResult.Ok)?.plan as? Plan.Steps)
                    ?.steps?.any { it.tool.missing(it.params, score.query) != null } == true
        }
        val out = JSONObject()
            .put("byFamily", byFamily)
            .put("askedByTheLauncherAnyway", caught)
            .put("exact", report.exact)
            .put("total", report.total)
            .put("exactRate", report.exactRate)
            .put("toolRate", report.toolRate)
            .put("paramRate", report.paramRate)
            .put("clarifyRate", report.clarifyRate())
            .put("refuseRate", report.refuseRate())
            .put("missedActions", report.missedActions)
            .put("rowsFile", rowsFile)
            .put("failures", JSONArray(report.failures(50)))
        File(dir, "score.json").writeText(out.toString(2))
        println(report.summary())
        report.failures().forEach { println("  $it") }
    }
}
