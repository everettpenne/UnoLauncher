package com.jake.duolauncher.appearance

import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Hyper glass: an experimental app icon whose picture is inside the glass and not on it. One shader draws it, inside whichever outline
 * the icon has (the launcher's squircle or any of Material's shapes):
 *
 *  1. the card, one flat colour (black, white, the accent), or none at all ([clear]), when the wallpaper shows through;
 *  2. the glass body: the icon's picture is a texture the shader reads, so it can be bent. The bend comes from the icon's own edge and
 *     nowhere else: the glass is thick at its rim, and within that rim each point looks inwards, straight across the edge, by an amount
 *     that falls away from the refraction strength at the edge to nothing where the rim ends. So the
 *     middle of the picture is untouched and the rim shows the picture from further in, pooled into soft round shapes (a blurred copy
 *     of the picture, drawn hard again). Where the edge turns, at a corner or between two lobes, the looks converge, which is what
 *     gathers the picture into the corners;
 *  3. light: a blue haze where those soft shapes spread past the picture, a narrow blue light inside the rim, and a 1.5 px white line
 *     on the edge, 80 % along the top and fainter all round.
 *
 * The edge is known through a [Field]: for every pixel, how far inside the outline it is and which way the nearest edge faces. It is
 * worked out once for a shape and size (exactly for the squircle, measured from the outline for the others) and given to the shader as
 * a texture. The layer sums live here once as plain Kotlin ([shade]) and the shader ([AGSL]) repeats them line for line: the Kotlin
 * ones draw the icon as a still picture where there are no runtime shaders (Android 12), and let a test hold the two against each other.
 */
internal object HyperGlass {
    /** How far into the icon its very edge looks, at rest and at the height of a press, as a share of the icon's width. */
    const val REST = .38f
    const val PRESSED = .55f
    const val PRESS_MS = 300
    /** The white line on the top edge: its width in pixels and its opacity. */
    const val HIGHLIGHT_PX = 1.5f
    const val HIGHLIGHT_ALPHA = .8f
    /** The blur of the picture's soft copy, in pixels of the icon. */
    const val FROST_BLUR_PX = 20f
    /** The blurred copy is made at a quarter of the size: a 20 px blur has no detail finer than that. */
    const val FROST_SCALE = 4
    /** The blue light inside the rim: how far in it reaches (a share of the half-width), its strength and its colour. */
    const val RIM_REACH = .07f
    const val RIM_ALPHA = .6f
    /** The white line's opacity round the rest of the edge (the top has [HIGHLIGHT_ALPHA]). */
    const val LINE_ROUND = .35f
    val rimBlue = floatArrayOf(.24f, .44f, 1f)
    /** The thick edge of the glass: how far in it reaches, as a share of the shape's depth (the furthest any point is from its edge). */
    const val EDGE_REACH = .42f
    /** How far outside the outline the field still knows its distance, in pixels (for the soft edge), and how it is packed in a texture. */
    const val FIELD_MARGIN = 4f
    /** How much of the icon a vector glyph fills. */
    const val GLYPH_FILL = .94f
    /** The soft light over the top half of the glass. */
    const val GLOSS = .05f
    /** Clear glass (no card): the glass's own faint tint. */
    val clear = Color.White.copy(alpha = .10f)

    /** The bend [ms] into a press: [REST] up to [PRESSED] at the middle and back, each half eased fast-out, slow-in. */
    fun pressStrength(ms: Float): Float {
        val t = (ms / PRESS_MS).coerceIn(0f, 1f)
        return if (t < .5f) REST + (PRESSED - REST) * FastOutSlowInEasing.transform(t * 2f)
        else PRESSED + (REST - PRESSED) * FastOutSlowInEasing.transform((t - .5f) * 2f)
    }

    const val AGSL = """
uniform shader iconTexture;
uniform shader frostTexture;
uniform shader shapeTexture;
uniform shader edgeTexture;
uniform shader wallpaperTexture;
uniform float2 size;
uniform float2 textureSize;
uniform float shapeRange;
uniform float shapeDepth;
uniform float refractionStrength;
uniform float highlightWidth;
uniform float wallpaperOn;
uniform float2 wallpaperOrigin;
uniform float2 wallpaperScale;
layout(color) uniform half4 background;

half4 main(float2 coord) {
    // The shape's field: how far inside the outline this pixel is (d, in pixels as drawn) and which way the nearest edge faces
    // (n, outward). m is the shape's depth, the furthest any point is from the edge.
    float grow = size.x / textureSize.x;
    half4 field = shapeTexture.eval(coord / grow);
    float d = (((field.r + field.g + field.b) / 3.0 + 0.0013) * shapeRange - 4.0) * grow;
    float2 n = edgeTexture.eval(coord / grow).rg * 2.0 - 1.0;
    n = n / max(length(n), 0.001);
    float m = shapeDepth * grow;
    float mask = clamp(d + 0.5, 0.0, 1.0);
    if (mask <= 0.0) { return half4(0.0); }
    float inside = max(d, 0.0);

    // The glass body. The bend comes from the edge alone: inside the thick rim each point looks inwards, straight across the edge,
    // by an amount that falls away as a square (all of the strength at the edge, none where the rim ends). The middle is untouched.
    float t = 1.0 - clamp(inside / (m * 0.42), 0.0, 1.0);
    float edge = t * t;
    float2 uv0 = coord / size;
    float2 uv = uv0 - n * (refractionStrength * edge);

    // What is behind the picture: the card's flat colour, or, for clear glass over a wallpaper, the wallpaper itself seen through the
    // same bend (softened towards the edge), with the card's colour as a tint over it.
    half4 behind = half4(background.rgb * background.a, background.a);
    if (wallpaperOn > 0.5) {
        float2 at = wallpaperOrigin + uv * size * wallpaperScale;
        float blur = t * 9.0;
        half3 wall = wallpaperTexture.eval(at).rgb;
        half3 softWall = (wallpaperTexture.eval(at + float2(blur, 0.0)).rgb + wallpaperTexture.eval(at - float2(blur, 0.0)).rgb
            + wallpaperTexture.eval(at + float2(0.0, blur)).rgb + wallpaperTexture.eval(at - float2(0.0, blur)).rgb) * 0.25;
        wall = mix(wall, softWall, min(t * 1.5, 1.0));
        behind = half4(mix(wall, background.rgb, background.a), 1.0);
    }

    // The picture: sharp in the middle; towards the edge it gives way to its blurred copy drawn hard again, which is what pools it
    // into soft round shapes. Everything from here is premultiplied colour with its own opacity, so clear glass stays see-through.
    half4 sharp = iconTexture.eval(uv * textureSize);
    half4 soft = frostTexture.eval(uv * textureSize);
    half4 pooled = soft * (smoothstep(0.34, 0.66, soft.a) / max(soft.a, 0.001));
    half4 picture = mix(sharp, pooled, min(t * 1.4, 1.0));
    half3 colour = picture.rgb + behind.rgb * (1.0 - picture.a);
    float alpha = picture.a + behind.a * (1.0 - picture.a);

    // The blue haze: where the blurred picture reaches past the picture itself, strongest in the thick edge.
    float haze = clamp(soft.a - picture.a, 0.0, 1.0) * (0.35 + 0.65 * min(t * 2.0, 1.0)) * 1.5;
    haze = min(haze, 0.9);
    colour = mix(colour, half3(0.24, 0.44, 1.0), haze); alpha = mix(alpha, 1.0, haze);
    // A soft light over the top half.
    float gloss = 0.05 * (1.0 - smoothstep(0.0, 0.55, uv0.y));
    colour = mix(colour, half3(1.0), gloss); alpha = mix(alpha, 1.0, gloss);

    // The rim: a narrow blue light just inside the edge, and on the edge itself a white line, full on the top and fainter all round.
    float rim = 1.0 - clamp(inside / (m * 0.07), 0.0, 1.0);
    rim = 0.6 * rim * rim;
    colour = mix(colour, half3(0.24, 0.44, 1.0), rim); alpha = mix(alpha, 1.0, rim);
    float line = (1.0 - smoothstep(highlightWidth - 0.5, highlightWidth + 0.5, inside)) * (0.35 + 0.45 * smoothstep(0.35, 0.8, -n.y));
    colour = mix(colour, half3(1.0), line); alpha = mix(alpha, 1.0, line);
    alpha = clamp(alpha, 0.0, 1.0);
    return half4(clamp(colour, half3(0.0), half3(alpha)) * mask, alpha * mask);
}
"""

