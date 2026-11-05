package com.jake.duolauncher

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every corner in Uno comes from `appearance/Corners.kt`: the `Corner` tokens, `Corner.inside` for anything nested, `ContinuousShape` for a
 * radius derived from geometry (a dock concentric with its icons, a preview drawn to scale), and `continuousPath` for outlines drawn by hand.
 * Compose's `RoundedCornerShape` and the canvas round rects draw circular arcs, not iOS's continuous corner, and a radius typed in place is
 * how the scale drifted to 27 values before. This guards the rule: one of those in the sources fails the build.
 *
 * `CircleShape` is allowed (a true circle has no corner to smooth). The keyboard is held to it too. A line that is drawn art
 * rather than a surface (a glyph) may say so with a `corner-ok:` comment and a reason.
 */
class CornerScaleTest {
    private val circular = Regex("""\b(RoundedCornerShape|CornerRadius|RoundRect|drawRoundRect|addRoundRect)\(""")
    private val exempt = listOf("appearance/Corners.kt")

    private fun sources(): List<File> {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { file -> exempt.none { file.invariantSeparatorsPath.contains("/duolauncher/$it") } }.toList()
    }

    @Test fun noCornerIsDrawnOutsideTheScale() {
        val found = sources().flatMap { file ->
            file.readLines().mapIndexedNotNull { i, line ->
                val code = line.substringBefore("//")
                if (circular.containsMatchIn(code) && "corner-ok:" !in line && !line.trimStart().startsWith("*")) "${file.name}:${i + 1}: ${line.trim()}" else null
            }
        }
        assertTrue("Use a Corner token, Corner.inside, ContinuousShape or continuousPath (appearance/Corners.kt):\n" + found.joinToString("\n"),
            found.isEmpty())
    }

    @Test fun theRuleSeesWhatItIsMeantToCatch() {
        assertTrue(circular.containsMatchIn("Modifier.clip(RoundedCornerShape(12.dp))"))
        assertTrue(circular.containsMatchIn("drawRoundRect(fill, cornerRadius = CornerRadius(4f))"))
        assertTrue(!circular.containsMatchIn("Modifier.clip(CircleShape)"))
        assertTrue(!circular.containsMatchIn("Modifier.clip(Corner.inside(Corner.LARGE_DP.dp, 3.dp))"))
    }
}
