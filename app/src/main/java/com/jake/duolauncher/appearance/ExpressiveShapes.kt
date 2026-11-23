/*
 * The shape recipes in this file (cookies, sunny, soft burst, flower, clover, pill, pentagon) are ported from Material 3's MaterialShapes
 * (androidx.compose.material3, 1.5.0-rc01, commonMain/androidx/compose/material3/MaterialShapes.kt):
 *
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * MaterialShapes itself is only in Material 3's 1.5 pre-releases (which need Compose 1.12 and Kotlin 2.2); the shapes are built here on the
 * stable androidx.graphics.shapes library it is built on, with the same vertices and roundings, so they are Google's outlines exactly.
 */
package com.jake.duolauncher.appearance

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.TransformResult
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Material 3 Expressive's shapes, each normalized to a 0..1 box. Built once, on first use. */
internal object ExpressivePolygons {
    private val round15 = CornerRounding(.15f)
    private val round50 = CornerRounding(.5f)

    private class P(val x: Float, val y: Float, val r: CornerRounding = CornerRounding.Unrounded)

    /** Material's rotateZ(-90) about the origin; [RoundedPolygon.normalized] then moves the result back into the box. */
    private fun RoundedPolygon.rotatedNeg90() = transformed { x, y -> TransformResult(y, -x) }

    private fun repeatPoints(points: List<P>, reps: Int, cx: Float, cy: Float, mirroring: Boolean): List<P> =
        if (mirroring) buildList {
            val angles = points.map { atan2(it.y - cy, it.x - cx) * 180f / PI.toFloat() }
            val distances = points.map { hypot(it.x - cx, it.y - cy) }
            val actualReps = reps * 2
            val section = 360f / actualReps
            repeat(actualReps) { rep ->
                points.indices.forEach { index ->
                    val i = if (rep % 2 == 0) index else points.lastIndex - index
                    if (i > 0 || rep % 2 == 0) {
                        val a = ((section * rep + if (rep % 2 == 0) angles[i] else section - angles[i] + 2 * angles[0]) * PI.toFloat() / 180f)
                        add(P(cos(a) * distances[i] + cx, sin(a) * distances[i] + cy, points[i].r))
                    }
                }
            }
        } else (0 until points.size * reps).map {
            val p = points[it % points.size]
            val a = ((it / points.size) * 360f / reps) * PI.toFloat() / 180f
            val dx = p.x - cx; val dy = p.y - cy
            P(dx * cos(a) - dy * sin(a) + cx, dx * sin(a) + dy * cos(a) + cy, p.r)
        }

    private fun custom(points: List<P>, reps: Int, mirroring: Boolean = false): RoundedPolygon {
        val all = repeatPoints(points, reps, .5f, .5f, mirroring)
        return RoundedPolygon(FloatArray(all.size * 2) { ix -> if (ix % 2 == 0) all[ix / 2].x else all[ix / 2].y },
            perVertexRounding = all.map { it.r }, centerX = .5f, centerY = .5f)
    }

    val cookie4 by lazy { custom(listOf(P(1.237f, 1.236f, CornerRounding(.258f)), P(.5f, .918f, CornerRounding(.233f))), 4).normalized() }
    val cookie7 by lazy { RoundedPolygon.star(7, innerRadius = .75f, rounding = round50).rotatedNeg90().normalized() }
    val cookie9 by lazy { RoundedPolygon.star(9, innerRadius = .8f, rounding = round50).rotatedNeg90().normalized() }
    val cookie12 by lazy { RoundedPolygon.star(12, innerRadius = .8f, rounding = round50).rotatedNeg90().normalized() }
    val sunny by lazy { RoundedPolygon.star(8, innerRadius = .8f, rounding = round15).normalized() }
    val verySunny by lazy { custom(listOf(P(.5f, 1.08f, CornerRounding(.085f)), P(.358f, .843f, CornerRounding(.085f))), 8).normalized() }
    val softBurst by lazy { custom(listOf(P(.193f, .277f, CornerRounding(.053f)), P(.176f, .055f, CornerRounding(.053f))), 10).normalized() }
    val flower by lazy { custom(listOf(P(.370f, .187f), P(.416f, .049f, CornerRounding(.381f)), P(.479f, .001f, CornerRounding(.095f))), 8, mirroring = true).normalized() }
    val clover8 by lazy { custom(listOf(P(.5f, .036f), P(.758f, -.101f, CornerRounding(.209f))), 8).normalized() }
    val pentagon by lazy { custom(listOf(P(.5f, -.009f, CornerRounding(.172f)), P(1.030f, .365f, CornerRounding(.164f)), P(.828f, .970f, CornerRounding(.169f))), 1, mirroring = true).normalized() }
    val pill by lazy { custom(listOf(P(.961f, .039f, CornerRounding(.426f)), P(1.001f, .428f), P(1f, .609f, CornerRounding(1f))), 2, mirroring = true).normalized() }
}