    /** A picture the sums read: premultiplied ARGB pixels, read between pixels and transparent past the edge (as the shader's DECAL). */
    class Texture(val width: Int, val height: Int, val pixels: IntArray) {
        /** Reads at ([x], [y]) in pixels into [out] as premultiplied r, g, b, a in 0..1. */
        fun read(x: Float, y: Float, out: FloatArray) {
            val fx = x - .5f; val fy = y - .5f
            val x0 = kotlin.math.floor(fx).toInt(); val y0 = kotlin.math.floor(fy).toInt()
            val tx = fx - x0; val ty = fy - y0
            out[0] = 0f; out[1] = 0f; out[2] = 0f; out[3] = 0f
            for (j in 0..1) for (i in 0..1) {
                val px = x0 + i; val py = y0 + j
                if (px < 0 || py < 0 || px >= width || py >= height) continue
                val w = (if (i == 0) 1f - tx else tx) * (if (j == 0) 1f - ty else ty)
                val c = pixels[py * width + px]
                out[0] += w * ((c shr 16) and 0xFF) / 255f; out[1] += w * ((c shr 8) and 0xFF) / 255f
                out[2] += w * (c and 0xFF) / 255f; out[3] += w * ((c ushr 24) and 0xFF) / 255f
            }
        }
    }

