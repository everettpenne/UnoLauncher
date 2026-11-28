package com.jake.duolauncher.agent.training

import com.jake.duolauncher.search.agent.EvalSet
import com.jake.duolauncher.search.agent.ToolRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Scores a model's replies by what they come to: `SCORE_OUTCOMES=path/to/dir ./gradlew :app:testDebugUnitTest --tests '*ScoreOutcomes*'`.
 *
 * The directory is the one [ScorePlans] reads (`rows.json`, `replies.jsonl`), with `turns.json` beside them when there is one (the
 * `rows-…-turns.json` [EmitTrainingSet] wrote: each request as it was sent, with its facts). Writes `outcomes.json`: how many turns
 * ended as they should, the misses counted by cause, and each miss with what was wanted and what happened.
 */
class ScoreOutcomes {
    @Test fun score() {
        val path = System.getenv("SCORE_OUTCOMES")
        assumeTrue("set SCORE_OUTCOMES=<dir> to score replies", !path.isNullOrBlank())
        val dir = File(path!!)
        val rows = EvalSet.parse(File(dir, "rows.json").readText())
        val replies = File(dir, "replies.jsonl").readLines().filter { it.isNotBlank() }.associate { line ->
            JSONObject(line).let { it.getString("query").trim() to it.optString("reply") }
        }
        val turns = File(dir, "turns.json").takeIf { it.exists() }?.let { file ->
            JSONArray(file.readText()).let { a -> (0 until a.length()).associate { i -> a.getJSONObject(i).let { it.getString("q").trim() to it.getString("user") } } }
        }.orEmpty()
        val scored = OutcomeScorer.score(rows, replies, turns, ToolRegistry.builtIn)
        val misses = scored.filter { !it.same }
        val causes = misses.groupingBy { it.cause!! }.eachCount().toSortedMap()
        val out = JSONObject()
            .put("total", scored.size)
            .put("same", scored.count { it.same })
            .put("planExact", scored.count { it.planExact })
            // A plan that is not word for word the expected one, and still comes to the same thing: not a failure.
            .put("planDiffersOutcomeSame", scored.count { !it.planExact && it.same })
            // The expected plan, word for word, and the turn still ends otherwise: cannot happen unless the loop is not a function of the plan.
            .put("planExactOutcomeDiffers", scored.count { it.planExact && !it.same })
            .put("causes", JSONObject(causes as Map<*, *>))
            .put("misses", JSONArray(misses.map { m ->
                JSONObject().put("q", m.query).put("cause", m.cause).put("reply", m.reply)
                    .put("wanted", JSONObject().put("kind", m.wanted.kind).put("did", JSONArray(m.wanted.did)).put("said", m.wanted.said))
                    .put("got", JSONObject().put("kind", m.got.kind).put("did", JSONArray(m.got.did)).put("said", m.got.said))
            }))
        File(dir, "outcomes.json").writeText(out.toString(2))
        println("same outcome ${scored.count { it.same }}/${scored.size} · plan exact ${scored.count { it.planExact }} · $causes")
    }
}