/** A normalized polygon as a Compose [Shape], scaled to whatever box it clips or fills. */
internal class PolygonShape(private val polygon: RoundedPolygon) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        // Fitted to the shorter side and centred, so the shape is never stretched in a box that is not square.
        val side = minOf(size.width, size.height)
        val path = polygon.toPath()
        path.transform(android.graphics.Matrix().apply { setScale(side, side); postTranslate((size.width - side) / 2f, (size.height - side) / 2f) })
        return Outline.Generic(path.asComposePath())
    }
}

/**
 * Material 3 Expressive's loading indicator, as on Pixel: a shape that turns as it morphs from one of Material's shapes into the next. The
 * polygons and morphs are built once and one path is reused for every frame. With Android's animations off it is a still cookie.
 */
@Composable
internal fun ShapeLoader(color: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val animate = remember { android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }
    val shapes = remember { with(ExpressivePolygons) { listOf(softBurst, cookie9, pentagon, pill, sunny, cookie4) } }
    val morphs = remember(shapes) { shapes.indices.map { Morph(shapes[it], shapes[(it + 1) % shapes.size]) } }
    val path = remember { android.graphics.Path() }
    val matrix = remember { android.graphics.Matrix() }
    val composePath = remember(path) { path.asComposePath() }
    val phase = if (animate) {
        val t = rememberInfiniteTransition(label = "shape loader")
        t.animateFloat(0f, shapes.size.toFloat(), infiniteRepeatable(tween(650 * shapes.size, easing = LinearEasing), RepeatMode.Restart), label = "phase").value
    } else 1f
    Canvas(modifier) {
        val step = phase.toInt().coerceIn(0, morphs.lastIndex)
        // Each step eases: a quick change, then a moment held in the new shape, as Material's indicator does.
        val raw = (phase - step).coerceIn(0f, 1f)
        val progress = (raw / .6f).coerceAtMost(1f).let { it * it * (3f - 2f * it) }
        path.rewind()
        morphs[step].toPath(progress, path)
        matrix.setScale(size.width, size.height); path.transform(matrix)
        rotate(phase * 140f) { drawPath(composePath, color) }
    }
}

/** The shapes the launcher's badges use, as Compose shapes. */
internal object ExpressiveShapes {
    val Cookie4 by lazy { PolygonShape(ExpressivePolygons.cookie4) }
    val Cookie7 by lazy { PolygonShape(ExpressivePolygons.cookie7) }
    val Cookie9 by lazy { PolygonShape(ExpressivePolygons.cookie9) }
    val Cookie12 by lazy { PolygonShape(ExpressivePolygons.cookie12) }
    val Sunny by lazy { PolygonShape(ExpressivePolygons.sunny) }
    val Flower by lazy { PolygonShape(ExpressivePolygons.flower) }
    val SoftBurst by lazy { PolygonShape(ExpressivePolygons.softBurst) }
    val Pill by lazy { PolygonShape(ExpressivePolygons.pill) }
    val Pentagon by lazy { PolygonShape(ExpressivePolygons.pentagon) }
}

/**
 * A lobed Material shape with what its glass lens needs (see the glass library's `lobedLens`): the exact polygon clips the surface, and the
 * lens bends what is behind it along a curve fitted to that polygon, a circle whose radius swells [lobes] times. The centre and the two radii
 * are measured from the polygon itself, so the fit follows Google's outline (an odd-lobed cookie does not sit dead centre in its box).
 */
internal class LobedShape(val polygon: RoundedPolygon, val lobes: Int, val phase: Float, val sharp: Float) {
    val shape: Shape = PolygonShape(polygon)
    val centerX: Float = polygon.centerX
    val centerY: Float = polygon.centerY
    /** The tips' radius, as a share of the unit box's side. */
    val outer: Float
    /** The dips' radius as a share of the tips'. */
    val inner: Float

    init {
        // The outline's nearest and farthest points from the centre, sampled along every curve of it.
        var far = 0f; var near = Float.MAX_VALUE
        polygon.cubics.forEach { c ->
            for (i in 0..8) {
                val t = i / 8f; val u = 1f - t
                val x = u * u * u * c.anchor0X + 3 * u * u * t * c.control0X + 3 * u * t * t * c.control1X + t * t * t * c.anchor1X
                val y = u * u * u * c.anchor0Y + 3 * u * u * t * c.control0Y + 3 * u * t * t * c.control1Y + t * t * t * c.anchor1Y
                val d = hypot(x - centerX, y - centerY)
                if (d > far) far = d
                if (d < near) near = d
            }
        }
        outer = far
        inner = if (far > 0f) (near / far).coerceIn(0f, 1f) else 1f
    }
}

/** The lobed shapes that can be glass with a lens. A tip of each cookie points up; Sunny's rays are pointed, the cookies' lobes round. */
internal object LobedShapes {
    private const val UP = (-PI / 2).toFloat()
    val cookie7 by lazy { LobedShape(ExpressivePolygons.cookie7, 7, UP, sharp = .15f) }
    val cookie9 by lazy { LobedShape(ExpressivePolygons.cookie9, 9, UP, sharp = .15f) }
    val cookie12 by lazy { LobedShape(ExpressivePolygons.cookie12, 12, UP, sharp = .15f) }
    val sunny by lazy { LobedShape(ExpressivePolygons.sunny, 8, 0f, sharp = .7f) }
}