    private fun smoothstep(a: Float, b: Float, x: Float): Float { val t = ((x - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3f - 2f * t) }

    /**
     * A shape, as the glass needs to know it: for each pixel of a [width] by [height] picture, how far inside the outline it is
     * ([d], in pixels; negative outside) and which way the nearest edge faces ([nx], [ny]: outward, of length one). [depth] is the
     * furthest any pixel is from the edge.
     */
    class Field(val width: Int, val height: Int, val d: FloatArray, val nx: FloatArray, val ny: FloatArray) {
        val depth: Float = d.max().coerceAtLeast(1f)

        /** Reads at ([x], [y]) in pixels, between pixels, into [out] as d, nx, ny. */
        fun read(x: Float, y: Float, out: FloatArray) {
            val fx = (x - .5f).coerceIn(0f, width - 1f); val fy = (y - .5f).coerceIn(0f, height - 1f)
            val x0 = fx.toInt().coerceAtMost(width - 2).coerceAtLeast(0); val y0 = fy.toInt().coerceAtMost(height - 2).coerceAtLeast(0)
            val tx = fx - x0; val ty = fy - y0
            val x1 = minOf(x0 + 1, width - 1); val y1 = minOf(y0 + 1, height - 1)
            fun mix(a: FloatArray) = (a[y0 * width + x0] * (1f - tx) + a[y0 * width + x1] * tx) * (1f - ty) + (a[y1 * width + x0] * (1f - tx) + a[y1 * width + x1] * tx) * ty
            out[0] = mix(d); out[1] = mix(nx); out[2] = mix(ny)
        }

        /** How far in the packed distance goes: the thick rim and a little more. Past it nothing bends, so nothing is lost. */
        val packedReach: Float get() = depth * EDGE_REACH + 2f

        /**
         * The distance as a texture the shader reads, from [FIELD_MARGIN] outside to [packedReach] inside as 0 to 1. A byte has only 256
         * steps, which showed as fine streaks in the bend, so red, green and blue each hold the same number rounded a third of a step
         * apart: their mean has three times the steps, and each still blends smoothly between pixels. Unpremultiplied ARGB, opaque.
         */
        fun packedDistance(): IntArray = IntArray(width * height) {
            val v = ((d[it] + FIELD_MARGIN) / (packedReach + FIELD_MARGIN)).coerceIn(0f, 1f) * 255f
            val r = v.toInt().coerceIn(0, 255); val g = (v + 1f / 3f).toInt().coerceIn(0, 255); val b = (v + 2f / 3f).toInt().coerceIn(0, 255)
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        /** The edge's direction as a texture: red and green are its two parts, -1..1 as 0..1. */
        fun packedDirection(): IntArray = IntArray(width * height) {
            val r = ((nx[it] * .5f + .5f) * 255f + .5f).toInt().coerceIn(0, 255); val g = ((ny[it] * .5f + .5f) * 255f + .5f).toInt().coerceIn(0, 255)
            (0xFF shl 24) or (r shl 16) or (g shl 8)
        }
    }

    /** The launcher's squircle ([Squircle]) as a field, worked out exactly: [w] by [h] pixels with corner [cornerRadius]. */
    fun squircleField(w: Int, h: Int, cornerRadius: Float): Field {
        val ds = FloatArray(w * h); val nxs = FloatArray(w * h); val nys = FloatArray(w * h)
        val hx = w / 2f; val hy = h / 2f
        val m = min(hx, hy)
        val c = (cornerRadius * Squircle.REACH).coerceIn(.001f, m)
        for (y in 0 until h) for (x in 0 until w) {
            val relX = x + .5f - hx; val relY = y + .5f - hy
            val px = abs(relX); val py = abs(relY)
            val qx = max(px - (hx - c), 0f) / c; val qy = max(py - (hy - c), 0f) / c
            val q4x = qx * qx * qx * qx; val q4y = qy * qy * qy * qy
            val f = (q4x * qx + q4y * qy).pow(.2f)
            var d = min(hx - px, hy - py)
            var nx = if (hx - px < hy - py) 1f else 0f; var ny = 1f - nx
            if (f > .0001f) {
                val f4 = f * f * f * f
                val gx = q4x / f4; val gy = q4y / f4
                val gl = max(sqrt(gx * gx + gy * gy), .001f)
                val dc = (1f - f) * c / gl
                if (dc < d) { d = dc; nx = gx / gl; ny = gy / gl }
            }
            val i = y * w + x
            ds[i] = d; nxs[i] = if (relX < 0f) -nx else nx; nys[i] = if (relY < 0f) -ny else ny
        }
        return Field(w, h, ds, nxs, nys)
    }

    /**
     * Any outline as a field, measured from a picture of it: [inside] says which pixels of a ([w] * [fine]) by ([h] * [fine]) drawing
     * of the shape are in it. The distance of each fine pixel to the nearest pixel of the other kind is found exactly (two passes of a
     * one-dimensional distance transform), averaged down to [w] by [h], and the edge's direction is the way that distance falls.
     */
    fun maskField(inside: BooleanArray, w: Int, h: Int, fine: Int): Field {
        val fw = w * fine; val fh = h * fine
        val far = 1e9f
        // The squared distance from every pixel to the nearest pixel where [zero] holds.
        fun spread(zero: (Int) -> Boolean): FloatArray {
            val grid = FloatArray(fw * fh) { if (zero(it)) 0f else far }
            val longest = max(fw, fh)
            val line = FloatArray(longest); val out = FloatArray(longest); val v = IntArray(longest); val z = FloatArray(longest + 1)
            fun pass(length: Int) {
                var k = 0; v[0] = 0; z[0] = -far; z[1] = far
                for (q in 1 until length) {
                    var sx: Float
                    while (true) {
                        val p = v[k]
                        sx = ((line[q] + q * q) - (line[p] + p * p)) / (2f * q - 2f * p)
                        if (sx <= z[k] && k > 0) k-- else break
                    }
                    k++; v[k] = q; z[k] = sx; z[k + 1] = far
                }
                k = 0
                for (q in 0 until length) { while (z[k + 1] < q) k++; val p = v[k]; out[q] = (q - p) * (q - p) + line[p] }
            }
            for (x in 0 until fw) { for (y in 0 until fh) line[y] = grid[y * fw + x]; pass(fh); for (y in 0 until fh) grid[y * fw + x] = out[y] }
            for (y in 0 until fh) { for (x in 0 until fw) line[x] = grid[y * fw + x]; pass(fw); for (x in 0 until fw) grid[y * fw + x] = out[x] }
            return grid
        }
        val toOutside = spread { !inside[it] }; val toInside = spread { inside[it] }
        val ds = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var sum = 0f
            for (j in 0 until fine) for (i in 0 until fine) {
                val k = (y * fine + j) * fw + x * fine + i
                // Half a pixel off each, so the edge itself (between a pixel that is in and one that is out) is at nought.
                sum += if (inside[k]) sqrt(toOutside[k]) - .5f else -(sqrt(toInside[k]) - .5f)
            }
            ds[y * w + x] = sum / (fine * fine) / fine
        }
        // The edge's direction: against the way the distance grows, taken over a few pixels so it turns smoothly.
        val nxs = FloatArray(w * h); val nys = FloatArray(w * h)
        fun at(x: Int, y: Int) = ds[y.coerceIn(0, h - 1) * w + x.coerceIn(0, w - 1)]
        for (y in 0 until h) for (x in 0 until w) {
            var gx = 0f; var gy = 0f
            for (k in -1..1) { gx += at(x + 2, y + k) - at(x - 2, y + k); gy += at(x + k, y + 2) - at(x + k, y - 2) }
            val gl = sqrt(gx * gx + gy * gy)
            if (gl > 1e-4f) { nxs[y * w + x] = -gx / gl; nys[y * w + x] = -gy / gl } else {
                // Flat (the very middle): away from the centre, so there is always a direction.
                val rx = x + .5f - w / 2f; val ry = y + .5f - h / 2f; val rl = max(sqrt(rx * rx + ry * ry), 1e-4f)
                nxs[y * w + x] = rx / rl; nys[y * w + x] = ry / rl
            }
        }
        return Field(w, h, ds, nxs, nys)
    }

    /**
     * One pixel of the icon, as the shader makes it: premultiplied r, g, b, a into [out]. [field] is the icon's shape, at the
     * picture's own size. [background] is the card's r, g, b and opacity (not premultiplied); with no opacity the glass is clear and
     * the result is see-through where it has nothing in it. [frost] is [picture] blurred, at 1 / [FROST_SCALE] of its size. [scratch] is
     * three arrays of four the caller keeps, so a whole icon allocates nothing.
     */
    fun shade(x: Float, y: Float, w: Float, h: Float, field: Field, strength: Float, background: FloatArray, picture: Texture,
        frost: Texture, out: FloatArray, scratch: Array<FloatArray>, highlightPx: Float = HIGHLIGHT_PX) {
        val grow = w / field.width
        val shape = scratch[2]
        field.read(x / grow, y / grow, shape)
        val d = shape[0] * grow
        var nx = shape[1]; var ny = shape[2]
        val nl = max(sqrt(nx * nx + ny * ny), .001f); nx /= nl; ny /= nl
        val m = field.depth * grow
        val mask = (d + .5f).coerceIn(0f, 1f)
        if (mask <= 0f) { out[0] = 0f; out[1] = 0f; out[2] = 0f; out[3] = 0f; return }
        val inside = max(d, 0f)

        val t = 1f - (inside / (m * EDGE_REACH)).coerceIn(0f, 1f)
        val edge = t * t
        val v0 = y / h
        val u = x / w - nx * strength * edge; val v = v0 - ny * strength * edge
        // The card behind the picture, premultiplied: a flat colour with its own opacity (none at all for clear glass).
        val ca = background[3]; val c0 = background[0] * ca; val c1 = background[1] * ca; val c2 = background[2] * ca
        val sharp = scratch[0]; val soft = scratch[1]
        picture.read(u * w, v * h, sharp)
        frost.read(u * w / FROST_SCALE, v * h / FROST_SCALE, soft)
        val harden = smoothstep(.34f, .66f, soft[3]) / max(soft[3], .001f)
        val pool = min(t * 1.4f, 1f)
        val pr = sharp[0] + (soft[0] * harden - sharp[0]) * pool; val pg = sharp[1] + (soft[1] * harden - sharp[1]) * pool
        val pb = sharp[2] + (soft[2] * harden - sharp[2]) * pool; val pa = sharp[3] + (soft[3] * harden - sharp[3]) * pool
        var r = pr + c0 * (1f - pa); var g = pg + c1 * (1f - pa); var b = pb + c2 * (1f - pa); var a = pa + ca * (1f - pa)

        val haze = min((soft[3] - pa).coerceIn(0f, 1f) * (.35f + .65f * min(t * 2f, 1f)) * 1.5f, .9f)
        r += (rimBlue[0] - r) * haze; g += (rimBlue[1] - g) * haze; b += (rimBlue[2] - b) * haze; a += (1f - a) * haze
        val gloss = GLOSS * (1f - smoothstep(0f, .55f, v0))
        r += (1f - r) * gloss; g += (1f - g) * gloss; b += (1f - b) * gloss; a += (1f - a) * gloss

        var rim = 1f - (inside / (m * RIM_REACH)).coerceIn(0f, 1f)
        rim = RIM_ALPHA * rim * rim
        r += (rimBlue[0] - r) * rim; g += (rimBlue[1] - g) * rim; b += (rimBlue[2] - b) * rim; a += (1f - a) * rim
        val line = (1f - smoothstep(highlightPx - .5f, highlightPx + .5f, inside)) * (LINE_ROUND + (HIGHLIGHT_ALPHA - LINE_ROUND) * smoothstep(.35f, .8f, -ny))
        r += (1f - r) * line; g += (1f - g) * line; b += (1f - b) * line; a += (1f - a) * line
        a = a.coerceIn(0f, 1f)
        out[0] = r.coerceIn(0f, a) * mask; out[1] = g.coerceIn(0f, a) * mask; out[2] = b.coerceIn(0f, a) * mask; out[3] = a * mask
    }

    /** The whole icon as pixels (premultiplied ARGB), for where there is no shader: the same picture the shader makes at [strength]. */
    fun render(picture: Texture, frost: Texture, field: Field, strength: Float, background: Int): IntArray {
        val w = picture.width; val h = picture.height
        val bg = floatArrayOf(((background shr 16) and 0xFF) / 255f, ((background shr 8) and 0xFF) / 255f, (background and 0xFF) / 255f, (background ushr 24) / 255f)
        val out = FloatArray(4); val scratch = Array(3) { FloatArray(4) }
        val pixels = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            shade(x + .5f, y + .5f, w.toFloat(), h.toFloat(), field, strength, bg, picture, frost, out, scratch)
            pixels[y * w + x] = ((out[3] * 255f + .5f).toInt() shl 24) or ((out[0] * 255f + .5f).toInt() shl 16) or
                ((out[1] * 255f + .5f).toInt() shl 8) or (out[2] * 255f + .5f).toInt()
        }
        return pixels
    }

