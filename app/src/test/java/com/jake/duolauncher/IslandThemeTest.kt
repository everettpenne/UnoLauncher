package com.jake.duolauncher

import androidx.compose.ui.graphics.Color
import com.jake.duolauncher.island.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class IslandThemeTest {
    private fun ok(text: String) = (IslandThemeFile.parse(text) as IslandThemeFile.Result.Ok).theme
    private fun rejected(text: String) = (IslandThemeFile.parse(text) as IslandThemeFile.Result.Rejected).reason
    private fun file(colors: String = "", shape: String = "", extra: String = "", name: String = "\"Test\"", version: Int = 1) =
        """{"format":"uno-island-theme","version":$version,"name":$name,"colors":{$colors},"shape":{$shape}$extra}"""

    @Test fun classicIsTodaysLook() {
        val c = IslandTheme.Classic
        assertEquals(Color.Black, c.fill); assertEquals(Color.White, c.ink)
        assertEquals(Color(0xFFFF375F), c.playback); assertEquals(Color(0xFF34C759), c.charging)
        assertEquals(Color(0xFFFF9F0A), c.timer); assertEquals(Color(0xFF7D7AFF), c.focus)
        assertEquals(PanelShape.SOFT, c.panel); assertEquals(ControlShape.PILL, c.controls); assertEquals(IslandMotion.STANDARD, c.motion)
        assertNull(IslandThemeMath.problem(c))
    }

    @Test fun everyBuiltInPresetIsLegible() {
        IslandThemes.presets.forEach { assertNull(it.name, IslandThemeMath.problem(it)) }
        assertEquals(IslandThemes.presets.size, IslandThemes.presets.map { it.name }.toSet().size)
    }

    @Test fun anEmptyThemeIsClassicWithAName() {
        val t = ok(file())
        assertEquals("Test", t.name); assertEquals(IslandTheme.Classic.copy(name = "Test"), t)
    }

    @Test fun aFullThemeReadsEveryField() {
        val t = ok(file(""""fill":"#05071A","ink":"#E9EDFF","playback":"#5E8BFF","charging":"#4fd1c5","timer":"#FFB86B","focus":"#9D8CFF"""",
            """"panel":"round","controls":"Rounded"""", ""","motion":"bouncy"""", name = "\"Dusk\"").replace("\"Dusk\"}", "\"Dusk\"}"))
        assertEquals(Color(0xFF05071A), t.fill); assertEquals(Color(0xFF4FD1C5), t.charging)
        assertEquals(PanelShape.ROUND, t.panel); assertEquals(ControlShape.ROUNDED, t.controls); assertEquals(IslandMotion.BOUNCY, t.motion)
    }

    @Test fun roundTripKeepsEverything() {
        IslandThemes.presets.forEach { assertEquals(it, ok(IslandThemeFile.toJson(it))) }
    }

    @Test fun unknownFieldsAreIgnored() {
        assertEquals("Test", ok(file(extra = ""","future":{"a":1},"icons":["x"]""")).name)
    }

    @Test fun aBrightBodyIsRejected() {
        assertTrue(rejected(file(""""fill":"#FFFFFF","ink":"#000000"""")).contains("too bright"))
        assertTrue(rejected(file(""""fill":"#808080"""")).contains("too bright"))
    }

    @Test fun lowContrastTextAndAccentsAreRejected() {
        assertTrue(rejected(file(""""ink":"#222222"""")).contains("text colour"))
        assertTrue(rejected(file(""""playback":"#101010"""")).contains("playback"))
    }

    @Test fun badColoursAreRejected() {
        listOf("\"red\"", "\"#FFF\"", "\"#GG0000\"", "\"#80FF0000\"", "16711680", "null").forEach {
            assertTrue(it, rejected(file(""""ink":$it""")).contains("ink colour"))
        }
    }

    @Test fun badShapesAndMotionAreRejected() {
        assertTrue(rejected(file(shape = """"panel":"cookie"""")).contains("shape.panel"))
        assertTrue(rejected(file(shape = """"controls":"blob"""")).contains("shape.controls"))
        assertTrue(rejected(file(extra = ""","motion":"warp"""")).contains("motion"))
    }

    @Test fun wrongFormatVersionAndJsonAreRejected() {
        assertTrue(rejected("not json").contains("not JSON"))
        assertTrue(rejected("""{"format":"other","version":1,"name":"x"}""").contains("not an Uno island theme"))
        assertTrue(rejected(file(version = 2)).contains("newer version"))
        assertTrue(rejected(file(version = 0)).contains("version"))
        assertTrue(rejected(file(name = "\"\"")).contains("name"))
        assertTrue(rejected(file(name = "\"${"x".repeat(41)}\"")).contains("name"))
        assertTrue(rejected(file(name = "\"a\\u0007b\"")).contains("name"))
    }

    @Test fun anOversizeFileIsRejectedBeforeParsing() {
        val big = ByteArray(IslandThemeFile.MAX_BYTES + 1) { ' '.code.toByte() }
        assertTrue((IslandThemeFile.parse(big) as IslandThemeFile.Result.Rejected).reason.contains("too large"))
    }

    @Test fun privacyAndSafetyMarksKeepTheirOwnColours() {
        val loud = IslandTheme.Classic.copy(timer = Color(0xFF00FF00), charging = Color(0xFF00FFFF))
        assertEquals(IslandSymbol.RECORDING.tint, loud.tint(IslandSymbol.RECORDING))
        assertEquals(IslandSymbol.VPN.tint, loud.tint(IslandSymbol.VPN))
        assertEquals(IslandSymbol.USB.tint, loud.tint(IslandSymbol.USB))
        assertEquals(Color(0xFF00FF00), loud.tint(IslandSymbol.TIMER)); assertEquals(Color(0xFF00FFFF), loud.tint(IslandSymbol.CHARGING))
    }

    @Test fun contrastMathMatchesWcag() {
        assertEquals(21f, IslandThemeMath.contrast(Color.White, Color.Black), .01f)
        assertEquals(1f, IslandThemeMath.contrast(Color.Black, Color.Black), .001f)
    }

    @Test fun motionTunesSpringsWithinSaneBounds() {
        val bouncy = IslandTheme.Classic.copy(motion = IslandMotion.BOUNCY)
        assertTrue(bouncy.spring<Float>(.1f, 300f).dampingRatio >= .3f)
        assertTrue(IslandTheme.Classic.spring<Float>(5f, 300f).dampingRatio <= 1.2f)
        // Closing is never tuned, so the island still settles onto the camera without overshoot.
        assertEquals(1f, bouncy.spring<Float>(1f, 420f, tunedDamping = false).dampingRatio, 0f)
    }

    @Test fun theDocumentedSampleThemeParses() {
        val doc = File("../docs/island-themes.md").takeIf { it.exists() } ?: File("docs/island-themes.md")
        val json = Regex("```json\\n(.*?)```", RegexOption.DOT_MATCHES_ALL).find(doc.readText())!!.groupValues[1]
        assertTrue(IslandThemeFile.parse(json) is IslandThemeFile.Result.Ok)
    }

    private fun overshoot(damping: Float) = if (damping >= 1f) 0.0 else Math.exp(-Math.PI * damping / Math.sqrt(1.0 - damping * damping))

    @Test fun everyMotionIsLegalAndHasItsOwnPersonality() {
        for (motion in IslandMotion.values()) {
            val theme = IslandTheme.Classic.copy(motion = motion)
            assertTrue(motion.name, theme.heightSpring().dampingRatio in .3f..1.2f && theme.widthSpring().dampingRatio in .3f..1.2f)
            assertEquals("closing never overshoots", 1f, theme.closeSpring().dampingRatio, 0f)
            // Even the wildest bounce has to fit inside the room the island is given (see IslandGeometry.MAX_OVERSHOOT).
            assertTrue(motion.name, 1.0 + maxOf(overshoot(motion.heightDamping), overshoot(motion.widthDamping)) <= IslandGeometry.MAX_OVERSHOOT + .02)
            assertTrue(motion.pulse in 1f..1.3f)
        }
        // Calm barely overshoots and the cartoon motions overshoot a lot (their personalities are pinned in theLivelyMotions... below).
        assertTrue(overshoot(IslandMotion.CALM.heightDamping) < .02)
        assertTrue(overshoot(IslandMotion.BOUNCY.heightDamping) > .15)
        assertTrue(overshoot(IslandMotion.JELLY.heightDamping) > .2)
    }

    @Test fun jellyAndDropletAreValidInAThemeFile() {
        for (name in listOf("jelly", "droplet", "bouncy")) {
            val r = IslandThemeFile.parse("""{"format":"uno-island-theme","version":1,"name":"T","motion":"$name"}""")
            assertEquals(name, ((r as IslandThemeFile.Result.Ok).theme.motion.name.lowercase()))
        }
        assertTrue(IslandThemeFile.parse("""{"format":"uno-island-theme","version":1,"name":"T","motion":"wobbly"}""") is IslandThemeFile.Result.Rejected)
        assertTrue(IslandThemes.presets.any { it.motion == IslandMotion.DROPLET } && IslandThemes.presets.any { it.motion == IslandMotion.JELLY })
    }

    @Test fun jellyBreathesAndAMotionWithoutCouplingDoesNot() {
        val k = IslandMotion.JELLY.coupling
        assertTrue(k > 0f)
        assertEquals("below half open the width follows the height", .3f, IslandMotion.coupledWidth(.3f, k), 1e-5f)
        assertEquals("fully open it is fully wide", 1f, IslandMotion.coupledWidth(1f, k), 1e-5f)
        assertTrue("taller than open means narrower", IslandMotion.coupledWidth(1.3f, k) < 1f)
        assertTrue("shorter than open means wider than the height", IslandMotion.coupledWidth(.8f, k) > .8f)
        assertTrue("the width never leaves the room the island is given", (0..140).all { IslandMotion.coupledWidth(it / 100f, k) in 0f..IslandGeometry.MAX_OVERSHOOT })
        assertTrue(IslandMotion.values().filter { it != IslandMotion.JELLY }.all { it.coupling == 0f })
    }

    @Test fun theLivelyMotionsAreVisiblyDifferentFromEachOther() {
        // A rubber ball overshoots on both axes together; jelly's width is the mirror of its height; a droplet's width is slow and calm.
        val ball = IslandMotion.BOUNCY
        assertTrue(Math.abs(ball.heightDamping - ball.widthDamping) < .1f && Math.abs(ball.heightStiffness - ball.widthStiffness) < 30f)
        val drop = IslandMotion.DROPLET
        assertTrue("droplet's width is slow", drop.widthStiffness < drop.heightStiffness / 3f)
        assertTrue("and does not wobble", overshoot(drop.widthDamping) < .03 && overshoot(drop.heightDamping) in .05..IslandGeometry.MAX_OVERSHOOT - 1.0)
        assertTrue("jelly rings longer than anything but is the only coupled one", overshoot(IslandMotion.JELLY.heightDamping) > overshoot(ball.heightDamping))
        val labels = IslandMotion.values().map { it.label }
        assertEquals(labels.size, labels.toSet().size)
    }
}