    /** [picture] at 1 / [FROST_SCALE] of its size and blurred by [FROST_BLUR_PX] (a Gaussian, in the picture's own pixels). */
    fun frost(picture: Texture): Texture {
        val w = max(1, picture.width / FROST_SCALE); val h = max(1, picture.height / FROST_SCALE)
        // Each small pixel is the mean of the block it covers.
        val small = Array(4) { FloatArray(w * h) }
        for (y in 0 until h) for (x in 0 until w) {
            var n = 0
            for (j in 0 until FROST_SCALE) for (i in 0 until FROST_SCALE) {
                val sx = x * FROST_SCALE + i; val sy = y * FROST_SCALE + j
                if (sx >= picture.width || sy >= picture.height) continue
                val c = picture.pixels[sy * picture.width + sx]
                small[0][y * w + x] += ((c shr 16) and 0xFF).toFloat(); small[1][y * w + x] += ((c shr 8) and 0xFF).toFloat()
                small[2][y * w + x] += (c and 0xFF).toFloat(); small[3][y * w + x] += ((c ushr 24) and 0xFF).toFloat()
                n++
            }
            if (n > 0) for (k in 0..3) small[k][y * w + x] /= n
        }
        // A radius of 20 px is a Gaussian of about half that; at this size, a quarter of it again.
        val sigma = FROST_BLUR_PX / 2f / FROST_SCALE
        val reach = max(1, (sigma * 3f).toInt())
        val weights = FloatArray(reach * 2 + 1) { exp(-((it - reach) * (it - reach)) / (2f * sigma * sigma)) }
        val total = weights.sum()
        val pass = FloatArray(w * h)
        for (k in 0..3) {
            // Across, then down. Past the edge there is nothing (transparent), as for the picture itself.
            for (y in 0 until h) for (x in 0 until w) {
                var s = 0f
                for (i in -reach..reach) { val sx = x + i; if (sx in 0 until w) s += small[k][y * w + sx] * weights[i + reach] }
                pass[y * w + x] = s / total
            }
            for (y in 0 until h) for (x in 0 until w) {
                var s = 0f
                for (i in -reach..reach) { val sy = y + i; if (sy in 0 until h) s += pass[sy * w + x] * weights[i + reach] }
                small[k][y * w + x] = s / total
            }
        }
        val pixels = IntArray(w * h) {
            ((small[3][it] + .5f).toInt().coerceIn(0, 255) shl 24) or ((small[0][it] + .5f).toInt().coerceIn(0, 255) shl 16) or
                ((small[1][it] + .5f).toInt().coerceIn(0, 255) shl 8) or (small[2][it] + .5f).toInt().coerceIn(0, 255)
        }
        return Texture(w, h, pixels)
    }

    /** The glyph's colour on [background]: white on a dark one, near-black on a light one. */
    fun glyphOn(background: Color): Color = if (background.alpha > .5f && background.luminance() > .45f) Color(0xFF111114) else Color.White
}

/** The two pictures an icon's shader reads, as bitmaps: the icon itself and its frosted copy. Made once per icon and size. */
internal class HyperGlassTextures(val picture: Bitmap) {
    val texture: HyperGlass.Texture
    val frost: HyperGlass.Texture
    val frostBitmap: Bitmap

    init {
        val w = picture.width; val h = picture.height
        // A bitmap's pixels come back unpremultiplied; the sums (and the shader's textures) are premultiplied.
        val pixels = IntArray(w * h).also { picture.getPixels(it, 0, w, 0, 0, w, h) }
        for (i in pixels.indices) {
            val c = pixels[i]; val a = c ushr 24
            if (a == 255) continue
            pixels[i] = (a shl 24) or ((((c shr 16) and 0xFF) * a / 255) shl 16) or ((((c shr 8) and 0xFF) * a / 255) shl 8) or ((c and 0xFF) * a / 255)
        }
        texture = HyperGlass.Texture(w, h, pixels)
        frost = HyperGlass.frost(texture)
        frostBitmap = Bitmap.createBitmap(frost.width, frost.height, Bitmap.Config.ARGB_8888).also { bitmap ->
            // Back to unpremultiplied for the bitmap, which premultiplies what it is given.
            val plain = IntArray(frost.pixels.size) {
                val c = frost.pixels[it]; val a = c ushr 24
                if (a == 0) 0 else (a shl 24) or (min(255, ((c shr 16) and 0xFF) * 255 / a) shl 16) or
                    (min(255, ((c shr 8) and 0xFF) * 255 / a) shl 8) or min(255, (c and 0xFF) * 255 / a)
            }
            bitmap.setPixels(plain, 0, frost.width, 0, 0, frost.width, frost.height)
        }
    }

    companion object {
        /** [vector] drawn in [tint] (or, with none, in its own colours) in the middle of a [px]-pixel square, filling [HyperGlass.GLYPH_FILL] of it. Its paths are drawn
         *  straight from the vector (fills, strokes, and a group's own move, turn and scale), with no painter, so it can be made anywhere. */
        fun rasterise(vector: ImageVector, px: Int, tint: Int?, fit: Float = 1f): Bitmap {
            val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val side = px * HyperGlass.GLYPH_FILL * fit
            canvas.translate((px - side) / 2f, (px - side) / 2f)
            canvas.scale(side / vector.viewportWidth, side / vector.viewportHeight)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            // In [tint], or with none, each path in the colour the vector gives it.
            fun colourOf(brush: androidx.compose.ui.graphics.Brush?) = tint ?: ((brush as? androidx.compose.ui.graphics.SolidColor)?.value?.toArgb() ?: android.graphics.Color.WHITE)
            fun draw(group: VectorGroup) {
                canvas.save()
                canvas.translate(group.translationX + group.pivotX, group.translationY + group.pivotY)
                canvas.rotate(group.rotation)
                canvas.scale(group.scaleX, group.scaleY)
                canvas.translate(-group.pivotX, -group.pivotY)
                for (node in group) when (node) {
                    is VectorGroup -> draw(node)
                    is VectorPath -> {
                        val path = PathParser().addPathNodes(node.pathData).toPath().asAndroidPath()
                        path.fillType = if (node.pathFillType == androidx.compose.ui.graphics.PathFillType.EvenOdd) android.graphics.Path.FillType.EVEN_ODD
                            else android.graphics.Path.FillType.WINDING
                        if (node.fill != null) {
                            paint.style = android.graphics.Paint.Style.FILL
                            paint.color = colourOf(node.fill)
                            paint.alpha = (node.fillAlpha * (colourOf(node.fill) ushr 24)).toInt().coerceIn(0, 255)
                            canvas.drawPath(path, paint)
                        }
                        if (node.stroke != null && node.strokeLineWidth > 0f) {
                            paint.style = android.graphics.Paint.Style.STROKE
                            paint.strokeWidth = node.strokeLineWidth
                            paint.strokeCap = android.graphics.Paint.Cap.ROUND; paint.strokeJoin = android.graphics.Paint.Join.ROUND
                            paint.color = colourOf(node.stroke)
                            paint.alpha = (node.strokeAlpha * (colourOf(node.stroke) ushr 24)).toInt().coerceIn(0, 255)
                            canvas.drawPath(path, paint)
                        }
                    }
                }
                canvas.restore()
            }
            draw(vector.root)
            return bitmap
        }

        /**
         * An app's icon with its plate taken away, as the texture: what is left is the picture itself, for the glass to bend. An
         * adaptive icon is made of a background and a foreground, so its foreground alone is drawn (larger than the launcher would
         * show it: the layer is half as big again as the icon, with a wide empty margin). Any other icon is one flat picture, so
         * its plate is found as the colour that runs round its edge and is cleared from the edge inwards ([clearPlate]).
         */
        fun unplated(icon: android.graphics.drawable.Drawable, px: Int, mono: Int? = null, fit: Float = 1f): Bitmap {
            val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val adaptive = icon as? android.graphics.drawable.AdaptiveIconDrawable
            // In one colour, an icon that brings its own one-colour drawing (Android 13's themed icons) is that drawing.
            val own = if (mono != null && adaptive != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) adaptive.monochrome else null
            if (own != null) {
                val side = (px * 1.5f * UNPLATED_ZOOM * fit).toInt(); val inset = (px - side) / 2
                own.mutate().apply { setBounds(inset, inset, inset + side, inset + side); setTint(mono!!) }.draw(canvas)
                return bitmap
            }
            val foreground = adaptive?.foreground
            if (foreground != null) {
                // The whole layer at 1.5 times the icon is the launcher's own framing (the middle two thirds showing); a little more,
                // so the picture runs out into the glass's thick edge, which is where it is bent and pooled.
                val side = (px * 1.5f * UNPLATED_ZOOM * fit).toInt()
                val inset = (px - side) / 2
                foreground.mutate().setBounds(inset, inset, inset + side, inset + side)
                foreground.draw(canvas)
                if (mono != null) return oneColour(bitmap, mono)
                // In the app's own colours: a white or grey logo takes back the colour its background layer gave it.
                val back = adaptive.background ?: return bitmap
                val behind = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                back.mutate().setBounds(inset, inset, inset + side, inset + side)
                back.draw(android.graphics.Canvas(behind))
                val pixels = IntArray(px * px).also { bitmap.getPixels(it, 0, px, 0, 0, px, px) }
                val under = IntArray(px * px).also { behind.getPixels(it, 0, px, 0, 0, px, px) }
                behind.recycle()
                if (restoreColour(pixels, under)) bitmap.setPixels(pixels, 0, px, 0, 0, px, px)
                return bitmap
            }
            val whole = (px * fit).toInt(); val edge = (px - whole) / 2
            icon.mutate().setBounds(edge, edge, edge + whole, edge + whole)
            icon.draw(canvas)
            val pixels = IntArray(px * px).also { bitmap.getPixels(it, 0, px, 0, 0, px, px) }
            val plate = IntArray(1)
            if (clearPlate(pixels, px, px, plate)) {
                // In the app's own colours, a white or grey picture takes back its plate's colour.
                if (mono == null) restoreColour(pixels, IntArray(pixels.size) { plate[0] })
                bitmap.setPixels(pixels, 0, px, 0, 0, px, px)
            }
            return if (mono == null) bitmap else oneColour(bitmap, mono)
        }

        /** [picture] in the one colour [tint], in place: its shape kept, and its light and dark kept as more and less of the colour
         *  (the lightest part solid, the darkest a quarter there), so a picture of several colours is still a picture. */
        fun oneColour(picture: Bitmap, tint: Int): Bitmap {
            val w = picture.width; val h = picture.height
            val pixels = IntArray(w * h).also { picture.getPixels(it, 0, w, 0, 0, w, h) }
            fun light(c: Int) = (((c shr 16) and 0xFF) * 54 + ((c shr 8) and 0xFF) * 183 + (c and 0xFF) * 19) / 256
            var lo = 255; var hi = 0
            for (c in pixels) if ((c ushr 24) > 128) { val l = light(c); if (l < lo) lo = l; if (l > hi) hi = l }
            val span = hi - lo
            for (i in pixels.indices) {
                val c = pixels[i]; val a = c ushr 24
                if (a == 0) continue
                // A picture that is all one lightness is all solid.
                val share = if (span < 40) 1f else .25f + .75f * (light(c) - lo).coerceIn(0, span) / span
                pixels[i] = ((a * share).toInt() shl 24) or (tint and 0xFFFFFF)
            }
            picture.setPixels(pixels, 0, w, 0, 0, w, h)
            return picture
        }

        /** A pixel's saturation (0 for grey, 1 for a pure colour) and value (its brightest channel), from 0 to 1. Pure, for the tests. */
        private fun saturation(c: Int): Float {
            val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
            val hi = maxOf(r, g, b); val lo = minOf(r, g, b)
            return if (hi == 0) 0f else (hi - lo).toFloat() / hi
        }

        /** [c] made vivid enough to read on black or clear glass: its hue kept, saturation at least [RESTORE_SATURATION], value at least
         *  [RESTORE_VALUE]. In plain sums, not Android's colour class, so it runs in a unit test. */
        fun vivid(c: Int): Int {
            val r = ((c shr 16) and 255) / 255f; val g = ((c shr 8) and 255) / 255f; val b = (c and 255) / 255f
            val hi = maxOf(r, g, b); val lo = minOf(r, g, b); val d = hi - lo
            val hue = when { d == 0f -> 0f; hi == r -> ((g - b) / d).mod(6f); hi == g -> (b - r) / d + 2f; else -> (r - g) / d + 4f } * 60f
            val s = (if (hi == 0f) 0f else d / hi).coerceAtLeast(RESTORE_SATURATION); val v = hi.coerceAtLeast(RESTORE_VALUE)
            val k = { n: Float -> val x = (n + hue / 60f).mod(6f); v - v * s * maxOf(0f, minOf(x, 4f - x, 1f)) }
            return (0xFF shl 24) or ((k(5f) * 255f + .5f).toInt() shl 16) or ((k(3f) * 255f + .5f).toInt() shl 8) or (k(1f) * 255f + .5f).toInt()
        }

        const val RESTORE_SATURATION = .55f
        const val RESTORE_VALUE = .9f

        /**
         * Gives a logo back the colour its plate gave it, in place, and says whether it did. Many icons draw a white or grey logo on a
         * coloured plate (Settings' gear on blue, a white eye on a blue-to-purple gradient), so with the plate taken off the logo is
         * white. Here each grey part of the logo ([pixels], unpremultiplied ARGB) takes the colour of the plate under it ([under], the
         * same size), made vivid, and shaded by how light the logo is there; parts of the logo that have colour of their own keep it,
         * and so does a logo whose plate is itself grey (black on white).
         */
        fun restoreColour(pixels: IntArray, under: IntArray): Boolean {
            // The plate's colour overall: unless it has some, there is nothing to give back.
            var sat = 0f; var n = 0
            for (c in under) if ((c ushr 24) > 200) { sat += saturation(c); n++ }
            if (n == 0 || sat / n < RESTORE_MIN_PLATE) return false
            var fallback = 0; var best = -1f
            for (c in under) if ((c ushr 24) > 200) { val s = saturation(c); if (s > best) { best = s; fallback = c } }
            var changed = false
            for (i in pixels.indices) {
                val c = pixels[i]; val a = c ushr 24
                if (a == 0) continue
                // Grey takes the colour fully; a part with colour of its own keeps it.
                val keep = ((saturation(c) - .12f) / .2f).coerceIn(0f, 1f)
                if (keep >= 1f) continue
                val u = under[i].takeIf { (it ushr 24) > 200 && saturation(it) >= RESTORE_MIN_PLATE } ?: fallback
                val tint = vivid(u)
                val light = (((c shr 16) and 255) * 54 + ((c shr 8) and 255) * 183 + (c and 255) * 19) / 256 / 255f
                val shade = .45f + .55f * light
                fun ch(shift: Int): Int {
                    val own = (c shr shift) and 255
                    val given = ((tint shr shift) and 255) * shade
                    return (own * keep + given * (1f - keep) + .5f).toInt().coerceIn(0, 255)
                }
                pixels[i] = (a shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
                changed = true
            }
            return changed
        }

        /** How colourful a plate must be, on average, for its colour to be given back to a grey logo. */
        const val RESTORE_MIN_PLATE = .2f

        const val UNPLATED_ZOOM = 1.04f
        private const val PLATE_TOLERANCE = 34

        /**
         * Clears a flat plate from [pixels] (unpremultiplied ARGB, [w] by [h]), in place, and says whether it found one. The plate's
         * colour is the commonest one a few pixels inside the picture's outline; if most of that ring is that colour, everything of
         * that colour that can be reached from the outline is made transparent, so the same colour inside the picture stays.
         */
        fun clearPlate(pixels: IntArray, w: Int, h: Int, found: IntArray? = null): Boolean {
            fun close(a: Int, b: Int) = abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) + abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) +
                abs((a and 0xFF) - (b and 0xFF)) <= PLATE_TOLERANCE
            // The ring: opaque pixels with a transparent (or outside) pixel within three steps of them, along rows and columns.
            val ring = ArrayList<Int>()
            fun opaque(x: Int, y: Int) = x in 0 until w && y in 0 until h && (pixels[y * w + x] ushr 24) > 200
            for (y in 0 until h) for (x in 0 until w) {
                if (!opaque(x, y)) continue
                if (!opaque(x - 3, y) || !opaque(x + 3, y) || !opaque(x, y - 3) || !opaque(x, y + 3)) ring += y * w + x
            }
            if (ring.size < 16) return false
            // The commonest colour on the ring, in coarse steps.
            val counts = HashMap<Int, Int>()
            for (i in ring) { val c = pixels[i]; val key = ((c shr 20) and 0xF shl 8) or ((c shr 12) and 0xF shl 4) or ((c shr 4) and 0xF); counts[key] = (counts[key] ?: 0) + 1 }
            val top = counts.maxByOrNull { it.value }!!.key
            val plate = ring.map { pixels[it] }.first { c -> (((c shr 20) and 0xF shl 8) or ((c shr 12) and 0xF shl 4) or ((c shr 4) and 0xF)) == top }
            if (ring.count { close(pixels[it], plate) } < ring.size * 6 / 10) return false
            found?.set(0, plate)
            // From the ring inwards, through everything of the plate's colour.
            val queue = ArrayDeque<Int>()
            val seen = BooleanArray(w * h)
            for (i in ring) if (close(pixels[i], plate)) { seen[i] = true; queue += i }
            var cleared = 0
            while (queue.isNotEmpty()) {
                val i = queue.removeFirst(); pixels[i] = 0; cleared++
                val x = i % w; val y = i / w
                for ((dx, dy) in NEIGHBOURS) {
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val j = ny * w + nx
                    if (seen[j] || (pixels[j] ushr 24) < 40 || !close(pixels[j], plate)) continue
                    seen[j] = true; queue += j
                }
            }
            return cleared > 0
        }
        private val NEIGHBOURS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

        /** An app's own icon as the texture: scaled to the [px]-pixel square, and a copy the sums can read (never a hardware bitmap). */
        fun fit(icon: Bitmap, px: Int): Bitmap {
            val readable = if (icon.config == Bitmap.Config.HARDWARE || icon.config != Bitmap.Config.ARGB_8888) icon.copy(Bitmap.Config.ARGB_8888, false) else icon
            return if (readable.width == px && readable.height == px) readable else Bitmap.createScaledBitmap(readable, px, px, true)
        }
    }
}

/** An icon shape as the glass needs it: its field, and the field as the two bitmaps the shader reads. */
internal class HyperGlassShape(val field: HyperGlass.Field) {
    val distance: Bitmap = Bitmap.createBitmap(field.width, field.height, Bitmap.Config.ARGB_8888).also { it.setPixels(field.packedDistance(), 0, field.width, 0, 0, field.width, field.height) }
    val direction: Bitmap = Bitmap.createBitmap(field.width, field.height, Bitmap.Config.ARGB_8888).also { it.setPixels(field.packedDirection(), 0, field.width, 0, 0, field.width, field.height) }
}

/**
 * The launcher's icon shapes as hyper glass shapes, made once for a shape and size and kept: the squircle exactly, each of Material's
 * shapes ([IconOutlines]) measured from a drawing of its outline three times the size.
 */
internal object HyperGlassShapes {
    private const val FINE = 3
    private val cache = android.util.LruCache<String, HyperGlassShape>(24)

    /** How much smaller a picture is drawn in one of Material's shapes, which have less room in them than the squircle. */
    fun fit(shape: com.jake.duolauncher.AppIconShape?): Float = when {
        IconOutlines.of(shape, 8f) == null -> 1f
        // A circle loses only the squircle's corners; the scalloped shapes also have dips to clear.
        shape == com.jake.duolauncher.AppIconShape.CIRCLE -> .93f
        else -> .86f
    }

    fun of(shape: com.jake.duolauncher.AppIconShape?, px: Int, cornerRadiusPx: Float): HyperGlassShape {
        val outline = IconOutlines.of(shape, px.toFloat())
        val key = if (outline == null) "squircle:$px:$cornerRadiusPx" else "${shape!!.name}:$px"
        cache.get(key)?.let { return it }
        val field = if (outline == null) HyperGlass.squircleField(px, px, cornerRadiusPx) else {
            val side = px * FINE
            val drawing = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(drawing).apply { scale(FINE.toFloat(), FINE.toFloat()) }
                .drawPath(outline, android.graphics.Paint().apply { color = android.graphics.Color.WHITE; isAntiAlias = false })
            val pixels = IntArray(side * side).also { drawing.getPixels(it, 0, side, 0, 0, side, side) }
            drawing.recycle()
            HyperGlass.maskField(BooleanArray(side * side) { (pixels[it] ushr 24) > 127 }, px, px, FINE)
        }
        return HyperGlassShape(field).also { cache.put(key, it) }
    }
}

/** The shader and its textures for one icon, kept for as long as the icon is on screen: nothing is made again from frame to frame. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class HyperGlassShader(private val textures: HyperGlassTextures, val shape: HyperGlassShape) {
    val shader = android.graphics.RuntimeShader(HyperGlass.AGSL).apply {
        // DECAL: a bent read past the picture's edge finds nothing (so the background shows), and does not smear the last pixel.
        setInputShader("iconTexture", android.graphics.BitmapShader(textures.picture, android.graphics.Shader.TileMode.DECAL, android.graphics.Shader.TileMode.DECAL)
            .apply { filterMode = android.graphics.BitmapShader.FILTER_MODE_LINEAR })
        setInputShader("frostTexture", android.graphics.BitmapShader(textures.frostBitmap, android.graphics.Shader.TileMode.DECAL, android.graphics.Shader.TileMode.DECAL).apply {
            filterMode = android.graphics.BitmapShader.FILTER_MODE_LINEAR
            setLocalMatrix(android.graphics.Matrix().apply {
                setScale(textures.picture.width.toFloat() / textures.frostBitmap.width, textures.picture.height.toFloat() / textures.frostBitmap.height)
            })
        })
        setInputShader("shapeTexture", android.graphics.BitmapShader(shape.distance, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
            .apply { filterMode = android.graphics.BitmapShader.FILTER_MODE_LINEAR })
        setInputShader("edgeTexture", android.graphics.BitmapShader(shape.direction, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
            .apply { filterMode = android.graphics.BitmapShader.FILTER_MODE_LINEAR })
        setFloatUniform("shapeRange", shape.field.packedReach + HyperGlass.FIELD_MARGIN)
        setFloatUniform("shapeDepth", shape.field.depth)
    }
    val brush = ShaderBrush(shader)
    private var wallpaperSet = false
    private var wallpaperOf: Bitmap? = null

    /** The wallpaper the clear glass bends, or none. The shader's input is made again only when the picture is another one. */
    fun wallpaper(picture: Bitmap?) {
        if (wallpaperSet && picture === wallpaperOf) return
        wallpaperSet = true; wallpaperOf = picture
        // With no wallpaper the input is never read, but it has to be something.
        shader.setInputShader("wallpaperTexture", android.graphics.BitmapShader(picture ?: textures.picture, android.graphics.Shader.TileMode.CLAMP,
            android.graphics.Shader.TileMode.CLAMP).apply { filterMode = android.graphics.BitmapShader.FILTER_MODE_LINEAR })
    }
}

/** The pictures made for phones with no runtime shaders, kept by what they were made from. */
private object HyperGlassPictures {
    private val cache = android.util.LruCache<String, ImageBitmap>(96)
    fun get(key: String): ImageBitmap? = cache.get(key)
    fun put(key: String, picture: ImageBitmap) { cache.put(key, picture) }
}

/** The wallpaper behind clear hyper glass: its picture, taken to fill the window the icons are in. */
internal class HyperGlassWallpaper(val image: ImageBitmap)

/**
 * A hyper glass icon from a vector [icon]: the glyph, in [glyph] (or, with none, in the vector's own colours), is the texture inside
 * the glass (see [HyperGlass]). [shape] is the
 * outline (the launcher's squircle when null; for Mixed, give the one the app takes, [com.jake.duolauncher.AppIconShape.forApp]).
 * [background] is the card under it: black, white, [hyperGlassAccent], or [HyperGlass.clear] for clear glass with no card. Clear glass
 * given a [wallpaper] bends the wallpaper behind it as well (where there are runtime shaders); without one it is simply see-through. A
 * press deepens the edge's bend and lets it settle ([HyperGlass.pressStrength]); [onClick] is called for a tap. [forceFallback] draws
 * the pre-made picture that Android 12 gets, and [strength] holds the bend at one value; both are for tests and for looking.
 */
@Composable
internal fun HyperGlassIcon(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 60.dp, background: Color = Color.Black,
    glyph: Color? = HyperGlass.glyphOn(background), shape: com.jake.duolauncher.AppIconShape? = null, cornerRadius: Dp = size * Corner.ICON_FRACTION,
    contentDescription: String? = icon.name, wallpaper: HyperGlassWallpaper? = null, forceFallback: Boolean = false, strength: Float? = null,
    onClick: (() -> Unit)? = null) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val fit = HyperGlassShapes.fit(shape)
    val textures = remember(icon, px, glyph, fit) { HyperGlassTextures(HyperGlassTextures.rasterise(icon, px, glyph?.toArgb(), fit)) }
    HyperGlassTile(textures, "v:${icon.name}:${System.identityHashCode(icon)}:${glyph?.toArgb()}", modifier, size, background, shape, cornerRadius,
        contentDescription, wallpaper, forceFallback, strength, onClick)
}

/** A hyper glass icon from a picture: the whole [icon] is the texture inside the glass, plate and all. */
@Composable
internal fun HyperGlassIcon(icon: ImageBitmap, modifier: Modifier = Modifier, size: Dp = 60.dp, background: Color = Color.Black,
    shape: com.jake.duolauncher.AppIconShape? = null, cornerRadius: Dp = size * Corner.ICON_FRACTION, contentDescription: String? = null,
    wallpaper: HyperGlassWallpaper? = null, forceFallback: Boolean = false, strength: Float? = null, onClick: (() -> Unit)? = null) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val textures = remember(icon, px) { HyperGlassTextures(HyperGlassTextures.fit(icon.asAndroidBitmap(), px)) }
    HyperGlassTile(textures, "b:${System.identityHashCode(icon)}", modifier, size, background, shape, cornerRadius, contentDescription, wallpaper, forceFallback, strength, onClick)
}

/**
 * A hyper glass icon from an app's own icon, with its plate taken away ([HyperGlassTextures.unplated]): what is left of the icon is the
 * texture inside the glass, bent and lit as a glyph is. It is drawn in the one colour [mono] (white on a dark card, dark on a light
 * one), as the glyphs are; with no [mono] it keeps its own colours.
 */
@Composable
internal fun HyperGlassAppIcon(icon: android.graphics.drawable.Drawable, modifier: Modifier = Modifier, size: Dp = 60.dp, background: Color = Color.Black,
    mono: Color? = HyperGlass.glyphOn(background), shape: com.jake.duolauncher.AppIconShape? = null, cornerRadius: Dp = size * Corner.ICON_FRACTION,
    contentDescription: String? = null, wallpaper: HyperGlassWallpaper? = null, forceFallback: Boolean = false, strength: Float? = null,
    onClick: (() -> Unit)? = null) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val fit = HyperGlassShapes.fit(shape)
    val textures = remember(icon, px, mono, fit) { HyperGlassTextures(HyperGlassTextures.unplated(icon, px, mono?.toArgb(), fit)) }
    HyperGlassTile(textures, "a:${System.identityHashCode(icon)}:${mono?.toArgb()}", modifier, size, background, shape, cornerRadius, contentDescription, wallpaper,
        forceFallback, strength, onClick)
}

/** The launcher's accent as a hyper glass background: Appearance › Accent, which is Uno's orange or the colour taken from the wallpaper
 *  (the place Material You's colour has in Uno). */
@Composable
internal fun hyperGlassAccent(): Color = launcherAccent().first

@Composable
private fun HyperGlassTile(textures: HyperGlassTextures, key: String, modifier: Modifier, size: Dp, background: Color, shape: com.jake.duolauncher.AppIconShape?, cornerRadius: Dp,
    contentDescription: String?, wallpaper: HyperGlassWallpaper?, forceFallback: Boolean, strength: Float?, onClick: (() -> Unit)?) {
    val context = LocalContext.current
    val animations = remember {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    // The press: one clock from 0 to 300 ms, turned into the bend as the icon is drawn. In the icon's own scope, so it runs out even
    // when the tap opens something.
    val press = remember { Animatable(HyperGlass.PRESS_MS.toFloat()) }
    val scope = rememberCoroutineScope()
    val radiusPx = with(LocalDensity.current) { cornerRadius.toPx() }
    val touch = if (onClick == null) Modifier else Modifier
        .pointerInput(onClick, animations) {
            detectTapGestures(onPress = {
                if (animations) scope.launch { press.snapTo(0f); press.animateTo(HyperGlass.PRESS_MS.toFloat(), tween(HyperGlass.PRESS_MS, easing = LinearEasing)) }
            }, onTap = { onClick() })
        }
        .semantics { role = Role.Button; onClick(null) { onClick(); true } }
    val outline = remember(shape, textures.picture.width, radiusPx) { HyperGlassShapes.of(shape, textures.picture.width, radiusPx) }
    val base = modifier.size(size).then(touch)
        .semantics { if (contentDescription != null) this.contentDescription = contentDescription }.testTag("hyper-glass-icon")

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !forceFallback) {
        val glass = remember(textures, outline) { HyperGlassShader(textures, outline) }
        // Where the icon is in its window, and how big the window is, for finding the wallpaper behind it. Read as the icon is drawn.
        var place by remember { mutableStateOf(floatArrayOf(0f, 0f, 1f, 1f)) }
        val placed = if (wallpaper == null) base else base.onGloballyPositioned {
            val at = it.positionInWindow(); val root = it.findRootCoordinates().size
            if (place[0] != at.x || place[1] != at.y || place[2] != root.width.toFloat() || place[3] != root.height.toFloat())
                place = floatArrayOf(at.x, at.y, root.width.toFloat(), root.height.toFloat())
        }
        Canvas(placed) {
            val picture = wallpaper?.image?.asAndroidBitmap()
            glass.wallpaper(picture)
            glass.shader.setFloatUniform("wallpaperOn", if (picture != null) 1f else 0f)
            if (picture != null) {
                val sx = picture.width / place[2]; val sy = picture.height / place[3]
                glass.shader.setFloatUniform("wallpaperOrigin", place[0] * sx, place[1] * sy)
                glass.shader.setFloatUniform("wallpaperScale", sx, sy)
            } else { glass.shader.setFloatUniform("wallpaperOrigin", 0f, 0f); glass.shader.setFloatUniform("wallpaperScale", 1f, 1f) }
            glass.shader.setFloatUniform("size", this.size.width, this.size.height)
            // The picture and the shape are read by their own size, so an icon laid out smaller than it asked for is the whole icon
            // smaller, not a corner of it.
            glass.shader.setFloatUniform("textureSize", textures.picture.width.toFloat(), textures.picture.height.toFloat())
            glass.shader.setFloatUniform("refractionStrength", strength ?: HyperGlass.pressStrength(press.value))
            glass.shader.setFloatUniform("highlightWidth", HyperGlass.HIGHLIGHT_PX)
            glass.shader.setColorUniform("background", background.toArgb())
            drawRect(glass.brush)
        }
    } else {
        // No runtime shaders: the same picture, made once off the main thread and kept. It does not bend on a press.
        val held = strength ?: HyperGlass.REST
        val pictureKey = "$key:${textures.picture.width}:${shape?.name}:$radiusPx:$held:${background.toArgb()}"
        var picture by remember(pictureKey) { mutableStateOf(HyperGlassPictures.get(pictureKey)) }
        LaunchedEffect(pictureKey) {
            if (picture == null) picture = withContext(Dispatchers.Default) {
                val w = textures.picture.width; val h = textures.picture.height
                val pixels = HyperGlass.render(textures.texture, textures.frost, outline.field, held, background.toArgb())
                // Premultiplied sums into a premultiplied bitmap, as they are.
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.isPremultiplied = true; it.copyPixelsFromBuffer(argbToBuffer(pixels)) }.asImageBitmap()
            }.also { HyperGlassPictures.put(pictureKey, it) }
        }
        Canvas(base) { picture?.let { drawImage(it, dstSize = androidx.compose.ui.unit.IntSize(this.size.width.toInt(), this.size.height.toInt())) } }
    }
}

/** Premultiplied ARGB ints as the bytes a bitmap keeps (R, G, B, A in that order). */
private fun argbToBuffer(pixels: IntArray): java.nio.ByteBuffer {
    val buffer = java.nio.ByteBuffer.allocate(pixels.size * 4)
    for (c in pixels) { buffer.put((c shr 16).toByte()); buffer.put((c shr 8).toByte()); buffer.put(c.toByte()); buffer.put((c ushr 24).toByte()) }
    buffer.rewind()
    return buffer
}


/**
 * Clear hyper glass on a page of apps. An icon of clear glass is two things: its own layer (the picture pooled at the rim, the haze, the
 * light), which never changes and is made once as a picture with every other icon; and the wallpaper seen through the glass, bent by
 * the same rim, which changes whenever the icon moves over the wallpaper. Only the second is drawn live, and it is as little as it can
 * be: one shader program for every icon in the window ([Lens], as the launcher's own glass has one), two texture reads a pixel (the
 * shape's bend, worked out once as a picture, and the wallpaper), from the picture of the wallpaper Home already keeps for its glass ([SharedWallpaper]: blurred once,
 * a third of the size). Nothing is made as an icon is composed, and an icon that has not moved is not drawn again.
 */
internal object HyperGlassHome {
    /** Whether Home's icons are clear hyper glass, and the icon shape in use; set by Home as its settings change. Snapshot state. */
    var on by mutableStateOf(false)
    var shape by mutableStateOf(com.jake.duolauncher.AppIconShape.SQUIRCLE)
    /**
     * Whether the wallpaper behind an icon follows it frame by frame while a page is swiped (right, and the default), or is held as it
     * was and caught up when the page settles (for a phone that cannot keep up). Measured on an emulator, three pages of 24 icons:
     * following costs about a third more frame time than plain icons, and holding saves little of that, because the graphics chip
     * runs each icon's bend on every frame whether or not it was drawn again. Keeping each icon's result in a layer of its own, so
     * that an icon not drawn again would cost nothing, was four times slower when the icons did move (dozens of off-screen
     * layers redone a frame), and taking turns among them only partly made that up; so there are no layers.
     */
    var follow by mutableStateOf(true)
    /** Whether a page is being swiped just now; Home's pager says. */
    var moving: () -> Boolean = { false }
    /** The size icons are made at (LauncherModel's), which is the size their shape is measured at. */
    const val ICON_PX = 144

    const val LENS_AGSL = """
uniform shader wallpaper;
uniform shader bendTexture;
uniform float2 size;
uniform float2 textureSize;
uniform float reach;

half4 main(float2 coord) {
    half4 bend = bendTexture.eval(coord * textureSize / size);
    if (bend.b <= 0.0) { return half4(0.0); }
    float2 at = coord - (bend.rg * 2.0 - 1.0) * reach * size;
    return half4(wallpaper.eval(at).rgb * bend.b, bend.b);
}
"""

    /**
     * A shape's bend as one picture, so the live shader has nothing to work out: for each pixel, how far and which way the glass looks
     * (red and green, as a share of [reach] of the icon's width, -1..1 as 0..1) and how much of the pixel is inside the outline (blue).
     * The same numbers [HyperGlass.shade] uses, at the strength the glass has at rest. Unpremultiplied ARGB, opaque.
     */
    fun bendMap(field: HyperGlass.Field, strength: Float): IntArray = IntArray(field.width * field.height) {
        val d = field.d[it]
        val t = 1f - (kotlin.math.max(d, 0f) / (field.depth * HyperGlass.EDGE_REACH)).coerceIn(0f, 1f)
        val pull = t * t
        val nl = kotlin.math.max(kotlin.math.sqrt(field.nx[it] * field.nx[it] + field.ny[it] * field.ny[it]), .001f)
        val r = ((field.nx[it] / nl * pull * .5f + .5f) * 255f + .5f).toInt().coerceIn(0, 255)
        val g = ((field.ny[it] / nl * pull * .5f + .5f) * 255f + .5f).toInt().coerceIn(0, 255)
        val b = ((d + .5f).coerceIn(0f, 1f) * 255f + .5f).toInt()
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** The one program and paint for every icon in a window. Used from the UI thread only. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    class Lens {
        private val shader = android.graphics.RuntimeShader(LENS_AGSL)
        private val paint = android.graphics.Paint()
        private val matrix = android.graphics.Matrix()
        private var image: Bitmap? = null
        private var imageShader: android.graphics.BitmapShader? = null
        private val bends = HashMap<HyperGlassShape, android.graphics.BitmapShader>()

        /** Fills [width] by [height] at the canvas's origin with [image] as seen through glass of [shape]. A point of the image lands
         *  at (x * [scale] + [translateX], y * [scale] + [translateY]), as [SharedWallpaperMap.place] gives it. */
        fun draw(canvas: android.graphics.Canvas, width: Float, height: Float, image: Bitmap, scale: Float, translateX: Float, translateY: Float,
            shape: HyperGlassShape, strength: Float) {
            val source = imageShader?.takeIf { this.image === image }
                ?: android.graphics.BitmapShader(image, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP).also {
                    it.filterMode = android.graphics.BitmapShader.FILTER_MODE_LINEAR; imageShader = it; this.image = image
                }
            matrix.setScale(scale, scale); matrix.postTranslate(translateX, translateY)
            source.setLocalMatrix(matrix)
            val bend = bends.getOrPut(shape) {
                val w = shape.field.width; val h = shape.field.height
                val map = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.setPixels(bendMap(shape.field, 1f), 0, w, 0, 0, w, h) }
                android.graphics.BitmapShader(map, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
                    .apply { filterMode = android.graphics.BitmapShader.FILTER_MODE_LINEAR }
            }
            shader.setInputShader("wallpaper", source)
            shader.setInputShader("bendTexture", bend)
            shader.setFloatUniform("size", width, height)
            shader.setFloatUniform("textureSize", shape.field.width.toFloat(), shape.field.height.toFloat())
            shader.setFloatUniform("reach", strength)
            paint.shader = shader
            canvas.drawRect(0f, 0f, width, height, paint)
        }
    }

    private var lens: Any? = null
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun lens(): Lens = (lens as? Lens) ?: Lens().also { lens = it }

    /** How many times an icon's wallpaper has been drawn, for tests and for measuring. */
    var draws = 0L
}

/**
 * The wallpaper seen through the clear hyper glass icon of the app [id], drawn behind the icon's own picture. Nothing at all unless
 * Home's icons are clear glass, there are runtime shaders, and Home has its picture of the wallpaper; until then (and with the
 * launcher's glass off) the icon's own picture sits on the wallpaper as it is.
 */
@Composable
internal fun Modifier.hyperGlassWallpaper(id: String): Modifier {
    if (!HyperGlassHome.on || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    val shared = SharedWallpaperRuntime.current ?: return this
    val image = shared.image ?: return this
    val shape = HyperGlassHome.shape.forApp(id)
    val glass = remember(shape) { HyperGlassShapes.of(shape, HyperGlassHome.ICON_PX, HyperGlassHome.ICON_PX * Corner.ICON_FRACTION) }
    // Where the icon is in the window. [latest] is always the newest place; [origin] is the one that asks for a redraw when it
    // changes (read as the icon is drawn, so a move redraws the icon and recomposes nothing), and is not written while a page is
    // swiped unless the wallpaper is being followed.
    val latest = remember { floatArrayOf(Float.NaN, 0f) }
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Unspecified) }
    val held = remember { floatArrayOf(Float.NaN, 0f, 0f) }
    return onGloballyPositioned {
        val at = it.positionInWindow()
        val first = latest[0].isNaN()
        latest[0] = at.x; latest[1] = at.y
        if (at != origin && (first || HyperGlassHome.follow || !HyperGlassHome.moving())) origin = at
    }.drawBehind {
        // Read both, so the icon is drawn again when it has moved and when a swipe ends.
        val moving = HyperGlassHome.moving()
        if (origin == androidx.compose.ui.geometry.Offset.Unspecified || shared.artWidth <= 0) return@drawBehind
        if (HyperGlassHome.follow || !moving || held[0].isNaN()) {
            val drift = shared.drift(shared.artWidth.toFloat())
            val place = SharedWallpaperMap.place(image.width, shared.artWidth, shared.artHeight, drift?.scale ?: 1f, drift?.translationX ?: 0f, latest[0], latest[1])
            held[0] = place.scale; held[1] = place.translateX; held[2] = place.translateY
        }
        HyperGlassHome.draws++
        drawIntoCanvas { canvas -> HyperGlassHome.lens().draw(canvas.nativeCanvas, size.width, size.height, image, held[0], held[1], held[2], glass, HyperGlass.REST) }
    }
}
